package com.kaisui.harness.ch05.schema;

import com.alibaba.fastjson2.annotation.JSONField;
import lombok.AllArgsConstructor;

import java.util.List;

// 统一的消息与工具调用类型定义
@AllArgsConstructor
public class Message {
    @JSONField(name = "role")
    private Role role;

    @JSONField(name = "content")
    private String content;

    @JSONField(name = "tool_calls")
    private List<ToolCall> toolCalls;

    @JSONField(name = "tool_call_id")
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
