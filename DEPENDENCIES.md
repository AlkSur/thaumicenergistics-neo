# 依赖与参考资料索引

更新于 2026-10-28。原 neo 仅保留未公开发布的 TC 移植版为本地二进制依赖；其余直接模组依赖通过固定版本 Maven 坐标获取。`library` 中既有文件保留作参考，没有移动或删除。

## GitHub 源码下载构建

独立单模组仓库：安装 JDK 21，将 port.261 TC Jar 放入根目录 `library/`，执行 `./gradlew.bat assemble`（Linux/macOS：`bash ./gradlew assemble`）。独立构建使用 `mod_version`，无需工作区版本脚本，详细说明见 [README.md](README.md)。

多模块工作区：安装 JDK 21，将 port.261 TC Jar 放入本模块 `library/` 后，在工作区根目录运行：

```powershell
.\gradlew.bat :thaumicenergistics:assemble -PneoOnly=true
```

Linux/macOS 使用 `bash ./gradlew` 替代 `./gradlew.bat`。新补齐的 Wrapper 自动下载固定 Gradle 8.12.1，并校验官方发行包 SHA-256；无需独立安装 Gradle。`neoOnly=true` 只包含原 neo，其他模块无需准备本地 Jar。GitHub ZIP 无 `.git` 时使用模块的 `mod_version` 回退版本，不要求 Git 安装或历史记录。

## 当前依赖

本模块移除了根工程继承的 Maven Local 仓库，不再优先使用本机手工安装的同坐标构件。Maven Central、本模块自行声明的 JEI Maven（限定 `mezz.jei` 组）、CurseMaven、Modrinth Maven 和 ModMaven 提供公开构件，独立导出时也可解析。其他子项目的仓库配置保持原样。

| 依赖 | 固定坐标或本地文件 | 用途 |
|---|---|---|
| AE2 19.2.17 | `org.appliedenergistics:appliedenergistics2:19.2.17`，另保留 `:api` 分类构件 | compileOnly；兼容开关启用时 runtimeOnly |
| TC port.261 | `library/thaumcraft-0.2.2.95-port.261.jar` | 唯一本地 Jar；compileOnly，兼容运行时也使用同一文件 |
| JEI 19.39.0.368 | `mezz.jei:jei-1.21.1-neoforge:19.39.0.368` | compileOnly，完整构件保持原编译范围 |
| AE2 JEI Integration 1.2.1 | `curse.maven:ae2-jei-integration-1074338:7727898` | compileOnly |
| IPN 2.2.5 / NeoForge 1.21.1 | `maven.modrinth:inventory-profiles-next:vjuNnHLv` | compileOnly |
| ExtendedAE-Plus 1.6.1 | `maven.modrinth:extendedae-plus:DLFDZIVx` | compileOnly，保留 EAP Mixin 编译目标 |
| AE2WT API 19.5.1 | `de.mari_023:ae2wtlib_api:19.5.1` | compileOnly，独立 API 坐标不变 |
| AE2WT 完整模组 19.5.1 | `maven.modrinth:applied-energistics-2-wireless-terminals:CxSEpEnO` | 仅 `compatWT=true` 时 runtimeOnly |
| GuideME 21.1.1 | `maven.modrinth:guideme:PALpfSdf` | 基础兼容运行前置，无本地文件回退 |
| Curios 9.5.1+1.21.1 | `maven.modrinth:curios:yohfFbgD` | 基础兼容运行前置，无本地文件回退 |
| Jade 15.9.1+neoforge | `maven.modrinth:jade:15.9.1+neoforge` | compileOnly，沿用原坐标 |
| AdvancedAE 1.6.12 | `maven.modrinth:advancedae:ablTMAjP` | compileOnly；按 compatAAE 开关运行 |
| AE2LT 2.1.2-beta3 | `maven.modrinth:ae2-lightning-tech:AosdXlfS` | compileOnly；按 compatAE2LT 开关运行 |

新增坐标均固定发布版/文件 ID，未使用 latest 或动态版本。新替换的可选编译依赖关闭传递依赖；可选模组不会因本次修改成为生产必装前置，也不会被打入生产 Jar。

