# PLAN - Mini Java HTTP Server

## 项目概述
一个基于 JDK 内置 `com.sun.net.httpserver.HttpServer` 的轻量 HTTP 服务器，零依赖，JDK 17+。

## 当前架构
```
src/server/
  Main.java              → 入口，端口 8080，路由注册
  handler/
    HelloHandler.java    → GET /hello?name=X → JSON 问候
    HealthHandler.java   → GET /health → {"status":"UP"}
```

## 设计目标
在现有骨架上进行增量迭代，保持零依赖、单模块、极简风格。

## 本轮迭代内容
1. **修复编译警告** — Main.java 有未使用的 `OutputStream` 导入
2. **增强 HealthHandler** — 加入 `timestamp` 和 `uptimeSeconds`，让健康检查真正有用
3. **添加优雅关闭** — ShutdownHook，SIGTERM 时平滑退出
4. **添加 404 兜底** — 未知路由返回统一 JSON 404
5. **构建脚本** — 提供 Makefile
