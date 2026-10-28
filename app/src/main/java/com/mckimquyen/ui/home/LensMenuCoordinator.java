package com.mckimquyen.ui.home;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;

import com.google.android.material.color.MaterialColors;
import com.mckimquyen.R;
import com.mckimquyen.adt.LensAppChoiceAdapter;
import com.mckimquyen.app.RAppsSingleton;
import com.mckimquyen.app.RApplication;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.model.LensWorkspace;
import com.mckimquyen.services.AppEventManager;
import com.mckimquyen.ui.LensManagementActivity;
import com.mckimquyen.ui.dialog.LensDialogs;
import com.mckimquyen.util.ApertureRevealHelper;
import com.mckimquyen.util.LensAppScope;
import com.mckimquyen.util.LensAppScopeEditor;
import com.mckimquyen.util.PolaroidExportHelper;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;
import com.mckimquyen.views.PointAnchor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import kotlin.Unit;

/**
 * ARCH-002 cluster 4: the lens management menu and its dialogs, moved out of ActHome.
 * The pure decisions come first so they can be tested on the JVM.
 */
public final class LensMenuCoordinator {
    /** What the coordinator needs from {@code ActHome}. The menu, dialog, share launcher and
     *  launch intent keep their storage there because tests read them by name. */
    public interface Host {
        Activity activity();

        UtilSettings settings();

        ArrayList<App> apps();

        LensViewHolder lenses();

        LensView activeLensView();

        PopupMenu menu();

        void setMenu(PopupMenu menu);

        AlertDialog dialog();

        void setDialog(AlertDialog dialog);

        void launchShare(Intent chooser);

        void setLaunchIntent(Intent intent);

        void showRecentAppsPanel();

        void openSearch();

        void refreshSearchBarVisibility();
    }

    private interface LensNameCallback {
        void onNameEntered(String name);
    }

    public static final int ID_SEARCH_APPS = 7;
    public static final int ID_TOGGLE_CLEAN_LENS = 8;
    public static final int ID_LENS_APPS = 9;
    public static final int ID_FREEZE_LENS = 10;
    public static final int ID_MANAGE_GROUP = 11;
    public static final int ID_HIDDEN_APPS = 12;

    public enum MenuAction {
        CREATE, RENAME, DELETE, SMART_FOCUS, SHARE, RECENT, SEARCH, CLEAN, LENS_APPS, FREEZE, HIDDEN, GROUP, NONE
    }

    public static MenuAction routeMenuItem(int itemId) {
        switch (itemId) {
            case 1: return MenuAction.CREATE;
            case 2: return MenuAction.RENAME;
            case 3: return MenuAction.DELETE;
            case 4: return MenuAction.SMART_FOCUS;
            case 5: return MenuAction.SHARE;
            case 6: return MenuAction.RECENT;
            case ID_SEARCH_APPS: return MenuAction.SEARCH;
            case ID_TOGGLE_CLEAN_LENS: return MenuAction.CLEAN;
            case ID_LENS_APPS: return MenuAction.LENS_APPS;
            case ID_FREEZE_LENS: return MenuAction.FREEZE;
            case ID_HIDDEN_APPS: return MenuAction.HIDDEN;
            case ID_MANAGE_GROUP: return MenuAction.GROUP;
            default: return MenuAction.NONE;
        }
    }

    public static int smartFocusLabelRes(boolean on) {
        return on ? R.string.lens_smart_focus_disable : R.string.lens_smart_focus_enable;
    }

    public static int freezeLabelRes(boolean frozen) {
        return frozen ? R.string.lens_unfreeze_positions : R.string.lens_freeze_positions;
    }

    public static boolean containsLens(List<LensWorkspace> lenses, String id) {
        for (LensWorkspace lens : lenses) {
            if (lens.getId().equals(id)) return true;
        }
        return false;
    }

