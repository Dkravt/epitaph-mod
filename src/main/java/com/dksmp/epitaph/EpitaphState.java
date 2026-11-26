package com.dksmp.epitaph;

import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.network.packet.Packet;

import java.util.ArrayList;
import java.util.List;

public final class EpitaphState {

    // ================= НАСТРОЙКИ (меняй тут) =================
    /** Максимальная длительность Epitaph в тиках (20 тиков = 1 секунда). */
    public static final int MAX_TICKS = 120;
    /** В каком радиусе (в блоках) у игроков и мобов появляется красная обводка. */
    public static final double RADIUS = 32.0;
    /** Цвет обводки (RGB). */
    public static final int OUTLINE_COLOR = 0xFF2850;
    /** Длительность картинки-эффекта при включении и при телепорте (тики). */
    public static final int FLASH_TICKS = 16;
    /** Максимальная яркость картинки-эффекта (0..1). */
    public static final float FLASH_ALPHA = 0.95f;
    /** Количество глитч-полосок по краям экрана. */
    public static final int GLITCH_BARS = 14;
    /** Сила розовой подсветки по краям экрана (0 = выключить). */
    public static final float EDGE_TINT = 0.28f;
    /** Затемнение экрана (0 = выключено, 1 = полностью чёрный). */
    public static final float DARKNESS = 0.0f;
    // =========================================================

    public static boolean active = false;
    public static int ticks = 0;
    /** Плавное появление эффектов по краям 0..1. */
    public static float fade = 0f;
    /** Тик картинки-эффекта: -1 = не показываем. */
    public static int flashTick = -1;
    /** Задержанные пакеты движения (Blink). */
    public static final List<Packet<?>> queue = new ArrayList<>();

    /** Нужна ли этой сущности красная обводка прямо сейчас. */
    public static boolean shouldOutline(Entity entity) {
        if (!active) return false;
        MinecraftClient client = MinecraftClient.getInstance();
        if (client.player == null || entity == client.player) return false;
        if (!(entity instanceof LivingEntity) || entity instanceof ArmorStandEntity) return false;
        return entity.squaredDistanceTo(client.player) <= RADIUS * RADIUS;
    }

    private EpitaphState() {}
}
