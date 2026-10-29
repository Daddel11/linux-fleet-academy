package com.fleetacademy.game;

final class PositionPreferences {
    private PositionPreferences() {}

    static float coordinate(Object value, float fallback) {
        if (!(value instanceof Integer) && !(value instanceof Float) && !(value instanceof Long)) return fallback;
        float result = ((Number) value).floatValue();
        return Float.isNaN(result) || Float.isInfinite(result) ? fallback : result;
    }
}
