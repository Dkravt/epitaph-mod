package com.dksmp.epitaph.mixin;

import com.dksmp.epitaph.EpitaphState;
import net.minecraft.client.network.ClientCommonNetworkHandler;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.network.packet.Packet;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Blink: пока Epitaph активна, пакеты движения не уходят на сервер,
 * а копятся в очереди. Сервер (и все остальные) видят тебя на старом месте.
 * При втором нажатии очередь отправляется, и ты "телепортируешься".
 */
@Mixin({ClientCommonNetworkHandler.class, ClientPlayNetworkHandler.class})
public class ClientCommonNetworkHandlerMixin {

    @Inject(method = "sendPacket", at = @At("HEAD"), cancellable = true, require = 0)
    private void epitaph$holdMovement(Packet<?> packet, CallbackInfo ci) {
        if (EpitaphState.active && packet instanceof PlayerMoveC2SPacket) {
            EpitaphState.queue.add(packet);
            ci.cancel();
        }
    }
}
