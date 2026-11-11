# easy-bpm-sdk（Java）

easy-bpm 审批中台的 Java 客户端：签名鉴权 + 审批闭环 + 增量轮询。

```java
EasyBpmClient client = EasyBpmClient.create("http://localhost:8090", "ak_demo", "sk_demo_2026");
client.start(StartRequest.builder().flowCode("leave").initiator("u1001", "张三")
        .businessKey("LEAVE-001").formData(Map.of("days", 3)).build());
```

- Java 17+，仅依赖 Jackson；`mvn install` 后按 `com.easybpm:easy-bpm-sdk:1.0.0` 引入；
- 完整用法（轮询器、Spring Boot 集成、错误处理、方法对照）：[`docs/sdk-java.md`](../docs/sdk-java.md)；
- 接口语义与错误码：[`docs/integration.md`](../docs/integration.md)。
