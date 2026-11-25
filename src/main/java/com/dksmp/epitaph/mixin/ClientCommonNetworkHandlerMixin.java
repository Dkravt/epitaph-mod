package com.dksmp.epitaph.mixin;

import com.dksmp.epitaph.EpitaphState;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ClientCommonNetworkHandler.class)
public class ClientCommonNetworkHandlerMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true)
    private void epitaph$holdMovement(Packet<?> packet, CallbackInfo ci) {
        if (EpitaphState.active && packet instanceof PlayerMoveC2SPacket) {
            EpitaphState.queue.add(packet);
            ci.cancel();
        }
    }
}
