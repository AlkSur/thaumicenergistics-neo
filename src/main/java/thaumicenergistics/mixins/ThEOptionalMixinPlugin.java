package thaumicenergistics.mixins;

import java.util.List;
import java.util.Set;
import org.objectweb.asm.tree.ClassNode;
import org.spongepowered.asm.mixin.extensibility.IMixinConfigPlugin;
import org.spongepowered.asm.mixin.extensibility.IMixinInfo;
import net.neoforged.fml.loading.LoadingModList;

/** 在目标类加载前排除缺席的可选模组，不依赖尚未创建的 ModList。 */
public final class ThEOptionalMixinPlugin implements IMixinConfigPlugin {
    @Override public void onLoad(String mixinPackage) { }
    @Override public String getRefMapperConfig() { return null; }
    @Override public boolean shouldApplyMixin(String targetClassName, String mixinClassName) {
        if (mixinClassName.contains(".compat.aae.")) {
            return loaded("advanced_ae") && (mixinClassName.endsWith("Essentia") || !loaded("appflux"));
        }
        if (mixinClassName.contains(".compat.ae2lt.")) return loaded("ae2lt");
        if (!mixinClassName.contains(".eap.")) return true;
        return loaded("extendedae_plus");
    }

    private static boolean loaded(String id) {
        var mods = LoadingModList.get();
        return mods != null && mods.getMods().stream()
                .anyMatch(mod -> mod.getModId().equals(id));
    }
    @Override public void acceptTargets(Set<String> mine, Set<String> others) { }
    @Override public List<String> getMixins() { return null; }
    @Override public void preApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
    @Override public void postApply(String target, ClassNode node, String mixin, IMixinInfo info) { }
}
