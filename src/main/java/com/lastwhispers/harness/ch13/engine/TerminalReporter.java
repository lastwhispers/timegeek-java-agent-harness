package com.lastwhispers.harness.ch13.engine;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

@Slf4j
public class TerminalReporter implements Reporter {

    @Override
    public void onThinking() {
        log.info("🤔 [思考中] 模型正在推理...");
    }

    @Override
    public void onToolCall(String toolName, String args) {
        log.info("🛠️ [调用工具] {}", toolName);
        // 清理参数中的换行符和特殊字符
        String displayArgs = args.replace("\n", "\\n").replace("\r", "\\r");
        if (displayArgs.length() > 150) {
            displayArgs = displayArgs.substring(0, 150) + "... (已截断)";
        }
        log.info("   参数: {}", displayArgs);
    }

    @Override
    public void onToolResult(String toolName, String result, boolean isError) {
        if (isError) {
            log.info("❌ [执行失败] {}", toolName);
            if (StringUtils.isNotEmpty(result)) {
                log.info("   错误: {}", result);
            }
        } else {
            log.info("✅ [执行成功] {}", toolName);
        }
    }

    @Override
    public void onMessage(String content) {
        if (StringUtils.isEmpty(content)) {
            return;
        }
        log.info("🤖 [Agent 回复]\n{}", content);
    }
}
