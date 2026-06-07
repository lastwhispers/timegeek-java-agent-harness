package com.lastwhispers.harness.ch19.tools;

import com.lastwhispers.harness.ch19.observability.Trace;
import com.lastwhispers.harness.ch19.schema.ToolCall;
import com.lastwhispers.harness.ch19.schema.ToolDefinition;
import com.lastwhispers.harness.ch19.schema.ToolResult;

import java.util.List;

// 工具注册与分发接口
// Registry 定义了工具的注册与分发执行接口
public interface Registry {
    /**
     * 将一个工具注册到系统中
     */
    void register(BaseTool tool);

    /**
     * 挂载一个中间件到拦截链中。
     * 中间件在工具实际执行前运行，可返回 (false, reason) 拒绝执行。
     */
    void use(MiddlewareFunc middleware);

    /**
     * 返回当前系统挂载的所有可用工具的 Schema
     */
    List<ToolDefinition> getAvailableTools();

    /**
     * 实际执行模型请求的工具，并返回结果
     */
    default ToolResult execute(ToolCall call) {
        return execute(call, null);
    }

    /**
     * 实际执行模型请求的工具，并返回结果。
     *
     * @param call       工具调用
     * @param parentSpan 父 Span，用于链路追踪埋点
     */
    ToolResult execute(ToolCall call, Trace.Span parentSpan);

    /**
     * 中间件函数式接口。
     * 返回 allowed=false 时，拒绝执行并返回拦截原因。
     */
    @FunctionalInterface
    interface MiddlewareFunc {
        MiddlewareResult apply(ToolCall call);
    }

    record MiddlewareResult(boolean allowed, String rejectReason) {
        public static MiddlewareResult allow() {
            return new MiddlewareResult(true, null);
        }
        public static MiddlewareResult deny(String reason) {
            return new MiddlewareResult(false, reason);
        }
    }
}
