package com.lastwhispers.harness.ch08.tools;

import com.lastwhispers.harness.ch08.schema.ToolCall;
import com.lastwhispers.harness.ch08.schema.ToolDefinition;
import com.lastwhispers.harness.ch08.schema.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RegistryImpl implements Registry {

    private final Map<String, BaseTool> tools = new HashMap<>();

    @Override
    public void register(BaseTool tool) {
        String name = tool.name();
        if (tools.containsKey(name)) {
            log.warn("[Warning] 工具 '{}' 已经被注册，将被覆盖。", name);
        }
        tools.put(name, tool);
        log.info("[Registry] 成功挂载工具: {}", name);
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (BaseTool tool : tools.values()) {
            defs.add(tool.definition());
        }
        return defs;
    }

    @Override
    public ToolResult execute(ToolCall call) {
        // 1. 路由查找：如果在注册表中找不到该工具，这是模型产生了幻觉，直接向模型抛出错误
        BaseTool tool = tools.get(call.getName());
        if (tool == null) {
            String errMsg = String.format("Error: 系统中不存在名为 '%s' 的工具。", call.getName());
            return new ToolResult(call.getId(), errMsg, true);
        }
        // 2. 执行工具逻辑：将原始的 JSON 字节流直接丢给具体工具
        // 3. 封装结果：将执行结果或底层物理错误封装后返回给 Main Loop
        try {
            String output = tool.execute(call.getArguments());
            return new ToolResult(call.getId(), output, false);
        } catch (Exception e) {
            return new ToolResult(call.getId(),
                    String.format("Error executing %s: %s", call.getName(), e.getMessage()), true);
        }
    }
}
