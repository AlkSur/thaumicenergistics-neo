package thaumicenergistics.common.integration.tc;

import java.util.HashMap;
import java.util.Map;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.events.ResearchCompletedEvent;
import thaumcraft.api.research.ResearchCategories;
import thaumcraft.api.research.ResearchItem;
import thaumcraft.api.research.ResearchPage;
import thaumcraft.api.crafting.IArcaneRecipe;
import thaumcraft.common.research.TCResearchManager;
import net.neoforged.neoforge.common.NeoForge;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.common.features.RecipeRegistration;
import thaumicenergistics.init.ModItems;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import appeng.core.definitions.AEItems;

/**
 * 神秘能源 魔导手册研究注册
 * 严格按照 1.7.10 ResearchRegistry 布局移植
 *
 * 1.7.10 伪前置（TC原版）映射：
 *   DISTILESSENTIA  = 要素蒸馏
 *   TUBEFILTER      = 高级源质管道
 *   MIRROR          = 魔镜
 *   INFUSION        = 注魔
 *   WARDEDARCANA    = 守护之奥术
 *   ALCHEMICALDUPLICATION = 炼金复制
 *   FOCUSFIRE       = 法杖核心
 *   VISPOWER        = 掌控魔力
 *   SCEPTRE         = 工艺权杖
 *   COREUSE         = 傀儡核心：使用
 *   COREGATHER      = 傀儡核心：聚集
 */
public final class ThEThaumcraft {
    private static final Logger LOG = LoggerFactory.getLogger("ThE-TC");
    static final String TAB = "THAUMICENERGISTICS";
    static final String MODID = ThaumicEnergistics.MODID;

    /**
     * 原版研究 key → 伪前置 key 的映射。
     * 用于 ResearchCompletedEvent 事件监听：当原版研究解锁时，自动 complete 对应伪前置。
     */
    private static final Map<String, String> REAL_TO_PSEUDO = new HashMap<>();

    private ThEThaumcraft() {}

    public static void init() {
        LOG.info("Registering ThaumicEnergistics research...");

        ResearchCategories.registerCategory(TAB,
                ResourceLocation.fromNamespaceAndPath(MODID, "textures/research/tab_icon.png"),
                ResourceLocation.fromNamespaceAndPath(MODID, "textures/research/research_background.png"));

        // ⚠ 必须先在当前 tab 注册伪前置节点（替代 TC 原版研究），
        //   否则 setParents 跨分类引用时线画不出来。
        registerPseudoParents();

        // 注册事件监听：当 TC 原版研究解锁时，自动 complete 对应伪前置节点
        registerPseudoSync();

        // 注册顺序沿袭 1.7.10 移植（决定研究 tab 内节点坐标布局），勿随意调整。
        registerBaseEnergistics();
        registerArcaneAssembler();
        registerKnowledgeInscriber();
        registerEssentiaStorage();
        registerArcaneCraftingTerminal();
        registerVisInterface();
        registerInfusionProvider();
        registerInfusionMonitor();
        registerEssentiaProvider();
        registerDigisentia();
        registerFocusAEWrench();
        registerCertusDupe();
        registerEssentiaVibrationChamber();
        registerDistillationEncoder();
        registerGolemWifiBackpack();

        // T-RESEARCH-B2：M3~M6 新内容入树；T-RESEARCH-B4：删除重挂（WIRELESSESSENTIATERMINAL/VISCONNECTORCARD 节点已删）
        registerWirelessArcaneCraftingTerminal();
        registerEssentiaAccessCard();
        registerGachaBox();

        LOG.info("ThaumicEnergistics research registered.");
    }

