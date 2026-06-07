package com.lastwhispers.harness.ch19.feishu;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONObject;
import com.lastwhispers.harness.ch19.context.Session;
import com.lastwhispers.harness.ch19.engine.AgentEngine;
import com.lastwhispers.harness.ch19.engine.Reporter;
import com.lastwhispers.harness.ch19.schema.Message;
import com.lastwhispers.harness.ch19.schema.Role;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

import static com.lastwhispers.harness.ch16.feishu.ApprovalManager.INSTANCE;

/**
 * 飞书 Bot 集成：接收消息、处理审批命令、调用 AgentEngine。
 * 使用 OkHttp 与飞书 Open API 通信。
 */
@Slf4j
public class FeishuBot {

    private final OkHttpClient httpClient;
    private final String appId;
    private final String appSecret;
    private final AgentEngine engine;
    private final Session session;
    private volatile String tenantAccessToken;
    private volatile FeishuReporter currentReporter;

    public FeishuBot(AgentEngine engine, Session session) {
        this.appId = System.getenv("FEISHU_APP_ID");
        this.appSecret = System.getenv("FEISHU_APP_SECRET");
        if (this.appId == null || this.appSecret == null) {
            throw new IllegalStateException("请设置 FEISHU_APP_ID 和 FEISHU_APP_SECRET 环境变量");
        }
        this.httpClient = new OkHttpClient();
        this.engine = engine;
        this.session = session;
        this.tenantAccessToken = getTenantAccessToken();
    }

    /**
     * 获取飞书 tenant_access_token（简单实现，不做刷新逻辑）。
     */
    private String getTenantAccessToken() {
        String url = "https://open.feishu.cn/open-apis/auth/v3/tenant_access_token/internal";
        JSONObject body = new JSONObject();
        body.put("app_id", appId);
        body.put("app_secret", appSecret);

        Request request = new Request.Builder()
            .url(url)
            .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
            .build();

        try (Response response = httpClient.newCall(request).execute()) {
            if (response.body() != null) {
                JSONObject json = JSON.parseObject(response.body().string());
                return json.getString("tenant_access_token");
            }
        } catch (IOException e) {
            log.error("[Feishu] 获取 tenant_access_token 失败", e);
        }
        throw new RuntimeException("获取飞书 tenant_access_token 失败");
    }

    public Reporter reporter() {
        return currentReporter;
    }

    /**
     * 处理从飞书 webhook 收到的事件 JSON。
     *
     * @return true 表示事件已处理
     */
    public boolean handleEvent(String jsonStr) {
        JSONObject event = JSON.parseObject(jsonStr);
        String type = event.getString("type");

        // v2 challenge 验证
        if ("url_verification".equals(type)) {
            return false;
        }

        JSONObject header = event.getJSONObject("header");
        if (header == null) return false;

        String eventType = header.getString("event_type");
        JSONObject eventPayload = event.getJSONObject("event");
        if (eventPayload == null) return false;

        JSONObject message = eventPayload.getJSONObject("message");
        if (message == null) return false;

        String contentStr = message.getString("content");
        String chatId = message.getString("chat_id");

        log.info("[Feishu] 收到会话 {} 消息: {}", chatId, contentStr);

        // 解析 text 类型消息内容
        if (contentStr != null && contentStr.startsWith("{\"text\":")) {
            JSONObject textJson = JSON.parseObject(contentStr);
            contentStr = textJson.getString("text");
        }
        if (contentStr == null) return false;

        // 拦截人工审批的特殊口令
        if (contentStr.startsWith("approve ")) {
            String taskId = contentStr.substring("approve ".length()).trim();
            INSTANCE.resolveApproval(taskId, true, "人类管理员已批准操作");
            log.info("[Feishu] 会话 {}: ✅ 已为您批准任务 {}", chatId, taskId);
            return true;
        }
        if (contentStr.startsWith("reject ")) {
            String taskId = contentStr.substring("reject ".length()).trim();
            INSTANCE.resolveApproval(taskId, false, "人类管理员认为该操作存在极高风险，已无情拒绝");
            log.info("[Feishu] 会话 {}: 🚫 已拒绝任务 {}", chatId, taskId);
            return true;
        }

        // 正常对话，启动 Agent 执行
        final String prompt = contentStr;
        final String chat = chatId;
        CompletableFuture.runAsync(() -> handleAgentRun(chat, prompt));
        return true;
    }

    private void handleAgentRun(String chatId, String prompt) {
        FeishuReporter reporter = new FeishuReporter(chatId);
        currentReporter = reporter;

        session.append(new Message(Role.USER, prompt));
        try {
            engine.run(session, reporter);
        } catch (Exception e) {
            reporter.sendMsg("❌ Agent 运行崩溃: " + e.getMessage());
            log.error("[Feishu] Agent 运行崩溃", e);
        }
    }

    /**
     * 飞书 Reporter：将引擎回调转发为飞书消息发送。
     */
    public class FeishuReporter implements Reporter {

        private final String chatId;

        FeishuReporter(String chatId) {
            this.chatId = chatId;
        }

        void sendMsg(String text) {
            String url = "https://open.feishu.cn/open-apis/im/v1/messages?receive_id_type=chat_id";
            JSONObject body = new JSONObject();
            body.put("receive_id", chatId);
            body.put("msg_type", "text");
            JSONObject content = new JSONObject();
            content.put("text", text);
            body.put("content", content.toJSONString());

            Request request = new Request.Builder()
                .url(url)
                .header("Authorization", "Bearer " + tenantAccessToken)
                .post(RequestBody.create(body.toJSONString(), MediaType.parse("application/json")))
                .build();

            try (Response response = httpClient.newCall(request).execute()) {
                if (!response.isSuccessful()) {
                    log.warn("[Feishu] 发送消息失败: HTTP {}", response.code());
                }
            } catch (IOException e) {
                log.error("[Feishu] 发送消息异常", e);
            }
        }

        @Override
        public void onThinking() {
            sendMsg("🤔 模型正在慢思考 (Thinking)...");
        }

        @Override
        public void onToolCall(String toolName, String args) {
            sendMsg(String.format("🛠️ **正在执行工具**：`%s`\n参数：`%s`", toolName, args));
        }

        @Override
        public void onToolResult(String toolName, String result, boolean isError) {
            if (isError) {
                sendMsg(String.format("⚠️ **执行报错** (%s)：\n%s", toolName, result));
            } else {
                sendMsg(String.format("✅ **执行成功** (%s)", toolName));
            }
        }

        @Override
        public void onMessage(String content) {
            sendMsg(content);
        }
    }
}