JEI、CurseMaven、Modrinth、AE2WT API 的坐标组使用 `exclusiveContent` 指向各自的发布仓库，避免被全局镜像或 NeoForge 仓库拦截。Gradle JVM 跟随系统代理，项目不固定代理地址；开启系统代理时走代理，关闭时直连。首次缺失依赖仍需要对应仓库可访问，缓存完整时可离线构建。

AE2、JEI 和 TC 版本分别读取现有 `ae2_version`、`jei_version`、`thaumcraft_version` 属性，当前数值保持原样。AE2 的 API 和完整构件都保留，不能因精简依赖误删内部类的编译支持。


- Minecraft 1.21.1 / NeoForge 21.1.234 / Java 21。
- AE2 Maven 坐标 `org.appliedenergistics:appliedenergistics2:19.2.17`，同时使用 API 与完整 JAR 编译内部接入。
- AE2 完整 JAR SHA-1：`49c18d6a4af487957d7e5a6ad5dcbf71090b8e14`；sources JAR SHA-1：`260a230e9fa1b4885489f7f58b30c19226093a52`。
- AE2WT API 编译坐标 `de.mari_023:ae2wtlib_api:19.5.1`，`transitive=false`。仅完整 `ae2wtlib` 存在时加载接入类。
- 除本地 TC 外的直接模组依赖全部使用远程仓库坐标；生产依赖保持 compileOnly，开发运行前置只在兼容开关启用时添加。

| 保留的本地参考文件 | modid | 用途及发布对应关系 | SHA-1 |
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
- 启用任一兼容开关自动补齐 AE2、TC、GuideME 21.1.1（PALpfSdf）、Curios 9.5.1+1.21.1（yohfFbgD）。GuideME、Curios、AE2WT 不再读取 `build/smoke-deps` 或 `library` 的同名文件；下载后由 Gradle 自身缓存复用。首次构建需要联网，缓存齐全后可使用 `--offline`。
- 仅第三方附属模组自带旧 AE2WT API 时，不启用我方 ItemWT 实现，也不因 API 版本阻止独立终端启动；完整 AE2WT 自身会要求其匹配的 API。
- AE2LT 元数据要求 AE2 `[19.2.17,19.3)`，不要在这套组合中升级到 19.3+。

## 2026-10-28 坐标核对依据

