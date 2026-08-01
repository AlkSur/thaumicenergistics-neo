package thaumicenergistics.common.features;

/**
 * 功能：注魔供应器。
 * 为 TC 注魔自动供应源质。
 * 自 1.7.10 FeatureInfusionProvider 移植（约 4k 行）。
 */
public class FeatureInfusionProvider extends ThEFeatureBase {
    public FeatureInfusionProvider() { super("infusion_provider"); }

    @Override
    protected void doRegister() {
        // Infusion Provider 方块经 ModBlocks 注册
        // TODO: Register infusion detection and essentia delivery logic
    }
}
