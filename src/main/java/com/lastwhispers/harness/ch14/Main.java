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
 * 【修改】编写一个会诱发错误的测试指令，验证自愈机制
 */
@Slf4j
public class Main {
    public static void main(String[] args) {
        Dotenv.load();

        String workDir = System.getProperty("user.dir") + "/workspace/ch14";
        LLMProvider llmProvider = new DashScopeProvider();

        // 挂载 4 大基础工具
        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        // 关闭 Plan 模式，专注于见证它改变主意的单点纠偏过程
        ContextCompactor compactor = new ContextCompactor(80000, 6);
        AgentEngine engine = new AgentEngine(llmProvider, registry, false, false, compactor);
        TerminalReporter reporter = new TerminalReporter();

        String sessionID = "test_recovery_001";
        Session session = SessionManager.INSTANCE.getOrCreate(sessionID, workDir);

        // 这是一个巨大的陷阱指令：
        // 我们不给它查看文件的机会，直接命令它凭初始上下文去修改文件，目的是诱发 old_text 不匹配的错误。
        String prompt = """
                我当前目录下有一个 auth.go 文件。
                请修改 auth.go 中的 login 函数。
                请直接使用 edit_file 工具替换下面的代码块，将判断条件改为同时允许"admin"、"root"和"guest"这三种用户登录：

                    // 鉴权入口函数
                    func login(user string) bool {
                        // 检查用户名
                        if user == "admin" {
                            return true
                        }
                        return false
                    }
                """;

        log.info("\n>>> 启动自愈测试任务...");

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
