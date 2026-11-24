package com.attributesviewer.client;

import com.attributesviewer.AttributesViewer;

import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Liga o painel de atributos na tela de inventario do jogador.
 * Na 1.18.2 o painel nao e adicionado como widget da tela: desenhamos e tratamos o mouse por eventos.
 */
@Mod.EventBusSubscriber(modid = AttributesViewer.MODID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public class ClientEvents {

    /** Contador de ticks do cliente (o painel atualiza os dados uma vez por tick). */
    public static long ticks = 0;

    private static AttributesGui gui = null;
    private static InventoryScreen guiScreen = null;

    private static boolean isActive(Object screen) {
        return gui != null && screen == guiScreen;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase == TickEvent.Phase.END) ticks++;
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onScreenInit(ScreenEvent.InitScreenEvent.Post e) {
        if (e.getScreen() instanceof InventoryScreen scn) {
            guiScreen = scn;
            gui = new AttributesGui(scn);
            if (AttributesGui.wasOpen) gui.toggleVisibility();
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.DrawScreenEvent.Post e) {
        if (isActive(e.getScreen())) {
            gui.render(e.getPoseStack(), e.getMouseX(), e.getMouseY(), e.getPartialTicks());
        }
    }

    @SubscribeEvent
    public static void onMouseClicked(ScreenEvent.MouseClickedEvent.Pre e) {
        if (isActive(e.getScreen()) && gui.mouseClicked(e.getMouseX(), e.getMouseY(), e.getButton())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseDragged(ScreenEvent.MouseDragEvent.Pre e) {
        if (isActive(e.getScreen()) && gui.mouseDragged(e.getMouseX(), e.getMouseY(), e.getMouseButton(), e.getDragX(), e.getDragY())) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onMouseReleased(ScreenEvent.MouseReleasedEvent.Pre e) {
        if (isActive(e.getScreen())) gui.mouseReleased();
    }

    @SubscribeEvent
    public static void onMouseScrolled(ScreenEvent.MouseScrollEvent.Pre e) {
        if (isActive(e.getScreen()) && gui.isMouseOver(e.getMouseX(), e.getMouseY()) && gui.mouseScrolled(e.getMouseX(), e.getMouseY(), e.getScrollDelta())) {
            e.setCanceled(true);
        }
    }

}
