package com.dksmp.epitaph;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Простые настройки мода. Файл: папка config/epitaph.json */
public final class EpitaphConfig {

    /** Дальность рывка в блоках (1..15). */
    public static int teleportDistance = 10;
    /** Максимальная длительность Эпитафии в секундах (1..10). */
    public static int durationSeconds = 6;
    /** Радиус красной обводки в блоках (8..64). */
    public static int outlineRadius = 32;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private static class Data {
        int teleportDistance = 10;
        int durationSeconds = 6;
        int outlineRadius = 32;
    }

    private static Path path() {
        return FabricLoader.getInstance().getConfigDir().resolve("epitaph.json");
    }

    public static void load() {
        Path p = path();
        if (!Files.exists(p)) {
            save();
            return;
        }
        try {
            Data d = GSON.fromJson(Files.readString(p), Data.class);
            if (d != null) {
                teleportDistance = clamp(d.teleportDistance, 1, 15);
                durationSeconds = clamp(d.durationSeconds, 1, 10);
                outlineRadius = clamp(d.outlineRadius, 8, 64);
            }
        } catch (Exception e) {
            // Битый файл настроек: остаёмся на значениях по умолчанию
        }
    }

    public static void save() {
        Data d = new Data();
        d.teleportDistance = teleportDistance;
        d.durationSeconds = durationSeconds;
        d.outlineRadius = outlineRadius;
        try {
            Files.writeString(path(), GSON.toJson(d));
        } catch (IOException e) {
            // Не получилось сохранить: не страшно
        }
    }

    private static int clamp(int v, int min, int max) {
        return Math.max(min, Math.min(max, v));
    }

    private EpitaphConfig() {}
}
