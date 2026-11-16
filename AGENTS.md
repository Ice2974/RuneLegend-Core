本文件是给 Agent 使用的仓库工程规则，只记录项目技术约束、修改边界和验证要求，不维护项目当前功能状态、令牌清单、版本清单或发布信息。

## 信息来源

- 项目事实以当前源码、`gradle.properties`、`fabric.mod.json`、`build.gradle`、README 和 `docs/` 为准；本文件只提供工作规则。
- 修改前先阅读与任务相关的源码、Mixin 配置、资源和测试，不要只根据文件名或旧版本经验猜测实现。
- 《圣符传说》Wiki 或其他外部资料只是设计参考；实现细节必须以本仓库源码和用户明确确认的规格为准。
- 开始修改前先运行 `git status`。工作区可能已有未提交修改，不要回滚、格式化或删除无关文件。
- 如果本文件与源码冲突，以源码为准，并在回复中说明冲突。

## 基本原则

- 本项目按合作类游戏设计，不考虑玩家之间的对战（PvP）；任何令牌效果都不得对玩家造成伤害。
- 优先做最小必要修改，不做无关重构、依赖升级、格式化或架构调整。
- 本项目同时只维护一个版本，不做多版本兼容、旧版本回退或 Snapshot 适配。
- 不能验证的内容要明确说明，不得把推测写成已测试或已确认。
- 发现无关问题时先记录为待人工确认项，不要顺手修改。
- 涉及原作行为时区分“已确认规格”“Wiki 参考值”和“当前实现”；缺少关键数值时列入待确认项。

## 目录边界

| 路径 | 职责与约束 |
| - | --- |
| `src/main/java/com/runelegend` | 服务端与公共逻辑：入口、游戏状态、令牌引擎、玩家令牌装备存储、物品锁定、命令、网络、服务端 Mixin。不得引用 `net.minecraft.client.*` 或 `com.runelegend.client.*`。 |
| `src/client/java/com/runelegend/client` | 仅客户端逻辑：输入辅助、网络缓存、HUD、物品锁定的预测抑制、客户端 Mixin。不得执行令牌判定、伤害计算、体力规则或游戏状态变更。 |
| `src/main/resources` | 公共 `fabric.mod.json`、服务端 Mixin 配置、语言文件、伤害类型和数据 Tag。 |
| `src/client/resources` | 客户端 Mixin 配置和 HUD 贴图；不得放置服务端判定数据。 |
| `src/test/java` | 纯 JUnit 单元测试，尽量不启动 Minecraft 运行时。 |
| `src/gametest/java` | Fabric GameTest 与客户端 GameTest；新增测试类必须同步登记 `src/gametest/resources/fabric.mod.json` 的 entrypoint。 |
| `docs/` | 用户与关卡开发文档；不要在此放置生成物或本机配置。 |
| `build/`、`.gradle/`、`.gradle-local/`、`.minecraft/` | 生成物或本机测试环境，不要提交、清理或当作项目事实来源。 |

## 固定标识与版本

| 标识 | 固定值 |
| - | --- |
| Mod ID / resource namespace | `runelegend` |
| 项目显示名 | `RuneLegend Core` |
| Maven group | `com.runelegend` |
| Java package | `com.runelegend` |
| Common 入口 | `com.runelegend.RuneLegendMod` |
| Client 入口 | `com.runelegend.client.RuneLegendClient` |
| 归档名 | `RuneLegend-Core` |

- Minecraft、Loader、Fabric API、Loom 和 mod 版本以 `gradle.properties` 为单一来源；修改后同步检查 `fabric.mod.json` 的 `depends` 版本范围，不要在 Java 或文档中再维护一套可漂移的版本清单。
- Java 目标为 25，由 `build.gradle` 的 toolchain 与 `settings.gradle` 的 Foojay resolver 解析；不要把本机 JDK 绝对路径提交到仓库。
- 修改协议、数据格式或资源命名前先确认兼容策略，不要静默改变已有玩家数据或客户端握手行为。
- `com.runelegend.api` 是面向其他服务端模组的公共入口；修改其方法签名或 `RuleSnapshot` 语义前先确认兼容策略。

## 架构不变式

