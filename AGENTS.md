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
| `src/main/java/com/runelegend` | 服务端与公共逻辑：入口、游戏状态、玩家状态与倒地救援、令牌引擎、玩家令牌装备存储、物品锁定、命令、网络、服务端 Mixin。不得引用 `net.minecraft.client.*` 或 `com.runelegend.client.*`。 |
| `src/client/java/com/runelegend/client` | 仅客户端逻辑：输入辅助、网络缓存、HUD、倒地标记渲染、物品锁定的预测抑制、客户端 Mixin。不得执行令牌判定、伤害计算、体力规则、玩家状态判定或游戏状态变更。 |
| `src/main/resources` | 公共 `fabric.mod.json`、服务端 Mixin 配置、语言文件、伤害类型、伤害与实体数据 Tag。 |
| `src/client/resources` | 客户端 Mixin 配置和 HUD 贴图；不得放置服务端判定数据。 |
| `src/test/java` | 纯 JUnit 单元测试，尽量不启动 Minecraft 运行时。 |
| `src/gametest/java` | Fabric GameTest 与客户端 GameTest；新增测试类必须同步登记 `src/gametest/resources/fabric.mod.json` 的 `fabric-gametest` 或 `fabric-client-gametest` entrypoint。 |
| `docs/` | 用户与关卡开发文档；不要在此放置生成物或本机配置。 |
| `build/`、`.gradle/`、`.gradle-local/`、`.minecraft/`、`run/` | 生成物或本机测试环境，不要提交、清理或当作项目事实来源。 |

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

- 服务器是唯一权威。令牌触发、伤害、体力、冷却、游戏状态、玩家身份、救援结果和装备变更都在服务端主线程判定；客户端只负责展示、输入时序辅助和失序重同步，不作权威判定。
- Loom 使用 `splitEnvironmentSourceSets()`。新增类时先判断 Common/Client 归属，禁止通过反射或字符串类名绕过源集边界。
- 游戏状态只有 `WAITING` 与 `RUNNING`，与玩家身份相互独立：等待状态允许装备变更并禁止全部令牌效果；运行状态禁改装备，只有 `RUNNING + ALIVE` 的玩家触发令牌或接受令牌治疗。状态只保存在服务端内存，不写盘，逻辑服务器重启或单人世界重新载入后从 `WAITING`、回合 0 开始；只有 `WAITING`→`RUNNING` 推进回合计数，`stop` 保留回合数；没有 `ALIVE` 玩家时自动停局。状态切换必须清理回合运行态并推进修订号。
- 剑格挡基于原版 `minecraft:blocks_attacks` 物品组件，不维护独立全局格挡开关。地图作者通过物品组件控制哪些物品能挡；本项目不自动给所有剑添加组件。`runelegend:sword_guardable` 与 `runelegend:sword_guard_bypasses` 只是供物品组件引用的伤害类型标签，不是第二套格挡开关。
- 体力门槛、消耗、耗尽停止和松键要求由服务端管理；`WAITING` 下格挡不消耗、不检查体力，也不产生格挡成功事件。客户端输入补丁只解决原版按住 Use 重试与松键包时序，不能形成第二套格挡规则。
- 格挡体力参数（上限、起手阈值、消耗、恢复和延迟）统一由 `GuardConfig` 读取并在 `guard.json` 暴露，不得在格挡逻辑中硬编码。
- `guard.json`、`tokens.json` 与 `player_states.json` 只在逻辑服务器启动时读取一次，不提供运行时热重载。
- 格挡方向和减伤由原版结算；只为缺失或不准确的来源坐标提供窄桥接。箭按飞行方向反推，爆炸按真实爆心，不借用射手或引爆者位置。
- 原版动画与实体同步优先；不要恢复自定义 GuardPose 协议。

## 玩家状态与救援