    // ==================== 伪前置节点（跨分类引用 TC 原版研究） ====================
    // 在 ThE 研究 tab 中创建占位节点，使跨分类连线正常。
    // 坐标来自 1.7.10 ResearchRegistry.PseudoResearchTypes
    //SCEPTRE(工艺权杖)、DISTILESSENTIA(要素蒸馏) 、WARDEDARCANA(守护之奥术)、VISPOWER(掌控魔力)、
    // TUBEFILTER(高级源质管道)、INFUSION(注魔) 、MIRROR(魔镜)、COREUSE(傀儡核心：使用)
    private static void registerPseudoParents() {
        // ALCHEMY
        registerPseudo("DISTILESSENTIA", "ALCHEMY", -2, 0);
        registerPseudo("TUBEFILTER", "ALCHEMY", -3, 0);
        registerPseudo("ALCHEMICALDUPLICATION", "ALCHEMY", -5, -6);
        // ARTIFICE
        registerPseudo("MIRROR", "ARTIFICE", -4, 0);
        registerPseudo("INFUSION", "ARTIFICE", -6, 0);
        registerPseudo("WARDEDARCANA", "ARTIFICE", 1, 2);
        // THAUMATURGY
        registerPseudo("FOCUSFIRE", "THAUMATURGY", -4, -7);
        registerPseudo("VISPOWER", "THAUMATURGY", 4, 0);
        registerPseudo("SCEPTRE", "THAUMATURGY", 5, 0);
        // GOLEMANCY
        registerPseudo("COREGATHER", "GOLEMANCY", 1, -6);
    }

    private static void registerPseudo(String realKey, String realCategory, int col, int row) {
        String pseudoKey = "PSEUDO_" + realKey;
        try {
            PseudoResearchItem.newPseudo(pseudoKey, TAB, realKey, realCategory, col, row)
                    .registerResearchItem();
            // 记录映射，供事件监听使用
            REAL_TO_PSEUDO.put(realKey, pseudoKey);
        } catch (Exception e) {
            LOG.warn("伪前置注册失败: PSEUDO_{} (TC:{}/{})", realKey, realCategory, realKey);
        }
    }

    /**
     * 注册事件监听：当 TC 原版研究被 complete 时，自动 complete 对应的伪前置节点。
     * 替代 1.7.10 的 sibling 机制——在 NeoForge 版本中 sibling 机制失效，
     * 因为 {@code TCResearchIndex.Entry} 是不可变 record，
     * 修改 {@code ResearchItem.siblings} 不会更新到 {@code TCResearchIndex}。
     */
    private static void registerPseudoSync() {
        NeoForge.EVENT_BUS.addListener(ResearchCompletedEvent.class, (ResearchCompletedEvent rc) -> {
            String completedKey = rc.researchKey();
            // 跳过 clue 解锁（key 以 @ 开头）
            if (completedKey.startsWith("@")) return;
            // 跳过 PSEUDO_ 节点完成事件，防止 complete() 触发二次事件造成连锁
            if (completedKey.startsWith("PSEUDO_")) return;
            String pseudoKey = REAL_TO_PSEUDO.get(completedKey);
            if (pseudoKey == null) return;
            // 自动 complete 伪前置节点（不通知玩家）
            try {
                TCResearchManager.complete(rc.player(), pseudoKey, false);
            } catch (Exception ignored) {
            }
        });
    }

    // ==================== 1. (0,0) BASEENERGISTICS 基础能源学====================
    //无前置
    private static void registerBaseEnergistics() {
        new ResearchItem("BASEENERGISTICS", TAB,
                new AspectList(),
                0, 0,
                0,
                ResourceLocation.fromNamespaceAndPath(MODID, "textures/research/tab_icon.png"))
                .setRound().setAutoUnlock()
                .setPages(new ResearchPage("tc.research_text.BASEENERGISTICS"))
                .registerResearchItem();
    }

    // ==================== 2. (6,-1) ARCANE_ASSEMBLER  奥术装配器====================
    // 前置: VISINTERFACE（要素中继接口） + SCEPTRE(工艺权杖)
    private static void registerArcaneAssembler() {
        new ResearchItem("ARCANEASSEMBLER", TAB,
                new AspectList().add(Aspect.CRAFT, 10).add(Aspect.MAGIC, 10)
                        .add(Aspect.MECHANISM, 5).add(Aspect.ORDER, 5),
                6, -1, 3, new ItemStack(ModItems.ARCANE_ASSEMBLER_ITEM.get()))
                .setRound().setParents("VISINTERFACE", "PSEUDO_SCEPTRE")
                .setPages(
                        new ResearchPage("tc.research_text.ARCANEASSEMBLER.stage.1"),
                        new ResearchPage(RecipeRegistration.ARCANE_ASSEMBLER),
                        new ResearchPage("tc.research_text.ARCANEASSEMBLER.stage.2"))
                .registerResearchItem();
    }

