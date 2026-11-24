package com.attributesviewer.client;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

import javax.annotation.Nullable;

import com.attributesviewer.AttributeFormatter;
import com.attributesviewer.AttributesViewer;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiComponent;
import net.minecraft.client.gui.components.ImageButton;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.locale.Language;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextComponent;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * Painel de atributos ao lado do inventario. Layout, medidas e comportamento portados do Apothic Attributes (1.20).
 * Como na 1.18.2 nao existe GuiGraphics, tudo e desenhado com PoseStack + GuiComponent.
 */
public class AttributesGui {

    public static final ResourceLocation TEXTURES = AttributesViewer.loc("textures/gui/attributes_gui.png");
    public static final ResourceLocation COMPONENT_TEXTURE = AttributesViewer.loc("textures/gui/attribute_component.png");
    public static final int ENTRY_HEIGHT = 22;
    public static final int MAX_ENTRIES = 6;
    public static final int WIDTH = 131;

    // So existe um jogador, entao guardamos estado em campos estaticos (nao persiste entre sessoes).
    public static boolean wasOpen = false;
    protected static float scrollOffset = 0;
    protected static boolean hideUnchanged = false;

    protected final InventoryScreen parent;
    protected final Player player;
    protected final Font font = Minecraft.getInstance().font;
    protected final ImageButton toggleBtn;
    protected ImageButton recipeBookButton = null;
    protected final HideUnchangedButton hideUnchangedBtn;

    protected int leftPos, topPos;
    protected boolean scrolling;
    protected int startIndex;
    protected List<AttributeInstance> data = new ArrayList<>();
    protected boolean open = false;
    protected long lastRefreshTick = -1;

    private static final Grad GRAD = new Grad();

    public AttributesGui(InventoryScreen parent) {
        this.parent = parent;
        this.player = Minecraft.getInstance().player;
        this.refreshData();
        this.leftPos = parent.getGuiLeft() - WIDTH;
        this.topPos = parent.getGuiTop();
        this.toggleBtn = new ImageButton(parent.getGuiLeft() + 63, parent.getGuiTop() + 10, 10, 10, WIDTH, 0, 10, TEXTURES, 256, 256, btn -> {
            this.toggleVisibility();
        }, new TranslatableComponent("attributesviewer.gui.show_attributes"));

        // Procura o botao do livro de receitas (o primeiro ImageButton da tela).
        for (GuiEventListener child : this.parent.children()) {
            if (child instanceof ImageButton imgBtn) {
                this.recipeBookButton = imgBtn;
                break;
            }
        }
        this.hideUnchangedBtn = new HideUnchangedButton(0, 0);
    }

    public void refreshData() {
        this.data.clear();
        ForgeRegistries.ATTRIBUTES.getValues().stream()
            .map(this.player::getAttribute)
            .filter(Objects::nonNull)
            .filter(ai -> !hideUnchanged || (ai.getBaseValue() != ai.getValue()))
            .forEach(this.data::add);
        this.data.sort(this::compareAttrs);
        this.startIndex = (int) (scrollOffset * this.getOffScreenRows() + 0.5D);
    }

    public void toggleVisibility() {
        this.open = !this.open;
        if (this.open && this.parent.getRecipeBookComponent().isVisible()) {
            this.parent.getRecipeBookComponent().toggleVisibility();
        }
        this.hideUnchangedBtn.visible = this.open;

        int newLeftPos;
        if (this.open && this.parent.width >= 379) {
            newLeftPos = 177 + (this.parent.width - this.parent.imageWidth - 200) / 2;
        }
        else {
            newLeftPos = (this.parent.width - this.parent.imageWidth) / 2;
        }

        this.parent.leftPos = newLeftPos;
        this.leftPos = this.parent.getGuiLeft() - WIDTH;
        this.topPos = this.parent.getGuiTop();

        if (this.recipeBookButton != null) {
            this.recipeBookButton.x = this.parent.getGuiLeft() + 104;
            this.recipeBookButton.y = this.parent.height / 2 - 22;
        }
        this.hideUnchangedBtn.x = this.leftPos + 7;
        this.hideUnchangedBtn.y = this.topPos + 151;
    }

