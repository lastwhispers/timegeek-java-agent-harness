package com.lastwhispers.harness.ch02.engine;

import com.lastwhispers.harness.ch02.provider.LLMProvider;
import com.lastwhispers.harness.ch02.schema.*;
import com.lastwhispers.harness.ch02.tools.Registry;
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

    public void run(String userPrompt) {
        log.info("[Engine] 引擎启动，锁定工作区: {}\n", this.workDir);
        // 1. 初始化会话的 Context (上下文内存)
        // 在真实的场景中，这里会由动态 Prompt 组装器加载 AGENTS.md。目前我们先硬编码。
        Message systemMessage = new Message();
        systemMessage.setRole(Role.SYSTEM);
        systemMessage.setContent("You are java-tiny-claw, an expert coding assistant. You have full access to tools in the workspace.");

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
            // 向大模型发起推理请求 (包含 Reasoning)
            log.info("[Engine] 正在思考 (Reasoning)...");
            Message responseMsg = null;
            try {
                responseMsg = this.llmProvider.generate(contextHistory, availableTools);
            } catch (Exception e) {
                log.error(e.getMessage());
                throw new RuntimeException(String.format("模型生成失败: %s", e.getMessage()));
            }
            // 将模型的响应完整追加到上下文历史中
            contextHistory.add(responseMsg);
            // 如果模型回复了纯文本，打印出来 (这通常是它的思考过程，或是最终结果)
            if (!StringUtils.isEmpty(responseMsg.getContent())) {
                log.info("🤖 模型: {}\n", responseMsg.getContent());
            }

            // 3. 退出条件判断
            // 如果模型没有请求任何工具调用，说明它认为任务已经完成，跳出循环。
            if (CollectionUtils.isEmpty(responseMsg.getToolCalls())) {
                log.info("[Engine] 任务完成，退出循环。");
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

