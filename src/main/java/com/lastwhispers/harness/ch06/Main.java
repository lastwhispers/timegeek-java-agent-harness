package com.lastwhispers.harness.ch06;

import com.lastwhispers.harness.ch06.engine.AgentEngine;
import com.lastwhispers.harness.ch06.provider.DashScopeProvider;
import com.lastwhispers.harness.ch06.provider.LLMProvider;
import com.lastwhispers.harness.ch06.tools.BashTool;
import com.lastwhispers.harness.ch06.tools.ReadFileTool;
import com.lastwhispers.harness.ch06.tools.Registry;
import com.lastwhispers.harness.ch06.tools.RegistryImpl;
import com.lastwhispers.harness.ch06.tools.WriteFileTool;
import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

// 组装运行
@Slf4j
public class Main {
    public static void main(String[] args) {
        // 加载项目根目录的 .env 文件
        Dotenv.load();

        // 1. 获取工作区物理边界
        String workDir = System.getProperty("user.dir")+"/workspace/ch06";

        // 2. 初始化真实的大脑 (API_KEY 和 MODEL 由 Provider 内部自动加载)
        LLMProvider llmProvider = new DashScopeProvider();

        // 3. 初始化真实的 Tool Registry
        Registry registry = new RegistryImpl();

        // 4. 将所有真实工具挂载到注册表中
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));

        // 5. 实例化核心引擎，关闭思考阶段 (EnableThinking = false) 以加快速度
        AgentEngine engine = new AgentEngine(llmProvider, registry, workDir, false);

        // 6. 下发一个必须通过真实工具才能完成的任务
        String prompt = """
                请帮我执行以下操作：
                1. 用 bash 查看当前系统的 Java 版本。
                2. 帮我写一个简单的 HelloWorld.java 文件，输出 "Hello, timegeek-java-agent-harness!"。
                3. 用 bash 编译并运行这个 Java 文件，确认它能正常工作。
                """;

        try {
            engine.run(prompt);
        } catch (Exception e) {
            log.error("引擎运行崩溃: {}", e.getMessage());
        }
    }
}
