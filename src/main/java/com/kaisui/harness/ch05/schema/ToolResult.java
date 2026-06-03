package com.kaisui.harness.ch05.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolResult {
    @JsonProperty("tool_call_id")
    private String toolCallId;

    @JsonProperty("output")
    private String output;

    @JsonProperty("is_error")
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
