package com.mckimquyen.adt

import android.content.Context
import androidx.appcompat.content.res.AppCompatResources
import androidx.core.graphics.drawable.toDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckedTextView
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App

/** Icon và nhãn dùng chung một dòng checkable native; ListView giữ trạng thái chọn. */
class LensAppChoiceAdapter(context: Context, private val apps: List<App>) :
    ArrayAdapter<CharSequence>(context, android.R.layout.simple_list_item_multiple_choice, apps.map { it.label }) {

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val row = super.getView(position, convertView, parent) as CheckedTextView
        val app = apps[position]
        val bitmap = RAppsSingleton.instance.getAppIcon(app.iconCacheKey) ?: app.icon
        val icon = bitmap?.toDrawable(context.resources)
            ?: requireNotNull(AppCompatResources.getDrawable(context, android.R.drawable.sym_def_app_icon)).mutate()
        val size = (ICON_SIZE_DP * context.resources.displayMetrics.density).toInt()
        icon.setBounds(0, 0, size, size)
        row.setCompoundDrawablesRelative(icon, null, null, null)
        row.compoundDrawablePadding = (ICON_GAP_DP * context.resources.displayMetrics.density).toInt()
        return row
    }

    companion object {
        const val ICON_SIZE_DP = 40
        private const val ICON_GAP_DP = 12
    }
}
