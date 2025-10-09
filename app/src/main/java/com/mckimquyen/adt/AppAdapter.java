package com.mckimquyen.adt;

import static com.mckimquyen.util.CKt.PKG_NAME;

import android.content.Context;
import android.content.Intent;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.Rect;
import android.net.Uri;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
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
import com.mckimquyen.model.AppPersistent;
import com.mckimquyen.services.BroadcastReceivers;
import com.mckimquyen.ui.ActSettings;
import com.mckimquyen.util.UtilApp;

import java.util.List;
import java.util.Objects;

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
        final AppViewHolder holder = new AppViewHolder(view, mContext);
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
        ImageView ivAppIcon;        // Icon app
        ImageView ivAppHide;        // Button ẩn/hiện app
        Button btAppLock;           // Button khóa/mở app (biometric)
        ImageView ivAppMenu;        // Menu button (3 dots)

        // ====================================================================
        // STATE
        // ====================================================================
        private App mApp;                    // App object hiện tại
        private final Context mContext;      // Context reference
        private final boolean mIsHaveBiometric; // Device có hỗ trợ biometric không

        // ====================================================================
        // CONSTRUCTOR
        // ====================================================================
        public AppViewHolder(View itemView, Context context) {
            super(itemView);
            this.mContext = context;
            this.mIsHaveBiometric = Biometric.INSTANCE.isHaveBiometric(mContext);

            // Initialize views - findViewById chỉ gọi 1 lần khi tạo ViewHolder
            this.cvAppContainer = itemView.findViewById(R.id.cvAppContainer);
            this.tvAppLabel = itemView.findViewById(R.id.tvAppLabel);
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
            ivAppIcon.setImageBitmap(mApp.getIcon());

            String pkgName = Objects.requireNonNull(mApp.getPackageName()).toString();
            String name = Objects.requireNonNull(mApp.getName()).toString();

            // ================================================================
            // LOCK BUTTON STATE (Chỉ hiển thị nếu device có biometric)
            // ================================================================
            if (this.mIsHaveBiometric) {
                boolean isAppOpened = AppPersistent.getAppOpened(pkgName, name);
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
            boolean isAppVisible = AppPersistent.getAppVisibility(pkgName, name);
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

            // Lấy trạng thái hiện tại
            boolean isAppVisible = AppPersistent.getAppVisibility(pkgName, name);

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

            // Lấy trạng thái lock hiện tại
            boolean isAppOpened = AppPersistent.getAppOpened(pkgName, name);

            // Yêu cầu biometric authentication
            if (mContext instanceof AppCompatActivity) {
                Biometric.INSTANCE.toggleLockApp(
                        (AppCompatActivity) mContext,
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
            if (mContext == null) {
                return;
            }
            // Pattern variable matching (Java 16+)
            if (mContext instanceof ActSettings activitySettings) {
                Intent changeAppsVisibilityIntent = new Intent(activitySettings, BroadcastReceivers.AppsVisibilityChangedReceiver.class);
                activitySettings.sendBroadcast(changeAppsVisibilityIntent);
            }
        }

        /**
         * Gửi broadcast khi lock status thay đổi
         * Home screen sẽ nhận broadcast này để update lock icons
         */
        private void sendChangeAppsLockBroadcast() {
            if (mContext == null) {
                return;
            }
            // Pattern variable matching (Java 16+)
            if (mContext instanceof ActSettings activitySettings) {
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
                PopupMenu popupMenu = new PopupMenu(mContext, view);
                popupMenu.setOnMenuItemClickListener(AppViewHolder.this);
                popupMenu.inflate(R.menu.menu_app);
                popupMenu.show();
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
