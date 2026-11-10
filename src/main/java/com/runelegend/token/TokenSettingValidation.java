package com.runelegend.token;

/**
 * Validation shared by the typed token setting records. It deliberately lives outside
 * {@link TokenConfig}: a setting constructor runs during its own class initialisation and must
 * never re-enter TokenConfig's initialisation while {@code TokenConfig.DEFAULT} is still being
 * built, or the default setting would be observed as null.
 */
final class TokenSettingValidation {
    private TokenSettingValidation() {}

    static void requireFinite(float value, String name) {
        if (!Float.isFinite(value)) throw invalid(name + " must be finite");
    }

    static IllegalArgumentException invalid(String message) {
        return new IllegalArgumentException("Invalid RuneLegend token setting: " + message);
    }
}
