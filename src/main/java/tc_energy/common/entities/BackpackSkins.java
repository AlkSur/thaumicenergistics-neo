package tc_energy.common.entities;

import net.minecraft.resources.ResourceLocation;
import tc_energy.ThaumicEnergistics;

/**
 * 傀儡无线背包皮肤枚举。
 * 每种皮肤对应一个纹理文件：{@code textures/model/golembackpack/{texId}.png}。
 * 皮肤通过 AE2 Facade 物品右键傀儡来更换。
 * 对应 1.7.10 的 {@code ItemGolemWirelessBackpack.BackpackSkins}。
 */
public enum BackpackSkins {

    Thaumium("thaum"),
    Stone("stone"),
    Straw("straw"),
    Wood("wood"),
    Flesh("flesh"),
    Clay("clay"),
    Iron("iron"),
    Tallow("tallow"),
    Gold("gold"),
    Diamond("diamond");

    public static final BackpackSkins[] VALUES = values();

    /** 纹理文件 ID（不含路径和扩展名） */
    private final String texId;

    /** 延迟初始化的纹理 ResourceLocation */
    private ResourceLocation texture;

    BackpackSkins(String texId) {
        this.texId = texId;
    }

    /**
     * 获取此皮肤的纹理 ResourceLocation。
     * 延迟初始化，避免在枚举构造期间调用 ThaumicEnergistics.MODID。
     */
    public ResourceLocation getTextureLocation() {
        if (this.texture == null) {
            this.texture = ResourceLocation.fromNamespaceAndPath(
                    ThaumicEnergistics.MODID, "textures/model/golembackpack/" + this.texId + ".png");
        }
        return this.texture;
    }

    /**
     * 从序号获取皮肤，越界时返回默认 Thaumium。
     */
    public static BackpackSkins fromOrdinal(int ordinal) {
        if (ordinal < 0 || ordinal >= VALUES.length) {
            return Thaumium;
        }
        return VALUES[ordinal];
    }
}
