package com.lastwhispers.harness.ch18.tools;

import com.lastwhispers.harness.ch18.schema.ToolDefinition;

// BaseTool 是所有具体工具必须实现的通用接口
public interface BaseTool {

    // Name 返回工具的全局唯一名称 (大模型通过这个名字调用它)
    String name();

    // Definition 返回用于提交给大模型的工具元信息和参数 JSON Schema
    ToolDefinition definition();

    // Execute 接收大模型吐出的 JSON 参数，执行具体业务逻辑
    // 注意：参数是 JSON 字符串，反序列化由各个具体工具内部自行处理
    String execute(String args) throws Exception;
}
