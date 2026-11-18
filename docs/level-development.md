# 关卡开发指南

本模组面向《圣符传说》第三方关卡开发者：用命令控制游戏流程，用数据包扩展判定集合，或用 Java API 集成其他服务端模组。令牌列表、生命周期与数值配置见[令牌文档](tokens.md)。

## 快速开始

1. 服务端与所有客户端安装本模组和 Fabric API。配置阶段握手会拒绝未安装模组或协议不匹配的客户端，不存在缺模组的部分功能模式。
2. 玩家在等待状态用 `/runelegend token equip` 配置令牌，或由管理员用 `/runelegend admin` 统一配装。
3. 等待时可用 `/runelegend player spectate true` 选择只观战下一局。用 `/runelegend game start` 开局（可直接放进命令方块），参与者进入冒险模式，观战者进入旁观模式；只有 `RUNNING + ALIVE` 可触发令牌，运行时禁改装备和观战选择。
4. 关卡达成结局时执行 `/runelegend game finish mymap:victory`，管理员可执行 `/runelegend game stop` 手动停局。没有 `ALIVE` 玩家时也会自动结算。本局临时状态、倒地记录和救援进度清空，回合计数保留，在线玩家统一传送到世界出生点并变回冒险模式，生命、饥饿和体力回满。等待状态登录同样在世界出生点上线，世界出生点就是等待区，可用 `/setworldspawn` 设置，详见[玩家状态](player-states.md)。

- 游戏状态不持久化：服务器重启或单人世界重新载入后回到 `WAITING`、回合 0，关卡需要重新执行 `game start`。
- `game start`、`game stop` 和 `game finish` 共用 `runelegend:game` 权限（默认等级 2）。状态发生变化时命令返回 `1`，重复调用或通知回调期间调用时返回 `0`。
- 只有 `RUNNING + ALIVE` 会触发令牌效果和令牌格挡成功事件；`WAITING` 下可以自由格挡和配装，适合作为准备区。等待区固定使用世界出生点，停局和等待期上线都会把玩家送回该点。

## 在数据包中查询对局和玩家

查询通过命令结果返回整数，可用 `execute store result` 写入计分板。`game status`、`player status` 和 `token status` 继续提供原有的文本与返回值。

| 命令 | 返回值 |
| --- | --- |
| `runelegend test player <player> alive` | 本局身份为 `ALIVE` 时返回 `1`，其余情况返回 `0` |
| `runelegend query game alive_count` | 当前回合 `ALIVE` 身份的玩家数，等待时为 `0` |
| `runelegend test player <player> equipped <token_id>` | 装备槽中有该 ID 时返回 `1`，否则返回 `0`。已保存的 ID 即使没有已注册的令牌定义，仍计为已装备 |
| `runelegend query player <player> stamina` | 当前格挡体力的整数原值，范围为 `0` 到 `guard.max` |
| `runelegend query player <player> cooldown <token_id>` | 冷却阶段剩余的服务器 tick。未装备、定义不可用、效果仍在生效或当前无冷却时返回 `0` |

存活测试与人数依据本局身份，管理员调整原版生命值或游戏模式时仍以身份为准。体力可查询任意身份的在线玩家。冷却随服务器 tick 推进（正常速度每秒 20 tick）；生效结束后才开始的冷却，从冷却阶段开始计入。令牌 ID 和配装命令使用同一写法，例如 `runelegend:echo` 或 `echo`。

`<player>` 须解析为一名在线玩家，使用原版玩家名或单玩家选择器。找不到在线目标时命令以原版错误失败，`execute store result` 写入 `0`。如需区分「有效查询得到 `0`」和「目标缺失」，可同时用 `execute store success` 记录成功标志。

```mcfunction
# 初始化函数中创建目标
scoreboard objectives add rl_map dummy

# 在关卡函数中读取并按人数触发逻辑
execute store result score #alive rl_map run runelegend query game alive_count
execute if score #alive rl_map matches 1.. run function mymap:round/with_survivors
execute as @a store result score @s rl_map run runelegend test player @s alive
```

