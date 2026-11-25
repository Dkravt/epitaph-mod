package com.dksmp.epitaph;

import net.minecraft.network.packet.Packet;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class EpitaphState {

    // ================= НАСТРОЙКИ (меняй тут) =================
    /** Максимальная длительность Epitaph в тиках (20 тиков = 1 секунда). */
    public static final int MAX_TICKS = 120;
    /** В каком радиусе (в блоках) видны красные силуэты игроков и мобов. */
    public static final double RADIUS = 32.0;
    /** Длительность пиксельной вспышки при включении (тики). */
    public static final int FLASH_TICKS = 14;
    /** Сколько "следов" (афтерименджей) у каждого силуэта. */
    public static final int TRAIL_LENGTH = 6;
    /** Как часто запоминается позиция для следа (в тиках). */
    public static final int TRAIL_STEP = 2;
    /** Насколько чёрный экран (0 = не тёмный, 1 = полностью чёрный). */
    public static final float DARKNESS = 0.93f;
    // =========================================================

    /** Один "призрак": где стоял моб/игрок и его размеры. */
    public record Ghost(Vec3d pos, float width, float height) {}

    public static boolean active = false;
    public static int ticks = 0;
    /** Плавное затемнение экрана 0..1. */
    public static float overlay = 0f;
    /** Где тебя "видит" сервер (точка, где ты нажал кнопку). */
    public static Vec3d startPos = Vec3d.ZERO;
    /** Задержанные пакеты движения (Blink). */
    public static final List<Packet<?>> queue = new ArrayList<>();
    /** Следы мобов/игроков: id сущности -> последние позиции. */
    public static final Map<Integer, Deque<Ghost>> trails = new HashMap<>();

    private EpitaphState() {}
}
