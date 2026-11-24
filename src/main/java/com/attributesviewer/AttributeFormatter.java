package com.attributesviewer;

import javax.annotation.Nullable;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.common.ForgeMod;

/**
 * Formatacao de valores e modificadores de atributos (mesma logica do IFormattableAttribute do Apothic Attributes).
 */
public final class AttributeFormatter {

    private AttributeFormatter() {}

    /**
     * Converte o valor de um modificador (ou o valor cru, se op for null) para o texto exibido.
     */
    public static MutableComponent toValueComponent(Attribute attr, @Nullable Operation op, double value) {
        // Resistencia a repulsao e velocidade de nado sao percentuais.
        if (attr == Attributes.KNOCKBACK_RESISTANCE || attr == ForgeMod.SWIM_SPEED.get()) {
            return new TranslatableComponent("attributesviewer.value.percent", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(value * 100));
        }
        // Velocidade de movimento: o padrao e 0.1, entao exibimos como percentual.
        if (attr == Attributes.MOVEMENT_SPEED && isNullOrAddition(op)) {
            return new TranslatableComponent("attributesviewer.value.percent", ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(value * 1000));
        }
        String key = isNullOrAddition(op) ? "attributesviewer.value.flat" : "attributesviewer.value.percent";
        return new TranslatableComponent(key, ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(isNullOrAddition(op) ? value : value * 100));
    }

    /**
     * Converte um modificador na linha de tooltip (ex: "+2 Armadura"), com info de debug se F3+H estiver ligado.
     */
    public static MutableComponent toComponent(Attribute attr, AttributeModifier modif, TooltipFlag flag) {
        double value = modif.getAmount();
        MutableComponent comp;
        if (value > 0.0D) {
            comp = new TranslatableComponent("attributesviewer.modifier.plus", toValueComponent(attr, modif.getOperation(), value), new TranslatableComponent(attr.getDescriptionId())).withStyle(ChatFormatting.BLUE);
        }
        else {
            value *= -1.0D;
            comp = new TranslatableComponent("attributesviewer.modifier.take", toValueComponent(attr, modif.getOperation(), value), new TranslatableComponent(attr.getDescriptionId())).withStyle(ChatFormatting.RED);
        }
        return comp.append(getDebugInfo(modif, flag));
    }

    public static Component getDebugInfo(AttributeModifier modif, TooltipFlag flag) {
        Component debugInfo = TextComponent.EMPTY;
        if (flag.isAdvanced()) {
            // Mostra a operacao e o valor "real". MULTIPLY_TOTAL tem offset de +1 por causa de como e calculado.
            double advValue = (modif.getOperation() == Operation.MULTIPLY_TOTAL ? 1 : 0) + modif.getAmount();
            String valueStr = ItemStack.ATTRIBUTE_MODIFIER_FORMAT.format(advValue);
            String txt = switch (modif.getOperation()) {
                case ADDITION -> advValue > 0 ? String.format("[+%s]", valueStr) : String.format("[%s]", valueStr);
                case MULTIPLY_BASE -> advValue > 0 ? String.format("[+%sx]", valueStr) : String.format("[%sx]", valueStr);
                case MULTIPLY_TOTAL -> String.format("[x%s]", valueStr);
            };
            debugInfo = new TextComponent(" ").append(new TextComponent(txt).withStyle(ChatFormatting.GRAY));
        }
        return debugInfo;
    }

    private static boolean isNullOrAddition(@Nullable Operation op) {
        return op == null || op == Operation.ADDITION;
    }

}
