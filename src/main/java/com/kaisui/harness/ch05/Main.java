package com.kaisui.harness.ch05;

import com.kaisui.harness.ch05.engine.AgentEngine;
import com.kaisui.harness.ch05.provider.LLMProvider;
import com.kaisui.harness.ch05.schema.ToolCall;
import com.kaisui.harness.ch05.schema.ToolDefinition;
import com.kaisui.harness.ch05.schema.ToolResult;
import com.kaisui.harness.ch05.tools.Registry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

// 模拟 Tool Registry
@Slf4j
class MockRegistry implements com.kaisui.harness.ch05.tools.Registry {
    @Override
    public List<com.kaisui.harness.ch05.schema.ToolDefinition> getAvailableTools() {
        // Phase 2 需要检测到工具，返回 bash 工具定义
        return List.of(new ToolDefinition("bash", "Execute a shell command", null));
    }

    @Override
    public com.kaisui.harness.ch05.schema.ToolResult execute(ToolCall call) {
        log.info("[MockRegistry] 执行工具: {} , 参数: {}", call.getName(), call.getArguments());
        return new ToolResult(
                call.getId(),
                "-rw-r--r--  1 user group  234 Oct 24 10:00 main.go\n",
                false
        );
    }
}

// 组装运行
@Slf4j
public class Main {
    public static void main(String[] args) {
        String workDir = System.getProperty("user.dir");

        // 默认使用 MockProvider，设置 DASHSCOPE_MODEL 环境变量切换为真实百炼模型
        LLMProvider provider;
        String dashModel = System.getenv("DASHSCOPE_MODEL");
        log.info("[Main] 使用 DashScope 模型: {}", dashModel);
        provider = new com.kaisui.harness.ch05.provider.DashScopeProvider(dashModel);

        Registry registry = new com.kaisui.harness.ch05.MockRegistry();

        com.kaisui.harness.ch05.engine.AgentEngine engine = new AgentEngine(provider, registry, workDir, true);

        engine.run("帮我检查当前目录的文件");
    }
}