- 游戏状态与玩家身份相互独立。身份只有 `ALIVE`、`DOWNED`、`ELIMINATED`、`SPECTATOR`，只在 `RUNNING` 下存在；`WAITING` 没有本局身份。不要用原版生命、游戏模式、队伍或记分板推断身份，也不要让客户端决定身份或救援结果。
- 开局为在线参与者分配 `ALIVE`（冒险模式），为等待期选择观战的玩家分配 `SPECTATOR`（旁观模式）；首次中途加入为 `SPECTATOR`。`ALIVE`/`DOWNED` 断线直接转为 `ELIMINATED`，重连不得复活；观战选择只在等待期修改、开局消费一次，等待期断线仍保留。
- 死亡拦截只能在原版不死图腾判定之后（`ServerPlayer.die` 入口）且仅在 `RUNNING` 生效：`ALIVE` 转 `DOWNED`，保留物品、装备和经验，不出现死亡界面、不掉落、不计死亡统计；`WAITING` 死亡继续走原版流程。拦截时清除药水效果、令牌运行态以及火焰、冻结、跌落等即时残留，并记录死亡维度、精确坐标与朝向。
- 救援条件固定在服务端：救援者与死亡锚点同维度、距离不超过 `player_states.json` 配置、持续潜行且身份为 `ALIVE`；一名目标与一名救援者最多各占一个关系，按距离和 UUID 稳定匹配。死亡超时与救援完成发生在同一 tick 时以阵亡优先。
- 扶起成功只能通过 `RuneLegendServer.revive(UUID, long sequence)` 的统一完成路径：传送到死亡维度的安全站立点（解析集中在 `RevivePositionResolver`，不加载区块，找不到时回退原始死亡坐标），恢复配置比例的最大生命值，转冒险模式和 `ALIVE`，删除倒地记录。传送失败或维度不可用时不提交 `ALIVE`。不要增加跳过 `ALIVE` 目标检查的通用救援开关，也不要用无敌或修改地形规避兜底风险。
- 没有 `ALIVE` 玩家时由服务端 tick 自动停局。停局清空包含离线玩家在内的身份、倒地记录、计时、救援关系和观战选择；在线玩家统一传送到世界出生点，回到冒险模式并回满生命、饥饿和体力。`WAITING` 登录的玩家同样在世界出生点上线，不恢复上次退出坐标；`RUNNING` 中途加入仍按原版加载位置上线。停局传送使用原版共享出生点（维度、角度与安全落点），不要改成模组自定义大厅坐标。游戏模式只在登录、实体替换和状态转换时设置，不要每 tick 覆盖管理员的修改。
- 身份、倒地锚点、截止时间和救援进度只保存在服务端内存，不写入 NBT、SavedData 或 `players.json`；`player_states.json` 在逻辑服务器启动时读取一次，缺失时生成默认文件，非法时整份回退默认值并记录错误、不改写原文件。
- 倒地标记由服务端快照/增量下发（仅当前维度、带回合与序号），客户端按回合、维度和距离裁剪并用世界渲染绘制文字；不要创建 TextDisplay 或自行计时。客户端失序时隐藏标记并请求重同步。
- 令牌触发、治疗与救援资格统一使用 `RUNNING + ALIVE` 判定；旁观、倒地、阵亡玩家不得触发令牌或参与救援。相关测试见 `PlayerStateGameTests`、`PlayerStateTokenGameTests` 与 `PlayerStateClientGameTest`。

## 令牌系统与伤害语义

- 令牌通过 `TokenRegistry` 注册，效果实现 `TokenEffectHandler`，运行状态按玩家 UUID 和令牌 ID 隔离；不同玩家不得共享冷却、充能或触发状态。新增令牌必须在本仓库内实现并在 `TokenRegistry.builtIn` 登记；不要假设存在运行时注册或热加载入口。只有 `RUNNING + ALIVE`、当前连接且非旁观的玩家可触发效果；玩家（包括队友）永远不是令牌伤害目标，治疗只作用于 `ALIVE` 玩家。
- 每个令牌的全部可调数值参数（包括但不限于冷却时间、持续时间、触发概率、伤害、半径和效果专属参数）必须进入 `TokenConfig` 的 typed settings 并在配置文件中暴露；效果类不得硬编码这些数值。
- 效果必须事件驱动，只有确实需要时间推进的效果才使用调度器，不要为每枚令牌做完整轮询。
- 令牌自产伤害不得递归触发其他令牌；新增伤害路径必须保留归因和嵌套命中隔离。
- 停局、倒地、阵亡或断线时清理 owned effect、调度任务、冷却和回合内状态（包括灵魂摄取、修罗化身等临时属性修饰符），不允许效果跨局、跨倒地或跨重连残留；令牌装备与 `players.json` 不受清理影响。
- 延迟任务必须通过 `TokenEngine.Context` 创建并在执行时重新校验当前连接、回合、身份和运行态代次；不得捕获裸玩家引用绕过校验，扶起或重连后旧任务不得恢复。
- **伤害是否无视护甲和防护附魔必须逐令牌按确认规格实现，不能假设原作所有令牌都穿透。** 
- 新增效果时显式选择三类通道之一：
  1. 穿透直接伤害：使用 `runelegend:token_damage` 或 `TokenEffectHandler.addMeleeDamage` 分离追加通道；无视护甲和防护附魔，但保留抗性提升、伤害吸收等非装备减伤。
  2. 普通直接伤害：使用合适的原版伤害来源或并入基础命中，正常经过护甲、防护附魔和原版战斗规则。
  3. 减益伤害：中毒、凋零等 MobEffect 造成的伤害保持各自原版规则，不改伤害类型或 Tag。
