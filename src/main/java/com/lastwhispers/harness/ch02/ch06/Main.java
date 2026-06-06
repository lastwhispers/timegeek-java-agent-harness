package com.lastwhispers.harness.ch02.ch06;

import com.lastwhispers.harness.ch02.ch06.engine.AgentEngine;
import com.lastwhispers.harness.ch02.ch06.provider.DashScopeProvider;
import com.lastwhispers.harness.ch02.ch06.provider.LLMProvider;
import com.lastwhispers.harness.ch02.ch06.tools.ReadFileTool;
import com.lastwhispers.harness.ch02.ch06.tools.Registry;
import com.lastwhispers.harness.ch02.ch06.tools.RegistryImpl;
import com.lastwhispers.harness.ch02.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

// 组装运行
@Slf4j
public class Main {
    public static void main(String[] args) {
        // 加载项目根目录的 .env 文件
        Dotenv.load();

        // 1. 获取工作区物理边界
        String workDir = System.getProperty("user.dir");

        // 2. 初始化真实的大脑 (API_KEY 和 MODEL 由 Provider 内部自动加载)
        LLMProvider llmProvider = new DashScopeProvider();

        // 3. 初始化真实的 Tool Registry
        Registry registry = new RegistryImpl();

        // 4. 将真实的 ReadFile 工具挂载到注册表中
        ReadFileTool readFileTool = new ReadFileTool(workDir);
        registry.register(readFileTool);

        // 5. 实例化核心引擎，由于任务简单，关闭思考阶段 (EnableThinking = false) 以加快速度
        AgentEngine engine = new AgentEngine(llmProvider, registry, workDir, false);

        // 6. 下发一个必须通过真实工具才能完成的任务
        String prompt = "请调用工具读取一下当前工作区目录下 hello.txt 文件的内容，并用一句话向我总结它说了什么。";

        try {
            engine.run(prompt);
        } catch (Exception e) {
            log.error("引擎运行崩溃: {}", e.getMessage());
        }
    }
}
