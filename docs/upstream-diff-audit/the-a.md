# 正确性审计：神秘能源 common 区（the-a，前缀 TEA）

本项目的“上游”就是 AlkSur 的 Thaumic Energistics Neo 本身，所以这一部分是正确性审计：对照 AE2 19.2.x 的契约、
SCEX 神秘时代 API，以及模组自己的说明文字。范围：`thaumicenergistics/common/**`（不含 `common/integration/**`）和 `api/**`。

## 状态

**部分完成。** 并行审计因额度两次中断，之后由主会话核对并提交已有改动。
已通读：tiles（装配器、源质供应器及其连接、注魔供应器、谐振仓、概率之脑）、全部 blocks、`WrenchFocusHandler`，
以及装配器、蒸馏样板编码器、知识铭刻器三个菜单。尚未阅读：InfusionMonitor、InfusionRiskCalculator、CellWorkbench、
crafting、entities、essentia/capability、features、grid、inventory、items（除扳手核心）、monitor、network、parts、storage、api。

## 已修复

| ID | 位置 | 问题 | 严重度 | 修复 |
| --- | --- | --- | --- | --- |
| TEA-01 | `container/slot/GhostSlot.java`、`ContainerKnowledgeInscriber` | 幽灵槽 `mayPickup` 为 true：双击收集（PICKUP_ALL）能把九格幽灵副本收进手里，凭空复制物品；拖拽分发会把真实物品“放进”模板格后丢失 | P0 | `mayPickup` 改为 false；菜单覆写 `canTakeItemForPickAll` 和 `canDragTo` 排除幽灵槽 |
| TEA-02 | `ContainerKnowledgeInscriber` | 菜单持有知识核心的副本并在关闭时写回：两名玩家同时打开时可各取走一颗核心，或用过期副本覆盖对方的修改 | P0 | 副本与方块实体里的核心不一致时关闭菜单；关闭时不再写回 |
| TEA-03 | `ContainerArcaneAssembler`、`TileArcaneAssembler.onBreakBlock` | `stillValid` 恒为 true，方块拆掉后菜单仍可操作；拆除时掉落物品但不清空槽位，仍开着的菜单能再取一份（复制） | P0 | `stillValid` 检查方块实体仍在原位且距离 ≤ 8 格；掉落时用 `removeItemNoUpdate` 清空槽位 |
| TEA-04 | `ContainerDistillationPatternEncoder`、`TileDistillationPatternEncoder` | `stillValid` 恒为 true；拆除方块时空白样板和已编码样板随方块消失；编码时先扣空白样板再检查要素能否映射，失败时白吞一张；源物品模板槽接受客户端给的任意数量 | P0/P1 | 同 TEA-03 的 `stillValid`；新增 `onBreakBlock` 掉落两格样板；先校验再扣样板；模板恒为 1 个；模板槽不参与双击和拖拽 |
| TEA-05 | `ContainerArcaneAssembler.getTile` | 开包与方块更新有竞态时直接强转，客户端崩溃 | P1 | 用 `instanceof` 判断，不是装配器时返回 null |
| TEA-06 | `blocks/BrainOfProbabilityBlock.java` | 放置时无条件覆盖上方方块，可以删掉基岩、别人的容器等任意方块 | P0 | 上方必须在建筑高度内且可替换，否则拒绝放置并提示；非玩家放置时上格放不下则保持“结构不完整”，不覆盖 |
| TEA-07 | `WrenchFocusHandler`、`items/ItemFocusAEWrench.java`、`blocks/ThEWrenchHandler.java`、`FumoBlock` | 扳手核心从 24 格外拆卸/旋转，不经过任何权限检查，可以隔着领地拆走别人的 ME 设备；冒险模式和出生点保护也不拦 | P0 | 新增 `mayModify`（旁观/冒险/出生点保护）和 `mayModifyRemotely`（另外广播一次 `BreakEvent` 让保护类模组拒绝）；先过权限再扣 vis |
| TEA-08 | `WrenchFocusHandler` | 服务端对 START/STOP/ABORT 都触发 `LeftClickBlock`，一次点击旋转两次；而且对任意方块调用 `rotate`，会把床、门、双箱的半块转歪，下次更新整件消失 | P1 | 只处理 START；只旋转 AE2 方块实体和本模组的 ME 设备 |
| TEA-09 | `blocks/OrientableEntityBlock.useItemOn` | 客户端返回 SUCCESS、服务端返回 PASS，手持方块对着谐振仓右键时客户端不预测放置 | P3 | 两端返回值一致 |

新增语言键（英文、中文）：`thaumicenergistics.brain_prob.no_room`、`thaumicenergistics.focus_aewrench.denied`。

## 已发现、尚未修复

这些是阅读时记录的候选问题，还没有逐条确认和修复：

- 装配器：空闲时 AE tick 间隔退到 20，`visTickCounter` 按调用次数计，充能和首次合成被放慢，加速卡对首单无效；`extractAEPower` 不足 90% 时电力白扣；`StoredVis` 读档不钳制。
- 源质供应器：`validateLinkedReceivers` / `getLinkedProvider` 对 32 格外未加载的区块调用 `getBlockEntity`（会同步加载区块）；`AEColor.values()[n]` 读档可能越界；`inject` 在服务不可用或要素没有 ID 时返回 0（等于全部接受），会吞源质。
- 注魔供应器：`takeFromContainer(aspect, n)` 部分提取后返回 false，会吞源质。
- 谐振仓：只有一个计数器，由最后一种要素决定整槽的燃烧档位（便宜的要素可以按 potentia 档燃烧）；燃烧末段多算发电；读档不钳制；研究文本与实际倍率不符。

## 验证

- `compileJava compileTestJava compileGameTestJava test -Pthaumcraft_version=0.2.2.107-port.277`：通过。
- `runSmokeGameTests -PcompatSmokeTest=true -Pthaumcraft_version=0.2.2.107-port.277 -Pneoforge_version=21.1.248`：模组能与 TC port.277 一起加载，冒烟 GameTest **12/12 通过**（TC port.277 要求 NeoForge ≥ 21.1.248，项目默认的 21.1.234 加载不了它）。
- 本批修复没有新增自动化测试，未在游戏中验证。模组版本号（`2.6.10-alpha`，沿用上游）没有改动。
