# DreamKillEcho AI 修改约束

1. 禁止在主线程做数据库 IO、网络 IO 或重型文件 IO。
2. 禁止添加任何 P2W 功能，包括击杀回血、加伤害、额外金币、额外经验、免掉落、战斗属性加成。
3. 禁止在 Kotlin 源码中硬编码面向玩家的中文消息，必须写入 `lang/zh_cn.yml`。
4. 修改配置结构必须同步更新 `README.md`。
5. 新增命令必须新增权限、`plugin.yml` 声明和 `lang/zh_cn.yml` 消息。
6. 所有调度必须走 `SchedulerAdapter`。
7. 新增表结构必须添加 schema 迁移逻辑。
8. 不允许把敏感词、主题、权限写死在业务源码里；默认值和权限常量除外。
9. 禁止业务代码直接 import Folia 专属类，避免 Spigot 启动失败。
10. 禁止在异步线程操作 Bukkit 实体、世界、粒子、声音、BossBar。
11. PlaceholderAPI 必须保持 softdepend，不允许作为强依赖使用。
12. VIP / SVIP 功能只能是展示型、装饰型、个性化功能。
