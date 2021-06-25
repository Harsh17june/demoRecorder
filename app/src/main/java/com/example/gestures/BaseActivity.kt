package com.example.gestures

import android.app.AlertDialog
import android.content.Intent
import android.view.GestureDetector
import android.view.MotionEvent
import androidx.core.view.GestureDetectorCompat
import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.DisplayMetrics
import android.util.Log
import android.util.SparseIntArray
import android.view.Surface
import android.view.View
import android.widget.Toast
import android.widget.ToggleButton
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.gestures.ForegroundService
import com.google.android.material.snackbar.Snackbar
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
open class BaseActivity : AppCompatActivity(), GestureDetector.OnGestureListener, GestureDetector.OnDoubleTapListener
    {
    private var mScreenDensity = 0
    private var mProjectionManager: MediaProjectionManager? = null
    private var mMediaProjection: MediaProjection? = null
    private var mVirtualDisplay: VirtualDisplay? = null
    private var mMediaProjectionCallback: MediaProjectionCallback? = null
    private var mToggleButton: ToggleButton? = null
    private var mMediaRecorder: MediaRecorder? = null
    private var serviceIntent: Intent? = null
    private lateinit var mDetector: GestureDetectorCompat

    companion object {
        private const val TAG = "MainActivity"
        private const val REQUEST_CODE = 1000
        private const val DISPLAY_WIDTH = 720
        private const val DISPLAY_HEIGHT = 1280
        private val ORIENTATIONS = SparseIntArray()
        private const val REQUEST_PERMISSIONS = 10
        var toggle : Boolean = false

        init {
            ORIENTATIONS.append(Surface.ROTATION_0, 90)
            ORIENTATIONS.append(Surface.ROTATION_90, 0)
            ORIENTATIONS.append(Surface.ROTATION_180, 270)
            ORIENTATIONS.append(Surface.ROTATION_270, 180)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_base)
        serviceIntent = Intent(this, ForegroundService::class.java)
        serviceIntent!!.putExtra("inputExtra", "Foreground Service Example in Android")
        ContextCompat.startForegroundService(this, serviceIntent!!)
        val metrics = DisplayMetrics()
        windowManager.defaultDisplay.getMetrics(metrics)
        mScreenDensity = metrics.densityDpi
        mMediaRecorder = MediaRecorder()
        mProjectionManager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
//        mToggleButton = findViewById<View>(R.id.toggle) as ToggleButton
//        mToggleButton!!.setOnClickListener { v ->
//        }
        mDetector = GestureDetectorCompat(this, this)
        mDetector.setOnDoubleTapListener(this)
    }

    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_CODE) {
            Log.e(TAG, "Unknown request code: $requestCode")
            return
        }
        if (resultCode != RESULT_OK) {
            Toast.makeText(
                this,
                "Screen Cast Permission Denied", Toast.LENGTH_SHORT
            ).show()
            toggle= false// now record start again possible
            return
        }
        mMediaProjectionCallback = MediaProjectionCallback()
        mMediaProjection = mProjectionManager!!.getMediaProjection(resultCode, data!!)
        var mmedia= mMediaProjection;
        mmedia?.registerCallback(mMediaProjectionCallback, null)
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder!!.start()
    }

    fun onToggleScreenShare() {
        initRecorder()
        shareScreen()

    }

    private fun shareScreen() {
        if (mMediaProjection == null) {
            startActivityForResult(mProjectionManager!!.createScreenCaptureIntent(), REQUEST_CODE)
            return
        }
        mVirtualDisplay = createVirtualDisplay()
        mMediaRecorder!!.start()
    }

    private fun createVirtualDisplay(): VirtualDisplay {
        return mMediaProjection!!.createVirtualDisplay(
            "MainActivity",
            DISPLAY_WIDTH, DISPLAY_HEIGHT, mScreenDensity,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            mMediaRecorder!!.surface, null /*Callbacks*/, null /*Handler*/
        )
    }

    private fun initRecorder() {
        try {
            mMediaRecorder!!.reset()
            val date = Date()
            val dateFormat = SimpleDateFormat("YYYY-MM-dd-hh-mm-ss-Ms")
            dateFormat.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
            val fileName = "/" + dateFormat.format(date) + ".mp4"
            Log.d("DEBUG", fileName)
            mMediaRecorder!!.setAudioSource(MediaRecorder.AudioSource.MIC)
            mMediaRecorder!!.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mMediaRecorder!!.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP)
            mMediaRecorder!!.setOutputFile(
                Environment
                    .getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                    .toString() + fileName
            )
            mMediaRecorder!!.setVideoSize(DISPLAY_WIDTH, DISPLAY_HEIGHT)
            mMediaRecorder!!.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mMediaRecorder!!.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mMediaRecorder!!.setVideoEncodingBitRate(512 * 1000)
            mMediaRecorder!!.setVideoFrameRate(30)
            val rotation = windowManager.defaultDisplay.rotation
            val orientation = ORIENTATIONS[rotation + 90]
            mMediaRecorder!!.setOrientationHint(orientation)
            mMediaRecorder!!.prepare()
        } catch (e: IOException) {
            e.printStackTrace()
        }
    }

    private inner class MediaProjectionCallback : MediaProjection.Callback() {
        override fun onStop() {
            if (toggle) {
                toggle= false
                mMediaRecorder!!.stop()
                mMediaRecorder!!.reset()
                Log.v(TAG, "Recording Stopped")
            }
            mMediaProjection = null
            stopScreenSharing()
        }
    }

    private fun stopScreenSharing() {
        if (mVirtualDisplay == null) {
            return
        }
        stopService(serviceIntent)
        mVirtualDisplay!!.release()
        destroyMediaProjection()
    }

    public override fun onDestroy() {
        super.onDestroy()
        destroyMediaProjection()
    }

    private fun destroyMediaProjection() {
        if (mMediaProjection != null) {
            mMediaProjection!!.unregisterCallback(mMediaProjectionCallback)
            mMediaProjection!!.stop()
            mMediaProjection = null
        }
        Log.i(TAG, "MediaProjection Stopped")
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            REQUEST_PERMISSIONS -> {
                if (grantResults.size > 0 && grantResults[0] +
                    grantResults[1] == PackageManager.PERMISSION_GRANTED
                ) {
                    onToggleScreenShare()
                } else {
                    toggle = false
                    Snackbar.make(
                        findViewById(android.R.id.content), R.string.label_permissions,
                        Snackbar.LENGTH_INDEFINITE
                    ).setAction(
                        "ENABLE"
                    ) {
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.addCategory(Intent.CATEGORY_DEFAULT)
                        intent.data = Uri.parse("package:$packageName")
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)
                        intent.addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                        startActivity(intent)
                    }.show()
                }
                return
            }
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        return if (mDetector.onTouchEvent(event)) {
            true
        } else {
            super.onTouchEvent(event)
        }
    }

    override fun onDown(event: MotionEvent): Boolean {
        Log.d("DEBUG_TAG", "onDown: $event")
        return true
    }

    override fun onFling(
        event1: MotionEvent,
        event2: MotionEvent,
        velocityX: Float,
        velocityY: Float
    ): Boolean {
        Log.d("DEBUG_TAG", "onFling: $event1 $event2")
        return true
    }

    override fun onLongPress(event: MotionEvent) {
        Log.d("DEBUG_TAG", "onLongPress: $event")

//        val builder = AlertDialog.Builder(this)
//        //set title for alert dialog
//        builder.setTitle("Select the option")
//        //set message for alert dialog
//        builder.setMessage("ss/video")
//        builder.setIcon(android.R.drawable.ic_dialog_alert)
//
//        //performing positive action
//        builder.setPositiveButton("Screenshot"){dialogInterface, which ->
//            Toast.makeText(applicationContext,"Taking screenshot", Toast.LENGTH_LONG).show()
//        }
//        //performing cancel action
//        builder.setNeutralButton("Cancel"){dialogInterface , which ->
//            Toast.makeText(applicationContext,"clicked cancel\n operation cancel",Toast.LENGTH_LONG).show()
//        }
        //performing negative action

        if (toggle == false) {
//            builder.setNegativeButton("Start Video") { dialogInterface, which ->
//                Toast.makeText(applicationContext, "Video recording started", Toast.LENGTH_LONG).show() //write you recording action here
//                //state saving handled by making toggle static

                val saveContext: Context = this
                serviceIntent!!.putExtra("inputExtra", "Screen Recording in Progress")
                ContextCompat.startForegroundService(saveContext, serviceIntent!!)
                if (ContextCompat.checkSelfPermission(
                        this@BaseActivity,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    ) + ContextCompat
                        .checkSelfPermission(
                            this,
                            Manifest.permission.RECORD_AUDIO
                        )
                    != PackageManager.PERMISSION_GRANTED
                ) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(
                            this@BaseActivity,
                            Manifest.permission.WRITE_EXTERNAL_STORAGE
                        ) ||
                        ActivityCompat.shouldShowRequestPermissionRationale(
                            this@BaseActivity,
                            Manifest.permission.RECORD_AUDIO
                        )
                    ) {
                        toggle = false
                        Snackbar.make(
                            findViewById(android.R.id.content), R.string.label_permissions,
                            Snackbar.LENGTH_INDEFINITE
                        ).setAction(
                            "ENABLE"
                        ) {
                            ActivityCompat.requestPermissions(
                                this@BaseActivity,
                                arrayOf(
                                    Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                    Manifest.permission.RECORD_AUDIO
                                ),
                                REQUEST_PERMISSIONS
                            )
                        }.show()
                    } else {
                        ActivityCompat.requestPermissions(
                            this@BaseActivity,
                            arrayOf(
                                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                                Manifest.permission.RECORD_AUDIO
                            ),
                            REQUEST_PERMISSIONS
                        )
                    }
                } else {
                    onToggleScreenShare()
                }

                toggle = true
            }