    protected int compareAttrs(AttributeInstance a1, AttributeInstance a2) {
        String name = I18n.get(a1.getAttribute().getDescriptionId());
        String name2 = I18n.get(a2.getAttribute().getDescriptionId());
        return name.compareTo(name2);
    }

    public boolean isMouseOver(double mouseX, double mouseY) {
        if (!this.open) return false;
        return this.isHovering(0, 0, WIDTH, 166, mouseX, mouseY);
    }

    private static TooltipFlag tooltipFlag() {
        return Minecraft.getInstance().options.advancedItemTooltips ? TooltipFlag.Default.ADVANCED : TooltipFlag.Default.NORMAL;
    }

    private static void bind() {
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        RenderSystem.setShaderTexture(0, TEXTURES);
    }

    private static void blit(PoseStack pose, int x, int y, int u, int v, int w, int h) {
        bind();
        GuiComponent.blit(pose, x, y, (float) u, (float) v, w, h, 256, 256);
    }

    public void render(PoseStack pose, int mouseX, int mouseY, float partialTicks) {
        this.toggleBtn.x = this.parent.getGuiLeft() + 63;
        this.toggleBtn.y = this.parent.getGuiTop() + 10;
        if (this.parent.getRecipeBookComponent().isVisible()) this.open = false;
        wasOpen = this.open;
        this.hideUnchangedBtn.visible = this.open;

        this.toggleBtn.render(pose, mouseX, mouseY, partialTicks);
        if (!this.open) return;

        long now = ClientEvents.ticks;
        if (this.lastRefreshTick != now) {
            this.lastRefreshTick = now;
            this.refreshData();
        }

        int left = this.leftPos;
        int top = this.topPos;
        blit(pose, left, top, 0, 0, WIDTH, 166);
        int scrollbarPos = (int) (117 * scrollOffset);
        blit(pose, left + 111, top + 16 + scrollbarPos, 244, this.isScrollBarActive() ? 0 : 15, 12, 15);
        int idx = this.startIndex;
        while (idx < this.startIndex + MAX_ENTRIES && idx < this.data.size()) {
            this.renderEntry(pose, this.data.get(idx), this.leftPos + 8, this.topPos + 16 + ENTRY_HEIGHT * (idx - this.startIndex), mouseX, mouseY);
            idx++;
        }
        this.font.draw(pose, new TranslatableComponent("attributesviewer.gui.attributes"), this.leftPos + 8, this.topPos + 5, 0x404040);
        this.font.draw(pose, new TranslatableComponent("attributesviewer.gui.hide_unchanged"), this.leftPos + 20, this.topPos + 152, 0x404040);
        this.hideUnchangedBtn.render(pose, mouseX, mouseY, partialTicks);
        this.renderTooltip(pose, mouseX, mouseY);
    }

    // ---------------------------------------------------------------- tooltip

    /** Uma linha do tooltip. Linhas de modificador reservam espaco para o icone da fonte. */
    private static class TipLine {

        final FormattedCharSequence text;
        @Nullable
        final ModifierSource<?> source;
        final boolean modifier; // faz parte de um modificador (largura +12)
        final boolean icon; // primeira linha do modificador: desenha o quadradinho + icone

        TipLine(FormattedCharSequence text, @Nullable ModifierSource<?> source, boolean modifier, boolean icon) {
            this.text = text;
            this.source = source;
            this.modifier = modifier;
            this.icon = icon;
        }

    }

