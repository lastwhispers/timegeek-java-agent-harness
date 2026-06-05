package com.kaisui.harness.ch05;

import com.kaisui.harness.ch05.engine.AgentEngine;
import com.kaisui.harness.ch05.provider.DashScopeProvider;
import com.kaisui.harness.ch05.provider.LLMProvider;
import com.kaisui.harness.ch05.tools.ReadFileTool;
import com.kaisui.harness.ch05.tools.Registry;
import com.kaisui.harness.ch05.tools.RegistryImpl;
import com.kaisui.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

// 组装运行
@Slf4j
public class Main {
    public static void main(String[] args) {
        // 加载项目根目录的 .env 文件
        Dotenv.load();

        // 确保设置了 DASHSCOPE_API_KEY（优先环境变量，其次系统属性）
        String apiKey = System.getProperty("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            log.error("请设置 DASHSCOPE_API_KEY 环境变量");
            return;
        }

        // 1. 获取工作区物理边界
        String workDir = System.getProperty("user.dir");

        // 2. 初始化真实的大脑 (DashScope 智谱 GLM-4.5，OpenAI 兼容接口)
        LLMProvider llmProvider = new DashScopeProvider("qwen3.7-max");

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
