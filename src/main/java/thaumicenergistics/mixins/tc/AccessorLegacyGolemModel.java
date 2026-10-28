package thaumicenergistics.mixins.tc;

import net.minecraft.client.model.geom.ModelPart;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
import thaumcraft.client.renderers.entity.LegacyGolemModel;

/**
 * LegacyGolemModel 的 root/body 字段均为 private 且无公开访问器，
 * GolemBackpackLayer 需要用 translateAndRotate 沿模型层级对齐身体坐标系。
 * body 无独立字段（只是 root 的子件），故只暴露 root，body 经 root.getChild("body") 获取。
 * LegacyGolemModel 是客户端类，注册在 mixins.json 的 client 段。
 */
@Mixin(LegacyGolemModel.class)
public interface AccessorLegacyGolemModel {

    @Accessor("root")
    ModelPart the$getRoot();
}
