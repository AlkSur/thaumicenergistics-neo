package thaumicenergistics.common.integration.tc;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.common.research.TCResearchIndex;
import thaumicenergistics.ThaumicEnergistics;

/**
 * 伪前置桩节点。在 ThE 的研究 tab 中创建一个占位节点，
 * 其图标、名称、页面全部指向 TC 原版研究，使得 ThE 节点可以跨分类连线。
 *
 * 关键实现细节：
 * - key 仍使用 PSEUDO_ 前缀（如 PSEUDO_VISPOWER），避免与原版研究在 TCResearchManager 的 CompoundTag 中冲突。
 * - 图标（icon_item/icon_resource）直接使用原版研究的图标，在 registerApiResearch 时会被正确拷贝到 TCResearchIndex.Entry。
 * - 页面（pages）直接使用原版研究的页面，使点击伪前置时能展示原版研究的内容。
 * - 名称翻译通过 ThE 的 lang 文件中添加 tc.research_name.PSEUDO_* 条目指向原版翻译 key 来解决。
 * - 解锁同步通过 ResearchCompletedEvent 事件监听实现，见 ThEThaumcraft.registerPseudoSync()。
 */
public class PseudoResearchItem extends ResearchItem {

    /** 原版真实研究的 key，如 "DISTILESSENTIA" */
    private final String realKey;

    /** 原版真实研究所在分类，如 "ALCHEMY" */
    private final String realCategory;

    /** 指向的原版研究（用于 getName/getText/getPages 委托）。 */
    private ResearchItem realResearch;

    private PseudoResearchItem(String pseudoKey, String category, int column, int row,
                               ItemStack icon, String realKey, String realCategory) {
        super(pseudoKey, category, new AspectList(), column, row, 1, icon);
        this.realKey = realKey;
        this.realCategory = realCategory;
        // concealed: 当 parentsComplete 或 completed 时可见
        this.setStub().setConcealed();
    }

    private PseudoResearchItem(String pseudoKey, String category, int column, int row,
                               ResourceLocation icon, String realKey, String realCategory) {
        super(pseudoKey, category, new AspectList(), column, row, 1, icon);
        this.realKey = realKey;
        this.realCategory = realCategory;
        this.setStub().setConcealed();
    }

    /**
     * 创建一个指向 TC 原版研究的伪前置。
     *
     * @param pseudoKey      伪前置在 ThE tab 中的 key（如 "PSEUDO_DISTILESSENTIA"）
     * @param category       ThE 研究分类（即 TAB）
     * @param realKey        TC 原版研究的 key（如 "DISTILESSENTIA"）
     * @param realCategory   TC 原版研究所在分类（如 "ALCHEMY"）
     * @param column         伪前置的 X 坐标
     * @param row            伪前置的 Y 坐标
     */
    public static PseudoResearchItem newPseudo(String pseudoKey, String category,
                                                String realKey, String realCategory,
                                                int column, int row) {
        var realCat = ResearchCategories.researchCategories.get(realCategory);
        if (realCat == null) {
            throw new IllegalArgumentException("TC category not found: " + realCategory);
        }
        ResearchItem realResearch = realCat.research.get(realKey);
        if (realResearch == null) {
            throw new IllegalArgumentException("TC research not found: " + realKey + " in " + realCategory);
        }

        PseudoResearchItem pseudo;
        // 图标第一优先：真实研究对应的物品（research_index.json 的 item 字段，TC tab 中即用此图标）
        ItemStack realIcon = resolveRealIconItem(realKey);
        if (!realIcon.isEmpty()) {
            pseudo = new PseudoResearchItem(pseudoKey, category, column, row, realIcon, realKey, realCategory);
        } else if (realResearch.icon_item != null && !realResearch.icon_item.isEmpty()) {
            pseudo = new PseudoResearchItem(pseudoKey, category, column, row, realResearch.icon_item, realKey, realCategory);
        } else if (realResearch.icon_resource != null) {
            pseudo = new PseudoResearchItem(pseudoKey, category, column, row, realResearch.icon_resource, realKey, realCategory);
        } else if (realCat.icon != null) {
            // 备选：真实研究所在分类的图标
            pseudo = new PseudoResearchItem(pseudoKey, category, column, row, realCat.icon, realKey, realCategory);
        } else {
            // 最终备选：ThE 研究 tab 图标
            pseudo = new PseudoResearchItem(pseudoKey, category, column, row,
                    ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID, "textures/research/tab_icon.png"),
                    realKey, realCategory);
        }

        // 保存真实研究引用：内容（名称/文字/页面）通过方法委托到真实研究（TC port 渲染调 getPages()）
        pseudo.realResearch = realResearch;

        ThaumicEnergistics.LOG.debug("[Pseudo] {} ← {}:{} icon={}",
                pseudoKey, realCategory, realKey,
                pseudo.icon_item != null && !pseudo.icon_item.isEmpty()
                        ? pseudo.icon_item.getDescriptionId() : String.valueOf(pseudo.icon_resource));

        return pseudo;
    }

    /**
     * 从 TC 的权威研究索引（research_index.json）解析真实研究对应的物品图标。
     */
    private static ItemStack resolveRealIconItem(String realKey) {
        try {
            return TCResearchIndex.entry(realKey)
                    .map(entry -> {
                        String itemId = entry.item();
                        if (itemId == null || itemId.isEmpty()) return ItemStack.EMPTY;
                        ResourceLocation id = ResourceLocation.tryParse(itemId);
                        if (id == null) return ItemStack.EMPTY;
                        Item item = BuiltInRegistries.ITEM.get(id);
                        return (item == null || item == net.minecraft.world.item.Items.AIR) ? ItemStack.EMPTY : new ItemStack(item);
                    })
                    .orElse(ItemStack.EMPTY);
        } catch (Exception e) {
            return ItemStack.EMPTY;
        }
    }

    public String realKey() { return realKey; }

    public String realCategory() { return realCategory; }

    // ===== 内容委托：TC port 渲染研究时调用 getName/getText/getPages，全部转发到真实研究 =====

    @Override
    public String getName() {
        return realResearch != null ? realResearch.getName() : super.getName();
    }

    @Override
    public String getText() {
        return realResearch != null ? realResearch.getText() : super.getText();
    }

    @Override
    public ResearchPage[] getPages() {
        // 优先：原版研究 Java 页面（API 注册的研究有）
        if (realResearch != null && realResearch.getPages() != null && realResearch.getPages().length > 0) {
            return realResearch.getPages();
        }
        // 兜底：原版研究页面在 research_index.json 的 pages 字段（页面 key，如 tc.research_page.DISTILESSENTIA.1）
        // registerApiResearch 会从 ResearchPage.text 提取这些 key 作为 Entry.pages，渲染时据此显示原版内容
        return TCResearchIndex.entry(realKey)
                .map(entry -> entry.pages().stream()
                        .filter(p -> p != null && !p.isBlank())
                        .map(ResearchPage::new)
                        .toArray(ResearchPage[]::new))
                .orElse(new ResearchPage[0]);
    }
}
