package live.minehub.polarpaper.util;

import live.minehub.polarpaper.PolarPaper;

import java.io.PrintWriter;
import java.io.StringWriter;

public class ExceptionUtil {

    public static void log(Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        String exceptionAsString = sw.toString();
        PolarPaper.logger().warning(exceptionAsString);
    }

}
