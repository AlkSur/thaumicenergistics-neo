# neo 兼容及知识记录仪核查（2026-11-08）

仅修改 `thaumicenergistics-neo`，保留命名空间 `thaumicenergistics` 及 TC port.261 接口。参考 neoRen 11 月 7 日修复，但不升级或引入 TC4R 20764。

## 逐项结论

| 项目 | neo 实际证据与处理 |
| --- | --- |
| 1. 接口代理库存递归 | `MixinInterfaceLogic` 在 getter 中 `new InterfaceEssentiaInventory(storage)`，构造器遍历 `original.getStack`，存在同类递归和代理快照破坏风险。改为标记实际对象，取消复制及 final 字段替换。 |
| 2. AE2LT 内部目标注入 | 本地发布 Jar `ae2lt-2.1.2-beta3.jar` 的 `ProviderTarget.resolveStorageTarget` 字节码确认调用 `PatternProviderTarget.get`。改用该 AE2 共用出口，删除 AE2LT 内部类 Mixin、配置、插件分支及实现编译依赖。保留现有可选运行开关，不改变所锁定版本。 |
| 3. 失效知识记录删除 | 原实现只载入可解码记录，保存会重新编码有效列表；删除依赖当前配方、研究及产物匹配。改为原始条目编辑列表与解码执行列表分离，按原始索引及完整数据核对删除，不按同名产物批量删除。 |
| 4. JEI 包装类变化 | 不适用。当前 TC Jar 仍公开 `JeiArcaneRecipe(id, inputs, output, aspects, research, shapeless)`，neo 直接调用，没有 Ren 所述的反射缓存问题。继续使用当前分类及九格 inputs，不迁入 TC4R 的类型。另修正记录仪转移跳过空格留下旧材料的问题。 |
| 5. 小于 3×3 的奥术配方 | 不适用 Ren 的裁剪方式。neo 的 `IArcaneRecipe` 接受九格 `Container`，`ShapedArcaneRecipe.matches` 原生遍历偏移；成本、产物和返还物沿用九格。单独修正普通合成预览为 `CraftingInput.ofPositioned(...).input()`；实际普通合成及返还物仍委托 AE2 原生路径。 |
| 6. TC4R 源质存储接口升级 | 不适用，不增加 `EssentiaStorageView` 或新版本能力。neo 当前 API 有 `IAspectContainer`、`IEssentiaTransport` 及模拟抽取。确认供应器阻挡曾调用要求输出权限的列表，改为只读本地库存观察；总线仍通过指定面连接、输出权限及模拟抽取确定可用数量。 |

## 数据与功能边界

- 标记库存仅对源质容量应用每格 256。非接口库存、物品及流体仍用原生规则。后续 `setCapacity/useRegisteredCapacities` 不会改变被标记库存查询到的源质容量。
- 保留原库存身份、子类代理、过滤器、回调、菜单及原生插入/抽取路径。虚拟配置目标可归一化到 256，真实旧超额库存不截断；部分抽取余量、原生 NBT 读取及菜单快照保留超额源质。其他资源的快照写入仍通过原生 setter。
- 破坏时的主动回网只处理明确的 AE2 具体本地 `ConfigInventory`；不将未知子类代理当作本地拥有的库存回网。附属自身的代理及掉落逻辑保留。
- 共用目标从 `IActionHost` 的节点 `getOwner()` 找升级宿主，不反射 AE2LT。缓存的新增源质目标在交付时重新检查卡片、节点身份和激活状态。原物品/流体目标、功耗、预检及派发余量队列不移植或重建。
- 知识核心原始 `Patterns` 条目及未知字段逐条原样保留；无法解码的条目显示可选占位图标，不进入装配器的解码配方列表。未知根格式拒绝编辑，避免覆盖。
- 点已存条目后点删除不要求当前配方或研究有效；新增仍检查研究。服务端删除前核对索引、原始条目和当前完整列表，防止选中后数据变化误删。改材料、JEI 转移、清空和换核心都会取消选择；选中条目不额外发送九个编辑包。
- 罐子仍将任意实际访问面归一到顶部。其他设备保留真实面的传输权限；阻挡观察优先 `IAspectContainer.getAspects()` 枚举本地多要素，只在缺少该视图时使用现有旧炉缓存或传输视图。同一库存的多个面不累加重复数量。抽取不因可观察而放行。

## 验证与限制

只执行 `:thaumicenergistics:assemble -PneoOnly=true --offline` 和静态核查，不运行游戏、JUnit 或 GameTest。检查目标方法/字段来自当前依赖，JSON、Mixin 清单及最终 Jar 包含新增类且不包含删除的内部目标类。

AE2 仍锁定 19.2.17，TC 仍为 `thaumcraft-0.2.2.95-port.261.jar`。已直接核对的是本地 AE2LT 2.1.2-beta3；2.1.4-beta3 的调用路径来自用户提供的 Ren 修复记录，没有将其标为本模块已发布二进制验证或玩家实测。实际版本组合、旧库存及旧核心需要玩家存档副本验收。

未夹带 `RecipeRegistration.java` 中已有的高级外壳材料并行修改，未修改 neoRen。
