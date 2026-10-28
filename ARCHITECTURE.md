# 当前代码结构与维护边界

更新于 2026-10-13。本文件描述当前实现，历史任务文档用于追溯，不作为运行完成的证据。

## 无线奥术终端

- 注册 ID 保持 `thaumicenergistics:wireless_arcane_crafting_terminal`。
- `ModItems` 只通过返回公共 `Item` 类型的兼容工厂创建可选实现，避免 JVM 提前加载 `ItemWT`。
- 完整 `ae2wtlib` 存在：`AE2WTArcaneTerminalItem` / `AE2WTArcaneTerminalMenuHost` 复用 AE2WT 连接、量子桥、奇点、电量与升级卡；客户端使用 `AE2WTArcaneTerminalScreen`，通过 `IUniversalTerminalCapable` 添加 Current Terminal 按钮与快捷键。
- 完整 `ae2wtlib` 缺席：使用独立无线物品/宿主和普通奥术屏幕。只有其他附属模组自带 `ae2wtlib_api` 时也走独立实现；API 本身不是启用正式集成的判据。
- `IArcaneTerminalInventoryHost` 只声明宿主能力；库存保存由 `PersistedTerminalInventories` 承担，打开期间的无线法杖补充由 `WirelessArcaneVis` 承担。
- `ArcaneTerminalRecipeState` 负责配方和成本计算，菜单保留槽位、操作和状态同步。

## 源质转移

- `AspectIds`：要素 ID 与实例查找。
- `EssentiaEndpoints`：带明确访问面的 TC capability 适配；未覆盖的旧容器交由兼容兜底。
- `LegacyFurnaceEssentia`：固定 TC 版本炼金炉私有方面缓存的反射。`TCReflection` 保留旧入口，避免一次性破坏调用者。
- `EssentiaTransfer`：网络与外部容器之间转移，返回实际交付量及余量；`insertOwned` 只处理已由 CPU/宿主持有的资源。
- `TransferBudget` / `EssentiaEnergy`：取得能量预算、限制资源数量、结算实际量并退款。
- `EssentiaItemContainers` / `EssentiaFillHelper`：真实玩家容器及部分成功后的内容保存。
- `EssentiaContainerItemStrategy`：AE2 标准填充/抽空及源质包装物品回收。
- `EssentiaTerminalInputHandler`：必要的客户端自定义手势；不拦截工具栏按钮。
- `EssentiaStorageBusInventory`：源质视图的读写权限和分区过滤，过滤配置变化才重新构建分区。
- `EssentiaInterfaceState`：接口缓冲与业务规则，Mixin 只接入字段和生命周期。

余量属于产生它的宿主，必须保存并重试。拆除无法退网的源质通过 AE2 包装物品保存，不能忽略实际插入数量。

## 样板与装配器

- `ArcaneAssemblerPattern`：AE2 样板定义与真实输入校验。
- `ArcanePatternDecoder`：完整编码样板的身份与解码，区分同产物不同配方。
- `ArcaneCraftJob`：当前订单的实际输入、配方成本、产物和待输出余量；沿用既有存档键。
- `TileArcaneAssembler`：节点、制作进度、vis 补充与方块生命周期。

制作前只校验 CPU 输入，接受后才清空输入计数器。生成产物只发生一次；之后只重试未被网络接收的部分。拆除时，制作前退真实输入，制作后掉落剩余产物和催化剂。

旧版本没有保存完整订单。仅在知识核心能唯一恢复对应配方时恢复；不能唯一确定时暂停旧订单并记录日志，不猜配方或让新订单覆盖它。

## 第三方供应器

`ProviderEssentiaTargets` 共用卡片判断与输出组合目标，物品/流体继续使用原目标，源质使用 `ThEEssentiaPushTarget`，不再次从网络取料。

- AE2 / EAE：沿用 AE2 逻辑和卡槽接入。EAE 准入采用注册 ID 软查。
- AAE 1.6.12：独立逻辑、宿主、菜单与两种屏幕追加升级库存及 UI；AppFlux 在场时跳过我方卡槽组。双参数 `findAdapter` 出口接入源质能力，能力组不与 AppFlux 互斥。
- AE2LT 2.1.2-beta3：普通路径复用 AE2；`ProviderTarget.resolveStorageTarget` 出口按来源卡片接入源质目标，定向、无线、批量和余量重试沿用其原生派发。

上述是代码实现范围，最终运行验收由用户负责，不宣称覆盖其他版本。

## 其他职责拆分

- `InfusionMonitorDisplay` / `InfusionMonitorText` 管理注魔监控器显示实体与文字；风险采集仍使用已有计算器和扫描间隔。
- `ProbabilityBrainRules` 集中概率之脑随机规则和数值；方块实体保留绑定、付费、库存及持久化协调。

## Mixin 边界

删除了两个空 Mixin 和两个重复屏幕点击 Mixin。原有配置保留 19 个接入；AAE 增加 6 个可选接入，AE2LT 增加 1 个可选接入，总计配置 26 个。可选加载插件在目标模组缺席时跳过对应接入。

保留 Mixin 的原因是宿主卡槽、生命周期和来源卡片门控没有足够的公开注册 API；不为减少数量而移除必要门控。

依赖和参考资料版本见 [DEPENDENCIES.md](DEPENDENCIES.md)。本轮交付记录见 [CHANGELOG-2026-10-13.md](CHANGELOG-2026-10-13.md)。
