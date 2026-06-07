package com.lastwhispers.harness.ch16;

import com.lastwhispers.harness.ch16.context.Session;
import com.lastwhispers.harness.ch16.context.SessionManager;
import com.lastwhispers.harness.ch16.engine.AgentEngine;
import com.lastwhispers.harness.ch16.engine.TerminalReporter;
import com.lastwhispers.harness.ch16.provider.DashScopeProvider;
import com.lastwhispers.harness.ch16.provider.LLMProvider;
import com.lastwhispers.harness.ch16.schema.Message;
import com.lastwhispers.harness.ch16.schema.Role;
import com.lastwhispers.harness.ch16.tools.*;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

/**
 * 【修改】构造一个必将诱发死循环的测试，验证 Reminder 注入机制
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

        // 关闭 Plan 模式，让它在死胡同里专注地展示挣扎过程
        AgentEngine engine = new AgentEngine(llmProvider, registry, false, false);
        TerminalReporter reporter = new TerminalReporter();

        String sessionID = "test_doom_loop_001";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        // 这是一个精心设计的陷阱指令：
        // 声称文件系统不稳定会报 File Not Found，诱导模型用相同参数反复重试 read_file，
        // 从而触发 ReminderInjector 的死循环检测与干预机制。
        String prompt = """
                帮我读取当前目录下的 secret_key.txt。
                注意：我们的文件系统现在非常不稳定，经常报 File Not Found。
                如果报错了，请你【千万不要改变参数】，直接原样再次调用 read_file 尝试，直到成功或连续重试 5 次为止。
                """;

        log.info("\n>>> 启动死循环干预测试...");

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
