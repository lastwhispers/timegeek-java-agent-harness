package com.lastwhispers.harness.ch19.observability;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Instant;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 轻量级链路追踪系统。
 * Span 树通过显式传入父 Span 构建，支持并发工具调用下的正确父子关系。
 */
@Slf4j
public class Trace {

    /**
     * Span 代表链路追踪中的一个时间跨度和操作节点。
     */
    public static class Span {

        @JSONField(name = "name")
        private final String name;

        @JSONField(name = "start_time")
        private String startTime;

        @JSONField(name = "end_time")
        private String endTime;

        @JSONField(name = "duration_ms")
        private long durationMs;

        @JSONField(name = "attributes")
        private final Map<String, Object> attributes = new HashMap<>();

        @JSONField(name = "children")
        private final List<Span> children = new ArrayList<>();

        Span(String name) {
            this.name = name;
            this.startTime = Instant.now().toString();
        }

        public String getName() { return name; }
        public Map<String, Object> getAttributes() { return attributes; }

        /**
         * 为当前 Span 记录关键元数据。
         */
        public synchronized void addAttribute(String key, Object value) {
            attributes.put(key, value);
        }

        /**
         * 结束当前 Span，计算耗时。
         */
        public void endSpan() {
            this.endTime = Instant.now().toString();
            this.durationMs = Duration.between(
                Instant.parse(startTime),
                Instant.parse(endTime)
            ).toMillis();
        }
    }

    /**
     * 开启一个新的 Span。
     *
     * @param parent 父 Span，如果为 null 则创建根 Span（无 children 挂载）
     * @param name   Span 名称
     * @return 新创建的 Span，已设置 startTime
     */
    public static Span startSpan(Span parent, String name) {
        Span span = new Span(name);
        if (parent != null) {
            parent.children.add(span);
        }
        return span;
    }

    /**
     * 当整个根 Span 结束时，将其序列化并保存为本地 JSON 文件。
     */
    public static void exportToFile(Span rootSpan, String workDir, String sessionID) {
        try {
            Path traceDir = Paths.get(workDir, ".claw", "traces");
            Files.createDirectories(traceDir);

            String filename = String.format("trace_%s_%d.json", sessionID, System.nanoTime());
            Path filePath = traceDir.resolve(filename);

            String json = JSON.toJSONString(rootSpan);
            Files.writeString(filePath, json);

            log.info("[Tracing] 本次任务的执行回放链路已保存至: {}", filePath);
        } catch (IOException e) {
            log.error("[Tracing] 导出 Trace 文件失败", e);
        }
    }
}
