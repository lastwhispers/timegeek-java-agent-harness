package com.lastwhispers.harness.ch20.eval;

/**
 * 定义一个需要 Agent 去完成并验证的独立评测用例。
 */
public class TestCase {

    private final String id;
    private final String name;
    private final String setupScript;
    private final String taskPrompt;
    private final String validateScript;
    private final int maxTurns;

    public TestCase(String id, String name, String setupScript, String taskPrompt, String validateScript) {
        this(id, name, setupScript, taskPrompt, validateScript, 0);
    }

    public TestCase(String id, String name, String setupScript, String taskPrompt, String validateScript, int maxTurns) {
        this.id = id;
        this.name = name;
        this.setupScript = setupScript;
        this.taskPrompt = taskPrompt;
        this.validateScript = validateScript;
        this.maxTurns = maxTurns;
    }

    public String getId() { return id; }
    public String getName() { return name; }
    public String getSetupScript() { return setupScript; }
    public String getTaskPrompt() { return taskPrompt; }
    public String getValidateScript() { return validateScript; }
    public int getMaxTurns() { return maxTurns; }

    @Override
    public String toString() {
        return String.format("[%s] %s", id, name);
    }
}
