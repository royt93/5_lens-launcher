package com.mckimquyen.adt;

import static com.mckimquyen.util.CKt.PKG_NAME;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.view.ContextThemeWrapper;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.Settings;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.PopupMenu;
import androidx.cardview.widget.CardView;
import androidx.core.content.ContextCompat;
import androidx.core.view.ViewCompat;
import androidx.recyclerview.widget.RecyclerView;

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
import java.util.Objects;
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
     * Cập nhật danh sách apps và notify adapter
     * Tối ưu: Reuse adapter thay vì tạo mới mỗi lần update
     *
     * @param newApps Danh sách apps mới
     */
    public void updateApps(List<App> newApps) {
        mApps.clear();
        mApps.addAll(newApps);
        notifyDataSetChanged();
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
        AppPersistent.setAppOrderBatch(mApps);
        mContext.sendBroadcast(new Intent(mContext, BroadcastReceivers.AppsEditedReceiver.class));
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
        CardView cvAppContainer;    // Container của item
        TextView tvAppLabel;        // Tên app
        TextView tvAppOrganization;
        ImageView ivAppIcon;        // Icon app
        ImageView ivAppHide;        // Button ẩn/hiện app
        Button btAppLock;           // Button khóa/mở app (biometric)
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

                if (isAppOpened) {
                    // App đang UNLOCKED (có thể mở được)
                    btAppLock.setText(R.string.lock);
                    ViewCompat.setBackgroundTintList(
                            btAppLock,
                            ColorStateList.valueOf(Color.GRAY)
                    );
                } else {
                    // App đang LOCKED (cần biometric để mở)
                    btAppLock.setText(R.string.unlock);
                    ViewCompat.setBackgroundTintList(
                            btAppLock,
                            ColorStateList.valueOf(ContextCompat.getColor(mContext, R.color.colorPrimary))
                    );
                }
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
                ivAppHide.setImageResource(R.drawable.ic_visibility_grey_24dp);
                ivAppHide.setColorFilter(Color.GRAY);
            } else {
                // App đang HIDDEN trong launcher
                ivAppHide.setImageResource(R.drawable.ic_visibility_off_grey_24dp);
                ivAppHide.setColorFilter(ContextCompat.getColor(mContext, R.color.colorPrimary));
            }

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
            AppPersistent.setOrganization(packageName, componentName, favorite, normalizedFolder, zone);
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
            new androidx.appcompat.app.AlertDialog.Builder(mActivityContext)
                    .setTitle(R.string.organization_set_folder)
                    .setView(input)
                    .setPositiveButton(android.R.string.ok, (dialog, which) ->
                            applyOrganization(
                                    mApp.isFavorite(),
                                    input.getText().toString(),
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
            AppPersistent.setAppVisibility(pkgName, name, !isAppVisible);

            // Update UI
            if (isAppVisible) {
                // Đang visible -> chuyển sang hidden
                Snackbar.make(cvAppContainer, mApp.getLabel() + " is now hidden", Snackbar.LENGTH_LONG).show();
                ivAppHide.setImageResource(R.drawable.ic_visibility_off_grey_24dp);
                ivAppHide.setColorFilter(ContextCompat.getColor(mContext, R.color.colorPrimary));
            } else {
                // Đang hidden -> chuyển sang visible
                Snackbar.make(cvAppContainer, mApp.getLabel() + " is now visible", Snackbar.LENGTH_LONG).show();
                ivAppHide.setImageResource(R.drawable.ic_visibility_grey_24dp);
                ivAppHide.setColorFilter(Color.GRAY);
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
                            AppPersistent.setAppOpened(pkgName, name, !isAppOpened);

                            // Update UI
                            if (isAppOpened) {
                                // Đang unlocked -> chuyển sang locked
                                Snackbar.make(cvAppContainer, label + " is now locked", Snackbar.LENGTH_LONG).show();
                                btAppLock.setText(R.string.unlock);
                                ViewCompat.setBackgroundTintList(
                                        btAppLock,
                                        ColorStateList.valueOf(ContextCompat.getColor(mContext, R.color.colorPrimary))
                                );
                            } else {
                                // Đang locked -> chuyển sang unlocked
                                Snackbar.make(cvAppContainer, label + " is now unlocked", Snackbar.LENGTH_LONG).show();
                                btAppLock.setText(R.string.lock);
                                ViewCompat.setBackgroundTintList(
                                        btAppLock,
                                        ColorStateList.valueOf(Color.GRAY)
                                );
                            }

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
            itemView.setOnClickListener(view ->
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
                    )
            );
            itemView.setOnLongClickListener(view -> {
                showAppMenu(view);
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
                    Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                    intent.setData(Uri.parse("package:" + mApp.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
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
                    Intent intent = new Intent(Intent.ACTION_DELETE);
                    intent.setData(Uri.parse("package:" + mApp.getPackageName()));
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    mContext.startActivity(intent);
                } catch (Exception e) {
                    Toast.makeText(mContext, R.string.error_app_not_found, Toast.LENGTH_SHORT).show();
                }
                return true;
            }

            return false;
        }
    }
}