//        }
        else {
//            builder.setNegativeButton("Stop Video") { dialogInterface, which ->

                stopService(serviceIntent)
                mMediaRecorder!!.stop()
                mMediaRecorder!!.reset()
                Log.v(TAG, "Stopping Recording")
                stopScreenSharing()

                Toast.makeText(applicationContext, "Video recording stopped", Toast.LENGTH_LONG).show()
                toggle = false
//            }
        }
    }

    override fun onScroll(
        event1: MotionEvent,
        event2: MotionEvent,
        distanceX: Float,
        distanceY: Float
    ): Boolean {
        Log.d("DEBUG_TAG", "onScroll: $event1 $event2")
        return true
    }

    override fun onShowPress(event: MotionEvent) {
        Log.d("DEBUG_TAG", "onShowPress: $event")
    }

    override fun onSingleTapUp(event: MotionEvent): Boolean {
        Log.d("DEBUG_TAG", "onSingleTapUp: $event")
        return true
    }

    override fun onDoubleTap(event: MotionEvent): Boolean {
        Log.d("DEBUG_TAG", "onDoubleTap: $event")
        //return true
        var dialog=FormFragment()
        dialog.show(supportFragmentManager,"formFragment")
        return true
    }

    override fun onDoubleTapEvent(event: MotionEvent): Boolean {
        Log.d("DEBUG_TAG", "onDoubleTapEvent: $event")
        return true
    }

    override fun onSingleTapConfirmed(event: MotionEvent): Boolean {
        Log.d("DEBUG_TAG", "onSingleTapConfirmed: $event")
        return true
    }



}
