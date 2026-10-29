package com.fleetacademy.game;

public final class PositionPreferencesTest {
    private static void check(Object value, float fallback, float expected) {
        float actual = PositionPreferences.coordinate(value, fallback);
        if (actual != expected) throw new AssertionError("Expected " + expected + " but got " + actual);
    }

    public static void main(String[] args) {
        check(900, 42f, 900f);
        check(4820.5f, 42f, 4820.5f);
        check(900L, 42f, 900f);
        check(null, 42f, 42f);
        check("900", 42f, 42f);
        check(true, 42f, 42f);
        check(Float.NaN, 42f, 42f);
        check(Float.POSITIVE_INFINITY, 42f, 42f);
        System.out.println("PositionPreferencesTest passed");
    }
}
