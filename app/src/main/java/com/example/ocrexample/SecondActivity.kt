package com.example.ocrexample

import android.Manifest
import android.app.AlertDialog
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
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
import com.example.ocrexample.databinding.ActivitySecondBinding
import com.google.common.util.concurrent.ListenableFuture
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.orhanobut.hawk.Hawk
import java.io.File
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class SecondActivity : ComponentActivity() {
    private var partNumber: String? = null
    private lateinit var cameraProviderFuture: ListenableFuture<ProcessCameraProvider>
    private lateinit var progressDialog: AlertDialog
    private lateinit var cameraExecutor: ExecutorService
    private val cameraPermission = Manifest.permission.CAMERA
    private val requestCodeCameraPermission = 1001
    private lateinit var imageCapture: ImageCapture
    var counter = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(ActivitySecondBinding.inflate(layoutInflater).root)

        cameraExecutor = Executors.newSingleThreadExecutor()

        counter = Hawk.get("counter", 0)

        requestCameraPermission()
        findViewById<Button>(R.id.btn_check).setOnClickListener {
            captureImage()
        }

        partNumber = intent.getStringExtra("PART_NUMBER") // Default value: 0
        findViewById<TextView>(R.id.tv_part_number).text = "Part Number: $partNumber"
    }

    override fun onResume() {
        super.onResume()
        cameraExecutor = Executors.newSingleThreadExecutor()
        requestCameraPermission()
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
        cameraProviderFuture = ProcessCameraProvider.getInstance(this)
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
        showProgressDialog()
        val photoFile = File(externalMediaDirs.first(), "${System.currentTimeMillis()}.jpg")
        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    val imageUri = Uri.fromFile(photoFile)
                    stopCamera()
                    processImageForOCR(imageUri)
                }

                override fun onError(exception: ImageCaptureException) {
                    Log.e("CameraX", "Image capture failed: ${exception.message}", exception)
                }
            })
    }

    private fun stopCamera() {
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll() // Stops the camera preview
        }, ContextCompat.getMainExecutor(this))
    }

    private fun processImageForOCR(imageUri: Uri) {
        val image = InputImage.fromFilePath(this, imageUri)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        dismissProgressDialog()
        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                val recognizedText = extractPartNumbers(visionText.text)
                if (recognizedText.isNotEmpty()) {
                    showCustomDialog(recognizedText[0])
                } else {
                    Toast.makeText(this, "No Part Number Found", Toast.LENGTH_SHORT).show()
                    startCamera()
                }
            }
            .addOnFailureListener { e ->
                Log.e("OCR", "Text recognition failed", e)
            }
    }

    private fun extractPartNumbers(text: String): List<String> {
        // Use regex to find part numbers in the format "B2X-E1631-00"
        val regex = Regex("[A-Z0-9]{3}-[A-Z0-9]{5}-[A-Z0-9]{2}")
        return regex.findAll(text).map { it.value }.toList()
    }

    private fun showProgressDialog(): AlertDialog {
        progressDialog = AlertDialog.Builder(this)
            .setView(R.layout.dialog_progress) // Custom Layout
            .setCancelable(false)
            .create()

        progressDialog.show()
        return progressDialog
    }

    private fun dismissProgressDialog() {
        progressDialog.dismiss()
    }

    private fun showCustomDialog(text: String) {
        val dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_custom, null)
        val dialog = AlertDialog.Builder(this)
            .setView(dialogView)
            .setCancelable(false)
            .create()

        val textView = dialogView.findViewById<TextView>(R.id.tv_part_number)
        val btnNext = dialogView.findViewById<Button>(R.id.btn_next)

        textView.text = if (text == partNumber) "Part Number Matches" else "Part Number Not Matches"

        btnNext.setOnClickListener {
            dialog.dismiss()
            Hawk.put("counter", (counter + 1))
            finish()
        }

        dialog.show()
    }

    override fun onDestroy() {
        super.onDestroy()
        stopCamera()
    }
}