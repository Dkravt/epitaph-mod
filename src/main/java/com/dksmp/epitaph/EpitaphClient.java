package com.dksmp.epitaph;

import com.mojang.blaze3d.systems.RenderSystem;
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
import net.minecraft.util.Identifier;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Random;
import java.util.Set;

public class EpitaphClient implements ClientModInitializer {

    private static final Identifier EFFECT_TEXTURE =
            Identifier.of("epitaph", "textures/gui/epitaph_effect.png");

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
            } else if (EpitaphState.ticks % EpitaphState.TRAIL_STEP == 0) {
                updateTrails(client);
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
        EpitaphState.trails.clear();
        EpitaphState.startPos = client.player.getPos();
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 0.6f));
        updateTrails(client);
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

        // 1) Лёгкий глитч по краям экрана (и затемнение, если включено в настройках)
        if (EpitaphState.fade > 0f) {
            if (EpitaphState.DARKNESS > 0f) {
                int a = (int) (EpitaphState.fade * EpitaphState.DARKNESS * 255f);
                ctx.fill(0, 0, w, h, a << 24);
            }
            drawEdgeGlitch(ctx, w, h);
        }

        // 2) Красные силуэты (афтерименджи)
        if (EpitaphState.active) {
            drawGhost(ctx, new EpitaphState.Ghost(EpitaphState.startPos, 0.6f, 1.8f), 1f, w, h);

            for (Deque<EpitaphState.Ghost> trail : EpitaphState.trails.values()) {
                // Рисуем от старых к новым. Самую свежую позицию (index 0) пропускаем,
                // чтобы красный след был именно "позади" настоящего моба.
                Iterator<EpitaphState.Ghost> it = trail.descendingIterator();
                int index = trail.size() - 1;
                while (it.hasNext()) {
                    EpitaphState.Ghost g = it.next();
                    if (index >= 1) {
                        float fade = 1f - (index - 1) / (float) EpitaphState.TRAIL_LENGTH;
                        drawGhost(ctx, g, fade, w, h);
                    }
                    index--;
                }
            }
        }

        // 3) Картинка-эффект (космос и розовые полосы) при включении и при телепорте
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

    /** Красный силуэт-афтеримидж: контур + лёгкая заливка. Людей рисуем как фигуру, остальных как коробку. */
    private static void drawGhost(DrawContext ctx, EpitaphState.Ghost g, float fade, int w, int h) {
        float[] feet = project(g.pos(), w, h);
        float[] head = project(g.pos().add(0, g.height(), 0), w, h);
        if (feet == null || head == null) return;

        float height = feet[1] - head[1];
        if (height < 6f) return;

        float f = Math.max(0f, Math.min(1f, fade));
        int fill = ((int) (f * EpitaphState.GHOST_FILL * 255f) << 24) | 0xE01030;
        int line = ((int) (f * EpitaphState.GHOST_LINE * 255f) << 24) | 0xFF2850;

        float cx = feet[0];
        float top = head[1];

        if (g.height() / g.width() >= 2.4f) {
            // Фигура как у игрока/зомби/скелета (пропорции модели 8-4-12 пикселей)
            float u = height / 32f;
            part(ctx, cx, top, -4, 0, 4, 8, u, fill, line);     // голова
            part(ctx, cx, top, -4, 8, 4, 20, u, fill, line);    // тело
            part(ctx, cx, top, -8, 8, -4, 20, u, fill, line);   // левая рука
            part(ctx, cx, top, 4, 8, 8, 20, u, fill, line);     // правая рука
            part(ctx, cx, top, -4, 20, 0, 32, u, fill, line);   // левая нога
            part(ctx, cx, top, 0, 20, 4, 32, u, fill, line);    // правая нога
        } else {
            float halfW = Math.max(4f, height * (g.width() / g.height()) / 2f);
            box(ctx, Math.round(cx - halfW), Math.round(top),
                    Math.round(cx + halfW), Math.round(feet[1]), fill, line);
        }
    }

    private static void part(DrawContext ctx, float cx, float top,
                             float x1, float y1, float x2, float y2, float u, int fill, int line) {
        box(ctx, Math.round(cx + x1 * u), Math.round(top + y1 * u),
                Math.round(cx + x2 * u), Math.round(top + y2 * u), fill, line);
    }

    /** Прямоугольник с тонким контуром. */
    private static void box(DrawContext ctx, int x1, int y1, int x2, int y2, int fill, int line) {
        if (x2 - x1 < 3 || y2 - y1 < 3) return;
        ctx.fill(x1, y1, x2, y2, fill);
        ctx.fill(x1, y1, x2, y1 + 1, line);
        ctx.fill(x1, y2 - 1, x2, y2, line);
        ctx.fill(x1, y1 + 1, x1 + 1, y2 - 1, line);
        ctx.fill(x2 - 1, y1 + 1, x2, y2 - 1, line);
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
