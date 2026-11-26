package com.dksmp.epitaph.mixin;

import com.dksmp.epitaph.EpitaphState;
import net.minecraft.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Красит обводку: красная для мобов, розовая для тебя. */
@Mixin(Entity.class)
public class EntityMixin {

    @Inject(method = "getTeamColorValue", at = @At("HEAD"), cancellable = true)
    private void epitaph$outlineColor(CallbackInfoReturnable<Integer> cir) {
        Entity self = (Entity) (Object) this;
        if (EpitaphState.shouldOutline(self)) {
            cir.setReturnValue(EpitaphState.outlineColor(self));
        }
    }
}
