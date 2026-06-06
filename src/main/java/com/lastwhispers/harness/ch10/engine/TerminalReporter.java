package com.lastwhispers.harness.ch10.engine;

import org.apache.commons.lang3.StringUtils;

public class TerminalReporter implements Reporter {

    @Override
    public void onThinking() {
        System.out.println("\n[🤔 思考中] 模型正在推理...");
    }

    @Override
    public void onToolCall(String toolName, String args) {
        System.out.println("[🛠️ 调用工具] " + toolName);
        // 清理参数中的换行符和特殊字符
        String displayArgs = args.replace("\n", "\\n").replace("\r", "\\r");
        if (displayArgs.length() > 150) {
            displayArgs = displayArgs.substring(0, 150) + "... (已截断)";
        }
        System.out.println("   参数: " + displayArgs);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean isError) {
        if (isError) {
            System.out.println("[❌ 执行失败] " + toolName);
            if (StringUtils.isNotEmpty(result)) {
                System.out.println("   错误: " + result);
            }
        } else {
            System.out.println("[✅ 执行成功] " + toolName);
        }
    }

    @Override
    public void onMessage(String content) {
        if (StringUtils.isEmpty(content)) {
            return;
        }
        System.out.println("\n🤖 Agent 回复:\n" + content + "\n");
    }
}
