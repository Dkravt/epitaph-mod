package com.dksmp.epitaph;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.sound.SoundEvent;
import net.minecraft.util.Identifier;

/** Свои звуки мода (файлы лежат в assets/epitaph/sounds, описаны в sounds.json). */
public final class EpitaphSounds {

    /** Эпитафия: старт и конец (dolphin-crimson). */
    public static final SoundEvent EPITAPH = SoundEvent.of(Identifier.of("epitaph", "epitaph"));
    /** Рывок (timeskip). */
    public static final SoundEvent DASH = SoundEvent.of(Identifier.of("epitaph", "dash"));

    public static void play(MinecraftClient client, SoundEvent sound) {
        client.getSoundManager().play(PositionedSoundInstance.master(sound, 1.0f));
    }

    private EpitaphSounds() {}
}
