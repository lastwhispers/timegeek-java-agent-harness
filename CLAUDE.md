# 项目规范

## JSON 框架
- 统一使用 fastjson2，不使用 Jackson、Gson 等其他 JSON 库
- 序列化/反序列化使用 `JSON.parseObject()` 和 `JSON.toJSONString()`
- POJO 中使用 `@JSONField` 注解控制字段映射

## 日志打印
- 统一使用 logback（通过 `@Slf4j` 注解）进行日志输出，禁止使用 `System.out.println` 或 `System.err.println`
- 打印参数、状态、调试信息统一使用 `log.info()` 或 `log.debug()`
- 异常信息使用 `log.error()`
