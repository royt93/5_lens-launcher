package com.mckimquyen.feature.organization

import androidx.appcompat.widget.PopupMenu
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.mckimquyen.R
import com.mckimquyen.adt.AppAdapter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AppOrganizationMenuIntegrationTest {
    @Test
    fun longPressMenuHandlersEditFavoriteAndEveryPinState() {
        ActivityScenario.launch(OrganizationHarnessActivity::class.java).use { scenario ->
            InstrumentationRegistry.getInstrumentation().waitForIdleSync()
            scenario.onActivity { activity ->
                val holder = activity.recyclerView
                    .findViewHolderForAdapterPosition(0) as AppAdapter.AppViewHolder
                assertTrue(holder.itemView.isLongClickable)
                assertEquals(0, holder.bindingAdapterPosition)

                assertTrue(holder.onMenuItemClick(menuItem(activity, holder, R.id.menuItemFavorite)))
                rebind(activity, holder)
                assertEquals(
                    activity.getString(R.string.organization_favorite_label),
                    summary(holder)
                )

                assertTrue(holder.onMenuItemClick(menuItem(activity, holder, R.id.menuItemPinStart)))
                rebind(activity, holder)
                assertTrue(summary(holder).contains(activity.getString(R.string.organization_pinned_start_label)))

                assertTrue(holder.onMenuItemClick(menuItem(activity, holder, R.id.menuItemPinEnd)))
                rebind(activity, holder)
                assertTrue(summary(holder).contains(activity.getString(R.string.organization_pinned_end_label)))
                assertFalse(summary(holder).contains(activity.getString(R.string.organization_pinned_start_label)))

                assertTrue(holder.onMenuItemClick(menuItem(activity, holder, R.id.menuItemUnpin)))
                rebind(activity, holder)
                assertEquals(activity.getString(R.string.organization_favorite_label), summary(holder))

                assertTrue(holder.onMenuItemClick(menuItem(activity, holder, R.id.menuItemFavorite)))
                rebind(activity, holder)
                assertEquals(android.view.View.GONE, organizationSummary(holder).visibility)

                val secondHolder = activity.recyclerView
                    .findViewHolderForAdapterPosition(1) as AppAdapter.AppViewHolder
                assertTrue(secondHolder.onMenuItemClick(
                    menuItem(activity, secondHolder, R.id.menuItemMoveEarlier)
                ))
                assertEquals(
                    listOf(OrganizationHarnessActivity.SECOND_APP_LABEL, OrganizationHarnessActivity.APP_LABEL),
                    (0 until activity.adapter.itemCount).map {
                        activity.adapter.getItemForPosition(it).label
                    }
                )
                assertTrue(secondHolder.onMenuItemClick(
                    menuItem(activity, secondHolder, R.id.menuItemMoveLater)
                ))
                assertEquals(
                    listOf(OrganizationHarnessActivity.APP_LABEL, OrganizationHarnessActivity.SECOND_APP_LABEL),
                    (0 until activity.adapter.itemCount).map {
                        activity.adapter.getItemForPosition(it).label
                    }
                )
            }
        }
    }

    private fun menuItem(
        activity: OrganizationHarnessActivity,
        holder: AppAdapter.AppViewHolder,
        id: Int
    ) = PopupMenu(activity, holder.itemView).menu.add(0, id, 0, "test")

    private fun organizationSummary(holder: AppAdapter.AppViewHolder) =
        holder.itemView.findViewById<android.widget.TextView>(R.id.tvAppOrganization)

    private fun summary(holder: AppAdapter.AppViewHolder) = organizationSummary(holder).text.toString()

    private fun rebind(activity: OrganizationHarnessActivity, holder: AppAdapter.AppViewHolder) {
        activity.adapter.onBindViewHolder(holder, 0)
    }
}