## 订阅回合开局和结算

每次进入 `RUNNING` 后，Core 在身份和游戏模式初始化完成时发布一次 `round_started`。结算时，Core 先保存包含离线玩家的最终身份，再清空回合运行态并执行回世界出生点的流程，完成后发布一次 `round_ended`。此时实时查询返回 `WAITING`、当前存活人数 `0` 和空身份，结算参数仍保留清理前的身份。

| 触发方式 | 结束原因 `reason` | 结局 `outcome_id` |
| --- | --- | --- |
| `runelegend game finish <outcome_id>` | `finish` | 关卡定义的资源 ID，例如 `mymap:victory`。省略命名空间时使用 `minecraft` |
| `runelegend game stop` | `manual_stop` | 空字符串 |
| 服务端 tick 检查到没有 `ALIVE` 玩家 | `no_alive` | 空字符串 |

全员观战时也会先收到开局通知，随后在服务端 tick 检查时收到 `no_alive` 结算。末位存活者断线时，结算身份包含该 UUID 的 `ELIMINATED` 记录。关服直接清理本次会话，重新加载时回到回合 0。

### 数据包函数标签

在数据包内的 `data/runelegend/tags/function/round_started.json` 或 `data/runelegend/tags/function/round_ended.json` 中添加函数。标签随数据包重载。下例是结束标签的内容。

```json
{
  "replace": false,
  "values": ["mymap:round/settle"]
}
```

标签内可以使用普通函数，也可以在宏行中读取本次参数。两个标签都接收 `round`（NBT long）和 `identities`（以 UUID 字符串为键、身份名为值的 NBT compound）。身份名为 `ALIVE`、`DOWNED`、`ELIMINATED` 或 `SPECTATOR`。结束标签额外接收上表中的 `reason` 和 `outcome_id`。下面的 `data/mymap/function/round/settle.mcfunction` 将摘要复制到地图自己的存储，供后续逻辑使用。

```mcfunction
$data modify storage mymap:round last set value {round:$(round)L,reason:"$(reason)",outcome_id:"$(outcome_id)",identities:$(identities)}
```

参数随本次函数调用传入，由 Core 在派发时确定。若要在后续函数或 `schedule` 任务中使用这些值，请在回调内保存或显式传递。函数沿用原版 `#minecraft:tick` 的服务器执行源，位于世界出生点及其维度（维度不可用时回退主世界），以权限等级 2 运行，`@s` 初始为空。可用 `execute in` 或 `execute as` 指定关卡自己的维度或执行者。

Java 通知先于对应函数标签同步执行。回调可以查询状态。回调直接或通过嵌套函数调用 `start`、`stop` 或 `finish` 时返回 `0`，Java 返回 `false`。若要在结算后重新开局，可将开局命令放入之后执行的函数。

## 调整战斗参数

战斗参数位于 `config/runelegend-core/server.json`，分为以下三组。

