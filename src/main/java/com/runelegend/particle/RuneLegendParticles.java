package com.runelegend.particle;

import com.runelegend.RuneLegendMod;
import net.fabricmc.fabric.api.particle.v1.FabricParticleTypes;
import net.minecraft.core.Registry;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.BuiltInRegistries;

public final class RuneLegendParticles {
    public static final SimpleParticleType FOUNTAIN_BUBBLE = FabricParticleTypes.simple();

    private RuneLegendParticles() {}

    public static void register() {
        Registry.register(BuiltInRegistries.PARTICLE_TYPE, RuneLegendMod.id("fountain_bubble"), FOUNTAIN_BUBBLE);
    }
}
