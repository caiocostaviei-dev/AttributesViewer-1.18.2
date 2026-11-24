package com.attributesviewer.client;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

import com.attributesviewer.client.ModifierSource.EffectModifierSource;
import com.attributesviewer.client.ModifierSource.ItemModifierSource;

import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.item.ItemStack;

/**
 * Parte de "registro" de um ModifierSource: sabe extrair todas as fontes daquele tipo de uma entidade.
 */
public abstract class ModifierSourceType<T> {

    private static final List<ModifierSourceType<?>> SOURCE_TYPES = new ArrayList<>();

    public static final ModifierSourceType<ItemStack> EQUIPMENT = register(new ModifierSourceType<>(){

        @Override
        public void extract(LivingEntity entity, BiConsumer<AttributeModifier, ModifierSource<?>> map) {
            for (EquipmentSlot slot : EquipmentSlot.values()) {
                ItemStack item = entity.getItemBySlot(slot);
                item.getAttributeModifiers(slot).values().forEach(modif -> {
                    map.accept(modif, new ItemModifierSource(item));
                });
            }
        }

        @Override
        public int getPriority() {
            return 0;
        }

    });

    public static final ModifierSourceType<MobEffectInstance> MOB_EFFECT = register(new ModifierSourceType<>(){

        @Override
        public void extract(LivingEntity entity, BiConsumer<AttributeModifier, ModifierSource<?>> map) {
            for (MobEffectInstance effectInst : entity.getActiveEffects()) {
                effectInst.getEffect().getAttributeModifiers().values().forEach(modif -> {
                    map.accept(modif, new EffectModifierSource(effectInst));
                });
            }
        }

        @Override
        public int getPriority() {
            return 100;
        }

    });

    public static Collection<ModifierSourceType<?>> getTypes() {
        return Collections.unmodifiableCollection(SOURCE_TYPES);
    }

    public static <T extends ModifierSourceType<?>> T register(T type) {
        SOURCE_TYPES.add(type);
        return type;
    }

    /** Ordem padrao dos modificadores: operacao, depois valor, depois UUID (igual ao original). */
    private static final Comparator<AttributeModifier> MODIFIER_COMPARATOR = Comparator.comparing(AttributeModifier::getOperation)
        .thenComparing(AttributeModifier::getAmount)
        .thenComparing(AttributeModifier::getId);

    public static Comparator<AttributeModifier> compareBySource(Map<UUID, ModifierSource<?>> sources) {
        return (a1, a2) -> {
            ModifierSource<?> s1 = sources.get(a1.getId());
            ModifierSource<?> s2 = sources.get(a2.getId());

            if (s1 != null && s2 != null) {
                int c = Integer.compare(s1.getType().getPriority(), s2.getType().getPriority());
                if (c != 0) return c;
                if (s1.getType() == s2.getType()) {
                    c = s1.compareData(s2);
                    if (c != 0) return c;
                }
                return MODIFIER_COMPARATOR.compare(a1, a2);
            }

            return s1 != null ? -1 : s2 != null ? 1 : 0;
        };
    }

    /** Extrai todas as fontes deste tipo da entidade. */
    public abstract void extract(LivingEntity entity, BiConsumer<AttributeModifier, ModifierSource<?>> map);

    /** Prioridade de exibicao: valores menores aparecem primeiro. */
    public abstract int getPriority();

}
