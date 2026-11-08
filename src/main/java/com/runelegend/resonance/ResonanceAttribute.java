package com.runelegend.resonance;

import com.runelegend.RuneLegendMod;
import com.runelegend.RuneLegendText;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

import java.util.List;
import java.util.Optional;

/** The three resonance attributes. Only the guard-selectable pair may be chosen by a player. */
public enum ResonanceAttribute {
    PROTECTION("protection", true),
    LOST("lost", true),
    DESTRUCTION("destruction", false);

    private static final List<ResonanceAttribute> SELECTABLE = List.of(PROTECTION, LOST);

    private final Identifier id;
    private final boolean guardSelectable;

    ResonanceAttribute(String path, boolean guardSelectable) {
        this.id = RuneLegendMod.id(path);
        this.guardSelectable = guardSelectable;
    }

    public Identifier id() {
        return id;
    }

    public boolean guardSelectable() {
        return guardSelectable;
    }

    public Component displayName() {
        return RuneLegendText.message("resonance.runelegend." + id.getPath());
    }

    public static Optional<ResonanceAttribute> byId(Identifier id) {
        for (var attribute : values()) {
            if (attribute.id.equals(id)) return Optional.of(attribute);
        }
        return Optional.empty();
    }

    public static List<ResonanceAttribute> selectable() {
        return SELECTABLE;
    }
}
