package com.dksmp.epitaph;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.client.sound.PositionedSoundInstance;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.decoration.ArmorStandEntity;
import net.minecraft.entity.projectile.ExplosiveProjectileEntity;
import net.minecraft.entity.projectile.PersistentProjectileEntity;
import net.minecraft.entity.projectile.ProjectileEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.sound.SoundEvents;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;

/**
 * Уворот (по мотивам Arrow Dodge): включаешь на время, и при угрозе тебя переносит за спину
 * противника.
 *  - Стрелы, трезубцы и огненные шары предсказываются заранее: увернуться удаётся ДО попадания.
 *  - Удар рукой и всё остальное: реакция после попадания (урон уже пришёл с сервера, клиентский
 *    мод отменить его не может), но ты сразу оказываешься за спиной у ударившего.
 */
public final class Dodge {

    /** Дальше этого расстояния за спину не телепортируемся (блоки). */
    private static final double MAX_RANGE = 24.0;

    public static void toggle(MinecraftClient client) {
        if (EpitaphState.dodgeArmed) {
            disarm();
        } else {
            arm(client);
        }
    }

    private static void arm(MinecraftClient client) {
        EpitaphState.dodgeArmed = true;
        EpitaphState.dodgeTicksLeft = EpitaphConfig.dodgeDurationSeconds * 20;
        EpitaphState.dodgeCharges = EpitaphConfig.dodgeCount;
        EpitaphState.dodgeCooldown = 0;
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.7f));
    }

    public static void disarm() {
        EpitaphState.dodgeArmed = false;
    }

    public static void tick(MinecraftClient client) {
        ClientPlayerEntity player = client.player;
        if (player == null || client.world == null) return;

        // Заметили, что по нам попали: hurtTime вырос
        int hurt = player.hurtTime;
        boolean gotHit = hurt > EpitaphState.lastHurtTime;
        EpitaphState.lastHurtTime = hurt;

        if (!EpitaphState.dodgeArmed) return;

        EpitaphState.dodgeTicksLeft--;
        if (EpitaphState.dodgeTicksLeft <= 0 || EpitaphState.dodgeCharges <= 0) {
            disarm();
            return;
        }
        if (EpitaphState.dodgeCooldown > 0) {
            EpitaphState.dodgeCooldown--;
            return;
        }

        // 1) Снаряд летит в тебя: уворачиваемся до попадания
        ProjectileEntity incoming = findIncoming(client, player);
        if (incoming != null && dodgeProjectile(client, player, incoming)) {
            onDodged(client);
            return;
        }

        // 2) По тебе ударили: переносимся за спину ударившему
        if (gotHit) {
            LivingEntity attacker = findAttacker(client, player);
            if (attacker != null && teleportTo(client, player, behind(attacker), attacker.getPos())) {
                onDodged(client);
            }
        }
    }

    private static void onDodged(MinecraftClient client) {
        EpitaphState.dodgeCharges--;
        EpitaphState.dodgeCooldown = EpitaphState.DODGE_COOLDOWN;
        EpitaphState.flashTick = 0;
        client.getSoundManager().play(
                PositionedSoundInstance.master(SoundEvents.ENTITY_ENDERMAN_TELEPORT, 1.3f));
        if (EpitaphState.dodgeCharges <= 0) {
            disarm();
        }
    }

    /** Ищем стрелу/трезубец/огненный шар, который в ближайшие тики попадёт в тебя. */
    private static ProjectileEntity findIncoming(MinecraftClient client, ClientPlayerEntity player) {
        Box target = player.getBoundingBox().expand(0.35);
        for (Entity e : client.world.getEntities()) {
            if (!(e instanceof PersistentProjectileEntity) && !(e instanceof ExplosiveProjectileEntity)) continue;
            ProjectileEntity proj = (ProjectileEntity) e;
            if (proj.getOwner() == player) continue;
            if (proj.squaredDistanceTo(player) > 40.0 * 40.0) continue;

            Vec3d vel = proj.getVelocity();
            if (vel.lengthSquared() < 0.09) continue; // воткнулась или почти стоит

            boolean noGravity = proj.hasNoGravity();
            Vec3d pos = proj.getPos();
            for (int t = 0; t < 4; t++) {
                Vec3d next = pos.add(vel);
                if (target.contains(next) || target.raycast(pos, next).isPresent()) {
                    return proj;
                }
                pos = next;
                vel = vel.multiply(0.99).add(0.0, noGravity ? 0.0 : -0.05, 0.0);
            }
        }
        return null;
    }

    private static boolean dodgeProjectile(MinecraftClient client, ClientPlayerEntity player, ProjectileEntity proj) {
        // Если знаем, кто стрелял: за спину стрелку
        Entity owner = proj.getOwner();
        if (owner instanceof LivingEntity shooter && shooter != player) {
            if (teleportTo(client, player, behind(shooter), shooter.getPos())) return true;
        }
        // Иначе (или если путь закрыт): шаг в сторону от линии полёта
        Vec3d v = proj.getVelocity();
        Vec3d flat = new Vec3d(v.x, 0.0, v.z);
        if (flat.lengthSquared() < 1.0E-4) return false;
        flat = flat.normalize();
        Vec3d side = new Vec3d(-flat.z, 0.0, flat.x);
        for (double sign : new double[]{1.0, -1.0}) {
            Vec3d target = player.getPos().add(side.multiply(2.5 * sign));
            if (teleportTo(client, player, target, proj.getPos())) return true;
        }
        return false;
    }

    /** Кто только что ударил: ближайший моб/игрок, у которого сейчас идёт замах. */
    private static LivingEntity findAttacker(MinecraftClient client, ClientPlayerEntity player) {
        LivingEntity best = null;
        double bestDist = 7.0 * 7.0;
        for (Entity e : client.world.getEntities()) {
            if (e == player || e instanceof ArmorStandEntity) continue;
            if (!(e instanceof LivingEntity living)) continue;
            if (!living.handSwinging) continue;
            double d = living.squaredDistanceTo(player);
            if (d < bestDist) {
                bestDist = d;
                best = living;
            }
        }
        return best;
    }

    /** Точка за спиной у цели (в 1.4 блока позади того, куда она смотрит). */
    private static Vec3d behind(LivingEntity target) {
        double yaw = Math.toRadians(target.getHeadYaw());
        double fx = -Math.sin(yaw);
        double fz = Math.cos(yaw);
        return new Vec3d(target.getX() - fx * 1.4, target.getY(), target.getZ() - fz * 1.4);
    }

    /**
     * Переносит тебя в точку, если весь путь свободен. Серверу уходят короткие шаги
     * (не больше 3 блоков), поэтому он принимает перемещение. После переноса разворачивает лицом к lookAt.
     */
    private static boolean teleportTo(MinecraftClient client, ClientPlayerEntity player, Vec3d target, Vec3d lookAt) {
        Vec3d start = player.getPos();
        Vec3d delta = target.subtract(start);
        double dist = delta.length();
        if (dist < 0.5 || dist > MAX_RANGE) return false;

        Box box = player.getBoundingBox();
        int steps = (int) Math.ceil(dist / 3.0);
        Vec3d[] points = new Vec3d[steps];
        for (int i = 1; i <= steps; i++) {
            Vec3d p = start.add(delta.multiply(i / (double) steps));
            if (!client.world.isSpaceEmpty(player, box.offset(p.subtract(start)))) return false;
            points[i - 1] = p;
        }

        ClientPlayNetworkHandler handler = client.getNetworkHandler();
        if (handler != null) {
            for (Vec3d p : points) {
                handler.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(p.x, p.y, p.z, player.isOnGround()));
            }
        }
        player.setPosition(target);

        double dx = lookAt.x - target.x;
        double dz = lookAt.z - target.z;
        if (dx * dx + dz * dz > 1.0E-4) {
            player.setYaw((float) (Math.toDegrees(Math.atan2(dz, dx)) - 90.0));
        }
        return true;
    }

    private Dodge() {}
}
