package com.mckimquyen.adt;

import static com.mckimquyen.util.CKt.PKG_NAME;

import android.content.Context;
import android.content.Intent;
import android.view.ContextThemeWrapper;
import android.graphics.Color;
import android.graphics.Rect;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import com.google.android.material.card.MaterialCardView;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.color.MaterialColors;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;
import com.mckimquyen.R;
import com.mckimquyen.ext.Biometric;
import com.mckimquyen.model.App;
import com.mckimquyen.model.AppOrganizationRules;
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.model.PinnedZone;
import com.mckimquyen.services.BroadcastReceivers;
import com.mckimquyen.ui.ActSettings;
import com.mckimquyen.util.UtilApp;

import java.util.List;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.Collections;

import kotlin.Unit;

/**
 * ============================================================================
 * APP ADAPTER - RecyclerView Adapter cho danh sách ứng dụng
 * ============================================================================
 * Hiển thị danh sách apps trong Settings screen với các tính năng:
 * - Hiển thị icon và tên app
 * - Ẩn/hiện app khỏi launcher (visibility toggle)
 * - Khóa/mở khóa app bằng biometric (lock toggle)
 * - Context menu: App Info, Uninstall
 * - Launch app khi click
 * ARCHITECTURE:
 * - Adapter: Quản lý danh sách apps
 * - ViewHolder: Quản lý UI và events của từng app item
 * - AppPersistent: Lưu trữ settings (visibility, lock status)
 * - Biometric: Xử lý authentication
 * MIGRATION NOTE:
 * Đã cố migrate sang Kotlin nhưng gặp lỗi với lambda callbacks và ViewHolder,
 * nên tạm giữ Java version (2023.03.19)
 * ============================================================================
 */
public class AppAdapter extends RecyclerView.Adapter<AppAdapter.AppViewHolder> {

    // ========================================================================
    // FIELDS
    // ========================================================================
    private final Context mContext;
    private final List<App> mApps;

    // ========================================================================
    // FEAT-007: MULTI-SELECT STATE
    // ========================================================================
    /** Payload marker for a selection-only rebind — see {@link #onBindViewHolder(AppViewHolder, int, List)}. */
    public static final Object PAYLOAD_SELECTION = new Object();
    private final Set<String> mSelectedIdentifiers = new LinkedHashSet<>();
    private SelectionListener mSelectionListener;

    public interface SelectionListener {
        void onSelectionChanged(int selectedCount);
    }

    // ========================================================================
    // CONSTRUCTOR
    // ========================================================================
    public AppAdapter(Context mContext, List<App> mApps) {
        this.mContext = mContext;
        this.mApps = mApps;
    }

    // ========================================================================
    // PUBLIC METHODS
    // ========================================================================

    /**
     * Lấy App object tại vị trí cụ thể
     *
     * @param position Vị trí trong danh sách
     * @return App object
     */
    public App getItemForPosition(int position) {
        return mApps.get(position);
    }

    /**
     * UI-005: resolves the Material You dynamic colorPrimary at call time instead of the static
     * @color/colorPrimary resource, so hide/lock icon tints track the wallpaper-derived palette.
     */
    private static int resolveDynamicPrimaryColor(Context context) {
        return MaterialColors.getColor(
                context,
                androidx.appcompat.R.attr.colorPrimary,
                ContextCompat.getColor(context, R.color.colorPrimary)
        );
    }

    private static int resolveDynamicOnSurfaceVariantColor(Context context) {
        return MaterialColors.getColor(
                context,
                com.google.android.material.R.attr.colorOnSurfaceVariant,
                ContextCompat.getColor(context, R.color.colorAppTint)
        );
    }

    /**
     * UI-006: pure lock-icon-state logic, pulled out of the view-binding code so it's unit
     * testable without a device/Robolectric. isAppOpened == true means "unlocked" (tapping it
     * LOCKS the app); false means "locked" (tapping it UNLOCKS the app).
     */
    @androidx.annotation.DrawableRes
    static int lockIconResFor(boolean isAppOpened) {
        return isAppOpened ? R.drawable.ic_lock_open_24dp : R.drawable.ic_lock_24dp;
    }

    @androidx.annotation.StringRes
    public static int lockContentDescriptionResFor(boolean isAppOpened) {
        return isAppOpened ? R.string.lock : R.string.unlock;
    }

    @androidx.annotation.StringRes
    public static int hideContentDescriptionResFor(boolean isAppVisible) {
        return isAppVisible ? R.string.desc_hide_app : R.string.desc_show_app;
    }