    protected void renderTooltip(PoseStack pose, int mouseX, int mouseY) {
        AttributeInstance inst = this.getHoveredSlot(mouseX, mouseY);
        if (inst == null) return;

        TooltipFlag flag = tooltipFlag();
        Attribute attr = inst.getAttribute();
        List<Component> list = new ArrayList<>();
        MutableComponent name = new TranslatableComponent(attr.getDescriptionId()).withStyle(Style.EMPTY.withColor(ChatFormatting.GOLD).withUnderlined(true));

        if (flag.isAdvanced()) {
            Style style = Style.EMPTY.withColor(ChatFormatting.GRAY).withUnderlined(false);
            name.append(new TextComponent(" [" + ForgeRegistries.ATTRIBUTES.getKey(attr) + "]").withStyle(style));
        }

        list.add(name);

        String key = attr.getDescriptionId() + ".desc";

        if (I18n.exists(key)) {
            list.add(new TranslatableComponent(key).withStyle(ChatFormatting.YELLOW, ChatFormatting.ITALIC));
        }
        else if (flag.isAdvanced()) {
            list.add(new TextComponent(key).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
        }

        ChatFormatting color = ChatFormatting.GRAY;
        if (attr instanceof RangedAttribute) {
            if (inst.getValue() > inst.getBaseValue()) {
                color = ChatFormatting.YELLOW;
            }
            else if (inst.getValue() < inst.getBaseValue()) {
                color = ChatFormatting.RED;
            }
        }

        Component valueComp = AttributeFormatter.toValueComponent(attr, null, inst.getValue()).withStyle(color);
        Component baseComp = AttributeFormatter.toValueComponent(attr, null, inst.getBaseValue()).withStyle(ChatFormatting.GRAY);

        list.add(TextComponent.EMPTY);
        list.add(new TranslatableComponent("attributesviewer.gui.current", valueComp).withStyle(ChatFormatting.GRAY));

        Component base = new TranslatableComponent("attributesviewer.gui.base", baseComp).withStyle(ChatFormatting.GRAY);

        if (attr instanceof RangedAttribute ra) {
            Component min = AttributeFormatter.toValueComponent(attr, null, ra.getMinValue());
            min = new TranslatableComponent("attributesviewer.gui.min", min);
            Component max = AttributeFormatter.toValueComponent(attr, null, ra.getMaxValue());
            max = new TranslatableComponent("attributesviewer.gui.max", max);
            list.add(new TranslatableComponent("%s \u2507 %s \u2507 %s", base, min, max).withStyle(ChatFormatting.GRAY));
        }
        else {
            list.add(base);
        }

        List<TipLine> finalTooltip = new ArrayList<>(list.size());
        for (Component txt : list) {
            this.addComp(txt, finalTooltip);
        }

        if (inst.getModifiers().stream().anyMatch(modif -> modif.getAmount() != 0)) {
            this.addComp(TextComponent.EMPTY, finalTooltip);
            this.addComp(new TranslatableComponent("attributesviewer.gui.modifiers").withStyle(ChatFormatting.GOLD), finalTooltip);

            Map<UUID, ModifierSource<?>> modifiersToSources = new HashMap<>();

            for (ModifierSourceType<?> type : ModifierSourceType.getTypes()) {
                type.extract(this.player, (modif, source) -> modifiersToSources.put(modif.getId(), source));
            }

            double[] numericValues = new double[3];

            for (Operation op : Operation.values()) {
                List<AttributeModifier> modifiers = new ArrayList<>(inst.getModifiers(op));
                double opValue = modifiers.stream().mapToDouble(AttributeModifier::getAmount).reduce(op == Operation.MULTIPLY_TOTAL ? 1 : 0, (res, elem) -> op == Operation.MULTIPLY_TOTAL ? res * (1 + elem) : res + elem);

                modifiers.sort(ModifierSourceType.compareBySource(modifiersToSources));
                for (AttributeModifier modif : modifiers) {
                    if (modif.getAmount() != 0) {
                        Component comp = AttributeFormatter.toComponent(attr, modif, flag);
                        ModifierSource<?> src = modifiersToSources.get(modif.getId());
                        this.addModifierComp(src, comp, finalTooltip);
                    }
                }
                numericValues[op.ordinal()] = opValue;
            }

            this.addComp(TextComponent.EMPTY, finalTooltip);
            this.addComp(new TextComponent("Modifier Formula").withStyle(ChatFormatting.GOLD), finalTooltip);

            this.addComp(buildFormula(valueComp, baseComp, numericValues), finalTooltip);
        }

        this.drawTooltip(pose, finalTooltip, this.leftPos - 16, mouseY);
    }

    private int maxTooltipWidth() {
        return this.leftPos - 16;
    }

    private void addComp(Component comp, List<TipLine> finalTooltip) {
        if (comp == TextComponent.EMPTY) {
            finalTooltip.add(new TipLine(FormattedCharSequence.EMPTY, null, false, false));
        }
        else {
            for (FormattedText fTxt : this.font.getSplitter().splitLines(comp, this.maxTooltipWidth(), comp.getStyle())) {
                finalTooltip.add(new TipLine(Language.getInstance().getVisualOrder(fTxt), null, false, false));
            }
        }
    }

    private void addModifierComp(@Nullable ModifierSource<?> source, Component comp, List<TipLine> finalTooltip) {
        List<FormattedCharSequence> lines = this.font.split(comp, this.maxTooltipWidth());
        for (int i = 0; i < lines.size(); i++) {
            finalTooltip.add(new TipLine(lines.get(i), source, true, i == 0));
        }
    }

    /** Desenha o tooltip no estilo vanilla (mesmo posicionamento do DefaultTooltipPositioner). */
    private void drawTooltip(PoseStack pose, List<TipLine> lines, int anchorX, int anchorY) {
        if (lines.isEmpty()) return;

        int width = 0;
        for (TipLine line : lines) {
            int w = this.font.width(line.text) + (line.modifier ? 12 : 0);
            if (w > width) width = w;
        }
        // O original passa x = leftPos - 16 - larguraMaxima; o posicionador padrao soma +12 em x e -12 em y.
        int x = anchorX - width + 12;
        int y = anchorY - 12;
        int height = lines.size() * 10 + (lines.size() > 1 ? 2 : 0);

        Minecraft mc = Minecraft.getInstance();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int screenH = mc.getWindow().getGuiScaledHeight();
        if (x + width > screenW) x = Math.max(x - 24 - width, 4);
        int k = height + 3;
        if (y + k > screenH) y = screenH - k;

        pose.pushPose();
        pose.translate(0, 0, 400);

        int bg = 0xF0100010;
        int borderStart = 0x505000FF;
        int borderEnd = (borderStart & 0xFEFEFE) >> 1 | borderStart & 0xFF000000;
        GRAD.fill(pose, x - 3, y - 4, x + width + 3, y - 3, bg, bg);
        GRAD.fill(pose, x - 3, y + height + 3, x + width + 3, y + height + 4, bg, bg);
        GRAD.fill(pose, x - 3, y - 3, x + width + 3, y + height + 3, bg, bg);
        GRAD.fill(pose, x - 4, y - 3, x - 3, y + height + 3, bg, bg);
        GRAD.fill(pose, x + width + 3, y - 3, x + width + 4, y + height + 3, bg, bg);
        GRAD.fill(pose, x - 3, y - 3 + 1, x - 3 + 1, y + height + 3 - 1, borderStart, borderEnd);
        GRAD.fill(pose, x + width + 2, y - 3 + 1, x + width + 3, y + height + 3 - 1, borderStart, borderEnd);
        GRAD.fill(pose, x - 3, y - 3, x + width + 3, y - 3 + 1, borderStart, borderStart);
        GRAD.fill(pose, x - 3, y + height + 2, x + width + 3, y + height + 3, borderEnd, borderEnd);

        int yy = y;
        for (int i = 0; i < lines.size(); i++) {
            TipLine line = lines.get(i);
            int xx = x;
            if (line.icon) {
                RenderSystem.setShader(GameRenderer::getPositionTexShader);
                RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
                RenderSystem.setShaderTexture(0, COMPONENT_TEXTURE);
                GuiComponent.blit(pose, x, yy, (float) (line.source == null ? 9 : 0), 0F, 9, 9, 18, 9);
                if (line.source != null) line.source.render(pose, this.font, x, yy);
                xx = x + 12;
            }
            this.font.drawShadow(pose, line.text, (float) xx, (float) yy, -1);
            yy += (i == 0 && lines.size() > 1) ? 12 : 10;
        }

        pose.popPose();
    }

    /** Expoe o fillGradient (protegido) do GuiComponent. */
    private static class Grad extends GuiComponent {

        void fill(PoseStack pose, int x1, int y1, int x2, int y2, int c1, int c2) {
            this.fillGradient(pose, x1, y1, x2, y2, c1, c2);
        }

    }

    // ---------------------------------------------------------------- linhas da lista

    private void renderEntry(PoseStack pose, AttributeInstance inst, int x, int y, int mouseX, int mouseY) {
        boolean hover = this.getHoveredSlot(mouseX, mouseY) == inst;
        blit(pose, x, y, 142, hover ? ENTRY_HEIGHT : 0, 100, ENTRY_HEIGHT);

        Component txt = new TranslatableComponent(inst.getAttribute().getDescriptionId());
        int splitWidth = 60;
        List<FormattedCharSequence> lines = this.font.split(txt, splitWidth);
        // So cabem duas linhas: forcamos duas linhas e reduzimos a escala se precisar.
        while (lines.size() > 2) {
            splitWidth += 10;
            lines = this.font.split(txt, splitWidth);
        }

        pose.pushPose();
        float scale = 1;
        int maxWidth = lines.stream().map(this.font::width).max(Integer::compareTo).get();
        if (maxWidth > 66) {
            scale = 66F / maxWidth;
            pose.scale(scale, scale, 1);
        }

        for (int i = 0; i < lines.size(); i++) {
            FormattedCharSequence line = lines.get(i);
            float width = this.font.width(line) * scale;
            float lineX = (x + 1 + (68 - width) / 2) / scale;
            float lineY = (y + (lines.size() == 1 ? 7 : 2) + i * 10) / scale;
            this.font.draw(pose, line, lineX, lineY, 0x404040);
        }
        pose.popPose();
        pose.pushPose();

        MutableComponent value = AttributeFormatter.toValueComponent(inst.getAttribute(), null, inst.getValue());

        scale = 1;
        if (this.font.width(value) > 27) {
            scale = 27F / this.font.width(value);
            pose.scale(scale, scale, 1);
        }

        int color = 0xFFFFFF;
        if (inst.getAttribute() instanceof RangedAttribute) {
            if (inst.getValue() > inst.getBaseValue()) {
                color = 0x55DD55;
            }
            else if (inst.getValue() < inst.getBaseValue()) {
                color = 0xFF6060;
            }
        }
        this.font.drawShadow(pose, value, (int) ((x + 72 + (27 - this.font.width(value) * scale) / 2) / scale), (int) ((y + 7) / scale), color);
        pose.popPose();
    }

    // ---------------------------------------------------------------- mouse

    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (this.toggleBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (!this.open) return false;
        if (this.hideUnchangedBtn.mouseClicked(mouseX, mouseY, button)) return true;
        if (!this.isScrollBarActive()) return false;
        this.scrolling = false;
        int left = this.leftPos + 111;
        int top = this.topPos + 15;
        if (mouseX >= left && mouseX < left + 12 && mouseY >= top && mouseY < top + 155) {
            this.scrolling = true;
            int i = this.topPos + 15;
            int j = i + 138;
            scrollOffset = ((float) mouseY - i - 7.5F) / (j - i - 15.0F);
            scrollOffset = Mth.clamp(scrollOffset, 0.0F, 1.0F);
            this.startIndex = (int) (scrollOffset * this.getOffScreenRows() + 0.5D);
            return true;
        }
        return false;
    }

    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (!this.open) return false;
        if (this.scrolling && this.isScrollBarActive()) {
            int i = this.topPos + 15;
            int j = i + 138;
            scrollOffset = ((float) mouseY - i - 7.5F) / (j - i - 15.0F);
            scrollOffset = Mth.clamp(scrollOffset, 0.0F, 1.0F);
            this.startIndex = (int) (scrollOffset * this.getOffScreenRows() + 0.5D);
            return true;
        }
        return false;
    }

