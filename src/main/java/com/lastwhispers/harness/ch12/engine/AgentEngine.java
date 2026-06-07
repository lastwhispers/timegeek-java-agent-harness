package com.lastwhispers.harness.ch12.engine;

import com.lastwhispers.harness.ch12.context.ContextCompactor;
import com.lastwhispers.harness.ch12.context.PromptComposer;
import com.lastwhispers.harness.ch12.context.Session;
import com.lastwhispers.harness.ch12.provider.LLMProvider;
import com.lastwhispers.harness.ch12.schema.*;
import com.lastwhispers.harness.ch12.tools.Registry;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * AgentEngine 是微型 OS 的核心驱动。
 *
 * 【注意】：我们移除了 Engine 层级的 workDir，因为 workDir 现在应该跟随 Session 走！
 */
@Slf4j
public class AgentEngine {

    private static final int DEFAULT_WORKING_MEMORY_LIMIT = 6;

    private final LLMProvider llmProvider;
    private final Registry registry;
    private final boolean enableThinking;
    private final ContextCompactor compactor;

    public AgentEngine(LLMProvider llmProvider, Registry registry, boolean enableThinking) {
        this(llmProvider, registry, enableThinking, new ContextCompactor(80000, 6));
    }

    public AgentEngine(LLMProvider llmProvider, Registry registry, boolean enableThinking, ContextCompactor compactor) {
        this.llmProvider = llmProvider;
        this.registry = registry;
        this.enableThinking = enableThinking;
        this.compactor = compactor;
    }

    /**
     * 【核心改造】：移除 userPrompt 参数，改为接收一个具体的 Session 实例。
     * 每轮从 Session 中提取工作记忆构建上下文，动态加载工作区环境。
     */
    public void run(Session session, Reporter reporter) {
        log.info("[Engine] 唤醒会话 [{}]，锁定工作区: {}", session.getId(), session.getWorkDir());

        // 根据当前 Session 的工作区，动态组装最新的 System Prompt
        PromptComposer composer = new PromptComposer(session.getWorkDir());
        Message systemMsg = composer.build();

        while (true) {
            List<ToolDefinition> availableTools = this.registry.getAvailableTools();

            // 1. 【上下文组装】：System Prompt + 截取最近的 6 条消息作为 Working Memory
            // 在实际业务中，由于工具返回结果可能很长，短期工作记忆往往设为 6-10 条足以维系连贯对话
            List<Message> workingMemory = session.getWorkingMemory(DEFAULT_WORKING_MEMORY_LIMIT);

            List<Message> contextHistory = new ArrayList<>();
            contextHistory.add(systemMsg);
            contextHistory.addAll(workingMemory);

            // 2. ================= Phase 1: Thinking =================
            if (this.enableThinking) {
                if (reporter != null) {
                    reporter.onThinking();
                }
                try {
                    List<Message> compactedContext = this.compactor.compact(contextHistory);
                    Message thinkResp = this.llmProvider.generate(compactedContext, null);
                    if (thinkResp != null && StringUtils.isNotBlank(thinkResp.getContent())) {
                        // 将思考过程持久化到 Session 中！
                        session.append(thinkResp);
                        // 把它追加到当前这一轮的临时上下文中，供 Action 阶段使用
                        contextHistory.add(thinkResp);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Thinking 阶段生成失败: " + e.getMessage(), e);
                }
            }

            // 3. ================= Phase 2: Action =================
            Message actionResp;
            try {
                List<Message> compactedContext = this.compactor.compact(contextHistory);
                actionResp = this.llmProvider.generate(compactedContext, availableTools);
            } catch (Exception e) {
                throw new RuntimeException("Action 阶段生成失败: " + e.getMessage(), e);
            }

            // 将大模型的行动响应持久化到 Session 中
            session.append(actionResp);
            contextHistory.add(actionResp);

            if (StringUtils.isNotBlank(actionResp.getContent()) && reporter != null) {
                reporter.onMessage(actionResp.getContent());
            }

            if (CollectionUtils.isEmpty(actionResp.getToolCalls())) {
                // 如果没有工具调用，说明本次任务已完成，打破 ReAct 循环，挂起等待人类的下一条指令
                break;
            }

            // 4. ================= 并发执行底层工具 =================
            int callCount = actionResp.getToolCalls().size();
            List<Message> observationMsgs = new ArrayList<>(callCount);
            for (int i = 0; i < callCount; i++) {
                observationMsgs.add(null);
            }

            try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
                List<CompletableFuture<Void>> futures = new ArrayList<>();

                for (int i = 0; i < callCount; i++) {
                    final int idx = i;
                    final ToolCall call = actionResp.getToolCalls().get(i);

                    CompletableFuture<Void> future = CompletableFuture.runAsync(() -> {
                        if (reporter != null) {
                            reporter.onToolCall(call.getName(), call.getArguments());
                        }

                        ToolResult result = this.registry.execute(call);

                        if (reporter != null) {
                            String displayOutput = result.getOutput();
                            if (displayOutput.length() > 200) {
                                displayOutput = displayOutput.substring(0, 200) + "... (已截断)";
                            }
                            reporter.onToolResult(call.getName(), displayOutput, result.isError());
                        }

                        if (result.isError()) {
                            log.info("  -> [Thread-{}] 工具执行报错: {}", idx, result.getOutput());
                        } else {
                            log.info("  -> [Thread-{}] 工具执行成功 (返回 {} 字节)", idx, result.getOutput().length());
                        }

                        observationMsgs.set(idx, new Message(Role.USER, result.getOutput(), call.getId()));
                    }, executor);

                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            // 将所有的工具执行结果（Observation）持久化到 Session 中，开启下一轮的复盘与推理
            session.append(observationMsgs);
        }
    }
}
