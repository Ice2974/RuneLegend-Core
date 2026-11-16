# RuneLegend-Core

RuneLegend-Core 是一个面向《圣符传说》第三方关卡开发者的 Fabric 模组。它在 Fabric 端上复刻了圣符传说的核心战斗系统——令牌、格挡体力，并提供游戏流程控制等功能。\
关卡作者可以直接通过命令、数据包或 Java API 搭建自己的圣符传说关卡，而不必自己实现这些机制。

《圣符传说》原是 EaseCation 服务器上的四人合作 PvE 剧情玩法，背景与规则可参考[官方 Wiki](https://wiki.easecation.net/%E5%9C%A3%E7%AC%A6%E4%BC%A0%E8%AF%B4)。\
**本项目是独立的社区第三方实现，与 EaseCation 官方无关。**

## 功能一览

- **游戏流程控制**：`WAITING` / `RUNNING` 两种状态。只有等待状态能更换令牌装备；每次状态切换都会清空回合内的令牌运行状态，开始新局会推进回合计数。状态、回合数和修订号只保存在服务端内存中，服务器重启或单人世界重新载入后会回到 `WAITING`、回合 0。
- **玩家状态**：`ALIVE` / `DOWNED` / `ELIMINATED` / `SPECTATOR`。倒地保留背包和经验，队友可在死亡坐标持续潜行扶起；不死图腾优先生效。没有 ALIVE 玩家后自动回到等待状态。规则和配置见[玩家状态文档](docs/player-states.md)。
- **令牌系统**：每名玩家 3 个令牌槽，同一 Minecraft 安装实例下的单人世界共用令牌装备，独立服务端使用自己的配置目录。所有效果由服务器权威判定，且只在 `RUNNING + ALIVE` 状态触发或接受治疗。倒地、阵亡、断线或停局会清除该玩家的充能、活动效果、冷却和调度任务；令牌装备本身保留。
- **体力系统**：`RUNNING` 下格挡持续消耗体力，体力耗尽自动收剑，低于起手阈值时无法起手（默认 10%）；`WAITING` 下格挡不消耗也不检查体力。体力数值由服务端结算；客户端只负责显示与输入时序协调，不决定格挡结果。
- **物品锁定**：带有 `custom_data` 标记 `runelegend:item_lock` 的物品在非创造模式玩家的物品栏和容器中受保护，无法被移动、丢弃、交换或用于装备、合成、交易；死亡掉落等原版行为不受限制。详见[关卡开发指南](docs/development.md#物品锁定)。
- **客户端 HUD**：准星下方展示令牌槽位图标（就绪 / 触发 / 生效 / 冷却倒计时）与体力百分比，无需资源包即可使用。

## 命令

所有命令都在 `/runelegend` 下。`token_id` 省略命名空间时默认为 `runelegend:`。

| 命令 | 权限 | 说明 |
| --- | --- | --- |
| `/runelegend token list` | 所有玩家 | 列出全部已注册令牌（含尚未实现效果的令牌） |
| `/runelegend token info <token_id>` | 所有玩家 | 查看令牌名称与描述 |
| `/runelegend token status` | 所有玩家 | 查看自己已装备的令牌及冷却剩余秒数 |
| `/runelegend token equip <token_id>` | 所有玩家 | 装备令牌，仅等待状态可用 |
| `/runelegend token unequip <token_id>` | 所有玩家 | 卸下令牌，仅等待状态可用 |
| `/runelegend token clear` | 所有玩家 | 清空自己的令牌，仅等待状态可用 |
| `/runelegend game status` | 所有玩家 | 查看游戏状态 |
| `/runelegend player status` | 所有玩家 | 查看本局身份或下一局观战选择 |
| `/runelegend player spectate <true\|false>` | 所有玩家 | 仅等待时选择下一局是否观战，选择只消费一次 |
| `/runelegend game start` | `runelegend:game`（默认权限等级 2） | 开始游戏 |
| `/runelegend game stop` | `runelegend:game`（默认权限等级 2） | 停止游戏并清空本局运行态 |
| `/runelegend admin <player> …` | `runelegend:admin`（默认权限等级 3） | 以其他玩家为目标执行 `status` / `equip` / `unequip` / `clear` |

## 令牌

每名玩家最多装备 3 个令牌，装备仅在 `WAITING` 状态可修改，并保存在配置目录的 `runelegend-core/players.json` 中。内置令牌的效果、触发条件、配置项与扩展方式见 **[docs/tokens.md](docs/tokens.md)**。

令牌运行态不跨对局保留：死亡、断线以及每次状态切换都会清空充能、生效中的效果、冷却与调度任务。`/runelegend game stop` 会立即移除在线玩家由灵魂摄取获得的额外生命上限；离线玩家在下次登录时移除，旧版存档遗留的生命上限修饰符同样会在登录时清理。

## 格挡与战斗机制

以下体力规则只在 `RUNNING` 状态生效；`WAITING` 下可以无限格挡，不消耗体力，也不会触发令牌事件。

- `WAITING` 和 `RUNNING` 均可格挡；能否格挡取决于物品的 `blocks_attacks` 组件，`RUNNING` 下还要求体力不低于起手阈值。状态切换不会终止已经开始的格挡。
- 体力上限默认 20000（HUD 以百分比显示），起手格挡默认至少需要 2000（10%），可在 `guard.json` 调整。
- 格挡期间默认每刻消耗 125（每秒 2500），满体力可连续格挡约 8 秒，耗尽时自动收剑；数值可在 `guard.json` 调整。
- 停止格挡默认约 0.5 秒后开始恢复，每刻恢复 100（每秒 2000），从 0 回满约需 10 秒；数值可在 `guard.json` 调整。
- 格挡期间无法近战攻击。起手失败或体力耗尽后需要松开使用键再重新按住，按住不放不会自动重试。
- 格挡方向判定：近战取伤害来源位置，弹射物按其飞行轨迹反推，爆炸取爆心；方向不正确时按原版逻辑正常受伤。
- 如果不想使用盾牌，可以通过命令给予玩家不可破坏并且能格挡的铁剑，示例：
```
/give @s iron_sword[minecraft:unbreakable={},blocks_attacks={disable_cooldown_scale:0.0f,damage_reductions:[{type:"#runelegend:sword_guardable",base:0.0f,factor:0.7f}],bypassed_by:"#runelegend:sword_guard_bypasses"}]
```
- 示例通过物品组件显式引用两个格挡标签；未引用这些标签的物品不会受其修改影响。模组不自动给剑添加或修改组件。
- 如果需要还原基岩版战斗机制，可以将玩家的 `attack_speed` 调高：
```
/attribute @s attack_speed base set 23
```

## HUD 说明

- 令牌图标仅在 `RUNNING + ALIVE` 时显示。触发时短暂发光，活动效果期间保持明亮，其余状态变暗；冷却中的图标显示剩余秒数。准备动画由具体令牌按需启用，与就绪状态独立。
- 体力低于满值或正在格挡时，图标行上方显示体力百分比。
- 可用 JVM 属性 `-Drunelegend.hudOffset=<0-100>` 向上微调 HUD 位置，避免与其他模组 HUD 重叠。

## 网络与存储

- 协议握手在配置阶段校验协议号与本次随机数，拒绝缺模组或协议不匹配的客户端；协议当前版本为 5。握手只确认协议兼容，不证明客户端实现可信，游戏判定始终以服务端为准。
- 协议 5 在现有快照和增量中加入玩家身份、观战选择、倒地标记及回合修订信息；协议 4 及更早的客户端会在配置阶段被拒绝。服务端与客户端需要同步升级，公共 Java API 与 `players.json` 格式不变，无需迁移存档。
- 令牌、体力和玩家状态采用“快照 + 增量”同步：常态只发送变化部分（体力最快每 4 刻一次，倒计时整秒变化时更新），客户端检测到丢包失序会隐藏不可信标记并请求重同步，服务端每秒最多响应一次。
- 游戏状态、回合数、玩家身份、倒地记录、扶起进度、令牌冷却与临时效果不写盘，按存档会话隔离。服务器重启后回到 `WAITING`、回合 0，需要重新开始游戏。
- 玩家令牌装备按 UUID 保存在 `config/runelegend-core/players.json`（`schemaVersion: 1`），每名玩家的 `equippedTokens` 按槽位顺序保存令牌 ID。服务端启动时读取，装备变更成功后立即落盘。读写失败时会记录日志，装备进入只读状态直至重启。
- 从使用世界存档的版本升级时，需要重新佩戴令牌。新版本不会导入或修改旧世界的 `data/runelegend/player_tokens.dat`。

## 关卡开发

命令与流程参数、数据包标签、物品锁定与 Java API 见 **[docs/level-development.md](docs/development.md)**；令牌列表、数值配置与扩展方式见 **[docs/tokens.md](docs/tokens.md)**。

## 从源码构建

```bash
git clone https://github.com/Ice2974/RuneLegend.git
cd RuneLegend
./gradlew build
```

- 需要 JDK 25（Gradle 工具链会自动选择），构建产物在 `build/libs/`，包含 sources jar。Windows 下使用 `gradlew.bat`。
- `./gradlew test` 只运行单元测试（`src/test`，JUnit 5）；`./gradlew build` 会同时运行服务端 GameTest。
- `src/gametest` 是 Fabric GameTest 套件，覆盖令牌效果、格挡、物品锁定、命令输出与 HUD 同步，通过 `build.gradle` 的 `fabricApi { configureTests { … } }` 启用；客户端 GameTest 用 `./gradlew runClientGameTest` 运行（需要图形环境）。