| 段 | 用途 |
| --- | --- |
| `guard` | 格挡体力 |
| `player_states` | 倒地时长、扶起距离、时长和生命比例，字段见[玩家状态文档](player-states.md#配置) |
| `tokens` | 内置令牌数值，字段见[令牌文档](tokens.md#令牌配置) |

玩家令牌装备由模组写入同目录的 `players.json`。

单人游戏中的 `config` 指该 Minecraft 实例的配置目录，独立服务器指服务端目录下的 `config`。`server.json` 在逻辑服务器启动时读取一次，缺失时自动生成，修改后需要重启服务器或重新载入单人世界。配置跨存档共用，玩家状态按存档会话隔离且不写盘。

保留顶层的整数 `"schemaVersion": 1`。文件读不到、顶层不是合法的 JSON 对象或版本无效时，三组都使用默认值并记录错误。分组缺失或类型错误时，只有对应分组使用默认值。读取已有配置时保留原文件。

`guard` 段控制格挡体力（1 tick = 1/20 秒）：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `max` | 20000 | 体力上限，取值范围 1–65535，HUD 以此值换算百分比 |
| `min_to_start` | 2000 | 起手格挡所需的最低体力，须在 0 到 `max` 之间 |
| `drain_per_tick` | 125 | 格挡期间每刻消耗的体力 |
| `regen_per_tick` | 100 | 恢复期间每刻恢复的体力 |
| `regen_delay_ticks` | 10 | 停止格挡后到开始恢复的延迟刻数 |

字段缺省时取默认值。出现非法值（类型错误、越界或 `min_to_start` 大于 `max`）时，`guard` 整段回退默认值并记录错误。

## 通过数据包定制

模组内置的数据标签都可以用自己的数据包覆盖或追加：

| 标签 | 类型 | 作用 | 默认值 |
| --- | --- | --- | --- |
| `runelegend:token_enemies` | 实体类型 | 令牌的“敌对目标”判定范围 | 空；为空时回退为原版 `Enemy` 生物 |
| `runelegend:bosses` | 实体类型 | Boss 分类 | `minecraft:ender_dragon`、`minecraft:wither` |
| `runelegend:ghost_family` | 实体类型 | 鬼魅生物分类 | 空 |
| `runelegend:sword_guardable` | 伤害类型 | 供物品 `blocks_attacks.damage_reductions[].type` 引用的可格挡伤害 | 近战、箭、蜂刺、爆炸等 7 种原版伤害 |
| `runelegend:sword_guard_bypasses` | 伤害类型 | 供物品 `blocks_attacks.bypassed_by` 引用的绕过格挡伤害 | `#minecraft:bypasses_armor`、`#minecraft:bypasses_invulnerability`、`runelegend:token_damage` |

`token_enemies` 非空时，只有该标签中的实体类型被视为令牌敌人；它不会自动与原版 `Enemy` 合并。下面的文件可将自定义实体纳入集合；若还需保留原版敌人，应同时列出所需类型。将文件放进数据包的 `data/runelegend/tags/entity_type/token_enemies.json`（内置标签均为 `replace: false`，可与数据包合并）：

```json
{
  "replace": false,
  "values": ["mymod:ghost_warrior"]
}
```

- `bosses` 与 `ghost_family` 是预定义的分类标签，当前没有内置令牌引用它们，可用于命令选择器（如 `@e[type=#runelegend:bosses]`）或扩展代码读取。
- `sword_guardable` 与 `sword_guard_bypasses` 只作为物品组件的引用目标；模组不会自动给物品添加或修改 `blocks_attacks` 组件。格挡示例见 [README](../README.md#格挡与战斗机制)。

## 物品锁定

给物品写入布尔值 `runelegend:item_lock`，即可保护它所在的槽位，让非创造模式玩家无法通过常规操作移动它：

```
/give @s minecraft:iron_sword[minecraft:custom_data={"runelegend:item_lock":1b}]
```

```
/give @s minecraft:diamond_sword[minecraft:custom_data={"runelegend:item_lock":1b},minecraft:unbreakable={}]
```

- 只有布尔值 `true`（`1b`）生效；`0b`、数字、字符串或其他路径都不会锁定。同一 `custom_data` 中的其他键会原样保留。
- 锁定物品不能被非创造模式玩家通过容器或物品栏点击、Shift 快速移动、数字键/副手交换、拖动分配、双击收集、丢弃、可能移动它的配方一键放置、村民交易选择或装备操作（含盔甲架与实体装备）移动、丢弃或替换；会覆盖锁定热键槽的物品选取同样会被拒绝。
- 正常使用不受影响，例如锁定食物仍可进食，锁定物品仍可放入物品展示框；死亡掉落、关闭界面时的原版返还以及创造模式玩家的操作也不受限制。
- 锁定与游戏状态无关，`WAITING` 和 `RUNNING` 下都会生效。
- 锁定约束的是物品当前所在的槽位；物品成为掉落物后不再受该锁定保护。收纳袋内的锁定物品也不会使收纳袋本身被锁定。
- 锁定只拦截玩家操作，命令等非玩家途径仍可直接修改物品。

## 通过 Java API 集成

其他服务端模组可以直接控制游戏流程：

```java
import com.runelegend.api.RuneLegendApi;

// 必须在服务器线程调用（例如 Fabric 事件回调中），否则抛出异常
RuneLegendApi api = RuneLegendApi.get(server);

if (!api.getRules().running()) {
    api.startGame();   // 进入 RUNNING，回合数 +1，清空令牌运行状态
}

// RuleSnapshot 记录了状态、回合数与修订号（revision）
long round = api.getRules().round();
```

`RuneLegendApi.get(server)` 只能在服务器线程、且服务器已经启动后调用；其他线程或模组未激活时抛出 `IllegalStateException`。`startGame()` / `stopGame()` 返回状态是否发生了改变。

`RuneLegendApi.finishGame(server, outcomeId)` 对应 `game finish`，返回状态是否改变。在服务端模组初始化时可注册 `RoundEvents.STARTED` 与 `RoundEvents.ENDED`。回调在服务器线程运行。开局回调收到回合号及不可变身份表。结束回调收到不可变 `RoundSummary`，包含回合号、`FINISH` / `MANUAL_STOP` / `NO_ALIVE`、可选结局 ID 和最终身份表。可以保留摘要，供之后的结算逻辑使用。

```java
RoundEvents.ENDED.register((server, summary) -> {
    if (summary.reason() == RoundSummary.Reason.FINISH) {
        var outcome = summary.outcomeId().orElseThrow();
        var finalIdentities = summary.identities();
        // 根据关卡结局 ID 和最终身份计算奖励
    }
});
```

在服务器线程调用 `RuneLegendApi.queries(server)` 可获取只读查询入口。`aliveCount()` 与命令使用同一存活人数口径，`player(uuid)` 始终返回一个不可变的 `PlayerSnapshot`，记录查询时的规则、连接状态、身份与装备。后续状态变化需要重新查询。

```java
var queries = RuneLegendApi.queries(server);
var player = queries.player(playerUuid);
boolean alive = player.alive();
boolean hasRoundRecord = player.identity().isPresent();
boolean connected = player.connected();
```

| UUID 所处情况 | `rules().state()` | `identity()` | `connected()` |
| --- | --- | --- | --- |
| 等待中的在线玩家 | `WAITING` | 空 | `true` |
| 本轮无记录的 UUID | `RUNNING` | 空 | `false` |
| 断线阵亡且本轮仍在运行 | `RUNNING` | `ELIMINATED` | `false` |
| 阵亡后重连 | `RUNNING` | `ELIMINATED` | `true` |

`equippedTokens()` 按槽位顺序提供已保存的装备 ID，离线时也可读取，无装备时列表为空。`stamina()` 对离线玩家返回 `OptionalInt.empty()`。`cooldownTicks(tokenId)` 对离线玩家、未装备或无注册定义的令牌返回 `OptionalInt.empty()`，对已装备且有定义的在线玩家令牌返回剩余 tick 或 `0`。

令牌框架本身也可以扩展：每个令牌是一个 `TokenEffectHandler`，通过 `TokenRegistry` 注册，由 `TokenEngine` 分发事件并管理运行状态，可参考 `src/main/java/com/runelegend/token/effect` 中的内置实现。新增令牌的全部可调数值需要放进 `TokenConfig` 并在 `server.json` 的 `tokens` 段暴露；当前版本尚未提供运行时注册入口，自定义令牌需要在本仓库内实现并重新构建。事件类型、`TokenEvent` 语义与伤害通道见[令牌文档](tokens.md#扩展令牌)。