- 服务器是唯一权威。令牌触发、伤害、体力、冷却、游戏状态和装备变更都在服务端主线程判定；客户端只负责展示、输入时序辅助和失序重同步，不作权威判定。
- Loom 使用 `splitEnvironmentSourceSets()`。新增类时先判断 Common/Client 归属，禁止通过反射或字符串类名绕过源集边界。
- 游戏状态只有 `WAITING` 与 `RUNNING`：等待状态允许装备变更并禁止全部令牌效果；运行状态禁改装备并允许令牌系统工作。状态只保存在服务端内存，不写盘，服务器重启后从 `WAITING`、回合 0 开始；只有 `WAITING`→`RUNNING` 推进回合计数，`stop` 保留回合数。状态切换必须清理回合运行态并推进修订号。
- 剑格挡基于原版 `minecraft:blocks_attacks` 物品组件，不维护独立全局格挡开关。地图作者通过物品组件控制哪些物品能挡；本项目不自动给所有剑添加组件。
- 体力门槛、消耗、耗尽停止和松键要求由服务端管理。客户端输入补丁只解决原版按住 Use 重试与松键包时序，不能形成第二套格挡规则。
- 格挡体力参数（上限、起手阈值、消耗、恢复和延迟）统一由 `GuardConfig` 读取并在 `guard.json` 暴露，不得在格挡逻辑中硬编码。
- `guard.json` 与 `tokens.json` 只在服务器启动时读取一次，不提供运行时热重载。
- 格挡方向和减伤由原版结算；只为缺失或不准确的来源坐标提供窄桥接。箭按飞行方向反推，爆炸按真实爆心，不借用射手或引爆者位置。
- 原版动画与实体同步优先；不要恢复自定义 GuardPose 协议。

## 令牌系统与伤害语义

- 令牌通过 `TokenRegistry` 注册，效果实现 `TokenEffectHandler`，运行状态按玩家 UUID 和令牌 ID 隔离；不同玩家不得共享冷却、充能或触发状态。
- 每个令牌的全部可调数值参数（包括但不限于冷却时间、持续时间、触发概率、伤害、半径和效果专属参数）必须进入 `TokenConfig` 的 typed settings 并在配置文件中暴露；效果类不得硬编码这些数值。
- 效果必须事件驱动，只有确实需要时间推进的效果才使用调度器，不要为每枚令牌做完整轮询。
- 令牌自产伤害不得递归触发其他令牌；新增伤害路径必须保留归因和嵌套命中隔离。
- 停局、死亡或断线时清理 owned effect、调度任务、冷却和回合内状态（包括灵魂摄取等临时属性修饰符），不允许效果跨局或跨重连残留。
- **伤害是否无视护甲和防护附魔必须逐令牌按确认规格实现，不能假设原作所有令牌都穿透。** 
- 新增效果时显式选择三类通道之一：
  1. 穿透直接伤害：使用 `runelegend:token_damage` 或 `TokenEffectHandler.addMeleeDamage` 分离追加通道；无视护甲和防护附魔，但保留抗性提升、伤害吸收等非装备减伤。
  2. 普通直接伤害：使用合适的原版伤害来源或并入基础命中，正常经过护甲、防护附魔和原版战斗规则。
  3. 减益伤害：中毒、凋零等 MobEffect 造成的伤害保持各自原版规则，不改伤害类型或 Tag。
- `runelegend:token_damage` 不是“令牌伤害”的通用标签，只代表需要绕过装备减伤的直接伤害；普通令牌伤害和减益伤害不得加入该类型。
- `TokenEffectHandler.addMeleeDamage` 表示与基础近战分离且需要绕过装备减伤的追加伤害；规格要求普通伤害的近战令牌不得复用该通道。若普通追加令牌增多，先重构显式伤害策略，不重载现有语义。
- 新增或修改伤害语义的 GameTest 必须覆盖护甲、防护附魔、抗性提升、基础近战不受意外污染，以及规格要求的普通/穿透差异。

## 网络、存储与数据包

- 自定义协议版本位于 `RuneLegendPayloads.PROTOCOL`。修改包形状、字段语义或握手规则时递增协议号，并同步服务端、客户端、握手测试和 README 迁移说明。
- 配置阶段握手拒绝缺模组和协议不匹配客户端；不要把缺模组客户端降级为部分功能模式。
- 本人状态使用快照加增量同步，不得恢复每 tick 全量广播；保留客户端失序检测和服务端重同步限频。
- 令牌装备以 UUID 为键保存在实例配置目录的 `runelegend-core/players.json`（`schemaVersion: 1`），使用 `equippedTokens` 字段。装备变更须原子落盘后才生效；读写失败时进入只读模式，禁止用空数据覆盖既有文件。
- 数据 Tag 是地图扩展接口。修改默认 Tag 时保持 `replace: false`，并检查数据包覆盖与重载行为。
- `token_enemies`、`bosses`、`ghost_family` 分类实体类型，不支持按单只实体推断；不要用名称、Boss Bar 或位置自动分类。

