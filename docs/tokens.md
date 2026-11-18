# 令牌

令牌是本模组提供的被动装备。每名玩家最多装备 3 个，装备仅在 `WAITING` 状态可修改，保存在配置目录的 `runelegend-core/players.json` 中（`schemaVersion: 1`，每名玩家的 `equippedTokens` 按槽位顺序记录令牌 ID）。所有触发、随机、伤害、治疗与状态变更都在服务端主线程判定，客户端只负责展示。

令牌效果只在 `RUNNING + ALIVE` 状态生效。玩家目标也必须为 ALIVE 才能接受治疗；所有玩家均不受令牌追加伤害、中毒等有害效果影响，即使被加入 `token_enemies` 标签。`WAITING` 状态下可以正常装备和卸下，但不会触发任何效果，格挡成功也不会产生令牌事件。

## 令牌生命周期

- 每次状态切换（`/runelegend game start`、`/runelegend game stop` 且状态确实改变）都会清空全部玩家的令牌运行态：充能、触发动画、生效中的效果、冷却和已调度的任务。
- 玩家倒地、阵亡或断线时清除该玩家的令牌运行态与冷却，令牌装备保留。运行中重连保留阵亡或观战身份，不能触发令牌；扶起或下一局重新参与后从空运行态开始。详见[玩家状态文档](player-states.md)。
- 灵魂摄取提供的额外生命上限在死亡、断线、停局时移除；离线玩家和旧版存档遗留的生命上限修饰符会在下次登录时清理，即使服务器已经开始了新局。
- 同一玩家的不同令牌各自持有独立状态；不同玩家之间不共享冷却、充能或效果。
- 令牌自产的直接伤害不会递归触发其他令牌事件。
- 周期、延迟和范围效果在实际执行时重新检查来源资格；清理前捕获的执行上下文在扶起后也不会恢复有效。实体存活替换可接续临时属性和剩余时长，死亡替换不能恢复战斗身份。

## 内置令牌

以下 9 种令牌的效果已实现。表内数值均为默认值，可在 `server.json` 的 `tokens` 段调整。

| ID | 名称 | 触发 | 效果 |
| --- | --- | --- | --- |
| `runelegend:soul_siphon` | 灵魂摄取 | 击杀敌对目标 | 5% 概率增加 2 点最大生命 |
| `runelegend:echo` | 残响 | 击杀敌对目标 | 50% 概率在目标位置留下半径 5 格的残响，持续 8 秒，每秒对范围内的敌对目标造成 3 HP 令牌伤害 |
| `runelegend:immediate_counter` | 即刻反击 | 成功格挡 | 随后 8 秒内每次直接近战命中额外造成 3 HP 令牌伤害；效果结束后冷却 12 秒 |
| `runelegend:venomous_tip` | 刃尖毒发 | 近战命中 | 50% 概率赋予目标 6 秒中毒 II |
| `runelegend:poisoned_arrow` | 淬毒之箭 | 箭命中 | 50% 概率赋予目标 12 秒中毒 II |
| `runelegend:asura_form` | 修罗化身 | 击杀敌对目标 | 累计击杀 3 只后变身 12 秒：近战追加 4 HP 令牌伤害、移动速度 +40%、临时血条 16 HP；结束后恢复原血条并冷却 9 秒 |
| `runelegend:resonance_healing` | 共鸣疗愈 | 成功格挡 | 自身生命不高于 50% 时，治疗自身周围 5 格内所有玩家 6 HP；冷却 15 秒 |
| `runelegend:vernal_tide` | 回春怒涛 | 近战命中 | 自身处于生命恢复状态时，50% 概率使本次命中追加 3 HP 真伤，并对以自身为中心半径 5 格内的其他敌人各造成 3 HP 令牌伤害 |
| `runelegend:arrogance` | 狂妄 | 生命值高于 60% 时，首次击杀敌对目标 | 直接近战对非玩家生物的追加真伤从 1 HP 开始，之后每击杀 1 个敌对目标再增加 1 HP，最多 5 HP；生命降至 60% 或以下时清空进度，无冷却 |

