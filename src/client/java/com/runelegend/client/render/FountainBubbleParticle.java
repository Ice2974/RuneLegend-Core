package com.runelegend.client.render;

import com.runelegend.particle.RuneLegendParticles;
import net.fabricmc.fabric.api.client.particle.v1.ParticleProviderRegistry;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.util.RandomSource;

public final class FountainBubbleParticle extends SingleQuadParticle {
    private static final double BOB_HEIGHT = 0.035;
    private final double height;
    private final double phase;

    private FountainBubbleParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites, RandomSource random) {
        super(level, x, y, z, sprites.get(0, 1));
        height = y;
        phase = random.nextDouble() * Math.TAU;
        quadSize = 0.075F + 0.035F * random.nextFloat();
        lifetime = 13 + random.nextInt(6);
        hasPhysics = false;
    }

    public static void register() {
        ParticleProviderRegistry.getInstance().register(RuneLegendParticles.FOUNTAIN_BUBBLE, sprites ->
                (options, level, x, y, z, vx, vy, vz, random) -> new FountainBubbleParticle(level, x, y, z, sprites, random));
    }

    @Override public void tick() {
        yd = height + BOB_HEIGHT * Math.sin(phase + age * 0.33) - y;
        super.tick();
        setAlpha(Math.min(1.0F, (lifetime - age) / 4.0F));
    }

    @Override public Layer getLayer() { return Layer.TRANSLUCENT; }
}
