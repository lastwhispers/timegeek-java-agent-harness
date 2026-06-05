package com.lastwhispers.harness.harness.ch04;

import com.lastwhispers.harness.harness.ch04.engine.AgentEngine;
import com.lastwhispers.harness.harness.ch04.provider.DashScopeProvider;
import com.lastwhispers.harness.harness.ch04.provider.LLMProvider;
import com.lastwhispers.harness.harness.ch04.schema.ToolCall;
import com.lastwhispers.harness.harness.ch04.schema.ToolDefinition;
import com.lastwhispers.harness.harness.ch04.schema.ToolResult;
import com.lastwhispers.harness.harness.ch04.tools.Registry;
import com.lastwhispers.harness.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.util.List;


// 模拟 Tool Registry
@Slf4j
class MockRegistry implements Registry {
    @Override
    public List<ToolDefinition> getAvailableTools() {
        // Phase 2 需要检测到工具，返回 bash 工具定义
        return List.of(new ToolDefinition("bash", "Execute a shell command", null));
    }

    @Override
    public ToolResult execute(ToolCall call) {
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
        // 加载 .env
        Dotenv.load();

        String workDir = System.getProperty("user.dir");

        // 初始化真实的大脑 (API_KEY 和 MODEL 由 Provider 内部自动加载)
        LLMProvider provider = new DashScopeProvider();

        Registry registry = new MockRegistry();

        AgentEngine engine = new AgentEngine(provider, registry, workDir, true);

        engine.run("帮我检查当前目录的文件");
    }
}
