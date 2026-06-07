package com.lastwhispers.harness.ch19.observability;

import com.lastwhispers.harness.ch19.provider.LLMProvider;
import com.lastwhispers.harness.ch19.schema.Message;
import com.lastwhispers.harness.ch19.schema.ToolDefinition;
import com.lastwhispers.harness.ch19.schema.Usage;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * 成本与耗时追踪装饰器。
 * 包装 LLMProvider，自动记录每次调用的 Token 用量、耗时和累计花费。
 */
@Slf4j
public class Tracker implements LLMProvider {

    private final LLMProvider delegate;

    /** 模型每百万 input token 的价格（USD），默认按 DashScope qwen-plus 计价 */
    private final double inputPricePerM;
    /** 模型每百万 output token 的价格（USD） */
    private final double outputPricePerM;

    private final AtomicInteger callCount = new AtomicInteger(0);
    private final AtomicInteger totalPromptTokens = new AtomicInteger(0);
    private final AtomicInteger totalCompletionTokens = new AtomicInteger(0);
    private final DoubleAdder totalCostUsd = new DoubleAdder();

    public Tracker(LLMProvider delegate) {
        this(delegate, 0.2, 0.8); // 默认价格：input $0.2/M, output $0.8/M
    }

    public Tracker(LLMProvider delegate, double inputPricePerM, double outputPricePerM) {
        this.delegate = delegate;
        this.inputPricePerM = inputPricePerM;
        this.outputPricePerM = outputPricePerM;
    }

    @Override
    public Message generate(List<Message> messages, List<ToolDefinition> availableTools) throws Exception {
        callCount.incrementAndGet();
        long startNs = System.nanoTime();

        Message result = delegate.generate(messages, availableTools);

        long elapsedMs = (System.nanoTime() - startNs) / 1_000_000;

        Usage usage = result.getUsage();
        if (usage != null) {
            totalPromptTokens.addAndGet(usage.getPromptTokens());
            totalCompletionTokens.addAndGet(usage.getCompletionTokens());

            double callCost = (usage.getPromptTokens() / 1_000_000.0) * inputPricePerM
                            + (usage.getCompletionTokens() / 1_000_000.0) * outputPricePerM;
            usage.setCostUsd(callCost);
            totalCostUsd.add(callCost);

            log.info("[Tracker] 第 {} 次调用 | prompt_tokens={} completion_tokens={} | 耗时 {}ms | 本次花费 ${:.4f} | 累计花费 ${:.4f}",
                callCount.get(),
                usage.getPromptTokens(),
                usage.getCompletionTokens(),
                elapsedMs,
                callCost,
                totalCostUsd.doubleValue());
        } else {
            log.info("[Tracker] 第 {} 次调用 | 耗时 {}ms | 无 Usage 信息", callCount.get(), elapsedMs);
        }

        return result;
    }

    /**
     * 获取累计调用统计摘要。
     */
    public String getSummary() {
        return String.format(
            "调用 %d 次 | prompt_tokens=%d completion_tokens=%d total_tokens=%d | 累计花费 $%.4f",
            callCount.get(),
            totalPromptTokens.get(),
            totalCompletionTokens.get(),
            totalPromptTokens.get() + totalCompletionTokens.get(),
            totalCostUsd.doubleValue()
        );
    }

    public int getCallCount() { return callCount.get(); }
    public int getTotalPromptTokens() { return totalPromptTokens.get(); }
    public int getTotalCompletionTokens() { return totalCompletionTokens.get(); }
    public double getTotalCostUsd() { return totalCostUsd.doubleValue(); }
}
