package com.lastwhispers.harness.ch16.feishu;

import java.util.List;
import java.util.regex.Pattern;

/**
 * 高危命令特征检测器。
 */
public class CommandSafetyChecker {

    private static final List<Pattern> DANGEROUS_PATTERNS = List.of(
        Pattern.compile("rm\\s+-r"),
        Pattern.compile("sudo\\s+"),
        Pattern.compile("drop\\s+"),
        Pattern.compile(">.*\\.go")
    );

    /**
     * 判断工具调用是否命中高危规则。
     * 仅对 bash、write_file、edit_file 三种工具生效。
     */
    public static boolean isDangerousCommand(String toolName, String args) {
        if (!"bash".equals(toolName) && !"write_file".equals(toolName) && !"edit_file".equals(toolName)) {
            return false;
        }

        if ("bash".equals(toolName)) {
            for (Pattern pattern : DANGEROUS_PATTERNS) {
                if (pattern.matcher(args).find()) {
                    return true;
                }
            }
        }
        return false;
    }
}