    public static boolean deleteEnabled(int position, int size, String id) {
        return LensViewHolder.isValidPosition(position, size) && LensWorkspace.canDelete(id, size);
    }

    private final Host host;

    /** UI-025: transient 1x1 view the lens menu is anchored to; null while no menu is open. */
    private View lensMenuPressAnchor;

    public LensMenuCoordinator(Host host) {
        this.host = host;
    }

    public View menuAnchorForTest() {
        return lensMenuPressAnchor;
    }

    /** Page-dots indicator near the top: a popup wants room below its anchor (see ActHome history). */
    public View lensMenuAnchor() {
        // setupViews() assigns the indicator before the adapter that can trigger this, so it is
        // non-null by the time any menu can open; rootLayout is a defensive fallback only.
        View indicator = host.lenses().indicator();
        return indicator != null ? indicator : host.activity().findViewById(R.id.rootLayout);
    }

    /**
     * UI-025: LensView draws on one canvas, so the pressed point has no view of its own. A 1x1
     * invisible view is placed there in the root layout; the menu anchors to it and opens beside
     * the finger. Falls back to the old indicator anchor if the root layout is unavailable.
     */
    public View anchorAtPressedPoint(View lens, float x, float y) {
        removeLensMenuPressAnchor();
        ViewGroup root = host.activity().findViewById(R.id.rootLayout);
        if (root == null) return lensMenuAnchor();
        int[] lensOnScreen = new int[2];
        int[] rootOnScreen = new int[2];
        lens.getLocationOnScreen(lensOnScreen);
        root.getLocationOnScreen(rootOnScreen);
        lensMenuPressAnchor = PointAnchor.INSTANCE.attach(
                root,
                lensOnScreen[0] + (int) x - rootOnScreen[0],
                lensOnScreen[1] + (int) y - rootOnScreen[1],
                new androidx.coordinatorlayout.widget.CoordinatorLayout.LayoutParams(
                        PointAnchor.SIZE_PX, PointAnchor.SIZE_PX));
        return lensMenuPressAnchor;
    }

    public void dismissLensManagementMenu() {
        PopupMenu previous = host.menu();
        host.setMenu(null);
        if (previous != null) {
            previous.dismiss();
        }
        removeLensMenuPressAnchor();
    }

    public void removeLensMenuPressAnchor() {
        if (lensMenuPressAnchor == null) return;
        View anchor = lensMenuPressAnchor;
        lensMenuPressAnchor = null;
        Activity activity = host.activity();
        PointAnchor.INSTANCE.remove(anchor, activity.isFinishing() || activity.isDestroyed());
    }

    /** UI-025: every item carries an icon so the text column is aligned like the icon menus. */
    private void addLensMenuItem(PopupMenu menu, Context themed, int id, int titleRes, int iconRes,
                                 boolean enabled) {
        android.view.MenuItem item = menu.getMenu().add(0, id, 0, titleRes);
        android.graphics.drawable.Drawable icon = androidx.appcompat.content.res.AppCompatResources
                .getDrawable(themed, iconRes);
        if (icon != null) {
            icon = androidx.core.graphics.drawable.DrawableCompat.wrap(icon.mutate());
            androidx.core.graphics.drawable.DrawableCompat.setTint(icon, MaterialColors.getColor(
                    themed, com.google.android.material.R.attr.colorOnSurface, android.graphics.Color.BLACK));
            item.setIcon(icon);
        }
        item.setEnabled(enabled);
    }

