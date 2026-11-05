package com.usb.smartcontrol;

import android.app.ActivityManager;
import android.app.KeyguardManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.hardware.usb.UsbManager;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.reflect.Array;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import io.github.libxposed.api.XposedInterface;

final class UsbStateController {
    private static final String TAG = "UsbSmartControl";
    private static final String ACTION_USB_STATE = "android.hardware.usb.action.USB_STATE";
    private static final String ACTION_USB_PORT_CHANGED =
            "android.hardware.usb.action.USB_PORT_CHANGED";
    private static final String USB_STATE_PATH = "/sys/class/android_usb/android0/state";
    private static final int USB_DATA_ROLE_HOST = 1;
    private static final int USB_DATA_ROLE_DEVICE = 2;
    private static final int USB_POWER_BRICK_STATUS_CONNECTED = 1;
    private static final int USB_COMPLIANCE_WARNING_MISSING_DATA_LINES = 6;
    private static final AtomicBoolean STARTED = new AtomicBoolean();
    private static volatile UsbStateController instance;

    private final Context context;
    private final XposedInterface xposed;
    private final SharedPreferences preferences;
    private final Handler handler;
    private final Runnable evaluateTask = this::evaluate;
    private final Runnable disconnectTask = this::refreshUsbConnection;
    private final UsbConnectionState connection = new UsbConnectionState();
    private UsbConnectionState.Gadget rawGadgetState = UsbConnectionState.Gadget.UNKNOWN;
    private UsbConnectionState.Gadget broadcastGadgetState = UsbConnectionState.Gadget.UNKNOWN;
    private boolean unlockedSinceBoot;

    private UsbStateController(Context context, XposedInterface xposed,
                               SharedPreferences preferences) {
        this.context = context;
        this.xposed = xposed;
        this.preferences = preferences;
        HandlerThread thread = new HandlerThread("UsbSmartControl");
        thread.start();
        handler = new Handler(thread.getLooper());
        KeyguardManager keyguard = (KeyguardManager) context.getSystemService(
                Context.KEYGUARD_SERVICE);
        unlockedSinceBoot = keyguard != null && !keyguard.isKeyguardLocked();
    }

    static boolean start(Context context, XposedInterface xposed,
                         SharedPreferences preferences) throws Throwable {
        if (!STARTED.compareAndSet(false, true)) {
            return false;
        }
        try {
            UsbStateController controller = new UsbStateController(context, xposed, preferences);
            controller.register();
            preferences.registerOnSharedPreferenceChangeListener(
                    (ignored, key) -> controller.evaluateNow());
            instance = controller;
            xposed.log(Log.INFO, TAG, "Event-driven controller registered");
            controller.evaluateNow();
            return true;
        } catch (Throwable error) {
            STARTED.set(false);
            throw error;
        }
    }

    static void notifyForegroundChanged() {
        UsbStateController controller = instance;
        if (controller != null
                && Config.MODE_FOREGROUND.equals(Config.gameMode(controller.preferences))) {
            controller.evaluateNow();
        }
    }

    static void notifyUsbStateChanged(String state) {
        UsbStateController controller = instance;
        UsbConnectionState.Gadget gadget = UsbConnectionState.Gadget.fromKernel(state);
        if (controller != null && gadget != UsbConnectionState.Gadget.UNKNOWN) {
            controller.handler.post(() -> {
                controller.rawGadgetState = gadget;
                controller.refreshUsbConnection();
            });
        }
    }

    private void register() {
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_STATE);
        filter.addAction(ACTION_USB_PORT_CHANGED);
        filter.addAction(Intent.ACTION_USER_PRESENT);
        filter.addAction(Intent.ACTION_SCREEN_ON);
        filter.addAction(Intent.ACTION_SCREEN_OFF);
        filter.addAction(Intent.ACTION_CONFIGURATION_CHANGED);
        filter.addAction(Intent.ACTION_BOOT_COMPLETED);

