package com.lastwhispers.harness.ch20.engine;

import com.lastwhispers.harness.ch20.tools.Registry;

/**
 * 引擎向外部工具暴露的执行能力接口。
 * Subagent 工具通过此接口拉起受限的子循环。
 */
public interface AgentRunner {

    /**
     * 启动一次受限的子智能体循环。
     *
     * @param taskPrompt         子任务指令
     * @param readOnlyRegistry   子智能体只能使用的只读工具注册表
     * @param reporter           用于展示子智能体工作轨迹的 Reporter
     * @return 子智能体的探索摘要报告
     */
    String runSub(String taskPrompt, Registry readOnlyRegistry, Reporter reporter);
}