    public void showLensManagementMenu(View anchor) {
        // UI-025: same rounded popup theme as AppAdapter/SearchResultAdapter/LensView - a bare
        // Activity context skips it and renders the default square popup.
        Context themed = new ContextThemeWrapper(host.activity(), R.style.PopupMenuTheme);
        // Quiet dismiss: the anchor this menu is about to use already exists, so only the old
        // popup goes. Clearing the field first keeps its dismiss listener from removing it.
        PopupMenu previous = host.menu();
        host.setMenu(null);
        if (previous != null) {
            previous.dismiss();
        }
        PopupMenu menu = new PopupMenu(themed, anchor);
        host.setMenu(menu);
        LensViewHolder lenses = host.lenses();
        int position = lenses.pager().getCurrentItem();
        addLensMenuItem(menu, themed, 1, R.string.lens_add, R.drawable.ic_add_24dp, true);
        addLensMenuItem(menu, themed, 2, R.string.lens_rename, R.drawable.ic_edit_24dp, true);
        addLensMenuItem(menu, themed, 3, R.string.lens_delete, R.drawable.ic_delete_outline_24dp,
                deleteEnabled(position, lenses.currentLenses().size(),
                        position >= 0 && position < lenses.currentLenses().size()
                                ? lenses.currentLenses().get(position).getId() : ""));
        // FISH-008 Phase 3: Smart Focus is per-lens, so it gets a quick toggle right here on the
        // lens it applies to (FrmSettings' own switch covers the same lens from Settings). The
        // label states the action rather than using a checkable item.
        addLensMenuItem(menu, themed, 4, smartFocusLabelResAt(position),
                R.drawable.ic_center_focus_24dp, true);
        addLensMenuItem(menu, themed, 5, R.string.lens_share_image, R.drawable.ic_share_24dp, true);
        addLensMenuItem(menu, themed, 6, R.string.recent_apps, R.drawable.ic_history_24dp, true);
        // FISH-016: accessible fallback to the pull-down gesture, plus a quick Clean-mode switch
        // (reusing the Settings strings, already translated in every locale).
        addLensMenuItem(menu, themed, ID_SEARCH_APPS, R.string.search_apps_hint,
                R.drawable.ic_search_menu_24dp, true);
        addLensMenuItem(menu, themed, ID_TOGGLE_CLEAN_LENS, R.string.setting_clean_lens_mode,
                R.drawable.ic_cleaning_24dp, true);
        addLensMenuItem(menu, themed, ID_LENS_APPS, R.string.lens_choose_apps,
                R.drawable.ic_apps_24dp, true);
        addLensMenuItem(menu, themed, ID_FREEZE_LENS, freezeLabelResAt(position),
                R.drawable.ic_lock_24dp, true);
        addLensMenuItem(menu, themed, ID_MANAGE_GROUP, R.string.lens_group_manage,
                R.drawable.ic_group_24dp, true);
        addLensMenuItem(menu, themed, ID_HIDDEN_APPS, R.string.lens_hidden_apps,
                R.drawable.ic_visibility_24dp, true);
        menu.setOnMenuItemClickListener(item -> onLensMenuItemSelected(item.getItemId(), position));
        menu.setForceShowIcon(true);
        menu.setOnDismissListener(dismissed -> {
            if (host.menu() == menu) {
                removeLensMenuPressAnchor();
            }
        });
        menu.show();
    }

    /** The menu's real behavior, split out from the PopupMenu that hosts it (tests drive this). */
    public boolean onLensMenuItemSelected(int itemId, int position) {
        List<LensWorkspace> current = host.lenses().currentLenses();
        if (position < 0 || position >= current.size()) return false;
        LensWorkspace lens = current.get(position);
        switch (routeMenuItem(itemId)) {
            case CREATE:
                createLensDialog(lens.getId());
                return true;
            case RENAME:
                renameLensDialog(lens);
                return true;
            case DELETE:
                confirmDeleteLensDialog(lens);
                return true;
            case SMART_FOCUS:
                toggleSmartFocusForLens(lens);
                return true;
            case SHARE:
                // FISH-013: this menu's "Share" entry calls exportActiveLensImage() directly, with
                // no pending-flag write first - intentional, not a missed call site. It's a
                // synchronous foreground action (the menu is already on screen), not the
                // cross-activity/process-death gap between FrmLens's tap and export completing
                // that FISH-013 exists to cover; clearing an already-absent key is a no-op.
                exportActiveLensImage();
                return true;
            case RECENT:
                host.showRecentAppsPanel();
                return true;
            case SEARCH:
                host.openSearch();
                return true;
            case CLEAN:
                toggleCleanLensMode();
                return true;
            case LENS_APPS:
                showLensAppsDialog(lens);
                return true;
            case FREEZE:
                toggleFreezeForLens(lens);
                return true;
            case HIDDEN:
                showHiddenLensAppsDialog(lens.getId());
                return true;
            case GROUP:
                openLensManagement(lens.getId());
                return true;
            default:
                return false;
        }
    }

