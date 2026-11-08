package thaumicenergistics.mixins.ae2.provider;

import java.util.List;
import java.util.Set;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.LoadingModList;
import net.neoforged.fml.loading.moddiscovery.ModInfo;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;

/**
 * AppFlux 已提供样板供应器卡槽时，跳过本模组的卡槽注入。源质能力由独立配置始终加载。
 */
public class ThEProviderMixinPlugin implements IMixinConfigPlugin {

    /** 应用通量的 modId（glodblock/ExtendedAE 系）。 */
    private static final String APPFLUX_MOD_ID = "appflux";

    @Override
    public void onLoad(String mixinPackage) {
    }

    @Override
    public String getRefMapperConfig() {
        return null;
    }

    @Override
    public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        return !isModLoaded(APPFLUX_MOD_ID);
    }

    @Override
    public void acceptTargets(Set<String> myTargets, Set<String> otherTargets) {
    }

    @Override
    public List<String> getMixins() {
        return null;
    }

    @Override
    public void preApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    @Override
    public void postApply(String targetClassName, ClassNode targetClass, String mixinClassName, IMixinInfo mixinInfo) {
    }

    /** {@code ModList} 在 mixin 应用早期为 null，此时退回 {@code LoadingModList}（同 AppFlux 插件做法）。 */
    private static boolean isModLoaded(String modId) {
        var modList = ModList.get();
        if (modList == null) {
            var loadingMods = LoadingModList.get();
            return loadingMods != null && loadingMods.getMods().stream()
                    .map(ModInfo::getModId)
                    .anyMatch(modId::equals);
        }
        return modList.isLoaded(modId);
    }
}
