package com.lastwhispers.harness.ch19.context;

/**
 * RecoveryManager 负责在工具执行失败时，根据报错特征分析并注入恢复建议。
 */
public class RecoveryManager {

    /**
     * 接收原始报错，匹配已知特征模式，返回增强后的报错信息。
     */
    public String analyzeAndInject(String toolName, String rawError) {
        String hint = null;
        String lowerError = rawError.toLowerCase();

        switch (toolName) {
            case "edit_file":
                if (rawError.contains("在文件中未找到 old_text")) {
                    hint = "你提供的 old_text 与文件当前内容不一致，或者缺少必要的缩进。请先使用 `read_file` 工具重新读取该文件，获取最新、准确的内容后，再重新发起编辑。";
                } else if (rawError.contains("匹配到了多处") || rawError.contains("模糊匹配到了")) {
                    hint = "你的 old_text 不够具体，命中了多个相同代码块。请在 old_text 中增加上下相邻的几行代码，以确保替换的唯一性。";
                }
                break;

            case "read_file":
            case "write_file":
                if (lowerError.contains("no such file") || lowerError.contains("找不到")) {
                    hint = "路径似乎不正确。请不要凭空猜测，先使用 `bash` 执行 `ls -la` 或 `find . -name` 命令查找正确的目录结构和文件名。";
                } else if (lowerError.contains("permission denied")) {
                    hint = "你没有权限操作该文件。请检查工作区限制，或者思考是否需要修改其他文件。";
                }
                break;

            case "bash":
                if (lowerError.contains("command not found") || lowerError.contains("未找到命令")) {
                    hint = "系统中未安装该命令。请先思考：是否有替代命令？或者你需要先编写脚本进行安装？";
                } else if (rawError.contains("超时") || lowerError.contains("deadline")) {
                    hint = "该命令执行被超时强杀。如果它是一个常驻服务（如 server 或 watch），请将其转入后台执行（例如使用 `nohup ... &`），不要阻塞主线程。";
                } else if (lowerError.contains("syntax error")) {
                    hint = "Bash 语法错误。请检查引号转义或特殊字符，确保命令在终端中可直接运行。";
                }
                break;
        }

        if (hint == null || hint.isEmpty()) {
            return rawError;
        }

        return rawError + "\n\n[系统救援指南]: " + hint;
    }
}
