package com.dksmp.epitaph.mixin;

import com.dksmp.epitaph.EpitaphState;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Включает обычную "свечение"-обводку игры для нужных сущностей (видна сквозь стены). */
@Mixin(MinecraftClient.class)
public class MinecraftClientMixin {

    @Inject(method = "hasOutline", at = @At("RETURN"), cancellable = true)
    private void epitaph$outline(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        if (!cir.getReturnValue() && EpitaphState.shouldOutline(entity)) {
            cir.setReturnValue(true);
        }
    }
}
