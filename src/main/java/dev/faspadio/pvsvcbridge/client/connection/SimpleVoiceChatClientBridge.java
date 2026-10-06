package dev.faspadio.pvsvcbridge.client.connection;

import dev.faspadio.pvsvcbridge.common.log.BridgeLog;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

public final class SimpleVoiceChatClientBridge {

    private static final String IMPLEMENTATION =
            "dev.faspadio.pvsvcbridge.client.connection.v1_21_11.SimpleVoiceChatClientBridge";
    private static final Class<?> BRIDGE_CLASS = loadBridgeClass();

    private SimpleVoiceChatClientBridge() {
    }

    public static boolean available() {
        return BRIDGE_CLASS != null;
    }

    public static void initialize() {
        invoke("initialize");
    }

    public static void start() {
        invoke("start");
    }

    public static void tick() {
        invoke("tick");
    }

    public static void stop() {
        invoke("stop");
    }

    private static Class<?> loadBridgeClass() {
        try {
            return Class.forName(IMPLEMENTATION);
        } catch (ClassNotFoundException unavailableForThisMinecraftVersion) {
            return null;
        }
    }

    private static void invoke(String methodName) {
        if (BRIDGE_CLASS == null) {
            return;
        }
        try {
            Method method = BRIDGE_CLASS.getMethod(methodName);
            method.invoke(null);
        } catch (NoSuchMethodException | IllegalAccessException error) {
            BridgeLog.error("Simple Voice Chat bridge entrypoint is invalid", error);
        } catch (InvocationTargetException error) {
            BridgeLog.error("Simple Voice Chat bridge operation failed", error.getCause());
        }
    }

}
