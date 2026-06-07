package com.lastwhispers.harness.ch20.feishu;

import com.lastwhispers.harness.ch20.engine.Reporter;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.SynchronousQueue;

/**
 * 审批流程的 Channel 管理中枢。
 * 工具执行前若命中高危规则，则通过此类挂起当前线程等待人工审批。
 */
@Slf4j
public class ApprovalManager {

    public record ApprovalResult(boolean allowed, String reason) {}

    public static final ApprovalManager INSTANCE = new ApprovalManager();

    private final Map<String, SynchronousQueue<ApprovalResult>> pendingTasks = new ConcurrentHashMap<>();

    /**
     * 发送审批请求并阻塞当前线程，直到人工审批到达。
     */
    public ApprovalResult waitForApproval(String taskId, String toolName, String args, Reporter reporter) {
        SynchronousQueue<ApprovalResult> queue = new SynchronousQueue<>();
        pendingTasks.put(taskId, queue);

        String noticeMsg = String.format(
            "⚠️ **高危操作审批请求**\n"
            + "Agent 试图执行以下动作:\n"
            + "- 工具: %s\n"
            + "- 参数: %s\n\n"
            + "任务 ID: **%s**\n\n"
            + "👉 请回复 \"approve %s\" 或 \"reject %s\" 决定是否放行。",
            toolName, args, taskId, taskId, taskId
        );

        if (reporter != null) {
            reporter.onToolResult("审批", noticeMsg, true);
        } else {
            log.info("\n[需要审批 TaskID: {}] {}", taskId, noticeMsg);
        }

        log.info("[Approval] 发送审批请求 (TaskID: {})，线程挂起等待...", taskId);

        try {
            ApprovalResult result = queue.take();
            log.info("[Approval] 收到审批结果 (TaskID: {}, Allowed: {})", taskId, result.allowed());
            return result;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new ApprovalResult(false, "审批等待被中断");
        } finally {
            pendingTasks.remove(taskId);
        }
    }

    /**
     * 唤醒挂起的线程，传递审批结果。
     */
    public void resolveApproval(String taskId, boolean allowed, String reason) {
        SynchronousQueue<ApprovalResult> queue = pendingTasks.get(taskId);
        if (queue != null) {
            log.info("[Approval] 收到飞书审批结果 (TaskID: {}, Allowed: {})", taskId, allowed);
            try {
                queue.put(new ApprovalResult(allowed, reason));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        } else {
            log.warn("[Approval] 未找到 TaskID={} 对应的审批队列", taskId);
        }
    }
}
