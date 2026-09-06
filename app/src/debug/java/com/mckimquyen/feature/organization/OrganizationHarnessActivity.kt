package com.mckimquyen.feature.organization

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.mckimquyen.adt.AppAdapter
import com.mckimquyen.model.App

class OrganizationHarnessActivity : AppCompatActivity() {
    lateinit var adapter: AppAdapter
        private set
    lateinit var recyclerView: RecyclerView
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        adapter = AppAdapter(
            this,
            mutableListOf(
                App(
                    label = APP_LABEL,
                    packageName = APP_PACKAGE,
                    name = APP_COMPONENT
                ),
                App(
                    label = SECOND_APP_LABEL,
                    packageName = "com.example.organizationtest.second",
                    name = "SecondActivity"
                )
            )
        )
        recyclerView = RecyclerView(this).apply {
            layoutManager = LinearLayoutManager(this@OrganizationHarnessActivity)
            adapter = this@OrganizationHarnessActivity.adapter
        }
        setContentView(recyclerView)
    }

    companion object {
        const val APP_LABEL = "Organization test app"
        const val APP_PACKAGE = "com.example.organizationtest"
        const val APP_COMPONENT = "MainActivity"
        const val SECOND_APP_LABEL = "Second organization test app"
    }
}