        BroadcastReceiver receiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context ignored, Intent intent) {
                String action = intent.getAction();
                if (ACTION_USB_STATE.equals(action) || ACTION_USB_PORT_CHANGED.equals(action)) {
                    boolean broadcastConnected = intent.getBooleanExtra("connected", false);
                    boolean broadcastConfigured = intent.getBooleanExtra("configured", false);
                    handler.post(() -> handleUsbConnectionEvent(
                            action, broadcastConnected, broadcastConfigured));
                    return;
                }
                handler.post(() -> {
                    if (Intent.ACTION_USER_PRESENT.equals(action)) {
                        unlockedSinceBoot = true;
                    }
                    evaluateNow();
                });
            }
        };

        Intent sticky;
        if (Build.VERSION.SDK_INT >= 33) {
            sticky = context.registerReceiver(receiver, filter, Context.RECEIVER_NOT_EXPORTED);
        } else {
            sticky = context.registerReceiver(receiver, filter);
        }
        if (sticky != null && ACTION_USB_STATE.equals(sticky.getAction())) {
            boolean connected = sticky.getBooleanExtra("connected", false);
            boolean configured = sticky.getBooleanExtra("configured", false);
            handler.post(() -> handleUsbConnectionEvent(ACTION_USB_STATE, connected, configured));
        } else {
            handler.post(this::refreshUsbConnection);
        }
    }

    private void handleUsbConnectionEvent(String action, boolean broadcastConnected,
                                          boolean broadcastConfigured) {
        if (ACTION_USB_STATE.equals(action)) {
            broadcastGadgetState = UsbConnectionState.Gadget.fromBroadcast(
                    broadcastConnected, broadcastConfigured);
        }
        refreshUsbConnection();
    }

    private void refreshUsbConnection() {
        UsbConnectionState.Gadget gadget = UsbConnectionState.Gadget.resolve(
                readKernelUsbState(), rawGadgetState, broadcastGadgetState);
        UsbConnectionState.Port port = readUsbPortState();
        boolean wasConnected = connection.isConnected();
        long delay = connection.update(gadget, port, SystemClock.uptimeMillis());
        handler.removeCallbacks(disconnectTask);
        if (delay >= 0L) {
            handler.postDelayed(disconnectTask, delay);
        }
        if (wasConnected != connection.isConnected()) {
            xposed.log(Log.INFO, TAG, "USB data connection changed: " + connection.isConnected()
                    + ", gadget=" + gadget + ", port=" + port);
        }
        evaluateNow();
    }

    private UsbConnectionState.Gadget readKernelUsbState() {
        try (BufferedReader reader = new BufferedReader(new FileReader(USB_STATE_PATH))) {
            return UsbConnectionState.Gadget.fromKernel(reader.readLine());
        } catch (IOException | SecurityException ignored) {
            return UsbConnectionState.Gadget.UNKNOWN;
        }
    }

    private UsbConnectionState.Port readUsbPortState() {
        try {
            UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
            if (usbManager == null) {
                return UsbConnectionState.Port.UNKNOWN;
            }
            Method getPorts = findMethod(usbManager.getClass(), "getPorts");
            if (getPorts == null) {
                return UsbConnectionState.Port.UNKNOWN;
            }
            getPorts.setAccessible(true);
            Object ports = getPorts.invoke(usbManager);
            int count;
            if (ports instanceof List<?>) {
                count = ((List<?>) ports).size();
            } else if (ports != null && ports.getClass().isArray()) {
                count = Array.getLength(ports);
            } else {
                return UsbConnectionState.Port.UNKNOWN;
            }
            if (count == 0) {
                return UsbConnectionState.Port.UNKNOWN;
            }

            UsbConnectionState.Port result = UsbConnectionState.Port.DETACHED;
            boolean hasUnknownStatus = false;
            for (int index = 0; index < count; index++) {
                Object port = ports instanceof List<?>
                        ? ((List<?>) ports).get(index)
                        : Array.get(ports, index);
                if (port == null) {
                    hasUnknownStatus = true;
                    continue;
                }
                Method getStatus = findMethod(port.getClass(), "getStatus");
                if (getStatus == null) {
                    hasUnknownStatus = true;
                    continue;
                }
                getStatus.setAccessible(true);
                Object status = getStatus.invoke(port);
                if (status == null) {
                    hasUnknownStatus = true;
                    continue;
                }
                Method isConnected = findMethod(status.getClass(), "isConnected");
                if (isConnected == null) {
                    hasUnknownStatus = true;
                    continue;
                }
                isConnected.setAccessible(true);
                Object value = isConnected.invoke(status);
                if (!(value instanceof Boolean)) {
                    hasUnknownStatus = true;
                    continue;
                }
                if ((Boolean) value) {
                    Method getCurrentDataRole = findMethod(status.getClass(),
                            "getCurrentDataRole");
                    if (getCurrentDataRole == null) {
                        hasUnknownStatus = true;
                        continue;
                    }
                    getCurrentDataRole.setAccessible(true);
                    Object dataRole = getCurrentDataRole.invoke(status);
                    if (!(dataRole instanceof Number)) {
                        hasUnknownStatus = true;
                        continue;
                    }
                    int currentDataRole = ((Number) dataRole).intValue();
                    if (currentDataRole == USB_DATA_ROLE_DEVICE) {
                        if (hasMissingDataLines(status) || isPowerBrick(status)) {
                            result = UsbConnectionState.Port.CHARGER;
                            continue;
                        }
                        return UsbConnectionState.Port.DEVICE;
                    }
                    if (currentDataRole == USB_DATA_ROLE_HOST) {
                        result = UsbConnectionState.Port.HOST;
                        continue;
                    }
                    hasUnknownStatus = true;
                    continue;
                }
            }
            return hasUnknownStatus ? UsbConnectionState.Port.UNKNOWN : result;
        } catch (Throwable ignored) {
            return UsbConnectionState.Port.UNKNOWN;
        }
    }

    private boolean isPowerBrick(Object status) throws ReflectiveOperationException {
        Method method = findMethod(status.getClass(), "getPowerBrickConnectionStatus");
        if (method == null) {
            return false;
        }
        method.setAccessible(true);
        Object value = method.invoke(status);
        return value instanceof Number
                && ((Number) value).intValue() == USB_POWER_BRICK_STATUS_CONNECTED;
    }

    private boolean hasMissingDataLines(Object status) throws ReflectiveOperationException {
        Method method = findMethod(status.getClass(), "getComplianceWarnings");
        if (method == null) {
            return false;
        }
        method.setAccessible(true);
        Object warnings = method.invoke(status);
        if (warnings == null || !warnings.getClass().isArray()) {
            return false;
        }
        for (int index = 0; index < Array.getLength(warnings); index++) {
            Object warning = Array.get(warnings, index);
            if (warning instanceof Number
                    && ((Number) warning).intValue() == USB_COMPLIANCE_WARNING_MISSING_DATA_LINES) {
                return true;
            }
        }
        return false;
    }

    private void evaluate() {
        try {
            KeyguardManager keyguardManager = (KeyguardManager) context.getSystemService(
                    Context.KEYGUARD_SERVICE);
            boolean locked = keyguardManager != null && keyguardManager.isKeyguardLocked();
            if (!locked) {
                unlockedSinceBoot = true;
            }

            boolean gameBlocked = isGameBlocked();
            boolean enableAdb = Config.autoAdb(preferences)
                    && connection.isConnected()
                    && unlockedSinceBoot
                    && !gameBlocked;
            boolean enableMtp = Config.autoMtp(preferences)
                    && connection.isConnected()
                    && (!Config.mtpUnlockOnly(preferences) || !locked);

            connection.setFunctionsPaused(!enableAdb && !enableMtp);
            applyAdbSetting(enableAdb);
            applyUsbFunctions(enableMtp);
        } catch (Throwable error) {
            xposed.log(Log.ERROR, TAG, "State evaluation failed", error);
        }
    }

    private void evaluateNow() {
        handler.removeCallbacks(evaluateTask);
        handler.post(evaluateTask);
    }

    private boolean isGameBlocked() {
        String mode = Config.gameMode(preferences);
        if (Config.MODE_DISABLED.equals(mode)) {
            return false;
        }
        if (Config.MODE_ORIENTATION.equals(mode)) {
            return context.getResources().getConfiguration().orientation
                    == Configuration.ORIENTATION_LANDSCAPE;
        }

        ActivityManager manager = (ActivityManager) context.getSystemService(Context.ACTIVITY_SERVICE);
        if (manager == null) {
            return false;
        }
        List<ActivityManager.RunningTaskInfo> tasks = manager.getRunningTasks(1);
        if (tasks == null || tasks.isEmpty()) {
            return false;
        }
        ComponentName top = tasks.get(0).topActivity;
        return top != null && Config.gamePackages(preferences).contains(top.getPackageName());
    }

    private void applyAdbSetting(boolean enabled) {
        int desired = enabled ? 1 : 0;
        int current = Settings.Global.getInt(
                context.getContentResolver(), Settings.Global.ADB_ENABLED, -1);
        if (current == desired) {
            return;
        }
        connection.onFunctionsChanged(SystemClock.uptimeMillis());
        if (Settings.Global.putInt(context.getContentResolver(), Settings.Global.ADB_ENABLED, desired)) {
            xposed.log(Log.INFO, TAG, "ADB " + (enabled ? "enabled" : "disabled"));
        }
    }

    private void applyUsbFunctions(boolean mtp) throws ReflectiveOperationException {
        UsbManager usbManager = (UsbManager) context.getSystemService(Context.USB_SERVICE);
        if (usbManager == null) {
            return;
        }

        Method getter = findMethod(usbManager.getClass(), "getCurrentFunctions");
        Method setter = findMethod(usbManager.getClass(), "setCurrentFunctions", long.class);
        if (getter == null || setter == null) {
            return;
        }
        getter.setAccessible(true);
        setter.setAccessible(true);

        long current = ((Number) getter.invoke(usbManager)).longValue();
        long mtpBit = usbFunction("FUNCTION_MTP", 4L);
        long desired = setBit(current, mtpBit, mtp);
        if (desired == current) {
            return;
        }

        connection.onFunctionsChanged(SystemClock.uptimeMillis());
        setter.invoke(usbManager, desired);
        xposed.log(Log.INFO, TAG, "Primary USB function changed: mtp=" + mtp);
    }

    private static long setBit(long value, long bit, boolean enabled) {
        return enabled ? value | bit : value & ~bit;
    }

    private static long usbFunction(String name, long fallback) {
        try {
            Field field = UsbManager.class.getDeclaredField(name);
            field.setAccessible(true);
            return field.getLong(null);
        } catch (ReflectiveOperationException ignored) {
            return fallback;
        }
    }

    private static Method findMethod(Class<?> type, String name, Class<?>... parameters) {
        for (Class<?> current = type; current != null; current = current.getSuperclass()) {
            try {
                return current.getDeclaredMethod(name, parameters);
            } catch (NoSuchMethodException ignored) {
            }
        }
        return null;
    }
}
