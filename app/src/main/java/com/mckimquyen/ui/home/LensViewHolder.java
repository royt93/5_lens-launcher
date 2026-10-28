package com.mckimquyen.ui.home;

import android.app.Activity;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager2.widget.ViewPager2;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.tabs.TabLayout;
import com.google.android.material.tabs.TabLayoutMediator;
import com.mckimquyen.R;
import com.mckimquyen.adt.LensPagerAdapter;
import com.mckimquyen.app.RApplication;
import com.mckimquyen.model.LensWorkspace;
import com.mckimquyen.util.LensLabelResolver;
import com.mckimquyen.util.LensShortcuts;
import com.mckimquyen.util.UtilSettings;
import com.mckimquyen.views.LensView;

import java.util.List;

import kotlin.Unit;

/**
 * ARCH-002 cluster 1: owns the lens pager of ActHome (pager, dots indicator, name label, adapter, lens list, page
 * binding) and is the only code that writes {@code ActHome.lensViews}, through {@link LensViewHolderHost}.
 * The block bodies below were moved verbatim from ActHome; only references to Activity members were redirected.
 */
public final class LensViewHolder {
    private final LensViewHolderHost host;

    private ViewPager2 lensPager;
    private TabLayout lensPageIndicator;
    private TextView tvLensName;
    private LensPagerAdapter lensPagerAdapter;
    private List<LensWorkspace> currentLenses = new java.util.ArrayList<>();
    private String lensManagementReturnTargetId;
    /** FISH-038: the one pending undo of the last lens move; dropped when the Activity goes away. */
    private kotlin.jvm.functions.Function0<Boolean> pendingLensMoveUndo;

    public LensViewHolder(LensViewHolderHost host) {
        this.host = host;
    }

    private UtilSettings utilSettings() {
        return host.utilSettings();
    }

    public ViewPager2 pager() { return lensPager; }
    public TabLayout indicator() { return lensPageIndicator; }
    public TextView nameLabel() { return tvLensName; }
    public LensPagerAdapter adapter() { return lensPagerAdapter; }
    public List<LensWorkspace> currentLenses() { return currentLenses; }

    /** Set by a lens shortcut intent; consumed by the next {@link #refreshLensList()}. */
    public void setManagementReturnTargetId(String lensId) { lensManagementReturnTargetId = lensId; }

    /** Finds the pager views and wires them; the body is the old ActHome.setupViews lines for the pager. */
    public void attach() {
        Activity activity = host.activity();
        lensPager = activity.findViewById(R.id.lensPager);
        lensPageIndicator = activity.findViewById(R.id.lensPageIndicator);
        tvLensName = activity.findViewById(R.id.tvLensName);
        lensPagerAdapter = new LensPagerAdapter((view, lens) -> {
            // FISH-008 Phase 3: every page carries its own lens identity, set on every bind and
            // rebind (not on page selection) so a prefetched neighbour page never draws with the
            // currently-visible lens's curvature/Smart Focus for a frame before being swiped to.
            view.setLensId(lens.getId());
            bindLensView(view, lens);
            return Unit.INSTANCE;
        });
        lensPager.setAdapter(lensPagerAdapter);
        TabLayoutMediator mediator = new TabLayoutMediator(lensPageIndicator, lensPager,
                (tab, position) -> tab.setIcon(R.drawable.lens_page_indicator_dot));
        host.setLensTabMediator(mediator);
        mediator.attach();
        lensPager.registerOnPageChangeCallback(lensPageChangeCallback);
        lensPageIndicator.setOnLongClickListener(v -> {
            host.offerLensMenu(v);
            return true;
        });
        tvLensName.setOnClickListener(v -> host.requestLensManagement());
        tvLensName.setOnLongClickListener(v -> {
            host.offerLensMenu(v);
            return true;
        });
    }

