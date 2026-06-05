package com.lastwhispers.harness.ch02.util;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

// 加载 .env 文件，将 KEY=VALUE 写入系统属性，使 resolve() 方法可读取
@Slf4j
public class Dotenv {

    // 默认加载项目根目录下的 .env 文件
    public static void load() {
        load(Path.of(System.getProperty("user.dir"), ".env"));
    }

    public static void load(Path path) {
        if (!Files.exists(path)) {
            log.warn("[Dotenv] .env 文件不存在，跳过: {}", path);
            return;
        }
        try {
            for (String line : Files.readAllLines(path)) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) {
                    continue;
                }
                int eqIdx = line.indexOf('=');
                if (eqIdx <= 0) {
                    continue;
                }
                String key = line.substring(0, eqIdx).trim();
                String value = line.substring(eqIdx + 1).trim();
                System.setProperty(key, value);
            }
            log.info("[Dotenv] 已从 {} 加载环境变量", path);
        } catch (IOException e) {
            log.warn("[Dotenv] 加载 .env 文件失败: {}", e.getMessage());
        }
    }
}
