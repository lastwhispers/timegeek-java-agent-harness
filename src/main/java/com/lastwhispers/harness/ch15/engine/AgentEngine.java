package com.lastwhispers.harness.ch15.engine;

import com.lastwhispers.harness.ch15.context.ContextCompactor;
import com.lastwhispers.harness.ch15.context.PromptComposer;
import com.lastwhispers.harness.ch15.context.RecoveryManager;
import com.lastwhispers.harness.ch15.context.Session;
import com.lastwhispers.harness.ch15.provider.LLMProvider;
import com.lastwhispers.harness.ch15.schema.*;
import com.lastwhispers.harness.ch15.tools.Registry;
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
    private final boolean planMode; // 计划模式开关
    private final ContextCompactor compactor;
    private final RecoveryManager recovery; // 自愈管理器
    private final ReminderInjector injector; // 死循环提醒注入器

    public AgentEngine(LLMProvider llmProvider, Registry registry, boolean enableThinking) {
        this(llmProvider, registry, enableThinking, false);
    }

    public AgentEngine(LLMProvider llmProvider, Registry registry, boolean enableThinking, boolean planMode) {
        this(llmProvider, registry, enableThinking, planMode, new ContextCompactor(80000, 6));
    }

    public AgentEngine(LLMProvider llmProvider, Registry registry, boolean enableThinking, boolean planMode, ContextCompactor compactor) {
        this.llmProvider = llmProvider;
        this.registry = registry;
        this.enableThinking = enableThinking;
        this.planMode = planMode;
        this.compactor = compactor;
        this.recovery = new RecoveryManager();
        this.injector = new ReminderInjector();
    }

    /**
     * 【核心改造】：移除 userPrompt 参数，改为接收一个具体的 Session 实例。
     * 每轮从 Session 中提取工作记忆构建上下文，动态加载工作区环境。
     */
    public void run(Session session, Reporter reporter) {
        log.info("[Engine] 唤醒会话 [{}]，锁定工作区: {}", session.getId(), session.getWorkDir());

        // 根据当前 Session 的工作区，动态组装最新的 System Prompt
        PromptComposer composer = new PromptComposer(session.getWorkDir(), this.planMode);
        Message systemMsg = composer.build();

        while (true) {
            List<ToolDefinition> availableTools = this.registry.getAvailableTools();

            // 1. 【上下文组装】：System Prompt + 截取最近的 6 条消息作为 Working Memory
            // 在实际业务中，由于工具返回结果可能很长，短期工作记忆往往设为 6-10 条足以维系连贯对话
            List<Message> workingMemory = session.getWorkingMemory(DEFAULT_WORKING_MEMORY_LIMIT);

            List<Message> contextHistory = new ArrayList<>();
            contextHistory.add(systemMsg);
            contextHistory.addAll(workingMemory);

            // 用于存放本轮 Turn 合并后的思考内容
            String currentTurnThinkingContent = "";

            // 2. ================= Phase 1: Thinking =================
            if (this.enableThinking) {
                if (reporter != null) {
                    reporter.onThinking();
                }
                try {
                    List<Message> compactedContext = this.compactor.compact(contextHistory);
                    Message thinkResp = this.llmProvider.generate(compactedContext, null);
                    if (thinkResp != null && StringUtils.isNotBlank(thinkResp.getContent())) {
                        // 【修改点】：思考内容暂存，先不 Append 到 session
                        currentTurnThinkingContent = thinkResp.getContent();
                        // 为了让 Phase 2 能看到刚才的思考，临时加入 contextHistory
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

            // 【核心修正】：合并 Thinking 和 Action 的内容，构造一条唯一的、合规的 Assistant 消息
            String mergedContent = StringUtils.trim(currentTurnThinkingContent + "\n" + actionResp.getContent());
            Message finalAssistantMsg = new Message();
            finalAssistantMsg.setRole(Role.ASSISTANT);
            finalAssistantMsg.setContent(mergedContent);
            finalAssistantMsg.setToolCalls(actionResp.getToolCalls());

            // 将合并后的合规消息持久化到 Session 中
            session.append(finalAssistantMsg);
            contextHistory.add(finalAssistantMsg);

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

            // 用于收集第一个工具供 Reminder 分析
            final ToolCall firstToolCall = actionResp.getToolCalls().get(0);
            final ToolResult[] firstToolResult = new ToolResult[1];

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

                        // 发生错误，交由 RecoveryManager 诊断并注入"锦囊妙计"
                        String finalOutput = result.getOutput();
                        if (result.isError()) {
                            finalOutput = this.recovery.analyzeAndInject(call.getName(), result.getOutput());
                            log.info("  -> [Thread-{}] 注入救援指南: {}", idx, finalOutput);
                        } else {
                            log.info("  -> [Thread-{}] 工具执行成功 (返回 {} 字节)", idx, result.getOutput().length());
                        }

                        if (reporter != null) {
                            String displayOutput = finalOutput;
                            if (displayOutput.length() > 200) {
                                displayOutput = displayOutput.substring(0, 200) + "... (已截断)";
                            }
                            reporter.onToolResult(call.getName(), displayOutput, result.isError());
                        }

                        // 将注入过 Recovery Hint 的最终结果写入上下文历史
                        observationMsgs.set(idx, new Message(Role.USER, finalOutput, call.getId()));

                        if (idx == 0) {
                            firstToolResult[0] = result;
                        }
                    }, executor);

                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            // 将所有的工具执行结果（Observation）持久化到 Session 中，开启下一轮的复盘与推理
            session.append(observationMsgs);

            // 【核心防线】：在进入下一轮前，进行死循环探测与注入
            Message reminderMsg = this.injector.checkAndInject(firstToolCall, firstToolResult[0]);
            if (reminderMsg != null) {
                session.append(reminderMsg);
            }
        }
    }
}
