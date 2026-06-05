package com.kaisui.harness.ch02.schema;

import com.alibaba.fastjson2.annotation.JSONField;

public class ToolResult {
    @JSONField(name = "tool_call_id")
    private String toolCallId;

    @JSONField(name = "output")
    private String output;

    @JSONField(name = "is_error")
    private boolean isError;

    public ToolResult() {}

    public ToolResult(String toolCallId, String output, boolean isError) {
        this.toolCallId = toolCallId;
        this.output = output;
        this.isError = isError;
    }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getOutput() { return output; }
    public void setOutput(String output) { this.output = output; }

    public boolean isError() { return isError; }
    public void setError(boolean error) { isError = error; }
}
