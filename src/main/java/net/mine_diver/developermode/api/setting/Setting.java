package net.mine_diver.developermode.api.setting;

import net.mine_diver.developermode.api.Message;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.util.Identifier;
import net.modificationstation.stationapi.api.util.StringIdentifiable;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Something about a player or their world that can be set from the developer
 * tools: a power, where the sun is, what the sky is doing.
 *
 * <p>Registered in {@link SettingRegistry}, from a
 * {@link net.mine_diver.developermode.api.event.setting.SettingRegistryEvent}.
 * Everything registered can be put in a preset without anything more being
 * asked of it.
 *
 * <p>A setting's values are a handful of constants of an enum, named the way
 * an {@code EnumProperty}'s are, by {@link StringIdentifiable#asString()}:
 * lowercase and stable. Values are kept in preset files and sent over the
 * wire by that name, so renaming one loses it everywhere it was kept, and
 * renaming the constant itself loses nothing.
 *
 * <p>A value, once set, does one of two things. It {@linkplain #stays() stays},
 * the way a power does, until something sets it again. Or it drifts, the way
 * the day moves on from noon, and a setting that drifts can be a
 * {@link LockableSetting}, held at a value until it is let go. A preset is lit
 * by what stays and what is locked, and choosing a lit one puts what stays
 * back at {@linkplain #rest() rest} and unlocks what it locked.
 *
 * <p>Reading and changing happen on different sides. {@link #current} is
 * asked on the client, of its own copy of the player and their world, every
 * time a slot or a preset is drawn; keeping that copy up to date is the
 * setting's business, since only it knows where its state lives. {@link #set}
 * is asked of the side that owns the state, which is the server, or the
 * client in singleplayer. By then the request has been checked: the player
 * may use the tools, and the value is one of this setting's.
 *
 * <p>Named for people through translation keys under {@link #translationKey()}:
 *
 * <ul>
 *   <li>the key itself, for its name, as a slot or a row of the preset editor
 *       shows it;
 *   <li>{@code .hint} beneath it, for a line saying what it is;
 *   <li>{@link #valueKey}, for each value's name, with {@code .hint}
 *       beneath that for a line about the value, shown on a slot that sets
 *       it.
 * </ul>
 *
 * @param <V> the enum its values are constants of
 */
public interface Setting<V extends Enum<V> & StringIdentifiable> {
    /** Every value, in the order they are offered, with the one it rests at first. */
    List<V> values();

    /** Whether a value, once set, stays until something sets it again. */
    boolean stays();

    /**
     * Where a preset that lets go of this puts it back, which only means
     * something for a setting that stays.
     */
    default V rest() {
        return values().get(0);
    }

    /**
     * What it is now, or null if there is nothing to say, such as the weather
     * somewhere without a sky. For a setting that drifts, this is the value it
     * is nearest to.
     */
    @Nullable V current(PlayerEntity player);

    /** @return why it could not be, or null if it is done */
    @Nullable Message set(PlayerEntity player, V value);

    /** The value of this setting's that goes by a name, or null if none does. */
    default @Nullable V value(String name) {
        for (V value : values()) {
            if (value.asString().equals(name)) return value;
        }
        return null;
    }

    /** What it is registered as, or null before it is. */
    default @Nullable Identifier id() {
        return SettingRegistry.INSTANCE.getId(this);
    }

    /** {@code setting.<namespace>.<path>}, from what it is registered as. */
    default String translationKey() {
        Identifier id = id();
        return "setting." + id.namespace + "." + id.path;
    }

    /** The key a value is named by, which is under this setting's own. */
    default String valueKey(V value) {
        return translationKey() + "." + value.asString();
    }
}
