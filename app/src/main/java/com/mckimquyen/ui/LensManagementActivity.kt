package com.mckimquyen.ui

import android.content.Intent
import android.os.Bundle
import android.view.MenuItem
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.R
import com.mckimquyen.model.LensWorkspace
import com.mckimquyen.util.UtilSettings

class LensManagementActivity : AppCompatActivity() {
    private lateinit var list: RecyclerView
    private lateinit var adapter: LensGroupAdapter
    private lateinit var settings: UtilSettings

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.act_lens_management)
        settings = UtilSettings(this)
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
        list = findViewById(R.id.rvLensGroups)
        adapter = LensGroupAdapter(::selectLens, settings::saveCollapsedLensGroups)
        list.layoutManager = LinearLayoutManager(this)
        list.adapter = adapter
        LensWorkspace.loadAll { lenses ->
            if (!isFinishing && !isDestroyed) {
                adapter.submit(lenses, settings.getCollapsedLensGroups())
            }
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            finish()
            return true
        }
        return super.onOptionsItemSelected(item)
    }

    private fun selectLens(lensId: String) {
        val intent = Intent(this, ActHome::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .putExtra(ActHome.EXTRA_TARGET_LENS_ID, lensId)
        startActivity(intent)
        finish()
    }
}
