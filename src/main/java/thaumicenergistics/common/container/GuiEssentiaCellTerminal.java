package thaumicenergistics.common.container;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Inventory;
import thaumicenergistics.ThaumicEnergistics;

public class GuiEssentiaCellTerminal extends AbstractContainerScreen<ContainerEssentiaCellTerminal> {
    static final ResourceLocation TEX = ResourceLocation.fromNamespaceAndPath(ThaumicEnergistics.MODID,"textures/gui/essentia_terminal.png");
    public GuiEssentiaCellTerminal(ContainerEssentiaCellTerminal m, Inventory inv, Component t) { super(m,inv,t); imageWidth=176; imageHeight=166; inventoryLabelY=imageHeight-94; }
    @Override protected void renderBg(GuiGraphics g, float pt, int mx, int my) { int x=(width-imageWidth)/2,y=(height-imageHeight)/2; g.blit(TEX,x,y,0,0,imageWidth,imageHeight); }
    @Override public void render(GuiGraphics g, int mx, int my, float pt) { super.render(g,mx,my,pt); renderTooltip(g,mx,my); }
}
