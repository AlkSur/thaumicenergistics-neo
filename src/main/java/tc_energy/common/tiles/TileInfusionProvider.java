package tc_energy.common.tiles;

import java.util.HashMap;
import java.util.Map;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;

import dev.tc4port.thaumcraft.api.aspect.AspectDefinition;
import dev.tc4port.thaumcraft.api.aspect.AspectId;
import dev.tc4port.thaumcraft.api.essentia.EssentiaApi;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSource;
import dev.tc4port.thaumcraft.api.essentia.EssentiaTransferMode;
import dev.tc4port.thaumcraft.api.essentia.EssentiaSearch;
import tc_energy.init.ModBlockEntities;
import tc_energy.init.ModItems;

/**
 * 注魔供应器：把 AE 网络存储注册为 Ren 版注魔矩阵可发现的 {@link EssentiaSource}。
 * <p>
 * Ren 版注魔矩阵通过 {@code EssentiaApi.extract(level, matrixPos, aspect, amount,
 * EssentiaSearch.nearby(12), mode)} 在范围内查找实现了 {@link EssentiaSource} 的方块实体。
 * 本方块实体把 extractEssentia 转发给 AE 网络（消耗能量），替代旧版 IAspectSource 挂钩。
 * 矩阵的搜索是全有全无抽取：只有网络能满足完整请求时才会命中本方块。
 */
public class TileInfusionProvider extends TileProviderBase implements EssentiaSource {

    /** 注册到 VisSourceRegistry 之前先占位：供应器本身不供应 vis，只供应源质。 */

    public TileInfusionProvider(BlockPos pos, BlockState state) {
        super(ModBlockEntities.INFUSION_PROVIDER.get(), pos, state);
    }

    @Override
    protected ItemStack getItemFromTile() {
        return new ItemStack(ModItems.INFUSION_PROVIDER_ITEM.get());
    }

    @Override
    protected double getIdlePowerUsage() {
        return 5.0;
    }

    public void serverTick() {
    }

    // ===== Ren 版 EssentiaSource（注魔矩阵范围搜索的对接面） =====

    @Override
    public int extractEssentia(AspectId aspect, int amount, EssentiaTransferMode mode) {
        if (!mainNode.isActive() || aspect == null || amount <= 0) {
            return 0;
        }
        if (mode == EssentiaTransferMode.SIMULATE) {
            return getAspectAmountInNetwork(aspect) >= amount ? amount : 0;
        }
        long extracted = extractEssentiaFromNetwork(aspect, amount, false);
        if (extracted >= amount) {
            doParticleFX(aspect);
            return amount;
        }
        if (extracted > 0) {
            // 不应发生：SIMULATE 已预检；万一发生则归还，保证全有全无契约
            injectEssentiaToNetwork(aspect, extracted, false);
        }
        return 0;
    }

    // ===== 粒子 =====

    private void doParticleFX(AspectId aspect) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        AspectDefinition definition = dev.tc4port.thaumcraft.api.aspect.AspectApi.get(aspect);
        int aspectColor = definition != null ? definition.color() : 0xFFFFFF;
        float red = (aspectColor >> 16 & 0xFF) / 255.0F;
        float green = (aspectColor >> 8 & 0xFF) / 255.0F;
        float blue = (aspectColor & 0xFF) / 255.0F;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;

        for (int i = 0; i < 5; i++) {
            serverLevel.sendParticles(
                    dev.tc4port.thaumcraft.registry.TCParticles.INFUSION_RUNE.get(),
                    x + level.random.nextFloat() * 0.6 - 0.3,
                    y + level.random.nextFloat() * 0.4 - 0.1,
                    z + level.random.nextFloat() * 0.6 - 0.3,
                    1, 0, 0, 0, 0.05);
        }
    }
}
