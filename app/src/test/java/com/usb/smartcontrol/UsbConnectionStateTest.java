package com.usb.smartcontrol;

import static com.usb.smartcontrol.UsbConnectionState.Gadget.CONFIGURED;
import static com.usb.smartcontrol.UsbConnectionState.Gadget.CONNECTED;
import static com.usb.smartcontrol.UsbConnectionState.Gadget.DISCONNECTED;
import static com.usb.smartcontrol.UsbConnectionState.Gadget.UNKNOWN;
import static com.usb.smartcontrol.UsbConnectionState.Port.CHARGER;
import static com.usb.smartcontrol.UsbConnectionState.Port.DETACHED;
import static com.usb.smartcontrol.UsbConnectionState.Port.DEVICE;
import static com.usb.smartcontrol.UsbConnectionState.Port.HOST;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class UsbConnectionStateTest {
    private final UsbConnectionState state = new UsbConnectionState();

    @Test
    public void portDeviceRoleWithoutHandshakeDoesNotEnableData() {
        state.update(DISCONNECTED, DEVICE, 0L);
        state.update(DISCONNECTED, DEVICE, 100L);
        assertFalse(state.isConnected());
    }

    @Test
    public void pyb110TransientNonPowerBrickStatusDoesNotEnableData() {
        state.update(DISCONNECTED, DETACHED, 0L);
        state.update(DISCONNECTED, DEVICE, 100L);
        state.update(DISCONNECTED, DEVICE, 400L);
        state.update(DISCONNECTED, CHARGER, 500L);
        assertFalse(state.isConnected());
    }

    @Test
    public void unknownGadgetAndDeviceRoleDoNotEnableData() {
        state.update(UNKNOWN, DEVICE, 0L);
        assertFalse(state.isConnected());
    }

    @Test
    public void rawHostHandshakeEnablesImmediately() {
        assertEquals(-1L, state.update(CONNECTED, DEVICE, 0L));
        assertTrue(state.isConnected());
    }

    @Test
    public void plk110UnknownPowerBrickDoesNotBlockConfiguredConnection() {
        state.update(CONFIGURED, DEVICE, 0L);
        assertTrue(state.isConnected());
    }

    @Test
    public void unavailablePortStatusDoesNotBlockConfirmedData() {
        state.update(CONFIGURED, UsbConnectionState.Port.UNKNOWN, 0L);
        assertTrue(state.isConnected());
    }

    @Test
    public void chargingPortRejectsStaleConfiguredState() {
        state.update(CONFIGURED, CHARGER, 0L);
        assertFalse(state.isConnected());
    }

    @Test
    public void detachedPortRejectsStaleConfiguredState() {
        state.update(CONFIGURED, DETACHED, 0L);
        assertFalse(state.isConnected());
    }

    @Test
    public void hostRoleDoesNotEnableDeviceFunctions() {
        state.update(CONNECTED, HOST, 0L);
        assertFalse(state.isConnected());
    }

    @Test
    public void disconnectCompletesAfter350Milliseconds() {
        state.update(CONFIGURED, DEVICE, 0L);
        assertEquals(350L, state.update(DISCONNECTED, DETACHED, 100L));
        assertEquals(1L, state.update(DISCONNECTED, DETACHED, 449L));
        assertTrue(state.isConnected());
        assertEquals(-1L, state.update(DISCONNECTED, DETACHED, 450L));
        assertFalse(state.isConnected());
    }

    @Test
    public void repeatedDisconnectEventsDoNotExtendDeadline() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.update(DISCONNECTED, DETACHED, 100L);
        assertEquals(250L, state.update(DISCONNECTED, DETACHED, 200L));
        assertEquals(100L, state.update(DISCONNECTED, DETACHED, 350L));
        state.update(DISCONNECTED, DETACHED, 450L);
        assertFalse(state.isConnected());
    }

    @Test
    public void reconnectCancelsPendingDisconnect() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.update(DISCONNECTED, DETACHED, 100L);
        assertEquals(-1L, state.update(CONNECTED, DEVICE, 300L));
        state.update(CONFIGURED, DEVICE, 600L);
        assertTrue(state.isConnected());
    }

    @Test
    public void ownFunctionSwitchPreservesDataDuringReenumeration() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.onFunctionsChanged(100L);
        assertEquals(2900L, state.update(DISCONNECTED, DEVICE, 200L));
        state.update(DISCONNECTED, DEVICE, 700L);
        state.update(DISCONNECTED, DEVICE, 2200L);
        assertTrue(state.isConnected());
        assertEquals(-1L, state.update(CONFIGURED, DEVICE, 2300L));
        assertTrue(state.isConnected());
    }

    @Test
    public void physicalUnplugBypassesFunctionSwitchGrace() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.onFunctionsChanged(100L);
        assertEquals(350L, state.update(DISCONNECTED, DETACHED, 200L));
        state.update(DISCONNECTED, DETACHED, 550L);
        assertFalse(state.isConnected());
    }

    @Test
    public void switchingToChargerBypassesFunctionSwitchGrace() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.onFunctionsChanged(100L);
        state.update(DISCONNECTED, CHARGER, 200L);
        state.update(DISCONNECTED, CHARGER, 550L);
        assertFalse(state.isConnected());
    }

    @Test
    public void failedReenumerationEventuallyDisablesData() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.onFunctionsChanged(100L);
        state.update(DISCONNECTED, DEVICE, 200L);
        assertEquals(1L, state.update(DISCONNECTED, DEVICE, 3099L));
        state.update(DISCONNECTED, DEVICE, 3100L);
        assertFalse(state.isConnected());
    }

    @Test
    public void functionChangeWithoutConnectionDoesNotDelayFutureDisconnect() {
        state.onFunctionsChanged(0L);
        state.update(CONFIGURED, DEVICE, 100L);
        assertEquals(350L, state.update(DISCONNECTED, DEVICE, 200L));
        state.update(DISCONNECTED, DEVICE, 550L);
        assertFalse(state.isConnected());
    }

    @Test
    public void unknownSnapshotPreservesEstablishedConnection() {
        state.update(CONFIGURED, DEVICE, 0L);
        assertEquals(-1L, state.update(UNKNOWN, DEVICE, 100L));
        assertTrue(state.isConnected());
    }

    @Test
    public void unknownSnapshotDoesNotCancelPendingDisconnect() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.update(DISCONNECTED, DEVICE, 100L);
        state.update(UNKNOWN, DEVICE, 450L);
        assertFalse(state.isConnected());
    }

    @Test
    public void nextConnectionDoesNotInheritExpiredGrace() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.onFunctionsChanged(100L);
        state.update(DISCONNECTED, DETACHED, 200L);
        state.update(DISCONNECTED, DETACHED, 550L);
        state.update(CONFIGURED, DEVICE, 600L);
        assertEquals(350L, state.update(DISCONNECTED, DEVICE, 700L));
        state.update(DISCONNECTED, DEVICE, 1050L);
        assertFalse(state.isConnected());
    }

    @Test
    public void broadcastNeedsConfiguredStateToEnableData() {
        state.update(UsbConnectionState.Gadget.fromBroadcast(true, false), DEVICE, 0L);
        assertFalse(state.isConnected());
        state.update(UsbConnectionState.Gadget.fromBroadcast(true, true), DEVICE, 100L);
        assertTrue(state.isConnected());
    }

    @Test
    public void invalidKernelStatesRemainUnknown() {
        assertEquals(UNKNOWN, UsbConnectionState.Gadget.fromKernel(null));
        assertEquals(UNKNOWN, UsbConnectionState.Gadget.fromKernel("suspended"));
        assertEquals(UNKNOWN, UsbConnectionState.Gadget.fromKernel(""));
        assertEquals(CONFIGURED, UsbConnectionState.Gadget.fromKernel("CONFIGURED\n"));
    }

    @Test
    public void disconnectedBroadcastCannotClaimConfiguredData() {
        assertEquals(DISCONNECTED, UsbConnectionState.Gadget.fromBroadcast(false, true));
    }

    @Test
    public void staleConfiguredBroadcastCannotOverrideCurrentKernelDisconnect() {
        assertEquals(DISCONNECTED, UsbConnectionState.Gadget.resolve(
                DISCONNECTED, CONFIGURED, CONFIGURED));
    }

    @Test
    public void rawDisconnectTakesPriorityWhenSysfsIsUnavailable() {
        assertEquals(DISCONNECTED, UsbConnectionState.Gadget.resolve(
                UNKNOWN, DISCONNECTED, CONFIGURED));
    }

    @Test
    public void configuredBroadcastRemainsCompatibleWithoutKernelAccess() {
        assertEquals(CONFIGURED, UsbConnectionState.Gadget.resolve(
                UNKNOWN, UNKNOWN, CONFIGURED));
    }

    @Test
    public void gameOrLockPausePreservesConfirmedHostSession() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.setFunctionsPaused(true);
        state.onFunctionsChanged(100L);
        assertEquals(-1L, state.update(DISCONNECTED, DEVICE, 200L));
        state.update(DISCONNECTED, DEVICE, 10000L);
        assertTrue(state.isConnected());
        state.setFunctionsPaused(false);
        state.onFunctionsChanged(10100L);
        state.update(DISCONNECTED, DEVICE, 10200L);
        state.update(CONFIGURED, DEVICE, 12000L);
        assertTrue(state.isConnected());
    }

    @Test
    public void pausedFunctionsDoNotAuthorizeANewChargingConnection() {
        state.setFunctionsPaused(true);
        state.update(DISCONNECTED, DEVICE, 0L);
        state.update(DISCONNECTED, DEVICE, 10000L);
        assertFalse(state.isConnected());
    }

    @Test
    public void physicalUnplugStillDisablesAPausedConnection() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.setFunctionsPaused(true);
        state.update(DISCONNECTED, DETACHED, 100L);
        state.update(DISCONNECTED, DETACHED, 450L);
        assertFalse(state.isConnected());
    }

    @Test
    public void chargerStatusStillDisablesAPausedConnection() {
        state.update(CONFIGURED, DEVICE, 0L);
        state.setFunctionsPaused(true);
        state.update(DISCONNECTED, CHARGER, 100L);
        state.update(DISCONNECTED, CHARGER, 450L);
        assertFalse(state.isConnected());
    }
}
