# 关卡开发指南

本模组面向《圣符传说》第三方关卡开发者：用命令控制游戏流程，用数据包扩展判定集合，或用 Java API 集成其他服务端模组。令牌列表、生命周期与数值配置见[令牌文档](tokens.md)。

## 快速开始

1. 服务端与所有客户端安装本模组和 Fabric API。配置阶段握手会拒绝未安装模组或协议不匹配的客户端，不存在缺模组的部分功能模式。
2. 玩家在等待状态用 `/runelegend token equip` 配置令牌，或由管理员用 `/runelegend admin` 统一配装。
3. 等待时可用 `/runelegend player spectate true` 选择只观战下一局。用 `/runelegend game start` 开局（可直接放进命令方块），参与者进入冒险模式，观战者进入旁观模式；只有 `RUNNING + ALIVE` 可触发令牌，运行时禁改装备和观战选择。
4. 关卡达成结局时执行 `/runelegend game finish mymap:victory`，管理员可执行 `/runelegend game stop` 手动停局。没有 `ALIVE` 玩家时也会自动结算。本局临时状态、倒地记录和救援进度清空，回合计数保留，在线玩家统一传送到世界出生点并变回冒险模式，生命、饥饿和体力回满。等待状态登录同样在世界出生点上线，世界出生点就是等待区，可用 `/setworldspawn` 设置，详见[玩家状态](player-states.md)。

- 游戏状态不持久化：服务器重启或单人世界重新载入后回到 `WAITING`、回合 0，关卡需要重新执行 `game start`。
- `game start`（含名单版本）、`game stop` 和 `game finish` 共用 `runelegend:game` 权限（默认等级 2）。状态发生变化时命令返回 `1`，重复调用或通知回调期间调用时返回 `0`。
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

## 限定参赛名单与关卡机关

下列命令均需要 `runelegend:game` 权限（默认等级 2）。成功开局、查得凭据或完成人员变更时返回 `1`，条件不满足时返回 `0`。`<player>` 使用原版单玩家选择器或在线玩家名。

| 命令 | 行为 |
| --- | --- |
| `runelegend game start participants <selector>` | 在等待期按原版玩家选择器的结果开局。只有开局此刻在线、匹配选择器且未选择观战的玩家成为 `ALIVE`，其他在线玩家成为 `SPECTATOR` |
| `runelegend query player <player> downed_handle` | 有效倒地时，在命令输出中显示这次倒地的 UUID 凭据。数据包可从 `#runelegend:player_downed` 的 `revive_handle` 参数读取同一凭据 |
| `runelegend player revive <player> <handle>` | 用凭据扶起对应玩家。当前回合、连接、身份和倒地截止时间必须有效，且传送到原死亡维度成功后才恢复 `ALIVE` |
| `runelegend player eliminate <player> <round> <reason_id>` | 指定当前回合号，将在线 `ALIVE` 或 `DOWNED` 玩家转为 `ELIMINATED`，取消相关救援并清理令牌运行态。原因用资源 ID，例如 `mymap:trapdoor`。省略命名空间时使用 `minecraft` |

例如 `runelegend game start participants @a[tag=ready]` 仅让带 `ready` 标签且未选择观战的在线玩家参战。选择器没有匹配时，全员观战，随后按无存活者规则自动结算。观战选择随开局消费。首次中途加入的玩家仍为观战，即使其 UUID 在关卡预先准备的名单中。参赛者断线后重连仍为阵亡。无参数开局会考虑所有在线玩家。

扶起凭据绑定玩家和单次倒地。扶起、超时、淘汰、断线或停局都会使原凭据失效，再次倒地会生成新凭据。传送失败时，倒地记录保留至原截止时间。机关扶起采用与玩家救援相同的安全落点与生命恢复规则，扶起事件的 `rescuer_uuid` 为空。阵亡玩家无法通过扶起命令返场。淘汰命令保留玩家装备用于后续回合，末位存活者被淘汰后仍由服务端 tick 自动结算。

在倒地标签的宏函数中保存这次倒地的凭据，供机关之后读取。多人关卡应为每次待处理的倒地保存各自的参数。下例使用一个存储条目演示一次机关救援。

```mcfunction
# #runelegend:player_downed 的宏函数
$data modify storage mymap:round pending_rescue set value {player_uuid:"$(player_uuid)",revive_handle:"$(revive_handle)"}
```

```mcfunction
# 机关触发时调用 function mymap:rescue with storage mymap:round pending_rescue
# mymap:rescue 的内容
$execute as $(player_uuid) run runelegend player revive @s $(revive_handle)
```

延迟淘汰时应使用原任务记录的回合号，避免影响后来开启的回合。服务器重启后回合号会从零重新计数，关卡加载时应清理存储中的旧任务。

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

