# 神秘能源源码入口 / Thaumic Energistics Neo source

- 上游 / Upstream: https://github.com/AlkSur/thaumicenergistics-neo
- Branch: main
- Commit: `94b1a43d0cf80e4044a3f9048e2abea184da02ef`
- Tree: `7ceeccc07341a097e63e2d2710ca05c156c2cbe9`
- 上游声明版本 / Declared source version: `2.6.10-alpha`
- 同步日期 / Imported: 2026-11-09

上游全部受 Git 管理的源码、资源、wrapper、构建脚本与许可证原样保留。本目录额外加入本交接文档和 `SCEX-SOURCE.json`，没有修改业务代码。

All upstream tracked files are imported unchanged. Only this handoff and the provenance JSON are added. The merge retains upstream commit ancestry.

## 构建 / Build

在本目录使用 Java 21，按上游 README 将未公开发布的 `thaumcraft-0.2.2.95-port.261.jar` 放入 `library/`，然后运行 `gradlew.bat assemble`（Windows）或 `bash ./gradlew assemble`（Linux/macOS）。本次没有替换构建依赖，没有运行构建或游戏测试。

Use Java 21 and follow upstream README. Supply its specified Thaumcraft JAR in `library/`, then run the local Gradle wrapper. This source import was not built or runtime-tested.

## 生产边界 / Deployment boundary

现服继续使用 `2.6.16-alpha`。上游声明版本与现服不同，不据此声称源码就是该发布 JAR，也不自动更新服务器。后续构建与兼容性核验应单独进行。

Production stays on `2.6.16-alpha`. The declared source version differs; equivalence to the deployed binary is unverified. Importing this source does not deploy a server update.

保留 LGPL/MIT 代码声明及麦淇淋材质 CC BY-NC-SA 3.0 许可，具体范围见上游 LICENSE、LICENSE-ASSETS.md 和完整许可文本。
