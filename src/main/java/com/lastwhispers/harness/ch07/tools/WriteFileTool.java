package com.lastwhispers.harness.ch07.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.lastwhispers.harness.ch07.schema.ToolDefinition;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class WriteFileTool implements BaseTool {

    // 【安全防线】：限制在 WorkDir 下执行，防止大模型修改系统级文件
    private final String workDir;

    public WriteFileTool(String workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "write_file";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "要写入的文件路径，如 src/main.go"
                        ),
                        "content", Map.of(
                                "type", "string",
                                "description", "要写入的完整文件内容"
                        )
                ),
                "required", List.of("path", "content")
        );
        return new ToolDefinition(
                name(),
                "创建或覆盖写入一个文件。如果目录不存在会自动创建。请提供相对于工作区的相对路径。",
                inputSchema
        );
    }

    private static class WriteFileArgs {
        @JSONField(name = "path")
        private String path;

        @JSONField(name = "content")
        private String content;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }

        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }

    @Override
    public String execute(String args) throws Exception {
        // 延迟解析：将大模型传过来的 JSON 参数解析为强类型结构体
        WriteFileArgs input = JSON.parseObject(args, WriteFileArgs.class);

        // 【安全防线】：拼接绝对路径，限制在 WorkDir 下执行，防止大模型修改系统级文件
        Path fullPath = Path.of(workDir, input.getPath());

        // 自动创建缺失的父级目录
        Path parentDir = fullPath.getParent();
        if (parentDir != null && !Files.exists(parentDir)) {
            Files.createDirectories(parentDir);
        }

        // 写入文件内容，权限设为 0644 (owner rw, group/others r)
        Files.writeString(fullPath, input.getContent());

        return String.format("成功将内容写入到文件: %s", input.getPath());
    }
}