    /**
     * Cập nhật danh sách apps và notify adapter
     * Tối ưu: Reuse adapter thay vì tạo mới mỗi lần update
     *
     * @param newApps Danh sách apps mới
     */
    public void updateApps(List<App> newApps) {
        List<App> oldApps = new ArrayList<>(mApps);
        mApps.clear();
        mApps.addAll(newApps);
        DiffUtil.DiffResult diffResult = DiffUtil.calculateDiff(new AppDiffCallback(oldApps, mApps));
        diffResult.dispatchUpdatesTo(this);
        pruneSelectionToCurrentApps();
    }

    /**
     * FEAT-007 audit finding: a background app-list refresh (uninstall, icon-pack switch,
     * sort change) must not leave the selection/`ActionMode` silently referencing an app
     * that no longer exists in the list — drop it, and tell the listener so the host
     * `ActionMode` title/menu updates (or closes, if that empties the selection).
     */
    private void pruneSelectionToCurrentApps() {
        if (mSelectedIdentifiers.isEmpty()) return;
        Set<String> currentIdentifiers = new LinkedHashSet<>();
        for (App app : mApps) {
            currentIdentifiers.add(identifierFor(app));
        }
        if (mSelectedIdentifiers.retainAll(currentIdentifiers)) {
            notifySelectionListener();
        }
    }

    public boolean moveItem(int fromPosition, int toPosition) {
        return moveItem(fromPosition, toPosition, true);
    }

    public boolean moveItem(int fromPosition, int toPosition, boolean persist) {
        if (fromPosition < 0 || toPosition < 0 ||
                fromPosition >= mApps.size() || toPosition >= mApps.size() ||
                fromPosition == toPosition) {
            return false;
        }
        Collections.swap(mApps, fromPosition, toPosition);
        notifyItemMoved(fromPosition, toPosition);
        if (persist) persistOrder();
        return true;
    }

    public void persistOrder() {
        for (int index = 0; index < mApps.size(); index++) {
            App app = mApps.get(index).copyWithOrder(index);
            mApps.set(index, app);
        }
        // FISH-008 Phase 2: this adapter only ever shows one lens's apps at a time, so the
        // first app's lensId (if any) is the whole batch's lens.
        String lensId = mApps.isEmpty() ? AppPersistent.DEFAULT_LENS_ID : mApps.get(0).getLensId();
        AppPersistent.setAppOrderBatch(mApps, lensId);
        mContext.sendBroadcast(new Intent(mContext, BroadcastReceivers.AppsEditedReceiver.class));
    }

    // ========================================================================
    // FEAT-007: MULTI-SELECT — pure, Context-free helpers (unit-testable)
    // ========================================================================

    static String identifierFor(App app) {
        return AppPersistent.generateIdentifier(
                app.getPackageName() != null ? app.getPackageName().toString() : null,
                app.getName() != null ? app.getName().toString() : null);
    }

    static Set<String> toggleIdentifier(Set<String> current, String identifier) {
        Set<String> next = new LinkedHashSet<>(current);
        if (!next.remove(identifier)) {
            next.add(identifier);
        }
        return next;
    }

    static Set<String> selectAllIdentifiers(List<App> apps) {
        Set<String> ids = new LinkedHashSet<>();
        for (App app : apps) {
            ids.add(identifierFor(app));
        }
        return ids;
    }

    // ========================================================================
    // FEAT-007: MULTI-SELECT — adapter state
    // ========================================================================

    public void setSelectionListener(SelectionListener listener) {
        mSelectionListener = listener;
    }

    public boolean isSelectionMode() {
        return !mSelectedIdentifiers.isEmpty();
    }

    public boolean isSelected(App app) {
        return mSelectedIdentifiers.contains(identifierFor(app));
    }

    public int getSelectionCount() {
        return mSelectedIdentifiers.size();
    }

    public List<App> getSelectedApps() {
        List<App> selected = new ArrayList<>();
        for (App app : mApps) {
            if (mSelectedIdentifiers.contains(identifierFor(app))) {
                selected.add(app);
            }
        }
        return selected;
    }

