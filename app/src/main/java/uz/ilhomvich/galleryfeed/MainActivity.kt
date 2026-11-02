package uz.ilhomvich.galleryfeed

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.core.view.WindowCompat
import uz.ilhomvich.galleryfeed.data.GalleryDatabase
import uz.ilhomvich.galleryfeed.ui.GalleryFeedApp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.statusBarColor = android.graphics.Color.TRANSPARENT
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        WindowCompat.getInsetsController(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }

        val database = GalleryDatabase.get(applicationContext)
        setContent {
            GalleryFeedApp(database = database)
        }
    }
}
