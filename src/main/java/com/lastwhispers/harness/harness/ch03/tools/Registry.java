package com.lastwhispers.harness.harness.ch03.tools;

import com.lastwhispers.harness.harness.ch03.schema.ToolCall;
import com.lastwhispers.harness.harness.ch03.schema.ToolDefinition;
import com.lastwhispers.harness.harness.ch03.schema.ToolResult;

import java.util.List;

// 工具注册与分发接口
// Registry 定义了工具的注册与分发执行接口
public interface Registry {
    // GetAvailableTools 返回当前系统挂载的所有可用工具的 Schema
    List<ToolDefinition> getAvailableTools();

    // Execute 实际执行模型请求的工具，并返回结果
    ToolResult execute(ToolCall call);
}
