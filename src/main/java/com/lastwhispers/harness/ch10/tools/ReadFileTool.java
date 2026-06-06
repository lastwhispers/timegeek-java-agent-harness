package com.lastwhispers.harness.ch10.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.lastwhispers.harness.ch10.schema.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

public class ReadFileTool implements BaseTool {

    // 将引擎的 WorkDir 注入给工具，限制它只能在此目录及其子目录下操作
    private static final int MAX_LEN = 8000;

    private final String workDir;

    public ReadFileTool(String workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "read_file";
    }

    @Override
    public ToolDefinition definition() {
        // 向大模型清晰地描述这个工具的用途和参数格式
        // 遵循 JSON Schema 规范定义参数
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "要读取的文件路径，如 cmd/claw/main.go"
                        )
                ),
                "required", List.of("path")
        );
        return new ToolDefinition(
                name(),
                "读取指定路径的文件内容。请提供相对工作区的路径。",
                inputSchema
        );
    }

    // 内部定义用于反序列化的结构体
    private static class ReadFileArgs {
        @JSONField(name = "path")
        private String path;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
    }

    @Override
    public String execute(String args) throws Exception {
        // 1. 延迟解析：将大模型传过来的 JSON 参数解析为强类型结构体
        //    返回 error 会被 Registry 捕获并传给大模型，模型会知道自己 JSON 格式写错了
        ReadFileArgs input = JSON.parseObject(args, ReadFileArgs.class);

        // 2. 拼接绝对路径 (注意：生产环境中需要做路径穿越检测防范，防止 ../../etc/passwd)
        Path fullPath = Path.of(workDir, input.getPath());

        // 3. 执行物理 IO 操作
        String content;
        try {
            content = Files.readString(fullPath);
        } catch (IOException e) {
            throw new IOException("打开文件失败: " + e.getMessage(), e);
        }

        // 4. 【核心防线】长度截断保护
        //    为了防止大模型读取几百 MB 的日志文件导致 Context 瞬间爆炸 (OOM)，
        //    在工具内部直接进行物理截断。
        if (content.length() > MAX_LEN) {
            return content.substring(0, MAX_LEN) +
                    "\n\n...[由于内容过长，已被系统截断至前 " + MAX_LEN + " 字节]...";
        }

        return content;
    }
}
