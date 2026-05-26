# DreamKillEcho

DreamKillEcho 是一个 Kotlin + Maven 编写的 Minecraft 击杀播报 / 死亡播报 / VIP 展示插件。插件只提供装饰型、展示型、个性化功能，不提供击杀回血、伤害加成、额外收益、免掉落等 P2W 能力。

## 功能列表

- PlayerDeathEvent 击杀与死亡识别：玩家近战、远程投掷物、生物、环境死亡与 unknown 兜底。
- VIP / SVIP 击杀主题：通过权限解锁，支持 MiniMessage、十六进制颜色、渐变。
- 展示特效：Title、ActionBar、Sound、Particle、Firework、BossBar，带全局限流。
- 击杀名片：可配置内容，支持仅击杀者、全服、附近玩家。
- 自定义击杀语：长度限制、冷却、敏感词过滤、危险 MiniMessage 标签拦截、审核模式。
- 连杀系统：kills、deaths、current_streak、max_streak、终结连杀、复仇击杀。
- 防刷屏 / 防刷击杀：同击杀者、同受害者、每分钟广播与特效限流、同受害者反复击杀限制。
- 世界限制：blacklist / whitelist，可分别控制播报、统计、特效。
- 玩家开关：`/dke toggle` 持久化关闭接收普通播报。
- SQLite 默认存储，MySQL 可选，HikariCP 连接池。
- PlaceholderAPI softdepend，存在时通过反射解析占位符。

## 兼容说明

项目使用 Spigot API 作为编译 API，目标 Minecraft 1.21.x，`plugin.yml` 声明 `api-version: '1.21'` 与 `folia-supported: true`。

Folia 兼容通过 `SchedulerAdapter` 运行时检测完成，业务代码禁止直接 import Folia 专属类。Folia 环境下实体操作走 entity scheduler，位置操作走 region scheduler，异步任务走 async scheduler；Spigot / Paper 环境下回退 BukkitScheduler。

## 安装方式

1. 执行 `mvn clean package`。
2. 将 `target/DreamKillEcho-1.0.0.jar` 放入服务器 `plugins` 目录。
3. 启动服务器，修改生成的 `config.yml`、`themes.yml`、`lang/zh_cn.yml`、`storage.yml`。
4. 使用 `/dke reload` 热重载非存储连接类型配置。修改 `storage.type` 后需要重启。

## Maven 构建

```bash
mvn clean package
```

最终 jar 由 Maven Shade Plugin 打包，包含 Kotlin stdlib、Adventure MiniMessage、adventure-platform-bukkit、HikariCP、SQLite JDBC、MySQL Connector/J。

## 命令

- `/dreamkillecho help`，别名 `/dke`、`/killecho`
- `/dke reload`
- `/dke toggle`
- `/dke theme list`
- `/dke theme set <theme>`
- `/dke theme preview <theme>`
- `/dke custom set <message>`
- `/dke custom preview`
- `/dke custom reset`
- `/dke custom status`
- `/dke review`
- `/dke approve <player>`
- `/dke deny <player>`
- `/dke stats <player>`
- `/dke top kills`
- `/dke top streak`
- `/dke resetstats <player>`

## 权限

基础权限见 `plugin.yml`。常用节点：

- `dreamkillecho.use`
- `dreamkillecho.toggle`
- `dreamkillecho.theme.default`
- `dreamkillecho.theme.vip.star`
- `dreamkillecho.theme.vip.cyber`
- `dreamkillecho.theme.svip.god`
- `dreamkillecho.theme.svip.immortal`
- `dreamkillecho.theme.svip.love`
- `dreamkillecho.effect.title`
- `dreamkillecho.effect.actionbar`
- `dreamkillecho.effect.sound`
- `dreamkillecho.effect.particle`
- `dreamkillecho.effect.firework`
- `dreamkillecho.effect.bossbar`
- `dreamkillecho.card.vip`
- `dreamkillecho.card.svip`
- `dreamkillecho.custom.message`
- `dreamkillecho.custom.color`
- `dreamkillecho.custom.minimessage`
- `dreamkillecho.admin.reload`
- `dreamkillecho.admin.review`
- `dreamkillecho.admin.stats`
- `dreamkillecho.admin.resetstats`
- `dreamkillecho.admin.bypass`

## 配置说明

- `config.yml`：世界限制、广播范围、名片、特效、连杀、防刷、刷新周期。
- `lang/zh_cn.yml`：所有可见消息。不要在源码里写用户可见中文。
- `themes.yml`：击杀主题、展示名、权限、MiniMessage 模板。
- `storage.yml`：SQLite / MySQL 连接配置。

## VIP/SVIP 商城建议

只销售展示型权限，例如主题、Title、ActionBar、粒子、名片、自定义击杀语。不要销售击杀回血、伤害加成、额外金币、额外经验、免掉落、战斗属性等破坏公平性的能力。

## PlaceholderAPI

PlaceholderAPI 是 softdepend。插件存在时，MessageService 会通过反射调用 PlaceholderAPI 解析消息模板；不存在时自动跳过。

## SQLite / MySQL

默认 SQLite：

```yaml
storage:
  type: sqlite
sqlite:
  file: "data.db"
```

MySQL：

```yaml
storage:
  type: mysql
mysql:
  host: "localhost"
  port: 3306
  database: "dreamkillecho"
  username: "root"
  password: "password"
```

数据库不可用时插件进入降级模式，只使用内存缓存运行，控制台会输出警告。

## 测试建议

- Spigot：启动 1.21.1 Spigot，确认插件加载、`/dke help`、SQLite 建表、死亡播报。
- Paper：启动 1.21.1 Paper，测试 PlaceholderAPI、粒子、声音、BossBar。
- Folia：启动 Folia 1.21.x，重点测试击杀事件、实体特效、区域调度、退出保存。
- MySQL：切换 `storage.type: mysql` 后重启，确认建表与数据持久化。

## 常见问题

- 修改主题后没生效：执行 `/dke reload`。
- 修改 `storage.type` 后没切换：存储连接池不会热切换，需要重启。
- 玩家看不到主题：检查 LuckPerms 是否发放对应 `dreamkillecho.theme.*` 权限。
- 自定义击杀语未显示：默认需要审核，通过 `/dke approve <player>`。

## Folia 注意事项

不要在新增业务代码中直接调用 `Bukkit.getScheduler()`，不要 import Folia 专属类。实体、世界、粒子、声音、BossBar 操作必须通过 `SchedulerAdapter` 回到正确调度上下文。

## TODO

- 第一版已实现核心可运行链路。后续可增强为更细粒度的死亡原因文案表、更多特效模板、离线玩家审核查询与单元测试覆盖。
