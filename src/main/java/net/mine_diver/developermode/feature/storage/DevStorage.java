package net.mine_diver.developermode.feature.storage;

import net.fabricmc.loader.api.FabricLoader;
import net.mine_diver.developermode.DeveloperMode;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtIo;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Named NBT documents under the loader's config directory.
 *
 * <p>A general layer on purpose. Summon presets are its first tenant, not its
 * reason: anything that should outlive a restart asks for a document by name
 * and gets a compound back. Gzipped NBT rather than a text format because Beta
 * already reads and writes it, so there is nothing to parse and nothing to get
 * wrong — {@code level.dat} is one of these.
 *
 * <p>Nothing here throws over what is on disk. A document that is missing,
 * truncated, or written by a version that has since changed its mind reads back
 * as absent, because the alternative is a bad file stopping the game from
 * starting. Writes land on a temporary and are moved into place, so an
 * interrupted one leaves the previous document intact rather than half of a new
 * one.
 */
public final class DevStorage {
    private static final String EXTENSION = ".nbt";
    /** Document names are ours, not the user's, so this only has to catch typos. */
    private static final String NAME_PATTERN = "[a-z0-9-]+";

    /**
     * Stamped into every document and checked on the way back in.
     *
     * <p>Not a tenant's schema version, the envelope's. It is here so that a
     * document written by a later Developer Mode is recognised as one and left
     * alone rather than read as though its keys still mean what they mean
     * today. Cheap now and impossible to add later: once unstamped documents
     * exist, nothing can tell them from documents that never needed a stamp.
     */
    private static final String VERSION_KEY = "Version";
    private static final String DATA_KEY = "Data";
    private static final int VERSION = 1;

    private DevStorage() {}

    /** Where documents live. Created on the first write, not before. */
    public static Path directory() {
        return FabricLoader.getInstance().getConfigDir().resolve(DeveloperMode.NAMESPACE.toString());
    }

    /**
     * @return the document, or null if it is absent or unreadable
     */
    public static NbtCompound read(String document) {
        if (!document.matches(NAME_PATTERN)) throw new IllegalArgumentException(document);

        Path file = directory().resolve(document + EXTENSION);
        if (!Files.isRegularFile(file)) return null;

        try (InputStream in = Files.newInputStream(file)) {
            NbtCompound envelope = NbtIo.readCompressed(in);

            int version = envelope.getInt(VERSION_KEY);
            if (version > VERSION) {
                DeveloperMode.LOGGER.warn("{} was written by a newer version ({}), leaving it alone", file, version);
                return null;
            }
            return envelope.contains(DATA_KEY) ? envelope.getCompound(DATA_KEY) : null;
        } catch (Throwable error) {
            DeveloperMode.LOGGER.warn("Could not read {}, ignoring it", file, error);
            return null;
        }
    }

    /**
     * @return false if it could not be written, having said so in the log
     */
    public static boolean write(String document, NbtCompound nbt) {
        if (!document.matches(NAME_PATTERN)) throw new IllegalArgumentException(document);

        Path file = directory().resolve(document + EXTENSION);
        Path temporary = directory().resolve(document + EXTENSION + ".tmp");
        try {
            // The tenant's compound goes inside an envelope rather than being
            // stamped in place, so the version tag can never collide with a key
            // the tenant chose for itself.
            NbtCompound envelope = new NbtCompound();
            envelope.putInt(VERSION_KEY, VERSION);
            envelope.put(DATA_KEY, nbt);

            Files.createDirectories(directory());
            try (OutputStream out = Files.newOutputStream(temporary)) {
                NbtIo.writeCompressed(envelope, out);
            }
            Files.move(temporary, file, StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Throwable error) {
            DeveloperMode.LOGGER.warn("Could not write {}", file, error);
            return false;
        }
    }
}