    // ==================== 2. (6,0) KNOWLEDGE_INSCRIBER 知识记录仪====================
    // 前置: ARCANEASSEMBLER（奥术装配器）
    private static void registerKnowledgeInscriber() {
        new ResearchItem("KNOWLEDGEINSCRIBER", TAB,
                new AspectList().add(Aspect.MIND, 10).add(Aspect.MECHANISM, 5).add(Aspect.MAGIC, 5),
                6, 0, 3, new ItemStack(ModItems.KNOWLEDGE_INSCRIBER_ITEM.get()))
                .setRound().setParents("ARCANEASSEMBLER")
                .setPages(
                        new ResearchPage("tc.research_text.KNOWLEDGEINSCRIBER"),
                        new ResearchPage(RecipeRegistration.KNOWLEDGE_CORE_ITEM),
                        new ResearchPage(RecipeRegistration.KNOWLEDGE_INSCRIBER_BLOCK))
                .registerResearchItem();
    }

    // ==================== 3. (0,2) ESSENTIASTORAGE 源质存储====================
    // 前置: BASEENERGISTICS（基础能源学） + DISTILESSENTIA(要素蒸馏) + WARDEDARCANA(守护之奥术)
    private static void registerEssentiaStorage() {
        new ResearchItem("ESSENTIASTORAGE", TAB,
                new AspectList().add(Aspect.ORDER, 1).add(Aspect.MAGIC, 1),
                0, 2, 2, new ItemStack(ModItems.ESSENTIA_CELL_1K.get()))
                .setRound().setParents("BASEENERGISTICS", "PSEUDO_DISTILESSENTIA", "PSEUDO_WARDEDARCANA")
                .setPages(
                        new ResearchPage("tc.research_text.ESSENTIASTORAGE"),
                        new ResearchPage(RecipeRegistration.STORAGE_COMPONENT_1K),
                        new ResearchPage(new IArcaneRecipe[]{RecipeRegistration.STORAGE_COMPONENT_4K, RecipeRegistration.STORAGE_COMPONENT_16K, RecipeRegistration.STORAGE_COMPONENT_64K}))
                .registerResearchItem();
    }

    // ==================== 4. (2,-1) ARCANECRAFTINGTERMINAL 奥术合成终端====================
    // 前置: BASEENERGISTICS（基础能源学）
    private static void registerArcaneCraftingTerminal() {
        new ResearchItem("ARCANECRAFTINGTERMINAL", TAB,
                new AspectList().add(Aspect.CRAFT, 10).add(Aspect.MAGIC, 10).add(Aspect.MECHANISM, 5),
                2, -1, 2, new ItemStack(ModItems.ARCANE_CRAFTING_TERMINAL_ITEM.get()))
                .setRound().setParents("BASEENERGISTICS")
                .setPages(
                        new ResearchPage("tc.research_text.ARCANECRAFTINGTERMINAL.stage.1"),
                        new ResearchPage(RecipeRegistration.ARCANE_CRAFTING_TERMINAL),
                        new ResearchPage("tc.research_text.ARCANECRAFTINGTERMINAL.stage.2"))
                .registerResearchItem();
    }

    // ==================== 5. (4,-1) VISINTERFACE 要素中继接口====================
    // 前置: ARCANECRAFTINGTERMINAL（奥术合成终端） + VISPOWER(掌控魔力)
    private static void registerVisInterface() {
        new ResearchItem("VISINTERFACE", TAB,
                new AspectList().add(Aspect.AURA, 10).add(Aspect.MAGIC, 5).add(Aspect.MECHANISM, 5),
                4, -1, 3, new ItemStack(ModItems.VIS_INTERFACE.get()))
                .setRound().setParents("ARCANECRAFTINGTERMINAL", "PSEUDO_VISPOWER")
                .setPages(
                        new ResearchPage("tc.research_text.VISINTERFACE"),
                        new ResearchPage(RecipeRegistration.VIS_INTERFACE))
                .registerResearchItem();
    }

    // ==================== 6. 源质总线节点已删（T-RESEARCH-B4：ESSENTIAACCESSCARD 顶位继承） ====================

