# 关卡开发指南

本模组面向《圣符传说》第三方关卡开发者：用命令控制游戏流程，用数据包扩展判定集合，或用 Java API 集成其他服务端模组。令牌列表、生命周期与数值配置见[令牌文档](tokens.md)。

## 快速开始

1. 服务端与所有客户端安装本模组和 Fabric API。配置阶段握手会拒绝未安装模组或协议不匹配的客户端，不存在缺模组的部分功能模式。
2. 玩家在等待状态用 `/runelegend token equip` 配置令牌，或由管理员用 `/runelegend admin` 统一配装。
3. 用 `/runelegend game start` 开始游戏（可直接放进命令方块）。进入 `RUNNING` 后令牌效果生效、格挡消耗体力，且不能再修改装备。
4. 关卡结束时用 `/runelegend game stop` 停止：本局令牌运行状态与冷却重置（回合计数保留）、体力回满、灵魂摄取提供的生命上限移除，玩家可以重新配装进入下一轮。

- 游戏状态不持久化：服务器重启或单人世界重新载入后回到 `WAITING`、回合 0，关卡需要重新执行 `game start`。
- `game start` / `game stop` 只在状态确实改变时生效，重复执行不会推进回合或重复清理运行态。
- 只有 `RUNNING` 状态会触发令牌效果和格挡成功事件；`WAITING` 下可以自由格挡和配装，适合作为准备区。

## 调整战斗参数

服务器第一次启动时会在配置目录生成以下文件：

| 文件 | 用途 | 是否可手工修改 |
| --- | --- | --- |
| `config/runelegend-core/guard.json` | 格挡体力 | 可，重启生效 |
| `config/runelegend-core/tokens.json` | 内置令牌数值 | 可，重启生效，字段见[令牌文档](tokens.md#令牌配置) |
| `config/runelegend-core/players.json` | 玩家令牌装备 | 由模组管理，不建议手工修改 |

单人游戏中的 `config` 指该 Minecraft 实例的配置目录，独立服务器指服务端目录下的 `config`。`guard.json` 与 `tokens.json` 只在服务器启动时读取，修改后需要重启服务器。

`guard.json` 控制格挡体力（1 tick = 1/20 秒）：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `max` | 20000 | 体力上限，取值范围 1–65535，HUD 以此值换算百分比 |
| `min_to_start` | 2000 | 起手格挡所需的最低体力，须在 0 到 `max` 之间 |
| `drain_per_tick` | 125 | 格挡期间每刻消耗的体力 |
| `regen_per_tick` | 100 | 恢复期间每刻恢复的体力 |
| `regen_delay_ticks` | 10 | 停止格挡后到开始恢复的延迟刻数 |

字段缺省时取默认值；出现非法值（类型错误、越界或 `min_to_start` 大于 `max`）时整套配置回退默认值，并在服务器日志中记录错误。非法文件不会被改写，修正后重启即可生效。

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

令牌框架本身也可以扩展：每个令牌是一个 `TokenEffectHandler`，通过 `TokenRegistry` 注册，由 `TokenEngine` 分发事件并管理运行状态，可参考 `src/main/java/com/runelegend/token/effect` 中的内置实现。新增令牌的全部可调数值需要放进 `TokenConfig` 并在 `tokens.json` 中暴露；当前版本尚未提供运行时注册入口，自定义令牌需要在本仓库内实现并重新构建。事件类型、`TokenEvent` 语义与伤害通道见[令牌文档](tokens.md#扩展令牌)。
