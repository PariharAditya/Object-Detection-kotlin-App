@file:Suppress("DEPRECATION")

package com.example.myapplication

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.view.Surface
import android.view.TextureView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import com.example.myapplication.databinding.ActivityMainBinding
import com.example.myapplication.ml.SsdMobilenetV11Metadata1
import org.tensorflow.lite.support.common.FileUtil
import org.tensorflow.lite.support.image.ImageProcessor
import org.tensorflow.lite.support.image.TensorImage
import org.tensorflow.lite.support.image.ops.ResizeOp

class MainActivity : AppCompatActivity()
{
    private lateinit var binding: ActivityMainBinding
    private lateinit var cameraManager: CameraManager
    private lateinit var handler: Handler
    private lateinit var cameraDevice: CameraDevice
    private lateinit var bitmap: Bitmap

    private lateinit var model : SsdMobilenetV11Metadata1
    private lateinit var imageProcessor:ImageProcessor

    lateinit var labels:List<String>
    var colors = listOf(
            Color.BLUE, Color.GREEN, Color.RED, Color.CYAN, Color.GRAY, Color.BLACK,
            Color.DKGRAY, Color.MAGENTA, Color.YELLOW, Color.RED)
    val paint = Paint()


    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        getPermission()

        labels = FileUtil.loadLabels(this, "labels.txt")
        imageProcessor = ImageProcessor.Builder()
            .add(ResizeOp(300, 300, ResizeOp.ResizeMethod.BILINEAR))
            .build()
        model = SsdMobilenetV11Metadata1.newInstance(this)

        val handlerThread = HandlerThread("videoThread")
        handlerThread.start()
        handler = Handler(handlerThread.looper)

        binding.textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener
        {
            override fun onSurfaceTextureAvailable(p0: SurfaceTexture, p1: Int, p2: Int)
            {
                cameraOpen()
            }

            override fun onSurfaceTextureSizeChanged(p0: SurfaceTexture, p1: Int, p2: Int)
            {
                //To change body of created functions use File | Settings | File Templates.
            }

            override fun onSurfaceTextureDestroyed(p0: SurfaceTexture): Boolean
            {
                return false
            }

            override fun onSurfaceTextureUpdated(p0: SurfaceTexture)
            {
              bitmap = binding.textureView.bitmap!!
               // Creates inputs for reference.
               val image = TensorImage.fromBitmap(bitmap)
               imageProcessor.process(image)

               // Runs model inference and gets result.
               val outputs = model.process(image)
               val locations = outputs.locationsAsTensorBuffer.floatArray
               val classes = outputs.classesAsTensorBuffer.floatArray
               val scores = outputs.scoresAsTensorBuffer.floatArray
               val numberOfDetections = outputs.numberOfDetectionsAsTensorBuffer.floatArray

                val mutable = bitmap.copy(Bitmap.Config.ARGB_8888, true)
                val canvas = Canvas(mutable)

                val h = mutable.height
                val w = mutable.width
                paint.textSize= h/20f
                paint.strokeWidth= h/100f

                var x: Int
                scores.forEachIndexed { index, fl ->
                    x = index
                    x *= 4
                    if(fl > 0.5){
                        paint.color = colors[index]
                        paint.style = Paint.Style.STROKE
                        canvas.drawRect(RectF(locations[x + 1] *w, locations[x] *h, locations[x + 3] *w, locations[x+2] *h), paint)
                        paint.style = Paint.Style.FILL
                        canvas.drawText(labels[classes[index].toInt()] +" "+fl.toString(), locations[x+1] *w, locations[x] *h, paint)
                    }
                }
                binding.imageView.setImageBitmap(mutable)
            }
        }
        cameraManager = getSystemService(Context.CAMERA_SERVICE) as CameraManager
    }

    override fun onDestroy()
    {
        super.onDestroy()
        // Releases model resources if no longer used.
        model.close()
    }


    @SuppressLint("MissingPermission")
    private fun cameraOpen()
    {
        cameraManager.openCamera("0", object : CameraDevice.StateCallback() {
            override fun onOpened(camera: CameraDevice) {
                // Camera opened successfully, you can perform camera operations here
                cameraDevice = camera
                val texture = binding.textureView.surfaceTexture
                val surface = Surface(texture)

                val captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW)
                captureRequestBuilder.addTarget(surface)

                cameraDevice.createCaptureSession(listOf(surface), object : CameraCaptureSession.StateCallback() {
                    override fun onConfigured(session: CameraCaptureSession) {
                        session.setRepeatingRequest(captureRequestBuilder.build(), null, null)
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        // Configuration failed, handle this case
                    }
                }, handler)
            }

            override fun onDisconnected(camera: CameraDevice) {
                // Camera disconnected, handle this case if needed
            }

            override fun onError(camera: CameraDevice, error: Int) {
                // Error occurred while opening the camera, handle this case if needed
            }
        }, handler)

    }

    private fun getPermission()
    {
        if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.CAMERA
            ) != android.content.pm.PackageManager.PERMISSION_GRANTED
        )
        {
            requestPermissions(arrayOf(Manifest.permission.CAMERA), 101)

        }
}
}

