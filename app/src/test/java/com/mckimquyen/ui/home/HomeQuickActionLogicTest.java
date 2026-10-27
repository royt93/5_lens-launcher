package com.mckimquyen.ui.home;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import com.mckimquyen.R;

import org.junit.Test;

public class HomeQuickActionLogicTest {
    @Test
    public void requestCodesKeepTheirPublishedValues() {
        // Instrumentation tests drive onRequestPermissionsResult with these literals.
        assertEquals(1001, HomeQuickActionDelegate.REQUEST_CODE_CAMERA);
        assertEquals(1002, HomeQuickActionDelegate.REQUEST_CODE_WIFI_SSID);
        assertEquals(1003, HomeQuickActionDelegate.REQUEST_CODE_CONTACTS);
    }

    @Test
    public void cameraResultTogglesOnlyWhenGranted() {
        assertEquals(HomeQuickActionDelegate.PermissionAction.TOGGLE_FLASHLIGHT,
                HomeQuickActionDelegate.routePermissionResult(HomeQuickActionDelegate.REQUEST_CODE_CAMERA, true));
        assertEquals(HomeQuickActionDelegate.PermissionAction.REFRESH_QUICK_ACTION,
                HomeQuickActionDelegate.routePermissionResult(HomeQuickActionDelegate.REQUEST_CODE_CAMERA, false));
    }

    @Test
    public void wifiResultAlwaysRefreshesTheRow() {
        assertEquals(HomeQuickActionDelegate.PermissionAction.REFRESH_QUICK_ACTION,
                HomeQuickActionDelegate.routePermissionResult(HomeQuickActionDelegate.REQUEST_CODE_WIFI_SSID, true));
        assertEquals(HomeQuickActionDelegate.PermissionAction.REFRESH_QUICK_ACTION,
                HomeQuickActionDelegate.routePermissionResult(HomeQuickActionDelegate.REQUEST_CODE_WIFI_SSID, false));
    }

    @Test
    public void contactsResultAlwaysRefreshesTheContactRow() {
        assertEquals(HomeQuickActionDelegate.PermissionAction.REFRESH_CONTACT,
                HomeQuickActionDelegate.routePermissionResult(HomeQuickActionDelegate.REQUEST_CODE_CONTACTS, true));
        assertEquals(HomeQuickActionDelegate.PermissionAction.REFRESH_CONTACT,
                HomeQuickActionDelegate.routePermissionResult(HomeQuickActionDelegate.REQUEST_CODE_CONTACTS, false));
    }

    @Test
    public void anUnknownRequestCodeDoesNothing() {
        assertEquals(HomeQuickActionDelegate.PermissionAction.NONE,
                HomeQuickActionDelegate.routePermissionResult(0, true));
        assertEquals(HomeQuickActionDelegate.PermissionAction.NONE,
                HomeQuickActionDelegate.routePermissionResult(1004, false));
    }

    @Test
    public void everyKnownRequestCodeMapsToItsFlagKeyAndPermission() {
        assertEquals("flashlight_permission_requested",
                HomeQuickActionDelegate.flagKeyFor(HomeQuickActionDelegate.REQUEST_CODE_CAMERA));
        assertEquals("android.permission.CAMERA",
                HomeQuickActionDelegate.permissionFor(HomeQuickActionDelegate.REQUEST_CODE_CAMERA));
        assertEquals("wifi_ssid_permission_requested",
                HomeQuickActionDelegate.flagKeyFor(HomeQuickActionDelegate.REQUEST_CODE_WIFI_SSID));
        assertEquals("android.permission.ACCESS_FINE_LOCATION",
                HomeQuickActionDelegate.permissionFor(HomeQuickActionDelegate.REQUEST_CODE_WIFI_SSID));
        assertEquals("contacts_permission_requested",
                HomeQuickActionDelegate.flagKeyFor(HomeQuickActionDelegate.REQUEST_CODE_CONTACTS));
        assertEquals("android.permission.READ_CONTACTS",
                HomeQuickActionDelegate.permissionFor(HomeQuickActionDelegate.REQUEST_CODE_CONTACTS));
    }

    @Test
    public void anUnknownRequestCodeHasNoFlagKeyOrPermission() {
        assertNull(HomeQuickActionDelegate.flagKeyFor(0));
        assertNull(HomeQuickActionDelegate.permissionFor(0));
    }

    @Test
    public void grantedNeedsAFirstResultThatIsGranted() {
        assertTrue(HomeQuickActionDelegate.isGranted(new int[]{0}));
        assertFalse(HomeQuickActionDelegate.isGranted(new int[]{-1}));
        assertFalse(HomeQuickActionDelegate.isGranted(new int[0]));
    }

    @Test
    public void theTorchFlipsExactlyOnce() {
        assertTrue(HomeQuickActionDelegate.nextTorchState(false));
        assertFalse(HomeQuickActionDelegate.nextTorchState(true));
    }

    @Test
    public void theOnOffLabelFollowsTheState() {
        assertEquals(R.string.quick_action_flashlight_on, HomeQuickActionDelegate.onOffLabelRes(true));
        assertEquals(R.string.quick_action_flashlight_off, HomeQuickActionDelegate.onOffLabelRes(false));
    }
}
