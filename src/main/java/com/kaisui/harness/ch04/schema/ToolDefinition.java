package com.kaisui.harness.ch04.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class ToolDefinition {
    @JsonProperty("name")
    private String name;

    @JsonProperty("description")
    private String description;

    @JsonProperty("input_schema")
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
