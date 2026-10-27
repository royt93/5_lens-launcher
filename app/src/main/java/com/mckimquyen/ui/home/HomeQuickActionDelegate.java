package com.mckimquyen.ui.home;

import android.Manifest;
import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.mckimquyen.R;
import com.mckimquyen.search.ContactSearchResult;
import com.mckimquyen.search.QuickAction;
import com.mckimquyen.search.QuickActionEngine;
import com.mckimquyen.util.UtilSettings;

/** ARCH-002 cluster 2: quick-action row, flashlight, Wi-Fi SSID, DND and contact row of ActHome. */
public final class HomeQuickActionDelegate {
    private final HomeQuickActionHost host;

    private View quickActionRow;
    private View quickActionDivider;
    private TextView tvQuickActionLabel;
    private TextView tvQuickActionValue;
    private View contactActionRow;
    private View contactActionDivider;
    private TextView tvContactResultName;
    private TextView tvContactResultPhone;
    private Button btContactCall;
    private Button btContactMessage;
    private com.mckimquyen.search.ContactSearchController contactSearchController;

    // SEARCH-004: request codes for the permission-gated quick actions. Tests send these literals.
    public static final int REQUEST_CODE_CAMERA = 1001;
    public static final int REQUEST_CODE_WIFI_SSID = 1002;
    public static final int REQUEST_CODE_CONTACTS = 1003;

    /** What ActHome must do after a permission dialog answered. */
    public enum PermissionAction { TOGGLE_FLASHLIGHT, REFRESH_QUICK_ACTION, REFRESH_CONTACT, NONE }

    public HomeQuickActionDelegate(HomeQuickActionHost host) {
        this.host = host;
    }

    private Activity activity() {
        return host.activity();
    }

    /** Finds the row views and starts the contact controller (the old ActHome.setupViews lines). */
    public void attach() {
        Activity activity = activity();
        quickActionRow = activity.findViewById(R.id.quickActionRow);
        quickActionDivider = activity.findViewById(R.id.quickActionDivider);
        tvQuickActionLabel = activity.findViewById(R.id.tvQuickActionLabel);
        tvQuickActionValue = activity.findViewById(R.id.tvQuickActionValue);
        contactActionRow = activity.findViewById(R.id.contactActionRow);
        contactActionDivider = activity.findViewById(R.id.contactActionDivider);
        tvContactResultName = activity.findViewById(R.id.tvContactResultName);
        tvContactResultPhone = activity.findViewById(R.id.tvContactResultPhone);
        btContactCall = activity.findViewById(R.id.btContactCall);
        btContactMessage = activity.findViewById(R.id.btContactMessage);
        contactSearchController = com.mckimquyen.search.ContactSearchController.create(
                activity,
                state -> {
                    renderContactSearchState(state);
                    return kotlin.Unit.INSTANCE;
                });
    }

    /** ActHome.onDestroy lines for the contact controller. */
    public void onDestroy() {
        if (contactSearchController != null) {
            contactSearchController.close();
            contactSearchController = null;
        }
    }

    public boolean quickActionRowVisible() {
        return quickActionRow.getVisibility() == View.VISIBLE;
    }

    public boolean contactActionRowVisible() {
        return contactActionRow.getVisibility() == View.VISIBLE;
    }

    /** Body of the old ActHome.onRequestPermissionsResult after super: record the answer, then refresh. */
    public void onPermissionResult(int requestCode, String[] permissions, int[] grantResults) {
        String flagKey = flagKeyFor(requestCode);
        if (flagKey != null) {
            com.mckimquyen.search.PermissionAskedFlags.recordResult(activity(), flagKey, permissions, grantResults,
                    permissionFor(requestCode));
        }
        switch (routePermissionResult(requestCode, isGranted(grantResults))) {
            case TOGGLE_FLASHLIGHT:
                performFlashlightToggle();
                break;
            case REFRESH_QUICK_ACTION:
                updateQuickAction(host.searchText());
                break;
            case REFRESH_CONTACT:
                updateContactAction(host.searchText());
                break;
            default:
                break;
        }
    }