标签内可以使用普通函数，也可以在宏行中读取本次参数。两个标签都接收 `round`（NBT long）和 `identities`（以 UUID 字符串为键、身份名为值的 NBT compound）。身份名为 `ALIVE`、`DOWNED`、`ELIMINATED` 或 `SPECTATOR`。结束标签额外接收上表中的 `reason` 和 `outcome_id`，以及最终回合统计 `stats`。下面的 `data/mymap/function/round/settle.mcfunction` 将摘要复制到地图自己的存储，供后续逻辑使用。

```mcfunction
$data modify storage mymap:round last set value {round:$(round)L,reason:"$(reason)",outcome_id:"$(outcome_id)",identities:$(identities),stats:$(stats)}
```

参数随本次函数调用传入，由 Core 在派发时确定。若要在后续函数或 `schedule` 任务中使用这些值，请在回调内保存或显式传递。函数沿用原版 `#minecraft:tick` 的服务器执行源，位于世界出生点及其维度（维度不可用时回退主世界），以权限等级 2 运行，`@s` 初始为空。可用 `execute in` 或 `execute as` 指定关卡自己的维度或执行者。

Java 通知先于对应函数标签同步执行。回调可以查询状态。回调直接或通过嵌套函数调用 `start`、`stop` 或 `finish` 时返回 `0`，Java 返回 `false`。若要在结算后重新开局，可将开局命令放入之后执行的函数。

## 订阅倒地、扶起和淘汰

在服务端模组初始化时注册 `PlayerStateEvents`，或在数据包的 `data/runelegend/tags/function/` 下添加下表对应的标签文件。标签的写法和重载行为与回合通知相同。

| Java 事件 | 函数标签 | 触发时机 |
| --- | --- | --- |
| `PlayerStateEvents.DOWNED` | `#runelegend:player_downed` | 玩家进入 `DOWNED` 并生成倒地记录。图腾生效时没有通知 |
| `PlayerStateEvents.REVIVED` | `#runelegend:player_revived` | 传送成功、恢复 `ALIVE` 并清除倒地记录后。玩家救援会携带实际救援者 UUID |
| `PlayerStateEvents.ELIMINATED` | `#runelegend:player_eliminated` | `ALIVE` 或 `DOWNED` 转为 `ELIMINATED` 并完成清理后。原因包括倒地超时（`timeout`）、断线（`disconnect`）、原版死亡回调提交淘汰（`death`）和关卡操作（`scripted`） |

救援完成与倒地超时落在同一 tick 时，只发布超时淘汰。传送失败时保留倒地记录，且没有扶起通知。断线回调发出时，Core 已将该 UUID 标记为离线；玩家重连或旧连接再次断开时，原有淘汰记录不会再发布。末位玩家倒地或断线导致自动停局时，身份通知先于 `round_ended`。

### 函数参数与执行源

标签函数的执行源与回合通知相同，由服务器以权限等级 2 在世界出生点及其维度执行，`@s` 初始为空。使用 `player_uuid` 或 `rescuer_uuid` 定位参与者。需要对仍在线且可选中的玩家执行命令时，可使用 `execute as <UUID>`。处理离线参与者时，用 UUID 字符串写入地图存储即可。

| 参数 | 类型与含义 |
| --- | --- |
| `round` | NBT long，本次身份变更所属的回合 |
| `player_uuid` | UUID 字符串，倒地、被扶起或被淘汰的玩家 |
| `anchor` | NBT compound。包含倒地 `sequence`（long）、`dimension`（资源 ID 字符串）、`x`、`y`、`z`（double）和 `yaw`、`pitch`（float）。无倒地记录的淘汰传入 `{}` |
| `revive_handle` | 仅倒地事件提供。对应这次倒地的 UUID 凭据字符串 |
| `rescuer_uuid` | 仅扶起事件提供。匹配到的救援者 UUID 字符串，机关扶起传入空字符串 |
| `reason`、`previous_identity` | 仅淘汰事件提供。原因见上表，先前身份为 `ALIVE` 或 `DOWNED` |
| `reason_id` | 仅淘汰事件提供。关卡操作传入自定义原因 ID 字符串，其他原因传入空字符串 |

锚点属于倒地发生时的维度，位置和朝向取自原始倒地记录。使用锚点坐标前先在其 `dimension` 中执行，再用 `positioned` 指定绝对坐标。同一次倒地及其后续扶起或淘汰可用回合、玩家 UUID 和倒地序号关联；服务器重启会重新开始计数。以下宏可记录淘汰，即使玩家已离线。

```mcfunction
$data modify storage mymap:round last_elimination set value {round:$(round)L,player_uuid:"$(player_uuid)",reason:"$(reason)",previous_identity:"$(previous_identity)",anchor:$(anchor)}
```

Java 回调先于相应函数标签，参数保存提交时的数据。若回调引发另一项身份变更，新事件会排在已提交的事件之后。回调可查询实时状态，`start`、`stop` 和 `finish` 的重入规则与回合通知相同。

## 查询回合统计

`runelegend stats get <player> <metric>` 接受在线玩家名、单玩家选择器或 UUID。离线玩家用 UUID 查询。运行中返回当前回合的累计值，停局后返回最近一场的最终值，开新局时归零。没有记录时返回 `0`。统计只保存在服务器会话内存中。

