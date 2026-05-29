# DreamKillEcho

DreamKillEcho 是一个 Kotlin + Maven 编写的 Minecraft 击杀播报 / 死亡播报 / 主题展示插件。插件只提供装饰型、展示型、个性化功能，不提供击杀回血、伤害加成、额外收益、免掉落等 P2W 能力。

## 功能列表

- PlayerDeathEvent 击杀与死亡识别：玩家近战、远程投掷物、生物、环境死亡与 unknown 兜底。
- 击杀播报主题：通过权限解锁，支持 MiniMessage、十六进制颜色、渐变；未手动选择主题时固定使用 `default`，主题可配置展示用 `rarity` 稀有度。
- 展示特效：Title、ActionBar、Sound、Particle、Firework 模拟粒子、BossBar，带全局限流。
- 击杀名片：可配置内容，支持仅击杀者、全服、附近玩家。
- 连杀系统：kills、deaths、current_streak、max_streak、终结连杀、复仇击杀。
- 防刷屏 / 防刷击杀：同击杀者、同受害者、每分钟广播与特效限流、同受害者反复击杀限制，并对反刷记录做 TTL 清理。
- 世界限制：blacklist / whitelist，可分别控制播报、统计、特效。
- 玩家开关：`/dke toggle` 持久化关闭接收普通播报。
- GUI 主题仓库：`/dke theme` 或 `/dke gui` 打开可美化菜单，主题列表按 `themes.yml` 自动分页，新增主题无需同步改按钮位。
- SQLite 默认存储，MySQL 可选，HikariCP 连接池。
- PlaceholderAPI softdepend，存在时通过缓存后的反射桥接解析占位符，并注册主题展示变量。

## 兼容说明

项目使用 Spigot API 作为编译 API，目标 Minecraft 1.21.x，`plugin.yml` 声明 `api-version: '1.21'` 与 `folia-supported: true`。

Folia 兼容通过 `SchedulerAdapter` 运行时检测完成，业务代码禁止直接 import Folia 专属类。Folia 环境下实体操作走 entity scheduler，位置操作走 region scheduler，异步任务走 async scheduler；Spigot / Paper 环境下回退 BukkitScheduler。

Folia 下不会直接跨区域读取击杀者位置、血量或 IP。`anti-farm.same-ip-no-stats` 在 Folia 环境会降级为不生效，并在控制台输出警告，避免跨区域线程风险。

Folia 下 `broadcast.range-mode: nearby` 和 `card.mode: nearby` 不做跨区域位置扫描，会退化为 `global` 接收范围；真正发送消息、Title、ActionBar、BossBar 仍会通过每个接收玩家的 entity scheduler 执行。

## 安装方式

1. 执行 `mvn clean package`。
2. 将 `target/dreamkillecho-1.0.0.jar` 放入服务器 `plugins` 目录。
3. 启动服务器，修改生成的 `config.yml`、`themes.yml`、`gui/theme-menu.yml`、`lang/zh_cn.yml`、`lang/en_us.yml`、`storage.yml`。
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
- `/dke theme`
- `/dke gui`
- `/dke set <theme>`
- `/dke custom set <message...>`
- `/dke custom preview <message...>`
- `/dke custom reset`
- `/dke stats [player]`
- `/dke top kills`
- `/dke top streak`
- `/dke review`
- `/dke approve <player>`
- `/dke deny <player>`
- `/dke resetstats <player>`

普通玩家 `/dke stats` 只能查看自己；查看他人统计、排行榜、重置统计和自定义击杀语审核都需要对应管理权限。

## 权限

基础权限见 `plugin.yml`。常用节点：

- `dreamkillecho.use`
- `dreamkillecho.toggle`
- `dreamkillecho.default`
- `dreamkillecho.blaze`
- `dreamkillecho.vanguard`
- `dreamkillecho.nebula`
- `dreamkillecho.frost`
- `dreamkillecho.judgment`
- `dreamkillecho.love`
- `dreamkillecho.phantom`
- `dreamkillecho.crimson`
- `dreamkillecho.abyss`
- `dreamkillecho.holy`
- `dreamkillecho.dragon`
- `dreamkillecho.custom.message`
- `dreamkillecho.custom.color`
- `dreamkillecho.custom.minimessage`
- `dreamkillecho.effect.title`
- `dreamkillecho.effect.actionbar`
- `dreamkillecho.effect.sound`
- `dreamkillecho.effect.particle`
- `dreamkillecho.effect.firework`
- `dreamkillecho.effect.bossbar`
- `dreamkillecho.card.vip`
- `dreamkillecho.card.svip`
- `dreamkillecho.admin.reload`
- `dreamkillecho.admin.stats`
- `dreamkillecho.admin.review`
- `dreamkillecho.admin.resetstats`
- `dreamkillecho.admin.bypass`

