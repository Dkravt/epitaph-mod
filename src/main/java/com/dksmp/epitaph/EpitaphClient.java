package com.dksmp.epitaph;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.packet.Packet;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class EpitaphClient implements ClientModInitializer {

    private static KeyBinding toggleKey;

    // Матрицы камеры из последнего кадра мира (нужны, чтобы нарисовать силуэты на экране)
    private static Matrix4f viewMatrix = new Matrix4f();
    private static Matrix4f projMatrix = new Matrix4f();
    private static Vec3d camPos = Vec3d.ZERO;

    @Override
    public void onInitializeClient() {
        // Кнопка по умолчанию: V (меняется в настройках управления, категория King Crimson)
        toggleKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.epitaph.toggle", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_V, "category.epitaph"));

        ClientTickEvents.END_CLIENT_TICK.register(EpitaphClient::onTick);

        WorldRenderEvents.LAST.register(context -> {
            if (context.matrixStack() != null) {
                viewMatrix = new Matrix4f(context.matrixStack().peek().getPositionMatrix());
            }
            projMatrix = new Matrix4f(context.projectionMatrix());
            camPos = context.camera().getPos();
        });

        HudRenderCallback.EVENT.register((drawContext, tickCounter) -> onHud(drawContext));
    }

    // ===================== ЛОГИКА =====================

    private static void onTick(MinecraftClient client) {
        if (client.player == null || client.world == null) {
            // Вышли из мира: просто всё сбрасываем (пакеты не отправляем)
            EpitaphState.active = false;
            EpitaphState.queue.clear();
            EpitaphState.trails.clear();
            EpitaphState.overlay = 0f;
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
            } else if (EpitaphState.ticks % EpitaphState.TRAIL_STEP == 0) {
                updateTrails(client);
            }
            EpitaphState.overlay = Math.min(1f, EpitaphState.overlay + 0.12f);
        } else {
            EpitaphState.overlay = Math.max(0f, EpitaphState.overlay - 0.12f);
        }
    }

    private static void start(MinecraftClient client) {
        EpitaphState.active = true;
        EpitaphState.ticks = 0;
        EpitaphState.queue.clear();
        EpitaphState.trails.clear();
        EpitaphState.startPos = client.player.getPos();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f));
        updateTrails(client);
    }

    private static void stop(MinecraftClient client) {
        // Сначала выключаем Blink, потом отправляем всё накопленное по порядку
        EpitaphState.active = false;
        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler != null) {
            for (Packet<?> packet : EpitaphState.queue) {
                handler.sendPacket(packet);
            }
        }
        EpitaphState.queue.clear();
        EpitaphState.trails.clear();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.2f));
    }

    private static void updateTrails(MinecraftClient client) {
        Set<Integer> seen = new HashSet<>();
        double maxSq = EpitaphState.RADIUS * EpitaphState.RADIUS;

        for (Entity e : client.world.getEntities()) {
            if (e == client.player) continue;
            if (!(e instanceof LivingEntity)) continue;
            if (e instanceof ArmorStandEntity) continue;
            if (e.squaredDistanceTo(client.player) > maxSq) continue;

            seen.add(e.getId());
            Deque<EpitaphState.Ghost> trail =
                    EpitaphState.trails.computeIfAbsent(e.getId(), k -> new ArrayDeque<>());
            trail.addFirst(new EpitaphState.Ghost(e.getPos(), e.getWidth(), e.getHeight()));
            while (trail.size() > EpitaphState.TRAIL_LENGTH) {
                trail.removeLast();
            }
        }
        EpitaphState.trails.keySet().retainAll(seen);
    }

    // ===================== ОТРИСОВКА =====================

    private static void onHud(DrawContext ctx) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null) return;

        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();

        // 1) Чёрное затемнение
        if (EpitaphState.overlay > 0f) {
            int a = (int) (EpitaphState.overlay * EpitaphState.DARKNESS * 255f);
            ctx.fill(0, 0, w, h, a << 24);
        }

        // 2) Красные силуэты (афтерименджи)
        if (EpitaphState.active) {
            // Где тебя видит сервер (твой "призрак")
            drawGhost(ctx, new EpitaphState.Ghost(EpitaphState.startPos, 0.6f, 1.8f), 0.85f, w, h);

            for (Deque<EpitaphState.Ghost> trail : EpitaphState.trails.values()) {
                // Рисуем от старых к новым, чтобы свежий был сверху
                Iterator<EpitaphState.Ghost> it = trail.descendingIterator();
                int total = trail.size();
                int index = total - 1;
                while (it.hasNext()) {
                    EpitaphState.Ghost g = it.next();
                    float alpha = 0.85f * (1f - index / (float) EpitaphState.TRAIL_LENGTH);
                    drawGhost(ctx, g, alpha, w, h);
                    index--;
                }
            }
        }

        // 3) Пиксельная вспышка (как фон на твоём 3-м варианте)
        if (EpitaphState.active && EpitaphState.ticks < EpitaphState.FLASH_TICKS) {
            drawFlash(ctx, w, h, EpitaphState.ticks / (float) EpitaphState.FLASH_TICKS);
        }
    }

    /** Красный силуэт: голова + тело. */
    private static void drawGhost(DrawContext ctx, EpitaphState.Ghost g, float alpha, int w, int h) {
        float[] feet = project(g.pos(), w, h);
        float[] head = project(g.pos().add(0, g.height(), 0), w, h);
        if (feet == null || head == null) return;

        float height = feet[1] - head[1];
        if (height < 3f) return;
        float width = Math.max(3f, height * (g.width() / g.height()));

        int a = (int) (Math.max(0f, Math.min(1f, alpha)) * 255f);
        int color = (a << 24) | 0xE01030;

        int x1 = Math.round(feet[0] - width / 2f);
        int x2 = Math.round(feet[0] + width / 2f);
        int yTop = Math.round(head[1]);
        int yBot = Math.round(feet[1]);
        int headH = Math.max(2, Math.round(height * 0.22f));
        int pad = Math.round(width * 0.2f);

        ctx.fill(x1 + pad, yTop, x2 - pad, yTop + headH, color); // голова
        ctx.fill(x1, yTop + headH, x2, yBot, color);             // тело
    }

    /** Точка в мире -> координаты на экране (или null, если за спиной). */
    private static float[] project(Vec3d pos, int w, int h) {
        Vector4f v = new Vector4f(
                (float) (pos.x - camPos.x),
                (float) (pos.y - camPos.y),
                (float) (pos.z - camPos.z),
                1f);
        v.mul(viewMatrix);
        v.mul(projMatrix);
        if (v.w <= 0.01f) return null;
        float nx = v.x / v.w;
        float ny = v.y / v.w;
        return new float[]{(nx * 0.5f + 0.5f) * w, (1f - (ny * 0.5f + 0.5f)) * h};
    }

    /** Пиксельный розово-фиолетовый круг, который расширяется от центра и гаснет. */
    private static void drawFlash(DrawContext ctx, int w, int h, float t) {
        float maxR = (float) Math.hypot(w, h) / 2f;
        float r = maxR * Math.min(1f, t * 1.6f);
        float alpha = t < 0.5f ? 0.95f : 0.95f * (1f - (t - 0.5f) * 2f);
        if (alpha <= 0f) return;
        int a = (int) (alpha * 255f);

        int cell = Math.max(8, w / 48);
        int cx = w / 2;
        int cy = h / 2;
        int n = (int) (r / cell) + 1;

        for (int gy = -n; gy <= n; gy++) {
            for (int gx = -n; gx <= n; gx++) {
                float d = (float) Math.sqrt(gx * gx + gy * gy) * cell;
                if (d > r) continue;

                float k = Math.min(1f, d / maxR);
                float band = (float) Math.floor(k * 6f) / 6f; // ступеньки = пиксельный вид
                int red = (int) (255 - (255 - 35) * band);
                int green = (int) (90 - 90 * band);
                int blue = (int) (225 - (225 - 40) * band);
                int color = (a << 24) | (red << 16) | (green << 8) | blue;

                int x1 = cx + gx * cell - cell / 2;
                int y1 = cy + gy * cell - cell / 2;
                ctx.fill(x1, y1, x1 + cell, y1 + cell, color);
            }
        }
    }
}
