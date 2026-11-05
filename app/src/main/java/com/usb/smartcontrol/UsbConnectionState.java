package com.usb.smartcontrol;

final class UsbConnectionState {
    static final long DISCONNECT_DEBOUNCE_MS = 350L;
    static final long FUNCTION_SWITCH_GRACE_MS = 3000L;

    enum Gadget {
        UNKNOWN, DISCONNECTED, CONNECTED, CONFIGURED;

        static Gadget fromKernel(String state) {
            if (state != null) {
                for (Gadget value : values()) {
                    if (value.name().equals(state.trim())) {
                        return value;
                    }
                }
            }
            return UNKNOWN;
        }

        static Gadget fromBroadcast(boolean connected, boolean configured) {
            if (!connected) {
                return DISCONNECTED;
            }
            return configured ? CONFIGURED : UNKNOWN;
        }

        static Gadget resolve(Gadget kernel, Gadget rawEvent, Gadget broadcast) {
            if (kernel != UNKNOWN) {
                return kernel;
            }
            return rawEvent != UNKNOWN ? rawEvent : broadcast;
        }
    }

    enum Port {
        UNKNOWN, DETACHED, DEVICE, CHARGER, HOST
    }

    private boolean connected;
    private boolean functionsPaused;
    private long disconnectSince = -1L;
    private long functionSwitchUntil = -1L;

    boolean isConnected() {
        return connected;
    }

    void setFunctionsPaused(boolean paused) {
        functionsPaused = paused;
    }

    void onFunctionsChanged(long now) {
        if (connected) {
            functionSwitchUntil = now + FUNCTION_SWITCH_GRACE_MS;
        }
    }

    long update(Gadget gadget, Port port, long now) {
        boolean blocked = port == Port.DETACHED || port == Port.CHARGER || port == Port.HOST;
        if (!blocked && (gadget == Gadget.CONNECTED || gadget == Gadget.CONFIGURED)) {
            connected = true;
            disconnectSince = -1L;
            return -1L;
        }
        if (connected && functionsPaused && port == Port.DEVICE) {
            disconnectSince = -1L;
            return -1L;
        }
        if (!blocked && gadget == Gadget.UNKNOWN && disconnectSince < 0L) {
            return -1L;
        }
        if (!connected) {
            disconnectSince = -1L;
            functionSwitchUntil = -1L;
            return -1L;
        }
        if (disconnectSince < 0L) {
            disconnectSince = now;
        }
        long deadline = disconnectSince + DISCONNECT_DEBOUNCE_MS;
        if (!blocked) {
            deadline = Math.max(deadline, functionSwitchUntil);
        }
        if (now < deadline) {
            return deadline - now;
        }
        connected = false;
        disconnectSince = -1L;
        functionSwitchUntil = -1L;
        return -1L;
    }
}
