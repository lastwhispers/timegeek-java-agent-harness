package com.lastwhispers.harness.ch10.engine;

import com.lastwhispers.harness.ch10.context.PromptComposer;
import com.lastwhispers.harness.ch10.provider.LLMProvider;
import com.lastwhispers.harness.ch10.schema.*;
import com.lastwhispers.harness.ch10.tools.Registry;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

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
    // 慢思考模式开关
    private Boolean enableThinking;

    public void run(String userPrompt) {
        run(userPrompt, null);
    }

    public void run(String userPrompt, Reporter reporter) {
        log.info("[Engine] 引擎启动，锁定工作区: {}\n", this.workDir);
        log.info("[Engine] 慢思考模式 (Thinking Phase): {}\n", this.enableThinking);

        // 【核心修改】动态组装 System Prompt
        PromptComposer composer = new PromptComposer(workDir);
        Message systemMessage = composer.build();

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
                if (reporter != null) {
                    reporter.onThinking();
                }
                try {
                    log.info("[Engine][Phase 1] 剥夺工具访问权，强制进入慢思考与规划阶段...");
                    // 核心机制：传入的 availableTools 为 null！
                    // 大模型看不到任何 JSON Schema，被迫只能输出纯文本的思考过程。
                    Message thinkResp = this.llmProvider.generate(contextHistory, null);
                    // 如果模型输出了思考过程，我们将其作为 Assistant 消息追加到上下文中
                    if (StringUtils.isNotBlank(thinkResp.getContent())) {
                        log.info("[内部思考 Trace]: {}\n", thinkResp.getContent());
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
                if (reporter != null) {
                    reporter.onMessage(responseMsg.getContent());
                } else {
                    log.info("[对外回复]: {}\n", responseMsg.getContent());
                }
            }

            // 3. 退出条件判断
            // 如果模型没有请求任何工具调用，说明它认为任务已经完成，跳出循环。
            if (CollectionUtils.isEmpty(responseMsg.getToolCalls())) {
                log.info("[Engine] 模型未请求调用工具，任务宣告完成。");
                break;
            }

            // 4. 执行行动 (Action) 与 获取观察结果 (Observation)
            log.info("[Engine] 模型请求并发调用 {} 个工具...\n", responseMsg.getToolCalls().size());

            // 核心改造：从串行演进为并行
            // 预分配固定长度切片，用于安全存放各并发工具的执行结果 (Observation)
            int callCount = responseMsg.getToolCalls().size();
            List<Message> observationMsgs = new ArrayList<>(callCount);
            for (int i = 0; i < callCount; i++) {
                observationMsgs.add(null);
            }

            // 使用虚拟线程线程池执行并发工具调用 (Java 21+)
            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int i = 0; i < callCount; i++) {
                    final int idx = i;
                    final ToolCall call = responseMsg.getToolCalls().get(i);

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        if (reporter != null) {
                            reporter.onToolCall(call.getName(), call.getArguments());
                        }
                        log.info("  -> [Thread-{}] 🛠️ 触发并行执行: {}\n", idx, call.getName());

                        // 调用底层 Registry 执行工具（物理操作）
                        ToolResult result = this.registry.execute(call);

                        if (reporter != null) {
                            String displayOutput = result.getOutput();
                            if (displayOutput.length() > 200) {
                                displayOutput = displayOutput.substring(0, 200) + "... (已截断)";
                            }
                            reporter.onToolResult(call.getName(), displayOutput, result.isError());
                        }

                        if (result.isError()) {
                            log.info("  -> [Thread-{}] ❌ 工具执行报错: {}\n", idx, result.getOutput());
                        } else {
                            log.info("  -> [Thread-{}] ✅ 工具执行成功 (返回 {} 字节)\n", idx, result.getOutput().length());
                        }

                        // 线程安全：每个线程操作预分配列表的不同索引，无需加锁
                        observationMsgs.set(idx, new Message(Role.USER, result.getOutput(), call.getId()));
                    }, executor);

                    futures.add(future);
                }

                // Join 阻塞等待：主循环挂起，直到所有并发任务全部执行完毕
                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            log.info("[Engine] 所有并发工具执行完毕，开始聚合观察结果 (Observation)...\n");

            // 按序追加回 Context
            for (Message obs : observationMsgs) {
                contextHistory.add(obs);
            }

            // 循环回到开头，模型将带着新加入的 Observation 继续它的下一轮思考...
        }
    }
}
