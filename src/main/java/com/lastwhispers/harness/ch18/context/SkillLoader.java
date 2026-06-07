package com.lastwhispers.harness.ch18.context;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;

@Slf4j
public class SkillLoader {

    private final String workDir;

    public SkillLoader(String workDir) {
        this.workDir = workDir;
    }

    public String loadAll() {
        Path skillBaseDir = Paths.get(workDir, ".claw", "skills");
        if (!Files.exists(skillBaseDir)) {
            return "";
        }

        StringBuilder builder = new StringBuilder();
        builder.append("\n### 可用专业技能 (Agent Skills)\n");
        builder.append("以下是你拥有的标准化外挂技能，请在符合 description 描述的场景下严格遵循其正文指令：\n\n");

        try (Stream<Path> walk = Files.walk(skillBaseDir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> "SKILL.md".equals(p.getFileName().toString()))
                    .forEach(p -> {
                        try {
                            String content = Files.readString(p);
                            Skill skill = parseSkillMd(content);
                            builder.append("#### 技能名称: ").append(skill.getName()).append("\n");
                            builder.append("**触发条件**: ").append(skill.getDescription()).append("\n\n");
                            builder.append("**执行指南**:\n");
                            builder.append(skill.getBody()).append("\n\n");
                            builder.append("---\n");
                        } catch (IOException e) {
                            log.warn("读取技能文件失败: {}", p, e);
                        }
                    });
        } catch (IOException e) {
            log.warn("加载技能目录失败: {}", skillBaseDir, e);
            return "";
        }

        if (builder.length() < 50) {
            return "";
        }
        return builder.toString();
    }

    private Skill parseSkillMd(String content) {
        Skill skill = new Skill();
        skill.setBody(content);

        if (content.startsWith("---\n") || content.startsWith("---\r\n")) {
            int secondDash = content.indexOf("---", 3);
            if (secondDash > 0) {
                String frontmatter = content.substring(3, secondDash);
                skill.setBody(content.substring(secondDash + 3).trim());

                for (String line : frontmatter.split("\n")) {
                    line = line.trim();
                    if (line.startsWith("name:")) {
                        skill.setName(line.substring("name:".length()).trim());
                    } else if (line.startsWith("description:")) {
                        skill.setDescription(line.substring("description:".length()).trim());
                    }
                }
            }
        }

        return skill;
    }
}
