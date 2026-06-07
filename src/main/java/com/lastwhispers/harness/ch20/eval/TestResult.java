package com.lastwhispers.harness.ch20.eval;

/**
 * 存放单次跑分结果。
 */
public class TestResult {

    private final String testCaseId;
    private final boolean passed;
    private final double totalCostCny;
    private final long durationMs;
    private final String errorMsg;

    public TestResult(String testCaseId, boolean passed, double totalCostCny, long durationMs, String errorMsg) {
        this.testCaseId = testCaseId;
        this.passed = passed;
        this.totalCostCny = totalCostCny;
        this.durationMs = durationMs;
        this.errorMsg = errorMsg;
    }

    public static TestResult failed(String testCaseId, String errorMsg) {
        return new TestResult(testCaseId, false, 0.0, 0, errorMsg);
    }

    public static TestResult passed(String testCaseId, double totalCostCny, long durationMs) {
        return new TestResult(testCaseId, true, totalCostCny, durationMs, null);
    }

    public String getTestCaseId() { return testCaseId; }
    public boolean isPassed() { return passed; }
    public double getTotalCostCny() { return totalCostCny; }
    public long getDurationMs() { return durationMs; }
    public String getErrorMsg() { return errorMsg; }
}
