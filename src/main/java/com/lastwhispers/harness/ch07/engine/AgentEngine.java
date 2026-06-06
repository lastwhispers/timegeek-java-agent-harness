package com.lastwhispers.harness.ch07.engine;


import com.lastwhispers.harness.ch07.provider.LLMProvider;
import com.lastwhispers.harness.ch07.schema.*;
import com.lastwhispers.harness.ch07.tools.Registry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;

@Data
@AllArgsConstructor
@Slf4j
// AgentEngine 是微型 OS 的核心驱动
public class AgentEngine {

    // 模型提供者
    private LLMProvider llmProvider;
    // 工具注册
    private Registry registry;
    // 工作区
    private String workDir;
    // 【新增】慢思考模式开关
    private Boolean enableThinking;

    public void run(String userPrompt) {
        log.info("[Engine] 引擎启动，锁定工作区: {}\n", this.workDir);
        log.info("[Engine] 慢思考模式 (Thinking Phase): {}\n", this.enableThinking);
        // 1. 初始化会话的 Context (上下文内存)
        // 在真实的场景中，这里会由动态 Prompt 组装器加载 AGENTS.md。目前我们先硬编码。
        Message systemMessage = new Message();
        systemMessage.setRole(Role.SYSTEM);
        systemMessage.setContent("You are go-tiny-claw, an expert coding assistant. You have full access to tools in the workspace.");

        Message userMessage = new Message();
        userMessage.setRole(Role.USER);
        userMessage.setContent(userPrompt);
        List<Message> contextHistory = new ArrayList<>();
        contextHistory.add(systemMessage);
        contextHistory.add(userMessage);

        int turnCount = 0;

        // 2. The Main Loop: 心跳开始 (标准的 ReAct 循环)
        while (true) {
            turnCount++;
            log.info("========== [Turn {}] 开始 ==========\n", turnCount);
            // 获取当前挂载的所有工具定义
            List<ToolDefinition> availableTools = this.registry.getAvailableTools();

            // ====================================================================
            // Phase 1: 慢思考阶段 (Thinking) - 剥夺工具，强制规划
            // ====================================================================

            if (this.enableThinking) {
                try {
                    log.info("[Engine][Phase 1] 剥夺工具访问权，强制进入慢思考与规划阶段...");
                    // 核心机制：传入的 availableTools 为 nil！
                    // 大模型看不到任何 JSON Schema，被迫只能输出纯文本的思考过程。
                    Message thinkResp = this.llmProvider.generate(contextHistory, null);
                    // 如果模型输出了思考过程，我们将其作为 Assistant 消息追加到上下文中
                    if (StringUtils.isNotBlank(thinkResp.getContent())) {
                        log.info("🧠 [内部思考 Trace]: {}\n", thinkResp.getContent());
                    }
                    contextHistory.add(thinkResp);
                } catch (Exception e) {
                    log.error("Thinking 阶段生成失败: {}", e.getMessage());
                    throw new RuntimeException(String.format("Thinking 阶段生成失败: %s", e.getMessage()));
                }
            }

            // ====================================================================
            // Phase 2: 行动阶段 (Action) - 恢复工具，顺着规划执行
            // ====================================================================
            log.info("[Engine][Phase 2] 恢复工具挂载，等待模型采取行动...");
            // 此时的 contextHistory 中已经包含了上一阶段模型自己的 Thinking Trace。
            // 模型会顺着自己的逻辑，结合恢复的 availableTools 发起精准的工具调用。
            Message responseMsg = null;
            try {
                responseMsg = this.llmProvider.generate(contextHistory, availableTools);
            } catch (Exception e) {
                log.error("模型生成失败: {}", e.getMessage());
                throw new RuntimeException(String.format("模型生成失败: %s", e.getMessage()));
            }
            // 将模型的响应完整追加到上下文历史中
            contextHistory.add(responseMsg);
            // 如果模型回复了纯文本，打印出来 (这通常是它的思考过程，或是最终结果)
            if (!StringUtils.isEmpty(responseMsg.getContent())) {
                log.info("[对外回复]: {}\n", responseMsg.getContent());
            }

            // 3. 退出条件判断
            // 如果模型没有请求任何工具调用，说明它认为任务已经完成，跳出循环。
            if (CollectionUtils.isEmpty(responseMsg.getToolCalls())) {
                log.info("[Engine] 模型未请求调用工具，任务宣告完成。");
                break;
            }

            // 4. 执行行动 (Action) 与 获取观察结果 (Observation)
            log.info("[Engine] 模型请求调用 {} 个工具...\n", responseMsg.getToolCalls().size());

            for (ToolCall toolCall : responseMsg.getToolCalls()) {
                log.info(" -> 🛠️ 执行工具: {}, 参数: {}\n", toolCall.getName(), (toolCall.getArguments()));
                // 通过 Registry 路由并执行底层工具
                ToolResult toolResult = this.registry.execute(toolCall);
                if (toolResult.isError()) {
                    log.info(" -> ❌ 工具执行报错: {}\n", toolResult.getOutput());
                } else {
                    log.info(" -> ✅ 工具执行成功 (返回 {} 字节)\n", (toolResult.getOutput().length()));
                }
                // 将工具执行的观察结果 (Observation) 封装为 User Message 追加到上下文中
                // 注意：ToolCallID 必须携带！这是维系大模型推理链条的关键
                Message observationMsg = new Message(Role.USER, toolResult.getOutput(), toolCall.getId());
                contextHistory.add(observationMsg);

            }

            // 循环回到开头，模型将带着新加入的 Observation 继续它的下一轮思考...
        }
    }

}
