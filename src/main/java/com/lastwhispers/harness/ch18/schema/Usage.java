package com.lastwhispers.harness.ch18.schema;

import com.alibaba.fastjson2.annotation.JSONField;

/**
 * Token 用量与成本记录。
 */
public class Usage {

    @JSONField(name = "prompt_tokens")
    private int promptTokens;

    @JSONField(name = "completion_tokens")
    private int completionTokens;

    @JSONField(name = "total_tokens")
    private int totalTokens;

    @JSONField(name = "cost_usd")
    private double costUsd;

    public Usage() {}

    public Usage(int promptTokens, int completionTokens, int totalTokens) {
        this.promptTokens = promptTokens;
        this.completionTokens = completionTokens;
        this.totalTokens = totalTokens;
    }

    public int getPromptTokens() { return promptTokens; }
    public void setPromptTokens(int promptTokens) { this.promptTokens = promptTokens; }

    public int getCompletionTokens() { return completionTokens; }
    public void setCompletionTokens(int completionTokens) { this.completionTokens = completionTokens; }

    public int getTotalTokens() { return totalTokens; }
    public void setTotalTokens(int totalTokens) { this.totalTokens = totalTokens; }

    public double getCostUsd() { return costUsd; }
    public void setCostUsd(double costUsd) { this.costUsd = costUsd; }

    @Override
    public String toString() {
        return String.format("Usage{prompt=%d, completion=%d, total=%d, cost=$%.4f}",
            promptTokens, completionTokens, totalTokens, costUsd);
    }
}
