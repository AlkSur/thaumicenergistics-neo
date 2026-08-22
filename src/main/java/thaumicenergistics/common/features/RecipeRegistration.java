package thaumicenergistics.common.features;

import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import thaumcraft.api.ThaumcraftApi;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.crafting.CrucibleRecipe;
import thaumcraft.api.crafting.InfusionRecipe;
import thaumcraft.api.crafting.ShapedArcaneRecipe;
import thaumcraft.api.crafting.ShapelessArcaneRecipe;
import thaumcraft.common.vis.TCVisHelper;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModBlocks;
import thaumicenergistics.init.ModItems;

public final class RecipeRegistration {
    private RecipeRegistration() {}

    // ==================== 配方引用（供研究页面使用） ====================
    // 注魔配方
    public static InfusionRecipe ESSENTIA_PROVIDER;
    public static InfusionRecipe INFUSION_PROVIDER;
    public static InfusionRecipe ARCANE_ASSEMBLER;
    /** 韵魔观测器注魔配方。 */
    public static InfusionRecipe INFUSION_MONITOR;
    // 奥术有序配方
    public static ShapedArcaneRecipe STORAGE_COMPONENT_1K;
    public static ShapedArcaneRecipe STORAGE_COMPONENT_4K;
    public static ShapedArcaneRecipe STORAGE_COMPONENT_16K;
    public static ShapedArcaneRecipe STORAGE_COMPONENT_64K;
    public static ShapedArcaneRecipe KNOWLEDGE_CORE_ITEM;
    public static ShapedArcaneRecipe FOCUS_AEWRENCH_RECIPE;
    public static ShapedArcaneRecipe GOLEM_WIFI_BACKPACK_RECIPE;
    public static ShapedArcaneRecipe ESSENTIA_IMPORT_BUS;
    public static ShapedArcaneRecipe ESSENTIA_EXPORT_BUS;
    public static ShapedArcaneRecipe ESSENTIA_STORAGE_BUS;
    public static ShapedArcaneRecipe KNOWLEDGE_INSCRIBER_BLOCK;
    public static ShapedArcaneRecipe DISTILLATION_ENCODER_BLOCK;
    // 奥术无序配方
    public static ShapelessArcaneRecipe DIFFUSION_CORE;
    public static ShapelessArcaneRecipe COALESCENCE_CORE;
    public static ShapelessArcaneRecipe ESSENTIA_LEVEL_EMITTER;
    public static ShapelessArcaneRecipe ESSENTIA_TERMINAL;
    public static ShapelessArcaneRecipe ARCANE_CRAFTING_TERMINAL;
    public static ShapelessArcaneRecipe VIS_INTERFACE;
    public static ShapelessArcaneRecipe ESSENTIA_VIBRATION_CHAMBER;
    /** 源质无线接收器（无序奥术）。 */
    public static ShapelessArcaneRecipe ESSENTIA_PROVIDER_CONNECTION;
    /** 源质无线绑定工具（无序奥术）。 */
    public static ShapelessArcaneRecipe WIRELESS_CONNECTOR;
    /** 赛特斯石英复制（坩埚炼金配方——研究页关联）。 */
    public static CrucibleRecipe CERTUS_DUPE_RECIPE;

    private static final TagKey<Item> WISP_ESSENCES = tag("thaumicenergistics", "wisp_essences");
    /** AE2 三种显示器（普通/暗/半暗）任一可用。 */
    private static final TagKey<Item> MONITORS = tag("thaumicenergistics", "monitors");

    @SuppressWarnings("SameParameterValue")
    private static TagKey<Item> tag(String ns, String path) {
        return TagKey.create(Registries.ITEM, ResourceLocation.fromNamespaceAndPath(ns, path));
    }

    public static void registerAll() {
        registerShapedArcane();
        registerShapelessArcane();
        registerInfusion();
        registerCrucible();
        ThaumicEnergistics.LOG.info("ThaumicEnergistics recipes registered.");
    }

