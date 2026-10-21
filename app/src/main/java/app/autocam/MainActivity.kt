package app.autocam

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.Bundle
import android.os.Environment
import android.os.SystemClock
import android.provider.MediaStore
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
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
    private lateinit var recBadge: View
    private lateinit var recDot: View
    private lateinit var timer: TextView
    private lateinit var sideColumn: View
    private lateinit var sideSpacer: View
    private lateinit var rowRecord: View
    private lateinit var rowEnd: View
    private lateinit var statusText: TextView
    private lateinit var hint: TextView
    private lateinit var openCameraButton: TextView
    private lateinit var liveFrame: View
    private lateinit var liveBadge: View
    private lateinit var liveDot: View
    private lateinit var liveTimer: TextView
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
    private var pulseLive: ObjectAnimator? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            val text = clock(SystemClock.elapsedRealtime() - startedAt)
            timer.text = text
            liveTimer.text = text
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
        recBadge = findViewById(R.id.recBadge)
        recDot = findViewById(R.id.recDot)
        timer = findViewById(R.id.timer)
        sideColumn = findViewById(R.id.sideColumn)
        sideSpacer = findViewById(R.id.sideSpacer)
        rowRecord = findViewById(R.id.rowRecord)
        rowEnd = findViewById(R.id.rowEnd)
        statusText = findViewById(R.id.statusText)
        hint = findViewById(R.id.hint)
        openCameraButton = findViewById(R.id.openCamera)
        liveFrame = findViewById(R.id.liveFrame)
        liveBadge = findViewById(R.id.liveBadge)
        liveDot = findViewById(R.id.liveDot)
        liveTimer = findViewById(R.id.liveTimer)
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
        message = findViewById(R.id.message)
    }

    override fun onResume() {
        super.onResume()
        if (!hasPermissions()) requestPermissions()
        else if (screen != Screen.REVIEW) openCamera()
    }

    override fun onPause() {
        if (recording) stopRecording() else closeCamera()
        super.onPause()
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
        applyChrome()
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
        recBadge.visibility = if (recording && onHome) View.VISIBLE else View.GONE
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
        liveBadge.visibility = if (recording && onLive) View.VISIBLE else View.GONE
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
                        liveTimer.text = "0:00"
                        startPulse()
                        timer.post(tick)
                        applyChrome()
                    }
                    is VideoRecordEvent.Finalize -> {
                        recording = false
                        timer.removeCallbacks(tick)
                        pulseHome?.cancel()
                        pulseLive?.cancel()
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
        pulseLive?.cancel()
        pulseHome = pulse(recDot)
        pulseLive = pulse(liveDot)
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

    private fun clock(ms: Long): String {
        val total = (ms / 1000).toInt().coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
