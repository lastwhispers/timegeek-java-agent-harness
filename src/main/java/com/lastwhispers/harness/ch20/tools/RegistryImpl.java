package com.lastwhispers.harness.ch20.tools;

import com.lastwhispers.harness.ch20.observability.Trace;
import com.lastwhispers.harness.ch20.schema.ToolCall;
import com.lastwhispers.harness.ch20.schema.ToolDefinition;
import com.lastwhispers.harness.ch20.schema.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
public class RegistryImpl implements Registry {

    private final Map<String, BaseTool> tools = new HashMap<>();
    private final List<MiddlewareFunc> middlewares = new ArrayList<>();

    @Override
    public void register(BaseTool tool) {
        String name = tool.name();
        if (tools.containsKey(name)) {
            log.warn("[Warning] 工具 '{}' 已经被注册，将被覆盖。", name);
        }
        tools.put(name, tool);
        log.info("[Registry] 成功挂载工具: {}", name);
    }

    @Override
    public void use(MiddlewareFunc middleware) {
        middlewares.add(middleware);
    }

    @Override
    public List<ToolDefinition> getAvailableTools() {
        List<ToolDefinition> defs = new ArrayList<>();
        for (BaseTool tool : tools.values()) {
            defs.add(tool.definition());
        }
        return defs;
    }

    @Override
    public ToolResult execute(ToolCall call, Trace.Span parentSpan) {
        // 【埋点】开启工具执行的 Span
        Trace.Span span = Trace.startSpan(parentSpan, "Tool." + call.getName());
        span.addAttribute("tool_name", call.getName());
        span.addAttribute("arguments", call.getArguments());

        try {
            // 1. 路由查找：如果在注册表中找不到该工具，这是模型产生了幻觉，直接向模型抛出错误
            BaseTool tool = tools.get(call.getName());
            if (tool == null) {
                String errMsg = String.format("Error: 系统中不存在名为 '%s' 的工具。", call.getName());
                span.addAttribute("error", errMsg);
                span.endSpan();
                return new ToolResult(call.getId(), errMsg, true);
            }

            // 2. 【核心防御】在执行底层逻辑前，依次运行所有的 Middleware
            for (MiddlewareFunc mw : middlewares) {
                MiddlewareResult result = mw.apply(call);
                if (!result.allowed()) {
                    log.info("[Registry] ⚠️ 工具 {} 被 Middleware 拦截: {}", call.getName(), result.rejectReason());
                    span.addAttribute("intercepted", true);
                    span.addAttribute("reject_reason", result.rejectReason());
                    span.endSpan();
                    return new ToolResult(call.getId(),
                            "执行被系统拦截。原因: " + result.rejectReason(), true);
                }
            }

            // 3. 执行工具逻辑（如果所有 Middleware 都放行了）
            String output = tool.execute(call.getArguments());

            // 截取输出前 100 字符放入 Trace，防止文件过度膨胀
            span.addAttribute("output_preview", truncate(output, 100));
            span.endSpan();
            return new ToolResult(call.getId(), output, false);
        } catch (Exception e) {
            span.addAttribute("error", e.getMessage());
            span.endSpan();
            return new ToolResult(call.getId(),
                    String.format("Error executing %s: %s", call.getName(), e.getMessage()), true);
        }
    }

    private static String truncate(String s, int max) {
        if (s.length() > max) {
            return s.substring(0, max) + "...";
        }
        return s;
    }
}
