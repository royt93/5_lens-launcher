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
 * Adapter cho ViewPager để hiển thị 3 tabs: Lens, Apps, Settings
 *
 * Fix: 1.3 - Sử dụng BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT để fix deprecated warning
 * Note: FragmentStatePagerAdapter vẫn deprecated, nhưng migrate sang ViewPager2
 * sẽ yêu cầu thay đổi layout XML và logic lớn, nên tạm thời giữ nguyên với behavior mới
 */
class FragmentPagerAdapter(
    fragmentManager: FragmentManager,
    private val mContext: Context,
) : FragmentStatePagerAdapter(
    fragmentManager,
    BEHAVIOR_RESUME_ONLY_CURRENT_FRAGMENT // Fix deprecated constructor
) {

    companion object {
        private const val NUM_PAGES = 3
    }

    /**
     * Trả về Fragment tương ứng với position
     * Position 0: Lens tab
     * Position 1: Apps tab
     * Position 2: Settings tab
     */
    override fun getItem(position: Int): Fragment {
        return when (position) {
            0 -> FrmLens.newInstance()
            1 -> FrmApps.newInstance()
            2 -> FrmSettings.newInstance()
            else -> Fragment() // Fallback, không bao giờ xảy ra với NUM_PAGES = 3
        }
    }

    /**
     * Tổng số pages
     */
    override fun getCount(): Int {
        return NUM_PAGES
    }

    /**
     * Tiêu đề của từng tab
     */
    override fun getPageTitle(position: Int): CharSequence? {
        return when (position) {
            0 -> mContext.resources.getString(R.string.tab_lens)
            1 -> mContext.resources.getString(R.string.tab_apps)
            2 -> mContext.resources.getString(R.string.tab_settings)
            else -> super.getPageTitle(position)
        }
    }
}
