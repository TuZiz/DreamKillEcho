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
19. 主题仓库 GUI 使用 `gui/theme-menu.yml` 映射标题、槽位和物品样式；不要把可美化 GUI 文案写死在业务源码中。
