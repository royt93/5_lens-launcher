package com.mckimquyen.adt

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentStatePagerAdapter
import com.mckimquyen.R
import com.mckimquyen.ui.FrmApps
import com.mckimquyen.ui.FrmLens
import com.mckimquyen.ui.FrmSettings

/**
 * ============================================================================
 * FRAGMENT PAGER ADAPTER
 * ============================================================================
 * Adapter cho ViewPager để quản lý 3 tabs chính của launcher:
 * - Tab 0: Lens (Fisheye view)
 * - Tab 1: Apps (Danh sách ứng dụng)
 * - Tab 2: Settings (Cài đặt)
 *
 * DEPRECATION NOTE:
 * FragmentStatePagerAdapter đã deprecated từ AndroidX, nhưng vẫn hoạt động tốt.
 * Để migrate sang ViewPager2 + FragmentStateAdapter sẽ cần:
 * - Thay đổi layout XML (ViewPager -> ViewPager2)
 * - Viết lại adapter extend FragmentStateAdapter
 * - Update logic trong ActHome
 * Migration này có thể làm sau khi có thời gian test kỹ.
 *
 * FIX HISTORY:
 * - 1.3: Sử dụng BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT để tối ưu lifecycle
 *        (Chỉ resume fragment đang hiển thị, pause các fragment khác)
 * ============================================================================
 */
class FragmentPagerAdapter(
    fragmentManager: FragmentManager,
    private val mContext: Context,
) : FragmentStatePagerAdapter(
    fragmentManager,
    // BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT: Tối ưu lifecycle
    // - Fragment hiện tại: RESUMED state
    // - Fragment khác: STARTED state
    // - Giúp tiết kiệm tài nguyên, fragment không hiển thị sẽ pause
    BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT
) {

    companion object {
        // Số lượng tabs cố định = 3
        private const val NUM_PAGES = 3

        // Tab positions - dùng để reference dễ dàng
        const val TAB_LENS = 0
        const val TAB_APPS = 1
        const val TAB_SETTINGS = 2
    }

    /**
     * Tạo Fragment tương ứng với position
     *
     * @param position Vị trí tab (0, 1, 2)
     * @return Fragment instance cho tab đó
     *
     * NOTE: Mỗi lần swipe hoặc select tab, method này được gọi
     * FragmentStatePagerAdapter tự động cache fragments nên không lo về performance
     */
    override fun getItem(position: Int): Fragment {
        return when (position) {
            TAB_LENS -> FrmLens.newInstance()      // Fisheye lens view
            TAB_APPS -> FrmApps.newInstance()      // Danh sách apps dạng grid
            TAB_SETTINGS -> FrmSettings.newInstance() // Settings & preferences
            else -> {
                // Fallback - Không bao giờ xảy ra vì NUM_PAGES = 3
                // Nhưng cần có để satisfy when expression
                Fragment()
            }
        }
    }

    /**
     * Trả về tổng số pages/tabs
     */
    override fun getCount(): Int {
        return NUM_PAGES
    }

    /**
     * Trả về tiêu đề của từng tab
     * Được sử dụng bởi TabLayout để hiển thị tên tab
     *
     * @param position Vị trí tab
     * @return Tên tab (từ strings.xml)
     */
    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            TAB_LENS -> mContext.getString(R.string.tab_lens)
            TAB_APPS -> mContext.getString(R.string.tab_apps)
            TAB_SETTINGS -> mContext.getString(R.string.tab_settings)
            else -> super.getPageTitle(position)
        }
    }

    /**
     * TODO: Nếu cần migrate sang ViewPager2, uncomment code bên dưới
     * và thay thế class này bằng implementation mới
     */
    /*
    // ViewPager2 Migration Example:
    class FragmentPagerAdapter2(
        fragmentActivity: FragmentActivity,
        private val mContext: Context
    ) : FragmentStateAdapter(fragmentActivity) {

        override fun getItemCount(): Int = NUM_PAGES

        override fun createFragment(position: Int): Fragment {
            return when (position) {
                TAB_LENS -> FrmLens.newInstance()
                TAB_APPS -> FrmApps.newInstance()
                TAB_SETTINGS -> FrmSettings.newInstance()
                else -> Fragment()
            }
        }
    }
    */
}
