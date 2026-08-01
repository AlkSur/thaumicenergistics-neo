package thaumicenergistics.common.integration.tc;

import net.minecraft.resources.ResourceLocation;
import thaumicenergistics.api.storage.IAspectStack;
import thaumicenergistics.api.storage.AspectStack;
import java.util.Optional;

/**
 * 源质类型与数量转换辅助。
 * 1.7.10 用 AE 气体/流体桥接；1.21.1 用直接类型映射。
 */
public final class EssentiaConversionHelper {
    private EssentiaConversionHelper() {}

    /** 每单位源质转移的 AE 能量成本。1.7.10: 0.3 AE/单位。 */
    public static final double AE_PER_ESSENTIA = 0.3;

    /** 标准 TC 源质罐容量（单位）。 */
    public static final int JAR_CAPACITY = 64;
    /** 标准 TC 药剂瓶容量（单位）。 */
    public static final int PHIAL_CAPACITY = 8;

    /**
     * 在不同源质单位间转换。
     * TODO: 映射到实际 TC Aspect API。
     */
    public static Optional<IAspectStack> convert(IAspectStack input, String targetUnit) {
        return Optional.of(input);
    }
}
