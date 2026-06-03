package com.kaisui.harness.ch04;

import com.kaisui.harness.ch04.engine.AgentEngine;
import com.kaisui.harness.ch04.provider.LLMProvider;
import com.kaisui.harness.ch04.schema.Message;
import com.kaisui.harness.ch04.schema.ToolCall;
import com.kaisui.harness.ch04.schema.ToolDefinition;
import com.kaisui.harness.ch04.schema.ToolResult;
import com.kaisui.harness.ch04.tools.Registry;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

// 升级版 Mock Provider — 区分 Thinking Phase 和 Action Phase
@Slf4j
class MockProvider implements LLMProvider {
    private int turn = 0;

    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools) throws Exception {
        // Phase 1: Thinking 阶段 — 工具列表为空，输出纯文本思考过程
        if (availableTools == null || availableTools.isEmpty()) {
            return Message.assistant("【推理中】目标是检查文件。我不能直接盲猜，我需要先调用 bash 工具执行 ls 命令，看看当前目录下有什么，然后再做定夺。");
        }

        // Phase 2: Action 阶段 — 工具已挂载，顺着 Thinking 的逻辑采取行动
        turn++;
        if (turn == 1) {
            log.info("[MockProvider] Action Turn 1: 返回工具调用 (bash ls -la)");
            return Message.assistantWithToolCalls(List.of(
                    new ToolCall("call_123", "bash", "{\"command\": \"ls -la\"}")
            ));
        }

        log.info("[MockProvider] Action Turn 2: 返回最终文本回复");
        return Message.assistant("根据工具返回的结果，我看到了 main.go，任务圆满完成！");
    }
}

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
        String workDir = System.getProperty("user.dir");

        // 默认使用 MockProvider，设置 DASHSCOPE_MODEL 环境变量切换为真实百炼模型
        LLMProvider provider;
        String dashModel = System.getenv("DASHSCOPE_MODEL");
        if (dashModel != null && !dashModel.isEmpty()) {
            log.info("[Main] 使用 DashScope 模型: {}", dashModel);
            provider = new com.kaisui.harness.ch04.provider.DashScopeProvider(dashModel);
        } else {
            log.info("[Main] 使用 MockProvider (设置 DASHSCOPE_MODEL 环境变量启用真实模型)");
            provider = new MockProvider();
        }

        Registry registry = new MockRegistry();

        AgentEngine engine = new AgentEngine(provider, registry, workDir, true);

        engine.run("帮我检查当前目录的文件");
    }
}