    /** Detaches the mediator and page callback and drops the pending undo (old ActHome.onDestroy lines). */
    public void onDestroy() {
        pendingLensMoveUndo = null;
        TabLayoutMediator mediator = host.lensTabMediator();
        if (mediator != null) {
            mediator.detach();
            host.setLensTabMediator(null);
        }
        if (lensPager != null && lensPageChangeCallback != null) {
            lensPager.unregisterOnPageChangeCallback(lensPageChangeCallback);
        }
    }

    /** Chrome (dots + name label) shows only on plain multi-lens home. */
    public static boolean chromeVisible(boolean cleanMode, boolean listMode, boolean searchShowing, int lensCount) {
        return !cleanMode && !listMode && !searchShowing && lensCount > 1;
    }

    /** True when [position] indexes into a list of [size] lenses. */
    public static boolean isValidPosition(int position, int size) {
        return position >= 0 && position < size;
    }

    /** FISH-012: lens ids this Activity instance has already offered the resurrect Snackbar for -
     *  guards against showing it twice for the same cold-launch page (bindLensView AND
     *  onPageSelected both fire for page 0 at launch). A second show would call
     *  showPinchCurvatureSnackbar again, which dismisses the still-showing first Snackbar as a
     *  side effect - and that dismissal now calls resetLiveDistortionFactor(), which clears the
     *  pending key too, silently un-resurrecting what was just resurrected. Reset per Activity
     *  instance only (a fresh instance after recreate/relaunch should still get one real prompt
     *  if the value is still genuinely unresolved). */
    private final java.util.Set<String> resurrectPromptedLensIds = new java.util.HashSet<>();

    private final ViewPager2.OnPageChangeCallback lensPageChangeCallback = new ViewPager2.OnPageChangeCallback() {
        @Override
        public void onPageSelected(int position) {
            LensView view = lensViewAt(position);
            if (view != null) {
                host.setActiveLensView(view);
                if (host.listApp() != null) {
                    view.setApps(host.appsForLens(view.getLensId()));
                }
            }
            if (isValidPosition(position, currentLenses.size())) {
                LensWorkspace lens = currentLenses.get(position);
                String currentActive = utilSettings() != null
                        ? utilSettings().getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                        : null;
                if (lens.getId() != null && !lens.getId().equals(currentActive)) {
                    if (utilSettings() != null) {
                        utilSettings().save(UtilSettings.KEY_ACTIVE_LENS_ID, lens.getId());
                    }
                    Object application = host.activity().getApplication();
                    if (application instanceof RApplication) {
                        ((RApplication) application).getAppRefreshPipeline().switchLens(lens.getId());
                    }
                }
                // FISH-012: this page may already have been bound (and its pending value already
                // silently restored) as a prefetched neighbour before the user ever swiped to it
                // - bindLensView will not run again just because it's now selected, so the
                // confirmation Snackbar has to be (re-)offered from here too.
                maybeShowResurrectSnackbar(lens);
                updateLensNavigationChrome();
            }
        }
    };

    /**
     * FISH-014: synchronizes visibility and text for the lens navigation chrome (both dots indicator
     * and the active lens name label) in lockstep across all lifecycle, swipe, search, and mode states.
     */
    public void updateLensNavigationChrome() {
        host.refreshHiddenRecovery();
        if (lensPageIndicator == null) return;
        boolean isCleanMode = utilSettings() != null && utilSettings().getBoolean(UtilSettings.KEY_CLEAN_LENS_MODE);
        boolean isList = utilSettings() != null && utilSettings().isListMode();
        boolean isSearchShowing = host.isSearchShowing();
        boolean visible = com.mckimquyen.ui.home.LensViewHolder.chromeVisible(
                isCleanMode, isList, isSearchShowing, currentLenses != null ? currentLenses.size() : 0);

        lensPageIndicator.setVisibility(visible ? View.VISIBLE : View.GONE);
        if (tvLensName != null) {
            tvLensName.setVisibility(visible ? View.VISIBLE : View.GONE);
            if (visible && currentLenses != null) {
                int position = lensPager != null ? lensPager.getCurrentItem() : 0;
                if (isValidPosition(position, currentLenses.size())) {
                    tvLensName.setText(currentLenses.get(position).getName());
                } else {
                    String activeId = utilSettings() != null
                            ? utilSettings().getString(UtilSettings.KEY_ACTIVE_LENS_ID)
                            : null;
                    tvLensName.setText(LensLabelResolver.resolveActiveLensName(currentLenses, activeId));
                }
            }
        }
    }

