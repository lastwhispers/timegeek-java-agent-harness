package com.lastwhispers.harness.ch08.provider;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.JSONArray;
import com.alibaba.fastjson2.JSONObject;
import com.lastwhispers.harness.ch08.schema.Message;
import com.lastwhispers.harness.ch08.schema.Role;
import com.lastwhispers.harness.ch08.schema.ToolCall;
import com.lastwhispers.harness.ch08.schema.ToolDefinition;
import lombok.extern.slf4j.Slf4j;
import okhttp3.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * DashScope (阿里云百炼) LLM Provider.
 * Calls the OpenAI-compatible endpoint at DashScope.
 * Requires DASHSCOPE_API_KEY environment variable.
 */
@Slf4j
public class DashScopeProvider implements LLMProvider {

    private static final MediaType JSON_TYPE = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final String baseUrl;

    public DashScopeProvider() {
        this(resolve("DASHSCOPE_MODEL"));
    }

    public DashScopeProvider(String model) {
        this(model, "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");
    }

    public DashScopeProvider(String model, String baseUrl) {
        this.apiKey = resolve("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("请设置 DASHSCOPE_API_KEY 环境变量");
        }
        if (model == null || model.isEmpty()) {
            throw new IllegalStateException("请设置 DASHSCOPE_MODEL 环境变量或在 .env 中配置");
        }
        this.model = model;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
    }

    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", model);
        body.put("max_tokens", 4096);

        // Convert messages
        JSONArray msgArray = new JSONArray();
        body.put("messages", msgArray);
        for (Message msg : messages) {
            JSONObject msgNode = new JSONObject();
            switch (msg.getRole()) {
                case SYSTEM:
                    msgNode.put("role", "system");
                    msgNode.put("content", msg.getContent());
                    break;
                case USER:
                    msgNode.put("role", "user");
                    if (msg.getToolCallId() != null && !msg.getToolCallId().isEmpty()) {
                        msgNode.put("tool_call_id", msg.getToolCallId());
                    }
                    msgNode.put("content", msg.getContent());
                    break;
                case ASSISTANT:
                    msgNode.put("role", "assistant");
                    if (msg.getContent() != null && !msg.getContent().isEmpty()) {
                        msgNode.put("content", msg.getContent());
                    }
                    if (msg.hasToolCalls()) {
                        JSONArray tcArray = new JSONArray();
                        msgNode.put("tool_calls", tcArray);
                        for (ToolCall tc : msg.getToolCalls()) {
                            JSONObject tcNode = new JSONObject();
                            tcNode.put("id", tc.getId());
                            tcNode.put("type", "function");
                            JSONObject funcNode = new JSONObject();
                            funcNode.put("name", tc.getName());
                            funcNode.put("arguments", tc.getArguments());
                            tcNode.put("function", funcNode);
                            tcArray.add(tcNode);
                        }
                    }
                    break;
                case TOOL:
                    msgNode.put("role", "tool");
                    msgNode.put("tool_call_id", msg.getToolCallId());
                    msgNode.put("content", msg.getContent());
                    break;
            }
            msgArray.add(msgNode);
        }

        // Convert tool definitions
        if (availableTools != null && !availableTools.isEmpty()) {
            JSONArray toolsArray = new JSONArray();
            body.put("tools", toolsArray);
            for (ToolDefinition toolDef : availableTools) {
                JSONObject toolNode = new JSONObject();
                toolNode.put("type", "function");
                JSONObject funcNode = new JSONObject();
                funcNode.put("name", toolDef.getName());
                funcNode.put("description", toolDef.getDescription());

                if (toolDef.getInputSchema() != null) {
                    if (toolDef.getInputSchema() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> schemaMap = (Map<String, Object>) toolDef.getInputSchema();
                        funcNode.put("parameters", JSONObject.from(schemaMap));
                    } else {
                        funcNode.put("parameters", JSON.parse(toolDef.getInputSchema().toString()));
                    }
                } else {
                    JSONObject emptyParams = new JSONObject();
                    emptyParams.put("type", "object");
                    emptyParams.put("properties", new JSONObject());
                    funcNode.put("parameters", emptyParams);
                }
                toolNode.put("function", funcNode);
                toolsArray.add(toolNode);
            }
        }

        String jsonBody = body.toJSONString();
        log.debug("[DashScopeProvider] Request body: {}", jsonBody);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON_TYPE))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new RuntimeException("DashScope API 请求失败: HTTP " + response.code() + ", " + errorBody);
            }

            String respBody = response.body().string();
            JSONObject root = JSON.parseObject(respBody);

            JSONArray choices = root.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("API 返回了空的 choices");
            }

            JSONObject messageNode = choices.getJSONObject(0).getJSONObject("message");

            Message resultMsg = new Message();
            resultMsg.setRole(Role.ASSISTANT);

            String content = messageNode.getString("content");
            if (content != null) {
                resultMsg.setContent(content);
            }

            JSONArray toolCallsNode = messageNode.getJSONArray("tool_calls");
            if (toolCallsNode != null && !toolCallsNode.isEmpty()) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (int i = 0; i < toolCallsNode.size(); i++) {
                    JSONObject tcNode = toolCallsNode.getJSONObject(i);
                    String id = tcNode.getString("id");
                    if (id == null) {
                        id = generateId();
                    }
                    JSONObject funcObj = tcNode.getJSONObject("function");
                    String name = funcObj.getString("name");
                    String args = funcObj.getString("arguments");
                    toolCalls.add(new ToolCall(id, name, args));
                }
                resultMsg.setToolCalls(toolCalls);
            }

            return resultMsg;
        }
    }

    private String generateId() {
        return "call_" + UUID.randomUUID().toString().substring(0, 8);
    }

    // 优先读环境变量，其次读系统属性（支持 .env 文件加载的场景）
    private static String resolve(String key) {
        String val = System.getenv(key);
        if (val != null && !val.isEmpty()) {
            return val;
        }
        return System.getProperty(key);
    }
}
