package com.runelegend.token.effect;

import com.runelegend.RuneLegendMod;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.List;

/** The normal health held in reserve while vanilla uses the temporary bar. */
public final class AsuraHealth {
    private static final net.minecraft.resources.Identifier SPEED = RuneLegendMod.id("asura_form_speed");
    private static final float SPEED_PER_LEVEL = 0.2F; // Vanilla Speed uses this amount per level.
    private final ServerPlayer player;
    private final AttributeInstance maximum;
    private final AttributeAccess access;
    private final float temporaryMaximum;
    private float normalHealth;

    private AsuraHealth(ServerPlayer player, float temporaryMaximum) {
        this.player = player;
        this.maximum = player.getAttribute(Attributes.MAX_HEALTH);
        this.access = (AttributeAccess) maximum;
        this.temporaryMaximum = temporaryMaximum;
        this.normalHealth = player.getHealth();
    }

    public interface AttributeAccess {
        AsuraHealth runelegend$asuraHealth();
        void runelegend$setAsuraHealth(AsuraHealth form);
        double runelegend$normalValue();
    }

    static AsuraHealth start(ServerPlayer player, float temporaryMaximum, int speedLevel) {
        var form = new AsuraHealth(player, temporaryMaximum);
        form.access.runelegend$setAsuraHealth(form);
        player.setHealth(temporaryMaximum);
        player.getAttribute(Attributes.MOVEMENT_SPEED).addOrUpdateTransientModifier(new AttributeModifier(
                SPEED, (double) SPEED_PER_LEVEL * speedLevel, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        form.syncHealthBar();
        return form;
    }

    void finish() {
        if (access.runelegend$asuraHealth() != this) return;
        access.runelegend$setAsuraHealth(null);
        player.getAttribute(Attributes.MOVEMENT_SPEED).removeModifier(SPEED);
        if (!player.isDeadOrDying()) {
            player.setHealth(normalHealth);
            syncHealthBar();
        }
    }

    public static AsuraHealth on(AttributeInstance attribute) {
        return attribute == null ? null : ((AttributeAccess) attribute).runelegend$asuraHealth();
    }

    public float temporaryMaximum() { return temporaryMaximum; }

    public void healNormalBar(float amount) {
        if (amount > 0 && Float.isFinite(amount)) {
            float room = (float) access.runelegend$normalValue() - normalHealth;
            if (room > 0) normalHealth += Math.min(amount, room);
        }
    }

    public float healthForSave() {
        return Math.min(normalHealth, (float) access.runelegend$normalValue());
    }

    private void syncHealthBar() {
        if (player.connection == null || !player.connection.isAcceptingMessages()) return;
        // Client health is clamped when its health packet arrives, so send the new cap first.
        player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), List.of(maximum)));
        player.resetSentInfo();
    }
}
