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
 * 【修改】将 Provider 包装进 Tracker 再注入 Engine，验证可观测性追踪
 */
@Slf4j
public class Main {
    public static void main(String[] args) {
        Dotenv.load();

        String workDir = System.getProperty("user.dir") + "/workspace/ch17";

        // 【核心注入】将 Provider 包装进 Tracker 装饰器
        Tracker tracker = new Tracker(new DashScopeProvider());
        LLMProvider llmProvider = tracker;
        TerminalReporter reporter = new TerminalReporter();

        // 【防御沙箱】为子智能体准备受限的只读注册表
        Registry readOnlyRegistry = new RegistryImpl();
        readOnlyRegistry.register(new ReadFileTool(workDir));
        readOnlyRegistry.register(new BashTool(workDir)); // 允许简单的 grep 等搜索操作

        // 为主智能体准备全功能注册表
        Registry mainRegistry = new RegistryImpl();
        mainRegistry.register(new ReadFileTool(workDir));
        mainRegistry.register(new WriteFileTool(workDir));
        mainRegistry.register(new BashTool(workDir));
        mainRegistry.register(new EditFileTool(workDir));

        // 初始化主引擎
        AgentEngine engine = new AgentEngine(llmProvider, mainRegistry, false, false);

        // 【核心装配】：将带有 Engine 引用和只读 Registry 的 Subagent 工具注册进主线
        mainRegistry.register(new SubagentTool(engine, readOnlyRegistry, reporter));

        String sessionID = "test_observability_001";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        String prompt = """
                我需要你在这个遗留项目里，找到那个"核心密码"。
                为了防止污染主上下文，请你务必派出子智能体（spawn_subagent）去执行探索任务。
                你可以让子智能体使用 bash 去查找当前目录（及其所有子目录）下名为 config.txt 的文件。
                子智能体拿到密码向你汇报后，请你亲自使用 write_file 工具，将密码写在根目录的 answer.txt 里。
                """+",当前目录为"+workDir;

        log.info("\n>>> 启动可观测性追踪测试...");

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

        log.info("会话已结束。");
    }
}
