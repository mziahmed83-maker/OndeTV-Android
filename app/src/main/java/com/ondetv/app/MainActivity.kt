package com.ondetv.app

import android.app.Application
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import com.ondetv.app.data.AppDatabase
import com.ondetv.app.data.IptvRepository
import com.ondetv.app.ui.OndeTvRootEnhanced

class OndeTvApp : Application() {
    lateinit var repository: IptvRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = IptvRepository(AppDatabase.get(this))
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                OndeTvRootEnhanced((application as OndeTvApp).repository)
            }
        }
    }
}
