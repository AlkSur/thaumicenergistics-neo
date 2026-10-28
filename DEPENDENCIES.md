# 依赖与参考资料索引

更新于 2026-10-13。`library` 是资料区，不等于全部运行依赖；没有移动或删除其中的旧资料。

## 当前依赖

- Minecraft 1.21.1 / NeoForge 21.1.234 / Java 21。
- AE2 Maven 坐标 `org.appliedenergistics:appliedenergistics2:19.2.17`，同时使用 API 与完整 JAR 编译内部接入。
- AE2 完整 JAR SHA-1：`49c18d6a4af487957d7e5a6ad5dcbf71090b8e14`；sources JAR SHA-1：`260a230e9fa1b4885489f7f58b30c19226093a52`。
- AE2WT API 编译坐标 `de.mari_023:ae2wtlib_api:19.5.1`，`transitive=false`。仅完整 `ae2wtlib` 存在时加载接入类。
- 生产依赖保持 compileOnly；开发运行前置只在兼容开关启用时添加。

| library 文件 | modid | 用途 | SHA-1 |
|---|---|---|---|
| AdvancedAE-1.6.12-1.21.1.jar | advanced_ae | 可选 AAE 接入；Maven ablTMAjP 与该文件同指纹 | `9358ccfa5477c7ab1c5ffab6c831e105fe46ecc3` |
| ae2jeiintegration-1.2.1.jar | ae2jeiintegration | 当前可选 JEI 转换 API | `e99898b3e9c32bf6ac757e7ad50247746622cdfc` |
| ae2lt-2.1.2-beta3.jar | ae2lt | 可选 AE2LT 接入；Maven AosdXlfS | `f21381bdcc4af71781050f02166d847e4dbb0880` |
| ae2wtlib-19.5.1.jar | ae2wtlib | 可选正式接入与运行开关 | `b1bee01393b15f1eb0f46d58862ef99234e1d337` |
| ae2wtlib_api-19.5.1.jar | ae2wtlib_api | API 对照；Gradle 使用固定 Maven 编译坐标 | `8383d132b7b5f4b31ca77c7bdbaf61fe01aaabc1` |
| ExtendedAE-1.21-2.2.35-neoforge.jar | extendedae | 可选 EAE 对照；Maven bC302UUP | `e3521ca2567fabe0f0131cc923ec94dd99d6fa7b` |
| extendedae_plus-1.6.1.jar | extendedae_plus | 当前可选 EAP Mixin 编译目标 | `222c613c091ab607ad6f8b900600c77bf804ac1c` |
| InventoryProfilesNext-neoforge-1.21.1-2.2.5.jar | inventoryprofilesnext | 当前 IPN 注解编译依赖 | `b09d7c0820c233ea34af54d0a3ab7ed510b32dcc` |
| jei-1.21.1-neoforge-19.39.0.368.jar | jei | 当前可选 JEI 编译 API | `82a8ee647d81c40f149c444c5d37088f035cb773` |
| thaumcraft-0.2.2.34-port.1.jar | thaumcraft | 历史/辅助对照；不是当前编译基线 | `852fd2a72632d14c5bcf0f6dcab054f18bf059f9` |
| thaumcraft-0.2.2.95-port.229.jar | thaumcraft | 历史/辅助对照；不是当前编译基线 | `63fd2766e3853cb4bb6985a3f3f627a5e2c51b45` |
| thaumcraft-0.2.2.95-port.256.jar | thaumcraft | 历史/辅助对照；不是当前编译基线 | `bc0c25588ea23ea4c1d02df02662dc01de7230aa` |
| thaumcraft-0.2.2.95-port.261.jar | thaumcraft | 当前编译与运行基线 | `1584c9cedfacf25009d0518697e98290c9f5d4fd` |
| thunderbolt-2.0.3-beta2.jar | thunderbolt | AE2LT 前置；Maven oyVnlG5A | `f16d374a08f5cde870d4221e5c8e7d89f858e0d4` |
| wcwt-1.3.8.jar-ME综合工作终端.jar | wcwt | 历史/辅助对照；不是当前编译基线 | `02b41fab2e7507edb9c98aa43f750bf466010880` |

## 固定兼容版本与开发运行

- `-PcompatWT=true`：AE2WT 19.5.1，完整 JAR 内嵌对应 API。
- `-PcompatAAE=true`：AAE 1.6.12（ablTMAjP）+ GeckoLib 4.8.2（RVIo5f6E）；AAE 内嵌 AE2AddonLib 1.0.3 和 AE2WT API 19.2.5。
- `-PcompatAE2LT=true`：AE2LT 2.1.2-beta3（AosdXlfS）+ Thunderbolt 2.0.3-beta2（oyVnlG5A）；AE2LT 内嵌 AE2WT API 19.4.1。
- `-PenableExtendedAeCompatTest=true`：EAE 1.21-2.2.35（bC302UUP）+ Glodium 1.21-2.2（pfbmdJ3b）；EAE 内嵌 AE2WT API 19.2.0。
- 启用任一兼容开关自动补齐 AE2、TC、GuideME 21.1.1（PALpfSdf）、Curios 9.5.1+1.21.1（yohfFbgD）。已下载的隔离依赖可复用 `build/smoke-deps`。
- 仅第三方附属模组自带旧 AE2WT API 时，不启用我方 ItemWT 实现，也不因 API 版本阻止独立终端启动；完整 AE2WT 自身会要求其匹配的 API。
- AE2LT 元数据要求 AE2 `[19.2.17,19.3)`，不要在这套组合中升级到 19.3+。

## 资料优先级

1. 当前固定版本的完整 JAR / sources JAR / 实际字节码。
2. 与固定版本一致的 API 文档。
3. `thaumcraft-addon-sdk-0.2.2.95-port.264` 只作导航，不能替代 port.261 的实际 API。
4. `参考` 内旧 TC port.34/其他反编译、1.7.10 源码及 AE2 工作树只用于历史和思路对照；先核对版本。
5. `ExtendedAE-appflux-1.21.1-neoforge` 可用于卡槽蓝图对照，不能直接照搬其能量或工具箱私有类型。
6. 材质包、备份资源、日志与笔记不进入编译依赖；不进行整体递归扫描。

## 发布来源

- [AE2](https://github.com/AppliedEnergistics/Applied-Energistics-2)、[AE2WT](https://github.com/Mari023/AE2WirelessTerminalLibrary/tree/1.21.1)。
- [AdvancedAE 固定发布](https://modrinth.com/mod/advancedae/version/ablTMAjP)、[AE2LT 固定发布](https://modrinth.com/mod/ae2-lightning-tech/version/AosdXlfS)。
- [ExtendedAE 固定发布](https://modrinth.com/mod/extended-ae/version/bC302UUP)。
- [GeckoLib](https://modrinth.com/mod/geckolib/version/RVIo5f6E)、[Glodium](https://modrinth.com/mod/glodium/version/pfbmdJ3b)、[GuideME](https://modrinth.com/mod/guideme/version/PALpfSdf)、[Curios](https://modrinth.com/mod/curios/version/yohfFbgD)。
- TC 使用本地 port.261 构件，索引记录本地指纹，不把旧 SDK 或旧源码目录当作该构件来源。
