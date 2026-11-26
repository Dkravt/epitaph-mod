package com.dksmp.epitaph;

import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.WorldRenderEvents;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.render.LightmapTextureManager;
import net.minecraft.client.render.OverlayTexture;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.util.Identifier;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.math.RotationAxis;
import net.minecraft.util.math.Vec3d;
import org.joml.Matrix4f;

/** Рисует маленький глаз на лбу у тебя, пока включён уворот (виден в виде от 3-го лица спереди). */
public final class EyeRenderer {

    private static final Identifier EYE = Identifier.of("epitaph", "textures/entity/eye.png");

    public static void register() {
        WorldRenderEvents.AFTER_ENTITIES.register(EyeRenderer::render);
    }

    private static void render(WorldRenderContext context) {
        if (!EpitaphState.dodgeArmed) return;

        MinecraftClient client = MinecraftClient.getInstance();
        ClientPlayerEntity player = client.player;
        if (player == null || client.options.getPerspective().isFirstPerson()) return;

        VertexConsumerProvider consumers = context.consumers();
        MatrixStack matrices = context.matrixStack();
        if (consumers == null || matrices == null) return;

        float delta = client.getRenderTickCounter().getTickDelta(false);
        Vec3d pos = player.getLerpedPos(delta);
        Vec3d cam = context.camera().getPos();
        float yaw = MathHelper.lerpAngleDegrees(delta, player.prevHeadYaw, player.headYaw);
        float pitch = MathHelper.lerp(delta, player.prevPitch, player.getPitch());
        float eye = player.getEyeHeight(player.getPose());

        matrices.push();
        matrices.translate(pos.x - cam.x, pos.y + eye - cam.y, pos.z - cam.z);
        matrices.multiply(RotationAxis.POSITIVE_Y.rotationDegrees(-yaw));
        matrices.multiply(RotationAxis.POSITIVE_X.rotationDegrees(pitch));
        matrices.translate(0.0, 0.10, 0.27); // лоб, чуть перед головой

        RenderLayer layer = RenderLayer.getEntityTranslucent(EYE);
        VertexConsumer buffer = consumers.getBuffer(layer);
        Matrix4f m = matrices.peek().getPositionMatrix();
        float s = 0.11f;
        vertex(buffer, m, -s, s, 0f, 0f);
        vertex(buffer, m, -s, -s, 0f, 1f);
        vertex(buffer, m, s, -s, 1f, 1f);
        vertex(buffer, m, s, s, 1f, 0f);
        matrices.pop();

        if (consumers instanceof VertexConsumerProvider.Immediate immediate) {
            immediate.draw(layer);
        }
    }

    private static void vertex(VertexConsumer buffer, Matrix4f m, float x, float y, float u, float v) {
        buffer.vertex(m, x, y, 0f)
                .color(255, 255, 255, 255)
                .texture(u, v)
                .overlay(OverlayTexture.DEFAULT_UV)
                .light(LightmapTextureManager.MAX_LIGHT_COORDINATE)
                .normal(0f, 0f, 1f);
    }

    private EyeRenderer() {}
}
