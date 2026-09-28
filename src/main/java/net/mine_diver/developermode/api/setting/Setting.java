package net.mine_diver.developermode.api.setting;

import net.mine_diver.developermode.api.Message;
import net.minecraft.entity.player.PlayerEntity;
import net.modificationstation.stationapi.api.util.Identifier;
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
 * <p>A setting has a handful of values, each named the way a block state
 * property's are: lowercase and stable. Values are kept in preset files and
 * sent over the wire by name, so renaming one loses it everywhere it was kept.
 *
 * <p>A value, once set, does one of two things. It {@linkplain #stays() stays},
 * the way a power does, until something sets it again. Or it drifts, the way
 * the day moves on from noon, and a setting that drifts can be
 * {@linkplain #lockable() lockable}, held at a value until it is let go. A
 * preset is lit by what stays and what is locked, and choosing a lit one puts
 * what stays back at {@linkplain #rest() rest} and unlocks what it locked.
 *
 * <p>Reading and changing happen on different sides. {@link #current} and
 * {@link #locked} are asked on the client, of its own copy of the player and
 * their world, every time a slot or a preset is drawn; keeping that copy up to
 * date is the setting's business, since only it knows where its state lives.
 * {@link #set}, {@link #lock} and {@link #unlock} are asked of the side that
 * owns the state, which is the server, or the client in singleplayer. By then
 * the request has been checked: the player may use the tools, the value is
 * one of this setting's, and a lock is only asked of a setting that is
 * lockable.
 *
 * <p>Named for people through translation keys under {@link #translationKey()}:
 *
 * <ul>
 *   <li>the key itself, for its name, as a slot or a row of the preset editor
 *       shows it;
 *   <li>{@code .hint} beneath it, for a line saying what it is;
 *   <li>{@link #valueKey(String)}, for each value's name, with {@code .hint}
 *       beneath that for a line about the value, shown on a slot that sets
 *       it.
 * </ul>
 */
public interface Setting {
    /** Every value, in the order they are offered, with the one it rests at first. */
    List<String> values();

    /** Whether a value, once set, stays until something sets it again. */
    boolean stays();

    /** Whether a setting that drifts can be held at a value. */
    default boolean lockable() {
        return false;
    }

    /**
     * Where a preset that lets go of this puts it back, which only means
     * something for a setting that stays.
     */
    default String rest() {
        return values().get(0);
    }

    /**
     * What it is now, or null if there is nothing to say, such as the weather
     * somewhere without a sky. For a setting that drifts, this is the value it
     * is nearest to.
     */
    @Nullable String current(PlayerEntity player);

    /** The value it is held at, or null if it is not held. */
    default @Nullable String locked(PlayerEntity player) {
        return null;
    }

    /** @return why it could not be, or null if it is done */
    @Nullable Message set(PlayerEntity player, String value);

    /**
     * Holds it at a value, moving it there first. Only asked of a lockable
     * setting.
     *
     * @return why it could not be, or null if it is done
     */
    default @Nullable Message lock(PlayerEntity player, String value) {
        throw new UnsupportedOperationException(id() + " cannot be locked");
    }

    /**
     * Lets go of a lock, and leaves it wherever it was held. Only asked of a
     * lockable setting.
     *
     * @return why it could not be, or null if it is done
     */
    default @Nullable Message unlock(PlayerEntity player) {
        throw new UnsupportedOperationException(id() + " cannot be locked");
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
    default String valueKey(String value) {
        return translationKey() + "." + value;
    }
}
