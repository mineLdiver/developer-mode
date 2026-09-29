package net.mine_diver.developermode.api.setting;

import net.modificationstation.stationapi.api.util.StringIdentifiable;

/**
 * The two values of a {@link SwitchSetting}, off first, since off is where a
 * switch rests.
 *
 * <p>Named the same for every switch, under keys of their own, so a switch
 * only needs translations for its own name and hint.
 */
public enum Switch implements StringIdentifiable {
    OFF("off"),
    ON("on");

    private final String name;

    Switch(String name) {
        this.name = name;
    }

    public static Switch of(boolean on) {
        return on ? ON : OFF;
    }

    public boolean isOn() {
        return this == ON;
    }

    /** The key this value is named by, whichever switch it is a value of. */
    public String translationKey() {
        return "gui.developermode.setting." + name;
    }

    @Override
    public String asString() {
        return name;
    }
}
