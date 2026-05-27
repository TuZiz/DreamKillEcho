# DreamKillEcho

DreamKillEcho 是一个 Kotlin + Maven 编写的 Minecraft 击杀播报 / 死亡播报 / VIP 展示插件。插件只提供装饰型、展示型、个性化功能，不提供击杀回血、伤害加成、额外收益、免掉落等 P2W 能力。

## 功能列表

- PlayerDeathEvent 击杀与死亡识别：玩家近战、远程投掷物、生物、环境死亡与 unknown 兜底。
- VIP / MVP / SVIP 击杀主题：通过权限解锁，支持 MiniMessage、十六进制颜色、渐变；未手动选择主题时自动使用玩家拥有的最高 `priority` 主题。
- 展示特效：Title、ActionBar、Sound、Particle、Firework、BossBar，带全局限流。
- 击杀名片：可配置内容，支持仅击杀者、全服、附近玩家。
- 自定义击杀语：长度限制、冷却、敏感词过滤、危险 MiniMessage 标签拦截、审核模式。
- 连杀系统：kills、deaths、current_streak、max_streak、终结连杀、复仇击杀。
- 防刷屏 / 防刷击杀：同击杀者、同受害者、每分钟广播与特效限流、同受害者反复击杀限制，并对反刷记录做 TTL 清理。
- 世界限制：blacklist / whitelist，可分别控制播报、统计、特效。
- 玩家开关：`/dke toggle` 持久化关闭接收普通播报。
- GUI 主题仓库：`/dke theme` 或 `/dke gui` 打开可美化菜单，主题列表按 `themes.yml` 自动分页，新增主题无需同步改按钮位。
- SQLite 默认存储，MySQL 可选，HikariCP 连接池。
- PlaceholderAPI softdepend，存在时通过缓存后的反射桥接解析占位符。

## 兼容说明

项目使用 Spigot API 作为编译 API，目标 Minecraft 1.21.x，`plugin.yml` 声明 `api-version: '1.21'` 与 `folia-supported: true`。

Folia 兼容通过 `SchedulerAdapter` 运行时检测完成，业务代码禁止直接 import Folia 专属类。Folia 环境下实体操作走 entity scheduler，位置操作走 region scheduler，异步任务走 async scheduler；Spigot / Paper 环境下回退 BukkitScheduler。

Folia 下不会直接跨区域读取击杀者位置、血量或 IP。`anti-farm.same-ip-no-stats` 在 Folia 环境会降级为不生效，并在控制台输出警告，避免跨区域线程风险。

## 安装方式

1. 执行 `mvn clean package`。
2. 将 `target/DreamKillEcho-1.0.0.jar` 放入服务器 `plugins` 目录。
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
- `/dke theme list`
- `/dke set <theme>`
- `/dke preview <theme>`
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
- `dreamkillecho.theme.vip`
- `dreamkillecho.theme.vipplus`
- `dreamkillecho.theme.mvp`
- `dreamkillecho.theme.mvpplus`
- `dreamkillecho.theme.svip`
- `dreamkillecho.theme.love`
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
- `effects.bossbar.seconds`：BossBar 展示秒数，最小值为 1，默认 5。
- `anti-farm.same-victim-record-ttl-seconds`：同一击杀者/受害者反刷记录保留时间，默认 600 秒，惰性清理以避免长期运行内存增长。
- `anti-farm.revenge-window-seconds`：复仇判定窗口，默认 600 秒，超过窗口的反向击杀不再触发复仇播报。
- `lang/zh_cn.yml` / `lang/en_us.yml`：所有可见消息。`config.yml` 中的 `language.default` 优先读取，缺失 key 时回退 `language.fallback`。
- `themes.yml`：击杀主题、展示名、权限、自动选择优先级 `priority`、MiniMessage 模板。`priority` 越高，玩家未手动选择主题时越优先自动使用。
- `gui/theme-menu.yml`：主题仓库 GUI 的标题、`GuiPlain` 布局、`GuiKey` 物品样式、`templates` 主题物品模板。`@` 会自动承载 `themes.yml` 中的主题列表，新增主题通常不需要再改这个文件；修改后执行 `/dke reload` 生效。
- `storage.yml`：SQLite / MySQL 连接配置。

