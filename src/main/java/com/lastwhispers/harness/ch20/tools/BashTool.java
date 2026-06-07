package com.lastwhispers.harness.ch20.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.lastwhispers.harness.ch20.schema.ToolDefinition;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class BashTool implements BaseTool {

    private static final int MAX_LEN = 8000;
    // 【驾驭底线 1】：Time Budgeting (时间预算与超时控制)
    // 给予命令一个最大执行时间，防止大模型卡死进程
    private static final long TIMEOUT_SECONDS = 30;

    // 【安全防线】：限制在 WorkDir 下执行
    private final String workDir;

    public BashTool(String workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "bash";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "command", Map.of(
                                "type", "string",
                                "description", "要执行的 bash 命令，例如: ls -la 或 go test ./..."
                        )
                ),
                "required", List.of("command")
        );
        return new ToolDefinition(
                name(),
                "在当前工作区执行任意的 bash 命令。支持链式命令(如 &&)。返回标准输出(stdout)和标准错误(stderr)。",
                inputSchema
        );
    }

    private static class BashArgs {
        @JSONField(name = "command")
        private String command;

        public String getCommand() { return command; }
        public void setCommand(String command) { this.command = command; }
    }

    @Override
    public String execute(String args) throws Exception {
        // 延迟解析：将大模型传过来的 JSON 参数解析为强类型结构体
        BashArgs input = JSON.parseObject(args, BashArgs.class);

        // 【驾驭底线 2】：绑定执行的工作区目录，确保命令在 WorkDir 下执行
        ProcessBuilder pb = new ProcessBuilder("bash", "-c", input.getCommand());
        pb.directory(Path.of(workDir).toFile());
        // 合并 stdout 和 stderr，方便统一回传给模型
        pb.redirectErrorStream(true);

        Process process = pb.start();
        // 【驾驭底线 1】：超时控制
        boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);

        // 读取合并后的输出
        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        // 如果命令执行超时，返回警告信息让模型知晓
        if (!finished) {
            process.destroyForcibly();
            return output + "\n[警告: 命令执行超时(" + TIMEOUT_SECONDS + "s)，已被系统强制终止。如果是启动常驻服务，请尝试将其转入后台。]";
        }

        int exitCode = process.exitValue();
        String outputStr = output.toString();

        // 【驾驭底线 3】：错误原样回传 (Self-Correction 自愈机制)
        // 当 bash 报错时，不能抛出 Java Exception 阻断程序！
        // 必须把错误信息拼接成字符串返回，利用大模型的自纠错能力自己分析报错
        if (exitCode != 0) {
            return String.format("执行报错: exit code %d\n输出:\n%s", exitCode, outputStr);
        }

        // 如果没有终端输出（比如仅仅执行了 mkdir），给模型一个明确的执行成功的反馈
        if (outputStr.isBlank()) {
            return "命令执行成功，无终端输出。";
        }

        // 【驾驭底线 4】：长度截断保护 (防 OOM)
        if (outputStr.length() > MAX_LEN) {
            return outputStr.substring(0, MAX_LEN) +
                    "\n\n...[终端输出过长，已截断至前 " + MAX_LEN + " 字节]...";
        }

        return outputStr;
    }
}
