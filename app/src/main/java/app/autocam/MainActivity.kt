package app.autocam

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.RectF
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CaptureRequest
import android.media.MediaRecorder
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.provider.MediaStore
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.view.ViewOutlineProvider
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Space
import android.widget.TextView
import android.widget.VideoView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.io.File
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity() {
    private enum class Screen { HOME, LIVE, REVIEW }

    private lateinit var home: View
    private lateinit var live: FrameLayout
    private lateinit var liveStage: FrameLayout
    private lateinit var review: View
    private lateinit var preview: TextureView
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
    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var recording = false
    private var clip: File? = null
    private var videoSize = Size(1440, 1080)
    private var activeArray: Rect? = null
    private var sensorOrientation = 270
    private var minZoom = 1f
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
    private var thread: HandlerThread? = null
    private var handler: Handler? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        bindViews()
        previewCard.clipToOutline = true
        previewCard.outlineProvider = ViewOutlineProvider.BACKGROUND
        reviewCard.clipToOutline = true
        reviewCard.outlineProvider = ViewOutlineProvider.BACKGROUND
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                if (screen == Screen.REVIEW) return
                if (camera != null) startPreview() else openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                configureTransform()
            }
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }
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
        startThread()
        if (!hasPermissions()) requestPermissions()
        else if (screen != Screen.REVIEW && preview.isAvailable) openCamera()
    }

    override fun onPause() {
        if (recording) stopRecording()
        closeCamera()
        stopThread()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        val ratio = uprightRatio()
        previewCard.ratio = ratio
        reviewCard.ratio = ratio
        preview.post { configureTransform() }
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
            if (preview.isAvailable && screen != Screen.REVIEW) openCamera()
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
        if (hasPermissions() && preview.isAvailable) openCamera()
    }

    private fun showLive() {
        if (screen == Screen.REVIEW) return
        screen = Screen.LIVE
        mountPreview(liveStage)
        home.visibility = View.GONE
        live.visibility = View.VISIBLE
        review.visibility = View.GONE
        applyChrome()
        if (camera != null && preview.isAvailable) startPreview()
        else if (preview.isAvailable) openCamera()
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
        previewLockup.visibility = if (camera == null && !recording) View.VISIBLE else View.GONE
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

    private fun startThread() {
        if (thread != null) return
        thread = HandlerThread("cam").also { it.start() }
        handler = Handler(thread!!.looper)
    }

    private fun stopThread() {
        thread?.quitSafely()
        thread = null
        handler = null
    }

    private fun openCamera() {
        if (!hasPermissions() || screen == Screen.REVIEW || camera != null) return
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val id = cameraId(manager) ?: run {
            say("No camera on this phone.")
            return
        }
        val chars = manager.getCameraCharacteristics(id)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
        activeArray = chars.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE)
        minZoom = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            chars.get(CameraCharacteristics.CONTROL_ZOOM_RATIO_RANGE)?.lower ?: 1f
        } else 1f
        videoSize = chooseSize(chars)
        runOnUiThread {
            val ratio = uprightRatio()
            previewCard.ratio = ratio
            reviewCard.ratio = ratio
            preview.post { configureTransform() }
        }
        message.visibility = View.GONE
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
                    runOnUiThread {
                        previewLockup.visibility = View.GONE
                        applyChrome()
                    }
                    startPreview()
                }
                override fun onDisconnected(device: CameraDevice) {
                    device.close()
                    if (camera == device) camera = null
                }
                override fun onError(device: CameraDevice, error: Int) {
                    device.close()
                    if (camera == device) camera = null
                    runOnUiThread { say("The camera didn’t open. Close Samsung Camera and try again.") }
                }
            }, handler)
        } catch (_: SecurityException) {
            say("Allow the camera and microphone, then open Auto Cam again.")
        }
    }

    private fun cameraId(manager: CameraManager): String? {
        val want = if (front) CameraCharacteristics.LENS_FACING_FRONT else CameraCharacteristics.LENS_FACING_BACK
        val ids = manager.cameraIdList
        return ids.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) == want
        } ?: ids.firstOrNull()
    }

    private fun chooseSize(chars: CameraCharacteristics): Size {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val recorder = map?.getOutputSizes(MediaRecorder::class.java)?.toSet().orEmpty()
        val previewSizes = map?.getOutputSizes(android.graphics.SurfaceTexture::class.java)?.toList().orEmpty()
        val sizes = previewSizes.filter { recorder.isEmpty() || it in recorder }.ifEmpty { recorder.toList() }
        if (sizes.isEmpty()) return Size(1440, 1080)
        val active = activeArray
        val target = if (active != null && active.height() > 0) {
            active.width().toFloat() / active.height()
        } else {
            4f / 3f
        }
        val capped = sizes.filter { max(it.width, it.height) <= 1920 }.ifEmpty { sizes }
        return capped.minBy {
            val aspect = it.width.toFloat() / it.height
            abs(aspect - target) * 100f - (it.width * it.height) / 1_000_000f
        }
    }

    private fun uprightRatio(): Float {
        val wide = max(videoSize.width, videoSize.height).toFloat()
        val tall = min(videoSize.width, videoSize.height).toFloat()
        val portrait = resources.configuration.orientation == Configuration.ORIENTATION_PORTRAIT
        return if (portrait) tall / wide else wide / tall
    }

    private fun startPreview() {
        val device = camera ?: return
        if (recording) return
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(videoSize.width, videoSize.height)
        runOnUiThread { configureTransform() }
        val surface = Surface(texture)
        closeSession()
        try {
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(capture: CameraCaptureSession) {
                    if (camera == null) return
                    session = capture
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW).apply {
                        addTarget(surface)
                        applyWideView()
                    }
                    try {
                        capture.setRepeatingRequest(request.build(), null, handler)
                    } catch (_: Exception) {
                    }
                }
                override fun onConfigureFailed(capture: CameraCaptureSession) {
                    runOnUiThread { say("The preview didn’t start.") }
                }
            }, handler)
        } catch (_: Exception) {
            runOnUiThread { say("The preview didn’t start.") }
        }
    }

    private fun CaptureRequest.Builder.applyWideView() {
        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
        activeArray?.let { set(CaptureRequest.SCALER_CROP_REGION, it) }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            set(CaptureRequest.CONTROL_ZOOM_RATIO, minZoom)
        }
    }

    /** Turns the camera picture with the phone, instead of leaving it a quarter-turn out. */
    private fun configureTransform() {
        val viewW = preview.width
        val viewH = preview.height
        if (viewW == 0 || viewH == 0) return
        val matrix = Matrix()
        val viewRect = RectF(0f, 0f, viewW.toFloat(), viewH.toFloat())
        val bufferRect = RectF(0f, 0f, videoSize.height.toFloat(), videoSize.width.toFloat())
        val cx = viewRect.centerX()
        val cy = viewRect.centerY()
        val rotation = surfaceRotation()
        if (rotation == Surface.ROTATION_90 || rotation == Surface.ROTATION_270) {
            bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER)
            matrix.postRotate(90f * (rotation - 2), cx, cy)
        } else if (rotation == Surface.ROTATION_180) {
            matrix.postRotate(180f, cx, cy)
        } else {
            bufferRect.offset(cx - bufferRect.centerX(), cy - bufferRect.centerY())
            matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER)
        }
        if (front) matrix.postScale(-1f, 1f, cx, cy)
        preview.setTransform(matrix)
    }

    private fun flip() {
        if (recording || screen != Screen.LIVE) return
        front = !front
        closeCamera()
        openCamera()
    }

    private fun startRecording() {
        val device = camera ?: return
        if (recording || screen == Screen.REVIEW) return
        closeSession()
        val file = File(cacheDir, "autocam-${System.currentTimeMillis()}.mp4")
        if (!prepareRecorder(file, true) && !prepareRecorder(file, false)) {
            say("Couldn’t start the camcorder microphone.")
            startPreview()
            return
        }
        val rec = recorder ?: return
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(videoSize.width, videoSize.height)
        val previewSurface = Surface(texture)
        val recordSurface = rec.surface
        try {
            device.createCaptureSession(listOf(previewSurface, recordSurface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(capture: CameraCaptureSession) {
                    session = capture
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(previewSurface)
                        addTarget(recordSurface)
                        applyWideView()
                    }
                    capture.setRepeatingRequest(request.build(), null, handler)
                    rec.start()
                    runOnUiThread {
                        clip = file
                        recording = true
                        startedAt = SystemClock.elapsedRealtime()
                        timer.text = "0:00"
                        liveTimer.text = "0:00"
                        startPulse()
                        timer.post(tick)
                        applyChrome()
                    }
                }
                override fun onConfigureFailed(capture: CameraCaptureSession) {
                    releaseRecorder()
                    runOnUiThread {
                        say("Couldn’t start recording.")
                        startPreview()
                    }
                }
            }, handler)
        } catch (_: Exception) {
            releaseRecorder()
            say("Couldn’t start recording.")
            startPreview()
        }
    }

    private fun prepareRecorder(file: File, stereo: Boolean): Boolean {
        releaseRecorder()
        val rec = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) MediaRecorder(this) else MediaRecorder()
        return try {
            rec.setAudioSource(MediaRecorder.AudioSource.CAMCORDER)
            rec.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            rec.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            rec.setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            rec.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            rec.setAudioChannels(if (stereo) 2 else 1)
            rec.setAudioSamplingRate(48000)
            rec.setAudioEncodingBitRate(256_000)
            rec.setVideoEncodingBitRate(8_000_000)
            rec.setVideoFrameRate(30)
            rec.setVideoSize(videoSize.width, videoSize.height)
            rec.setOrientationHint(orientationHint())
            rec.setOutputFile(file.absolutePath)
            rec.prepare()
            recorder = rec
            true
        } catch (_: Exception) {
            rec.release()
            false
        }
    }

    private fun displayRotationDegrees(): Int = when (surfaceRotation()) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }

    private fun surfaceRotation(): Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        display?.rotation ?: Surface.ROTATION_0
    } else {
        @Suppress("DEPRECATION")
        windowManager.defaultDisplay.rotation
    }

    private fun orientationHint(): Int {
        val degrees = displayRotationDegrees()
        val hint = if (front) {
            (sensorOrientation + degrees) % 360
        } else {
            (sensorOrientation - degrees + 360) % 360
        }
        return if (front) (360 - hint) % 360 else hint
    }

    private fun stopRecording() {
        if (!recording) return
        recording = false
        timer.removeCallbacks(tick)
        pulseHome?.cancel()
        pulseLive?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {
            clip?.delete()
            clip = null
        }
        releaseRecorder()
        closeSession()
        if (clip != null && clip!!.length() > 0) {
            closeCamera()
            showReview()
        } else {
            applyChrome()
            startPreview()
        }
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

    private fun closeSession() {
        try {
            session?.close()
        } catch (_: Exception) {
        }
        session = null
    }

    private fun releaseRecorder() {
        try {
            recorder?.reset()
        } catch (_: Exception) {
        }
        try {
            recorder?.release()
        } catch (_: Exception) {
        }
        recorder = null
    }

    private fun closeCamera() {
        closeSession()
        releaseRecorder()
        try {
            camera?.close()
        } catch (_: Exception) {
        }
        camera = null
    }

    private fun clock(ms: Long): String {
        val total = (ms / 1000).toInt().coerceAtLeast(0)
        return "${total / 60}:${(total % 60).toString().padStart(2, '0')}"
    }
}