    /** 坩埚炼金配方：赛特斯石英复制（丢 1 赛特斯石英 + 水晶/交换要素 → 2 赛特斯石英）。 */
    private static void registerCrucible() {
        ItemStack quartz = ae("certus_quartz_crystal");
        if (quartz.isEmpty()) return;
        CERTUS_DUPE_RECIPE = ThaumcraftApi.addCrucibleRecipe(
                "CERTUS_DUPE",
                quartz.copyWithCount(2),
                quartz.copyWithCount(1),
                new AspectList().add(Aspect.CRYSTAL, 5).add(Aspect.EXCHANGE, 5));
    }

    private static void registerShapedArcane() {
        STORAGE_COMPONENT_1K = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIASTORAGE", stack(ModItems.STORAGE_COMPONENT_1K),
                new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 1),
                "EQ ",
                "QPQ",
                " QE",
                'E', WISP_ESSENCES,
                'Q', ae("certus_quartz_crystal"),
                'P', ae("logic_processor"));

        STORAGE_COMPONENT_4K = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIASTORAGE", stack(ModItems.STORAGE_COMPONENT_4K),
                new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 2),
                "EPE",
                "1G1",
                "E1E",
                'E', WISP_ESSENCES,
                'P', ae("calculation_processor"),
                '1', stack(ModItems.STORAGE_COMPONENT_1K),
                'G', ae("quartz_glass"));

        STORAGE_COMPONENT_16K = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIASTORAGE", stack(ModItems.STORAGE_COMPONENT_16K),
                new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 4),
                "SPE",
                "4G4",
                "E4S",
                'S', tc("salis_mundus"),
                'P', ae("engineering_processor"),
                '4', stack(ModItems.STORAGE_COMPONENT_4K),
                'G', ae("quartz_glass"),
                'E', WISP_ESSENCES);

        STORAGE_COMPONENT_64K = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIASTORAGE", stack(ModItems.STORAGE_COMPONENT_64K),
                new AspectList().add(Aspect.FIRE, 3).add(Aspect.ORDER, 8),
                "SPS",
                "6G6",
                "S6S",
                'S', tc("salis_mundus"),
                'P', ae("engineering_processor"),
                '6', stack(ModItems.STORAGE_COMPONENT_16K),
                'G', ae("quartz_glass"));

        KNOWLEDGE_CORE_ITEM = ThaumcraftApi.addArcaneCraftingRecipe("KNOWLEDGEINSCRIBER", stack(ModItems.KNOWLEDGE_CORE),
                new AspectList().add(Aspect.WATER, 3).add(Aspect.ORDER, 3).add(Aspect.EARTH, 1),
                "QlQ",
                "lBl",
                "QPQ",
                'Q', ae("quartz_glass"),
                'l', new ItemStack(Items.LAPIS_LAZULI, 1),
                'B', tc("zombie_brain"),
                'P', ae("calculation_processor"));

        FOCUS_AEWRENCH_RECIPE = ThaumcraftApi.addArcaneCraftingRecipe("FOCUS_AEWRENCH", stack(ModItems.FOCUS_AEWRENCH),
                new AspectList().add(Aspect.AIR, 10).add(Aspect.FIRE, 10),
                "AqF",
                "qWq",
                "FqA",
                'A', tc("air_shard"),
                'q', new ItemStack(Items.QUARTZ, 1),
                'F', tc("fire_shard"),
                'W', ae("certus_quartz_wrench"));

        GOLEM_WIFI_BACKPACK_RECIPE = ThaumcraftApi.addArcaneCraftingRecipe("GOLEMWIFIBACKPACK", stack(ModItems.GOLEM_WIFI_BACKPACK),
                new AspectList().add(Aspect.AIR, 6).add(Aspect.FIRE, 4).add(Aspect.ORDER, 3),
                "tIt",
                "nRn",
                "fCf",
                't', tc("thaumium_ingot"),
                'I', ae("interface"),
                'n', tc("nitor"),
                'R', ae("wireless_receiver"),
                'f', ae("fluix_crystal"),
                'C', ae("charger"));

        ESSENTIA_IMPORT_BUS = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIABUSES", stack(ModItems.ESSENTIA_IMPORT_BUS),
                new AspectList().add(Aspect.FIRE, 2).add(Aspect.EARTH, 2).add(Aspect.WATER, 1),
                // 注意：TC ShapedArcaneRecipe 要求模式为矩形（各行等长），删 J 后必须补空格占位
                "JD ",
                "IFI",
                "   ",
                'J', tc("jar_of_essentia"),
                'D', stack(ModItems.DIFFUSION_CORE),
                'I', new ItemStack(Items.IRON_INGOT, 1),
                'F', tc("essentia_filter"));

        ESSENTIA_EXPORT_BUS = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIABUSES", stack(ModItems.ESSENTIA_EXPORT_BUS),
                new AspectList().add(Aspect.FIRE, 2).add(Aspect.EARTH, 2).add(Aspect.WATER, 1),
                "JC ",
                "IFI",
                "   ",
                'J', tc("jar_of_essentia"),
                'C', stack(ModItems.COALESCENCE_CORE),
                'I', new ItemStack(Items.IRON_INGOT, 1),
                'F', tc("essentia_filter"));

        ESSENTIA_STORAGE_BUS = ThaumcraftApi.addArcaneCraftingRecipe("ESSENTIABUSES", stack(ModItems.ESSENTIA_STORAGE_BUS),
                new AspectList().add(Aspect.EARTH, 3).add(Aspect.FIRE, 3).add(Aspect.WATER, 3),
                "DCF",
                "IGI",
                "   ",
                'D', stack(ModItems.DIFFUSION_CORE),
                'C', stack(ModItems.COALESCENCE_CORE),
                'F', tc("essentia_filter"),
                'I', new ItemStack(Items.IRON_INGOT, 1),
                'G', ae("quartz_glass"));

        KNOWLEDGE_INSCRIBER_BLOCK = ThaumcraftApi.addArcaneCraftingRecipe("KNOWLEDGEINSCRIBER", stack(ModBlocks.KNOWLEDGE_INSCRIBER),
                new AspectList().add(Aspect.WATER, 5).add(Aspect.EARTH, 5).add(Aspect.FIRE, 5).add(Aspect.AIR, 5).add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5),
                "ILI",
                " T ",
                "IPI",
                'I', new ItemStack(Items.IRON_INGOT, 1),
                'L', MONITORS,
                'T', tc("thaumonomicon"),
                'P', ae("logic_processor"));

        DISTILLATION_ENCODER_BLOCK = ThaumcraftApi.addArcaneCraftingRecipe("DISTILLATIONENCODER", stack(ModBlocks.DISTILLATION_ENCODER),
                new AspectList().add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5).add(Aspect.FIRE, 3),
                "ILI",
                " G ",
                "IPI",
                'I', new ItemStack(Items.IRON_INGOT, 1),
                'L', MONITORS,
                'G', tc("thaumometer"),
                'P', ae("engineering_processor"));
    }


    private static void registerShapelessArcane() {
        // 1. 扩散核心
        DIFFUSION_CORE = ThaumcraftApi.addShapelessArcaneCraftingRecipe("DIGISENTIA", stack(ModItems.DIFFUSION_CORE),
                new AspectList().add(Aspect.WATER, 2).add(Aspect.ENTROPY, 2),
                tc("quicksilver_drop"),
                tc("quicksilver_drop"),
                tc("quicksilver_drop"),
                tc("entropy_shard"),
                ae("annihilation_core"));

        // 2. 凝聚核心
        COALESCENCE_CORE = ThaumcraftApi.addShapelessArcaneCraftingRecipe("DIGISENTIA", stack(ModItems.COALESCENCE_CORE),
                new AspectList().add(Aspect.WATER, 2).add(Aspect.ORDER, 2),
                tc("quicksilver_drop"),
                tc("quicksilver_drop"),
                tc("quicksilver_drop"),
                tc("order_shard"),
                ae("formation_core"));

        // 3. 原质等级发射器
        ESSENTIA_LEVEL_EMITTER = ThaumcraftApi.addShapelessArcaneCraftingRecipe("ESSENTIATERMINAL", new ItemStack(ModItems.ESSENTIA_LEVEL_EMITTER.get(), 1),
                new AspectList().add(Aspect.FIRE, 4),
                ae("calculation_processor"),
                new ItemStack(Items.REDSTONE_TORCH),
                tc("salis_mundus"));

        // 4. 原质终端
        ESSENTIA_TERMINAL = ThaumcraftApi.addShapelessArcaneCraftingRecipe("ESSENTIATERMINAL", stack(ModItems.ESSENTIA_TERMINAL_ITEM),
                new AspectList().add(Aspect.WATER, 5).add(Aspect.ORDER, 2).add(Aspect.FIRE, 1),
                MONITORS,
                stack(ModItems.DIFFUSION_CORE),
                stack(ModItems.COALESCENCE_CORE),
                ae("logic_processor"),
                tc("vis_filter"));

        // 5. 奥术合成终端
        ARCANE_CRAFTING_TERMINAL = ThaumcraftApi.addShapelessArcaneCraftingRecipe("ARCANECRAFTINGTERMINAL", stack(ModItems.ARCANE_CRAFTING_TERMINAL_ITEM),
                new AspectList().add(Aspect.AIR, 10).add(Aspect.EARTH, 10).add(Aspect.FIRE, 10).add(Aspect.WATER, 10).add(Aspect.ORDER, 10).add(Aspect.ENTROPY, 10),
                ae("terminal"),
                tc("arcane_workbench"),
                ae("calculation_processor"));

        // 6. 源流接口
        VIS_INTERFACE = ThaumcraftApi.addShapelessArcaneCraftingRecipe("VISINTERFACE", stack(ModItems.VIS_INTERFACE),
                new AspectList().add(Aspect.AIR, 2).add(Aspect.EARTH, 2).add(Aspect.FIRE, 2).add(Aspect.WATER, 2).add(Aspect.ORDER, 2).add(Aspect.ENTROPY, 2),
                tc("balanced_shard"),
                ae("me_p2p_tunnel"));

        // 7. 原质振荡室
        ESSENTIA_VIBRATION_CHAMBER = ThaumcraftApi.addShapelessArcaneCraftingRecipe("ESSENTIAVIBRATIONCHAMBER", stack(ModBlocks.ESSENTIA_VIBRATION_CHAMBER),
                new AspectList().add(Aspect.FIRE, 10).add(Aspect.WATER, 6).add(Aspect.ORDER, 4).add(Aspect.ENTROPY, 4),
                ae("vibration_chamber"),
                tc("jar_of_essentia"),
                stack(ModItems.COALESCENCE_CORE));

        // 8. 源质无线接收器（无序奥术，研究：源质供应器）
        ESSENTIA_PROVIDER_CONNECTION = ThaumcraftApi.addShapelessArcaneCraftingRecipe("ESSENTIAPROVIDER",
                new ItemStack(ModBlocks.ESSENTIA_PROVIDER_CONNECTION.get(), 2),
                new AspectList().add(Aspect.ORDER, 8).add(Aspect.AIR, 6).add(Aspect.FIRE, 3),
                stack(ModItems.COALESCENCE_CORE),
                stack(ModItems.DIFFUSION_CORE),
                ae("wireless_receiver"),
                tc("balanced_shard"));

        // 9. 源质无线绑定工具（无序奥术，研究：源质供应器）
        WIRELESS_CONNECTOR = ThaumcraftApi.addShapelessArcaneCraftingRecipe("ESSENTIAPROVIDER",
                stack(ModItems.WIRELESS_CONNECTOR),
                new AspectList().add(Aspect.ORDER, 5).add(Aspect.ENTROPY, 5),
                ae("certus_quartz_wrench"),
                tc("balanced_shard"));

    }


    private static void registerInfusion() {
        // 1. 原质供应器
        ESSENTIA_PROVIDER = ThaumcraftApi.addInfusionCraftingRecipe("ESSENTIAPROVIDER", stack(ModBlocks.ESSENTIA_PROVIDER),
                1, new AspectList().add(Aspect.MECHANISM, 64).add(Aspect.MAGIC, 32).add(Aspect.ORDER, 32).add(Aspect.EXCHANGE, 16),
                ae("interface"),
                tc("water_shard"),
                tc("water_shard"),
                tc("salis_mundus"),
                tc("salis_mundus"),
                stack(ModItems.DIFFUSION_CORE),
                stack(ModItems.COALESCENCE_CORE),
                tc("essentia_filter"),
                tc("essentia_filter"));

        // 2. 注魔供应器
        INFUSION_PROVIDER = ThaumcraftApi.addInfusionCraftingRecipe("INFUSIONPROVIDER", stack(ModBlocks.INFUSION_PROVIDER),
                3, new AspectList().add(Aspect.MECHANISM, 64).add(Aspect.MAGIC, 32).add(Aspect.ORDER, 32).add(Aspect.EXCHANGE, 16),
                ae("interface"),
                tc("essentia_mirror"),
                tc("essentia_mirror"),
                tc("air_shard"),
                tc("air_shard"),
                stack(ModItems.COALESCENCE_CORE),
                stack(ModItems.COALESCENCE_CORE),
                tc("salis_mundus"),
                tc("salis_mundus"));

        // 3. 奥术装配器
        // 权杖要求：6 个原质要素值全满（满充能）。createFullVisScepter() 生成"满充能"预期栈，
        // TC port 的 InfusionRecipe.hasExpectedComponents 会逐键比对 custom_data——
        // 预期键必须全部存在于实物且数值相等 → 未满充能的权杖无法匹配。
        // 组件数：8 个（1.7.10 原版一致）。实测 9 组件（含世界盐）注魔祭坛无法启动——
        // TC 自身所有注魔配方组件数上限就是 8。
        ARCANE_ASSEMBLER = ThaumcraftApi.addInfusionCraftingRecipe("ARCANEASSEMBLER", stack(ModBlocks.ARCANE_ASSEMBLER),
                5, new AspectList().add(Aspect.CRAFT, 64).add(Aspect.EXCHANGE, 32).add(Aspect.AURA, 16).add(Aspect.MAGIC, 16).add(Aspect.METAL, 8).add(Aspect.CRYSTAL, 8),
                ae("molecular_assembler"),
                tc("fire_shard"),
                tc("entropy_shard"),
                tc("order_shard"),
                tc("balanced_shard"),
                tc("water_shard"),
                tc("earth_shard"),
                tc("air_shard"),
                createFullVisScepter());

        // 4. 韵魔观测器
        // 中央：原版侦测器；外围：元始护符 + 揭示护目镜 + 世界盐×2。
        // 需要灵气：魔力×16、灵气(AURA)×32、感官(SENSES, tag=sensus)×16。
        // 不稳定度 3（配方基础，参与"总不稳定 = 基础 + 祭坛对称度"，可自由调整：
        // 1~2 低风险 / 3~5 中等 / 6~8 高）。
        INFUSION_MONITOR = ThaumcraftApi.addInfusionCraftingRecipe("INFUSIONMONITOR", stack(ModBlocks.INFUSION_MONITOR),
                3, new AspectList().add(Aspect.MAGIC, 16).add(Aspect.AURA, 32).add(Aspect.SENSES, 16),
                new ItemStack(Items.OBSERVER),
                tc("primal_charm"),
                tc("goggles_revealing"),
                tc("salis_mundus"),
                tc("salis_mundus"));
    }

    /**
     * 生成"满充能"银木神秘权杖作为注魔配方的预期组件。
     *
     * ⚠ 关键：不要调用 TCVisHelper.setWandParts()！玩家实际合成的权杖（ArcaneSceptreRecipe →
     * WandCastingItem.createWandFor）是纯物品，custom_data 只有充能时写入的 tc_vis_* 键，
     * 没有 tc_wand_rod/tc_wand_cap。若在预期侧写入这些键，hasExpectedComponents 逐键比对时
     * 会因实物缺少而永不匹配——满充能权杖也放不进去。
     *
     * getWandMaxVis 用 fallbackVariant "silverwood_thaumium_scepter" 解析即可得 15000
     * （银木杆 10000 × 权杖 1.5），无需依赖栈上的部件数据。
     */
    private static ItemStack createFullVisScepter() {
        ItemStack scepter = tc("silverwood_thaumium_scepter");
        if (scepter.isEmpty()) return scepter;
        int max = TCVisHelper.getWandMaxVis(scepter, "silverwood_thaumium_scepter");
        for (Aspect a : Aspect.getPrimalAspects()) {
            TCVisHelper.storeVis(scepter, a, max, max);
        }
        return scepter;
    }

    private static ItemStack stack(java.util.function.Supplier<? extends net.minecraft.world.level.ItemLike> s) {
        return new ItemStack(s.get());
    }
    private static ItemStack tc(String name) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("thaumcraft", name)));
    }
    private static ItemStack ae(String name) {
        return new ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM.get(
                ResourceLocation.fromNamespaceAndPath("ae2", name)));
    }
}