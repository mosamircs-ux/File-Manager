package org.fossify.filemanager

import android.graphics.Color
import com.github.ajalt.reprint.core.Reprint
import org.fossify.commons.FossifyApp
import org.fossify.filemanager.extensions.config

class App : FossifyApp() {
    override val isAppLockFeatureAvailable = true

    override fun onCreate() {
        super.onCreate()
        Reprint.initialize(this)
        applyLuminaDefaultBrandingIfFirstRun()
    }

    private fun applyLuminaDefaultBrandingIfFirstRun() {
        val prefs = getSharedPreferences("lumina_branding_prefs", MODE_PRIVATE)
        if (!prefs.getBoolean("lumina_theme_v2", false)) {
            config.apply {
                isSystemThemeEnabled = false
                isGlobalThemeEnabled = false
                backgroundColor = Color.parseColor("#0F172A")
                textColor = Color.parseColor("#F1F5F9")
                primaryColor = Color.parseColor("#06B6D4")
                accentColor = Color.parseColor("#4F46E5")
                appIconColor = Color.parseColor("#4F46E5")
            }
            prefs.edit().putBoolean("lumina_theme_v2", true).apply()
        }
    }
}
