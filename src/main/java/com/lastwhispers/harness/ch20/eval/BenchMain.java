package com.lastwhispers.harness.ch20.eval;

import com.lastwhispers.harness.util.Dotenv;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

/**
 * 自动化跑分入口！
 * 构建一组评测集，启动 BenchmarkRunner 执行并输出终极报告。
 */
@Slf4j
public class BenchMain {
    public static void main(String[] args) {
        Dotenv.load();

        String modelName = System.getenv("DASHSCOPE_MODEL");
        if (modelName == null || modelName.isEmpty()) {
            log.error("请设置 DASHSCOPE_MODEL 环境变量进行跑分测试");
            System.exit(1);
        }

        // 构建一套微型评测集
        List<TestCase> testcases = List.of(
                new TestCase(
                        "test_001_edit",
                        "测试模糊替换工具的准确性",
                        // 准备靶机：生成一个有错误的 json 文件
                        "echo '{\"name\": \"tiny-claw\", \"version\": \"v1.0.0\"}' > config.json",
                        // 考题：要求修改版本号
                        "当前目录下有一个 config.json。请你使用 edit_file 工具，将其中的 version 从 v1.0.0 改为 v2.0.0。不要做其他多余操作。",
                        // 判卷脚本：使用 grep 检查文件是否包含 v2.0.0
                        "grep '\"version\": \"v2.0.0\"' config.json"
                ),
                new TestCase(
                        "test_002_code_gen",
                        "测试代码阅读与创建新文件的综合能力",
                        // 准备靶机：生成一个简单的乘法函数
                        """
                                echo 'package math

                                func Multiply(a, b int) int {
                                	return a * b
                                }' > math.go
                                """,
                        // 考题：要求 Agent 根据刚才的代码，自己去写一份单元测试
                        "当前目录下有一个 math.go。请你仔细阅读它，然后在同级目录下，帮我写一个规范的单元测试文件 math_test.go，用来测试 Multiply 函数。请务必包含正常的测试用例。",
                        // 判卷脚本：直接运行 go test！如果不通过则直接 0 分。
                        "go mod init bench && go test -v ./..."
                )
        );

        // 启动跑分执行器！
        BenchmarkRunner runner = new BenchmarkRunner(modelName);
        runner.runSuite(testcases);
    }
}
