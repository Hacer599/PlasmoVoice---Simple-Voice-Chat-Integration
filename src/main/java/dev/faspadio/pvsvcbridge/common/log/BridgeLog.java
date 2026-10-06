package dev.faspadio.pvsvcbridge.common.log;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class BridgeLog {

    private static final Logger LOGGER = LoggerFactory.getLogger("PV-SVC-Bridge");
    private static final String PREFIX = "[PV-SVC-Bridge] ";

    private static volatile boolean debug;

    private BridgeLog() {
    }

    public static void setDebug(boolean enabled) {
        debug = enabled;
    }

    public static boolean isDebug() {
        return debug;
    }

    public static void info(String message, Object... arguments) {
        LOGGER.info(PREFIX + message, arguments);
    }

    public static void warn(String message, Object... arguments) {
        LOGGER.warn(PREFIX + message, arguments);
    }

    public static void error(String message, Object... arguments) {
        LOGGER.error(PREFIX + message, arguments);
    }

    public static void error(String message, Throwable error) {
        LOGGER.error(PREFIX + message, error);
    }

    public static void debug(String message, Object... arguments) {
        if (debug) {
            LOGGER.info(PREFIX + message, arguments);
        }
    }

    public static void debug(String message, Throwable error) {
        if (debug) {
            LOGGER.info(PREFIX + message, error);
        }
    }
}
