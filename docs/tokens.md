# 令牌

令牌是本模组提供的被动装备。每名玩家最多装备 3 个，装备仅在 `WAITING` 状态可修改，保存在配置目录的 `runelegend-core/players.json` 中（`schemaVersion: 1`，每名玩家的 `equippedTokens` 按槽位顺序记录令牌 ID）。所有触发、随机、伤害、治疗与状态变更都在服务端主线程判定，客户端只负责展示。

令牌效果只在 `RUNNING + ALIVE` 状态生效。玩家目标也必须为 ALIVE 才能接受治疗；所有玩家均不受令牌追加伤害、中毒等有害效果影响，即使被加入 `token_enemies` 标签。`WAITING` 状态下可以正常装备和卸下，但不会触发任何效果，格挡成功也不会产生令牌事件。

需要判定敌对目标的效果会使用关卡配置的实体类型范围与可选实例谓词，详见[按实例筛选令牌敌人](level-development.md#按实例筛选令牌敌人)。

## 令牌生命周期

- 每次实际开局或结算都会清空全部玩家的令牌运行态，包括充能、触发动画、生效中的效果、冷却和已调度的任务。
- 玩家倒地、阵亡或断线时清除该玩家的令牌运行态与冷却，令牌装备保留。运行中重连保留阵亡或观战身份，不能触发令牌；扶起或下一局重新参与后从空运行态开始。详见[玩家状态文档](player-states.md)。
- 灵魂摄取提供的额外生命上限在死亡、断线、停局时移除；离线玩家和旧版存档遗留的生命上限修饰符会在下次登录时清理，即使服务器已经开始了新局。
- 同一玩家的不同令牌各自持有独立状态；不同玩家之间不共享冷却、充能或效果。
- 令牌自产的直接伤害不会递归触发其他令牌事件。
- 周期、延迟和范围效果在实际执行时重新检查来源资格；清理前捕获的执行上下文在扶起后也不会恢复有效。实体存活替换可接续临时属性和剩余时长，死亡替换不能恢复战斗身份。

## 内置令牌

以下 14 种令牌的效果已实现。表内数值均为默认值，可在 `server.json` 的 `tokens` 段调整；残响半径 5 格与生命之泉半径 3 格为固定值，不开放配置。

| ID | 名称 | 触发 | 效果 |
| --- | --- | --- | --- |
| `runelegend:soul_siphon` | 灵魂摄取 | 击杀敌对目标 | 5% 概率增加 2 点最大生命 |
| `runelegend:echo` | 残响 | 击杀敌对目标 | 50% 概率在目标位置留下半径 5 格的残响，持续 8 秒，每秒对范围内的敌对目标造成 3 HP 令牌伤害 |
| `runelegend:immediate_counter` | 即刻反击 | 成功格挡 | 随后 8 秒内每次直接近战命中额外造成 3 HP 令牌伤害；效果结束后冷却 12 秒 |
| `runelegend:venomous_tip` | 刃尖毒发 | 近战命中 | 50% 概率赋予目标 6 秒中毒 II |
| `runelegend:poisoned_arrow` | 淬毒之箭 | 箭命中 | 50% 概率赋予目标 12 秒中毒 II |
| `runelegend:asura_form` | 修罗化身 | 击杀敌对目标 | 累计击杀 3 只后变身 12 秒：近战追加 4 HP 令牌伤害、移动速度 +40%、临时血条 16 HP；结束后恢复原血条并冷却 9 秒 |
| `runelegend:resonance_healing` | 共鸣疗愈 | 成功格挡 | 自身生命不高于 50% 时，治疗自身周围 5 格内符合条件的玩家。范围内每有一只存活的敌人，每人恢复 2 HP。冷却 10 秒 |
| `runelegend:vernal_tide` | 回春怒涛 | 近战命中 | 自身处于生命恢复状态时，50% 概率使本次命中追加 3 HP 真伤，并对以自身为中心半径 5 格内的其他敌人各造成 3 HP 令牌伤害 |
| `runelegend:arrogance` | 狂妄 | 生命值高于 60% 时，首次击杀敌对目标 | 直接近战对非玩家生物的追加真伤从 1 HP 开始，之后每击杀 1 个敌对目标再增加 1 HP，最多 5 HP；生命降至 60% 或以下时清空进度，无冷却 |
| `runelegend:execution_echo` | 斩杀回响 | 近战击杀敌对目标 | 记录该次攻击结算全部令牌追加伤害后的伤害（向上取整，最多 10 HP）；之后每次近战攻击追加该记录值的真伤，每秒衰减 1 HP，衰减到 0 时结束；结束后冷却 10 秒 |
| `runelegend:retribution` | 奉还 | 受到有伤害的主动攻击 | 持续 5 秒，从触发命中起累计吸收前的受伤数值。每次直接近战命中非玩家生物时追加等量真伤。到期清零并冷却 10 秒 |
| `runelegend:substitute` | 替身 | 自身生命不高于 50% | 召唤满血替身，复制自身当前的最大生命、护甲与皮肤。附近怪物优先攻击替身。替身存活 20 秒后自动死亡。被击杀或到期死亡时，对 5 格内的敌人造成 20 HP 普通爆炸伤害，随后冷却 40 秒 |
| `runelegend:fountain_of_life` | 生命之泉 | 自身生命不高于 50% | 在脚下留下水平半径 3 格的泉，持续 10 秒。泉内存活玩家获得生命恢复 II 和抗性提升 II，离泉后分别保留 1 秒和 5 秒。泉到期后冷却 10 秒 |
| `runelegend:full_blessing` | 全域祝福 | 自身生命不高于 50% | 同一游戏刻内祝福所有维度的在线存活玩家各 3 次，包括自身。每次恢复 4 HP 或存入库存，每人最多存 6 次。发放完成后冷却 20 秒 |

表中的“成功格挡”指原版 `blocks_attacks` 格挡实际减免了伤害的受击，部分减免和完全减免都会触发；`WAITING` 状态下不会产生格挡成功事件。

狂妄每次提高追加伤害时都会闪光，包括首次触发。击杀获得的新档位从下一次命中生效；箭杀可以累计，独立的令牌伤害击杀不计数。伤害吸收值不计入触发所用的生命比例。

斩杀回响只在挥击致死一个敌对目标时记录，且仅在增伤期与冷却期之外记录。记录值是该次挥击在护甲、防护附魔、抗性提升结算之后、伤害吸收之前的伤害，并包含其他令牌为同一次挥击追加的伤害（同样经过抗性结算）。记录值不超过 `max_recorded_damage`。增伤期内每次近战攻击追加当前剩余值，增伤衰减到 0 后开始冷却；增伤期与冷却期内的击杀不会覆盖记录。

奉还可由生物近战、有生物归因的投射物和直接法术、音波及生物自身爆炸触发。摔伤、中毒、荆棘、机关箭、TNT 和自伤仅在生效期间计入。累计值已经过格挡、受击间隔、护甲、防护附魔和抗性结算，取伤害吸收之前的数值。被伤害吸收完全抵消的命中仍可触发并计入，完全格挡的命中伤害为 0。之后每次近战主目标命中都使用当时的累计值，治疗和攻击均不会消耗该值。

每名装备者同时最多有一个替身。怪物锁定或选定本局玩家时，优先改打原版索敌距离内最近的可见替身。末影龙俯冲等绑定真实玩家的专属阶段、已发射的投射物和范围伤害沿用原版规则。

替身到期自动死亡，也可以提前被怪物击杀。替身死亡、被移除或随区块卸载时开始冷却。冷却结束时仍符合令牌资格和血量条件便自动再次召唤。回血后，存活的替身继续生效直至离场。玩家换维度时，替身留在召唤维度。队友无法直接伤害替身，替身死亡时没有装备掉落或经验。

全域祝福每次发放时按接收者当时的生命值判断。生命值不高于阈值时立即治疗，超过阈值时存入个人库存，库存已满时丢弃本次祝福。库存汇总来自所有装备者的祝福，接收者无需装备令牌。倒地、阵亡、断线和回合切换会清除库存。

接收者的生命值降至阈值或以下时，逐次消耗库存，直至生命值超过阈值、回满或库存用尽。装备者同时符合触发和消耗条件时，先结算整批全队祝福。库存已满也会正常开始冷却。冷却结束时，装备者仍符合触发条件便立即再次祝福。

生命之泉留在触发时的坐标和维度。玩家脚底的水平距离和高差均满足范围才算在泉内，高差最多为 1 格。气泡显示为一层薄薄的圆面。玩家离开或泉自然到期时开始计算残留时间，重入可续上效果。重叠的泉独立计时，同种效果取最高等级。更强或同级的普通药水优先生效，普通药水持续时间照常流逝。

冷却结束时，装备者仍符合资格且生命值不高于阈值，就在当时的位置重新触发。装备者倒地、阵亡或断线会撤销该来源的泉和残留效果。接收者失去令牌受益资格或回合结束时，清除对应的泉效果。

### 伤害类型

残响、即刻反击、修罗化身、回春怒涛、狂妄、斩杀回响和奉还的追加伤害使用 `runelegend:token_damage` 伤害类型。该类型被加入原版 `#minecraft:bypasses_armor` 与 `#minecraft:bypasses_enchantments`，因此无视护甲和防护附魔，但仍会经过抗性提升、伤害吸收等非装备减伤，也不会递归触发令牌事件。

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
| `damage` | ≥ 0 | `3.0` | 每次脉冲对单个敌对目标造成的令牌伤害 |
| `duration_ticks` | ≥ 0 的整数 | `160` | 残响持续时间（tick） |
| `interval_ticks` | ≥ 10 的整数 | `20` | 脉冲间隔（tick），最低 10 tick（0.5 秒） |

残响半径固定为 5 格，不开放配置；旧配置中的 `radius` 字段会被忽略。`interval_ticks` 低于 10 时整段配置回退默认值。

每次脉冲成功伤害至少一个敌对目标时，以残响中心播放一次水平扩散的圆环动画。动画每刻播放一帧，共 8 刻。

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
| `radius` | > 0 | `5.0` | 以触发者为中心，统计敌人和治疗玩家共用的半径（格） |
| `heal_per_enemy` | 有限数值且 ≥ 0 | `1.0` | 每只存活的令牌敌人为每名受治疗玩家提供的治疗量（HP） |
| `cooldown_seconds` | ≥ 0 的整数 | `10` | 触发后的冷却（秒） |

每名受治疗玩家恢复的生命值等于范围内存活的令牌敌人数乘以 `heal_per_enemy`。敌人沿用 [令牌敌人判定](level-development.md#通过数据包定制)。敌人数为 0 时治疗量为 0，成功触发仍会进入冷却。

已有配置使用 `heal_per_enemy` 设置每只敌人的治疗量。只有旧字段 `heal_amount` 时，每只敌人的治疗量使用默认的 1 HP。

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

### `runelegend:execution_echo`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `decay_per_interval` | 0.01–1024 | `1.0` | 每个衰减间隔减少的追加伤害（HP） |
| `decay_interval_seconds` | ≥ 1 的整数 | `1` | 衰减间隔（秒） |
| `cooldown_seconds` | ≥ 0 的整数 | `10` | 增伤衰减到 0 后的冷却（秒） |
| `max_recorded_damage` | 1–1024 的整数 | `10` | 击杀记录值的上限（HP） |

### `runelegend:retribution`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `duration_seconds` | ≥ 1 的整数 | `5` | 触发后的生效时长（秒） |
| `cooldown_seconds` | ≥ 0 的整数 | `10` | 生效结束后的冷却时长（秒） |

### `runelegend:substitute`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `health_threshold` | 0–1 | `0.5` | 当前生命值占最大生命值的比例上限，包含等于阈值 |
| `duration_seconds` | ≥ 1 的整数 | `20` | 替身从召唤到自动死亡的时长（秒） |
| `cooldown_seconds` | ≥ 0 的整数 | `40` | 替身离场后开始的冷却（秒），设为 `0` 时最早下一 tick 检查是否再次召唤 |
| `explosion_radius` | 有限正数 | `5.0` | 以替身死亡位置为中心的球形半径（格），含边界 |
| `explosion_damage` | 有限正数 | `20.0` | 对范围内每个敌人施加的爆炸伤害（HP），按原版护甲、附魔和抗性减免 |

爆炸只伤害符合令牌敌人筛选的生物，无距离衰减，不破坏方块或点燃地形。替身因区块卸载或回合清理而被移除时不会爆炸。

### `runelegend:fountain_of_life`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `health_threshold` | 0–1 | `0.5` | 触发血量的最大生命比例上限，包含等于阈值 |
| `regeneration_level` | 1–256 的整数 | `2` | 生命恢复等级 |
| `resistance_level` | 1–256 的整数 | `2` | 抗性提升等级 |
| `regeneration_linger_seconds` | ≥ 0 的整数 | `1` | 离泉后生命恢复持续时间（秒） |
| `resistance_linger_seconds` | ≥ 0 的整数 | `5` | 离泉后抗性提升持续时间（秒） |
| `duration_seconds` | ≥ 1 的整数 | `10` | 泉持续时间（秒） |
| `cooldown_seconds` | ≥ 0 的整数 | `10` | 泉消失后的冷却时间（秒） |

泉的水平半径固定为 3 格，不开放配置；旧配置中的 `radius` 字段会被忽略。时间字段最大为 `107374182` 秒，以保证换算的 tick 数能存为整数。

### `runelegend:full_blessing`

| 字段 | 范围 | 默认值 | 说明 |
| --- | --- | --- | --- |
| `blessings_per_trigger` | 1–1024 的整数 | `3` | 每次触发时，每名接收者本刻获得的祝福次数 |
| `health_threshold` | 0–1 | `0.5` | 触发、直接治疗及消耗库存共用的当前生命比例上限，包含等于阈值 |
| `heal_amount` | 有限正数 | `4.0` | 每次祝福恢复的生命值（HP） |
| `max_stored_blessings` | 0–1024 的整数 | `6` | 每名玩家的库存上限，设为 `0` 时禁用库存 |
| `cooldown_seconds` | ≥ 0 的整数 | `20` | 整批发放完成后开始的冷却（秒），设为 `0` 时最早下一刻再次触发 |

整数型秒数上限为 `107374182`（`Integer.MAX_VALUE / 20`）。

## 扩展令牌

附属 Fabric 模组通过 `com.runelegend.api.token` 提供令牌。在附属模组的 `fabric.mod.json` 中声明对 `runelegend` 的依赖，并添加公共入口。Core 在公共初始化期间调用入口一次，随后冻结注册表。入口应直接提交注册，设置和效果实例由 Core 在逻辑服务器启动后使用。

```json
{
  "depends": { "runelegend": "*" },
  "entrypoints": { "runelegend:tokens": ["example.RallyTokens"] }
}
```

```java
package example;

import com.mojang.serialization.Codec;
import com.runelegend.api.token.*;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ExtraCodecs;
import java.util.Set;

public final class RallyTokens implements TokenEntrypoint {
    private static final Codec<Float> SETTINGS =
            ExtraCodecs.floatRange(0.0F, 20.0F).fieldOf("heal").codec();

    @Override public void registerTokens(Registrar registrar) {
        registrar.register(Identifier.parse("mymap:rally"), SETTINGS, 2.0F,
                Set.of(TokenTrigger.Kind.GUARD_SUCCESS), heal -> new TokenEffect() {
                    @Override public void onEvent(TokenContext context, TokenTrigger trigger) {
                        if (context.heal(context.player(), heal)) context.flash();
                    }
                });
    }
}
```

- 注册使用完整 ID。`runelegend` 和 `minecraft` 是保留命名空间，同路径的不同命名空间各自独立。重复 ID、非法默认值和非对象设置都会导致注册失败。
- 设置 Codec 必须编解码 JSON 对象并校验字段范围。新生成的 `server.json` 会在 `tokens` 写入默认配置，例如 `"mymap:rally": {"heal": 2.0}`。已有文件可手动加段，缺段使用默认值，非法段记录错误并只回退对应令牌。Core 保留原配置文件，重启逻辑服务器后读取修改。
- 用 `/runelegend token equip mymap:rally` 配装，用 `/runelegend token info mymap:rally` 查看说明。附属令牌在命令中使用完整 ID，内置令牌仍支持省略 `runelegend:`。移除附属模组后，已保存的 ID 仍占用原槽位，HUD 显示未知图标，可用完整 ID 卸下。重新装回附属模组会恢复该 ID 的定义。

### 客户端资源

将名称 `token.mymap.rally` 和说明 `token.mymap.rally.description` 放入 `assets/mymap/lang/en_us.json`、`zh_cn.json` 等语言文件。缺少语言条目时显示翻译键。图标放在 `assets/mymap/textures/gui/sprites/token/rally.png`。如果 ID 为 `mymap:combat/rally`，图标路径为 `textures/gui/sprites/token/combat/rally.png`，语言键中的路径也保留 `combat/rally`。客户端可从附属模组或资源包加载这些资源，缺少图标时使用 Core 的未知图标。服务端代码和配置由服务端加载，客户端安装 Core 即可通过现有握手和同步接收完整 ID。

### 触发与清理

- `TokenEffect` 工厂应返回新实例。实例按逻辑服务器、玩家 UUID、令牌 ID 和运行态代次隔离，可在实例字段保存本局状态。触发在服务器主线程进行，只对当前已连接、存活且非旁观的 `RUNNING + ALIVE` 玩家分发。
- 可订阅 `MELEE`、`HURT`、`KILL`、`ARROW`、`GUARD_SUCCESS`、`HEALTH`。近战、箭和击杀事件的目标是被命中的实体；受击、格挡成功和生命变化事件的目标是令牌持有者。`HEALTH` 在生命或最大生命变化时分发。`HURT` 只分发给存活玩家。`startCooldown` 更新 HUD 及查询值，效果可用 `cooldownRemainingTicks` 判断是否继续响应事件。
- `TokenContext` 只在当前回调和服务器线程上有效。延迟工作用 `schedule(key, delayTicks, callback)`，在回调内用新上下文取得当前玩家，避免持有旧玩家实体或旧上下文。执行时重新校验回合、连接、身份和运行态代次。
- 用 `trackEffect` 登记属性修饰符、召唤物等托管资源的清理。同键登记会先执行原清理，`finishEffect` 会撤销资源并取消同键任务。存活实体替换时，`onPlayerReplaced` 在新实体接管连接前调用，效果可以迁移实体绑定资源，原冷却和调度继续有效。倒地、阵亡、断线、停局及服务器关闭会取消运行任务，撤销托管资源，再调用 `onCleanup`。

### 伤害通道

| 接口 | 结算 |
| --- | --- |
| `TokenContext.damage` | 单独的原版玩家攻击伤害，经护甲、防护附魔、抗性和吸收结算。保留伤害统计归因，普通直接伤害和其击杀均不会递归触发令牌。 |
| `TokenEffect.penetratingMeleeBonus` | 订阅 `MELEE` 时可为本次基础近战追加有限正值。追加部分绕过护甲和防护附魔，仍受抗性及吸收影响，与基础伤害合并为一次命中。 |
| `TokenContext.applyDebuff` | 用原版免疫及合并规则施加有害状态。中毒、凋零等后续效果沿用原版伤害类型和计时，生物身上已经施加的原版状态按原版到期。 |

伤害和有害状态接口排除玩家、替身和不在当前维度的目标。治疗接口限同维度的存活参与者。当前生命条或修罗化身保留的常态生命条实际增加时，`heal` 返回 `true`。附属模组是可信 Java 代码，应使用这些接口实施效果，遵守合作玩法的目标限制。

`TokenTrigger.damageBeforeMitigation` 表示原版格挡和受伤间隔调整后、进入 `actuallyHurt` 的基础命中量，尚未经过护甲、附魔、抗性和吸收，也不含穿透追加。`damageBeforeAbsorption` 表示护甲、防护附魔和抗性结算后，加上同次挥击的近战令牌追加，且尚未扣除吸收的数值。没有命中记录的合成事件两值相同。结算统计使用独立的回合统计接口。
