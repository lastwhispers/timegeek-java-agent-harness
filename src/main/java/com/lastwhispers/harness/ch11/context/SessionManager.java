package com.lastwhispers.harness.ch11.context;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 全局 Session Manager：用于多用户/多终端隔离。
 * 模拟 Go 中的 GlobalSessionMgr。
 */
public class SessionManager {

    private final Map<String, Session> sessions = new ConcurrentHashMap<>();

    /**
     * 全局单例。
     */
    public static final SessionManager INSTANCE = new SessionManager();

    private SessionManager() {
    }

    /**
     * 获取或创建一个会话。如果指定 id 的会话已存在则直接返回，否则新建。
     */
    public Session getOrCreate(String id, String workDir) {
        return sessions.computeIfAbsent(id, k -> new Session(id, workDir));
    }
}
