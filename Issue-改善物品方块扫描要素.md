# [Feature Request] 改善物品/方块扫描要素的自动赋予机制（恢复配方溯源 + 性能优化）

## 概述 Summary

请求为 TC4 1.21.1 移植版恢复 1.7.10 的**合成配方溯源**机制（物品作为配方产出时，按原料要素自动赋予要素），并修复 `getObjectTags` 无索引穷举遍历导致的严重性能问题。

当前移植版 `ItemAspectRegistry.getObjectTags` 只保留：注册表 → NBT → 精确表 → ID 词根推算 → `ENTROPY` 兜底。**词根表仅覆盖约 15 组 ID**，对 mod 物品几乎不命中，大量物品退化为 `ENTROPY×1` 或词根给的 1-2 种要素，且无法反映合成复杂度。

## 问题描述 Problem

### 功能缺失（影响整个 mod 生态）

1. **要素不准确**：mod 物品（如 `thaumicenergistics:essentia_provider`、`waystones:waystone`）扫描要素靠 ID 词根猜——词根对不上就 `ENTROPY×1`，对得上也只有 1-3 种
2. **无合成溯源**：合成材料完全不影响要素——1 个原料的和 10 个原料的合成品要素一样，无法体现合成复杂度

### 性能严重（实测）

- `getObjectTags` 每次调用**穷举遍历全部配方**（1932+ 个 CRAFTING）比较产出——无索引、无缓存
- 客户端 JEI 启动遍历全部物品时触发全量扫描：**实测单次扫描耗时 10 秒 / 1127 tick**（F3+L profiler）
- 频繁调用（Thaumonomicon/JEI 显示）导致持续卡顿

## 期望行为 Expected Behavior

- mod 物品扫描要素**按合成材料赋予**（与 1.7.10 一致）
- 要素数量/种类**合理**（种类 2-6、总量 8-24、单要素 ≤64）
- 扫描/显示**无卡顿**（单次查询毫秒级）

## 建议实现 Suggested Implementation

### A. 功能：getObjectTags 增加配方溯源分支

```
在词根推算 / ENTROPY 兜底之前：
  recipe = 查产出=stack 的配方
  if recipe != null:
    原料要素 = 递归 getObjectTags(原料)（深度 ≤2，visited 防环）
    返回 原料要素之和（同种要素取最大，避免累加爆炸）
```

可选覆盖配方类型（按需）：普通合成（Crafting）、熔炉（Smelting）、注魔（Infusion）、炼金（Crucible）、奥术（Arcane）。

### B. 性能：产出→配方索引

```
懒构建 Map<Item, List<Recipe>>（按产出分组，首次查询时建一次）
getObjectTags 内 O(1) 查索引，不再每次穷举遍历全部配方
结果缓存 ConcurrentHashMap<Item, AspectList>，绑定 Server 引用（/reload 自动失效）
```

### C. 要素限制（贴近 1.7.10 + 平衡）

- 单要素 ≤64
- 种类 ≤6（按数量降序取前 6）
- 总量按复杂度归一化：`clamp(8 + 原料种类×2, 8, 24)`
- 同种要素多个原料出现时**取最大值**（不累加，防 1000+）

## 复现步骤 Steps to Reproduce

1. 安装任意含合成配方的 mod（如 waystones 传送石碑：3 黑曜石 + 3 石砖 + 1 传送石）
2. 用魔导透镜扫描该 mod 物品
3. 实际：`ENTROPY×1` 或词根 1-3 种（传送石碑给 1 混沌）
4. 期望：黑曜石（暗/虚空）+ 石砖（石头）+ 传送石（水晶/末影）要素

## 性能复现

1. 客户端启动（JEI 加载，遍历全部物品调 getObjectTags）
2. F3+L 采样 10 秒
3. 实际：`getObjectTags` 占比极高（穷举遍历配方），单物品 10s 级

## 参考实现要点（作者可参考）

- 1.21.1 `RecipeManager.byType` 为 **private**——用 `getAllRecipesFor(RecipeType)`（公开，返回 `List<RecipeHolder<T>>`）
- `RecipeHolder.id()` 直接返回 `ResourceLocation`（无 `.location()`）
- TC 配方访问：`ThaumcraftApi.getInfusionRecipe(result)` / `getCrucibleRecipe(result)` / `getCraftingRecipes()`（遍历 `IArcaneRecipe`，含 `getAspects()` vis 成本）
- `InfusionRecipe` 原料：`centralInput()` + `getComponents()`（公开）
- 缓存注意：组件敏感物品（药水/染色/耐久 NBT 变体）不进缓存（TC 的 NBT 分支先截走）；COMPUTING 哨兵防跨配方递归环；缓存绑定 Server 引用（reload 失效）

## 环境 Environment

- Minecraft 1.21.1
- NeoForge 21.1.x
- Thaumcraft

## 补充

- 完整反编译调研与参考实现已由 ThaumicEnergistics 移植团队完成，可提供更详细方案（含存储体系：内存缓存 + 可选本地二进制文件缓存，指纹校验跨启动复用）