    /** Long-press entry point: toggles one app in/out of the selection. */
    public void toggleSelection(App app) {
        boolean wasSelectionMode = isSelectionMode();
        String identifier = identifierFor(app);
        Set<String> next = toggleIdentifier(mSelectedIdentifiers, identifier);
        mSelectedIdentifiers.clear();
        mSelectedIdentifiers.addAll(next);

        // Entering/leaving selection mode changes every row's icon visibility, not just this
        // one — a single-item payload update would leave the other visible rows stale.
        if (wasSelectionMode != isSelectionMode()) {
            notifyItemRangeChanged(0, mApps.size(), PAYLOAD_SELECTION);
        } else {
            notifyIdentifierChanged(identifier);
        }
        notifySelectionListener();
    }

    public void selectAll() {
        mSelectedIdentifiers.clear();
        mSelectedIdentifiers.addAll(selectAllIdentifiers(mApps));
        notifyItemRangeChanged(0, mApps.size(), PAYLOAD_SELECTION);
        notifySelectionListener();
    }

    /** Cleared on tab switch (fresh adapter) and on {@code ActionMode} exit — never left stale. */
    public void clearSelection() {
        if (mSelectedIdentifiers.isEmpty()) return;
        mSelectedIdentifiers.clear();
        notifyItemRangeChanged(0, mApps.size(), PAYLOAD_SELECTION);
        notifySelectionListener();
    }

    private void notifyIdentifierChanged(String identifier) {
        for (int i = 0; i < mApps.size(); i++) {
            if (identifierFor(mApps.get(i)).equals(identifier)) {
                notifyItemChanged(i, PAYLOAD_SELECTION);
                return;
            }
        }
    }

    private void notifySelectionListener() {
        if (mSelectionListener != null) {
            mSelectionListener.onSelectionChanged(mSelectedIdentifiers.size());
        }
    }

    // ========================================================================
    // FEAT-007: MULTI-SELECT — bulk actions (each reuses the existing single-app
    // AppPersistent codepath in a loop; no new bulk-specific business logic)
    // ========================================================================

    public void bulkSetVisibility(boolean visible) {
        boolean changed = false;
        for (int i = 0; i < mApps.size(); i++) {
            App app = mApps.get(i);
            if (!mSelectedIdentifiers.contains(identifierFor(app)) || app.isVisible() == visible) continue;
            String pkg = Objects.requireNonNull(app.getPackageName()).toString();
            String name = Objects.requireNonNull(app.getName()).toString();
            AppPersistent.setAppVisibility(pkg, name, visible, app.getLensId());
            mApps.set(i, app.copyWithLockAndVisibility(app.isOpened(), visible, app.getOpenCount()));
            notifyItemChanged(i);
            changed = true;
        }
        if (changed) {
            mContext.sendBroadcast(new Intent(mContext, BroadcastReceivers.AppsVisibilityChangedReceiver.class));
        }
    }

    public void bulkPin(PinnedZone zone) {
        boolean changed = false;
        for (int i = 0; i < mApps.size(); i++) {
            App app = mApps.get(i);
            if (!mSelectedIdentifiers.contains(identifierFor(app))) continue;
            String pkg = Objects.requireNonNull(app.getPackageName()).toString();
            String name = Objects.requireNonNull(app.getName()).toString();
            AppPersistent.setOrganization(pkg, name, app.isFavorite(), app.getFolderName(), zone, app.getLensId());
            mApps.set(i, app.copyWithOrganization(app.isFavorite(), app.getFolderName(), zone));
            notifyItemChanged(i);
            changed = true;
        }
        if (changed) {
            mContext.sendBroadcast(new Intent(mContext, BroadcastReceivers.AppsEditedReceiver.class));
        }
    }

    // ========================================================================
    // RECYCLERVIEW ADAPTER OVERRIDES
    // ========================================================================

    @Override
    public int getItemCount() {
        return mApps.size();
    }

    @Override
    public long getItemId(int position) {
        return mApps.get(position).getId();
    }