    // ==================== 7. (-5,-2) INFUSIONPROVIDER 注魔供应器====================
    // 前置: ESSENTIAACCESSCARD（源质访问卡，T-RESEARCH-B4 顶位继承原源质总线） + INFUSION(注魔) + MIRROR(魔镜)
    private static void registerInfusionProvider() {
        new ResearchItem("INFUSIONPROVIDER", TAB,
                new AspectList().add(Aspect.MECHANISM, 10).add(Aspect.MAGIC, 10)
                        .add(Aspect.ORDER, 8).add(Aspect.EXCHANGE, 5),
                -5, -2, 3, new ItemStack(ModItems.INFUSION_PROVIDER_ITEM.get()))
                .setRound().setParents("ESSENTIAACCESSCARD", "PSEUDO_INFUSION", "PSEUDO_MIRROR")
                .setPages(
                        new ResearchPage("tc.research_text.INFUSIONPROVIDER.stage.1"),
                        new ResearchPage(RecipeRegistration.INFUSION_PROVIDER),
                        new ResearchPage("tc.research_text.INFUSIONPROVIDER.stage.2"))
                .registerResearchItem();
    }

    // ==================== 8. (-7,-2) INFUSIONMONITOR 韵魔观测器====================
    // 前置: INFUSIONPROVIDER（注魔供应器）；位于其左侧。无需研究比较，直接花费要素解锁。
    private static void registerInfusionMonitor() {
        new ResearchItem("INFUSIONMONITOR", TAB,
                new AspectList().add(Aspect.MAGIC, 4).add(Aspect.AURA, 4).add(Aspect.SENSES, 6),
                -7, -2, 1, new ItemStack(ModItems.INFUSION_MONITOR_ITEM.get()))
                .setRound().setParents("INFUSIONPROVIDER")
                .setPages(
                        new ResearchPage("tc.research_text.INFUSIONMONITOR.stage.1"),
                        new ResearchPage(RecipeRegistration.INFUSION_MONITOR),
                        new ResearchPage("tc.research_text.INFUSIONMONITOR.stage.2"))
                .registerResearchItem();
    }

    // ==================== 8. (-2,-4) ESSENTIAPROVIDER 源质供应器====================
    // 前置: ESSENTIAACCESSCARD（源质访问卡，T-RESEARCH-B4 顶位继承原源质总线）
    private static void registerEssentiaProvider() {
        new ResearchItem("ESSENTIAPROVIDER", TAB,
                new AspectList().add(Aspect.MECHANISM, 8).add(Aspect.MAGIC, 8).add(Aspect.EXCHANGE, 5),
                -2, -4, 3, new ItemStack(ModItems.ESSENTIA_PROVIDER_ITEM.get()))
                .setRound().setParents("ESSENTIAACCESSCARD")
                .setPages(
                        // 源质供应器：介绍+用法 / 配方
                        new ResearchPage("tc.research_text.ESSENTIAPROVIDER.stage.1"),
                        new ResearchPage(RecipeRegistration.ESSENTIA_PROVIDER),
                        // 源质无线接收器：介绍+用法 / 配方
                        new ResearchPage("tc.research_text.ESSENTIAPROVIDER.connection.stage.1"),
                        new ResearchPage(RecipeRegistration.ESSENTIA_PROVIDER_CONNECTION),
                        // 源质无线绑定工具：介绍+用法 / 配方
                        new ResearchPage("tc.research_text.ESSENTIAPROVIDER.tool.stage.1"),
                        new ResearchPage(RecipeRegistration.WIRELESS_CONNECTOR))
                .registerResearchItem();
    }

    // ==================== 9. (-1,-4) ESSENTIATERMINAL 源质终端已删（T-M2-REMOVE） ====================
    // 节点无后继（全仓 grep 实证）；ESSENTIA_LEVEL_EMITTER 配方键与配方页已迁 ESSENTIAACCESSCARD（裁定 A）

