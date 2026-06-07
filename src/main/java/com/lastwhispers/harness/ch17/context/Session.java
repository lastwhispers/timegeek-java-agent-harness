package com.lastwhispers.harness.ch17.context;

import com.lastwhispers.harness.ch17.schema.Message;
import com.lastwhispers.harness.ch17.schema.Role;
import lombok.Getter;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;

/**
 * Session 代表了一次持续的人机交互过程。它负责维护该会话的完整历史。
 */
public class Session {

    @Getter
    private final String id;
    @Getter
    private final String workDir; // 该会话绑定的物理工作区
    @Getter
    private final Instant createdAt;
    @Getter
    private volatile Instant updatedAt;

    // 存放此 Session 中所有的用户输入、大模型回复和工具调用结果
    private final List<Message> history = new ArrayList<>();
    private final ReadWriteLock lock = new ReentrantReadWriteLock(); // 读写锁，防止并发读写历史时发生 Data Race

    public Session(String id, String workDir) {
        this.id = id;
        this.workDir = workDir;
        this.createdAt = Instant.now();
        this.updatedAt = Instant.now();
    }

    /**
     * 线程安全地向 Session 中追加消息
     */
    public void append(Message... msgs) {
        lock.writeLock().lock();
        try {
            Collections.addAll(history, msgs);
            this.updatedAt = Instant.now();

            // 【持久化预留点】：在真实的工业级实现中（如 Claude Code），
            // 我们会在这里将 s.history 以 JSONL 的格式 Append 到 workDir/.claw/sessions/xxx.jsonl 中。
            // saveToDisk();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 线程安全地向 Session 中追加消息列表
     */
    public void append(List<Message> msgs) {
        lock.writeLock().lock();
        try {
            history.addAll(msgs);
            this.updatedAt = Instant.now();

            // 【持久化预留点】：同上
            // saveToDisk();
        } finally {
            lock.writeLock().unlock();
        }
    }

    /**
     * 获取短期工作记忆——驾驭工程的核心！
     * 它不返回全量历史，而是从后往前截取最近的 limit 条消息，形成 Agent 的「短期工作记忆」。
     * <p>
     * 【驾驭防线】：大模型 API 强制要求历史消息的连续性！
     * 如果我们截断的第一条消息恰好是一个 ToolResult（Role=USER 且含有 toolCallId），
     * 但发出这个请求的 ToolCall 被我们截断抛弃了，大模型 API 会直接报 400 Bad Request。
     * 因此，如果切片首条属于「孤儿」工具响应，我们必须将其强行舍弃，顺延到下一条正常的 User/Assistant 消息。
     */
    public List<Message> getWorkingMemory(int limit) {
        lock.readLock().lock();
        try {
            int total = history.size();
            if (total <= limit || limit <= 0) {
                // 如果历史总量小于限制，或者不设限，全量返回（需要深拷贝以防外部修改）
                return new ArrayList<>(history);
            }

            // 截取最近的 limit 条消息
            List<Message> result = new ArrayList<>(history.subList(total - limit, total));

            // 如果切片首条属于「孤儿」工具响应，强行舍弃，顺延到下一条正常消息
            while (!result.isEmpty()) {
                Message first = result.get(0);
                if (first.getRole() == Role.USER && first.getToolCallId() != null && !first.getToolCallId().isEmpty()) {
                    result.remove(0);
                } else {
                    break;
                }
            }

            return result;
        } finally {
            lock.readLock().unlock();
        }
    }
}
