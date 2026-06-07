package com.lastwhispers.harness.ch17.engine;

import com.lastwhispers.harness.ch17.schema.Message;
import com.lastwhispers.harness.ch17.schema.Role;
import com.lastwhispers.harness.ch17.schema.ToolCall;
import com.lastwhispers.harness.ch17.schema.ToolResult;
import lombok.extern.slf4j.Slf4j;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HashMap;
import java.util.Map;

/**
 * 死循环探测与动态提醒生成器。
 * 通过工具名+参数的 MD5 指纹跟踪连续失败次数，达到阈值后注入干预指令。
 */
@Slf4j
public class ReminderInjector {

    private static final int FAILURE_THRESHOLD = 3;

    private final Map<String, Integer> consecutiveFailures = new HashMap<>();

    /**
     * 检查并注入提醒消息。
     *
     * @return 如果触发干预则返回提醒 Message，否则返回 null
     */
    public Message checkAndInject(ToolCall lastToolCall, ToolResult lastResult) {
        if (lastToolCall == null || lastResult == null) {
            return null;
        }

        String fingerprint = generateFingerprint(lastToolCall.getName(), lastToolCall.getArguments());

        if (!lastResult.isError()) {
            consecutiveFailures.clear();
            return null;
        }

        int failCount = consecutiveFailures.merge(fingerprint, 1, Integer::sum);

        log.info("[Reminder] 监控到工具 {} 执行失败，该参数特征连续失败次数: {}", lastToolCall.getName(), failCount);

        if (failCount >= FAILURE_THRESHOLD) {
            log.info("[Reminder] 触发死循环干预！注入强力修正指令。");

            String nudgeMsg = String.format(
                "[SYSTEM REMINDER 警告]\n"
                + "你似乎陷入了死循环。你刚刚连续 %d 次使用相同的参数调用了 '%s' 工具，并且都失败了。\n"
                + "请立即停止这种无效的重试！你的注意力被当前的报错过度吸引了。\n"
                + "你需要：\n"
                + "1. 停止猜测参数。跳出当前的局部思维。\n"
                + "2. 彻底改变你的策略。\n"
                + "3. 如果你确实无法通过系统工具解决当前问题，请直接结束任务并向用户说明你需要什么人工帮助，而不是继续盲目消耗 API 资源尝试。",
                failCount, lastToolCall.getName()
            );

            return new Message(Role.USER, nudgeMsg);
        }

        return null;
    }

    static String generateFingerprint(String toolName, String args) {
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            md5.update(toolName.getBytes(StandardCharsets.UTF_8));
            md5.update(args.getBytes(StandardCharsets.UTF_8));
            byte[] digest = md5.digest();
            StringBuilder sb = new StringBuilder(digest.length * 2);
            for (byte b : digest) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return toolName + ":" + args;
        }
    }
}
