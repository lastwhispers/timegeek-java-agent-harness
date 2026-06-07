# TODO

## 工程基础设施
- [x] 1. 更新 build.gradle（依赖、仓库、测试配置、jar/run task）
- [x] 2. 更新 settings.gradle 项目名称
- [x] 3. 创建 build.sh 构建脚本
- [x] 4. 创建 Dockerfile
- [x] 5. 添加 .gitignore（忽略 .gradle/）

## 核心工具与模型
- [x] 6. 创建 server.util.JsonUtil 工具类
- [x] 7. 创建 server.model.ApiError 错误模型

## 核心架构
- [ ] 8. 重构 Main.java（/api/v1 前缀、统一异常处理、优雅停机、JSON 根路径）
- [ ] 9. 重构 HealthHandler（uptime、version、pretty JSON）
- [ ] 10. 重构 HelloHandler（pretty JSON）

## API 端点
- [ ] 11. 创建 PingHandler
- [ ] 12. 创建 EchoHandler
- [ ] 13. 创建 MetricsHandler（JMX）
- [ ] 14. 创建 VersionHandler

## 事件系统
- [x] 15. 创建 TaskEvent 事件类
- [x] 16. 创建 TaskEventListener 接口
- [x] 17. 创建 TaskEventPublisher 发布器

## 高级功能
- [ ] 18. 创建 RateLimiter 限流中间件
- [ ] 19. 创建 TaskRepository + FileTaskRepository（JSON持久化）
- [ ] 20. 创建 TaskService（CRUD + 状态机 + 事件发布）
- [ ] 21. 创建 TaskHandler（REST CRUD + 状态转移）
- [ ] 22. 创建 RetryService（重试 + 熔断）

## 测试与验证
- [ ] 23. 创建集成测试 ServerIntegrationTest.java
- [ ] 24. 编译构建测试
- [ ] 25. 启动服务 + curl 测试所有端点
- [ ] 26. Docker 构建 + 容器运行测试
