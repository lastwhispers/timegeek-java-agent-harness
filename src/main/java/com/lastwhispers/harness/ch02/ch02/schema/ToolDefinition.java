package com.lastwhispers.harness.ch02.ch02.schema;

import com.alibaba.fastjson2.annotation.JSONField;

public class ToolDefinition {
    @JSONField(name = "name")
    private String name;

    @JSONField(name = "description")
    private String description;

    @JSONField(name = "input_schema")
    private Object inputSchema;

    public ToolDefinition() {}

    public ToolDefinition(String name, String description, Object inputSchema) {
        this.name = name;
        this.description = description;
        this.inputSchema = inputSchema;
    }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public Object getInputSchema() { return inputSchema; }
    public void setInputSchema(Object inputSchema) { this.inputSchema = inputSchema; }
}
