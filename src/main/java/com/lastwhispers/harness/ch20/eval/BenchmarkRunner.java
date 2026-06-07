package com.lastwhispers.harness.ch20.eval;

import com.lastwhispers.harness.ch20.context.Session;
import com.lastwhispers.harness.ch20.engine.AgentEngine;
import com.lastwhispers.harness.ch20.observability.Tracker;
import com.lastwhispers.harness.ch20.provider.DashScopeProvider;
import com.lastwhispers.harness.ch20.provider.LLMProvider;
import com.lastwhispers.harness.ch20.schema.Message;
import com.lastwhispers.harness.ch20.schema.Role;
import com.lastwhispers.harness.ch20.tools.*;
import lombok.extern.slf4j.Slf4j;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * 自动化 Harness Benchmark 评估执行器。
 * 为每个评测用例创建独立沙箱，启动 Agent 执行任务，并用校验脚本验收结果。
 */
@Slf4j
public class BenchmarkRunner {

    private final String modelName;

    public BenchmarkRunner(String modelName) {
        this.modelName = modelName;
    }

    /**
     * 执行一组评测集，并输出跑分报告。
     */
    public void runSuite(List<TestCase> testcases) {
        log.info("==================================================");
        log.info("🚀 启动自动化 Harness Benchmark 评估... | 模型: {}", modelName);
        log.info("==================================================");

        List<TestResult> results = new ArrayList<>();
        int passedCount = 0;
        double totalCost = 0.0;

        for (TestCase tc : testcases) {
            log.info("\n>>> ⏳ 正在执行用例 {}: {}", tc.getId(), tc.getName());

            TestResult res = runSingleTest(tc);
            results.add(res);

            if (res.isPassed()) {
                passedCount++;
                log.info(">>> ✅ 用例 [{}] 测试通过! | 耗时: {}ms | 花费: ¥{:.6f}",
                    tc.getId(), res.getDurationMs(), res.getTotalCostCny());
            } else {
                log.info(">>> ❌ 用例 [{}] 测试失败! | 错误: {}", tc.getId(), res.getErrorMsg());
            }
            totalCost += res.getTotalCostCny();
        }

        // 打印终极报表
        double successRate = (double) passedCount / testcases.size() * 100;
        log.info("\n================ 🏆 跑分终极报告 ================");
        log.info("总用例数: {} | 成功数: {} | 成功率: {:.2f}%", testcases.size(), passedCount, successRate);
        log.info("总消耗成本: ¥{:.6f}", totalCost);
        log.info("==================================================");
    }

    private TestResult runSingleTest(TestCase tc) {
        long startTime = System.currentTimeMillis();

        // 1. 为每个用例创建一个绝对干净的沙箱目录 (物理隔离)
        String baseWorkDir = System.getProperty("user.dir") + "/workspace/ch20";
        String sandBoxName = tc.getId() + "_" + System.currentTimeMillis();
        Path workDirPath = Path.of(baseWorkDir, sandBoxName);
        try {
            Files.createDirectories(workDirPath);
        } catch (Exception e) {
            return TestResult.failed(tc.getId(), "创建沙箱目录失败: " + e.getMessage());
        }
        String workDir = workDirPath.toString();

        // 2. (可选) 执行 Setup 脚本准备靶机代码
        if (tc.getSetupScript() != null && !tc.getSetupScript().isEmpty()) {
            int setupExit = runBash(tc.getSetupScript(), workDir);
            if (setupExit != 0) {
                return TestResult.failed(tc.getId(), "靶机 Setup 失败");
            }
        }

        // 3. 组装具备打点能力 (Tracker) 的引擎
        LLMProvider realProvider = new DashScopeProvider(modelName);
        Tracker tracker = new Tracker(realProvider);

        Session session = new Session(tc.getId(), workDir);

        Registry registry = new RegistryImpl();
        registry.register(new ReadFileTool(workDir));
        registry.register(new WriteFileTool(workDir));
        registry.register(new BashTool(workDir));
        registry.register(new EditFileTool(workDir));

        AgentEngine engine = new AgentEngine(tracker, registry, false, false);

        // 4. 让 Agent 开始干活
        session.append(new Message(Role.USER, tc.getTaskPrompt()));
        // 传入 null 屏蔽普通日志，防止刷屏
        try {
            engine.run(session, null);
        } catch (Exception e) {
            return TestResult.failed(tc.getId(), "Agent 崩溃: " + e.getMessage());
        }

        // 5. 【核心断言】Agent 跑完了，我们来验收成果！
        int validateExit = runBash(tc.getValidateScript(), workDir);
        long duration = System.currentTimeMillis() - startTime;

        if (validateExit != 0) {
            return new TestResult(tc.getId(), false, tracker.getTotalCostUsd(), duration,
                "验证脚本执行失败");
        }

        return TestResult.passed(tc.getId(), tracker.getTotalCostUsd(), duration);
    }

    /**
     * 在指定工作目录下执行 bash 命令，返回 exit code。
     */
    private int runBash(String script, String workDir) {
        try {
            ProcessBuilder pb = new ProcessBuilder("bash", "-c", script);
            pb.directory(Path.of(workDir).toFile());
            pb.redirectErrorStream(true);
            Process process = pb.start();

            // 读取输出（防止管道阻塞）
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                while (reader.readLine() != null) {
                    // 吞掉输出，只关心 exit code
                }
            }

            boolean finished = process.waitFor(60, java.util.concurrent.TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return -1;
            }
            return process.exitValue();
        } catch (Exception e) {
            log.warn("执行脚本异常: {}", e.getMessage());
            return -1;
        }
    }
}
