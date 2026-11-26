package com.dksmp.epitaph;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class EpitaphClient implements ClientModInitializer {

    private static final Identifier EFFECT_TEXTURE =
            Identifier.of("epitaph", "textures/gui/epitaph_effect.png");

    private static KeyBinding toggleKey;

    @Override
    public void onInitializeClient() {
        // Кнопка по умолчанию: V (меняется в настройках управления, категория King Crimson)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.epitaph.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.epitaph"));

        ClientTickEvents.END_CLIENT_TICK.register(EpitaphClient::onTick);
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> onHud(drawContext));
    }

    // ===================== ЛОГИКА =====================

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            // Вышли из мира: просто всё сбрасываем (пакеты не отправляем)
            EpitaphState.active = false;
            EpitaphState.queue.clear();
            EpitaphState.fade = 0f;
            EpitaphState.flashTick = -1;
            return;
        }

        while (toggleKey.wasPressed()) {
            if (EpitaphState.active) {
                stop(client);
            } else {
                start(client);
            }
        }

        if (EpitaphState.active) {
            EpitaphState.ticks++;
            if (EpitaphState.ticks >= EpitaphState.MAX_TICKS) {
                stop(client); // время вышло: автоматический телепорт
            }
            EpitaphState.fade = Math.min(1f, EpitaphState.fade + 0.12f);
        } else {
            EpitaphState.fade = Math.max(0f, EpitaphState.fade - 0.12f);
        }

        if (EpitaphState.flashTick >= 0) {
            EpitaphState.flashTick++;
            if (EpitaphState.flashTick >= EpitaphState.FLASH_TICKS) {
                EpitaphState.flashTick = -1;
            }
        }
    }

    private static void start(MinecraftClient client) {
        EpitaphState.active = true;
        EpitaphState.ticks = 0;
        EpitaphState.flashTick = 0;
        EpitaphState.queue.clear();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f));
    }

    private static void stop(MinecraftClient client) {
        // Сначала выключаем Blink, потом отправляем всё накопленное по порядку
        EpitaphState.active = false;
        EpitaphState.flashTick = 0;
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler != null) {
            for (Packet<?> packet : EpitaphState.queue) {
                handler.sendPacket(packet);
            }
        }
        EpitaphState.queue.clear();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.2f));
    }

    // ===================== ОТРИСОВКА =====================

    private static void onHud(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // 1) Лёгкий глитч по краям экрана (и затемнение, если включено в настройках)
        if (EpitaphState.fade > 0f) {
            if (EpitaphState.DARKNESS > 0f) {
                int a = (int) (EpitaphState.fade * EpitaphState.DARKNESS * 255f);
                ctx.fill(0, 0, w, h, a << 24);
            }
            drawEdgeGlitch(ctx, w, h);
        }

        // 2) Картинка-эффект (космос и розовые полосы) при включении и при телепорте
        // (красная обводка сущностей рисуется самой игрой, см. mixin)
        if (EpitaphState.flashTick >= 0) {
            drawEffectImage(ctx, w, h, EpitaphState.flashTick / (float) EpitaphState.FLASH_TICKS);
        }
    }

    /** Слабый глитч по краям: розовая подсветка и короткие мерцающие полоски. */
    private static void drawEdgeGlitch(DrawContext ctx, int w, int h) {
        float strength = EpitaphState.fade;

        if (EpitaphState.EDGE_TINT > 0f) {
            int strips = 8;
            int stripW = Math.max(2, w / 80);
            for (int i = 0; i < strips; i++) {
                float k = 1f - i / (float) strips;
                int a = (int) (EpitaphState.EDGE_TINT * k * strength * 255f);
                int c = (a << 24) | 0xE0207A;
                ctx.fill(i * stripW, 0, (i + 1) * stripW, h, c);
                ctx.fill(w - (i + 1) * stripW, 0, w - i * stripW, h, c);
            }
        }

        // Полоски меняются примерно 15 раз в секунду
        Random rnd = new Random((System.nanoTime() / 65_000_000L) * 7919L + EpitaphState.ticks);
        int[] palette = {0xFF2A6A, 0xFF9AF0, 0x40E0FF, 0xE01030};
        for (int i = 0; i < EpitaphState.GLITCH_BARS; i++) {
            boolean left = rnd.nextBoolean();
            int barH = 1 + rnd.nextInt(3);
            int barW = 6 + rnd.nextInt(Math.max(1, w / 12));
            int y = rnd.nextInt(Math.max(1, h));
            int x = left ? 0 : w - barW;
            int a = (int) ((0.25f + rnd.nextFloat() * 0.30f) * strength * 255f);
            ctx.fill(x, y, x + barW, y + barH, (a << 24) | palette[rnd.nextInt(palette.length)]);
        }
    }

    /** Картинка-эффект на весь экран: быстро появляется, потом плавно гаснет. */
    private static void drawEffectImage(DrawContext ctx, int w, int h, float t) {
        float alpha = t < 0.2f ? t / 0.2f : 1f - (t - 0.2f) / 0.8f;
        alpha = Math.max(0f, Math.min(1f, alpha)) * EpitaphState.FLASH_ALPHA;
        if (alpha <= 0f) return;

        // Лёгкая тряска по горизонтали для динамики
        int shake = (int) (Math.sin(EpitaphState.flashTick * 2.3f) * 10f);

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        ctx.drawTexture(EFFECT_TEXTURE, -20 + shake, 0, w + 40, h, 0f, 0f, 512, 288, 512, 288);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        RenderSystem.disableBlend();
    }
}
