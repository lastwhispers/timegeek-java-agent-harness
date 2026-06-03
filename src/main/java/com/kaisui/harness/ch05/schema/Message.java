package com.kaisui.harness.ch05.schema;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;

import java.util.List;

// 统一的消息与工具调用类型定义
@JsonInclude(JsonInclude.Include.NON_NULL)
@AllArgsConstructor
public class Message {
    @JsonProperty("role")
    private Role role;

    @JsonProperty("content")
    private String content;

    @JsonProperty("tool_calls")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private List<ToolCall> toolCalls;

    @JsonProperty("tool_call_id")
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private String toolCallId;

    public Message() {}

    public Message(Role role, String content) {
        this.role = role;
        this.content = content;
    }

    public Message(Role role, String content, String toolCallId) {
        this.role = role;
        this.content = content;
        this.toolCallId = toolCallId;
    }

    public static Message system(String content) {
        return new Message(Role.SYSTEM, content);
    }

    public static Message user(String content) {
        return new Message(Role.USER, content);
    }

    public static Message assistant(String content) {
        return new Message(Role.ASSISTANT, content);
    }

    public static Message assistantWithToolCalls(List<ToolCall> toolCalls) {
        Message m = new Message();
        m.role = Role.ASSISTANT;
        m.toolCalls = toolCalls;
        return m;
    }

    public static Message toolResult(String toolCallId, String content) {
        Message m = new Message();
        m.role = Role.TOOL;
        m.toolCallId = toolCallId;
        m.content = content;
        return m;
    }

    public Role getRole() { return role; }
    public void setRole(Role role) { this.role = role; }

    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }

    public List<ToolCall> getToolCalls() { return toolCalls; }
    public void setToolCalls(List<ToolCall> toolCalls) { this.toolCalls = toolCalls; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public boolean hasToolCalls() { return toolCalls != null && !toolCalls.isEmpty(); }

    @Override
    public String toString() {
        if (hasToolCalls()) {
            return "Message{role=" + role + ", toolCalls=" + toolCalls + "}";
        }
        if (toolCallId != null) {
            return "Message{role=" + role + ", toolCallId='" + toolCallId + "', content=" + content + "}";
        }
        return "Message{role=" + role + ", content=" + content + "}";
    }
}
