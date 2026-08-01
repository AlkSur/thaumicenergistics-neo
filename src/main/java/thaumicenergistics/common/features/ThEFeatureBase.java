package thaumicenergistics.common.features;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import thaumicenergistics.ThaumicEnergistics;
import thaumicenergistics.config.ThEConfig;
import java.util.function.Supplier;

/**
 * 所有功能模块的基类。每个功能可独立启停。
 * 自 1.7.10 ThEFeatureBase 移植。
 */
public abstract class ThEFeatureBase {
    private final String name;
    protected boolean enabled;

    protected ThEFeatureBase(String name) {
        this.name = name;
        this.enabled = ThEConfig.isFeatureEnabled(name);
    }

    public final String getName() { return name; }
    public final boolean isEnabled() { return enabled; }

    /** 模组构造期间调用，用于注册物品与方块。 */
    public void register() {
        if (!enabled) return;
        ThaumicEnergistics.LOG.info("Feature [{}] loading...", name);
        doRegister();
    }

    /** 所有功能注册完成后调用，用于跨功能设置。 */
    public void postInit() {
        if (!enabled) return;
        doPostInit();
    }

    /** 覆写以注册本功能特有的方块/物品。 */
    protected abstract void doRegister();
    /** 覆写以进行注册后设置（配方、研究等）。 */
    protected void doPostInit() {}
}
