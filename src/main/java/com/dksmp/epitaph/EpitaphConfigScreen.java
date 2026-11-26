package com.dksmp.epitaph;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/** Экран настроек, открывается из Mod Menu (кнопка настроек у мода). */
public class EpitaphConfigScreen extends Screen {

    private final Screen parent;

    public EpitaphConfigScreen(Screen parent) {
        super(Text.literal("Epitaph (King Crimson)"));
        this.parent = parent;
    }

    @Override
    protected void init() {
        int x = this.width / 2 - 100;
        int y = this.height / 4;

        this.addDrawableChild(new ConfigSlider(x, y, 200, "Дальность рывка (блоков)",
                1, 15, EpitaphConfig.teleportDistance, v -> EpitaphConfig.teleportDistance = v));
        this.addDrawableChild(new ConfigSlider(x, y + 28, 200, "Длительность Эпитафии (сек)",
                1, 10, EpitaphConfig.durationSeconds, v -> EpitaphConfig.durationSeconds = v));
        this.addDrawableChild(new ConfigSlider(x, y + 56, 200, "Радиус обводки (блоков)",
                8, 64, EpitaphConfig.outlineRadius, v -> EpitaphConfig.outlineRadius = v));

        this.addDrawableChild(ButtonWidget.builder(Text.literal("Готово"), button -> this.close())
                .dimensions(x, this.height - 40, 200, 20)
                .build());
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float delta) {
        super.render(context, mouseX, mouseY, delta);
        context.drawCenteredTextWithShadow(this.textRenderer, this.title, this.width / 2, 20, 0xFFFFFF);
    }

    @Override
    public void close() {
        EpitaphConfig.save();
        this.client.setScreen(this.parent);
    }

    /** Ползунок с целыми значениями. */
    private static class ConfigSlider extends SliderWidget {
        private final String label;
        private final int min;
        private final int max;
        private final IntConsumer setter;

        ConfigSlider(int x, int y, int width, String label, int min, int max, int current, IntConsumer setter) {
            super(x, y, width, 20, Text.empty(), (current - min) / (double) (max - min));
            this.label = label;
            this.min = min;
            this.max = max;
            this.setter = setter;
            this.updateMessage();
        }

        private int intValue() {
            return this.min + (int) Math.round(this.value * (this.max - this.min));
        }

        @Override
        protected void updateMessage() {
            this.setMessage(Text.literal(this.label + ": " + this.intValue()));
        }

        @Override
        protected void applyValue() {
            this.setter.accept(this.intValue());
        }
    }
}