## 物品锁定

- 锁定标记固定为物品 `minecraft:custom_data` 组件中的布尔键 `runelegend:item_lock`（`1b`）；只有布尔 `true` 生效，其他类型或嵌套路径不视为锁定。不要改成全局开关或按槽位、物品 ID 锁定。
- 规则集中在 `ItemLockPolicy`，服务端和客户端预测共用；Mixin 只桥接。客户端 Mixin 只抑制本地预测与界面操作，服务端必须独立拒绝并强制下发权威物品栏、容器或装备状态，即使内容未变化。
- 仅约束非创造模式玩家的主动转移：容器/物品栏点击、快速移动、数字键与副手交换、拖动、双击收集、丢弃、配方放置、交易选择、装备（含盔甲架和实体装备）以及方块/实体选取。
- 锁定是逐物品堆叠的组件状态，不属于槽位或容器；不得传播到嵌套内容，也不得阻止普通使用（进食、放入物品展示框）。
- 死亡掉落、物品成为掉落物后的行为、创造模式玩家以及非玩家途径（命令、方块逻辑等）不受锁定约束。
- 修改锁定语义或新增玩家转移入口时，必须同时更新服务端拦截、客户端预测抑制和 `ItemLockGameTests` / `ItemLockTransferGameTests` / `ItemLockClientGameTest`。

## Mixin 维护

- Mixin 只用于 Fabric API 无法表达的窄钩子；新增前先确认没有合适的 Fabric 事件、回调或原版组件。
- 每个 Mixin 绑定当前 `gradle.properties` 中 Minecraft 版本的真实方法签名和调用点。修改前用 Loom 缓存、官方 JAR 或可编译源码核对，不凭旧版本经验写注入点。
- 服务端与客户端 Mixin 分别登记在 `runelegend.mixins.json`、`runelegend.client.mixins.json`；删除类时同步删除配置项。
- 共享注入点使用明确的 `require` / `allow` 约束；战斗 Mixin 修改后必须跑完整 GameTest。
- Mixin 只桥接事件或调用，业务规则放回对应服务，不在 Mixin 中堆逻辑。

## 玩家可见文本与资源

- 新增玩家可见文本必须同时更新 `zh_cn.json` 与 `en_us.json`；不要在命令、Mixin 或效果类中硬编码长文本。
- 修改命令、配置字段、数据标签或玩家可见行为时，同步更新受影响的 `README.md` 或 `docs/` 文档。
- HUD 是展示层。令牌栏在非 `RUNNING` 状态隐藏；体力 HUD 独立显示。

## 构建与验证

Windows 下优先使用 `.\gradlew.bat`。分级验证：

| 修改类型 | 最低验证 |
| - | --- |
| 仅 Markdown | `git diff --check`（新增未跟踪文件先 `git add -N`） |
| Common 业务逻辑、令牌、玩家数据、命令或网络 | `.\gradlew.bat build`；涉及网络或客户端状态时追加 `runClientGameTest` |
| 物品锁定、客户端预测抑制 | `.\gradlew.bat build runClientGameTest` |
| Client HUD、输入或贴图 | `.\gradlew.bat build runClientGameTest`，视觉变化需截图核对 |
| Mixin 注入点或配置 | `.\gradlew.bat build`，并运行相关 GameTest |
| 纯单元测试 | `.\gradlew.bat test` |

- `build` 会执行单元测试和服务端 GameTest；不要只编译后宣称测试通过。
- 涉及多人、实体追踪、延迟、Dedicated Server 或协议兼容时，至少用发行 JAR 做实机或探针验证，并说明验证范围。
- 本机 Gradle 可能需要会话级 JDK 25 与临时目录环境变量；这些设置不得写入提交文件。
- 因环境限制无法验证时，明确说明未验证项、原因和风险。

## 默认不要修改

除非任务明确要求，默认不要修改：

- `README.md` 与 `AGENTS.md`
- 既有语言键和贴图

## 回复要求

完成任务后说明修改文件与原因、验证命令与结果、未验证内容和原因、待人工确认项；没有待确认项则写“无”。
