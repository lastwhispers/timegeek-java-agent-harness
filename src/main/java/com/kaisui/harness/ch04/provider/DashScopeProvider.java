package com.kaisui.harness.ch04.provider;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.kaisui.harness.ch04.schema.Message;
import com.kaisui.harness.ch04.schema.Role;
import com.kaisui.harness.ch04.schema.ToolCall;
import com.kaisui.harness.ch04.schema.ToolDefinition;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

/**
 * DashScope (阿里云百炼) LLM Provider.
 * Calls the OpenAI-compatible endpoint at DashScope.
 * Requires DASHSCOPE_API_KEY environment variable.
 */
public class DashScopeProvider implements LLMProvider {

    private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

    private final OkHttpClient client;
    private final String apiKey;
    private final String model;
    private final String baseUrl;
    private final ObjectMapper mapper;

    public DashScopeProvider(String model) {
//        this(model, "https://token-plan.cn-beijing.maas.aliyuncs.com/v1");
        this(model, "https://token-plan.cn-beijing.maas.aliyuncs.com/compatible-mode/v1");
    }

    public DashScopeProvider(String model, String baseUrl) {
        this.apiKey = System.getenv("DASHSCOPE_API_KEY");
        if (apiKey == null || apiKey.isEmpty()) {
            throw new IllegalStateException("请设置 DASHSCOPE_API_KEY 环境变量");
        }
        this.model = model;
        this.baseUrl = baseUrl;
        this.client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .readTimeout(120, TimeUnit.SECONDS)
                .build();
        this.mapper = new ObjectMapper();
    }

    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools) throws Exception {
        ObjectNode body = mapper.createObjectNode();
        body.put("model", model);
        body.put("max_tokens", 4096);

        // Convert messages
        ArrayNode msgArray = body.putArray("messages");
        for (Message msg : messages) {
            ObjectNode msgNode = msgArray.addObject();
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
                        ArrayNode tcArray = msgNode.putArray("tool_calls");
                        for (ToolCall tc : msg.getToolCalls()) {
                            ObjectNode tcNode = tcArray.addObject();
                            tcNode.put("id", tc.getId());
                            tcNode.put("type", "function");
                            ObjectNode funcNode = tcNode.putObject("function");
                            funcNode.put("name", tc.getName());
                            funcNode.put("arguments", tc.getArguments());
                        }
                    }
                    break;
                case TOOL:
                    msgNode.put("role", "tool");
                    msgNode.put("tool_call_id", msg.getToolCallId());
                    msgNode.put("content", msg.getContent());
                    break;
            }
        }

        // Convert tool definitions
        if (availableTools != null && !availableTools.isEmpty()) {
            ArrayNode toolsArray = body.putArray("tools");
            for (ToolDefinition toolDef : availableTools) {
                ObjectNode toolNode = toolsArray.addObject();
                toolNode.put("type", "function");
                ObjectNode funcNode = toolNode.putObject("function");
                funcNode.put("name", toolDef.getName());
                funcNode.put("description", toolDef.getDescription());

                // Parse input_schema into parameters
                if (toolDef.getInputSchema() != null) {
                    JsonNode schemaNode;
                    if (toolDef.getInputSchema() instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> schemaMap = (Map<String, Object>) toolDef.getInputSchema();
                        schemaNode = mapper.valueToTree(schemaMap);
                    } else {
                        schemaNode = mapper.readTree(toolDef.getInputSchema().toString());
                    }
                    funcNode.set("parameters", schemaNode);
                } else {
                    // Empty schema
                    ObjectNode emptyParams = funcNode.putObject("parameters");
                    emptyParams.put("type", "object");
                    emptyParams.putObject("properties");
                }
            }
        }

        String jsonBody = mapper.writeValueAsString(body);
        System.out.println("[DashScopeProvider] Request body:\n" + jsonBody);

        Request request = new Request.Builder()
                .url(baseUrl + "/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response response = client.newCall(request).execute()) {
            if (!response.isSuccessful()) {
                String errorBody = response.body() != null ? response.body().string() : "no body";
                throw new RuntimeException("DashScope API 请求失败: HTTP " + response.code() + ", " + errorBody);
            }

            String respBody = response.body().string();
            JsonNode root = mapper.readTree(respBody);

            JsonNode choices = root.get("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("API 返回了空的 choices");
            }

            JsonNode choice = choices.get(0);
            JsonNode messageNode = choice.get("message");

            Message resultMsg = new Message();
            resultMsg.setRole(Role.ASSISTANT);

            JsonNode contentNode = messageNode.get("content");
            if (contentNode != null && !contentNode.isNull()) {
                resultMsg.setContent(contentNode.asText());
            }

            JsonNode toolCallsNode = messageNode.get("tool_calls");
            if (toolCallsNode != null && toolCallsNode.isArray()) {
                List<ToolCall> toolCalls = new java.util.ArrayList<>();
                for (JsonNode tcNode : toolCallsNode) {
                    String id = tcNode.has("id") ? tcNode.get("id").asText() : generateId();
                    String name = tcNode.path("function").path("name").asText();
                    String args = tcNode.path("function").path("arguments").asText();
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
}
