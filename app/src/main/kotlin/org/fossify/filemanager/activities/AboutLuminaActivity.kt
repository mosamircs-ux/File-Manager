package org.fossify.filemanager.activities

import android.os.Bundle
import org.fossify.commons.extensions.beVisibleIf
import org.fossify.commons.extensions.getProperPrimaryColor
import org.fossify.commons.extensions.updateTextColors
import org.fossify.commons.extensions.viewBinding
import org.fossify.commons.helpers.NavigationIcon
import org.fossify.filemanager.BuildConfig
import org.fossify.filemanager.R
import org.fossify.filemanager.databinding.ActivityAboutLuminaBinding
import org.fossify.filemanager.helpers.AdManager

class AboutLuminaActivity : SimpleActivity() {
    private val binding by viewBinding(ActivityAboutLuminaBinding::inflate)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(binding.root)
        binding.apply {
            setupEdgeToEdge(padBottomSystem = listOf(aboutScrollview))
            setupMaterialScrollListener(aboutScrollview, aboutAppbar)
            aboutVersion.text = getString(R.string.lumina_version_format, BuildConfig.VERSION_NAME)
            aboutPrivacyHolder.beVisibleIf(AdManager.isPrivacyOptionsRequired)
            aboutPrivacyHolder.setOnClickListener {
                AdManager.showPrivacyOptionsForm(this@AboutLuminaActivity)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        setupTopAppBar(binding.aboutAppbar, NavigationIcon.Arrow)
        updateTextColors(binding.aboutScrollview)
        binding.aboutVersion.setTextColor(getProperPrimaryColor())
        binding.aboutLicenseTitle.setTextColor(getProperPrimaryColor())
        binding.aboutPrivacyLabel.setTextColor(getProperPrimaryColor())
    }
}
