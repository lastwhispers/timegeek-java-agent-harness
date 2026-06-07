package com.lastwhispers.harness.ch19;

import com.lastwhispers.harness.ch19.context.Session;
import com.lastwhispers.harness.ch19.context.SessionManager;
import com.lastwhispers.harness.ch19.engine.AgentEngine;
import com.lastwhispers.harness.ch19.engine.TerminalReporter;
import com.lastwhispers.harness.ch19.observability.Tracker;
import com.lastwhispers.harness.ch19.provider.DashScopeProvider;
import com.lastwhispers.harness.ch19.provider.LLMProvider;
import com.lastwhispers.harness.ch19.schema.Message;
import com.lastwhispers.harness.ch19.schema.Role;
import com.lastwhispers.harness.ch19.tools.*;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

/**
 * 【修改】执行任务后，产出 trace.json 文件，验证链路追踪系统
 */
@Slf4j
public class Main {
    public static void main(String[] args) {
        Dotenv.load();

        String workDir = System.getProperty("user.dir") + "/workspace/ch19";

        // 【核心注入】将 Provider 包装进 Tracker 装饰器
        Tracker tracker = new Tracker(new DashScopeProvider());
        LLMProvider llmProvider = tracker;
        TerminalReporter reporter = new TerminalReporter();

        // 为主智能体准备全功能注册表
        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        // 初始化主引擎
        AgentEngine engine = new AgentEngine(llmProvider, registry, false, false);

        String sessionID = "test_trace_001";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        // 触发一个跨工具类型的并发任务，验证 Trace 中的并发 Span 树结构
        String prompt = """
                为了加快执行速度，请你在一轮回复中，【同时并行】完成以下两件事：
                1. 使用 bash 工具执行 'sleep 2 && echo "系统环境检查完毕"'
                2. 使用 write_file 工具，在当前目录下创建一个 'trace_test.md'，内容写上 "测试并发的写入"。
                请确保你是分别调用两个不同的工具，不要试图把它们合并成一个命令！
                """;

        log.info("\n>>> 启动带 Tracing 链路追踪的测试...");

        // 将用户的 Prompt 压入 Session
        session.append(new Message(Role.USER, prompt));

        // 唤醒引擎执行
        try {
            engine.run(session, reporter);
        } catch (Exception e) {
            log.error("引擎运行崩溃: {}", e.getMessage(), e);
        }

        // 打印追踪摘要
        log.info("\n>>> Tracker 追踪摘要: {}", tracker.getSummary());
        log.info(">>> Session 累计: prompt_tokens={} completion_tokens={} total_tokens={} cost=${:.4f}",
            session.getTotalPromptTokens(),
            session.getTotalCompletionTokens(),
            session.getTotalTokens(),
            session.getTotalCostUsd());

        log.info("会话已结束，请查看工作目录下的 .claw/traces/ 目录中的 trace.json 文件。");
    }
}
