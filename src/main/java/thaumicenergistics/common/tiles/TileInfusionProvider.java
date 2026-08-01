package thaumicenergistics.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import thaumcraft.api.aspects.Aspect;
import thaumcraft.api.aspects.AspectList;
import thaumcraft.api.aspects.IAspectSource;
import thaumcraft.common.registry.TCParticles;
import thaumicenergistics.init.ModBlockEntities;
import thaumicenergistics.init.ModItems;

public class TileInfusionProvider extends TileProviderBase implements IAspectSource {

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

    // ===== IAspectSource =====

    @Override
    public AspectList getAspects() {
        // 注魔供应器不存储源质；返回空列表避免调用方（TC 管道 suction）NPE
        return new AspectList();
    }

    @Override
    public void setAspects(AspectList aspects) {
    }

    @Override
    public boolean doesContainerAccept(Aspect tag) {
        return false;
    }

    @Override
    public int addToContainer(Aspect tag, int amount) {
        return 0;
    }

    @Override
    public boolean takeFromContainer(Aspect tag, int amount) {
        if (!mainNode.isActive()) return false;
        long extracted = extractEssentiaFromNetwork(tag, amount, false);
        if (extracted >= amount) {
            doParticleFX(tag.getColor());
            return true;
        }
        return false;
    }

    @Override
    @Deprecated
    public boolean takeFromContainer(AspectList ot) {
        return false;
    }

    @Override
    public boolean doesContainerContainAmount(Aspect tag, int amount) {
        if (!mainNode.isActive()) return false;
        return getAspectAmountInNetwork(tag) >= amount;
    }

    @Override
    @Deprecated
    public boolean doesContainerContain(AspectList ot) {
        return false;
    }

    @Override
    public int containerContains(Aspect tag) {
        if (!mainNode.isActive()) return 0;
        return (int) Math.min(getAspectAmountInNetwork(tag), Integer.MAX_VALUE);
    }

    // ===== 粒子 =====

    private void doParticleFX(int aspectColor) {
        if (!(level instanceof ServerLevel serverLevel)) return;

        float red = (aspectColor >> 16 & 0xFF) / 255.0F;
        float green = (aspectColor >> 8 & 0xFF) / 255.0F;
        float blue = (aspectColor & 0xFF) / 255.0F;

        double x = worldPosition.getX() + 0.5;
        double y = worldPosition.getY() + 0.5;
        double z = worldPosition.getZ() + 0.5;

        for (int i = 0; i < 5; i++) {
            serverLevel.sendParticles(
                    TCParticles.BLOCK_RUNE.get(),
                    x + level.random.nextFloat() * 0.6 - 0.3,
                    y + level.random.nextFloat() * 0.4 - 0.1,
                    z + level.random.nextFloat() * 0.6 - 0.3,
                    1, 0, 0, 0, 0.05);
        }
    }
}