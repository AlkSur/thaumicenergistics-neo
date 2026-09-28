package tc_energy.common.tiles;

import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;

public abstract class ThETileBase extends BlockEntity {
    protected boolean isActive;
    public ThETileBase(BlockEntityType<?> type, BlockPos pos, BlockState state) { super(type, pos, state); }
    public boolean isActive() { return isActive; }
    public void onBreakBlock() {}
    @Override protected void loadAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.loadAdditional(tag, reg); isActive = tag.getBoolean("Active"); }
    @Override protected void saveAdditional(CompoundTag tag, HolderLookup.Provider reg) { super.saveAdditional(tag, reg); tag.putBoolean("Active", isActive); }
}