## 主题占位符

`themes.yml`、名片、Title、ActionBar、BossBar 等模板支持 MiniMessage 和以下插件占位符：

- `<prefix>`：语言文件中的插件前缀。
- `<killer>`：击杀者名称；环境死亡时为语言文件的 unknown-player。
- `<victim>`：死亡玩家名称。
- `<mob>`：生物或投掷物来源名称。
- `<weapon>`：武器名称，优先显示物品自定义名；原版物品和投掷物会用 Adventure `Component.translatable(...)` 交给客户端按本地语言翻译。
- 该占位符在真正发送给玩家的消息里是组件占位符，预览文本仍会保留可读 fallback。
- `<killer_health>`：击杀者剩余原始血量，例如 20 表示满血。
- `<victim_health>`：死亡玩家剩余原始血量。
- `<distance>`：击杀距离，单位为方块。
- `<streak>`：当前连杀数。
- `<max_streak>`：历史最高连杀数。
- `<death_cause>`：死亡原因 key。
- `<theme>`：当前主题显示名。
- `<server>`：配置中的服务器名称。

## VIP/MVP/SVIP 商城建议

只销售展示型权限，例如主题、Title、ActionBar、粒子、名片、自定义击杀语。不要销售击杀回血、伤害加成、额外金币、额外经验、免掉落、战斗属性等破坏公平性的能力。

## PlaceholderAPI

PlaceholderAPI 是 softdepend。插件存在时，MessageService 会通过轻量 `PlaceholderBridge` 缓存 `setPlaceholders(Player, String)` 方法后解析消息模板；不存在时自动跳过，不会作为强依赖加载。

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

数据库不可用时插件进入降级模式，只使用内存缓存运行，控制台会输出警告。后续玩家加载、查询和定时 flush 会继续尝试重连；dirty profile / stats 不会因为一次 flush 失败被清除，数据库恢复后会尽量写回。

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
- 玩家看不到主题：检查 LuckPerms 是否发放对应 `dreamkillecho.theme.<theme>` 权限。默认会员主题节点为 `dreamkillecho.theme.vip`、`dreamkillecho.theme.vipplus`、`dreamkillecho.theme.mvp`、`dreamkillecho.theme.mvpplus`、`dreamkillecho.theme.svip`，额外个性主题为 `dreamkillecho.theme.love`。
- 自定义击杀语未显示：默认需要审核，通过 `/dke approve <player>`。

## Folia 注意事项

不要在新增业务代码中直接调用 `Bukkit.getScheduler()`，不要 import Folia 专属类。实体、世界、粒子、声音、BossBar 操作必须通过 `SchedulerAdapter` 回到正确调度上下文。

玩家 `PlayerProfile` 的主题、自定义击杀语、审核状态、播报开关等持久化字段必须通过 `StorageService.updateProfile(...)` 修改，避免并发 flush 读到半更新状态。

## Production hardening notes

- Custom kill message permissions are layered: `dreamkillecho.custom.message` only allows setting a message; `dreamkillecho.custom.color` allows color, hex color, `gradient`, and `rainbow` tags; `dreamkillecho.custom.minimessage` allows the configured MiniMessage safe subset.
- Player custom messages still reject `click`, `hover`, `insertion`, `selector`, `score`, `nbt`, `keybind`, and `translatable` tags even with `dreamkillecho.custom.minimessage`. Without `custom.minimessage`, player input is stored as escaped plain text and is not parsed as MiniMessage formatting.
- On Folia, player-kill handling switches to the killer entity scheduler before reading killer name, weapon, health, location, theme, permissions, custom message, or effect state. `anti-farm.same-ip-no-stats` remains degraded on Folia because cross-region player IP reads are not reliable.
- Kill log writes follow `worlds.blocked-world-stats`; worlds where stats are disabled do not write kill log rows.

## TODO

- 第一版已实现核心可运行链路。后续可增强为更细粒度的死亡原因文案表、更多特效模板与单元测试覆盖。