| 指标 | 含义 |
| --- | --- |
| `downs` | 进入倒地的次数 |
| `revived` | 被扶起的次数，包含机关扶起 |
| `rescues` | 作为匹配到的救援者成功扶起队友的次数 |
| `eliminations` | 自己被淘汰的次数，包含断线 |
| `hits_dealt`、`hits_taken` | 造成和承受的已结算命中次数 |
| `damage_dealt`、`damage_taken` | 造成和承受的实际生命损失 |
| `damage_blocked`、`damage_absorbed` | 自己以物品格挡和吸收生命抵消的伤害量 |
| `unattributed_hits_taken`、`unattributed_damage_taken` | 自己承受的无攻击者命中次数及实际生命损失 |

命令返回 Brigadier 整数，可用 `execute store result score` 读取。次数按原值返回，伤害指标累计后以 0.001 生命值为单位四舍五入，超出整数范围时返回 `2147483647`。1 生命值相当于半颗心。例如：

```mcfunction
execute store result score @s mymap_damage run runelegend stats get @s damage_dealt
```

结算函数的 `stats` 是以 UUID 字符串为键的 NBT compound，内层以指标名为键，次数存为 long，伤害按未缩放的生命值存为 double。没有变化的指标和没有任何贡献的 UUID 可以省略，读取缺项时按 0 处理。Java 的 `RuneLegendApi.queries(server).stats(uuid)` 返回当前可查询的不可变统计，`RoundSummary.stats()` 保存该场的不可变最终统计。关卡可自行用这些值计算积分。

### 命中统计口径

- 对参与者承受的伤害、以及可归因给本轮参与者的生物伤害，原版进入实际减伤与吸收结算后记一次命中。完全格挡、完全吸收和被抗性抵消的已结算命中都计次数。免疫或受击间隔直接拒绝的尝试不计。
- 格挡量取物品格挡结果，吸收量取该次伤害实际扣除的吸收生命。实际扣血取护甲、防护附魔、抗性和吸收之后从当前生命条扣掉的值，上限为受击前的剩余生命。扣到 0 后图腾补回的生命和倒地时恢复的 1 点生命都属于后续变化。图腾救命仍计本次扣血，但不会增加倒地次数。
- 修罗化身期间的扣血计入临时生命条损失，切换或恢复生命条不产生伤害统计。替身承受的伤害不计给本体，替身本身也不计入玩家统计。
- 同次近战的穿透追加与基础伤害合计为一次。单独结算的令牌伤害各自计一次，并沿用令牌的反递归规则。
- 攻击者取原版伤害来源的责任实体，命中记录同时区分直接实体（如箭）与伤害类型。持续伤害每次实际结算独立计数。来源能提供本轮参与者时计给该 UUID，即使玩家此时已离线；没有责任实体时记录为未归因，不用上一击、效果施加者或最近攻击者推断。生物自身造成的伤害保留其归因。

## 调整战斗参数

战斗参数位于 `config/runelegend-core/server.json`，分为以下三组。

| 段 | 用途 |
| --- | --- |
| `guard` | 格挡体力 |
| `player_states` | 倒地时长、扶起距离、时长和生命比例，字段见[玩家状态文档](player-states.md#配置) |
| `tokens` | 内置与附属令牌设置，字段按完整 ID 分组，见[令牌文档](tokens.md#令牌配置) |

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

身份事件在服务器线程提供不可变参数，可通过 `round()`、`playerId()` 和倒地锚点的 `sequence()` 关联同一次倒地。`REVIVED` 的 `rescuerId()` 和 `ELIMINATED` 的 `anchor()` 使用 `Optional` 表示可能缺失的参数。

```java
PlayerStateEvents.REVIVED.register((server, event) -> {
    UUID target = event.playerId();
    event.rescuerId().ifPresent(rescuer -> {
        // 用 event.round()、target、event.anchor().sequence() 和 rescuer 记录这次救援
    });
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

`RuneLegendApi.startGame(server, candidateUuids)` 接收 UUID 集合，按开局时的在线玩家和观战选择分配身份。`queries.downedHandle(playerUuid)` 返回不可变的 `Optional<DownedHandle>`，只有当前回合在线且倒地未到期的玩家有凭据。关卡可以保留凭据并在机关触发时调用 `RuneLegendApi.tryRevive(server, handle)`，由 Core 在写入前重新校验。`RuneLegendApi.tryEliminate(server, playerUuid, round, reasonId)` 执行指定回合的淘汰。两项写操作完成时返回 `true`，条件不满足时返回 `false`。关卡淘汰事件的 `reason()` 为 `SCRIPTED`，`reasonId()` 提供调用者传入的资源 ID。

附属 Fabric 模组可以通过 `runelegend:tokens` 注册令牌。关卡可用现有配装和查询命令按完整 ID 引用这些令牌，例如 `runelegend admin @p equip mymap:rally`。注册示例、配置、资源路径和效果语义见[令牌文档](tokens.md#扩展令牌)。
