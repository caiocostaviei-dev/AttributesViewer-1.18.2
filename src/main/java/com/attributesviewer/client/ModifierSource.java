package com.attributesviewer.client;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Recipiente para qualquer fonte de modificador (item equipado, efeito...).
 * Sabe se desenhar como um icone pequeno na tela de atributos e se comparar com outras fontes do mesmo tipo.
 */
public abstract class ModifierSource<T> {

    protected final ModifierSourceType<T> type;
    protected final T data;

    public ModifierSource(ModifierSourceType<T> type, T data) {
        this.type = type;
        this.data = data;
    }

    /** Desenha o icone (9x9) na posicao dada. */
    public abstract void render(PoseStack pose, Font font, int x, int y);

    /** Compara esta fonte com outra do mesmo tipo (usado para ordenar a lista de modificadores). */
    public abstract int compareData(ModifierSource<?> other);

    public ModifierSourceType<T> getType() {
        return this.type;
    }

    public final T getData() {
        return this.data;
    }

    public static class ItemModifierSource extends ModifierSource<ItemStack> {

        public ItemModifierSource(ItemStack data) {
            super(ModifierSourceType.EQUIPMENT, data);
        }

        @Override
        public void render(PoseStack pose, Font font, int x, int y) {
            // Itens da GUI usam a model view stack; escala 0.5 para caber no quadrado 9x9.
            PoseStack mv = RenderSystem.getModelViewStack();
            mv.pushPose();
            mv.translate(x + 0.5, y + 0.5, 350);
            mv.scale(0.5F, 0.5F, 1);
            RenderSystem.applyModelViewMatrix();
            Minecraft.getInstance().getItemRenderer().renderGuiItem(this.data, 0, 0);
            mv.popPose();
            RenderSystem.applyModelViewMatrix();
        }

        @Override
        public int compareData(ModifierSource<?> other) {
            if (!(other instanceof ItemModifierSource o)) return 0;
            ResourceLocation a = ForgeRegistries.ITEMS.getKey(this.data.getItem());
            ResourceLocation b = ForgeRegistries.ITEMS.getKey(o.data.getItem());
            if (a == null || b == null) return 0;
            return a.compareTo(b);
        }

    }

    public static class EffectModifierSource extends ModifierSource<MobEffectInstance> {

        public EffectModifierSource(MobEffectInstance data) {
            super(ModifierSourceType.MOB_EFFECT, data);
        }

        @Override
        public void render(PoseStack pose, Font font, int x, int y) {
            MobEffect effect = this.data.getEffect();
            TextureAtlasSprite sprite = Minecraft.getInstance().getMobEffectTextures().get(effect);
            pose.pushPose();
            pose.translate(x, y, 0);
            pose.scale(0.5F, 0.5F, 1);
            RenderSystem.setShaderTexture(0, sprite.atlas().location());
            GuiComponent.blit(pose, 0, 0, 0, 18, 18, sprite);
            pose.popPose();
        }

        @Override
        public int compareData(ModifierSource<?> other) {
            if (!(other instanceof EffectModifierSource o)) return 0;
            ResourceLocation a = ForgeRegistries.MOB_EFFECTS.getKey(this.data.getEffect());
            ResourceLocation b = ForgeRegistries.MOB_EFFECTS.getKey(o.data.getEffect());
            if (a == null || b == null) return 0;
            return a.compareTo(b);
        }

    }

}
