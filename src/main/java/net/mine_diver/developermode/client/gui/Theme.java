package net.mine_diver.developermode.client.gui;

/**
 * Every color the developer UI uses, as ARGB.
 */
public final class Theme {
    public static final int SCRIM          = 0x77000000;

    public static final int PANEL          = 0xE81A1D21;
    public static final int PANEL_RAISED   = 0xF022262B;
    public static final int PANEL_SUNKEN   = 0x66000000;

    public static final int BORDER         = 0xFF33393F;
    public static final int BORDER_FOCUSED = 0xFF4EC9B0;

    public static final int TITLE_BAR      = 0xFF262B30;
    public static final int TITLE_BAR_FOCUSED = 0xFF2F363D;

    public static final int ACCENT         = 0xFF4EC9B0;
    public static final int ACCENT_FILL    = 0x664EC9B0;
    /** Alpha is the brightness at the center of the glow, not an opacity. */
    public static final int GLOW           = 0x8C6FF0D6;
    public static final int DANGER         = 0xFFE06C75;

    public static final int TEXT           = 0xFFE6E9EC;
    public static final int TEXT_DIM       = 0xFF8B939B;
    public static final int TEXT_FAINT     = 0xFF5A6169;

    public static final int HOVER          = 0x26FFFFFF;

    public static final int NBT_KEY        = 0xFFC8CDD3;
    public static final int NBT_NUMBER     = 0xFFD8A657;
    public static final int NBT_STRING     = 0xFF89B482;
    public static final int NBT_CONTAINER  = 0xFF7DAEA3;
    public static final int NBT_OPAQUE     = 0xFF6E757C;

    private Theme() {}
}
