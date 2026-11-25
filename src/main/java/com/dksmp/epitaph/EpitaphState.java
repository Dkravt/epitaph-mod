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
    /** Длительность картинки-эффекта при включении и при телепорте (тики). */
    public static final int FLASH_TICKS = 16;
    /** Максимальная яркость картинки-эффекта (0..1). */
    public static final float FLASH_ALPHA = 0.95f;
    /** Сколько "следов" (афтерименджей) у каждого силуэта. */
    public static final int TRAIL_LENGTH = 6;
    /** Как часто запоминается позиция для следа (в тиках). */
    public static final int TRAIL_STEP = 2;
    /** Прозрачность заливки красного силуэта (0..1). */
    public static final float GHOST_FILL = 0.22f;
    /** Яркость контура красного силуэта (0..1). */
    public static final float GHOST_LINE = 0.85f;
    /** Количество глитч-полосок по краям экрана. */
    public static final int GLITCH_BARS = 14;
    /** Сила розовой подсветки по краям экрана (0 = выключить). */
    public static final float EDGE_TINT = 0.28f;
    /** Затемнение экрана (0 = выключено, 1 = полностью чёрный). */
    public static final float DARKNESS = 0.0f;
    // =========================================================

    /** Один "призрак": где стоял моб/игрок и его размеры. */
    public record Ghost(Vec3d pos, float width, float height) {}

    public static boolean active = false;
    public static int ticks = 0;
    /** Плавное появление эффектов по краям 0..1. */
    public static float fade = 0f;
    /** Тик картинки-эффекта: -1 = не показываем. */
    public static int flashTick = -1;
    /** Где тебя "видит" сервер (точка, где ты нажал кнопку). */
    public static Vec3d startPos = Vec3d.ZERO;
    /** Задержанные пакеты движения (Blink). */
    public static final List<Packet<?>> queue = new ArrayList<>();
    /** Следы мобов/игроков: id сущности -> последние позиции. */
    public static final Map<Integer, Deque<Ghost>> trails = new HashMap<>();

    private EpitaphState() {}
}
