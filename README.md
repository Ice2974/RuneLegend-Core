# RuneLegend-Core

RuneLegend-Core 是一个面向《圣符传说》第三方关卡开发者的 Fabric 模组。它在 Fabric 端上复刻了圣符传说的核心战斗系统——令牌、格挡体力，并提供游戏流程控制等功能。\
关卡作者可以直接通过命令、数据包或 Java API 搭建自己的圣符传说关卡，而不必自己实现这些机制。

《圣符传说》原是 EaseCation 服务器上的四人合作 PvE 剧情玩法，背景与规则可参考[官方 Wiki](https://wiki.easecation.net/%E5%9C%A3%E7%AC%A6%E4%BC%A0%E8%AF%B4)。\
**本项目是独立的社区第三方实现，与 EaseCation 官方无关。**

## 功能一览

- **游戏流程控制**：`WAITING`/ `RUNNING` 两种状态。只有等待状态能更换令牌装备；每次状态切换都会清空回合内的令牌运行状态，开始新局会推进回合计数。状态、回合数和修订号保存在世界存档中，单人退出重进或服务器重启后不会自动回到等待状态。
- **令牌系统**：每名玩家 3 个令牌槽，装备持久化到世界存档。内置 4 种令牌（见下表），全部由服务器权威判定。死亡、断线时清理充能、活动效果和调度任务，仅保留未到期冷却；重连恢复剩余冷却，离线冷却到期后自动回收。状态切换会连同冷却一起清空回合运行态。动作令牌可以响应基于 MC 原版 `blocks_attacks` 组件的格挡。
- **体力系统**：格挡持续消耗体力，体力耗尽自动收剑，低于起手阈值时无法起手（默认 10%）。数值全部在服务器结算，客户端只负责显示。
- **客户端 HUD**：准星下方展示令牌槽位图标（就绪 / 触发 / 生效 / 冷却倒计时）与体力百分比，无需资源包即可使用。

## 内置令牌

| ID | 名称 | 效果 |
| --- | --- | --- |
| `runelegend:sharpened_tip` | 强化刃尖 | 直接近战命中 50% 概率额外造成 2 HP 真伤 |
| `runelegend:soul_siphon` | 灵魂摄取 | 击杀敌对目标 5% 概率增加 2 点最大生命，不叠加、不治疗；跨保存和重连保留，死亡重生后清除 |
| `runelegend:echo` | 残响 | 击杀敌对目标 50% 概率在其位置留下残响，持续 8 秒，每秒对残响半径 5 格内的敌对目标造成 3 HP 真伤 |
| `runelegend:immediate_counter` | 即刻反击 | 成功格挡后，随后 8 秒内直接近战每击额外造成 3 HP 真伤，冷却 12 秒 |

灵魂摄取的生命增益不属于回合活动效果，停局或卸下令牌不会移除它，普通死亡重生会按原版规则清除。

## 命令

所有命令都在 `/runelegend` 下。`token_id` 省略命名空间时默认为 `runelegend:`。

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/runelegend token list` | 所有玩家 | 列出全部已注册令牌 |
| `/runelegend token info <token_id>` | 所有玩家 | 查看令牌描述 |
| `/runelegend status` | 所有玩家 | 查看自己的体力与已装备令牌（含冷却剩余） |
| `/runelegend token equip <token_id>` | 所有玩家 | 装备令牌，仅等待状态可用 |
| `/runelegend token unequip <token_id>` | 所有玩家 | 卸下令牌，仅等待状态可用 |
| `/runelegend token clear` | 所有玩家 | 清空自己的令牌，仅等待状态可用 |
| `/runelegend game status` | 所有玩家 | 查看游戏状态 |
| `/runelegend game start` | `runelegend:game`（默认权限等级 2） | 开始游戏 |
| `/runelegend game stop` | `runelegend:game`（默认权限等级 2） | 停止游戏并重置回合 |
| `/runelegend admin <player> …` | `runelegend:admin`（默认权限等级 3） | 以其他玩家为目标执行 `status` / `equip` / `unequip` / `clear` |

## 关卡开发指南

### 调整战斗参数

服务器第一次启动时会生成 `config/runelegend-core/tokens.json` 与 `config/runelegend-core/guard.json`。两者都只在服务器启动时读取，修改后需要重启服务器。

`guard.json` 控制格挡体力（1 tick = 1/20 秒）：

| 字段 | 默认值 | 说明 |
| --- | --- | --- |
| `max` | 20000 | 体力上限，取值范围 1–65535，HUD 以此值换算百分比 |
| `min_to_start` | 2000 | 起手格挡所需的最低体力，须在 0 到 `max` 之间 |
| `drain_per_tick` | 125 | 格挡期间每刻消耗的体力 |
| `regen_per_tick` | 100 | 恢复期间每刻恢复的体力 |
| `regen_delay_ticks` | 10 | 停止格挡后到开始恢复的延迟刻数 |

字段缺省时取默认值；出现非法值（类型错误、越界或 `min_to_start` 大于 `max`）时整套配置整体回退默认值，并在服务器日志中记录错误。

### 快速开始

1. 服务端与所有客户端安装本模组和 Fabric API（模组在握手阶段强制要求客户端安装）。
2. 玩家在等待状态用 `/runelegend token equip` 配置令牌，或由管理员用 `/runelegend admin` 统一配装。
3. 用 `/runelegend game start` 开始游戏（可直接放进命令方块）。
4. 关卡结束时用 `/runelegend game stop` 停止：回合与令牌运行状态重置、体力回满，玩家可以重新配装进入下一轮。

### 通过数据包定制

模组内置的数据标签都可以用自己的数据包覆盖或追加：

| 标签 | 类型 | 作用 | 默认值 |
| --- | --- | --- | --- |
| `runelegend:token_enemies` | 实体类型 | 令牌的“敌对目标”判定范围 | 空；为空时回退为原版 `Enemy` 生物 |
| `runelegend:bosses` | 实体类型 | Boss 判定 | `minecraft:ender_dragon`、`minecraft:wither` |
| `runelegend:ghost_family` | 实体类型 | 鬼魅生物判定 | 空 |
| `runelegend:sword_guardable` | 伤害类型 | 供物品 `blocks_attacks.damage_reductions[].type` 引用的可格挡伤害 | 近战、箭、蜂刺、爆炸等 7 种原版伤害 |
| `runelegend:sword_guard_bypasses` | 伤害类型 | 供物品 `blocks_attacks.bypassed_by` 引用的绕过格挡伤害 | `#minecraft:bypasses_armor`、`#minecraft:bypasses_invulnerability`、`runelegend:token_damage` |

`token_enemies` 非空时，只有该标签中的实体类型被视为令牌敌人；它不会自动与原版 `Enemy` 合并。下面的文件可将自定义实体纳入集合；若还需保留原版敌人，应同时列出所需类型。将文件放进数据包的 `data/runelegend/tags/entity_type/token_enemies.json`（内置标签均为 `replace: false`，可与数据包合并）：

```json
{
  "replace": false,
  "values": ["mymod:ghost_warrior"]
}
```

### 通过 Java API 集成

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

令牌框架本身也是可扩展的：每个令牌是一个 `TokenEffectHandler`，通过 `TokenRegistry` 注册，由 `TokenEngine` 分发事件（近战、受击、击杀、箭、格挡、生命变化）并管理运行状态，可参考 `src/main/java/com/runelegend/token/effect` 中的内置实现。当前版本尚未提供运行时注册入口，自定义令牌需要在本仓库内实现并重新构建。

`TokenEvent` 是令牌触发事件，不是伤害统计记录。`damageBeforeMitigation` 表示原版格挡和受伤间隔调整后、进入 `actuallyHurt` 的基础命中量，尚未经过护甲、附魔、抗性和吸收，也不包含独立追加的近战令牌伤害。旧的 `effectiveDamage()` 访问器保留但已弃用，返回值含义不变。致死受击不会向死亡玩家分发 `HURT`，令牌自产直接伤害也不会递归分发令牌事件，因此不能据此直接计算输出或承伤统计。

## 格挡与战斗机制

- `WAITING` 和 `RUNNING` 均可格挡；能否格挡由具体物品的 `blocks_attacks` 组件及服务端体力决定。状态切换不终止有效的原版物品使用。
- 体力上限默认 20000（HUD 以百分比显示），起手格挡默认至少需要 2000（10%），可在 `guard.json` 调整。
- 格挡期间默认每刻消耗 125（每秒 2500），满体力可连续格挡约 8 秒，耗尽时自动收剑；数值可在 `guard.json` 调整。
- 停止格挡默认 0.5 秒后开始恢复，每刻恢复 100（每秒 2000），从 0 回满约需 10 秒；数值可在 `guard.json` 调整。
- 格挡方向判定：近战取伤害来源位置，弹射物按其飞行轨迹反推，爆炸取爆心；方向不正确时按原版逻辑正常受伤。
- 如果不想使用盾牌，可以通过命令给予玩家不可破坏并且能格挡的铁剑，示例：
```
/give @s iron_sword[minecraft:unbreakable={},blocks_attacks={disable_cooldown_scale:0,damage_reductions:[{type:"#runelegend:sword_guardable",base:0,factor:0.7}],bypassed_by:"#runelegend:sword_guard_bypasses"}]
```
- 示例通过物品组件显式引用两个格挡标签；未引用这些标签的物品不会受其修改影响。模组不自动给剑添加或修改组件。
- 如果需要还原基岩版战斗机制，可以将玩家的 `attack_speed` 调高：
```
/attribute @s attack_speed base set 23
```

## HUD 说明

- 令牌图标仅在 `RUNNING` 且玩家存活时显示。触发时短暂发光，活动效果期间保持明亮，其余状态变暗；冷却中的图标显示剩余秒数。准备动画由具体令牌按需启用，与就绪状态独立。
- 体力低于满值或正在格挡时，图标行上方显示体力百分比。
- 可用 JVM 属性 `-Drunelegend.hudOffset=<0-100>` 向上微调 HUD 位置，避免与其他模组 HUD 重叠。

## 网络与存储

- 协议握手在配置阶段校验协议号与本次随机数，拒绝缺模组或协议不匹配的客户端；协议当前版本为 3。握手只确认协议兼容，不证明客户端实现可信，游戏判定始终以服务端为准。
- 协议 3 在玩家状态同步中携带当前体力上限（HUD 百分比按服务器配置缩放）；协议 2 客户端会在配置阶段握手被拒绝，服务端与客户端需要同步升级，无存档迁移。
- 令牌与体力状态采用“快照 + 增量”同步：常态只发送变化部分（体力最快每 4 刻一次），客户端检测到丢包失序会自动请求重同步，服务端每秒最多响应一次。
- 游戏状态保存在主世界维度数据目录的 `data/runelegend/game_rules.dat`（schema 1）。仅在文件确实不存在时初始化为 `WAITING`、回合 0；已有文件损坏、schema 不支持或无法读取时中止启动并报告文件路径，保留原文件，需修复或恢复后重启。正常 schema 1 存档无需迁移。令牌冷却与临时效果不跨服务器重启保留。
- 玩家装备保存在主世界维度数据目录的 `data/runelegend/player_tokens.dat`（schema 1）。文件无法读取时进入只读模式：玩家可正常游玩，但写入会被拒绝并在日志中报告，不会用空数据覆盖已有记录。

## 从源码构建

```bash
git clone https://github.com/Ice2974/RuneLegend.git
cd RuneLegend
./gradlew build
```

- 需要 JDK 25（Gradle 工具链会自动选择），构建产物在 `build/libs/`，包含 sources jar。
- `./gradlew test` 只运行单元测试（`src/test`，JUnit 5）。
- `src/gametest` 是 Fabric GameTest 套件，覆盖令牌效果、格挡与 HUD 同步，通过 `build.gradle` 的 `fabricApi { configureTests { … } }` 启用。

## 依赖

| 名称 | 类型 | 链接 |
| - | - | - |
| Fabric API | 必须 | [Modrinth](https://modrinth.com/mod/fabric-api) |

## 版本支持

| 游戏版本 | 开发状态 | 最后支持版本 |
| --- | --- | --- |
| 26.3 | 维护中 |  |
