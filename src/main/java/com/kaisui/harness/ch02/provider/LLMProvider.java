package com.kaisui.harness.ch02.provider;

import com.kaisui.harness.ch02.schema.Message;
import com.kaisui.harness.ch02.schema.ToolDefinition;

import java.util.List;

// LLM Provider 接口定义
public interface LLMProvider {
    // // Generate 接收当前的上下文历史、可用工具列表，并发起一次大模型推理
    Message generate(List<Message> messages, List<ToolDefinition> availableTools) throws Exception;
}

