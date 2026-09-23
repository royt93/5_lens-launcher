package com.mckimquyen.adt

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mckimquyen.R
import com.mckimquyen.ui.FrmApps
import com.mckimquyen.ui.FrmLens
import com.mckimquyen.ui.FrmSettings

/** ViewPager2 adapter for the 3 main tabs: Lens, Apps, Settings. */
class FragmentPagerAdapter(
    private val fragmentActivity: FragmentActivity,
) : FragmentStateAdapter(fragmentActivity) {

    companion object {
        private const val NUM_PAGES = 3
        const val TAB_LENS = 0
        const val TAB_APPS = 1
        const val TAB_SETTINGS = 2
    }

    override fun getItemCount(): Int = NUM_PAGES

    override fun createFragment(position: Int): Fragment {
        return when (position) {
            TAB_LENS -> FrmLens.newInstance()
            TAB_APPS -> FrmApps.newInstance()
            TAB_SETTINGS -> FrmSettings.newInstance()
            else -> Fragment()
        }
    }

    /** ViewPager2 has no built-in page title; used by the Activity's TabLayoutMediator. */
    fun getPageTitle(position: Int): CharSequence {
        return when (position) {
            TAB_LENS -> fragmentActivity.getString(R.string.tab_lens)
            TAB_APPS -> fragmentActivity.getString(R.string.tab_apps)
            TAB_SETTINGS -> fragmentActivity.getString(R.string.tab_settings)
            else -> ""
        }
    }
}