    /** (Re)loads the lens list from Room and hands it to the pager adapter; hides the dots
     *  indicator entirely when only one lens exists so single-lens devices look and behave
     *  exactly like before this feature. */
    @androidx.annotation.VisibleForTesting
    public void refreshLensList() {
        LensWorkspace.loadAll(lenses -> {
            onLensesChanged(lenses);
            lensPagerAdapter.submitLenses(lenses);
            updateLensNavigationChrome();
            if (lensManagementReturnTargetId != null) {
                String targetId = lensManagementReturnTargetId;
                lensManagementReturnTargetId = null;
                for (int index = 0; index < lenses.size(); index++) {
                    if (lenses.get(index).getId().equals(targetId)) {
                        int targetIndex = index;
                        lensPager.post(() -> {
                            if (lensPager != null && lensPager.getCurrentItem() != targetIndex) {
                                lensPager.setCurrentItem(targetIndex, false);
                            }
                        });
                        break;
                    }
                }
            }
            // FISH-008 Phase 2 r2: restore active page after rotation/recreate so the user stays on
            // the lens they were viewing instead of bouncing back to page 0.
            if (utilSettings() != null && !lenses.isEmpty()) {
                java.util.Set<String> validLensIds = new java.util.HashSet<>();
                for (LensWorkspace lens : lenses) validLensIds.add(lens.getId());
                utilSettings().reconcileActiveLens(validLensIds);
                String savedActive = utilSettings().getString(UtilSettings.KEY_ACTIVE_LENS_ID);
                for (int i = 0; i < lenses.size(); i++) {
                    if (lenses.get(i).getId().equals(savedActive)) {
                        final int targetIndex = i;
                        // ViewPager2 needs a layout tick after adapter update to accept target position
                        lensPager.post(() -> {
                            if (lensPager != null && lensPager.getCurrentItem() != targetIndex) {
                                lensPager.setCurrentItem(targetIndex, false);
                            }
                        });
                        break;
                    }
                }
            }
            // B3 (test-audit): consumed here ONLY when a page is already bound (host.activeLensView() !=
            // null) - the real-world case that matters most, since ActHome is launchMode=
            // "singleTask" and FrmLens's share button re-launches an already-running, already-
            // fully-bound instance via onNewIntent. submitLenses() with an unchanged lens list
            // triggers no rebind at all in that case, so bindLensView's own check below would
            // never run - this is the only place that ever fires for that path. On a genuine
            // cold launch, host.activeLensView() can still be null here (the DB load can finish before
            // ViewPager2 has bound its first page) - leave the flag set so bindLensView catches
            // it the moment that first bind actually happens, instead of firing against a null
            // view or, worse, silently losing the one-shot request forever.
            if (host.pendingAutoExportLens() && host.activeLensView() != null) {
                host.clearPendingAutoExportLens();
                lensPager.post(host::requestLensExport);
            }
            return Unit.INSTANCE;
        });
    }

    /** Runs on every page bind (initial + recycled). Only the page currently tracked as
     *  {@link LensViewHolderHost#activeLensView()} (or the very first bind, before any page has been selected yet) is
     *  fed today's already-loaded apps here - a different, not-yet-selected page gets its own
     *  lens's apps once the user actually swipes to it (see lensPageChangeCallback), so a
     *  prefetched adjacent page never briefly shows the wrong lens's layout. */
    public void bindLensView(LensView view) {
        bindLensView(view, null);
    }

