package live.minehub.polarpaper.core.util;

import io.papermc.paper.configuration.Configurations;
import io.papermc.paper.configuration.PaperConfigurations;
import io.papermc.paper.configuration.WorldConfiguration;
import net.minecraft.server.MinecraftServer;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Predicate;

/**
 * Makes polar worlds share one paper world config instead of loading one per world.
 * <br/><br/>
 * Paper loads the world config in the ServerLevel constructor (reads paper-world-defaults.yml, creates and reads a
 * paper-world.yml in the world folder, then maps it), which is most of the time spent creating a level.
 * There's no hook for it so the server's PaperConfigurations is swapped for one that returns a cached config
 * for polar worlds. Other worlds are unaffected.
 * <br/><br/>
 * While installed, a paper-world.yml in a polar world's folder is ignored. /paper reload still works.
 */
public final class SharedPaperWorldConfig {
    private static final Logger LOGGER = LoggerFactory.getLogger(SharedPaperWorldConfig.class);
    private static final String WORLD_CONFIG_FILE_NAME = "paper-world.yml";

    private static @Nullable PaperConfigurations original;

    private SharedPaperWorldConfig() {
    }

    /**
     * @param isPolarWorld whether the world key (namespace:path) of a level being created is a polar world
     * @return false if it couldn't be installed, worlds then load their config as usual
     */
    public static synchronized boolean install(Predicate<String> isPolarWorld) {
        if (original != null) return true;

        MinecraftServer server = MinecraftServer.getServer();
        PaperConfigurations current = server.paperConfigurations;
        if (current.getClass() != PaperConfigurations.class) {
            // forks with their own configurations class might have extra state
            LOGGER.warn("Not sharing the paper world config, unsupported configurations class {}", current.getClass().getName());
            return false;
        }

        try {
            Field globalFolderField = Configurations.class.getDeclaredField("globalFolder");
            globalFolderField.setAccessible(true);
            Path globalFolder = (Path) globalFolderField.get(current);

            setConfigurations(server, new SharingConfigurations(globalFolder, isPolarWorld));
            original = current;
            return true;
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Not sharing the paper world config, failed to install", e);
            return false;
        }
    }

    public static synchronized void uninstall() {
        if (original == null) return;

        try {
            setConfigurations(MinecraftServer.getServer(), original);
        } catch (ReflectiveOperationException | RuntimeException e) {
            LOGGER.warn("Failed to restore the paper configurations", e);
        }
        original = null;
    }

    private static void setConfigurations(MinecraftServer server, PaperConfigurations configurations) throws ReflectiveOperationException {
        Field field = MinecraftServer.class.getDeclaredField("paperConfigurations");
        field.setAccessible(true);
        field.set(server, configurations);
    }

    private static final class SharingConfigurations extends PaperConfigurations {
        private final Predicate<String> isPolarWorld;
        private volatile @Nullable WorldConfiguration shared;

        private SharingConfigurations(Path globalFolder, Predicate<String> isPolarWorld) {
            super(globalFolder);
            this.isPolarWorld = isPolarWorld;
        }

        @Override
        public WorldConfiguration createWorldConfig(ContextMap contextMap) {
            if (!isPolarWorld.test(contextMap.require(WORLD_KEY).toString())) return super.createWorldConfig(contextMap);

            WorldConfiguration config = shared;
            if (config != null) return config;

            synchronized (this) {
                config = shared;
                if (config != null) return config;

                Path worldConfigFile = contextMap.require(WORLD_DIRECTORY).resolve(WORLD_CONFIG_FILE_NAME);
                boolean existed = Files.exists(worldConfigFile);

                config = super.createWorldConfig(contextMap);

                // paper creates an empty paper-world.yml in the world folder
                if (!existed) {
                    try {
                        Files.deleteIfExists(worldConfigFile);
                    } catch (IOException _) {
                    }
                }

                shared = config;
                return config;
            }
        }
    }
}
