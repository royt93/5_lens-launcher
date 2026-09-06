package com.mckimquyen.search

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.R
import com.mckimquyen.app.RAppsSingleton
import com.mckimquyen.model.App

fun interface SearchResultClickListener {
    fun onAppClick(app: App, source: View)
}

class SearchResultAdapter(
    private val onAppClick: SearchResultClickListener
) : RecyclerView.Adapter<SearchResultAdapter.ResultViewHolder>() {
    private val apps = mutableListOf<App>()

    fun submitList(newApps: List<App>) {
        apps.clear()
        apps.addAll(newApps)
        notifyDataSetChanged()
    }

    fun firstOrNull(): App? = apps.firstOrNull()

    override fun getItemCount(): Int = apps.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ResultViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.view_search_result, parent, false)
        return ResultViewHolder(view)
    }

    override fun onBindViewHolder(holder: ResultViewHolder, position: Int) {
        holder.bind(apps[position])
    }

    inner class ResultViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val icon: ImageView = itemView.findViewById(R.id.ivSearchResultIcon)
        private val label: TextView = itemView.findViewById(R.id.tvSearchResultLabel)
        private val packageName: TextView = itemView.findViewById(R.id.tvSearchResultPackage)

        fun bind(app: App) {
            val appLabel = app.label.toString()
            label.text = appLabel
            packageName.text = app.packageName
            // CORE-002: keyed by iconCacheKey, not packageName (see BitmapCache.buildKey)
            icon.setImageBitmap(RAppsSingleton.instance.getAppIcon(app.iconCacheKey))
            itemView.contentDescription = itemView.context.getString(R.string.search_open_app, appLabel)
            itemView.setOnClickListener { onAppClick.onAppClick(app, itemView) }
        }
    }
}
