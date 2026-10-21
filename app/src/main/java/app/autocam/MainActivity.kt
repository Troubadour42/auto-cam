package app.autocam

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.Intent
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.video.FallbackStrategy
import androidx.camera.video.FileOutputOptions
import androidx.camera.video.Quality
import androidx.camera.video.QualitySelector
import androidx.camera.video.Recorder
import androidx.camera.video.Recording
import androidx.camera.video.VideoCapture
import androidx.camera.video.VideoRecordEvent
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File

class MainActivity : AppCompatActivity() {
    private enum class Screen { HOME, LIVE, REVIEW }

    private lateinit var home: View
    private lateinit var live: FrameLayout
    private lateinit var liveStage: FrameLayout
    private lateinit var review: View
    private lateinit var preview: PreviewView
    private lateinit var previewCard: AspectFrameLayout
    private lateinit var previewLockup: ImageView
    private lateinit var recDot: View
    private lateinit var timer: TextView
    private lateinit var sideClock: View
    private lateinit var portraitClock: View
    private lateinit var portraitDot: View
    private lateinit var portraitTimer: TextView
    private lateinit var sideColumn: View
    private lateinit var sideSpacer: View
    private lateinit var rowRecord: View
    private lateinit var rowEnd: View
    private lateinit var statusText: TextView
    private lateinit var hint: TextView
    private lateinit var openCameraButton: TextView
    private lateinit var liveFrame: View
    private lateinit var liveClock: View
    private lateinit var liveDot: View
    private lateinit var liveTimer: TextView
    private lateinit var livePortraitClock: View
    private lateinit var livePortraitDot: View
    private lateinit var livePortraitTimer: TextView
    private lateinit var liveSide: View
    private lateinit var liveStatus: TextView
    private lateinit var shutterCore: View
    private lateinit var reviewLockup: View
    private lateinit var reviewTitle: View
    private lateinit var reviewSide: View
    private lateinit var reviewSpacer: View
    private lateinit var reviewButtons: View
    private lateinit var reviewCard: AspectFrameLayout
    private lateinit var playback: VideoView
    private lateinit var pauseButton: ImageButton
    private lateinit var message: TextView

