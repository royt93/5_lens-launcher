package com.mckimquyen.ui

import android.animation.ObjectAnimator
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.mckimquyen.R
import com.mckimquyen.util.LocaleHelper
import com.mckimquyen.util.LocaleHelper.AppLanguage

class LanguageBottomSheetDialogFragment : BottomSheetDialogFragment() {

    interface OnLanguageSelectedListener {
        fun onLanguageSelected(languageCode: String)
    }

    private var listener: OnLanguageSelectedListener? = null
    private var adapter: LanguageAdapter? = null

    override fun getTheme(): Int = R.style.TransBottomSheetDialog

    fun setOnLanguageSelectedListener(listener: OnLanguageSelectedListener) {
        this.listener = listener
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.dialog_language_picker, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val rvLanguages = view.findViewById<RecyclerView>(R.id.rvLanguages)
        val etSearch = view.findViewById<EditText>(R.id.etSearch)
        val currentLanguage = LocaleHelper.getLanguage(requireContext())

        adapter = LanguageAdapter(
            items = LocaleHelper.supportedLanguages,
            currentLangCode = currentLanguage
        ) { language ->
            LocaleHelper.setLocale(requireContext(), language.code)
            LocaleHelper.setLanguageSelected(requireContext())
            listener?.onLanguageSelected(language.code)
            dismiss()
        }

        rvLanguages.layoutManager = LinearLayoutManager(requireContext())
        rvLanguages.adapter = adapter

        // Auto-scroll to current language
        val currentIndex = LocaleHelper.supportedLanguages.indexOfFirst { it.code == currentLanguage }
        if (currentIndex >= 0) {
            rvLanguages.post {
                (rvLanguages.layoutManager as? LinearLayoutManager)
                    ?.scrollToPositionWithOffset(currentIndex, 60)
            }
        }

        // Search filter
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                adapter?.filter(s?.toString() ?: "")
            }
        })
    }

    // ─── Adapter ────────────────────────────────────────────────────────────────

    private class LanguageAdapter(
        private val items: List<AppLanguage>,
        private var currentLangCode: String,
        private val onItemClick: (AppLanguage) -> Unit
    ) : RecyclerView.Adapter<LanguageAdapter.ViewHolder>() {

        private var filtered: List<AppLanguage> = items.toList()

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val tvFlag: TextView = view.findViewById(R.id.tvFlag)
            val tvLanguageName: TextView = view.findViewById(R.id.tvLanguageName)
            val tvLanguageEnglishName: TextView = view.findViewById(R.id.tvLanguageEnglishName)
            val ivSelected: ImageView = view.findViewById(R.id.ivSelected)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context)
                .inflate(R.layout.item_language, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val item = filtered[position]
            val isSelected = item.code == currentLangCode

            holder.tvFlag.text = item.flag
            holder.tvLanguageName.text = item.nativeName
            holder.tvLanguageEnglishName.text = item.englishName

            // Selected state visual
            holder.ivSelected.visibility = if (isSelected) View.VISIBLE else View.GONE
            holder.tvLanguageName.alpha = if (isSelected) 1f else 0.87f

            // Ripple on item root
            holder.itemView.background = with(holder.itemView.context) {
                val attrs = intArrayOf(android.R.attr.selectableItemBackground)
                val ta = obtainStyledAttributes(attrs)
                ta.getDrawable(0).also { ta.recycle() }
            }

            holder.itemView.setOnClickListener {
                val previousSelectedIndex = filtered.indexOfFirst { it.code == currentLangCode }
                currentLangCode = item.code

                // Animate the selected icon with a pop effect
                holder.ivSelected.visibility = View.VISIBLE
                holder.ivSelected.scaleX = 0f
                holder.ivSelected.scaleY = 0f
                val scaleX = ObjectAnimator.ofFloat(holder.ivSelected, "scaleX", 0f, 1.2f, 1f)
                val scaleY = ObjectAnimator.ofFloat(holder.ivSelected, "scaleY", 0f, 1.2f, 1f)
                scaleX.duration = 250
                scaleY.duration = 250
                scaleX.interpolator = DecelerateInterpolator()
                scaleY.interpolator = DecelerateInterpolator()
                scaleX.start()
                scaleY.start()

                if (previousSelectedIndex >= 0 && previousSelectedIndex != holder.bindingAdapterPosition) {
                    notifyItemChanged(previousSelectedIndex)
                }

                // Small delay so user sees the animation, then trigger selection
                holder.itemView.postDelayed({
                    onItemClick(item)
                }, 200)
            }
        }

        override fun getItemCount(): Int = filtered.size

        fun filter(query: String) {
            val newList = if (query.isBlank()) {
                items.toList()
            } else {
                val q = query.trim().lowercase()
                items.filter {
                    it.nativeName.lowercase().contains(q) ||
                        it.englishName.lowercase().contains(q) ||
                        it.code.lowercase().contains(q)
                }
            }

            val diff = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
                override fun getOldListSize() = filtered.size
                override fun getNewListSize() = newList.size
                override fun areItemsTheSame(oldPos: Int, newPos: Int) =
                    filtered[oldPos].code == newList[newPos].code
                override fun areContentsTheSame(oldPos: Int, newPos: Int) =
                    filtered[oldPos] == newList[newPos]
            })

            filtered = newList
            diff.dispatchUpdatesTo(this)
        }
    }
}
