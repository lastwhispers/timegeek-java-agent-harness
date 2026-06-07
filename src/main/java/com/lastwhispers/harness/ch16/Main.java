package com.lastwhispers.harness.ch16;

import com.lastwhispers.harness.ch16.context.Session;
import com.lastwhispers.harness.ch16.context.SessionManager;
import com.lastwhispers.harness.ch16.engine.AgentEngine;
import com.lastwhispers.harness.ch16.engine.TerminalReporter;
import com.lastwhispers.harness.ch16.feishu.ApprovalManager;
import com.lastwhispers.harness.ch16.feishu.CommandSafetyChecker;
import com.lastwhispers.harness.ch16.feishu.FeishuBot;
import com.lastwhispers.harness.ch16.provider.DashScopeProvider;
import com.lastwhispers.harness.ch16.provider.LLMProvider;
import com.lastwhispers.harness.ch16.schema.Message;
import com.lastwhispers.harness.ch16.schema.Role;
import com.lastwhispers.harness.ch16.tools.*;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

/**
 * 【修改】注册审批 Middleware，验证高危操作人工审批流程
 */
@Slf4j
public class Main {
    public static void main(String[] args) {
        Dotenv.load();

        String workDir = System.getProperty("user.dir") + "/workspace";
        LLMProvider llmProvider = new DashScopeProvider();

        // 挂载 4 大基础工具
        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        // 关闭 Plan 模式，专注于见证审批拦截过程
        AgentEngine engine = new AgentEngine(llmProvider, registry, false, false);

        // 【核心注入】注册安全拦截 Middleware
        registry.use(call -> {
            String argsStr = call.getArguments();
            if (CommandSafetyChecker.isDangerousCommand(call.getName(), argsStr)) {
                String taskId = call.getId();
                ApprovalManager.ApprovalResult result = ApprovalManager.INSTANCE.waitForApproval(
                    taskId, call.getName(), argsStr, new TerminalReporter()
                );
                if (!result.allowed()) {
                    return Registry.MiddlewareResult.deny(result.reason());
                }
                return Registry.MiddlewareResult.allow();
            }
            return Registry.MiddlewareResult.allow();
        });

        TerminalReporter reporter = new TerminalReporter();

        String sessionID = "test_command_intercept_001";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        // 诱发高危命令检测的提示词
        String prompt = """
                请帮我删除当前目录下所有以 .tmp 结尾的临时文件，使用 rm -r 命令。
                """;

        log.info("\n>>> 启动审批拦截测试...");

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
}
