package com.mckimquyen.adt

import android.content.Context
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mckimquyen.R
import com.mckimquyen.ui.FrmApps
import com.mckimquyen.ui.FrmLens
import com.mckimquyen.ui.FrmSettings

/**
 * ============================================================================
 * FRAGMENT PAGER ADAPTER (ViewPager2 + FragmentStateAdapter)
 * ============================================================================
 * Adapter cho ViewPager2 để quản lý 3 tabs chính của launcher:
 * - Tab 0: Lens (Fisheye view)
 * - Tab 1: Apps (Danh sách ứng dụng)
 * - Tab 2: Settings (Cài đặt)
 *
 * MIGRATION NOTE:
 * - Migrated từ FragmentStatePagerAdapter -> FragmentStateAdapter
 * - Cần update ViewPager -> ViewPager2 trong XML layout
 * - FragmentStateAdapter tự động optimize lifecycle và memory
 *
 * ADVANTAGES của ViewPager2:
 * - Better performance với RecyclerView internally
 * - RTL (Right-to-Left) support built-in
 * - Vertical orientation support
 * - Improved fragment lifecycle management
 * - DiffUtil support cho animations
 *
 * BREAKING CHANGES:
 * - getPageTitle() không còn được support trong ViewPager2
 * - Cần setup TabLayout riêng với TabLayoutMediator
 * - getItem() -> createFragment()
 * - getCount() -> getItemCount()
 * ============================================================================
 */
class FragmentPagerAdapter(
    fragmentActivity: FragmentActivity,
    private val mContext: Context,
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        // Số lượng tabs cố định = 3
        private const val NUM_PAGES = 3

        // Tab positions - dùng để reference dễ dàng
        const val TAB_LENS = 0
        const val TAB_APPS = 1
        const val TAB_SETTINGS = 2
    }

    /**
     * Trả về tổng số pages/tabs
     * Required override cho FragmentStateAdapter
     */
    override fun getItemCount(): Int = NUM_PAGES

    /**
     * Tạo Fragment tương ứng với position
     *
     * @param position Vị trí tab (0, 1, 2)
     * @return Fragment instance cho tab đó
     *
     * NOTE:
     * - FragmentStateAdapter tự động cache và manage lifecycle
     * - Fragment được destroy khi không visible để tiết kiệm memory
     * - Fragment được recreate khi user swipe về
     */
    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_LENS -> FrmLens.newInstance()         // Fisheye lens view
            TAB_APPS -> FrmApps.newInstance()         // Danh sách apps dạng grid
            TAB_SETTINGS -> FrmSettings.newInstance() // Settings & preferences
            else -> {
                Fragment()
            }
        }
    }

    /**
     * Lấy tiêu đề của tab theo position
     *
     * NOTE: ViewPager2 không support getPageTitle() natively
     * Method này dùng cho TabLayoutMediator trong Activity
     *
     * @param position Vị trí tab
     * @return Tên tab (từ strings.xml)
     */
    fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            TAB_LENS -> mContext.getString(R.string.tab_lens)
            TAB_APPS -> mContext.getString(R.string.tab_apps)
            TAB_SETTINGS -> mContext.getString(R.string.tab_settings)
            else -> ""
        }
    }

    /**
     * ============================================================================
     * USAGE trong Activity (ActHome.kt):
     * ============================================================================
     *
     * // Setup ViewPager2
     * val adapter = FragmentPagerAdapter(this, this)
     * viewPager2.adapter = adapter
     *
     * // Setup TabLayout với TabLayoutMediator
     * TabLayoutMediator(tabLayout, viewPager2) { tab, position ->
     *     tab.text = adapter.getPageTitle(position)
     * }.attach()
     *
     * // Optional: Set offscreen page limit
     * viewPager2.offscreenPageLimit = 2  // Cache 2 pages on each side
     *
     * // Optional: Set page transformer for animations
     * viewPager2.setPageTransformer(ZoomOutPageTransformer())
     *
     * ============================================================================
     * UPDATE trong layout XML:
     * ============================================================================
     *
     * Replace:
     *   <androidx.viewpager.widget.ViewPager
     *       android:id="@+id/viewPager"
     *       ... />
     *
     * With:
     *   <androidx.viewpager2.widget.ViewPager2
     *       android:id="@+id/viewPager2"
     *       android:layout_width="match_parent"
     *       android:layout_height="match_parent"
     *       android:orientation="horizontal" />
     *
     * ============================================================================
     */
}