    /** The menu entry names the action it performs, so it has to read the lens's current state. */
    public int smartFocusLabelResAt(int position) {
        List<LensWorkspace> lenses = host.lenses().currentLenses();
        UtilSettings settings = host.settings();
        boolean on = position >= 0 && position < lenses.size() && settings != null
                && settings.isSmartFocusBias(lenses.get(position).getId());
        return smartFocusLabelRes(on);
    }

    /** Like {@link #smartFocusLabelResAt}: the entry names the action, so it reads current state. */
    public int freezeLabelResAt(int position) {
        List<LensWorkspace> lenses = host.lenses().currentLenses();
        UtilSettings settings = host.settings();
        boolean frozen = position >= 0 && position < lenses.size() && settings != null
                && settings.isLensFrozen(lenses.get(position).getId());
        return freezeLabelRes(frozen);
    }

    /** FISH-016: Clean mode is global, so unlike Smart Focus it flips one shared key, then
     *  re-applies the same gates every resume already uses - no second source of truth. */
    private void toggleCleanLensMode() {
        UtilSettings settings = host.settings();
        if (settings == null) return;
        settings.save(UtilSettings.KEY_CLEAN_LENS_MODE,
                !settings.getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE));
        host.refreshSearchBarVisibility();
        LensView view = host.activeLensView();
        if (view != null) {
            view.invalidate();
        }
    }

    /** FISH-022: lens name opens grouped lens management and selection in a separate Activity. */
    public void openLensManagement(String lensId) {
        Activity activity = host.activity();
        Intent intent = new Intent(activity, LensManagementActivity.class);
        host.setLaunchIntent(intent);
        if (lensId != null) intent.putExtra(LensManagementActivity.EXTRA_MANAGE_LENS_ID, lensId);
        activity.startActivity(intent);
    }

    /**
     * FISH-021: freezing writes the order the user currently sees as each app's orderNumber, so
     * the sorter keeps it and Smart Focus stops rearranging this lens. Unfreezing also discards
     * any manual drag order, hence the confirmation.
     */
    private void toggleFreezeForLens(LensWorkspace lens) {
        UtilSettings settings = host.settings();
        if (settings == null) return;
        if (settings.isLensFrozen(lens.getId())) {
            dismissLensDialog();
            AlertDialog dialog = LensDialogs.confirm(host.activity(), R.string.lens_unfreeze_title,
                    host.activity().getString(R.string.lens_unfreeze_message), R.string.lens_unfreeze_positions,
                    false, () -> {
                    settings.saveLensFrozen(lens.getId(), false);
                        AppPersistent.clearOrderForLens(lens.getId());
                        reloadLensAfterOrderChange(lens);
                    }, null);
            host.setDialog(dialog);
            dialog.show();
            return;
        }
        LensView view = host.activeLensView();
        if (view == null) return;
        AppPersistent.setAppOrderBatch(new ArrayList<>(view.getDisplayedApps()), lens.getId());
        settings.saveLensFrozen(lens.getId(), true);
        view.refreshSmartFocus();
    }

    private void reloadLensAfterOrderChange(LensWorkspace lens) {
        Object application = host.activity().getApplication();
        if (application instanceof RApplication) {
            ((RApplication) application).getAppRefreshPipeline().switchLens(lens.getId());
        }
        LensView view = host.activeLensView();
        if (view != null) {
            view.refreshSmartFocus();
        }
    }

    private void toggleSmartFocusForLens(LensWorkspace lens) {
        UtilSettings settings = host.settings();
        if (settings == null) return;
        boolean enabled = !settings.isSmartFocusBias(lens.getId());
        settings.saveSmartFocusBias(lens.getId(), enabled);
        LensView view = host.activeLensView();
        if (view != null) {
            view.refreshSmartFocus();
        }
    }

    /** FISH-EXPORT: snapshots the currently visible lens (the host's active LensView) into a
     *  polaroid-framed PNG and hands it to the share sheet via the host's share launcher. */
    public void exportActiveLensImage() {
        Activity activity = host.activity();
        LensView view = host.activeLensView();
        if (view == null) {
            // FISH-013: an auto-export request that can never be resolved (no bound page) must
            // not stay pending on disk forever, and must not fail silently.
            clearPendingAutoExportLensAndNotifyFailure();
            return;
        }
        String activeLensId = view.getLensId();
        LensWorkspace matched = null;
        for (LensWorkspace lens : host.lenses().currentLenses()) {
            if (lens.getId().equals(activeLensId)) {
                matched = lens;
                break;
            }
        }
        if (matched == null) {
            // FISH-013: the requesting lens no longer exists (e.g. deleted while the request was
            // in flight) - same unrecoverable case as above.
            clearPendingAutoExportLensAndNotifyFailure();
            return;
        }
        String lensName = matched.getName();
        PolaroidExportHelper.INSTANCE.exportAsync(view, lensName, activity, uri -> {
            // FISH-013: only clear the durable pending flag once export has actually reached a
            // terminal outcome (success or failure) - never at request time - so a process kill
            // mid-export still resurrects the request on next launch instead of losing it.
            UtilSettings settings = host.settings();
            if (settings != null) {
                settings.clearPendingAutoExportLens();
            }
            if (uri != null) {
                try {
                    host.launchShare(Intent.createChooser(
                            PolaroidExportHelper.INSTANCE.buildShareIntent(uri),
                            activity.getString(R.string.share_via)));
                } catch (Exception e) {
                    Toast.makeText(activity, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
                }
            } else {
                Toast.makeText(activity, R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
            }
            return Unit.INSTANCE;
        });
    }

    /** FISH-013: shared terminal-failure path for exportActiveLensImage's unrecoverable cases -
     *  clears the durable pending flag so the request stops resurrecting forever, and tells the
     *  user explicitly instead of a silent no-op. */
    private void clearPendingAutoExportLensAndNotifyFailure() {
        UtilSettings settings = host.settings();
        if (settings != null) {
            settings.clearPendingAutoExportLens();
        }
        Toast.makeText(host.activity(), R.string.error_lens_share_failed, Toast.LENGTH_SHORT).show();
    }

    private void createLensDialog(String copyFromLensId) {
        LensViewHolder holder = host.lenses();
        String defaultName = host.activity().getString(
                R.string.lens_new_name_template, holder.currentLenses().size() + 1);
        showLensNameDialog(R.string.lens_add, defaultName, name ->
                LensWorkspace.createLens(name, copyFromLensId, lenses -> {
                    // FISH-008 Phase 3: the new lens copies its source's curvature/Smart Focus
                    // too, not just its layout. createLens() doesn't return the new id, so find
                    // the one lens that wasn't there before.
                    UtilSettings settings = host.settings();
                    if (settings != null) {
                        for (LensWorkspace lens : lenses) {
                            if (!containsLens(holder.currentLenses(), lens.getId())) {
                                settings.duplicateLensSettings(copyFromLensId, lens.getId());
                            }
                        }
                    }
                    adoptLenses(lenses);
                    return Unit.INSTANCE;
                }));
    }

    /** Adopts a fresh lens list: holder state, pager adapter and navigation chrome, in that order. */
    private void adoptLenses(List<LensWorkspace> lenses) {
        LensViewHolder holder = host.lenses();
        holder.onLensesChanged(lenses);
        holder.adapter().submitLenses(lenses);
        holder.updateLensNavigationChrome();
    }

    private void renameLensDialog(LensWorkspace lens) {
        showLensNameDialog(R.string.lens_rename_title, lens.getName(), name ->
                LensWorkspace.renameLens(lens, name, lenses -> {
                    adoptLenses(lenses);
                    return Unit.INSTANCE;
                }));
    }

    /** Dismisses the current lens dialog before replacing it or releasing the Activity. */
    public void dismissLensDialog() {
        AlertDialog dialog = host.dialog();
        if (dialog != null) {
            if (dialog.isShowing()) {
                dialog.dismiss();
            }
            host.setDialog(null);
        }
    }

    /**
     * FISH-021: tick the apps a lens shows. Reuses the host's lens dialog, so onDestroy and
     * rotation already dismiss it. Search is deliberately not filtered, so this lists every
     * installed app.
     */
    public void showLensAppsDialog(LensWorkspace lens) {
        Activity activity = host.activity();
        UtilSettings settings = host.settings();
        ArrayList<App> listApp = host.apps();
        if (listApp == null || listApp.isEmpty() || settings == null) return;
        ArrayList<App> apps = new ArrayList<>(listApp);
        Set<String> allIds = new HashSet<>();
        for (App app : apps) allIds.add(LensAppScope.identifierOf(app));
        LensAppScopeEditor editor = new LensAppScopeEditor(settings);

        // The choice lives in this set, updated by the dialog's own click callback. Never read it
        // back from the ListView: it only knows rows that have been bound, so on a list longer
        // than the screen the rows not yet scrolled into view report "unticked" untouched.
        final Set<String> picked = new HashSet<>(editor.effectiveIds(lens.getId(), allIds));

        String[] ids = new String[apps.size()];
        for (int i = 0; i < apps.size(); i++) {
            ids[i] = LensAppScope.identifierOf(apps.get(i));
        }

        dismissLensDialog();
        dismissLensDialog();
        AlertDialog dialog = LensDialogs.multiChoice(activity, R.string.lens_choose_apps,
                new LensAppChoiceAdapter(activity, apps), initialChoiceIndexes(ids, picked),
                android.R.string.ok, indexes -> {
                    Set<String> chosen = new HashSet<>();
                    for (int index : indexes) if (index >= 0 && index < ids.length) chosen.add(ids[index]);
                    editor.apply(lens.getId(), chosen);
                    return Unit.INSTANCE;
                }, null);
        host.setDialog(dialog);
        dialog.show();
    }

    public void showHiddenLensAppsDialog(String lensId) {
        Activity activity = host.activity();
        UtilSettings settings = host.settings();
        if (settings == null) return;
        Set<String> hidden = settings.getLensHiddenAppIds(lensId);
        ArrayList<App> apps = new ArrayList<>();
        List<App> installed = RAppsSingleton.getInstance().getApps();
        if (installed != null) {
            for (App app : installed) {
                if (hidden.contains(LensAppScope.identifierOf(app))) apps.add(app);
            }
        }
        dismissLensDialog();
        if (apps.isEmpty()) {
            AlertDialog empty = LensDialogs.confirm(activity, R.string.lens_hidden_apps,
                    activity.getString(R.string.lens_hidden_apps_empty), android.R.string.cancel,
                    false, () -> {}, null);
            host.setDialog(empty);
            empty.show();
            return;
        }
        Set<String> restored = new HashSet<>();
        ArrayList<String> ids = new ArrayList<>();
        for (App app : apps) ids.add(LensAppScope.identifierOf(app));
        Set<Integer> initiallyChecked = new HashSet<>();
        AlertDialog dialog = LensDialogs.multiChoice(activity, R.string.lens_hidden_apps,
                new LensAppChoiceAdapter(activity, apps), initiallyChecked, R.string.lens_restore_apps,
                indexes -> {
                    for (int index : indexes) if (index >= 0 && index < ids.size()) restored.add(ids.get(index));
                    Set<String> remaining = new HashSet<>(settings.getLensHiddenAppIds(lensId));
                    remaining.removeAll(restored);
                    settings.saveLensHiddenAppIds(lensId, remaining);
                    AppEventManager.INSTANCE.notifyLensScopeChanged(lensId);
                    return Unit.INSTANCE;
                }, null);
        host.setDialog(dialog);
        dialog.show();
    }

    private void showLensNameDialog(int titleRes, String initialText, LensNameCallback onDone) {
        Activity activity = host.activity();
        dismissLensDialog();
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        AlertDialog dialog = LensDialogs.textInput(activity, titleRes, R.string.lens_name_hint,
                initialText, R.string.lens_group_error_blank, name -> {
                    ApertureRevealHelper.INSTANCE.dismissWithReveal(dialogHolder[0], lensMenuAnchor(),
                            () -> onDone.onNameEntered(name));
                    return Unit.INSTANCE;
                }, null, false);
        dialogHolder[0] = dialog;
        host.setDialog(dialog);
        ApertureRevealHelper.INSTANCE.prepareDialog(dialog);
        dialog.show();
        ApertureRevealHelper.INSTANCE.revealShownDialog(dialog, lensMenuAnchor());
    }

    private void confirmDeleteLensDialog(LensWorkspace lens) {
        Activity activity = host.activity();
        String message = activity.getString(R.string.lens_delete_confirm_message, lens.getName());
        dismissLensDialog();
        final AlertDialog[] dialogHolder = new AlertDialog[1];
        AlertDialog dialog = LensDialogs.confirm(activity, R.string.lens_delete, message, R.string.lens_delete,
                true, () -> { ApertureRevealHelper.INSTANCE.dismissWithReveal(dialogHolder[0], lensMenuAnchor(), () -> {
                    LensWorkspace.deleteLens(lens.getId(), lenses -> {
                        UtilSettings settings = host.settings();
                        if (settings != null) settings.deleteLensSettings(lens.getId());
                        adoptLenses(lenses);
                        if (!lenses.isEmpty()) {
                            LensViewHolder holder = host.lenses();
                            int targetPos = Math.min(holder.pager().getCurrentItem(), lenses.size() - 1);
                            holder.pager().setCurrentItem(targetPos, false);
                            LensWorkspace targetLens = lenses.get(targetPos);
                            if (settings != null) settings.save(UtilSettings.KEY_ACTIVE_LENS_ID, targetLens.getId());
                            Object app = activity.getApplication();
                            if (app instanceof RApplication) {
                                ((RApplication) app).getAppRefreshPipeline().switchLens(targetLens.getId());
                            }
                        }
                        return Unit.INSTANCE;
                    });
                }); }, null, false);
        dialogHolder[0] = dialog;
        host.setDialog(dialog);
        ApertureRevealHelper.INSTANCE.prepareDialog(dialog);
        dialog.show();
        ApertureRevealHelper.INSTANCE.revealShownDialog(dialog, lensMenuAnchor());
    }

    private static Set<Integer> initialChoiceIndexes(String[] ids, Set<String> selected) {
        Set<Integer> indexes = new HashSet<>();
        for (int i = 0; i < ids.length; i++) if (selected.contains(ids[i])) indexes.add(i);
        return indexes;
    }

    /** Teardown order matters: dialog first, then the menu, then the press anchor. */
    public void onDestroy() {
        dismissLensDialog();
        PopupMenu menu = host.menu();
        if (menu != null) {
            menu.dismiss();
            host.setMenu(null);
        }
        removeLensMenuPressAnchor();
    }
}