表中的“成功格挡”指原版 `blocks_attacks` 格挡实际减免了伤害的受击，部分减免和完全减免都会触发；`WAITING` 状态下不会产生格挡成功事件。

狂妄每次提高追加伤害时都会闪光，包括首次触发。击杀获得的新档位从下一次命中生效；箭杀可以累计，独立的令牌伤害击杀不计数。伤害吸收值不计入触发所用的生命比例。

另有 5 种已注册但效果尚未实现的令牌。它们可以正常装备、保存与同步，`/runelegend token list` 和 tab 补全也会列出，但不会触发任何效果，游戏内描述会提示尚未实现。它们没有对应的效果配置项。

| ID | 名称 | 状态 |
| --- | --- | --- |
| `runelegend:substitute` | 替身 | 效果尚未实现 |
| `runelegend:fountain_of_life` | 生命之泉 | 效果尚未实现 |
| `runelegend:full_blessing` | 全域祝福 | 效果尚未实现 |
| `runelegend:retribution` | 奉还 | 效果尚未实现 |
| `runelegend:execution_echo` | 斩杀回响 | 效果尚未实现 |

### 伤害类型

残响、即刻反击、修罗化身、回春怒涛和狂妄的追加伤害使用 `runelegend:token_damage` 伤害类型。该类型被加入原版 `#minecraft:bypasses_armor` 与 `#minecraft:bypasses_enchantments`，因此无视护甲和防护附魔，但仍会经过抗性提升、伤害吸收等非装备减伤，也不会递归触发令牌事件。

回春怒涛对命中目标的追加伤害并入该次近战结算（与即刻反击、修罗化身、狂妄相同，不产生第二次命中），对其余敌人的范围伤害是独立的 `runelegend:token_damage` 命中，因此会遵守原版受击间隔。溅射范围不包含被近战直接命中的目标。

刃尖毒发与淬毒之箭造成的中毒按原版 MobEffect 规则结算，可被原版免疫和抗性影响，不属于 `runelegend:token_damage`。

## 令牌配置

在 `config/runelegend-core/server.json` 的 `tokens` 段调整。文件在逻辑服务器启动时读取，修改后需要重启服务器或重新载入单人世界。

```json
{
  "schemaVersion": 1,
  "tokens": {
    "runelegend:soul_siphon": {
      "chance": 0.05,
      "max_health_bonus": 2.0
    }
  }
}
```

