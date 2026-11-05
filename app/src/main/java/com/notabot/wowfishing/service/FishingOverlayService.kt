package com.notabot.wowfishing.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.Button
import android.widget.CheckBox
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.NotificationCompat
import com.notabot.wowfishing.R
import com.notabot.wowfishing.audio.AudioSplashDetector
import com.notabot.wowfishing.model.BotConfig

class FishingOverlayService : Service() {

    private lateinit var windowManager: WindowManager
    private lateinit var config: BotConfig
    private val mainHandler = Handler(Looper.getMainLooper())

    private var controlView: View? = null
    private var castMarkerView: View? = null
    private var reelMarkerView: View? = null
    private var lureMarkerView: View? = null
    private var settingsDialogView: View? = null

    private lateinit var audioDetector: AudioSplashDetector

    private var isBotActive = false
    private var isReelingOrDelaying = false
    private var lureExpirationTimestamp = 0L

    private val timeoutRunnable = Runnable {
        if (isBotActive && !isReelingOrDelaying) {
            updateStatusText("Timeout reached, recasting…")
            startOrContinueFishingCycle()
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        config = BotConfig.load(this)

        startForegroundNotification()
        setupAudioDetector()
        createTargetMarkers()
        createControlOverlay()
    }

    private fun startForegroundNotification() {
        val channelId = "nabo_service_channel"
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                channelId,
                getString(R.string.channel_name),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = getString(R.string.channel_description)
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification: Notification = NotificationCompat.Builder(this, channelId)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(getString(R.string.notification_content))
            .setSmallIcon(R.drawable.ic_launcher_foreground)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
            )
        } else {
            startForeground(NOTIFICATION_ID, notification)
        }
    }

    private fun setupAudioDetector() {
        audioDetector = AudioSplashDetector(
            onAudioLevel = { level ->
                mainHandler.post {
                    controlView?.findViewById<ProgressBar>(R.id.pbAudioLevel)?.progress = level
                }
            },
            onSplashDetected = {
                mainHandler.post {
                    onCatchTriggered()
                }
            }
        ).apply {
            threshold = config.soundThreshold
            start()
        }
    }

    private fun getOverlayWindowType(): Int {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }
    }

    @SuppressLint("InflateParams")
    private fun createControlOverlay() {
        val inflater = LayoutInflater.from(this)
        val view = inflater.inflate(R.layout.overlay_controls, null)
        controlView = view

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 50
            y = 120
        }

        val btnToggle = view.findViewById<ImageButton>(R.id.btnToggleRunning)
        val btnSettings = view.findViewById<ImageButton>(R.id.btnOverlaySettings)
        val btnClose = view.findViewById<ImageButton>(R.id.btnCloseOverlay)
        val dragHandle = view.findViewById<ImageView>(R.id.ivDragHandle)

        setupDragListener(dragHandle, params, view)

        btnToggle.setOnClickListener {
            toggleBotState()
        }

        btnSettings.setOnClickListener {
            toggleSettingsDialog()
        }

        btnClose.setOnClickListener {
            stopSelf()
        }

        windowManager.addView(view, params)
    }

    @SuppressLint("InflateParams")
    private fun createTargetMarkers() {
        val inflater = LayoutInflater.from(this)
        val markerSize = dpToPx(64)
        val halfMarker = markerSize / 2f

        if (config.castX <= 0f) config.castX = 200f + halfMarker
        if (config.castY <= 0f) config.castY = 300f + halfMarker
        if (config.reelX <= 0f) config.reelX = 400f + halfMarker
        if (config.reelY <= 0f) config.reelY = 300f + halfMarker
        if (config.lureX <= 0f) config.lureX = 600f + halfMarker
        if (config.lureY <= 0f) config.lureY = 300f + halfMarker

        // Marker 1: Cast Button Target
        val castView = inflater.inflate(R.layout.overlay_target_marker, null)
        castMarkerView = castView
        castView.findViewById<TextView>(R.id.tvTargetName).text = getString(R.string.overlay_cast_label)
        castView.findViewById<View>(R.id.viewCenterDot).setBackgroundColor(getColor(R.color.overlay_cast))

        val castParams = WindowManager.LayoutParams(
            markerSize,
            markerSize,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (config.castX - halfMarker).toInt()
            y = (config.castY - halfMarker).toInt()
        }

        setupMarkerDragListener(castView, castParams) { centerX, centerY ->
            config.castX = centerX
            config.castY = centerY
            config.save(this)
        }
        windowManager.addView(castView, castParams)

        // Marker 2: Reel Button Target
        val reelView = inflater.inflate(R.layout.overlay_target_marker, null)
        reelMarkerView = reelView
        reelView.findViewById<TextView>(R.id.tvTargetName).text = getString(R.string.overlay_reel_label)
        reelView.findViewById<View>(R.id.viewCenterDot).setBackgroundColor(getColor(R.color.overlay_reel))

        val reelParams = WindowManager.LayoutParams(
            markerSize,
            markerSize,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (config.reelX - halfMarker).toInt()
            y = (config.reelY - halfMarker).toInt()
        }

        setupMarkerDragListener(reelView, reelParams) { centerX, centerY ->
            config.reelX = centerX
            config.reelY = centerY
            config.save(this)
        }
        windowManager.addView(reelView, reelParams)

        // Marker 3: Lure Button Target
        val lureView = inflater.inflate(R.layout.overlay_target_marker, null)
        lureMarkerView = lureView
        lureView.findViewById<TextView>(R.id.tvTargetName).text = getString(R.string.overlay_lure_label)
        lureView.findViewById<View>(R.id.viewCenterDot).setBackgroundColor(getColor(R.color.overlay_lure))
        lureView.visibility = if (config.autoLureEnabled) View.VISIBLE else View.GONE

        val lureParams = WindowManager.LayoutParams(
            markerSize,
            markerSize,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = (config.lureX - halfMarker).toInt()
            y = (config.lureY - halfMarker).toInt()
        }

        setupMarkerDragListener(lureView, lureParams) { centerX, centerY ->
            config.lureX = centerX
            config.lureY = centerY
            config.save(this)
        }
        windowManager.addView(lureView, lureParams)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupDragListener(
        touchView: View,
        params: WindowManager.LayoutParams,
        rootView: View
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        touchView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(rootView, params)
                    true
                }
                else -> false
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun setupMarkerDragListener(
        markerView: View,
        params: WindowManager.LayoutParams,
        onPositionChanged: (Float, Float) -> Unit
    ) {
        var initialX = 0
        var initialY = 0
        var initialTouchX = 0f
        var initialTouchY = 0f

        markerView.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params.x
                    initialY = params.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params.x = initialX + (event.rawX - initialTouchX).toInt()
                    params.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager.updateViewLayout(markerView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val centerX = params.x.toFloat() + (params.width / 2f)
                    val centerY = params.y.toFloat() + (params.height / 2f)
                    onPositionChanged(centerX, centerY)
                    true
                }
                else -> false
            }
        }
    }

    private fun toggleBotState() {
        if (!FishingAccessibilityService.isRunning) {
            Toast.makeText(this, "Enable Accessibility Service first", Toast.LENGTH_LONG).show()
            return
        }

        isBotActive = !isBotActive
        val btnToggle = controlView?.findViewById<ImageButton>(R.id.btnToggleRunning)

        if (isBotActive) {
            btnToggle?.setImageResource(R.drawable.ic_stop)
            btnToggle?.setColorFilter(getColor(R.color.accent_red))
            isReelingOrDelaying = false
            updateStatusText("Starting automation…")
            startOrContinueFishingCycle()
        } else {
            btnToggle?.setImageResource(R.drawable.ic_play)
            btnToggle?.setColorFilter(getColor(R.color.accent_green))
            audioDetector.disarmTrigger()
            mainHandler.removeCallbacksAndMessages(null)
            isReelingOrDelaying = false
            updateStatusText(getString(R.string.overlay_status_idle))
        }
    }

    private fun startOrContinueFishingCycle() {
        if (!isBotActive) return

        if (config.autoLureEnabled && isLureExpired()) {
            applyLureThen {
                castFishingLine()
            }
        } else {
            castFishingLine()
        }
    }

    private fun isLureExpired(): Boolean {
        return SystemClock.uptimeMillis() >= lureExpirationTimestamp
    }

    private fun applyLureThen(onComplete: () -> Unit) {
        if (!isBotActive) return

        updateStatusText(getString(R.string.overlay_status_luring))
        mainHandler.removeCallbacks(timeoutRunnable)

        val accessibility = FishingAccessibilityService.instance
        if (accessibility == null) {
            stopBotOnError("Accessibility service disconnected")
            return
        }

        accessibility.click(config.lureX, config.lureY) {
            if (!isBotActive) return@click

            lureExpirationTimestamp = SystemClock.uptimeMillis() + (config.lureDurationMinutes * 60 * 1000L)
            val delayMs = (config.lureApplyDelaySec * 1000f).toLong().coerceAtLeast(2500L)
            updateStatusText(String.format("Lure applied! Cast in %.1fs", config.lureApplyDelaySec))

            mainHandler.postDelayed({
                if (isBotActive) {
                    onComplete()
                }
            }, delayMs)
        }
    }

    private fun castFishingLine() {
        if (!isBotActive) return

        updateStatusText(getString(R.string.overlay_status_casting))
        mainHandler.removeCallbacks(timeoutRunnable)

        val accessibility = FishingAccessibilityService.instance
        if (accessibility == null) {
            stopBotOnError("Accessibility service disconnected")
            return
        }

        accessibility.click(config.castX, config.castY) {
            if (!isBotActive) return@click

            // Arm sound detector after a 2.5s ignore window (to filter casting/splash landing noise)
            audioDetector.armTrigger(gracePeriodMs = 2500L)
            updateStatusText(getString(R.string.overlay_status_listening))

            // Schedule timeout in case no fish bites
            val timeoutMs = (config.castTimeoutSec * 1000L).coerceAtLeast(15000L)
            mainHandler.postDelayed(timeoutRunnable, timeoutMs)
        }
    }

    private fun onCatchTriggered() {
        if (!isBotActive || isReelingOrDelaying) return

        isReelingOrDelaying = true
        mainHandler.removeCallbacks(timeoutRunnable)
        updateStatusText(getString(R.string.overlay_status_reeling))

        val accessibility = FishingAccessibilityService.instance
        if (accessibility == null) {
            stopBotOnError("Accessibility service disconnected")
            return
        }

        // Tap the Reel button binding
        accessibility.click(config.reelX, config.reelY) {
            if (!isBotActive) return@click

            val delayMillis = config.getRandomRecastDelayMillis()
            val delaySeconds = delayMillis / 1000.0f
            updateStatusText(String.format("Reeled! Next action in %.1fs", delaySeconds))

            // Wait randomized delay before next cycle
            mainHandler.postDelayed({
                if (isBotActive) {
                    isReelingOrDelaying = false
                    startOrContinueFishingCycle()
                }
            }, delayMillis)
        }
    }

    private fun updateStatusText(message: String) {
        controlView?.findViewById<TextView>(R.id.tvBotStatus)?.text = message
    }

    private fun stopBotOnError(errorMessage: String) {
        isBotActive = false
        audioDetector.disarmTrigger()
        mainHandler.removeCallbacksAndMessages(null)
        controlView?.findViewById<ImageButton>(R.id.btnToggleRunning)?.setImageResource(R.drawable.ic_play)
        controlView?.findViewById<ImageButton>(R.id.btnToggleRunning)?.setColorFilter(getColor(R.color.accent_green))
        updateStatusText("Error: $errorMessage")
        Toast.makeText(this, errorMessage, Toast.LENGTH_SHORT).show()
    }

    @SuppressLint("InflateParams")
    private fun toggleSettingsDialog() {
        if (settingsDialogView != null) {
            windowManager.removeView(settingsDialogView)
            settingsDialogView = null
            return
        }

        val inflater = LayoutInflater.from(this)
        val dialogView = inflater.inflate(R.layout.dialog_settings, null)
        settingsDialogView = dialogView

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            getOverlayWindowType(),
            WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.CENTER
        }

        val tvThreshold = dialogView.findViewById<TextView>(R.id.tvThresholdLabel)
        val sbThreshold = dialogView.findViewById<SeekBar>(R.id.sbThreshold)
        val tvMinDelay = dialogView.findViewById<TextView>(R.id.tvMinDelayLabel)
        val sbMinDelay = dialogView.findViewById<SeekBar>(R.id.sbMinDelay)
        val tvMaxDelay = dialogView.findViewById<TextView>(R.id.tvMaxDelayLabel)
        val sbMaxDelay = dialogView.findViewById<SeekBar>(R.id.sbMaxDelay)
        val cbAutoLure = dialogView.findViewById<CheckBox>(R.id.cbAutoLure)
        val tvLureDuration = dialogView.findViewById<TextView>(R.id.tvLureDurationLabel)
        val sbLureDuration = dialogView.findViewById<SeekBar>(R.id.sbLureDuration)
        val btnClose = dialogView.findViewById<Button>(R.id.btnCloseSettings)

        // Threshold setup
        sbThreshold.progress = config.soundThreshold
        tvThreshold.text = "Sound Threshold: ${config.soundThreshold}"
        sbThreshold.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceAtLeast(5)
                tvThreshold.text = "Sound Threshold: $clamped"
                config.soundThreshold = clamped
                audioDetector.threshold = clamped
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Min Delay setup (0.5s to 5.0s, step 0.5)
        val minDelayProgress = ((config.minRecastDelaySec - 0.5f) / 0.5f).toInt().coerceIn(0, 10)
        sbMinDelay.progress = minDelayProgress
        tvMinDelay.text = String.format("Min Delay: %.1fs", config.minRecastDelaySec)
        sbMinDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = 0.5f + (progress * 0.5f)
                config.minRecastDelaySec = value
                if (config.maxRecastDelaySec < value) {
                    config.maxRecastDelaySec = value
                    sbMaxDelay.progress = progress
                }
                tvMinDelay.text = String.format("Min Delay: %.1fs", value)
                tvMaxDelay.text = String.format("Max Delay: %.1fs", config.maxRecastDelaySec)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Max Delay setup
        val maxDelayProgress = ((config.maxRecastDelaySec - 0.5f) / 0.5f).toInt().coerceIn(0, 10)
        sbMaxDelay.progress = maxDelayProgress
        tvMaxDelay.text = String.format("Max Delay: %.1fs", config.maxRecastDelaySec)
        sbMaxDelay.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val value = (0.5f + (progress * 0.5f)).coerceAtLeast(config.minRecastDelaySec)
                config.maxRecastDelaySec = value
                tvMaxDelay.text = String.format("Max Delay: %.1fs", value)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        // Auto-Lure setup
        cbAutoLure.isChecked = config.autoLureEnabled
        tvLureDuration.text = "Lure Duration: ${config.lureDurationMinutes} min"
        sbLureDuration.progress = (config.lureDurationMinutes - 1).coerceIn(0, 29)

        cbAutoLure.setOnCheckedChangeListener { _, isChecked ->
            config.autoLureEnabled = isChecked
            lureMarkerView?.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (isChecked) {
                lureExpirationTimestamp = 0L
            }
        }

        sbLureDuration.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val minutes = progress + 1
                config.lureDurationMinutes = minutes
                tvLureDuration.text = "Lure Duration: $minutes min"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnClose.setOnClickListener {
            config.save(this)
            windowManager.removeView(dialogView)
            settingsDialogView = null
        }

        windowManager.addView(dialogView, params)
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    override fun onDestroy() {
        super.onDestroy()
        isBotActive = false
        mainHandler.removeCallbacksAndMessages(null)
        audioDetector.stop()

        controlView?.let { windowManager.removeView(it) }
        castMarkerView?.let { windowManager.removeView(it) }
        reelMarkerView?.let { windowManager.removeView(it) }
        lureMarkerView?.let { windowManager.removeView(it) }
        settingsDialogView?.let { windowManager.removeView(it) }

        config.save(this)
    }

    companion object {
        private const val NOTIFICATION_ID = 1001
    }
}