    private var screen = Screen.HOME
    private var front = true
    private var recording = false
    private var cameraReady = false
    private var clip: File? = null
    private var provider: ProcessCameraProvider? = null
    private var videoCapture: VideoCapture<Recorder>? = null
    private var activeRecording: Recording? = null
    private var startedAt = 0L
    private var pulseHome: ObjectAnimator? = null
    private var pulsePortrait: ObjectAnimator? = null
    private var pulseLive: ObjectAnimator? = null
    private var pulseLivePortrait: ObjectAnimator? = null
    private var speech: SpeechRecognizer? = null
    private var voiceWanted = false
    private var voiceHeard = false
    private val voiceHandler = Handler(Looper.getMainLooper())
    private val restartVoice = Runnable { if (voiceWanted && !recording) listen() }
    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            val text = clock(SystemClock.elapsedRealtime() - startedAt)
            timer.text = text
            portraitTimer.text = text
            liveTimer.text = text
            livePortraitTimer.text = text
            timer.postDelayed(this, 200)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        bindViews()
        preview.scaleType = PreviewView.ScaleType.FIT_CENTER
        preview.implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        previewCard.clipToOutline = true
        previewCard.outlineProvider = ViewOutlineProvider.BACKGROUND
        reviewCard.clipToOutline = true
        reviewCard.outlineProvider = ViewOutlineProvider.BACKGROUND
        findViewById<View>(R.id.lockup).setOnClickListener { finish() }
        previewLockup.setOnClickListener { finish() }
        reviewLockup.setOnClickListener { finish() }
        findViewById<View>(R.id.btnOpen).setOnClickListener { showLive() }
        openCameraButton.setOnClickListener { showLive() }
        findViewById<View>(R.id.btnRecord).setOnClickListener { startRecording() }
        findViewById<View>(R.id.sideStart).setOnClickListener { startRecording() }
        findViewById<View>(R.id.liveStart).setOnClickListener { startRecording() }
        findViewById<View>(R.id.btnEnd).setOnClickListener { if (recording) stopRecording() }
        findViewById<View>(R.id.sideEnd).setOnClickListener { if (recording) stopRecording() }
        findViewById<View>(R.id.liveEnd).setOnClickListener { if (recording) stopRecording() }
        findViewById<View>(R.id.shutter).setOnClickListener {
            if (recording) stopRecording() else startRecording()
        }
        findViewById<View>(R.id.flip).setOnClickListener { flip() }
        findViewById<View>(R.id.closeLive).setOnClickListener { finish() }
        findViewById<View>(R.id.save).setOnClickListener { saveClip() }
        findViewById<View>(R.id.sideSave).setOnClickListener { saveClip() }
        findViewById<View>(R.id.delete).setOnClickListener { deleteClip() }
        findViewById<View>(R.id.sideDelete).setOnClickListener { deleteClip() }
        pauseButton.setOnClickListener { togglePause() }
        showHome()
    }

    private fun bindViews() {
        home = findViewById(R.id.home)
        live = findViewById(R.id.live)
        liveStage = findViewById(R.id.liveStage)
        review = findViewById(R.id.review)
        preview = findViewById(R.id.preview)
        previewCard = findViewById(R.id.previewCard)
        previewLockup = findViewById(R.id.previewLockup)
        recDot = findViewById(R.id.recDot)
        timer = findViewById(R.id.timer)
        sideClock = findViewById(R.id.sideClock)
        portraitClock = findViewById(R.id.portraitClock)
        portraitDot = findViewById(R.id.portraitDot)
        portraitTimer = findViewById(R.id.portraitTimer)
        sideColumn = findViewById(R.id.sideColumn)
        sideSpacer = findViewById(R.id.sideSpacer)
        rowRecord = findViewById(R.id.rowRecord)
        rowEnd = findViewById(R.id.rowEnd)
        statusText = findViewById(R.id.statusText)
        hint = findViewById(R.id.hint)
        openCameraButton = findViewById(R.id.openCamera)
        liveFrame = findViewById(R.id.liveFrame)
        liveClock = findViewById(R.id.liveClock)
        liveDot = findViewById(R.id.liveDot)
        liveTimer = findViewById(R.id.liveTimer)
        livePortraitClock = findViewById(R.id.livePortraitClock)
        livePortraitDot = findViewById(R.id.livePortraitDot)
        livePortraitTimer = findViewById(R.id.livePortraitTimer)
        liveSide = findViewById(R.id.liveSide)
        liveStatus = findViewById(R.id.liveStatus)
        shutterCore = findViewById(R.id.shutterCore)
        reviewLockup = findViewById(R.id.reviewLockup)
        reviewTitle = findViewById(R.id.reviewTitle)
        reviewSide = findViewById(R.id.reviewSide)
        reviewSpacer = findViewById(R.id.reviewSpacer)
        reviewButtons = findViewById(R.id.reviewButtons)
        reviewCard = findViewById(R.id.reviewCard)
        playback = findViewById(R.id.playback)
        pauseButton = findViewById(R.id.pause)
        message = findViewById(R.id.message)
    }

    override fun onResume() {
        super.onResume()
        if (!hasPermissions()) requestPermissions()
        else if (screen != Screen.REVIEW) openCamera()
        syncVoice()
    }

    override fun onPause() {
        if (recording) stopRecording() else closeCamera()
        stopVoice()
        super.onPause()
    }

    override fun onDestroy() {
        stopVoice()
        speech?.destroy()
        speech = null
        restoreSound()
        super.onDestroy()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        applyChrome()
    }

    private fun landscape() =
        resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE

    private fun hasPermissions() =
        ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            this,
            arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            1,
        )
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (hasPermissions()) {
            message.visibility = View.GONE
            if (screen != Screen.REVIEW) openCamera()
            syncVoice()
        } else {
            say("Allow the camera and microphone, then open Auto Cam again.")
        }
    }

    private fun showHome() {
        screen = Screen.HOME
        mountPreview(previewCard)
        home.visibility = View.VISIBLE
        live.visibility = View.GONE
        review.visibility = View.GONE
        applyChrome()
        if (hasPermissions()) openCamera()
    }

    private fun showLive() {
        if (screen == Screen.REVIEW || recording) return
        screen = Screen.LIVE
        mountPreview(liveStage)
        home.visibility = View.GONE
        live.visibility = View.VISIBLE
        review.visibility = View.GONE
        applyChrome()
        if (hasPermissions()) openCamera()
    }

    private fun showReview() {
        screen = Screen.REVIEW
        home.visibility = View.GONE
        live.visibility = View.GONE
        review.visibility = View.VISIBLE
        playback.setVideoPath(clip!!.absolutePath)
        playback.setOnPreparedListener {
            it.isLooping = true
            playback.start()
        }
        playback.start()
        showPauseIcon(playing = true)
        applyChrome()
        syncVoice()
    }

    private fun mountPreview(into: FrameLayout) {
        val parent = preview.parent as? ViewGroup
        if (parent === into) return
        parent?.removeView(preview)
        into.addView(
            preview,
            0,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
    }

    private fun applyChrome() {
        val land = landscape()
        val onHome = screen == Screen.HOME
        sideColumn.visibility = if (land && onHome) View.VISIBLE else View.GONE
        sideSpacer.visibility = if (land && onHome) View.VISIBLE else View.GONE
        rowRecord.visibility = if (land) View.GONE else View.VISIBLE
        rowEnd.visibility = if (land) View.GONE else View.VISIBLE
        previewLockup.visibility = if (!cameraReady && !recording) View.VISIBLE else View.GONE
        previewCard.setBackgroundResource(if (recording && onHome) R.drawable.card_rec else R.drawable.card_round)
        val showSideClock = recording && land && onHome
        val showPortraitClock = recording && !land && onHome
        sideClock.visibility = if (showSideClock) View.VISIBLE else View.GONE
        portraitClock.visibility = if (showPortraitClock) View.VISIBLE else View.GONE
        statusText.text = if (recording) "RECORDING" else "READY"
        statusText.setTextColor(ContextCompat.getColor(this, if (recording) R.color.rec else R.color.muted))
        hint.text = if (recording) "Tap the square to stop." else "Use the buttons to open the camera and record."
        findViewById<View>(R.id.btnRecord).isEnabled = !recording
        findViewById<View>(R.id.sideStart).isEnabled = !recording
        findViewById<View>(R.id.btnEnd).isEnabled = recording
        findViewById<View>(R.id.sideEnd).isEnabled = recording
        findViewById<View>(R.id.btnRecord).alpha = if (recording) 0.4f else 1f
        findViewById<View>(R.id.sideStart).alpha = if (recording) 0.4f else 1f
        findViewById<View>(R.id.btnEnd).alpha = if (recording) 1f else 0.4f
        findViewById<View>(R.id.sideEnd).alpha = if (recording) 1f else 0.4f

        val onLive = screen == Screen.LIVE
        liveSide.visibility = if (land && onLive) View.VISIBLE else View.GONE
        liveClock.visibility = if (recording && land && onLive) View.VISIBLE else View.GONE
        livePortraitClock.visibility = if (recording && !land && onLive) View.VISIBLE else View.GONE
        liveFrame.background = if (recording && onLive) ContextCompat.getDrawable(this, R.drawable.stroke_red) else null
        liveStatus.text = if (recording) "Recording" else "Ready"
        findViewById<View>(R.id.liveStart).isEnabled = !recording
        findViewById<View>(R.id.liveEnd).isEnabled = recording
        findViewById<View>(R.id.liveStart).alpha = if (recording) 0.4f else 1f
        findViewById<View>(R.id.liveEnd).alpha = if (recording) 1f else 0.4f
        findViewById<View>(R.id.flip).isEnabled = !recording
        findViewById<View>(R.id.flip).alpha = if (recording) 0.4f else 1f
        val core = if (recording) 28 else 56
        val px = (core * resources.displayMetrics.density).toInt()
        shutterCore.layoutParams = FrameLayout.LayoutParams(px, px).apply { gravity = android.view.Gravity.CENTER }
        shutterCore.setBackgroundResource(if (recording) R.drawable.shutter_stop else R.drawable.shutter_dot)

        val onReview = screen == Screen.REVIEW
        reviewLockup.visibility = if (land) View.GONE else View.VISIBLE
        reviewTitle.visibility = if (land) View.GONE else View.VISIBLE
        reviewSide.visibility = if (land && onReview) View.VISIBLE else View.GONE
        reviewSpacer.visibility = if (land && onReview) View.VISIBLE else View.GONE
        reviewButtons.visibility = if (land) View.GONE else View.VISIBLE
        syncVoice()
    }

    private fun say(text: String) {
        message.text = text
        message.visibility = View.VISIBLE
    }

    private fun openCamera() {
        if (!hasPermissions() || screen == Screen.REVIEW || recording) return
        val future = ProcessCameraProvider.getInstance(this)
        future.addListener({
            provider = future.get()
            bindCamera()
        }, ContextCompat.getMainExecutor(this))
    }

    private fun bindCamera() {
        val cameraProvider = provider ?: return
        if (screen == Screen.REVIEW || recording) return
        val previewUseCase = Preview.Builder()
            .setResolutionSelector(
                ResolutionSelector.Builder()
                    .setAspectRatioStrategy(AspectRatioStrategy.RATIO_16_9_FALLBACK_AUTO_STRATEGY)
                    .build(),
            )
            .build()
        previewUseCase.surfaceProvider = preview.surfaceProvider
        val recorder = Recorder.Builder()
            .setAspectRatio(androidx.camera.core.AspectRatio.RATIO_16_9)
            .setQualitySelector(
                QualitySelector.fromOrderedList(
                    listOf(Quality.FHD, Quality.HD, Quality.SD),
                    FallbackStrategy.lowerQualityOrHigherThan(Quality.SD),
                ),
            )
            .build()
        val video = VideoCapture.withOutput(recorder)
        videoCapture = video
        val selector = if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA
        try {
            cameraProvider.unbindAll()
            val camera = cameraProvider.bindToLifecycle(this, selector, previewUseCase, video)
            val widest = camera.cameraInfo.zoomState.value?.minZoomRatio ?: 1f
            camera.cameraControl.setZoomRatio(widest)
            cameraReady = true
            message.visibility = View.GONE
            applyChrome()
        } catch (_: Exception) {
            cameraReady = false
            say("The camera didn’t open. Close Samsung Camera and try again.")
        }
    }

    private fun flip() {
        if (recording || screen != Screen.LIVE) return
        front = !front
        bindCamera()
    }

    private fun startRecording() {
        val video = videoCapture ?: return
        if (recording || screen == Screen.REVIEW) return
        stopVoice()
        val file = File(cacheDir, "autocam-${System.currentTimeMillis()}.mp4")
        val options = FileOutputOptions.Builder(file).build()
        activeRecording = video.output
            .prepareRecording(this, options)
            .withAudioEnabled()
            .start(ContextCompat.getMainExecutor(this)) { event ->
                when (event) {
                    is VideoRecordEvent.Start -> {
                        clip = file
                        recording = true
                        startedAt = SystemClock.elapsedRealtime()
                        timer.text = "0:00"
                        portraitTimer.text = "0:00"
                        liveTimer.text = "0:00"
                        livePortraitTimer.text = "0:00"
                        startPulse()
                        timer.post(tick)
                        applyChrome()
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = false
                        timer.removeCallbacks(tick)
                        pulseHome?.cancel()
                        pulsePortrait?.cancel()
                        pulseLive?.cancel()
                        pulseLivePortrait?.cancel()
                        activeRecording = null
                        if (!event.hasError() && file.length() > 0) {
                            clip = file
                            closeCamera()
                            showReview()
                        } else {
                            file.delete()
                            if (event.hasError()) say("The recording didn’t finish.")
                            applyChrome()
                            openCamera()
                        }
                    }
                    else -> Unit
                }
            }
    }

    private fun stopRecording() {
        activeRecording?.stop()
    }

    private fun startPulse() {
        pulseHome?.cancel()
        pulsePortrait?.cancel()
        pulseLive?.cancel()
        pulseLivePortrait?.cancel()
        pulseHome = pulse(recDot)
        pulsePortrait = pulse(portraitDot)
        pulseLive = pulse(liveDot)
        pulseLivePortrait = pulse(livePortraitDot)
    }

    private fun pulse(view: View) = ObjectAnimator.ofFloat(view, View.ALPHA, 1f, 0.25f).apply {
        duration = 700
        repeatMode = ValueAnimator.REVERSE
        repeatCount = ValueAnimator.INFINITE
        start()
    }

    private fun saveClip() {
        val file = clip ?: return
        val name = "AutoCam-${System.currentTimeMillis()}.mp4"
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, name)
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, Environment.DIRECTORY_MOVIES + "/AutoCam")
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values) ?: run {
            say("Couldn’t save to the gallery.")
            return
        }
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        playback.stopPlayback()
        file.delete()
        clip = null
        showHome()
    }

    private fun deleteClip() {
        playback.stopPlayback()
        clip?.delete()
        clip = null
        showHome()
    }

    private fun closeCamera() {
        try {
            provider?.unbindAll()
        } catch (_: Exception) {
        }
        cameraReady = false
    }

    private fun togglePause() {
        if (screen != Screen.REVIEW) return
        if (playback.isPlaying) {
            playback.pause()
            showPauseIcon(playing = false)
        } else {
            playback.start()
            showPauseIcon(playing = true)
        }
    }

    private fun showPauseIcon(playing: Boolean) {
        pauseButton.setImageResource(if (playing) R.drawable.ic_pause else R.drawable.ic_play)
        pauseButton.contentDescription = if (playing) "Pause" else "Play"
    }

    private val voiceListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) { voiceHeard = false }
        override fun onBeginningOfSpeech() {}
        override fun onRmsChanged(rmsdB: Float) {}
        override fun onBufferReceived(buffer: ByteArray?) {}
        override fun onEndOfSpeech() {}
        override fun onError(error: Int) {
            if (!voiceWanted || recording) return
            voiceHandler.removeCallbacks(restartVoice)
            voiceHandler.postDelayed(restartVoice, 400)
        }
        override fun onResults(results: Bundle?) {
            results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach(::onHeard)
            if (voiceWanted && !recording) {
                voiceHandler.removeCallbacks(restartVoice)
                voiceHandler.postDelayed(restartVoice, 250)
            }
        }
        override fun onPartialResults(partial: Bundle?) {
            partial?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.forEach(::onHeard)
        }
        override fun onEvent(eventType: Int, params: Bundle?) {}
    }

    private fun syncVoice() {
        if (recording || !hasPermissions() || isFinishing) {
            stopVoice()
            return
        }
        if (!SpeechRecognizer.isRecognitionAvailable(this)) return
        hushBeep()
        if (voiceWanted) return
        voiceWanted = true
        if (speech == null) {
            try {
                speech = SpeechRecognizer.createSpeechRecognizer(this).also {
                    it.setRecognitionListener(voiceListener)
                }
            } catch (_: Exception) {
                voiceWanted = false
                return
            }
        }
        listen()
    }

    private fun listen() {
        if (!voiceWanted || recording) return
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 400L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 700L)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, packageName)
        }
        try {
            speech?.startListening(intent)
        } catch (_: Exception) {
        }
    }

    private fun stopVoice() {
        voiceWanted = false
        voiceHandler.removeCallbacks(restartVoice)
        try {
            speech?.cancel()
        } catch (_: Exception) {
        }
        restoreSound()
    }

    private fun onHeard(text: String) {
        if (voiceHeard || recording) return
        val said = text.lowercase()
        val start = said.contains("start recording") || said.contains("start record")
        val save = Regex("\\bsave\\b").containsMatchIn(said)
        val delete = Regex("\\bdelete\\b").containsMatchIn(said)
        when {
            screen == Screen.REVIEW && delete -> {
                voiceHeard = true
                deleteClip()
            }
            screen == Screen.REVIEW && save -> {
                voiceHeard = true
                saveClip()
            }
            screen != Screen.REVIEW && start -> {
                voiceHeard = true
                startRecording()
            }
        }
    }

    private fun hushBeep() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_MUTE, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_MUTE, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_MUTE, 0)
        val music = if (screen == Screen.REVIEW) AudioManager.ADJUST_UNMUTE else AudioManager.ADJUST_MUTE
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, music, 0)
    }

    private fun restoreSound() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) return
        val audio = getSystemService(AUDIO_SERVICE) as AudioManager
        audio.adjustStreamVolume(AudioManager.STREAM_SYSTEM, AudioManager.ADJUST_UNMUTE, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_NOTIFICATION, AudioManager.ADJUST_UNMUTE, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_RING, AudioManager.ADJUST_UNMUTE, 0)
        audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, AudioManager.ADJUST_UNMUTE, 0)
    }

    private fun clock(ms: Long): String {
        val total = (ms / 1000).toInt().coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
