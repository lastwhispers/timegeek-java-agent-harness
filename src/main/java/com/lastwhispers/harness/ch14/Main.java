package com.lastwhispers.harness.ch14;

import com.lastwhispers.harness.ch14.context.ContextCompactor;
import com.lastwhispers.harness.ch14.context.Session;
import com.lastwhispers.harness.ch14.context.SessionManager;
import com.lastwhispers.harness.ch14.engine.AgentEngine;
import com.lastwhispers.harness.ch14.engine.TerminalReporter;
import com.lastwhispers.harness.ch14.provider.DashScopeProvider;
import com.lastwhispers.harness.ch14.provider.LLMProvider;
import com.lastwhispers.harness.ch14.schema.Message;
import com.lastwhispers.harness.ch14.schema.Role;
import com.lastwhispers.harness.ch14.tools.*;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

/**
 * 【修改】下发长程任务，并开启 Plan Mode 测试
 */
@Slf4j
public class Main {
    public static void main(String[] args) {
        // 通过命令行参数接收用户的 prompt
//        String prompt = parsePrompt(args);
        String prompt = "我需要你搭建一个极简的 Java 语言 Web Server 项目。";
        if (prompt == null || prompt.isEmpty()) {
            log.info("用法: java Main -prompt \"你的任务指令\"");
            System.exit(1);
        }

        Dotenv.load();

        String workDir = System.getProperty("user.dir") + "/workspace/ch13";
        LLMProvider llmProvider = new DashScopeProvider();

        // 挂载 4 大基础工具
        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        // 实例化引擎并开启计划模式 (PlanMode=true)
        ContextCompactor compactor = new ContextCompactor(80000, 6);
        AgentEngine engine = new AgentEngine(llmProvider, registry, false, true, compactor);
        TerminalReporter reporter = new TerminalReporter();

        // 使用一个固定的 SessionID，以便在多次运行之间共享基于内存的"短期工作记忆"。
        // (在真实的 CLI 中，如果进程重启，Session 的内存历史其实是丢失的。
        //  但这正是要演示的重点：即便短期内存丢失，只要 TODO.md 还在，任务就能继续！)
        String sessionID = "task_web_server_01";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        log.info("\n>>> 收到指令: {}", prompt);

        // 将用户的 Prompt 压入 Session
        session.append(new Message(Role.USER, prompt));

        // 唤醒引擎执行
        try {
            engine.run(session, reporter);
        } catch (Exception e) {
            log.error("引擎运行崩溃: {}", e.getMessage(), e);
        }

        log.info("会话已结束。");
    }

    /**
     * 简单的命令行参数解析，支持 -prompt "xxx" 格式
     */
    private static String parsePrompt(String[] args) {
        for (int i = 0; i < args.length; i++) {
            if ("-prompt".equals(args[i]) && i + 1 < args.length) {
                return args[i + 1];
            }
        }
        return null;
    }
}
