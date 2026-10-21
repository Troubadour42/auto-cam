package app.autocam

import android.Manifest
import android.animation.ObjectAnimator
import android.animation.ValueAnimator
import android.content.ContentValues
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.graphics.Matrix
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
import android.widget.Button
import android.widget.LinearLayout
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

class MainActivity : AppCompatActivity() {
    private lateinit var preview: TextureView
    private lateinit var playback: VideoView
    private lateinit var frame: View
    private lateinit var recBadge: LinearLayout
    private lateinit var recDot: View
    private lateinit var timer: TextView
    private lateinit var side: LinearLayout
    private lateinit var sidePrimary: Button
    private lateinit var sideSecondary: Button
    private lateinit var sidePrimaryLabel: TextView
    private lateinit var sideSecondaryLabel: TextView
    private lateinit var bottom: LinearLayout
    private lateinit var bottomPrimary: Button
    private lateinit var bottomSecondary: Button
    private lateinit var message: TextView

    private var camera: CameraDevice? = null
    private var session: CameraCaptureSession? = null
    private var recorder: MediaRecorder? = null
    private var recording = false
    private var reviewing = false
    private var clip: File? = null
    private var videoSize = Size(1920, 1080)
    private var sensorOrientation = 270
    private var startedAt = 0L
    private var pulse: ObjectAnimator? = null
    private val tick = object : Runnable {
        override fun run() {
            if (!recording) return
            timer.text = clock(SystemClock.elapsedRealtime() - startedAt)
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
        preview = findViewById(R.id.preview)
        playback = findViewById(R.id.playback)
        frame = findViewById(R.id.frame)
        recBadge = findViewById(R.id.recBadge)
        recDot = findViewById(R.id.recDot)
        timer = findViewById(R.id.timer)
        side = findViewById(R.id.side)
        sidePrimary = findViewById(R.id.sidePrimary)
        sideSecondary = findViewById(R.id.sideSecondary)
        sidePrimaryLabel = findViewById(R.id.sidePrimaryLabel)
        sideSecondaryLabel = findViewById(R.id.sideSecondaryLabel)
        bottom = findViewById(R.id.bottom)
        bottomPrimary = findViewById(R.id.bottomPrimary)
        bottomSecondary = findViewById(R.id.bottomSecondary)
        message = findViewById(R.id.message)

        sidePrimary.setOnClickListener { onPrimary() }
        bottomPrimary.setOnClickListener { onPrimary() }
        sideSecondary.setOnClickListener { onSecondary() }
        bottomSecondary.setOnClickListener { onSecondary() }
        preview.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                openCamera()
            }
            override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                mirrorPreview()
            }
            override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture) = true
            override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
        }
        showCameraChrome()
    }

    override fun onResume() {
        super.onResume()
        startThread()
        if (hasPermissions() && preview.isAvailable && !reviewing) openCamera()
        else if (!hasPermissions()) requestPermissions()
    }

    override fun onPause() {
        if (recording) stopRecording(showReview = true)
        closeCamera()
        stopThread()
        super.onPause()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        showChrome()
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
            if (preview.isAvailable) openCamera()
        } else {
            say("Allow the camera and microphone, then open Auto Cam again.")
        }
    }

    private fun onPrimary() {
        if (reviewing) saveClip()
        else if (!recording) startRecording()
    }

    private fun onSecondary() {
        if (reviewing) deleteClip()
        else if (recording) stopRecording(showReview = true)
    }

    private fun showChrome() {
        if (reviewing) showReviewChrome() else showCameraChrome()
    }

    private fun showCameraChrome() {
        val land = landscape()
        side.visibility = if (land) View.VISIBLE else View.GONE
        bottom.visibility = if (land) View.GONE else View.VISIBLE
        sidePrimary.text = "●"
        sidePrimary.setTextColor(ContextCompat.getColor(this, R.color.rec))
        sidePrimaryLabel.text = "Start"
        sideSecondary.text = "■"
        sideSecondaryLabel.text = "End"
        sideSecondary.isEnabled = recording
        bottomPrimary.text = if (recording) "■" else "●"
        bottomPrimary.setTextColor(ContextCompat.getColor(this, if (recording) R.color.cream else R.color.rec))
        bottomSecondary.visibility = View.GONE
        playback.visibility = View.GONE
        preview.visibility = View.VISIBLE
        frame.background = if (recording) ContextCompat.getDrawable(this, R.drawable.stroke_red) else null
        recBadge.visibility = if (recording) View.VISIBLE else View.GONE
    }

    private fun showReviewChrome() {
        val land = landscape()
        side.visibility = if (land) View.VISIBLE else View.GONE
        bottom.visibility = if (land) View.GONE else View.VISIBLE
        sidePrimary.text = "↓"
        sidePrimary.setTextColor(ContextCompat.getColor(this, R.color.cream))
        sidePrimaryLabel.text = "Save"
        sideSecondary.text = "✕"
        sideSecondaryLabel.text = "Delete"
        sideSecondary.isEnabled = true
        bottomPrimary.text = "Save"
        bottomPrimary.setTextColor(ContextCompat.getColor(this, R.color.cream))
        bottomSecondary.visibility = View.VISIBLE
        bottomSecondary.text = "Delete"
        preview.visibility = View.GONE
        playback.visibility = View.VISIBLE
        frame.background = ContextCompat.getDrawable(this, R.drawable.stroke_green)
        recBadge.visibility = View.GONE
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
        if (!hasPermissions() || reviewing || camera != null) return
        val manager = getSystemService(CAMERA_SERVICE) as CameraManager
        val id = frontCamera(manager) ?: run {
            say("No front camera on this phone.")
            return
        }
        val chars = manager.getCameraCharacteristics(id)
        sensorOrientation = chars.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 270
        videoSize = chooseSize(chars)
        message.visibility = View.GONE
        try {
            manager.openCamera(id, object : CameraDevice.StateCallback() {
                override fun onOpened(device: CameraDevice) {
                    camera = device
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

    private fun frontCamera(manager: CameraManager): String? {
        val ids = manager.cameraIdList
        return ids.firstOrNull {
            manager.getCameraCharacteristics(it).get(CameraCharacteristics.LENS_FACING) ==
                CameraCharacteristics.LENS_FACING_FRONT
        } ?: ids.firstOrNull()
    }

    private fun chooseSize(chars: CameraCharacteristics): Size {
        val map = chars.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
        val sizes = map?.getOutputSizes(MediaRecorder::class.java)?.toList().orEmpty()
        if (sizes.isEmpty()) return Size(1280, 720)
        return sizes
            .filter { it.width <= 1920 && it.height <= 1080 }
            .minByOrNull { abs(it.width.toFloat() / it.height - 16f / 9f) + (1920 - it.width) / 1920f }
            ?: sizes.maxBy { it.width * it.height }
    }

    private fun startPreview() {
        val device = camera ?: return
        val texture = preview.surfaceTexture ?: return
        texture.setDefaultBufferSize(videoSize.width, videoSize.height)
        mirrorPreview()
        val surface = Surface(texture)
        try {
            device.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                override fun onConfigured(capture: CameraCaptureSession) {
                    session = capture
                    val request = device.createCaptureRequest(CameraDevice.TEMPLATE_RECORD).apply {
                        addTarget(surface)
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    }
                    capture.setRepeatingRequest(request.build(), null, handler)
                }
                override fun onConfigureFailed(capture: CameraCaptureSession) {
                    runOnUiThread { say("The preview didn’t start.") }
                }
            }, handler)
        } catch (_: Exception) {
            runOnUiThread { say("The preview didn’t start.") }
        }
    }

    private fun mirrorPreview() {
        val viewWidth = preview.width.toFloat()
        val viewHeight = preview.height.toFloat()
        if (viewWidth == 0f || viewHeight == 0f) return
        val matrix = Matrix()
        matrix.setScale(-1f, 1f, viewWidth / 2f, viewHeight / 2f)
        preview.setTransform(matrix)
    }

    private fun startRecording() {
        val device = camera ?: return
        if (recording) return
        closeSession()
        val file = File(cacheDir, "autocam-${System.currentTimeMillis()}.mp4")
        val made = prepareRecorder(file, stereo = true) || prepareRecorder(file, stereo = false)
        if (!made) {
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
                        set(CaptureRequest.CONTROL_MODE, CaptureRequest.CONTROL_MODE_AUTO)
                        set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_CONTINUOUS_VIDEO)
                    }
                    capture.setRepeatingRequest(request.build(), null, handler)
                    rec.start()
                    runOnUiThread {
                        clip = file
                        recording = true
                        startedAt = SystemClock.elapsedRealtime()
                        timer.text = "0:00"
                        pulse = ObjectAnimator.ofFloat(recDot, View.ALPHA, 1f, 0.25f).apply {
                            duration = 700
                            repeatMode = ValueAnimator.REVERSE
                            repeatCount = ValueAnimator.INFINITE
                            start()
                        }
                        timer.post(tick)
                        showCameraChrome()
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

    private fun orientationHint(): Int {
        val rotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            display?.rotation ?: Surface.ROTATION_0
        } else {
            @Suppress("DEPRECATION")
            windowManager.defaultDisplay.rotation
        }
        val degrees = when (rotation) {
            Surface.ROTATION_90 -> 90
            Surface.ROTATION_180 -> 180
            Surface.ROTATION_270 -> 270
            else -> 0
        }
        var hint = (sensorOrientation + degrees) % 360
        hint = (360 - hint) % 360
        return hint
    }

    private fun stopRecording(showReview: Boolean) {
        if (!recording) return
        recording = false
        timer.removeCallbacks(tick)
        pulse?.cancel()
        try {
            recorder?.stop()
        } catch (_: Exception) {
            clip?.delete()
            clip = null
        }
        releaseRecorder()
        closeSession()
        if (showReview && clip != null && clip!!.length() > 0) {
            reviewing = true
            playback.setVideoPath(clip!!.absolutePath)
            playback.setOnPreparedListener { it.isLooping = true; playback.start() }
            showReviewChrome()
        } else {
            reviewing = false
            showCameraChrome()
            startPreview()
        }
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
        val uri = contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, values)
        if (uri == null) {
            say("Couldn’t save to the gallery.")
            return
        }
        contentResolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } }
        values.clear()
        values.put(MediaStore.Video.Media.IS_PENDING, 0)
        contentResolver.update(uri, values, null, null)
        file.delete()
        clip = null
        finishReview()
    }

    private fun deleteClip() {
        playback.stopPlayback()
        clip?.delete()
        clip = null
        finishReview()
    }

    private fun finishReview() {
        reviewing = false
        playback.visibility = View.GONE
        preview.visibility = View.VISIBLE
        showCameraChrome()
        if (camera != null) startPreview() else openCamera()
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