- `runelegend:token_damage` 不是“令牌伤害”的通用标签，只代表需要绕过装备减伤的直接伤害；普通令牌伤害和减益伤害不得加入该类型。
- `TokenEffectHandler.addMeleeDamage` 表示与基础近战分离且需要绕过装备减伤的追加伤害；该追加伤害并入同一次命中，不产生第二段受击或新的受击间隔。规格要求普通伤害的近战令牌不得复用该通道。若普通追加令牌增多，先重构显式伤害策略，不重载现有语义。
- 新增或修改伤害语义的 GameTest 必须覆盖护甲、防护附魔、抗性提升、基础近战不受意外污染，以及规格要求的普通/穿透差异。

## 网络、存储与数据包

- 自定义协议版本位于 `RuneLegendPayloads.PROTOCOL`。修改包形状、字段语义或握手规则时递增协议号，并同步服务端、客户端、握手测试和 README 迁移说明。
- 配置阶段握手拒绝缺模组和协议不匹配客户端；不要把缺模组客户端降级为部分功能模式。
- 每个玩家自己的状态使用快照加增量同步，覆盖令牌、体力、身份、观战选择、倒地标记与规则回合/修订号，不得恢复每 tick 全量广播。增量只发送变化块（体力最快每 4 刻），倒地标记只下发当前维度的记录并在整秒变化时更新；保留客户端失序检测（隐藏不可信数据）和服务端重同步限频（每 20 tick 最多响应一次）。
- 令牌装备以 UUID 为键保存在实例配置目录的 `runelegend-core/players.json`（`schemaVersion: 1`），使用 `equippedTokens` 字段；逻辑服务器启动时读取，装备变更须原子落盘后才生效。读写失败时进入只读模式，禁止用空数据覆盖既有文件。
- 数据 Tag 是地图扩展接口。修改默认 Tag 时保持 `replace: false`，并检查数据包覆盖与重载行为。格挡与令牌伤害相关的是伤害类型标签 `runelegend:sword_guardable`、`runelegend:sword_guard_bypasses` 和伤害类型 `runelegend:token_damage`；后者同时被本模组加入 `#minecraft:bypasses_armor` 与 `#minecraft:bypasses_enchantments`，修改会影响全部穿透令牌。
- `token_enemies`、`bosses`、`ghost_family` 分类实体类型，不支持按单只实体推断；不要用名称、Boss Bar 或位置自动分类。

## 物品锁定

- 锁定标记固定为物品 `minecraft:custom_data` 组件中的布尔键 `runelegend:item_lock`（`1b`）；只有布尔 `true` 生效，其他类型或嵌套路径不视为锁定。不要改成全局开关或按槽位、物品 ID 锁定。
- 规则集中在 `ItemLockPolicy`，服务端和客户端预测共用；装备下发修正集中在 `ItemLockEquipment`，容器拖动状态经 `ItemLockMenuAccess` 读取原版，Mixin 只桥接。客户端 Mixin 只抑制本地预测与界面操作，服务端必须独立拒绝并强制下发权威物品栏、容器或装备状态，即使内容未变化。
- 仅约束非创造模式玩家的主动转移：容器/物品栏点击、快速移动、数字键与副手交换、拖动、双击收集、丢弃、配方放置、交易选择、装备（含盔甲架和实体装备）以及方块/实体选取。
- 锁定是逐物品堆叠的组件状态，不属于槽位或容器；不得传播到嵌套内容，也不得阻止普通使用（进食、放入物品展示框）。
- 死亡掉落、物品成为掉落物后的行为、创造模式玩家以及非玩家途径（命令、方块逻辑等）不受锁定约束。
- 修改锁定语义或新增玩家转移入口时，必须同时更新服务端拦截、客户端预测抑制和 `ItemLockGameTests` / `ItemLockTransferGameTests` / `ItemLockClientGameTest`。

