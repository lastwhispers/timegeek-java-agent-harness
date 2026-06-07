package com.lastwhispers.harness.ch16.context;

import com.lastwhispers.harness.ch16.schema.Message;
import com.lastwhispers.harness.ch16.schema.Role;
import com.lastwhispers.harness.ch16.schema.ToolCall;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.util.ArrayList;
import java.util.List;

/**
 * ContextCompactor 负责监控和压缩上下文内存，防止大模型发生 OOM。
 * 采用"双重降级防线"策略：远期历史区全量掩码 (Masking)，短期保护区超长局部截断 (Truncation)。
 */
@Slf4j
public class ContextCompactor {

    // 远期历史区：工具返回内容超过此值即被全量掩码，推理内容超过此值即被折叠
    private static final int EARLY_HISTORY_MAX_CONTENT = 200;
    // 短期保护区：即使处于近期保护区，只要单条内容过大，也必须截断防 OOM
    private static final int WORKING_MEMORY_MAX_CONTENT = 1000;
    // 掐头去尾截断保留长度（大模型通常只需要看开头报错和结尾总结）
    private static final int HEAD_TAIL_KEEP = 500;

    /**
     * 触发压缩的最大字符数阈值 (水位线，可参考使用的大模型的 token 窗口大小)
     */
    private final int maxChars;
    /**
     * Working Memory 保护区：最近的 N 条消息，压缩时强制保留完整内容
     */
    private final int retainLastMsgs;

    public ContextCompactor(int maxChars, int retainLastMsgs) {
        this.maxChars = maxChars;
        this.retainLastMsgs = retainLastMsgs;
    }

    /**
     * 接收准备发送给大模型的消息数组。
     * 如果总长度超标，对远期历史区进行全量掩码 (Masking)，对短期保护区进行超长局部截断 (Truncation)。
     */
    public List<Message> compact(List<Message> msgs) {
        int currentLength = estimateLength(msgs);

        // 如果没有超过水位线，直接返回原数组 (大多数情况下的正常路径)
        if (currentLength < maxChars) {
            return msgs;
        }

        log.info("[Compactor] ⚠️ 内存告警：当前上下文长度 ({} 字符) 超过阈值 ({}), 触发压缩清理...", currentLength, maxChars);

        List<Message> compacted = new ArrayList<>();
        int msgCount = msgs.size();

        // 计算受保护的 Working Memory 起始索引
        int protectStartIndex = Math.max(0, msgCount - retainLastMsgs);

        for (int i = 0; i < msgCount; i++) {
            Message msg = msgs.get(i);

            // 1. 系统提示词 (System Prompt) 绝对不能动，直接保留
            if (msg.getRole() == Role.SYSTEM) {
                compacted.add(msg);
                continue;
            }

            // 我们必须拷贝一份新消息，因为在并发环境中直接修改原引用可能导致底层数据结构被污染
            boolean isInWorkingMemory = i >= protectStartIndex;
            Message newMsg = msg;

            // 【核心驾驭逻辑】: 双重降级防线
            // 对于工具的返回结果 (Observation/ToolResult)
            if (isToolResult(msg)) {
                if (!isInWorkingMemory) {
                    // 【第一道防线：远期历史】如果是早期对话，执行无情替换 (Full Masking)
                    if (msg.getContent().length() > EARLY_HISTORY_MAX_CONTENT) {
                        newMsg = copyWithContent(msg, String.format("...[为了节省内存，早期的工具输出已被系统强制清理。原始长度: %d 字节]...", msg.getContent().length()));
                    }
                } else {
                    // 【第二道防线：短期记忆】即使处于近期保护区，只要单条内容过大，也必须截断防 OOM (Head-Tail Truncation)
                    // 我们保留前 500 字符和后 500 字符（掐头去尾法，大模型通常只需要看开头报错和结尾总结）
                    if (msg.getContent().length() > WORKING_MEMORY_MAX_CONTENT) {
                        String head = msg.getContent().substring(0, HEAD_TAIL_KEEP);
                        String tail = msg.getContent().substring(msg.getContent().length() - HEAD_TAIL_KEEP);
                        newMsg = copyWithContent(msg, String.format("%s\n\n...[内容过长，中间 %d 字节已被系统截断]...\n\n%s", head, msg.getContent().length() - WORKING_MEMORY_MAX_CONTENT, tail));
                    }
                }
            } else if (msg.getRole() == Role.ASSISTANT && msg.getContent() != null && !msg.getContent().isEmpty()) {
                // 对于大模型的冗长推理废话 (Thinking Trace)
                if (!isInWorkingMemory && msg.getContent().length() > EARLY_HISTORY_MAX_CONTENT) {
                    newMsg = copyWithContent(msg, "...[早期的推理思考过程已折叠]...");
                }
            }

            // 注意：我们绝不会去动 msg.getToolCalls()，因为这是模型行动的证据，是维系逻辑链的关键！
            compacted.add(newMsg);
        }

        int newLength = estimateLength(compacted);
        log.info("[Compactor] ✅ 压缩完成。上下文长度从 {} 降至 {} 字符。", currentLength, newLength);

        return compacted;
    }

    /**
     * 判断消息是否为工具返回结果（role=user/tool 且带有 toolCallId）
     */
    private boolean isToolResult(Message msg) {
        return (msg.getRole() == Role.USER || msg.getRole() == Role.TOOL)
                && msg.getToolCallId() != null && !msg.getToolCallId().isEmpty();
    }

    /**
     * 拷贝一条消息并替换 content 字段。不拷贝 toolCalls 以避免破坏逻辑链。
     */
    private Message copyWithContent(Message src, String newContent) {
        Message copy = new Message(src.getRole(), newContent);
        copy.setToolCalls(src.getToolCalls());
        copy.setToolCallId(src.getToolCallId());
        return copy;
    }

    /**
     * 粗略计算当前上下文的总字符长度
     * 包含消息 body + toolCalls 的 name + arguments
     */
    private int estimateLength(List<Message> msgs) {
        int length = 0;
        for (Message msg : msgs) {
            if (msg.getContent() != null) {
                length += msg.getContent().length();
            }
            if (CollectionUtils.isNotEmpty(msg.getToolCalls())) {
                for (ToolCall tc : msg.getToolCalls()) {
                    if (tc.getName() != null) {
                        length += tc.getName().length();
                    }
                    if (tc.getArguments() != null) {
                        length += tc.getArguments().length();
                    }
                }
            }
        }
        return length;
    }
}
