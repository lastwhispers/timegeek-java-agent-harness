package com.lastwhispers.harness.ch20.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lastwhispers.harness.ch20.engine.AgentRunner;
import com.lastwhispers.harness.ch20.engine.Reporter;
import com.lastwhispers.harness.ch20.schema.ToolDefinition;
import lombok.extern.slf4j.Slf4j;

/**
 * 子智能体工具：允许主智能体拉起一个受限的、只读的子循环来执行深度探索任务。
 */
@Slf4j
public class SubagentTool implements BaseTool {

    private final AgentRunner runner;
    private final Registry readOnlyRegistry;
    private final Reporter reporter;

    public SubagentTool(AgentRunner runner, Registry readOnlyRegistry, Reporter reporter) {
        this.runner = runner;
        this.readOnlyRegistry = readOnlyRegistry;
        this.reporter = reporter;
    }

    @Override
    public String name() {
        return "spawn_subagent";
    }

    @Override
    public ToolDefinition definition() {
        JSONObject inputSchema = new JSONObject();
        inputSchema.put("type", "object");

        JSONObject properties = new JSONObject();
        JSONObject taskPromptProp = new JSONObject();
        taskPromptProp.put("type", "string");
        taskPromptProp.put("description", "给子智能体下达的明确探索指令。");
        properties.put("task_prompt", taskPromptProp);

        inputSchema.put("properties", properties);
        inputSchema.put("required", new String[]{"task_prompt"});

        return new ToolDefinition(
            name(),
            "派出一个专门用于深度探索（Exploration）的子智能体。当你需要阅读大量代码、跨文件查找逻辑时请调用此工具。它在探索完毕后，会给你返回一份极度精炼的摘要报告。",
            inputSchema.toJSONString()
        );
    }

    @Override
    public String execute(String argsJson) {
        JSONObject parsed = JSON.parseObject(argsJson);
        String taskPrompt = parsed.getString("task_prompt");
        if (taskPrompt == null || taskPrompt.isBlank()) {
            return "解析参数失败: 缺少必需的 'task_prompt' 字段";
        }

        log.info("[Subagent] 主 Agent 发起委派！正在拉起探路者: [{}]...", taskPrompt);

        String summary = runner.runSub(taskPrompt, readOnlyRegistry, reporter);

        log.info("[Subagent] 子智能体任务结束。报告返回给主干...");

        return "【子智能体探索报告】:\n" + summary;
    }
}
