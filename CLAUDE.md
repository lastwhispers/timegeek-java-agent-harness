# 项目规范

## JSON 框架
- 统一使用 fastjson2，不使用 Jackson、Gson 等其他 JSON 库
- 序列化/反序列化使用 `JSON.parseObject()` 和 `JSON.toJSONString()`
- POJO 中使用 `@JSONField` 注解控制字段映射
