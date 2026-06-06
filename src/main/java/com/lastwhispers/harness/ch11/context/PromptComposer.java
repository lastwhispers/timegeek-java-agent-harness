package com.lastwhispers.harness.ch11.context;

import com.lastwhispers.harness.ch11.schema.Message;
import com.lastwhispers.harness.ch11.schema.Role;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;

@Slf4j
public class PromptComposer {

    private final String workDir;
    private final SkillLoader skillLoader;

    public PromptComposer(String workDir) {
        this.workDir = workDir;
        this.skillLoader = new SkillLoader(workDir);
    }

    public Message build() {
        StringBuilder promptBuilder = new StringBuilder();

        promptBuilder.append("""
                # 核心身份
                你名叫 timegeek-java-agent，一个由驾驭工程驱动的骨灰级研发助手。
                你具备极简主义哲学，拒绝废话。你能通过系统提供的内置工具，创建、读取、修改和执行工作区中的代码。

                # 核心纪律 (CRITICAL)
                1. 如需检查文件是否存在，请使用 bash 的 ls 或 test -f，而不是对目录使用 read_file。
                2. 创建新文件时，务必使用 write_file，并同时提供 path 和 content 参数。
                3. 编辑文件前务必先读取现有文件，以理解上下文。
                4. 无论何时你需要写代码或创建文件，都要直接使用 write_file 工具。
                5. 遇到工具执行报错时，仔细阅读 stderr，尝试自己修正命令并重试。
                6. 始终用中文回复，以便传达你的进展和想法。
                """);

        // 加载 AGENTS.md
        String agentsMdPath = Paths.get(workDir, "AGENTS.md").toString();
        try {
            String content = Files.readString(Paths.get(agentsMdPath));
            promptBuilder.append("\n# 项目专属指南 (来自 AGENTS.md)\n");
            promptBuilder.append("以下是当前工作区特有的架构规范与注意事项，你的行为必须绝对符合以下要求：\n");
            promptBuilder.append("```markdown\n");
            promptBuilder.append(content);
            promptBuilder.append("\n```\n");
        } catch (IOException e) {
            log.debug("未找到 AGENTS.md，跳过加载");
        }

        // 加载 Skills
        String skillsContent = skillLoader.loadAll();
        if (!skillsContent.isEmpty()) {
            promptBuilder.append(skillsContent);
        }

        Message systemMessage = new Message();
        systemMessage.setRole(Role.SYSTEM);
        systemMessage.setContent(promptBuilder.toString());
        return systemMessage;
    }
}