## Mixin 维护

- Mixin 只用于 Fabric API 无法表达的窄钩子；新增前先确认没有合适的 Fabric 事件、回调或原版组件。
- 每个 Mixin 绑定当前 `gradle.properties` 中 Minecraft 版本的真实方法签名和调用点。修改前用 Loom 缓存、官方 JAR 或可编译源码核对，不凭旧版本经验写注入点。
- 服务端与客户端 Mixin 分别登记在 `runelegend.mixins.json`、`runelegend.client.mixins.json`；客户端 GameTest 专用 Mixin 登记在 `src/gametest/resources/runelegend.item-lock.client-test.mixins.json`。删除类时同步删除配置项。
- 死亡拦截必须留在 `ServerPlayer.die` 入口（原版不死图腾判定之后）且只在 `RUNNING` 下取消死亡；不得提前到受伤入口或无条件拦截，否则会吞掉图腾或改变 `WAITING` 的原版死亡流程。
- 共享注入点使用明确的 `require` / `allow` 约束；战斗 Mixin 修改后必须跑完整 GameTest。
- Mixin 只桥接事件或调用，业务规则放回对应服务，不在 Mixin 中堆逻辑。

## 玩家可见文本与资源

- 新增玩家可见文本必须同时更新 `zh_cn.json` 与 `en_us.json`；不要在命令、Mixin 或效果类中硬编码长文本。命令等使用 `RuneLegendText` 的服务端文本以 `en_us.json` 作为回退表；回退表缺少键时，没有对应语言条目的客户端会看到原始 key。
- 修改命令、配置字段、数据标签或玩家可见行为时，同步更新受影响的 `README.md` 或 `docs/` 文档。
- HUD 与倒地标记是展示层。令牌栏只在 `RUNNING + ALIVE`、玩家存活且非旁观时显示；体力 HUD 独立显示（未满或格挡中）；倒地提示由客户端按服务端标记在世界层渲染，不创建 TextDisplay 实体，也不自行计时。

## 构建与验证

Windows 下优先使用 `.\gradlew.bat`。分级验证：

| 修改类型 | 最低验证 |
| - | --- |
| 仅 Markdown | `git diff --check`（新增未跟踪文件先 `git add -N`） |
| Common 业务逻辑、令牌、玩家数据、命令或网络 | `.\gradlew.bat build`；涉及网络或客户端状态时追加 `runClientGameTest` |
| 玩家状态、倒地救援、死亡拦截或相关网络字段 | `.\gradlew.bat build runClientGameTest` |
| 物品锁定、客户端预测抑制 | `.\gradlew.bat build runClientGameTest` |
| Client HUD、输入或贴图 | `.\gradlew.bat build runClientGameTest`，视觉变化需截图核对 |
| Mixin 注入点或配置 | `.\gradlew.bat build`，并运行相关 GameTest |
| 纯单元测试 | `.\gradlew.bat test` |

- `build` 会执行单元测试和服务端 GameTest；不要只编译后宣称测试通过。
- 仓库 CI（`.github/workflows/build.yml`）依次执行 `build` 与 `runClientGameTest`；本地提交前尽量按相同范围验证。
- 涉及多人、实体追踪、延迟、Dedicated Server 或协议兼容时，至少用发行 JAR 做实机或探针验证，并说明验证范围。
- 本机 Gradle 可能需要会话级 JDK 25 与临时目录环境变量；这些设置不得写入提交文件。
- 因环境限制无法验证时，明确说明未验证项、原因和风险。

## 默认不要修改

除非任务明确要求，默认不要修改：

- `README.md` 与 `AGENTS.md`
- 既有语言键和贴图

## 回复要求

完成任务后说明修改文件与原因、验证命令与结果、未验证内容和原因、待人工确认项；没有待确认项则写“无”。
