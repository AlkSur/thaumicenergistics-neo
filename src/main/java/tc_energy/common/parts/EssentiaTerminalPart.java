package tc_energy.common.parts;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.phys.Vec3;
import appeng.api.parts.IPartItem;
import appeng.api.parts.IPartModel;
import appeng.api.util.KeyTypeSelection;
import appeng.items.parts.PartModels;
import appeng.parts.PartModel;
import appeng.parts.reporting.AbstractTerminalPart;
import tc_energy.ThaumicEnergistics;
import tc_energy.common.integration.appeng.AEssentiaKeyType;

public class EssentiaTerminalPart extends AbstractTerminalPart {

    @PartModels
    public static final ResourceLocation RL_BASE = ThaumicEnergistics.id("parts/essentia_terminal_base");

    @PartModels
    public static final ResourceLocation RL_OFF = ThaumicEnergistics.id("parts/essentia_terminal_off");

    @PartModels
    public static final ResourceLocation RL_HAS_CHANNEL = ThaumicEnergistics.id("parts/essentia_terminal_has_channel");

    @PartModels
    public static final ResourceLocation RL_ON = ThaumicEnergistics.id("parts/essentia_terminal_on");

    public static final IPartModel MODELS_OFF = new PartModel(RL_BASE, RL_OFF, MODEL_STATUS_OFF);
    public static final IPartModel MODELS_ON = new PartModel(RL_BASE, RL_ON, MODEL_STATUS_ON);
    public static final IPartModel MODELS_HAS_CHANNEL = new PartModel(RL_BASE, RL_ON, MODEL_STATUS_HAS_CHANNEL);

    /**
     * 自定义 KeyTypeSelection：只允许源质类型，禁止物品和流体。
     * 父类 AbstractTerminalPart 的 private 字段 keyTypeSelection 是 keyType -> true（全类型），
     * 这里通过覆盖 getKeyTypeSelection() 返回本实例，使 MEStorageMenu 只看到源质。
     */
    private final KeyTypeSelection essentiaOnlyKeyTypeSelection = new KeyTypeSelection(
            this::saveChanges,
            keyType -> keyType == AEssentiaKeyType.INSTANCE);

    public EssentiaTerminalPart(IPartItem<?> partItem) {
        super(partItem);
    }

    @Override
    public IPartModel getStaticModels() {
        return this.selectModel(MODELS_OFF, MODELS_ON, MODELS_HAS_CHANNEL);
    }

    @Override
    public MenuType<?> getMenuType(Player player) {
        return tc_energy.init.ModMenuTypes.ESSENTIA_TERMINAL.get();
    }

    @Override
    public boolean onUseWithoutItem(Player player, Vec3 pos) {
        if (!super.onUseWithoutItem(player, pos) && !player.level().isClientSide) {
            appeng.menu.MenuOpener.open(getMenuType(player), player,
                    appeng.menu.locator.MenuLocators.forPart(this));
        }
        return true;
    }

    /**
     * 返回自定义的 KeyTypeSelection，只包含 AEssentiaKeyType。
     * MEStorageMenu.broadcastChanges() 通过此接口获取可用类型，GUI 据此过滤显示。
     */
    @Override
    public KeyTypeSelection getKeyTypeSelection() {
        return this.essentiaOnlyKeyTypeSelection;
    }

    /**
     * 覆盖 NBT 读取：父类先读（更新父类 private 字段），然后本类用自己的实例再读。
     * 父类 private 字段会被忽略，因为 getKeyTypeSelection() 返回本类实例。
     */
    @Override
    public void readFromNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.readFromNBT(data, registries);
        this.essentiaOnlyKeyTypeSelection.readFromNBT(data, registries);
    }

    /**
     * 覆盖 NBT 写入：父类先写（写入父类 private 字段的全类型），然后本类用自己的实例覆盖。
     * writeToNBT 使用 tag.put("enabledKeyTypes", ...)，后写入覆盖先写入，最终只保存源质类型。
     */
    @Override
    public void writeToNBT(CompoundTag data, HolderLookup.Provider registries) {
        super.writeToNBT(data, registries);
        this.essentiaOnlyKeyTypeSelection.writeToNBT(data);
    }
}
