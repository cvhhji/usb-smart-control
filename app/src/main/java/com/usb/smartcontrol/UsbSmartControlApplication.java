package com.usb.smartcontrol;

import android.app.Application;
import android.os.Handler;
import android.os.Looper;

import java.lang.ref.WeakReference;

import io.github.libxposed.service.XposedService;
import io.github.libxposed.service.XposedServiceHelper;

public final class UsbSmartControlApplication extends Application implements XposedServiceHelper.OnServiceListener {
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private volatile XposedService xposedService;
    private WeakReference<SettingsActivity> activityReference = new WeakReference<>(null);

    @Override
    public void onCreate() {
        super.onCreate();
        XposedServiceHelper.registerListener(this);
    }

    @Override
    public void onServiceBind(XposedService service) {
        xposedService = service;
        notifyActivity();
    }

    @Override
    public void onServiceDied(XposedService service) {
        if (xposedService == service) {
            xposedService = null;
            notifyActivity();
        }
    }

    XposedService getXposedService() {
        return xposedService;
    }

    void attach(SettingsActivity activity) {
        activityReference = new WeakReference<>(activity);
    }

    void detach(SettingsActivity activity) {
        if (activityReference.get() == activity) {
            activityReference.clear();
        }
    }

    private void notifyActivity() {
        mainHandler.post(() -> {
            SettingsActivity activity = activityReference.get();
            if (activity != null) {
                activity.onXposedServiceChanged(xposedService);
            }
        });
    }
}
