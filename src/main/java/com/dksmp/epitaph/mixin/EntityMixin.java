package com.dksmp.epitaph.mixin;

import com.dksmp.epitaph.EpitaphState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Красит обводку в красный цвет. */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void epitaph$outlineColor(CallbackInfoReturnable<Integer> cir) {
        if (EpitaphState.shouldOutline((Entity) (Object) this)) {
            cir.setReturnValue(EpitaphState.OUTLINE_COLOR);
        }
    }
}
