package com.lastwhispers.harness.ch14.tools;

import com.alibaba.fastjson2.JSON;
import com.alibaba.fastjson2.annotation.JSONField;
import com.lastwhispers.harness.ch14.schema.ToolDefinition;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;

/**
 * 对现有文件进行局部字符串替换。包含四级模糊匹配策略：
 * L1: 精确匹配 -> L2: 换行符归一化 -> L3: Trim 匹配 -> L4: 逐行去缩进匹配
 */
public class EditFileTool implements BaseTool {

    private final String workDir;

    public EditFileTool(String workDir) {
        this.workDir = workDir;
    }

    @Override
    public String name() {
        return "edit_file";
    }

    @Override
    public ToolDefinition definition() {
        Map<String, Object> inputSchema = Map.of(
                "type", "object",
                "properties", Map.of(
                        "path", Map.of(
                                "type", "string",
                                "description", "要修改的文件路径"
                        ),
                        "old_text", Map.of(
                                "type", "string",
                                "description", "文件中原有的文本。必须包含足够的上下文，以确保在文件中的唯一性。"
                        ),
                        "new_text", Map.of(
                                "type", "string",
                                "description", "要替换成的新文本"
                        )
                ),
                "required", List.of("path", "old_text", "new_text")
        );
        return new ToolDefinition(
                name(),
                "对现有文件进行局部的字符串替换。这比重写整个文件更安全、更快速。请提供足够的 old_text 上下文以确保匹配的唯一性。",
                inputSchema
        );
    }

    private static class EditFileArgs {
        @JSONField(name = "path")
        private String path;
        @JSONField(name = "old_text")
        private String oldText;
        @JSONField(name = "new_text")
        private String newText;

        public String getPath() { return path; }
        public void setPath(String path) { this.path = path; }
        public String getOldText() { return oldText; }
        public void setOldText(String oldText) { this.oldText = oldText; }
        public String getNewText() { return newText; }
        public void setNewText(String newText) { this.newText = newText; }
    }

    @Override
    public String execute(String args) throws Exception {
        EditFileArgs input = JSON.parseObject(args, EditFileArgs.class);

        Path fullPath = Path.of(workDir, input.getPath());

        String originalContent;
        try {
            originalContent = Files.readString(fullPath);
        } catch (IOException e) {
            throw new IOException("读取文件失败，请确认路径是否正确: " + e.getMessage(), e);
        }

        String newContent = fuzzyReplace(originalContent, input.getOldText(), input.getNewText());

        Files.writeString(fullPath, newContent);

        return String.format("成功修改文件: %s", input.getPath());
    }

    /**
     * 四级模糊匹配替换策略
     */
    static String fuzzyReplace(String originalContent, String oldText, String newText) {
        // L1: 精确匹配
        int count = countOccurrences(originalContent, oldText);
        if (count == 1) {
            return originalContent.replaceFirst(escapeRegex(oldText), Matcher.quoteReplacement(newText));
        }
        if (count > 1) {
            throw new IllegalArgumentException(
                    String.format("old_text 匹配到了 %d 处，请提供更多的上下文代码以确保唯一性", count));
        }

        // L2: 换行符归一化
        String normalizedContent = originalContent.replace("\r\n", "\n");
        String normalizedOld = oldText.replace("\r\n", "\n");

        count = countOccurrences(normalizedContent, normalizedOld);
        if (count == 1) {
            return normalizedContent.replaceFirst(escapeRegex(normalizedOld), Matcher.quoteReplacement(newText));
        }

        // L3: Trim Space 匹配
        String trimmedOld = normalizedOld.trim();
        if (!trimmedOld.isEmpty()) {
            count = countOccurrences(normalizedContent, trimmedOld);
            if (count == 1) {
                return normalizedContent.replaceFirst(escapeRegex(trimmedOld), Matcher.quoteReplacement(newText));
            }
        }

        // L4: 逐行去缩进匹配
        return lineByLineReplace(normalizedContent, normalizedOld, newText);
    }

    /**
     * 计算子串在字符串中出现的次数（非正则）
     */
    private static int countOccurrences(String haystack, String needle) {
        if (needle.isEmpty()) return 0;
        int count = 0;
        int idx = 0;
        while ((idx = haystack.indexOf(needle, idx)) != -1) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * 转义正则特殊字符
     */
    private static String escapeRegex(String s) {
        return java.util.regex.Pattern.quote(s);
    }

    /**
     * 逐行去缩进匹配：忽略每行前后空白进行匹配，保留原文的缩进风格
     */
    static String lineByLineReplace(String content, String oldText, String newText) {
        String[] contentLines = content.split("\n", -1);
        String[] oldLines = oldText.trim().split("\n");

        if (oldLines.length == 0 || contentLines.length < oldLines.length) {
            throw new IllegalArgumentException("找不到该代码片段");
        }

        // 将 oldText 每行 trim
        String[] trimmedOldLines = new String[oldLines.length];
        for (int i = 0; i < oldLines.length; i++) {
            trimmedOldLines[i] = oldLines[i].trim();
        }

        int matchCount = 0;
        int matchStartIndex = -1;
        int matchEndIndex = -1;

        for (int i = 0; i <= contentLines.length - oldLines.length; i++) {
            boolean isMatch = true;
            for (int j = 0; j < oldLines.length; j++) {
                if (!contentLines[i + j].trim().equals(trimmedOldLines[j])) {
                    isMatch = false;
                    break;
                }
            }
            if (isMatch) {
                matchCount++;
                matchStartIndex = i;
                matchEndIndex = i + oldLines.length;
            }
        }

        if (matchCount == 0) {
            throw new IllegalArgumentException("在文件中未找到 old_text，请检查内容和缩进");
        }
        if (matchCount > 1) {
            throw new IllegalArgumentException(
                    String.format("模糊匹配到了 %d 处代码，请提供更多上下文以定位", matchCount));
        }

        List<String> newContentLines = new ArrayList<>();
        for (int i = 0; i < matchStartIndex; i++) {
            newContentLines.add(contentLines[i]);
        }
        // 将 newText 按行拆分，逐行加入
        for (String line : newText.split("\n", -1)) {
            newContentLines.add(line);
        }
        for (int i = matchEndIndex; i < contentLines.length; i++) {
            newContentLines.add(contentLines[i]);
        }

        return String.join("\n", newContentLines);
    }
}
