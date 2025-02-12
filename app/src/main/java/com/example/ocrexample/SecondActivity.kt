package com.example.ocrexample

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.ocrexample.databinding.ActivityMainBinding
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SecondActivity : ComponentActivity() {

    private lateinit var cameraExecutor: ExecutorService
    private val cameraPermission = Manifest.permission.CAMERA
    private val requestCodeCameraPermission = 1001
    private lateinit var imageCapture: ImageCapture

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivityMainBinding.inflate(layoutInflater).root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        requestCameraPermission()
        findViewById<Button>(R.id.captureButton).setOnClickListener {
            captureImage()
        }
    }

    private fun requestCameraPermission() {
        if (ContextCompat.checkSelfPermission(
                this,
                cameraPermission
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(cameraPermission),
                requestCodeCameraPermission
            )
        } else startCamera()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == requestCodeCameraPermission && grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            startCamera()
        } else {
            Toast.makeText(this, "Camera permission required", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val viewFinder: PreviewView = findViewById(R.id.viewFinder)

            val preview = Preview.Builder().build().also {
                it.surfaceProvider = viewFinder.surfaceProvider // `viewFinder` is a `PreviewView`
            }

            imageCapture = ImageCapture.Builder().build()

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (exc: Exception) {
                Log.e("CameraX", "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun captureImage() {
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val imageUri = Uri.fromFile(photoFile)
                    processImageForOCR(imageUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Image capture failed: ${exception.message}", exception)
                }
            })
    }

    private fun processImageForOCR(imageUri: Uri) {
        val image = InputImage.fromFilePath(this, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = visionText.text
                Log.d("OCR", "Detected text: $recognizedText")
                Toast.makeText(this, recognizedText, Toast.LENGTH_LONG).show()
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Text recognition failed", e)
            }
    }

    private fun extractPartNumbers(text: String): List<String> {
        // Use regex to find part numbers in the format "B2X-E1631-00-00-80"
        val regex = Regex("[A-Z0-9]{3}-[A-Z0-9]{5}-[A-Z0-9]{2}")
        return regex.findAll(text).map { it.value }.toList()
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}