`themes.yml` 中每个主题的 `permission` 字段必须和实际权限节点一致，当前默认主题节点都已在 `plugin.yml` 中声明。

## 配置说明

- `config.yml`：世界限制、广播范围、名片、特效、连杀、防刷、刷新周期。
- `broadcast.range-mode` / `card.mode`：支持 `global`、`nearby`、`killer` 或名片的 `killer` 模式；Folia 下 `nearby` 不跨区域读取玩家位置，会安全退化为 `global` 接收范围。
- `effects.bossbar.seconds`：BossBar 展示秒数，最小值为 1，默认 5。
- `effects.firework`：默认关闭；启用后只模拟烟花粒子和声音，不生成真实烟花火箭实体，不造成伤害。
- `custom-message.*`：自定义击杀语长度、冷却、审核、屏蔽词和 MiniMessage 标签安全策略。普通玩家需要 `dreamkillecho.custom.message` 才能设置，颜色类标签需要 `dreamkillecho.custom.color`，完整安全子集需要 `dreamkillecho.custom.minimessage`。
- `anti-farm.same-victim-record-ttl-seconds`：同一击杀者/受害者反刷记录保留时间，默认 600 秒，惰性清理以避免长期运行内存增长。
- `anti-farm.revenge-window-seconds`：复仇判定窗口，默认 600 秒，超过窗口的反向击杀不再触发复仇播报。
- `storage.flush-interval-seconds`：定时 flush 间隔，默认 120 秒。
- `storage.shutdown-timeout-seconds`：插件关闭时等待最终 dirty 数据写回的最长秒数，默认 5 秒。
- `lang/zh_cn.yml` / `lang/en_us.yml`：所有可见消息。`config.yml` 中的 `language.default` 优先读取，缺失 key 时回退 `language.fallback`；服务器已有旧语言文件缺少新版 key 时，会在内存中补齐 jar 内默认值，不覆盖你的自定义文件。
- `themes.yml`：击杀主题、展示名、权限、展示用稀有度 `rarity`、MiniMessage 模板。玩家未手动选择主题时固定使用 `default`，不会按权限自动选择更高稀有度主题。
- `gui/theme-menu.yml`：主题仓库 GUI 的标题、`GuiPlain` 布局、`GuiKey` 物品样式、`templates` 主题物品模板。默认主题 lore 只展示稀有度、状态和播报示例，不展示内部主题 ID 或权限节点；旧配置里残留的主题 ID / 权限 lore 行会在加载时隐藏；`@` 会自动承载 `themes.yml` 中的主题列表，新增主题通常不需要再改这个文件；修改后执行 `/dke reload` 生效。
- `storage.yml`：SQLite / MySQL 连接配置。

## 主题占位符

`themes.yml`、名片、Title、ActionBar、BossBar 等模板支持 MiniMessage 和以下插件占位符：

- `<prefix>`：语言文件中的插件前缀。
- `<killer>`：击杀者名称；环境死亡时为语言文件的 unknown-player。
- `<victim>`：死亡玩家名称。
- `<mob>`：生物或投掷物来源名称。
- `<weapon>`：武器名称，优先显示物品自定义名；原版物品和投掷物优先读取 `lang/<language>.yml` 中的 `weapon.material.*` / `weapon.projectile.*` 显示名，未配置时回退为可读英文 key。
- 该占位符在真正发送给玩家的消息和主题预览里都会使用同一套语言文件显示名；真实击杀播报中的武器文本带鼠标悬停提示，会显示名称、类型、数量、耐久、附魔和 lore 摘要。
- `<killer_health>`：击杀者剩余原始血量，例如 20 表示满血。
- `<victim_health>`：死亡玩家剩余原始血量。
- `<distance>`：击杀距离，单位为方块。
- `<streak>`：当前连杀数。
- `<max_streak>`：历史最高连杀数。
- `<death_cause>`：死亡原因 key。
- `<theme>`：当前主题显示名。
- `<theme_id>`：当前主题 ID。
- `<rarity>` / `<theme_rarity>`：当前主题稀有度，来自 `themes.yml` 的 `rarity` 字段。
- `<server>`：配置中的服务器名称。

## 商城建议

只销售展示型权限，例如主题、Title、ActionBar、粒子、名片。不要销售击杀回血、伤害加成、额外金币、额外经验、免掉落、战斗属性等破坏公平性的能力。

## PlaceholderAPI

