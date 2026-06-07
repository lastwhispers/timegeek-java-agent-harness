package com.lastwhispers.harness.ch12;

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

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@Slf4j
public class Main {
    public static void main(String[] args) throws InterruptedException {
        Dotenv.load();
        String workDir = System.getProperty("user.dir") + "/workspace";
        LLMProvider llmProvider = new DashScopeProvider();

        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(llmProvider, registry, false);
        TerminalReporter reporter = new TerminalReporter();

        CountDownLatch latch = new CountDownLatch(2);

        // ================= 并发场景 1：Session A =================
        Thread sessionAThread = new Thread(() -> {
            try {
                Session sessionA = SessionManager.INSTANCE.getOrCreate("chat_front_001", workDir);

                log.info("\n>>> [Session A / Turn 1]: 帮我看看 README.md 里记录了什么？");
                sessionA.append(new Message(Role.USER, "帮我看看 README.md 里记录了什么？"));
                engine.run(sessionA, reporter);

                // 塞入废话，刷掉记忆
                for (int i = 0; i < 6; i++) {
                    sessionA.append(new Message(Role.USER, "这只是一句闲聊占位符。"));
                    sessionA.append(new Message(Role.ASSISTANT, "好的，收到闲聊。"));
                }

                log.info("\n>>> [Session A / Turn 2]: 请直接告诉我，刚才第一轮你查到了什么？不准调用工具！");
                sessionA.append(new Message(Role.USER, "请直接告诉我，刚才第一轮你查到了什么？不准调用工具！"));
                engine.run(sessionA, reporter);
            } catch (Exception e) {
                log.error("Session A 运行崩溃: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        // ================= 并发场景 2：Session B =================
        Thread sessionBThread = new Thread(() -> {
            try {
                // 延迟启动，模拟并发时序
                TimeUnit.SECONDS.sleep(1);

                Session sessionB = SessionManager.INSTANCE.getOrCreate("chat_back_002", workDir);

                log.info("\n>>> [Session B]: 别人查到了一个密钥，你这里能看到吗？不准调用工具！");
                sessionB.append(new Message(Role.USER, "别人查到了一个密钥，你这里能看到吗？不准调用工具！"));
                engine.run(sessionB, reporter);
            } catch (Exception e) {
                log.error("Session B 运行崩溃: {}", e.getMessage());
            } finally {
                latch.countDown();
            }
        });

        sessionAThread.start();
        sessionBThread.start();

        latch.await();
        log.info("所有会话已结束。");
    }
}
