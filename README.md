# Thaumic Energistics Neo

Minecraft 1.21.1 / NeoForge 的神秘能源附属模组。

## 单模组源码构建

本仓库只有这一个模组时（根目录直接包含 `build.gradle` 和 `src/`），需要 JDK 21 和网络连接。将未公开发布的 TC 移植版 `thaumcraft-0.2.2.95-port.261.jar` 放入根目录的 `library/`，其余依赖由 Gradle 自动下载。

Windows PowerShell：

```powershell
.\gradlew.bat assemble
```

Linux / macOS：

```sh
bash ./gradlew assemble
```

无需手工安装 Gradle，也无需父目录、工作区的 `versioning.gradle` 或 Git 历史。成品位于 `build/libs/`；不带 `-sources` 后缀的 Jar 用于游戏。以上命令只编译打包，不执行测试或启动游戏。

模组元数据模板位于 `src/main/templates/META-INF/neoforge.mods.toml`，构建和 IDEA 重新导入时自动展开版本等变量。迁移、提交或导出源码时，需要同时包含该模板和 `build.gradle`；生成的 `build/generated/resources/modMetadata/` 不必提交。

独立项目使用 `gradle.properties` 的 `mod_version` 作为发布版本；初始化独立 Git 仓库或更改文件夹名称不会改变版本。发布新版时维护该属性，亦可通过 `-Pmod_version=你的版本号` 覆盖。工作区内构建继续采用根目录脚本的 Git 标签及提交计数规则。

## 在多模块工作区中构建

如果此文件位于 `modules/thaumicenergistics-neo/`，请将 TC Jar 放入本模块的 `library/`，然后在整个工作区根目录执行：

```powershell
.\gradlew.bat :thaumicenergistics:assemble -PneoOnly=true
```

构建依赖及可选兼容版本见 [DEPENDENCIES.md](DEPENDENCIES.md)。AE2 仍固定为 19.2.17。