概率使用 0–1 的小数，`*_ticks` 以 20 tick = 1 秒换算，伤害和治疗的单位是 HP（2 HP = 1 颗心）。字段缺省时取默认值。单枚令牌的配置不是 JSON 对象时回退默认值，字段类型错误或越界时该令牌整段回退并记录错误。`tokens` 缺失或类型错误时，所有令牌使用默认值。未知令牌 ID 会被忽略并记录警告。修改后的数值会反映在 `/runelegend token info` 的描述中。文件生成和顶层校验规则见[关卡开发指南](level-development.md#调整战斗参数)。

### `runelegend:soul_siphon`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `chance` | 0–1 | `0.05` | 击杀敌对目标时的触发概率 |
| `max_health_bonus` | ≥ 0 | `2.0` | 触发后增加的最大生命 |

### `runelegend:echo`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `chance` | 0–1 | `0.5` | 击杀敌对目标时的触发概率 |
| `radius` | > 0 | `5.0` | 残响半径（格） |
| `damage` | ≥ 0 | `3.0` | 每次脉冲对单个敌对目标造成的令牌伤害 |
| `duration_ticks` | ≥ 0 的整数 | `160` | 残响持续时间（tick） |
| `interval_ticks` | > 0 的整数 | `20` | 脉冲间隔（tick） |

### `runelegend:immediate_counter`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `bonus_damage` | ≥ 0 | `3.0` | 每次近战追加的令牌伤害 |
| `duration_ticks` | ≥ 0 的整数 | `160` | 格挡成功后的生效时长（tick） |
| `cooldown_after_effect_ticks` | ≥ 0 的整数 | `240` | 效果结束后的冷却时长（tick） |

### `runelegend:venomous_tip`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `chance` | 0–1 | `0.5` | 近战命中时的触发概率 |
| `duration_seconds` | > 0 的整数 | `6` | 中毒持续时间（秒） |

### `runelegend:poisoned_arrow`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `chance` | 0–1 | `0.5` | 箭命中时的触发概率 |
| `duration_seconds` | > 0 的整数 | `12` | 中毒持续时间（秒） |

### `runelegend:asura_form`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `required_kills` | ≥ 1 的整数 | `3` | 变身所需的击杀数 |
| `duration_seconds` | ≥ 1 的整数 | `12` | 变身持续时间（秒） |
| `bonus_damage` | ≥ 0 | `4.0` | 变身期间每次近战追加的令牌伤害 |
| `speed_level` | ≥ 1 的整数 | `2` | 等效速度等级（每级 +20% 移动速度） |
| `temporary_health` | 1–1024 | `16.0` | 变身期间的临时生命上限 |
| `cooldown_seconds` | ≥ 0 的整数 | `9` | 变身结束后的冷却（秒） |

### `runelegend:resonance_healing`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `health_threshold` | 0–1 | `0.5` | 触发所需的生命比例上限（按最大生命的比例） |
| `radius` | > 0 | `5.0` | 治疗半径（格） |
| `heal_amount` | ≥ 0 | `6.0` | 对每名受治疗玩家恢复的生命 |
| `cooldown_seconds` | ≥ 0 的整数 | `15` | 触发后的冷却（秒） |

### `runelegend:vernal_tide`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `chance` | 0–1 | `0.5` | 近战命中且自身处于生命恢复状态时的触发概率 |
| `radius` | > 0 | `5.0` | 以自身为中心、对命中目标以外的敌人生效的范围半径（格） |
| `damage` | ≥ 0 | `3.0` | 命中目标追加、以及每个范围内其他敌人受到的伤害 |

### `runelegend:arrogance`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `health_threshold` | 0–1 | `0.6` | 当前生命比例须严格高于此值；降至此值或以下时重置 |
| `kills_per_increase` | ≥ 1 的整数 | `1` | 触发后每次增伤所需的敌对目标击杀数，首次触发固定需要 1 次击杀 |
| `damage_per_increase` | 有限正数 | `1.0` | 首次获得和之后每组击杀增加的真伤 |
| `max_bonus_damage` | 有限正数 | `5.0` | 每次直接近战命中可追加的真伤上限 |

整数型秒数上限为 `107374182`（`Integer.MAX_VALUE / 20`）。

## 扩展令牌

每个令牌是一个 `TokenEffectHandler`，通过 `TokenRegistry` 注册，由 `TokenEngine` 分发事件并管理运行状态。内置实现可参考 `src/main/java/com/runelegend/token/effect`。

- `TokenDefinition` 声明令牌订阅的事件：`MELEE`、`HURT`、`KILL`、`ARROW`、`GUARD_SUCCESS`、`HEALTH`。只有订阅了对应事件的令牌才会收到该事件；`HEALTH` 只在玩家生命或最大生命变化时按需分发。
- 效果应事件驱动；只有确实需要时间推进的效果才通过 `Context.at` 调度任务。
- 每个令牌的全部可调数值必须进入 `TokenConfig` 的 typed settings，并在 `server.json` 的 `tokens` 段暴露，效果类不得硬编码。
- 新增伤害路径时必须保留归因和嵌套命中隔离；令牌自产伤害不得递归触发令牌。
- 当前版本没有运行时注册入口，自定义令牌需要在本仓库内实现、在 `TokenRegistry.builtIn` 中登记并重新构建。

`TokenEvent` 是令牌触发事件，不是伤害统计记录。`damageBeforeMitigation` 表示原版格挡和受伤间隔调整后、进入 `actuallyHurt` 的基础命中量，尚未经过护甲、附魔、抗性和吸收，也不包含独立追加的近战令牌伤害。旧的 `effectiveDamage()` 访问器保留但已弃用，返回值含义不变。致死受击不会向死亡玩家分发 `HURT`，令牌自产直接伤害也不会递归分发令牌事件，因此不能据此直接计算输出或承伤统计。
