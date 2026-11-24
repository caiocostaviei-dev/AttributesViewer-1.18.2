package com.attributesviewer;

import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.NetworkConstants;

/**
 * Attributes Viewer: tela de atributos no inventario, portada do Apothic Attributes (MIT) para a 1.18.2.
 * Mod somente de cliente: pode entrar em servidores que nao tenham ele instalado.
 */
@Mod(AttributesViewer.MODID)
public class AttributesViewer {

    public static final String MODID = "attributesviewer";

    public AttributesViewer() {
        ModLoadingContext.get().registerExtensionPoint(IExtensionPoint.DisplayTest.class,
            () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, isServer) -> true));
    }

    public static ResourceLocation loc(String path) {
        return new ResourceLocation(MODID, path);
    }

}