PlaceholderAPI 是 softdepend。插件存在时，MessageService 会通过轻量 `PlaceholderBridge` 缓存 `setPlaceholders(Player, String)` 方法后解析消息模板；不存在时自动跳过，不会作为强依赖加载。

本插件会在 PlaceholderAPI 存在时注册 `dreamkillecho` expansion：

- `%dreamkillecho_theme%`：玩家当前主题显示名；未选择时为默认主题。
- `%dreamkillecho_theme_id%`：玩家当前主题 ID。
- `%dreamkillecho_theme_rarity%` / `%dreamkillecho_rarity%`：玩家当前主题稀有度。

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

数据库不可用时插件进入降级模式，只使用内存缓存运行，控制台会输出警告。后续玩家加载、查询和定时 flush 会继续尝试重连；dirty profile / stats 不会因为一次 flush 失败被清除。玩家刚进服未完成 stats 加载时，击杀统计会先异步加载旧数据再应用增量，避免用 0 基线覆盖旧统计。

MySQL 连接 URL 默认使用 `characterEncoding=utf8mb4`，用于正常保存中文和 emoji 文本。

Shade 配置中没有对 `sqlite-jdbc` 和 `mysql-connector-j` 做 relocation：这两个驱动依赖标准 JDBC 驱动类名与服务注册，重定位会增加 `DriverManager` 识别风险。当前做法是显式 `Class.forName(...)` 加载驱动，冲突风险主要来自最终包内同时包含多个 JDBC 驱动服务描述，已接受该风险并保留明确的驱动类名。

启动时会执行幂等 schema migration。当前迁移会保留已有表结构，补充 `players.name_lower` 并填充历史数据，同时创建 `players.name_lower`、自定义消息审核、`stats.kills`、`stats.max_streak`、`kill_logs.created_at`、`kill_logs.killer_uuid`、`kill_logs.victim_uuid` 等索引，SQLite 与 MySQL 均按同一版本序列执行。

## 测试建议

- Spigot：启动 1.21.1 Spigot，确认插件加载、`/dke help`、SQLite 建表、死亡播报。
- Paper：启动 1.21.1 Paper，测试 PlaceholderAPI、粒子、声音、BossBar。
- Folia：启动 Folia 1.21.x，重点测试击杀事件、实体特效、区域调度、退出保存，并确认 `same-ip-no-stats` 降级日志符合预期。
- MySQL：切换 `storage.type: mysql` 后重启，确认建表与数据持久化。

## 常见问题

- 修改主题后没生效：执行 `/dke reload`。
- 修改 GUI 后没生效：确认修改的是 `plugins/DreamKillEcho/gui/theme-menu.yml`，并且 `GuiPlain`、`GuiKey`、`templates` 的字符与函数定义正确；如果只是新增主题，一般只需要改 `themes.yml`，然后执行 `/dke reload`。
- 修改 `storage.type` 后没切换：存储连接池不会热切换，需要重启。
- 玩家看不到主题：检查 LuckPerms 是否发放对应 `dreamkillecho.<theme>` 权限。默认主题节点为 `dreamkillecho.default`，其余内置主题节点为 `dreamkillecho.blaze`、`dreamkillecho.vanguard`、`dreamkillecho.love`、`dreamkillecho.phantom`、`dreamkillecho.crimson`、`dreamkillecho.abyss`、`dreamkillecho.nebula`、`dreamkillecho.frost`、`dreamkillecho.holy`、`dreamkillecho.judgment`、`dreamkillecho.dragon`。
- 自定义击杀语没有显示：确认已开启 `custom-message.use-as-theme-message`，且内容在需要审核时已由管理员 `/dke approve <player>` 批准。

## Folia 注意事项

不要在新增业务代码中直接调用 `Bukkit.getScheduler()`，不要 import Folia 专属类。实体、世界、粒子、声音、BossBar 操作必须通过 `SchedulerAdapter` 回到正确调度上下文。

玩家 `PlayerProfile` 的主题、审核状态、播报开关等持久化字段必须通过 `StorageService.updateProfile(...)` 修改，避免并发 flush 读到半更新状态。

## Production hardening notes

- On Folia, player-kill handling switches to the killer entity scheduler before reading killer name, weapon, health, location, theme, permissions, or effect state. `anti-farm.same-ip-no-stats` remains degraded on Folia because cross-region player IP reads are not reliable.
- Kill log writes follow `worlds.blocked-world-stats`; worlds where stats are disabled do not write kill log rows.

## TODO

- 第一版已实现核心可运行链路。后续可增强为更细粒度的死亡原因文案表、更多特效模板与单元测试覆盖。