    public static PermissionAction routePermissionResult(int requestCode, boolean granted) {
        if (requestCode == REQUEST_CODE_CAMERA) {
            return granted ? PermissionAction.TOGGLE_FLASHLIGHT : PermissionAction.REFRESH_QUICK_ACTION;
        } else if (requestCode == REQUEST_CODE_WIFI_SSID) {
            return PermissionAction.REFRESH_QUICK_ACTION;
        } else if (requestCode == REQUEST_CODE_CONTACTS) {
            return PermissionAction.REFRESH_CONTACT;
        }
        return PermissionAction.NONE;
    }

    /** SharedPreferences key remembering that this permission was already asked; null for an unknown code. */
    public static String flagKeyFor(int requestCode) {
        if (requestCode == REQUEST_CODE_CAMERA) return UtilSettings.KEY_FLASHLIGHT_PERMISSION_REQUESTED;
        if (requestCode == REQUEST_CODE_WIFI_SSID) return UtilSettings.KEY_WIFI_SSID_PERMISSION_REQUESTED;
        if (requestCode == REQUEST_CODE_CONTACTS) return UtilSettings.KEY_CONTACTS_PERMISSION_REQUESTED;
        return null;
    }

    /** The permission whose answer decides the flag; null for an unknown code. */
    public static String permissionFor(int requestCode) {
        if (requestCode == REQUEST_CODE_CAMERA) return Manifest.permission.CAMERA;
        if (requestCode == REQUEST_CODE_WIFI_SSID) return Manifest.permission.ACCESS_FINE_LOCATION;
        if (requestCode == REQUEST_CODE_CONTACTS) return Manifest.permission.READ_CONTACTS;
        return null;
    }

    /** True only when the first result exists and is PERMISSION_GRANTED (an empty result array is a denial). */
    public static boolean isGranted(int[] grantResults) {
        return grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED;
    }

    public static boolean nextTorchState(boolean current) {
        return !current;
    }

    public static int onOffLabelRes(boolean on) {
        return on ? R.string.quick_action_flashlight_on : R.string.quick_action_flashlight_off;
    }

    /**
     * SEARCH-004: CameraManager.setTorchMode() has no public getter for current state, so this
     * app tracks it itself - correct as long as nothing else in the system toggles the torch
     * behind this app's back, an accepted limitation matching how every flashlight app works.
     */
    private boolean flashlightOn = false;

