package live.minehub.polarpaper.util;

import live.minehub.polarpaper.PolarDataConverter;
import org.jetbrains.annotations.NotNull;

public class ChainDataConverter implements PolarDataConverter {

    private static final int CHAIN_RENAME_VERSION = 4536; // 25w32a https://minecraft.wiki/w/Data_version

    @Override
    public void convertBlockPalette(@NotNull String[] palette, int fromVersion, int toVersion) {
        if (fromVersion >= CHAIN_RENAME_VERSION) return; // upgrade not needed

        for (int i = 0; i < palette.length; i++) {
            String string = palette[i];
            String fixed = string.replace("minecraft:chain", "minecraft:iron_chain");
            palette[i] = fixed;
        }
    }
}
