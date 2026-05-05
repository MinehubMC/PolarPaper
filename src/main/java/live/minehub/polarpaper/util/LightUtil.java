package live.minehub.polarpaper.util;

import live.minehub.polarpaper.PolarSection;
import net.minecraft.world.level.chunk.DataLayer;

import java.util.Arrays;

public class LightUtil {

    public static final int LIGHT_LENGTH = 2048;
    public static final byte[] EMPTY_CONTENT = new byte[LIGHT_LENGTH];
    public static final byte[] FULLY_LIT_CONTENT = new byte[LIGHT_LENGTH];

    static {
        Arrays.fill(FULLY_LIT_CONTENT, (byte) -1);
    }

    public static byte[] getLightArray(PolarSection.LightContent lightContent, byte[] data) {
        return switch (lightContent) {
            case MISSING -> null;
            case EMPTY -> EMPTY_CONTENT.clone();
            case FULL -> FULLY_LIT_CONTENT.clone();
            case PRESENT -> data;
        };
    }

    public static PolarSection.LightContent getLightContent(DataLayer dataLayer) {
        byte[] content = dataLayer.isDefinitelyHomogenous() ? null : dataLayer.getData();
        if (dataLayer.isDefinitelyFilledWith(0)) return PolarSection.LightContent.EMPTY;
        if (dataLayer.isDefinitelyFilledWith(15)) return PolarSection.LightContent.FULL;
        if (content == null || content.length == 0) return PolarSection.LightContent.MISSING;
        else if (Arrays.equals(content, EMPTY_CONTENT)) return PolarSection.LightContent.EMPTY;
        else if (Arrays.equals(content, FULLY_LIT_CONTENT)) return PolarSection.LightContent.FULL;
        else return PolarSection.LightContent.PRESENT;
    }

}
