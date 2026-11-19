package com.runelegend.client.render;

import com.runelegend.particle.RuneLegendParticles;
import com.runelegend.token.TokenConfig.EchoSettings;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.Mth;

public final class EchoPulseParticle extends SingleQuadParticle {
    private static final int FRAME_COUNT = 8;
    private static final double SURFACE_OFFSET = 1.0 / 64.0;
    private static final FacingCameraMode HORIZONTAL = (rotation, camera, partialTick) -> rotation.rotationX(-Mth.HALF_PI);
    private final SpriteSet sprites;

    private EchoPulseParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y + SURFACE_OFFSET, z, sprites.first());
        this.sprites = sprites;
        // The largest ring spans 140 pixels in the 160-pixel canvas.
        quadSize = (float) (EchoSettings.RADIUS * (160.0 / 140.0));
        lifetime = FRAME_COUNT;
    }

    public static void register() {
        ParticleProviderRegistry.getInstance().register(RuneLegendParticles.ECHO_PULSE, sprites ->
                (options, level, x, y, z, vx, vy, vz, random) -> new EchoPulseParticle(level, x, y, z, sprites));
    }

    @Override public void tick() {
        if (++age >= lifetime) {
            remove();
        } else {
            setSprite(sprites.get(age, FRAME_COUNT - 1));
        }
    }

    @Override public FacingCameraMode getFacingCameraMode() { return HORIZONTAL; }

    @Override public Layer getLayer() { return Layer.TRANSLUCENT; }
}
