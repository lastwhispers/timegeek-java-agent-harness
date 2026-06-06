package com.lastwhispers.harness.ch05.schema;

import com.alibaba.fastjson2.annotation.JSONField;

public class ToolCall {
    @JSONField(name = "id")
    private String id;

    @JSONField(name = "name")
    private String name;

    @JSONField(name = "arguments")
    private String arguments;

    public ToolCall() {}

    public ToolCall(String id, String name, String arguments) {
        this.id = id;
        this.name = name;
        this.arguments = arguments;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getArguments() { return arguments; }
    public void setArguments(String arguments) { this.arguments = arguments; }

    @Override
    public String toString() {
        return "ToolCall{id='" + id + "', name='" + name + "', args=" + arguments + "}";
    }
}
