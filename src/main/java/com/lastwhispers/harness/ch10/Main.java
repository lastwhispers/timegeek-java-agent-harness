package com.lastwhispers.harness.ch10;

import com.lastwhispers.harness.ch10.engine.AgentEngine;
import com.lastwhispers.harness.ch10.engine.TerminalReporter;
import com.lastwhispers.harness.ch10.provider.DashScopeProvider;
import com.lastwhispers.harness.ch10.provider.LLMProvider;
import com.lastwhispers.harness.ch10.tools.*;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

// 组装运行
@Slf4j
public class Main {
    public static void main(String[] args) {
        // 加载项目根目录的 .env 文件
        Dotenv.load();

        // 1. 获取工作区物理边界
        String workDir = System.getProperty("user.dir")+"/workspace";

        // 2. 初始化真实的大脑 (API_KEY 和 MODEL 由 Provider 内部自动加载)
        LLMProvider llmProvider = new DashScopeProvider();

        // 3. 初始化真实的 Tool Registry
        Registry registry = new RegistryImpl();

        // 4. 将所有真实工具挂载到注册表中
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        // 5. 实例化核心引擎，开启慢思考，促使大模型一次性规划出并行的工具调用
        AgentEngine engine = new AgentEngine(llmProvider, registry, workDir, false);

        // 6. 创建终端报告器
        TerminalReporter reporter = new TerminalReporter();

        // 7. 下发一个必须通过真实工具才能完成的任务
        String prompt = """
                	我需要在当前目录下新建一个 Ping.java，提供一个简单的 http ping 接口。
	                写完之后，帮我把代码用 git 提交一下。
                """;

        try {
            engine.run(prompt, reporter);
        } catch (Exception e) {
            log.error("引擎运行崩溃: {}", e.getMessage());
        }
    }
}
