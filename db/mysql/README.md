# 生产库初始化

1. 建库：

```sql
CREATE DATABASE easy_bpm DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
```

2. 执行本目录 `schema.sql`，创建 easy-bpm 业务表。
3. Flowable 引擎表（`ACT_*`）首次启动由引擎自动创建；正式环境建议首次启动后，将 `flowable.database-schema-update` 设为 `false`。
4. 演示数据默认关闭（`easybpm.demo.enabled=false`），生产如需初始化租户与凭证，请通过控制台或运维脚本完成。

> 业务表与引擎表同库。升级 Flowable 大版本前，请先备份并参考官方升级脚本。
