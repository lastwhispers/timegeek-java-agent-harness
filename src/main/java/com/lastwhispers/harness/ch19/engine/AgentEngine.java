package com.lastwhispers.harness.ch19.engine;

import com.lastwhispers.harness.ch19.context.ContextCompactor;
import com.lastwhispers.harness.ch19.context.PromptComposer;
import com.lastwhispers.harness.ch19.context.RecoveryManager;
import com.lastwhispers.harness.ch19.context.Session;
import com.lastwhispers.harness.ch19.observability.Trace;
import com.lastwhispers.harness.ch19.provider.LLMProvider;
import com.lastwhispers.harness.ch19.schema.*;
import com.lastwhispers.harness.ch19.tools.Registry;
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
public class AgentEngine implements AgentRunner {

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

        // 【埋点 1】开启 Root Span，记录整个任务的生命周期
        Trace.Span rootSpan = Trace.startSpan(null, "Agent.Run");
        rootSpan.addAttribute("SessionID", session.getId());
        rootSpan.addAttribute("WorkDir", session.getWorkDir());

        try {
            doRun(session, reporter, rootSpan);
        } catch (Exception e) {
            rootSpan.addAttribute("error", e.getMessage());
            throw e;
        } finally {
            // defer 保证在引擎退出时，无论成功失败，都能结束根 Span 并导出 Trace 报告
            rootSpan.endSpan();
            Trace.exportToFile(rootSpan, session.getWorkDir(), session.getId());
            log.info("[Tracing] 本次任务的执行回放链路已保存至工作区的 .claw/traces 目录下");
        }
    }

    private void doRun(Session session, Reporter reporter, Trace.Span rootSpan) {
        // 根据当前 Session 的工作区，动态组装最新的 System Prompt
        PromptComposer composer = new PromptComposer(session.getWorkDir(), this.planMode);
        Message systemMsg = composer.build();

        int turnCount = 0;
        while (true) {
            turnCount++;
            // 【埋点 2】记录单次 Turn 循环
            Trace.Span turnSpan = Trace.startSpan(rootSpan, "Turn-" + turnCount);

            List<ToolDefinition> availableTools = this.registry.getAvailableTools();

            // 1. 【上下文组装】：System Prompt + 截取最近的 6 条消息作为 Working Memory
            List<Message> workingMemory = session.getWorkingMemory(DEFAULT_WORKING_MEMORY_LIMIT);

            List<Message> contextHistory = new ArrayList<>();
            contextHistory.add(systemMsg);
            contextHistory.addAll(workingMemory);

            List<Message> compactedBeforeThinking = this.compactor.compact(contextHistory);
            turnSpan.addAttribute("context_message_count", compactedBeforeThinking.size());

            // 用于存放本轮 Turn 合并后的思考内容
            String currentTurnThinkingContent = "";

            // 2. ================= Phase 1: Thinking =================
            if (this.enableThinking) {
                if (reporter != null) {
                    reporter.onThinking();
                }
                try {
                    // 【埋点 3】记录 Thinking 调用
                    Trace.Span thinkSpan = Trace.startSpan(turnSpan, "LLM.Thinking");
                    Message thinkResp = this.llmProvider.generate(compactedBeforeThinking, null);
                    thinkSpan.endSpan();

                    if (thinkResp != null && StringUtils.isNotBlank(thinkResp.getContent())) {
                        currentTurnThinkingContent = thinkResp.getContent();
                        contextHistory.add(thinkResp);
                    }
                } catch (Exception e) {
                    throw new RuntimeException("Thinking 阶段生成失败: " + e.getMessage(), e);
                }
            }

            // 3. ================= Phase 2: Action =================
            Message actionResp;
            try {
                List<Message> compactedBeforeAction = this.compactor.compact(contextHistory);
                // 【埋点 4】记录 Action 调用
                Trace.Span actSpan = Trace.startSpan(turnSpan, "LLM.Action");
                actionResp = this.llmProvider.generate(compactedBeforeAction, availableTools);
                actSpan.endSpan();
            } catch (Exception e) {
                throw new RuntimeException("Action 阶段生成失败: " + e.getMessage(), e);
            }

            // 【核心修正】：合并 Thinking 和 Action 的内容，构造一条唯一的、合规的 Assistant 消息
            String mergedContent = StringUtils.trim(currentTurnThinkingContent + "\n" + actionResp.getContent());
            Message finalAssistantMsg = new Message();
            finalAssistantMsg.setRole(Role.ASSISTANT);
            finalAssistantMsg.setContent(mergedContent);
            finalAssistantMsg.setToolCalls(actionResp.getToolCalls());
            finalAssistantMsg.setUsage(actionResp.getUsage());

            // 将合并后的合规消息持久化到 Session 中
            session.append(finalAssistantMsg);
            contextHistory.add(finalAssistantMsg);

            if (StringUtils.isNotBlank(actionResp.getContent()) && reporter != null) {
                reporter.onMessage(actionResp.getContent());
            }

            if (CollectionUtils.isEmpty(actionResp.getToolCalls())) {
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

                        // 【埋点 5】传入 turnSpan 作为父 Span，多个工具的 Span 会平行挂在 Turn 节点下
                        ToolResult result = this.registry.execute(call, turnSpan);

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

            turnSpan.endSpan();
        }
    }

    /**
     * RunSub 是专为 Subagent 拉起的一次性受限循环。
     * 不依赖外部 Session，打完就跑。
     * Reporter 用于让终端用户看到子智能体的工作轨迹。
     */
    @Override
    public String runSub(String taskPrompt, Registry readOnlyRegistry, Reporter reporter) {

        // 【核心优化】：子智能体极其容易偷懒，必须在 System Prompt 中严厉警告它必须使用工具！
        List<Message> contextHistory = new ArrayList<>();
        contextHistory.add(new Message(Role.SYSTEM, """
            你是一个专门负责深度探索的探路者 (Explorer Subagent)。
            你的任务是根据主架构师的指令，在当前工作区内仔细阅读代码、查阅日志，搜集足够的信息。

            【核心纪律】
            1. 你必须、且只能依靠内置工具（如 bash 的 find/grep，或 read_file）去寻找答案。绝对不允许凭空捏造或猜测！
            2. 如果你没有找到确切的答案，你必须继续使用工具深入搜索。
            3. 当且仅当你找到了确切的线索后，停止调用工具，直接输出一段纯文本作为你的终极汇报。主架构师会根据你的汇报来做下一步决策。
            """));
        contextHistory.add(new Message(Role.USER, taskPrompt));

        // 限制子智能体最多只能跑 10 个 Turn，防止它自己卡死
        final int maxSubTurns = 10;
        int turnCount = 0;

        while (true) {
            turnCount++;
            if (turnCount > maxSubTurns) {
                throw new RuntimeException(String.format(
                    "子智能体探索过于深入，超过 %d 轮被强制召回，请主 Agent 给它更明确的指令", maxSubTurns));
            }

            // 【驾驭底线】：子智能体仅能获取传入的只读工具注册表
            List<ToolDefinition> availableTools = readOnlyRegistry.getAvailableTools();

            List<Message> compactedContext = this.compactor.compact(contextHistory);

            // 子任务要求急速响应，强制关闭主体的慢思考，直接预测行动
            Message actionResp;
            try {
                actionResp = this.llmProvider.generate(compactedContext, availableTools);
            } catch (Exception e) {
                throw new RuntimeException("子智能体推理失败: " + e.getMessage(), e);
            }

            contextHistory.add(actionResp);

            // 【核心退出条件】：子智能体一旦不调用工具了，说明它做好了总结汇报
            if (CollectionUtils.isEmpty(actionResp.getToolCalls())) {
                return actionResp.getContent();
            }

            // 执行只读工具的并发循环
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
                        // 【可视化关键】：让终端用户看到 Subagent 正在做什么
                        if (reporter != null) {
                            reporter.onToolCall("[Subagent] " + call.getName(), call.getArguments());
                        }

                        ToolResult result = readOnlyRegistry.execute(call);

                        String finalOutput = result.getOutput();
                        if (result.isError()) {
                            finalOutput = this.recovery.analyzeAndInject(call.getName(), result.getOutput());
                        }

                        if (reporter != null) {
                            String display = finalOutput;
                            if (display.length() > 200) {
                                display = display.substring(0, 200) + "... (已截断)";
                            }
                            reporter.onToolResult("[Subagent] " + call.getName(), display, result.isError());
                        }

                        observationMsgs.set(idx, new Message(Role.USER, finalOutput, call.getId()));
                    }, executor);

                    futures.add(future);
                }

                CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();
            }

            contextHistory.addAll(observationMsgs);
        }
    }
}
