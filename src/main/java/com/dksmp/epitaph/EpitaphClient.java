package com.dksmp.epitaph;

import com.mojang.blaze3d.systems.RenderSystem;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Box;
import org.lwjgl.glfw.GLFW;

import java.util.Random;

public class EpitaphClient implements ClientModInitializer {

    private static final Identifier EFFECT_TEXTURE =
            Identifier.of("epitaph", "textures/gui/epitaph_effect.png");

    private static KeyBinding toggleKey;
    private static KeyBinding dodgeKey;

    @Override
    public void onInitializeClient() {
        EpitaphConfig.load();

        // V: Эпитафия, Shift+V: рывок (меняется в настройках управления, категория King Crimson)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.epitaph.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.epitaph"));
        // Правый Shift: включить / выключить уворот
        dodgeKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.epitaph.dodge", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, "category.epitaph"));

        ClientTickEvents.END_CLIENT_TICK.register(EpitaphClient::onTick);
        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> onHud(drawContext));
        EyeRenderer.register();
    }

    // ===================== ЛОГИКА =====================

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            // Вышли из мира: просто всё сбрасываем (пакеты не отправляем)
            EpitaphState.active = false;
            EpitaphState.queue.clear();
            EpitaphState.fade = 0f;
            EpitaphState.flashTick = -1;
            EpitaphState.dodgeArmed = false;
            EpitaphState.dodgeFade = 0f;
            EpitaphState.lastHurtTime = 0;
            return;
        }

        if (EpitaphState.teleportCooldown > 0) {
            EpitaphState.teleportCooldown--;
        }

        while (toggleKey.wasPressed()) {
            if (isShiftDown(client)) {
                dash(client);          // Shift + V: рывок
            } else if (EpitaphState.active) {
                stop(client);          // V: телепорт обратно (конец Эпитафии)
            } else {
                start(client);         // V: старт Эпитафии
            }
        }

        while (dodgeKey.wasPressed()) {
            Dodge.toggle(client);      // Правый Shift: уворот вкл / выкл
        }
        Dodge.tick(client);

        if (EpitaphState.active) {
            EpitaphState.ticks++;
            if (EpitaphState.ticks >= EpitaphConfig.durationSeconds * 20) {
                stop(client); // время вышло: автоматический телепорт
            }
            EpitaphState.fade = Math.min(1f, EpitaphState.fade + 0.12f);
        } else {
            EpitaphState.fade = Math.max(0f, EpitaphState.fade - 0.12f);
        }

        if (EpitaphState.dodgeArmed) {
            EpitaphState.dodgeFade = Math.min(1f, EpitaphState.dodgeFade + 0.12f);
        } else {
            EpitaphState.dodgeFade = Math.max(0f, EpitaphState.dodgeFade - 0.12f);
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

    private static boolean isShiftDown(MinecraftClient client) {
        long window = client.getWindow().getHandle();
        return InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_LEFT_SHIFT)
                || InputUtil.isKeyPressed(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    /**
     * Рывок: мгновенно переносит тебя вперёд (по направлению взгляда, по горизонтали)
     * на заданную дальность. Если впереди стена, останавливается перед ней.
     * Сервер получает несколько коротких шагов, а не один большой прыжок, поэтому не откатывает.
     */
    private static void dash(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;
        if (EpitaphState.teleportCooldown > 0) return;

        double rad = Math.toRadians(player.getYaw());
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);

        // Ищем самую дальнюю свободную точку на пути (шаг 0.5 блока)
        Box box = player.getBoundingBox();
        double free = 0.0;
        for (double d = 0.5; d <= EpitaphConfig.teleportDistance + 1e-6; d += 0.5) {
            if (client.world.isSpaceEmpty(player, box.offset(dx * d, 0.0, dz * d))) {
                free = d;
            } else {
                break;
            }
        }
        if (free < 1.0) return; // впереди стена вплотную

        double sx = player.getX();
        double sy = player.getY();
        double sz = player.getZ();

        // Сообщаем серверу маршрут короткими шагами (не больше 4 блоков за шаг)
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        int steps = (int) Math.ceil(free / 4.0);
        if (handler != null) {
            for (int i = 1; i <= steps; i++) {
                double d = free * i / steps;
                handler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                        sx + dx * d, sy, sz + dz * d, player.isOnGround()));
            }
        }
        player.setPosition(sx + dx * free, sy, sz + dz * free);

        // Тот же эффект-картинка, но без глитча по краям (глитч только во время Эпитафии)
        EpitaphState.flashTick = 0;
        EpitaphState.teleportCooldown = EpitaphState.TELEPORT_COOLDOWN;
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.0f));
    }

    // ===================== ОТРИСОВКА =====================

    private static void onHud(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // 1) Лёгкий глитч по краям экрана: во время Эпитафии (сильнее) и во время уворота (слабее)
        float strength = Math.max(EpitaphState.fade, EpitaphState.dodgeFade * 0.7f);
        if (strength > 0f) {
            if (EpitaphState.DARKNESS > 0f && EpitaphState.fade > 0f) {
                int a = (int) (EpitaphState.fade * EpitaphState.DARKNESS * 255f);
                ctx.fill(0, 0, w, h, a << 24);
            }
            drawEdgeGlitch(ctx, w, h, strength);
        }

        // 2) Счётчик уворота
        if (EpitaphState.dodgeArmed) {
            int seconds = (EpitaphState.dodgeTicksLeft + 19) / 20;
            ctx.drawCenteredTextWithShadow(client.textRenderer,
                    Text.literal("Уворот: " + EpitaphState.dodgeCharges + "  |  " + seconds + " с"),
                    w / 2, h - 62, 0xFF9AF0);
        }

        // 3) Картинка-эффект (космос и розовые полосы) при включении, телепорте, рывке и увороте
        // (красную обводку сущностей рисует сама игра, см. mixin)
        if (EpitaphState.flashTick >= 0) {
            drawEffectImage(ctx, w, h, EpitaphState.flashTick / (float) EpitaphState.FLASH_TICKS);
        }
    }

    /** Слабый глитч по краям: розовая подсветка и короткие мерцающие полоски. */
    private static void drawEdgeGlitch(DrawContext ctx, int w, int h, float strength) {
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
