# DreamKillEcho AI 修改约束

1. 禁止在主线程做数据库 IO、网络 IO 或重型文件 IO。
2. 禁止添加任何 P2W 功能，包括击杀回血、加伤害、额外金币、额外经验、免掉落、战斗属性加成。
3. 禁止在 Kotlin 源码中硬编码面向玩家的中文消息，必须写入 `lang/zh_cn.yml`。
4. 修改配置结构或新增配置资源必须同步更新 `README.md` 和本文件。
5. 新增命令必须新增权限、`plugin.yml` 声明和 `lang/zh_cn.yml` 消息。
6. 所有调度必须走 `SchedulerAdapter`。
7. 新增表结构必须添加 schema 迁移逻辑。
8. 不允许把敏感词、主题、权限写死在业务源码里；默认值和权限常量除外。
9. 禁止业务代码直接 import Folia 专属类，避免 Spigot 启动失败。
10. 禁止在异步线程操作 Bukkit 实体、世界、粒子、声音、BossBar。
11. PlaceholderAPI 必须保持 softdepend，不允许作为强依赖使用。
12. VIP / SVIP 功能只能是展示型、装饰型、个性化功能。
13. 禁止超大类和超大 package；命令必须拆成 CommandRouter + SubCommand，复杂业务按领域拆分。
14. 语言系统必须支持 `lang/zh_cn.yml` 与 `lang/en_us.yml`，默认语言缺失 key 时必须回退 fallback。
15. Folia 下给玩家发送消息、Title、ActionBar、BossBar 必须走 per-player `SchedulerAdapter.runEntity(player)`。
16. 新增或修改配置字段时必须让字段真实生效，不能只写进 `config.yml` 但业务不读取。
17. 统计数据修改必须串行化或加锁，禁止多个线程直接修改同一个可变 `PlayerStats`。
18. 环境死亡和生物击杀必须走语言文件 `broadcast.*` 消息，不能伪装成未知玩家击杀。
19. 主题仓库 GUI 使用 `gui/theme-menu.yml` 的 `Title` + `GuiPlain` + `GuiKey` + `templates` 结构；`@` 作为自动分页内容位，主题列表必须由 `themes.yml` 自动分页承载，新增主题不要再手工同步 GUI 按钮位，不要把可美化 GUI 文案写死在业务源码中。
20. 修改 `PlayerProfile` 的主题、自定义击杀语、审核状态、播报开关等持久化字段时，必须通过 `StorageService.updateProfile(...)`，禁止外部业务直接修改 profile 后再手工标记 dirty。
21. 新增或修改数据库字段、索引、查询优化时，必须写入幂等 schema migration，并同时兼容 SQLite 与 MySQL。
22. Folia 下不得跨区域读取玩家位置、血量、IP 等实体状态；无法可靠实现的能力必须降级并在 README 或日志中说明。
23. `storage.shutdown-timeout-seconds` 控制关闭时最终 dirty 数据写回等待时间；修改存储生命周期时必须保持可配置并输出 dirtyProfiles / dirtyStats 日志。
24. Folia 下 `broadcast.range-mode: nearby` 和 `card.mode: nearby` 必须退化为 global 或明确安全快照模式，禁止跨区域实时扫描玩家位置。
