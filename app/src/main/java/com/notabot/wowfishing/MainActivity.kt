package com.notabot.wowfishing

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Button
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.notabot.wowfishing.model.BotConfig
import com.notabot.wowfishing.service.FishingAccessibilityService
import com.notabot.wowfishing.service.FishingOverlayService

class MainActivity : AppCompatActivity() {

    private lateinit var config: BotConfig

    private lateinit var btnPermOverlay: Button
    private lateinit var btnPermAccessibility: Button
    private lateinit var btnPermMic: Button
    private lateinit var btnLaunchOverlay: Button
    private lateinit var btnStopOverlay: Button

    private lateinit var tvThresholdLabel: TextView
    private lateinit var sbThreshold: SeekBar
    private lateinit var tvDelayLabel: TextView
    private lateinit var sbDelayMin: SeekBar
    private lateinit var sbDelayMax: SeekBar
    private lateinit var cbAutoLure: android.widget.CheckBox
    private lateinit var tvLureDurationLabel: TextView
    private lateinit var sbLureDuration: SeekBar

    private val micPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        updatePermissionButtons()
        if (!isGranted) {
            Toast.makeText(this, "Microphone permission is required to detect splash sound", Toast.LENGTH_SHORT).show()
        }
    }

    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) {
        updatePermissionButtons()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        config = BotConfig.load(this)

        initViews()
        setupListeners()
        requestNotificationPermissionIfNeeded()
    }

    override fun onResume() {
        super.onResume()
        updatePermissionButtons()
    }

    private fun initViews() {
        btnPermOverlay = findViewById(R.id.btnPermOverlay)
        btnPermAccessibility = findViewById(R.id.btnPermAccessibility)
        btnPermMic = findViewById(R.id.btnPermMic)
        btnLaunchOverlay = findViewById(R.id.btnLaunchOverlay)
        btnStopOverlay = findViewById(R.id.btnStopOverlay)

        tvThresholdLabel = findViewById(R.id.tvMainThresholdLabel)
        sbThreshold = findViewById(R.id.sbMainThreshold)
        tvDelayLabel = findViewById(R.id.tvMainDelayLabel)
        sbDelayMin = findViewById(R.id.sbMainDelayMin)
        sbDelayMax = findViewById(R.id.sbMainDelayMax)
        cbAutoLure = findViewById(R.id.cbMainAutoLure)
        tvLureDurationLabel = findViewById(R.id.tvMainLureDurationLabel)
        sbLureDuration = findViewById(R.id.sbMainLureDuration)

        // Initialize sliders from saved config
        sbThreshold.progress = config.soundThreshold
        tvThresholdLabel.text = "Sound Threshold: ${config.soundThreshold}"

        val minProgress = ((config.minRecastDelaySec - 0.5f) / 0.5f).toInt().coerceIn(0, 10)
        val maxProgress = ((config.maxRecastDelaySec - 0.5f) / 0.5f).toInt().coerceIn(0, 10)
        sbDelayMin.progress = minProgress
        sbDelayMax.progress = maxProgress
        updateDelayLabel()

        cbAutoLure.isChecked = config.autoLureEnabled
        sbLureDuration.progress = (config.lureDurationMinutes - 1).coerceIn(0, 29)
        tvLureDurationLabel.text = "Lure Duration: ${config.lureDurationMinutes} min"
    }

    private fun setupListeners() {
        btnPermOverlay.setOnClickListener {
            val intent = Intent(
                Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:$packageName")
            )
            startActivity(intent)
        }

        btnPermAccessibility.setOnClickListener {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            startActivity(intent)
        }

        btnPermMic.setOnClickListener {
            micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }

        sbThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(5)
                config.soundThreshold = clamped
                tvThresholdLabel.text = "Sound Threshold: $clamped"
                config.save(this@MainActivity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbDelayMin.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.5f + (progress * 0.5f)
                config.minRecastDelaySec = value
                if (config.maxRecastDelaySec < value) {
                    config.maxRecastDelaySec = value
                    sbDelayMax.progress = progress
                }
                updateDelayLabel()
                config.save(this@MainActivity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        sbDelayMax.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (0.5f + (progress * 0.5f)).coerceAtLeast(config.minRecastDelaySec)
                config.maxRecastDelaySec = value
                updateDelayLabel()
                config.save(this@MainActivity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        cbAutoLure.setOnCheckedChangeListener { _, isChecked ->
            config.autoLureEnabled = isChecked
            config.save(this@MainActivity)
        }

        sbLureDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val minutes = progress + 1
                config.lureDurationMinutes = minutes
                tvLureDurationLabel.text = "Lure Duration: $minutes min"
                config.save(this@MainActivity)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnLaunchOverlay.setOnClickListener {
            if (!hasAllPermissions()) {
                Toast.makeText(this, "Please grant all required permissions first", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val serviceIntent = Intent(this, FishingOverlayService::class.java)
            ContextCompat.startForegroundService(this, serviceIntent)
            btnStopOverlay.visibility = View.VISIBLE
            Toast.makeText(this, "Overlay launched!", Toast.LENGTH_SHORT).show()
        }

        btnStopOverlay.setOnClickListener {
            val serviceIntent = Intent(this, FishingOverlayService::class.java)
            stopService(serviceIntent)
            btnStopOverlay.visibility = View.GONE
            Toast.makeText(this, "Overlay stopped", Toast.LENGTH_SHORT).show()
        }
    }

    private fun updateDelayLabel() {
        tvDelayLabel.text = String.format(
            "Recast Delay: %.1fs - %.1fs (Randomized)",
            config.minRecastDelaySec,
            config.maxRecastDelaySec
        )
    }

    private fun hasOverlayPermission(): Boolean {
        return Settings.canDrawOverlays(this)
    }

    private fun hasMicPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAllPermissions(): Boolean {
        return hasOverlayPermission() &&
                FishingAccessibilityService.isRunning &&
                hasMicPermission()
    }

    private fun updatePermissionButtons() {
        val overlayGranted = hasOverlayPermission()
        btnPermOverlay.text = if (overlayGranted) getString(R.string.status_granted) else getString(R.string.btn_grant)
        btnPermOverlay.isEnabled = !overlayGranted

        val accessibilityActive = FishingAccessibilityService.isRunning
        btnPermAccessibility.text = if (accessibilityActive) getString(R.string.status_granted) else getString(R.string.btn_grant)
        btnPermAccessibility.isEnabled = !accessibilityActive

        val micGranted = hasMicPermission()
        btnPermMic.text = if (micGranted) getString(R.string.status_granted) else getString(R.string.btn_grant)
        btnPermMic.isEnabled = !micGranted

        btnLaunchOverlay.isEnabled = hasAllPermissions()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val permission = Manifest.permission.POST_NOTIFICATIONS
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(permission)
            }
        }
    }
}
