package com.usb.smartcontrol;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import java.lang.reflect.Method;

import io.github.libxposed.api.XposedInterface;
import io.github.libxposed.api.XposedModule;
import io.github.libxposed.api.XposedModuleInterface.ModuleLoadedParam;
import io.github.libxposed.api.XposedModuleInterface.SystemServerStartingParam;

public final class UsbSmartModule extends XposedModule {
    private static final String TAG = "UsbSmartControl";
    private static final int PHASE_ACTIVITY_MANAGER_READY = 550;

    @Override
    public void onModuleLoaded(ModuleLoadedParam param) {
        log(Log.INFO, TAG, "API 102 module loaded in " + param.getProcessName());
    }

    @Override
    public void onSystemServerStarting(SystemServerStartingParam param) {
        try {
            installBootPhaseHook(param.getClassLoader());
            installForegroundEventHook(param.getClassLoader());
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to install SystemServer hooks", error);
        }
    }

    private void installBootPhaseHook(ClassLoader classLoader) throws ClassNotFoundException {
        Class<?> type = Class.forName(
                "com.android.server.SystemServiceManager", false, classLoader);
        int installed = 0;
        for (Method method : type.getDeclaredMethods()) {
            if (!"startBootPhase".equals(method.getName())) {
                continue;
            }
            method.setAccessible(true);
            String id = "boot-phase-" + Integer.toHexString(method.toGenericString().hashCode());
            hook(method)
                    .setId(id)
                    .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                    .intercept(chain -> {
                        Object result = chain.proceed();
                        int phase = bootPhase(chain.getArgs().toArray());
                        if (phase >= PHASE_ACTIVITY_MANAGER_READY) {
                            startController();
                        }
                        return result;
                    });
            installed++;
        }
        if (installed == 0) {
            throw new NoSuchMethodError("SystemServiceManager.startBootPhase");
        }
        log(Log.INFO, TAG, "Boot phase hook installed");
    }

    private void startController() {
        try {
            Context context = findSystemContext();
            if (context == null) {
                log(Log.ERROR, TAG, "System context unavailable after ActivityManager ready");
                return;
            }
            SharedPreferences preferences = getRemotePreferences(Config.GROUP);
            if (UsbStateController.start(context, this, preferences)) {
                log(Log.INFO, TAG, "SystemServer controller started");
            }
        } catch (Throwable error) {
            log(Log.ERROR, TAG, "Unable to start SystemServer controller", error);
        }
    }

    private static int bootPhase(Object[] arguments) {
        for (int index = arguments.length - 1; index >= 0; index--) {
            if (arguments[index] instanceof Integer) {
                return (Integer) arguments[index];
            }
        }
        return -1;
    }

    private void installForegroundEventHook(ClassLoader classLoader) {
        String[][] candidates = {
                {"com.android.server.wm.ActivityTaskSupervisor", "updateTopResumedActivityIfNeeded"},
                {"com.android.server.wm.ActivityTaskManagerService", "setResumedActivityUncheckLocked"},
                {"com.android.server.wm.RootWindowContainer", "resumeFocusedTasksTopActivities"}
        };
        int installed = 0;
        for (String[] candidate : candidates) {
            try {
                Class<?> type = Class.forName(candidate[0], false, classLoader);
                for (Method method : type.getDeclaredMethods()) {
                    if (!candidate[1].equals(method.getName())) {
                        continue;
                    }
                    method.setAccessible(true);
                    String id = "foreground-event-" + Integer.toHexString(
                            method.toGenericString().hashCode());
                    hook(method)
                            .setId(id)
                            .setExceptionMode(XposedInterface.ExceptionMode.PROTECTIVE)
                            .intercept(chain -> {
                                Object result = chain.proceed();
                                UsbStateController.notifyForegroundChanged();
                                return result;
                            });
                    installed++;
                }
            } catch (Throwable ignored) {
            }
            if (installed > 0) {
                break;
            }
        }
        log(installed > 0 ? Log.INFO : Log.WARN, TAG,
                installed > 0
                        ? "Foreground event hook installed"
                        : "No compatible foreground event hook found");
    }

    private static Context findSystemContext() throws ReflectiveOperationException {
        Class<?> activityThread = Class.forName("android.app.ActivityThread");
        Method currentActivityThread = activityThread.getDeclaredMethod("currentActivityThread");
        Method getSystemContext = activityThread.getDeclaredMethod("getSystemContext");
        currentActivityThread.setAccessible(true);
        getSystemContext.setAccessible(true);
        Object thread = currentActivityThread.invoke(null);
        Object context = thread == null ? null : getSystemContext.invoke(thread);
        return context instanceof Context ? (Context) context : null;
    }
}