    public void mouseReleased() {
        this.scrolling = false;
    }

    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (!this.open) return false;
        if (this.isScrollBarActive()) {
            int i = this.getOffScreenRows();
            scrollOffset = (float) (scrollOffset - delta / i);
            scrollOffset = Mth.clamp(scrollOffset, 0.0F, 1.0F);
            this.startIndex = (int) (scrollOffset * i + 0.5D);
            return true;
        }
        return false;
    }

    private boolean isScrollBarActive() {
        return this.data.size() > MAX_ENTRIES;
    }

    protected int getOffScreenRows() {
        return Math.max(0, this.data.size() - MAX_ENTRIES);
    }

    @Nullable
    public AttributeInstance getHoveredSlot(int mouseX, int mouseY) {
        for (int i = 0; i < MAX_ENTRIES; i++) {
            if (this.startIndex + i < this.data.size()) {
                if (this.isHovering(8, 14 + ENTRY_HEIGHT * i, 100, ENTRY_HEIGHT, mouseX, mouseY)) return this.data.get(this.startIndex + i);
            }
        }
        return null;
    }

    protected boolean isHovering(int pX, int pY, int pWidth, int pHeight, double pMouseX, double pMouseY) {
        int i = this.leftPos;
        int j = this.topPos;
        pMouseX -= i;
        pMouseY -= j;
        return pMouseX >= pX - 1 && pMouseX < pX + pWidth + 1 && pMouseY >= pY - 1 && pMouseY < pY + pHeight + 1;
    }

    // ---------------------------------------------------------------- formula

    private static final DecimalFormat F = ItemStack.ATTRIBUTE_MODIFIER_FORMAT;

    /**
     * Monta o texto com a formula matematica do calculo do atributo.
     *
     * @param value         Componente do valor final.
     * @param base          Componente do valor base.
     * @param numericValues Totais dos modificadores na ordem das operacoes (add, mulBase, mulTotal).
     */
    public static Component buildFormula(Component value, Component base, double[] numericValues) {
        double add = numericValues[0];
        double mulBase = numericValues[1];
        double mulTotal = numericValues[2];

        boolean isAddNeg = add < 0;
        boolean isMulNeg = mulBase < 0;

        String addSym = isAddNeg ? "-" : "+";
        add = Math.abs(add);

        String mulBaseSym = isMulNeg ? "-" : "+";
        mulBase = Math.abs(mulBase);

        String addStr = F.format(add);
        String mulBaseStr = F.format(mulBase);
        String mulTotalStr = F.format(mulTotal);

        String formula = "%2$s";

        if (add != 0) {
            ChatFormatting color = isAddNeg ? ChatFormatting.RED : ChatFormatting.YELLOW;
            formula = formula + " " + colored(addSym + " " + addStr, color);
        }

        if (mulBase != 0) {
            String withParens = add == 0 ? formula : "(%s)".formatted(formula);
            ChatFormatting color = isMulNeg ? ChatFormatting.RED : ChatFormatting.YELLOW;
            formula = withParens + " " + colored(mulBaseSym + " " + mulBaseStr + " * ", color) + withParens;
        }

        if (mulTotal != 1) {
            String withParens = add == 0 && mulBase == 0 ? formula : "(%s)".formatted(formula);
            ChatFormatting color = mulTotal < 1 ? ChatFormatting.RED : ChatFormatting.YELLOW;
            formula = colored(mulTotalStr + " * ", color) + withParens;
        }

        return new TranslatableComponent("%1$s = " + formula, value, base).withStyle(ChatFormatting.GRAY);
    }

    /** Colore um texto usando codigos de formatacao legados e termina com RESET. */
    private static String colored(String str, ChatFormatting color) {
        return "" + ChatFormatting.PREFIX_CODE + color.getChar() + str + ChatFormatting.PREFIX_CODE + ChatFormatting.RESET.getChar();
    }

    // ---------------------------------------------------------------- botao "ocultar inalterados"

    public class HideUnchangedButton extends ImageButton {

        public HideUnchangedButton(int x, int y) {
            super(x, y, 10, 10, 131, 20, 10, TEXTURES, 256, 256, btn -> {}, new TextComponent("Hide Unchanged Attributes"));
            this.visible = false;
        }

        @Override
        public void onPress() {
            hideUnchanged = !hideUnchanged;
        }

        @Override
        public void renderButton(PoseStack pose, int mouseX, int mouseY, float partialTick) {
            int u = 131, v = 20;
            int vOffset = hideUnchanged ? 0 : 10;
            if (this.isHovered) {
                vOffset += 20;
            }
            RenderSystem.enableDepthTest();
            pose.pushPose();
            pose.translate(0, 0, 100);
            blit(pose, this.x, this.y, u, v + vOffset, 10, 10);
            pose.popPose();
        }

    }

}
