package com.lastwhispers.harness.ch12;

import com.lastwhispers.harness.ch12.context.ContextCompactor;
import com.lastwhispers.harness.ch12.context.Session;
import com.lastwhispers.harness.ch12.context.SessionManager;
import com.lastwhispers.harness.ch12.engine.AgentEngine;
import com.lastwhispers.harness.ch12.engine.TerminalReporter;
import com.lastwhispers.harness.ch12.provider.DashScopeProvider;
import com.lastwhispers.harness.ch12.provider.LLMProvider;
import com.lastwhispers.harness.ch12.schema.Message;
import com.lastwhispers.harness.ch12.schema.Role;
import com.lastwhispers.harness.ch12.tools.*;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class Main {
    public static void main(String[] args) {
        Dotenv.load();

        String workDir = System.getProperty("user.dir") + "/workspace/ch12";
        LLMProvider llmProvider = new DashScopeProvider();

        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));

        // 实例化引擎，传入一个极小的 Compactor 阈值 (8000 字符) 用于测试 OOM 防护机制
        ContextCompactor compactor = new ContextCompactor(8000, 6);
        AgentEngine engine = new AgentEngine(llmProvider, registry, false, compactor);
        TerminalReporter reporter = new TerminalReporter();

        String sessionID = "test_oom_protection_001";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        // 发起一个会导致读取大文件的恶意任务
        String prompt = """
            请帮我执行以下三个步骤：
            1. 使用 bash 执行 echo "开始排查日志"
            2. 使用 read_file 工具读取当前目录下的巨大文件 mock_log.txt
            3. 使用 bash 执行 date 命令获取当前时间，并告诉我任务全部完成。
            """;

        session.append(new Message(Role.USER, prompt));

        try {
            engine.run(session, reporter);
        } catch (Exception e) {
            log.error("引擎运行崩溃: {}", e.getMessage(), e);
        }

        log.info("会话已结束。");
    }
}
