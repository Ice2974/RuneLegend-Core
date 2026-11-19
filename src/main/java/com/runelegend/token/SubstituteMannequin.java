package com.runelegend.token;

import net.minecraft.core.Holder;
import net.minecraft.core.component.DataComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.EquipmentSlotGroup;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.decoration.Mannequin;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueOutput;

import java.util.function.Consumer;

final class SubstituteMannequin extends Mannequin {
    private final float maximumHealth;
    private Consumer<Boolean> onEnd;

    SubstituteMannequin(ServerPlayer player) {
        super(player.level());
        maximumHealth = player.getMaxHealth();
        snapTo(player.position(), player.getYRot(), player.getXRot());
        setYHeadRot(player.getYHeadRot());
        applyImplicitComponent(DataComponents.PROFILE, player.getProfile());
        byte visibleLayers = 0;
        for (var part : PlayerModelPart.values()) {
            if (player.isModelPartShown(part)) visibleLayers |= (byte) part.getMask();
        }
        getEntityData().set(DATA_PLAYER_MODE_CUSTOMISATION, visibleLayers);
        for (var slot : EquipmentSlotGroup.ARMOR) {
            if (slot.getType() != EquipmentSlot.Type.HUMANOID_ARMOR) continue;
            setItemSlot(slot, player.getItemBySlot(slot).copy());
        }
        getAttribute(Attributes.MAX_HEALTH).setBaseValue(maximumHealth);
        setHealth(maximumHealth);
    }

    void onEnd(Consumer<Boolean> action) {
        onEnd = action;
    }

    void expire() {
        if (!isAlive()) return;
        setHealth(0);
        die(damageSources().genericKill());
    }

    private void end(boolean died) {
        var action = onEnd;
        onEnd = null;
        if (action != null) action.accept(died);
    }

    @Override protected void onAttributeUpdated(Holder<Attribute> attribute) {
        if (attribute.is(Attributes.MAX_HEALTH.unwrapKey().orElseThrow())) {
            // The copied maximum already includes any max-health modifiers from the owner's armor.
            var maximum = getAttribute(Attributes.MAX_HEALTH);
            maximum.removeModifiers();
            maximum.setBaseValue(maximumHealth);
        }
        super.onAttributeUpdated(attribute);
    }

    @Override public boolean hurtServer(ServerLevel level, DamageSource source, float amount) {
        return !(source.getEntity() instanceof Player) && super.hurtServer(level, source, amount);
    }

    @Override protected void dropAllDeathLoot(ServerLevel level, DamageSource source) {}

    @Override public boolean canUseSlot(EquipmentSlot slot) { return false; }

    @Override protected boolean canRide(Entity vehicle) { return false; }

    @Override protected boolean canAddPassenger(Entity passenger) { return false; }

    @Override public boolean canUsePortal(boolean allowPassengers) { return false; }

    @Override public boolean canTeleport(Level origin, Level destination) { return origin == destination; }

    // Keep the entity in the vanilla chunk-unload path while omitting it from the serialized entities.
    @Override public boolean saveAsPassenger(ValueOutput output) { return false; }

    @Override public void die(DamageSource source) {
        super.die(source);
        if (isDeadOrDying()) end(true);
    }

    @Override public void onRemoval(RemovalReason reason) {
        super.onRemoval(reason);
        end(false);
    }
}