    /**
     * @param lens the workspace this page is being bound to, when the caller knows it. During a
     *             rebind the holder is not yet discoverable through the pager, so the lens id is
     *             the only reliable way to tell "this is the page the user is looking at".
     */
    public void bindLensView(LensView view, LensWorkspace lens) {
        view.setPackageManager(host.activity().getPackageManager());
        // FISH-038: "Move to lens" needs a second lens; the Undo snackbar is owned here, not by the view.
        view.setHasOtherLenses(currentLenses != null && currentLenses.size() > 1);
        view.setOnAppMovedToLensListener((targetName, undo) -> {
            pendingLensMoveUndo = undo;
            Snackbar.make(lensPager, host.activity().getString(R.string.move_to_lens_done, targetName), Snackbar.LENGTH_LONG)
                    .setAction(R.string.reset_undo, v -> {
                        kotlin.jvm.functions.Function0<Boolean> pending = pendingLensMoveUndo;
                        if (pending != null && pending.invoke()) {
                            pendingLensMoveUndo = null;
                        } else {
                            Toast.makeText(host.activity(), R.string.reset_undo_failed, Toast.LENGTH_LONG).show();
                        }
                    })
                    .show();
        });
        // FISH-038b: a held icon that moved becomes a system drag; the host shows the drop strip.
        view.setOnAppDragStartListener(new com.mckimquyen.views.OnAppDragStartListener() {
            @Override public void onDragStarted(com.mckimquyen.views.LensDragPayload payload) {
                host.offerDropStrip(payload);
            }
            @Override public void onDragCancelled() { host.dismissDropStrip(); }
        });
        view.setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
        view.setOnCurvatureAdjustedListener((curvature, finished) -> {
            if (finished) {
                host.offerPinchConfirmation(curvature);
            }
        });
        // FISH-008 Phase 3: the dots indicator - the menu's only other entry point - is hidden
        // while a single lens exists, so long-pressing empty grid space has to reach it too.
        // Otherwise no single-lens install (i.e. everyone, right after the v11 migration) can
        // ever create a second lens. Anchored on the page itself, same as UI-022's icon menu.
        view.setOnEmptySpaceLongPressListener((x, y) -> {
            // The previous menu's dismiss listener removes the anchor, so it has to go first -
            // otherwise it would tear down the anchor the new menu is about to be built on.
            host.dismissLensMenu();
            host.offerLensMenu(host.lensMenuAnchorAt(view, x, y));
        });
        // FISH-016: pull-down from the lens top opens full search, in Clean mode too.
        view.setOnSearchSwipeDownListener(host::requestSearch);
        // FISH-025: confirmed edge swipe switches pages; do not wrap past first/last lens.
        view.setOnEdgeSwipeListener(direction -> {
            if (lensPager == null || lensPager.getAdapter() == null) return;
            int count = lensPager.getAdapter().getItemCount();
            if (count <= 1) return;
            int current = lensPager.getCurrentItem();
            int target = current;
            if (direction == com.mckimquyen.gesture.EdgeSwipeDirection.PREVIOUS && current > 0) {
                target = current - 1;
            } else if (direction == com.mckimquyen.gesture.EdgeSwipeDirection.NEXT && current < count - 1) {
                target = current + 1;
            }
            if (target != current) {
                lensPager.setCurrentItem(target, true);
                view.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            }
        });
        // FISH-012: a pinch adjustment that was never resolved (Save/dismiss) before the process
        // died is restored here silently, on every bind - not just the active page - so it's
        // ready the instant the user swipes to whichever lens it belonged to. The confirmation
        // Snackbar itself is only re-shown for the page currently on screen (below) - see
        // maybeShowResurrectSnackbar's own doc comment for why onPageSelected also needs it.
        restorePendingPinchIfAny(view, lens);
        // FISH-008 Phase 3 fix: after a configuration change every page rebinds while `host.listApp()`
        // is already populated, but `host.activeLensView()` still points at the destroyed Activity's
        // LensView - so no page matched here and the restored page was left with an empty grid
        // until the next app-list broadcast. The pager cannot resolve its holder mid-rebind, so
        // match on the lens id instead: the page whose lens is the active one is the visible one.
        // Found by rotating a real TECNO KJ7 with two lenses on the second page.
        boolean isActiveLensPage = lens != null && utilSettings() != null
                && lens.getId().equals(utilSettings().getString(UtilSettings.KEY_ACTIVE_LENS_ID));
        if (host.activeLensView() == null || host.activeLensView() == view || isActiveLensPage) {
            host.setActiveLensView(view);
            if (host.listApp() != null) {
                view.setApps(host.appsForLens(view.getLensId()));
            }
            maybeShowResurrectSnackbar(lens);
            // B3 (test-audit): must fire from here, the exact point host.activeLensView() first becomes
            // non-null, not from refreshLensList()'s DB-load callback via a separate
            // lensPager.post() - that raced two independent queuing mechanisms (a plain
            // Handler.post against the RecyclerView's own Choreographer-scheduled bind pass) and
            // could silently lose the one-shot auto-export forever if this bind lost the race.
            if (host.pendingAutoExportLens()) {
                host.clearPendingAutoExportLens();
                view.post(host::requestLensExport);
            }
        }
    }