    /**
     * Tạo ViewHolder mới khi RecyclerView cần
     * ViewHolder được reuse nên method này không được gọi nhiều
     */
    @NonNull
    @Override
    public AppViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(parent.getContext());
        View view = inflater.inflate(R.layout.view_item_app, parent, false);
        final AppViewHolder holder = new AppViewHolder(view, mContext, this);
        holder.setOnClickListeners();
        return holder;
    }

    /**
     * Bind data vào ViewHolder
     * Method này được gọi mỗi khi item scroll vào view
     */
    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position) {
        App app = getItemForPosition(position);
        if (app == null) {
            return;
        }
        holder.setAppElement(app);
        holder.setSelectionState(isSelected(app), isSelectionMode());
    }

    /**
     * FEAT-007: a selection-only change (toggle/select-all/clear) never carries a full
     * rebind — only the checked state and icon visibility are touched, not the label,
     * icon bitmap, lock state, etc.
     */
    @Override
    public void onBindViewHolder(@NonNull AppViewHolder holder, int position, @NonNull List<Object> payloads) {
        if (payloads.contains(PAYLOAD_SELECTION)) {
            App app = getItemForPosition(position);
            if (app != null) {
                holder.setSelectionState(isSelected(app), isSelectionMode());
            }
            return;
        }
        super.onBindViewHolder(holder, position, payloads);
    }

    /**
     * ============================================================================
     * APP VIEW HOLDER - Quản lý UI của từng app item
     * ============================================================================
     * ViewHolder pattern để tối ưu performance RecyclerView
     * - Cache view references
     * - Handle click events
     * - Update UI state (visibility, lock)
     * ============================================================================
     */
    public static class AppViewHolder extends RecyclerView.ViewHolder implements PopupMenu.OnMenuItemClickListener {

        // ====================================================================
        // UI COMPONENTS
        // ====================================================================
        MaterialCardView cvAppContainer;    // Container của item
        TextView tvAppLabel;        // Tên app
        TextView tvAppOrganization;
        ImageView ivAppIcon;        // Icon app
        ImageView ivAppHide;        // Button ẩn/hiện app
        ImageView btAppLock;        // Button khóa/mở app (biometric) — icon, tint signals state
        ImageView ivAppMenu;        // Menu button (3 dots)

        // ====================================================================
        // STATE
        // ====================================================================
        private App mApp;                    // App object hiện tại
        private final Context mContext;      // Application context reference
        private final Context mActivityContext; // Activity context for biometric
        private final boolean mIsHaveBiometric; // Device có hỗ trợ biometric không
        private final AppAdapter mAdapter;   // Reference to adapter for state sync

        // ====================================================================
        // CONSTRUCTOR
        // ====================================================================
        public AppViewHolder(View itemView, Context context, AppAdapter adapter) {
            super(itemView);
            // Keep both contexts: ApplicationContext for general use, Activity for biometric
            this.mContext = context.getApplicationContext();
            this.mActivityContext = context; // Keep Activity context for biometric
            this.mIsHaveBiometric = Biometric.INSTANCE.isHaveBiometric(context);
            this.mAdapter = adapter;

            // Initialize views - findViewById chỉ gọi 1 lần khi tạo ViewHolder
            this.cvAppContainer = itemView.findViewById(R.id.cvAppContainer);
            this.tvAppLabel = itemView.findViewById(R.id.tvAppLabel);
            this.tvAppOrganization = itemView.findViewById(R.id.tvAppOrganization);
            this.ivAppIcon = itemView.findViewById(R.id.ivAppIcon);
            this.ivAppHide = itemView.findViewById(R.id.ivAppHide);
            this.btAppLock = itemView.findViewById(R.id.btAppLock);
            this.ivAppMenu = itemView.findViewById(R.id.ivAppMenu);
        }

        // ====================================================================
        // DATA BINDING
        // ====================================================================

        /**
         * Bind App data vào UI
         * Method này được gọi mỗi khi RecyclerView reuse ViewHolder
         *
         * @param app App object cần hiển thị
         */
        public void setAppElement(App app) {
            this.mApp = app;

            // Set basic info
            tvAppLabel.setText(mApp.getLabel());
            bindOrganizationSummary();
            // BUG-07 fix consequence: App.icon is now null (icon stored in BitmapCache only).
            // Must fetch icon via RAppsSingleton.getAppIcon() instead of mApp.getIcon().
            // CORE-002: keyed by iconCacheKey (component + version + icon-pack identity),
            // not packageName, so same-package activities and stale versions never collide.
            android.graphics.Bitmap cachedIcon = com.mckimquyen.app.RAppsSingleton.getInstance()
                    .getAppIcon(mApp.getIconCacheKey());
            ivAppIcon.setImageBitmap(cachedIcon);

            String pkgName = Objects.requireNonNull(mApp.getPackageName()).toString();
            String name = Objects.requireNonNull(mApp.getName()).toString();

            // ================================================================
            // LOCK BUTTON STATE (Chỉ hiển thị nếu device có biometric)
            // ================================================================
            if (this.mIsHaveBiometric) {
                boolean isAppOpened = mApp.isOpened();
                btAppLock.setVisibility(View.VISIBLE);

                btAppLock.setImageResource(lockIconResFor(isAppOpened));
                btAppLock.setColorFilter(isAppOpened ? resolveDynamicOnSurfaceVariantColor(mContext) : resolveDynamicPrimaryColor(mContext));
                btAppLock.setContentDescription(mContext.getString(lockContentDescriptionResFor(isAppOpened)));
            } else {
                // Device không có biometric -> ẩn lock button
                btAppLock.setVisibility(View.GONE);
            }

            // ================================================================
            // VISIBILITY BUTTON STATE
            // ================================================================
            boolean isAppVisible = mApp.isVisible();
            if (isAppVisible) {
                // App đang VISIBLE trong launcher
                ivAppHide.setImageResource(R.drawable.ic_visibility_24dp);
                ivAppHide.setColorFilter(resolveDynamicOnSurfaceVariantColor(mContext));
            } else {
                // App đang HIDDEN trong launcher
                ivAppHide.setImageResource(R.drawable.ic_visibility_off_24dp);
                ivAppHide.setColorFilter(resolveDynamicPrimaryColor(mContext));
            }
            ivAppHide.setContentDescription(mContext.getString(
                    hideContentDescriptionResFor(isAppVisible), mApp.getLabel()));
            ivAppMenu.setContentDescription(mContext.getString(R.string.desc_app_menu, mApp.getLabel()));
            ivAppIcon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

            // ================================================================
            // SPECIAL CASE: App launcher của mình (self-reference)
            // ================================================================
            // Không cho phép ẩn hoặc lock app launcher chính
            if (mApp.getPackageName().toString().equals(PKG_NAME)) {
                ivAppHide.setVisibility(View.GONE);
                btAppLock.setVisibility(View.GONE);
            } else {
                // App thông thường -> hiển thị đầy đủ controls
                ivAppHide.setVisibility(View.VISIBLE);
                if (mIsHaveBiometric) {
                    btAppLock.setVisibility(View.VISIBLE);
                } else {
                    btAppLock.setVisibility(View.GONE);
                }
            }
        }

        /**
         * FEAT-007: reflects the adapter's selection state on this row. The checked state
         * itself is native {@link CardView} (accessibility reports checked/unchecked for
         * free); while selection mode is active, the per-row hide/lock/menu controls are
         * hidden so a tap always means "toggle selection", never a stray single-app action.
         * On exit, {@link #setAppElement(App)} is re-run to restore them — reusing its
         * existing self-app/biometric visibility rules instead of duplicating them here,
         * since a bare "set VISIBLE" would be wrong for the launcher's own row or a
         * no-biometric device (bug found by {@code AppAdapterWidgetTest
         * #testDeselectingLastItem_restoresPerRowIconVisibility}: this previously only ever
         * hid the icons and never brought them back on exit).
         */
        public void setSelectionState(boolean selected, boolean selectionModeActive) {
            cvAppContainer.setChecked(selected);
            if (selectionModeActive) {
                ivAppHide.setVisibility(View.GONE);
                btAppLock.setVisibility(View.GONE);
                ivAppMenu.setVisibility(View.GONE);
            } else if (mApp != null) {
                // ivAppMenu's visibility isn't part of setAppElement's own rules (it's
                // always shown outside of selection mode, in the original layout's default
                // state) — restore it explicitly; ivAppHide/btAppLock's rules (self-app,
                // biometric) ARE setAppElement's, so that call covers those two.
                ivAppMenu.setVisibility(View.VISIBLE);
                setAppElement(mApp);
            }
        }

        private void bindOrganizationSummary() {
            List<String> labels = new ArrayList<>();
            if (mApp.isFavorite()) {
                labels.add(mContext.getString(R.string.organization_favorite_label));
            }
            if (mApp.getFolderName() != null && !mApp.getFolderName().isBlank()) {
                labels.add(mContext.getString(R.string.organization_folder_label, mApp.getFolderName()));
            }
            if (mApp.getPinnedZone() == PinnedZone.START) {
                labels.add(mContext.getString(R.string.organization_pinned_start_label));
            } else if (mApp.getPinnedZone() == PinnedZone.END) {
                labels.add(mContext.getString(R.string.organization_pinned_end_label));
            }
            tvAppOrganization.setText(android.text.TextUtils.join(" • ", labels));
            tvAppOrganization.setVisibility(labels.isEmpty() ? View.GONE : View.VISIBLE);
        }

        private void applyOrganization(boolean favorite, String folder, PinnedZone zone) {
            String packageName = Objects.requireNonNull(mApp.getPackageName()).toString();
            String componentName = Objects.requireNonNull(mApp.getName()).toString();
            String normalizedFolder = AppOrganizationRules.normalizeFolder(folder);
            AppPersistent.setOrganization(packageName, componentName, favorite, normalizedFolder, zone, mApp.getLensId());
            mApp = mApp.copyWithOrganization(favorite, normalizedFolder, zone);
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                mAdapter.mApps.set(position, mApp);
                mAdapter.notifyItemChanged(position);
            }
            mActivityContext.sendBroadcast(
                    new Intent(mActivityContext, BroadcastReceivers.AppsEditedReceiver.class));
            Snackbar.make(cvAppContainer, R.string.organization_saved, Snackbar.LENGTH_SHORT).show();
        }

        private void showFolderDialog() {
            EditText input = new EditText(mActivityContext);
            input.setHint(R.string.organization_folder_hint);
            input.setSingleLine(true);
            input.setText(mApp.getFolderName());
            input.setSelectAllOnFocus(true);
            int paddingH = (int) (24 * mActivityContext.getResources().getDisplayMetrics().density);
            int paddingV = (int) (8 * mActivityContext.getResources().getDisplayMetrics().density);
            android.widget.FrameLayout container = new android.widget.FrameLayout(mActivityContext);
            container.setPadding(paddingH, paddingV, paddingH, 0);
            container.addView(input);

            new MaterialAlertDialogBuilder(mActivityContext, R.style.MaterialYouDialogTheme)
                    .setTitle(R.string.organization_set_folder)
                    .setView(container)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            applyOrganization(
                                    mApp.isFavorite(),
                                    input.getText() != null ? input.getText().toString() : "",
                                    mApp.getPinnedZone()))
                    .setNeutralButton(R.string.organization_clear_folder, (dialog, which) ->
                            applyOrganization(mApp.isFavorite(), null, mApp.getPinnedZone()))
                    .setNegativeButton(android.R.string.cancel, null)
                    .show();
        }

        private void showAppMenu(View anchor) {
            Context wrapper = new ContextThemeWrapper(mActivityContext, R.style.PopupMenuTheme);
            PopupMenu popupMenu = new PopupMenu(wrapper, anchor, Gravity.END);
            popupMenu.setOnMenuItemClickListener(AppViewHolder.this);
            popupMenu.inflate(R.menu.menu_app);
            popupMenu.getMenu().findItem(R.id.menuItemFavorite).setTitle(
                    mApp.isFavorite()
                            ? R.string.organization_remove_favorite
                            : R.string.organization_add_favorite);
            popupMenu.getMenu().findItem(R.id.menuItemUnpin).setVisible(
                    mApp.getPinnedZone() != PinnedZone.NONE);
            int position = getBindingAdapterPosition();
            popupMenu.getMenu().findItem(R.id.menuItemMoveEarlier).setEnabled(position > 0);
            popupMenu.getMenu().findItem(R.id.menuItemMoveLater).setEnabled(
                    position >= 0 && position < mAdapter.getItemCount() - 1);
            popupMenu.setForceShowIcon(true);
            popupMenu.show();
        }

        // ====================================================================
        // VISIBILITY TOGGLE
        // ====================================================================

        /**
         * Toggle visibility của app (ẩn/hiện trong launcher)
         *
         * @param app App cần toggle
         */
        public void toggleAppVisibility(App app) {
            this.mApp = app;
            String pkgName = Objects.requireNonNull(mApp.getPackageName()).toString();
            String name = Objects.requireNonNull(mApp.getName()).toString();

            // Lấy trạng thái hiện tại từ mApp property thay vì DB query
            boolean isAppVisible = mApp.isVisible();

            // Toggle trạng thái
            AppPersistent.setAppVisibility(pkgName, name, !isAppVisible, mApp.getLensId());

            // Update UI
            if (isAppVisible) {
                // Đang visible -> chuyển sang hidden
                Snackbar.make(cvAppContainer, mApp.getLabel() + " is now hidden", Snackbar.LENGTH_LONG).show();
                ivAppHide.setImageResource(R.drawable.ic_visibility_off_24dp);
                ivAppHide.setColorFilter(resolveDynamicPrimaryColor(mContext));
                ivAppHide.setContentDescription(mContext.getString(R.string.desc_show_app, mApp.getLabel()));
            } else {
                // Đang hidden -> chuyển sang visible
                Snackbar.make(cvAppContainer, mApp.getLabel() + " is now visible", Snackbar.LENGTH_LONG).show();
                ivAppHide.setImageResource(R.drawable.ic_visibility_24dp);
                ivAppHide.setColorFilter(resolveDynamicOnSurfaceVariantColor(mContext));
                ivAppHide.setContentDescription(mContext.getString(R.string.desc_hide_app, mApp.getLabel()));
            }

            // Đồng bộ trạng thái vào Adapter list
            int position = getBindingAdapterPosition();
            if (position != RecyclerView.NO_POSITION) {
                App updatedApp = mApp.copyWithLockAndVisibility(mApp.isOpened(), !isAppVisible, mApp.getOpenCount());
                mAdapter.mApps.set(position, updatedApp);
                mAdapter.notifyItemChanged(position);
            }
        }

        // ====================================================================
        // LOCK TOGGLE (BIOMETRIC)
        // ====================================================================

        /**
         * Toggle lock status của app (khóa/mở khóa với biometric)
         *
         * @param app App cần toggle lock
         */
        public void toggleAppLock(App app) {
            this.mApp = app;
            String pkgName = Objects.requireNonNull(mApp.getPackageName()).toString();
            String name = Objects.requireNonNull(mApp.getName()).toString();
            String label = Objects.requireNonNull(mApp.getLabel()).toString();

            // Lấy trạng thái lock hiện tại từ mApp property thay vì DB query
            boolean isAppOpened = mApp.isOpened();

            // Yêu cầu biometric authentication
            if (mActivityContext instanceof AppCompatActivity) {
                Biometric.INSTANCE.toggleLockApp(
                        (AppCompatActivity) mActivityContext,
                        label,
                        pkgName,
                        isAppOpened,
                        (s, aBoolean) -> {
                            // Callback sau khi authentication thành công
                            AppPersistent.setAppOpened(pkgName, name, !isAppOpened, mApp.getLensId());

                            // Update UI
                            boolean isNowOpened = !isAppOpened;
                            if (isAppOpened) {
                                // Đang unlocked -> chuyển sang locked
                                Snackbar.make(cvAppContainer, label + " is now locked", Snackbar.LENGTH_LONG).show();
                            } else {
                                // Đang locked -> chuyển sang unlocked
                                Snackbar.make(cvAppContainer, label + " is now unlocked", Snackbar.LENGTH_LONG).show();
                            }
                            btAppLock.setImageResource(lockIconResFor(isNowOpened));
                            btAppLock.setColorFilter(isNowOpened ? resolveDynamicOnSurfaceVariantColor(mContext) : resolveDynamicPrimaryColor(mContext));
                            btAppLock.setContentDescription(mContext.getString(lockContentDescriptionResFor(isNowOpened)));

                            // Đồng bộ trạng thái vào Adapter list
                            int position = getBindingAdapterPosition();
                            if (position != RecyclerView.NO_POSITION) {
                                App updatedApp = mApp.copyWithLockAndVisibility(!isAppOpened, mApp.isVisible(), mApp.getOpenCount());
                                mAdapter.mApps.set(position, updatedApp);
                                mAdapter.notifyItemChanged(position);
                            }

                            // Return Unit instead of null to fix @NotNull warning
                            return Unit.INSTANCE;
                        }
                );
            }
        }

        // ====================================================================
        // BROADCAST SENDERS
        // ====================================================================

        /**
         * Gửi broadcast khi visibility thay đổi
         * Home screen sẽ nhận broadcast này để refresh danh sách apps
         */
        private void sendChangeAppsVisibilityBroadcast() {
            if (mActivityContext == null) {
                return;
            }
            // Pattern variable matching (Java 16+)
            if (mActivityContext instanceof ActSettings activitySettings) {
                Intent changeAppsVisibilityIntent = new Intent(activitySettings, BroadcastReceivers.AppsVisibilityChangedReceiver.class);
                activitySettings.sendBroadcast(changeAppsVisibilityIntent);
            }
        }

        /**
         * Gửi broadcast khi lock status thay đổi
         * Home screen sẽ nhận broadcast này để update lock icons
         */
        private void sendChangeAppsLockBroadcast() {
            if (mActivityContext == null) {
                return;
            }
            // Pattern variable matching (Java 16+)
            if (mActivityContext instanceof ActSettings activitySettings) {
                Intent intent = new Intent(activitySettings, BroadcastReceivers.AppsLockChangedReceiver.class);
                activitySettings.sendBroadcast(intent);
            }
        }

        // ====================================================================
        // CLICK LISTENERS
        // ====================================================================

        /**
         * Setup tất cả click listeners cho các UI elements
         * Method này chỉ gọi 1 lần khi tạo ViewHolder
         */
        public void setOnClickListeners() {
            // ================================================================
            // CLICK ITEM: Launch app
            // ================================================================
            itemView.setOnClickListener(view -> {
                if (mAdapter.isSelectionMode()) {
                    mAdapter.toggleSelection(mApp);
                    return;
                }
                UtilApp.launchComponent(
                        mContext,
                        Objects.requireNonNull(mApp.getPackageName()).toString(),
                        Objects.requireNonNull(mApp.getLabel()).toString(),
                        Objects.requireNonNull(mApp.getName()).toString(),
                        itemView,
                        new Rect(
                                0,
                                0,
                                itemView.getMeasuredWidth(),
                                itemView.getMeasuredHeight()
                        )
                );
            });
            // FEAT-007: long-press enters/extends multi-select (native ActionMode pattern).
            // The launcher's own row never enters selection — it can't be hidden/pinned/
            // uninstalled anyway (see the PKG_NAME special case in setAppElement).
            itemView.setOnLongClickListener(view -> {
                if (mApp != null && !Objects.requireNonNull(mApp.getPackageName()).toString().equals(PKG_NAME)) {
                    mAdapter.toggleSelection(mApp);
                }
                return true;
            });

            // ================================================================
            // CLICK HIDE BUTTON: Toggle visibility
            // ================================================================
            ivAppHide.setOnClickListener(v -> {
                if (mApp != null) {
                    sendChangeAppsVisibilityBroadcast();
                    toggleAppVisibility(mApp);
                } else {
                    Snackbar.make(cvAppContainer, mContext.getString(R.string.error_app_not_found), Snackbar.LENGTH_LONG).show();
                }
            });

            // ================================================================
            // CLICK LOCK BUTTON: Toggle lock (biometric)
            // ================================================================
            btAppLock.setOnClickListener(v -> {
                if (mApp != null) {
                    sendChangeAppsLockBroadcast();
                    toggleAppLock(mApp);
                } else {
                    Snackbar.make(cvAppContainer, mContext.getString(R.string.error_app_not_found), Snackbar.LENGTH_LONG).show();
                }
            });

            // ================================================================
            // CLICK MENU BUTTON: Show popup menu
            // ================================================================
            ivAppMenu.setOnClickListener(view -> {
                showAppMenu(view);
            });
        }

        // ====================================================================
        // POPUP MENU HANDLER
        // ====================================================================

        /**
         * Xử lý click trên popup menu items
         *
         * @param item MenuItem được click
         * @return true nếu event được handle
         */
        @Override
        public boolean onMenuItemClick(MenuItem item) {
            int id = item.getItemId();

            if (id == R.id.menuItemFavorite) {
                applyOrganization(!mApp.isFavorite(), mApp.getFolderName(), mApp.getPinnedZone());
                return true;
            } else if (id == R.id.menuItemFolder) {
                showFolderDialog();
                return true;
            } else if (id == R.id.menuItemPinStart) {
                applyOrganization(mApp.isFavorite(), mApp.getFolderName(), PinnedZone.START);
                return true;
            } else if (id == R.id.menuItemPinEnd) {
                applyOrganization(mApp.isFavorite(), mApp.getFolderName(), PinnedZone.END);
                return true;
            } else if (id == R.id.menuItemUnpin) {
                applyOrganization(mApp.isFavorite(), mApp.getFolderName(), PinnedZone.NONE);
                return true;
            } else if (id == R.id.menuItemMoveEarlier) {
                int position = getBindingAdapterPosition();
                return mAdapter.moveItem(position, position - 1);
            } else if (id == R.id.menuItemMoveLater) {
                int position = getBindingAdapterPosition();
                return mAdapter.moveItem(position, position + 1);
            }

            // ================================================================
            // APP INFO: Mở settings app info
            // ================================================================
            if (id == R.id.menuItemElementAppInfo) {
                try {
                    mContext.startActivity(UtilApp.appInfoIntent(mApp.getPackageName().toString()));
                } catch (Exception e) {
                    Toast.makeText(mContext, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            // ================================================================
            // UNINSTALL: Mở uninstall dialog
            // ================================================================
            else if (id == R.id.menuItemElementUninstall) {
                try {
                    mContext.startActivity(UtilApp.uninstallIntent(mApp.getPackageName().toString()));
                } catch (Exception e) {
                    Toast.makeText(mContext, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            return false;
        }
    }
}