    // ==================== 10. (0,-2) DIGISENTIA 数字化源质====================
    // 前置: BASEENERGISTICS（基础能源学） + DISTILESSENTIA(要素蒸馏)
    private static void registerDigisentia() {
        new ResearchItem("DIGISENTIA", TAB,
                new AspectList().add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5).add(Aspect.MAGIC, 3),
                0, -2, 1, new ItemStack(ModItems.DIFFUSION_CORE.get()))
                .setRound().setParents("BASEENERGISTICS", "PSEUDO_DISTILESSENTIA")
                .setPages(
                        new ResearchPage("tc.research_text.DIGISENTIA.stage.1"),
                        new ResearchPage(RecipeRegistration.DIFFUSION_CORE),
                        new ResearchPage(RecipeRegistration.COALESCENCE_CORE),
                        new ResearchPage("tc.research_text.DIGISENTIA.stage.2"))
                .registerResearchItem();
    }

    // ==================== 12. (-3,-7) FOCUS_AEWRENCH 法杖核心 AE扳手====================
    // 前置: BASEENERGISTICS（基础能源学） + FOCUSFIRE(法杖核心)
    private static void registerFocusAEWrench() {
        new ResearchItem("FOCUS_AEWRENCH", TAB,
                new AspectList().add(Aspect.TOOL, 5).add(Aspect.ENERGY, 5).add(Aspect.MECHANISM, 3),
                -3, -7, 3, new ItemStack(ModItems.FOCUS_AEWRENCH.get()))
                .setRound().setParents("PSEUDO_FOCUSFIRE").setParentsHidden("BASEENERGISTICS")
                .setPages(
                        new ResearchPage("tc.research_text.FOCUS_AEWRENCH"),
                        new ResearchPage(RecipeRegistration.FOCUS_AEWRENCH_RECIPE))
                .registerResearchItem();
    }

    // ==================== 13. (-5,-5) CERTUS_DUPE 赛特斯石英复制====================
    // 前置: ALCHEMICALDUPLICATION(炼金复制) + BASEENERGISTICS（基础能源学）
    private static void registerCertusDupe() {
        new ResearchItem("CERTUS_DUPE", TAB,
                new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.MAGIC, 5).add(Aspect.EXCHANGE, 5),
                -5, -5, 2, new ItemStack(AEItems.CERTUS_QUARTZ_CRYSTAL.asItem()))
                .setRound().setParents("PSEUDO_ALCHEMICALDUPLICATION").setParentsHidden("BASEENERGISTICS")
                .setPages(
                        new ResearchPage("tc.research_text.CERTUS_DUPE"),
                        new ResearchPage(RecipeRegistration.CERTUS_DUPE_RECIPE))
                .registerResearchItem();
    }

    // ==================== 14. (1,-4) VIBRATIONCHAMBER 源质谐振仓====================
    // 前置: DIGISENTIA
    private static void registerEssentiaVibrationChamber() {
        new ResearchItem("ESSENTIAVIBRATIONCHAMBER", TAB,
                new AspectList().add(Aspect.ENERGY, 8).add(Aspect.MECHANISM, 5).add(Aspect.FIRE, 3),
                1, -4, 3, new ItemStack(ModItems.ESSENTIA_VIBRATION_CHAMBER_ITEM.get()))
                .setRound().setParents("DIGISENTIA")
                .setPages(
                        new ResearchPage("tc.research_text.ESSENTIAVIBRATIONCHAMBER"),
                        new ResearchPage(RecipeRegistration.ESSENTIA_VIBRATION_CHAMBER))
                .registerResearchItem();
    }

    // ==================== 15. (-4,-4) DISTILLATIONENCODER 蒸馏编码台====================
    // 前置: ESSENTIAACCESSCARD（源质访问卡，T-RESEARCH-B4 顶位继承原源质总线）
    private static void registerDistillationEncoder() {
        new ResearchItem("DISTILLATIONENCODER", TAB,
                new AspectList().add(Aspect.MECHANISM, 5).add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5),
                -4, -4, 3, new ItemStack(ModItems.DISTILLATION_ENCODER_ITEM.get()))
                .setRound().setParents("ESSENTIAACCESSCARD")
                .setPages(
                        new ResearchPage("tc.research_text.DISTILLATIONENCODER"),
                        new ResearchPage(RecipeRegistration.DISTILLATION_ENCODER_BLOCK))
                .registerResearchItem();
    }

    // ==================== 16. (0,-6) GOLEMWIFIBACKPACK 无线傀儡背包====================
    // 前置: DIGISENTIA + COREGATHER(傀儡核心：聚集)
    private static void registerGolemWifiBackpack() {
        new ResearchItem("GOLEMWIFIBACKPACK", TAB,
                new AspectList().add(Aspect.MAGIC, 5).add(Aspect.MECHANISM, 5).add(Aspect.MIND, 3),
                0, -6, 3, new ItemStack(ModItems.GOLEM_WIFI_BACKPACK.get()))
                .setRound().setParents("PSEUDO_COREGATHER").setParentsHidden("DIGISENTIA")
                .setPages(
                        new ResearchPage("tc.research_text.GOLEMWIFI"),
                        new ResearchPage(RecipeRegistration.GOLEM_WIFI_BACKPACK_RECIPE))
                .registerResearchItem();
    }

    // ==================== T-RESEARCH-B2：M3~M6 新增节点（纯新增） ====================
    // T-RESEARCH-B4：WIRELESSESSENTIATERMINAL 节点已删；T-M2-REMOVE：无线源质终端本体与配方一并删除

    // ==================== 18. (3,-2) WIRELESSARCANECRAFTINGTERMINAL 无线奥术合成终端====================
    // 前置: ARCANECRAFTINGTERMINAL（奥术合成终端）
    private static void registerWirelessArcaneCraftingTerminal() {
        new ResearchItem("WIRELESSARCANECRAFTINGTERMINAL", TAB,
                new AspectList().add(Aspect.CRAFT, 8).add(Aspect.MAGIC, 8).add(Aspect.MECHANISM, 5),
                3, -2, 2, new ItemStack(ModItems.WIRELESS_ARCANE_CRAFTING_TERMINAL.get()))
                .setRound().setParents("ARCANECRAFTINGTERMINAL")
                .setPages(
                        new ResearchPage("tc.research_text.WIRELESSARCANECRAFTINGTERMINAL.stage.1"),
                        new ResearchPage(RecipeRegistration.WIRELESS_ARCANE_CRAFTING_TERMINAL),
                        new ResearchPage("tc.research_text.WIRELESSARCANECRAFTINGTERMINAL.stage.2"))
                .registerResearchItem();
    }

    // ==================== 21. (-2,-2) ESSENTIAACCESSCARD 源质访问卡====================
    // T-RESEARCH-B4：顶位继承原源质总线（用户拍板「继承它的关系包括前置、后继」）
    // T-M2-REMOVE：三总线删除后，ESSENTIA_LEVEL_EMITTER 配方注册键与配方页迁入本节点（裁定 A）。
    // 坐标 = 原 ESSENTIABUSES (-2,-2)；前置 = 原 ESSENTIABUSES 前置（DIGISENTIA + PSEUDO_TUBEFILTER），
    // 原 PSEUDO_INFUSION/PSEUDO_MIRROR 前置丢弃；原 ESSENTIABUSES 三后继（INFUSIONPROVIDER/
    // ESSENTIAPROVIDER/DISTILLATIONENCODER）与三总线配方键已改挂本节点。
    private static void registerEssentiaAccessCard() {
        new ResearchItem("ESSENTIAACCESSCARD", TAB,
                new AspectList().add(Aspect.MIND, 5).add(Aspect.EXCHANGE, 5).add(Aspect.MAGIC, 3),
                -2, -2, 2, new ItemStack(ModItems.ESSENTIA_UPGRADE_CARD.get()))
                .setRound().setParents("DIGISENTIA", "PSEUDO_TUBEFILTER")
                .setPages(
                        new ResearchPage("tc.research_text.ESSENTIAACCESSCARD"),
                        new ResearchPage(RecipeRegistration.ESSENTIA_UPGRADE_CARD),
                        new ResearchPage(RecipeRegistration.ESSENTIA_LEVEL_EMITTER))
                .registerResearchItem();
    }

    // ==================== 22. (-1,-4) GACHABOX 概率之脑====================
    // 前置: DIGISENTIA（数字化源质）；裁决：不加伪前置。
    // T-M6-RECIPE-2：配方页改挂注魔配方（catalyst=知识核心，罐装脑走方块交互不入配方）
    // 用户指令：节点移至原源质终端旧址（源质谐振仓 (1,-4) 左边）。
    private static void registerGachaBox() {
        new ResearchItem("GACHABOX", TAB,
                new AspectList().add(Aspect.MIND, 8).add(Aspect.ENTROPY, 5).add(Aspect.MAGIC, 5),
                -1, -4, 3, new ItemStack(ModItems.BRAIN_OF_PROBABILITY_ITEM.get()))
                .setRound().setParents("DIGISENTIA")
                .setPages(
                        new ResearchPage("tc.research_text.GACHABOX.stage.1"),
                        new ResearchPage(RecipeRegistration.GACHA_BOX),
                        new ResearchPage("tc.research_text.GACHABOX.stage.2"))
                .registerResearchItem();
    }

}