    /** FISH-012: silently restores {@code lens}'s pending pinch value (if any) into {@code view}'s
     *  live/preview state. Safe to call on every bind, active page or not, so a not-yet-visible
     *  page already shows the right curvature the instant the user swipes to it. */
    private void restorePendingPinchIfAny(LensView view, LensWorkspace lens) {
        if (view == null || lens == null || utilSettings() == null) return;
        Float pending = utilSettings().getPendingDistortionFactor(lens.getId());
        if (pending != null) {
            view.restoreLiveDistortionFactor(pending);
        }
    }

    /** FISH-012: re-shows the confirmation Snackbar for {@code lens}'s pending pinch value, if
     *  any - call this ONLY for the page the user is actually looking at right now. Called from
     *  both bindLensView (covers the active page being freshly bound or rebound) and
     *  lensPageChangeCallback.onPageSelected (covers swiping onto a page that was already bound
     *  as a prefetched neighbour and therefore never goes through bindLensView again -
     *  ViewPager2's underlying RecyclerView keeps adjacent pages bound without rebinding them on
     *  selection). resurrectPromptedLensIds above ensures only the first of those two calls for a
     *  given lens actually shows anything. */
    public void maybeShowResurrectSnackbar(LensWorkspace lens) {
        if (lens == null || utilSettings() == null) return;
        if (!resurrectPromptedLensIds.add(lens.getId())) return;
        Float pending = utilSettings().getPendingDistortionFactor(lens.getId());
        if (pending != null) {
            host.offerPinchConfirmation(pending);
        }
    }

    /** ViewPager2 wraps exactly one child, its own internal RecyclerView - a well-known (if
     *  unofficial) way to reach a specific page's already-bound ViewHolder, used here only to
     *  resolve which LensView a just-selected page actually is. */
    public LensView lensViewAt(int position) {
        if (lensPager == null || lensPager.getChildCount() == 0) return null;
        View child = lensPager.getChildAt(0);
        if (!(child instanceof RecyclerView)) return null;
        RecyclerView.ViewHolder holder = ((RecyclerView) child).findViewHolderForAdapterPosition(position);
        if (holder instanceof LensPagerAdapter.PageHolder) {
            return ((LensPagerAdapter.PageHolder) holder).getLensView();
        }
        return null;
    }

    /** FISH-021: one place that adopts a fresh lens list, so the launcher shortcuts never go stale. */
    public void onLensesChanged(List<LensWorkspace> lenses) {
        currentLenses = lenses;
        LensShortcuts.refresh(host.activity().getApplicationContext(), lenses);
        // FISH-038: pages bound before the list changed must learn whether a move target exists now.
        if (lensPagerAdapter != null) {
            for (int i = 0; i < lensPagerAdapter.getItemCount(); i++) {
                LensView page = lensViewAt(i);
                if (page != null) page.setHasOtherLenses(lenses.size() > 1);
            }
        }
    }
}