    /**
     * SEARCH-002: show/hide/populate the calculator/unit-conversion/timer/battery%/settings
     * quick-action row above the normal app results, based on what the typed query resolves to.
     */
    public void updateQuickAction(CharSequence query) {
        QuickAction action = QuickActionEngine.resolve(activity(), query);
        boolean show = action != null;
        quickActionRow.setVisibility(show ? View.VISIBLE : View.GONE);
        quickActionDivider.setVisibility(show ? View.VISIBLE : View.GONE);
        if (!show) {
            quickActionRow.setOnClickListener(null);
            return;
        }

        if (action instanceof QuickAction.Info info) {
            tvQuickActionLabel.setText(info.getLabel());
            tvQuickActionValue.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            tvQuickActionValue.setText(info.getValue());
            quickActionRow.setOnClickListener(v -> {
                ClipboardManager clipboard = (ClipboardManager) activity().getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText(info.getLabel(), info.getValue()));
                Toast.makeText(activity(), info.getValue(), Toast.LENGTH_SHORT).show();
            });
        } else if (action instanceof QuickAction.Action quickAction) {
            tvQuickActionLabel.setText(quickAction.getLabel());
            // UI-016: was empty - the row looked like dead space with nothing on the right,
            // compounding why this row read as "not working" (it did; it was just too
            // understated to notice). A trailing chevron signals "this opens something" the
            // same way a tappable list row conventionally does.
            tvQuickActionValue.setText("");
            tvQuickActionValue.setCompoundDrawablesRelativeWithIntrinsicBounds(
                    0, 0, R.drawable.ic_arrow_forward_24dp, 0);
            quickActionRow.setOnClickListener(v -> {
                try {
                    activity().startActivity(quickAction.getIntent());
                    host.hideSearch();
                } catch (ActivityNotFoundException e) {
                    Toast.makeText(activity(), R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
                }
            });
        } else if (action instanceof QuickAction.FlashlightToggle toggle) {
            tvQuickActionLabel.setText(toggle.getLabel());
            tvQuickActionValue.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            tvQuickActionValue.setText(onOffLabelRes(flashlightOn));
            quickActionRow.setOnClickListener(v -> toggleFlashlight());
        } else if (action instanceof QuickAction.WifiSsidPermissionRequest permissionRequest) {
            tvQuickActionLabel.setText(permissionRequest.getLabel());
            tvQuickActionValue.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            tvQuickActionValue.setText(R.string.quick_action_tap_to_allow);
            quickActionRow.setOnClickListener(v -> requestWifiSsidPermission());
        } else if (action instanceof QuickAction.DndAccessRequest dndRequest) {
            tvQuickActionLabel.setText(dndRequest.getLabel());
            tvQuickActionValue.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            tvQuickActionValue.setText(dndRequest.getPreviouslyRequested()
                    ? R.string.quick_action_dnd_denied
                    : R.string.quick_action_tap_to_allow);
            quickActionRow.setOnClickListener(v -> openDndAccessSettings());
        } else if (action instanceof QuickAction.DndToggle dndToggle) {
            tvQuickActionLabel.setText(dndToggle.getLabel());
            tvQuickActionValue.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, 0, 0);
            tvQuickActionValue.setText(onOffLabelRes(dndToggle.isOn()));
            quickActionRow.setOnClickListener(v -> performDndToggle(dndToggle.isOn()));
        }
    }

    private void toggleFlashlight() {
        if (ContextCompat.checkSelfPermission(activity(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                activity(), new String[]{Manifest.permission.CAMERA}, REQUEST_CODE_CAMERA);
            return;
        }
        performFlashlightToggle();
    }

    public void performFlashlightToggle() {
        CameraManager cameraManager = (CameraManager) activity().getSystemService(Context.CAMERA_SERVICE);
        try {
            String torchCameraId = null;
            for (String id : cameraManager.getCameraIdList()) {
                Boolean hasFlash = cameraManager.getCameraCharacteristics(id)
                        .get(CameraCharacteristics.FLASH_INFO_AVAILABLE);
                if (Boolean.TRUE.equals(hasFlash)) {
                    torchCameraId = id;
                    break;
                }
            }
            if (torchCameraId == null) {
                Toast.makeText(activity(), R.string.error_no_flashlight, Toast.LENGTH_SHORT).show();
                return;
            }
            flashlightOn = nextTorchState(flashlightOn);
            cameraManager.setTorchMode(torchCameraId, flashlightOn);
            updateQuickAction(host.searchText());
        } catch (CameraAccessException e) {
            Toast.makeText(activity(), R.string.error_no_flashlight, Toast.LENGTH_SHORT).show();
        }
    }

    private void requestWifiSsidPermission() {
        // Android 12+ requires requesting ACCESS_COARSE_LOCATION alongside FINE (lint
        // CoarseFineLocation) - the user may grant only coarse, in which case
        // QuickActionEngine.resolveWifiSsid still won't read the SSID (needs FINE specifically)
        // and silently falls through, same as an outright denial.
        ActivityCompat.requestPermissions(
                activity(),
                new String[]{Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION},
                REQUEST_CODE_WIFI_SSID
        );
    }

    /**
     * SEARCH-007: DND special access has no requestPermissions() callback - the only way to grant
     * it is this Settings screen, and the only way to detect the result is re-checking
     * isNotificationPolicyAccessGranted() next time the row renders, which onResume() does below.
     */
    private void openDndAccessSettings() {
        new UtilSettings(activity()).save(UtilSettings.KEY_DND_PERMISSION_REQUESTED, true);
        try {
            activity().startActivity(new Intent(android.provider.Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity(), R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
        }
    }

    private void performDndToggle(boolean currentlyOn) {
        android.app.NotificationManager notificationManager =
                (android.app.NotificationManager) activity().getSystemService(Context.NOTIFICATION_SERVICE);
        if (!notificationManager.isNotificationPolicyAccessGranted()) {
            // Access was revoked externally between resolve() and this tap - fall back to the
            // request flow instead of crashing on setInterruptionFilter's SecurityException.
            openDndAccessSettings();
            return;
        }
        notificationManager.setInterruptionFilter(currentlyOn
                ? android.app.NotificationManager.INTERRUPTION_FILTER_ALL
                : android.app.NotificationManager.INTERRUPTION_FILTER_NONE);
        updateQuickAction(host.searchText());
    }

    public void updateContactAction(CharSequence query) {
        if (contactSearchController != null) {
            contactSearchController.onQueryChanged(query);
        }
    }

    private void renderContactSearchState(com.mckimquyen.search.ContactSearchUiState state) {
        if (activity().isFinishing() || activity().isDestroyed()) return;
        if (state instanceof com.mckimquyen.search.ContactSearchUiState.Result) {
            ContactSearchResult contact = ((com.mckimquyen.search.ContactSearchUiState.Result) state).getContact();
            contactActionRow.setVisibility(View.VISIBLE);
            contactActionDivider.setVisibility(View.VISIBLE);
            btContactCall.setText(R.string.contact_search_call);
            btContactMessage.setVisibility(View.VISIBLE);
            tvContactResultName.setText(contact.getDisplayName());
            tvContactResultPhone.setText(contact.getPhoneNumber());
            contactActionRow.setContentDescription(activity().getString(R.string.search_open_contact, contact.getDisplayName()));
            contactActionRow.setOnClickListener(v -> startContactIntent(contact.dialIntent()));
            btContactCall.setOnClickListener(v -> startContactIntent(contact.dialIntent()));
            btContactMessage.setOnClickListener(v -> startContactIntent(contact.messageIntent()));
        } else if (state instanceof com.mckimquyen.search.ContactSearchUiState.PermissionRequest) {
            contactActionRow.setVisibility(View.VISIBLE);
            contactActionDivider.setVisibility(View.VISIBLE);
            tvContactResultName.setText(R.string.contact_search_permission_label);
            tvContactResultPhone.setText(R.string.contact_search_permission_rationale);
            contactActionRow.setContentDescription(activity().getString(R.string.contact_search_permission_rationale));
            btContactCall.setText(R.string.contact_search_allow);
            btContactMessage.setVisibility(View.GONE);
            contactActionRow.setOnClickListener(v -> requestContactsPermission());
            btContactCall.setOnClickListener(v -> requestContactsPermission());
        } else {
            contactActionRow.setVisibility(View.GONE);
            contactActionDivider.setVisibility(View.GONE);
            contactActionRow.setOnClickListener(null);
            btContactCall.setOnClickListener(null);
            btContactMessage.setOnClickListener(null);
        }
        host.refreshWebSearchFallback();
    }

    private void requestContactsPermission() {
        ActivityCompat.requestPermissions(
                activity(),
                new String[]{Manifest.permission.READ_CONTACTS},
                REQUEST_CODE_CONTACTS
        );
    }

    private void startContactIntent(Intent intent) {
        try {
            activity().startActivity(intent);
            host.hideSearch();
        } catch (ActivityNotFoundException e) {
            Toast.makeText(activity(), R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
        }
    }
}