- JEI、IPN、EAP、AE2WT 通过本地 Jar SHA-1 反查 Modrinth 发布版，平台、版本和文件指纹均吻合；JEI实际使用作者 Maven 的同版本完整构件。
- AE2 JEI Integration 未通过 Modrinth 文件指纹检索找到对应发布，改用 CurseForge 项目1074338、文件7727898；实际从 CurseMaven 下载后确认 SHA-1 与本地1.2.1完全一致。
- GuideME PALpfSdf 与 Curios yohfFbgD 通过官方版本 API 确认；保持原联调版本。
- 公开来源：[JEI](https://modrinth.com/mod/jei/version/bEGnP8IF)、[AE2 JEI Integration](https://www.curseforge.com/minecraft/mc-mods/ae2-jei-integration/files/7727898)、[IPN](https://modrinth.com/mod/inventory-profiles-next/version/vjuNnHLv)、[EAP](https://modrinth.com/mod/extendedae-plus/version/DLFDZIVx)、[AE2WT](https://modrinth.com/mod/applied-energistics-2-wireless-terminals/version/CxSEpEnO)。
- 本机网络代理如需用于 Gradle，应通过本次命令或个人 Gradle 配置传入，不能把127.0.0.1等机器专属设置提交进工程。

## 2026-10-28 验证结果

- 默认联网 `assemble -x test` 通过；移除 ModDevGradle 后续加入的 Maven Local 后，四组兼容开关同时开启的 assemble 也通过。
- 使用仅位于 build 目录的临时校验入口，强制解析并获取 compileClasspath 的93个外部构件及 runtimeClasspath 的117个外部构件文件；不是仅查看依赖树，也未启动游戏。
- JEI、AE2 JEI Integration、IPN、EAP、GuideME、Curios、AE2WT共七个替换构件均通过 SHA-1 核对；其中原本地 Jar/开发缓存存在的文件也与新仓库下载指纹一致。
- 核对时该模块最终仓库列表不再包含 Maven Local；ModDevGradle 自身生成的 Minecraft 构件仓库保留。
- 下载齐全后，以相同四组开关执行 assemble 和依赖文件校验的 `--offline` 复核通过。
- 生产包没有内嵌第三方 Jar 或 AE2/JEI/TC/AE2WT 实现类。未运行 JUnit、GameTest、客户端、服务端或玩家存档，不将依赖解析通过等同于游戏联调验收通过。

## 公开源码入口验证（2026-10-28）

- 从受 Git 管理的根构建文件、原 neo 源码及本次新增文件建立副本；副本不含 `.git`、原工程 `.gradle` 或构建产物，`library/` 中只手工放入 port.261 TC Jar。
- Wrapper 在独立空 Gradle 用户目录中自动下载官方 Gradle 8.12.1，并通过发行包 SHA-256 校验。之后首次获取 NeoForge 构建工具时遇到当前网络的 TLS 握手失败，完整空缓存构建未通过，不能将其记为已验收。
- 改用已下载的公开依赖缓存，在上述源码副本执行 `:thaumicenergistics:assemble -PneoOnly=true --offline --no-build-cache --rerun-tasks`，实际重新编译和打包通过，没有复用项目 Java 编译任务缓存。
- 配置结果仅含根工程和 `:thaumicenergistics`，模块 Maven Local 仓库列表为空；无 Git 时版本回退为 `2.6.10-alpha`，与生产 Jar 内模组元数据一致。
- 缺 TC 文件时的单独前置校验按预期失败，并提示完整文件名和放置目录。未运行游戏或测试任务。

## 独立单模组导出验证（2026-10-28）

- 使用更新后的导出脚本，以 `--working-tree` 将 Git 跟踪文件的当前修改导出到全新审查目录；目录内没有 `versioning.gradle`，本地依赖仅 TC port.261。
- 以 `assemble --offline --no-build-cache --rerun-tasks` 实际重编译、打包通过，使用已下载的公开依赖缓存；未重新进行全空缓存联网验证。
- 独立工程读取 `mod_version=2.6.10-alpha`，Jar 内版本一致；工作区仍按 Git 计算为 `2.6.11-alpha`。两者版本来源有意分开，独立发布时维护 `mod_version`。
- 将独立源码复制到另一名称的目录，并初始化其自己的 Git 仓库，确认命令行版本覆盖仍生效；独立项目不会读取父工作区的 Git 历史。
- 再次导出无源文件变更，Wrapper 时间戳、目标 Git 元数据及构建产物均保留。导出的 Wrapper 可直接启动 Gradle 8.12.1；脚本本身不再调用系统 Gradle。未运行游戏或测试任务。

## 系统代理与直连验证（2026-10-28）

- 撤销本机固定代理主机/端口配置，根 `org.gradle.jvmargs` 增加 `-Djava.net.useSystemProxies=true`；导出脚本会照常将根共享属性并入独立工程。
- 在新 Gradle 进程中确认无显式 `http.proxyHost`/`https.proxyHost`，系统代理模式选择本机现有系统代理；另在独立检查进程中将 ProxySelector 临时设为 DIRECT，模拟系统代理关闭，未改动系统代理开关。
- 两条路径都通过 Gradle 的 HTTP 客户端下载加入随机查询参数的 Maven POM，并执行 `:thaumicenergistics:assemble` 成功。生产构建使用现有依赖/编译缓存，不是全空缓存验证。
- 直连复核发现 JEI 曾被送入本机全局注入的 NeoForge 根地址及插件提供的 releases 仓库，连接异常地址后超时。清理本机错误根地址，并以独占坐标组隔离专用发布仓库后，直连构建通过。
- 全空缓存首次获取 NeoForge 自身构件的可达性仍未验收；不能用上述结果宣称任何网络环境都能完成首次依赖下载。

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
