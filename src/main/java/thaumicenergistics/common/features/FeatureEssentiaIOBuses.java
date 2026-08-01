package thaumicenergistics.common.features;

import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.init.ModItems;

/**
 * 功能：源质 IO 总线。
 * 启用源质输入/输出/存储总线Part部件
 * Part注册已移至模组主类ThaumicEnergistics构造方法中，
 * 通过AE2官方PartHelper.registerPart() API完成，
 * 本Feature负责功能开关、配方注册、研究数据等跨模组集成内容。
 */
public class FeatureEssentiaIOBuses extends ThEFeatureBase {
    public FeatureEssentiaIOBuses() { super("io_buses"); }

    @Override
    protected void doRegister() {
        // ===== Part核心注册已在ThaumicEnergistics构造方法中完成 =====
        // 原因：AE2 PartHelper.registerPart()需要DeferredItem注册完成后立即调用，
        // Feature.register()的调用时机同样满足，但为了集中管理Part注册入口，统一在主类中处理。

        // ===== 以下登记本Feature涉及的物品，供跨模组引用追踪 =====
        ThaumicEnergistics.LOG.info("  -> EssentiaImportBus PartItem: {} (ready)",
                ModItems.ESSENTIA_IMPORT_BUS.getId());
        ThaumicEnergistics.LOG.info("  -> EssentiaExportBus PartItem: {} (pending)",
                ModItems.ESSENTIA_EXPORT_BUS.getId());
        ThaumicEnergistics.LOG.info("  -> EssentiaStorageBus PartItem: {} (pending)",
                ModItems.ESSENTIA_STORAGE_BUS.getId());
    }

    @Override
    protected void doPostInit() {
        // 后置初始化：注册神秘时代研究条目、AE2合成配方等
    }
}