package com.kaisui.harness.ch02;

import com.kaisui.harness.ch02.engine.AgentEngine;
import com.kaisui.harness.ch02.provider.LLMProvider;
import com.kaisui.harness.ch02.schema.*;
import com.kaisui.harness.ch02.tools.Registry;
import lombok.extern.slf4j.Slf4j;

import java.util.Collections;
import java.util.List;

// 模拟 LLM 大模型 Provider
@Slf4j
class MockProvider implements LLMProvider {
    private int turn = 0;

    // 模拟大模型的响应：第一轮请求执行 bash，第二轮输出最终结果
    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools) {
        turn++;
        if (turn == 1) {
            log.info("[MockProvider] Turn 1: 返回工具调用 (bash ls -la)");
            return Message.assistantWithToolCalls(List.of(
                    new ToolCall("call_123", "bash", "{\"command\": \"ls -la\"}")
            ));
        }
        log.info("[MockProvider] Turn 2: 返回最终文本回复");
        return Message.assistant("我看到了文件列表，里面包含 main.go，任务完成！");
    }
}

// 模拟 Tool Registry
@Slf4j
class MockRegistry implements Registry {
    @Override
    public List<ToolDefinition> getAvailableTools() {
        return Collections.emptyList();
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
        String workDir = System.getProperty("user.dir");

        LLMProvider provider = new MockProvider();
        Registry registry = new MockRegistry();

        AgentEngine engine = new AgentEngine(provider, registry, workDir);

        engine.run("帮我检查当前目录的文件");
    }
}
