package com.textrecognitionocrapp.app

import android.content.ClipboardManager
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.method.ScrollingMovementMethod
import android.widget.Button
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.textrecognitionocrapp.app.databinding.ActivityMainBinding
import java.io.File
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding

    private var currentPhotoPath: String? = null
    private lateinit var requestPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var takePictureLauncher: ActivityResultLauncher<Uri>
    private lateinit var selectImageLauncher: ActivityResultLauncher<Intent>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Inflate the layout using view binding
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // Request permission to use the camera
        requestPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted ->
            if (isGranted) {
                captureImage()
            } else {
                Toast.makeText(this, "Camera Permission Denied", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle capturing an image
        takePictureLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess) {
                currentPhotoPath?.let { imagePath ->
                    val bitmap = BitmapFactory.decodeFile(imagePath)
                    viewBinding.ivCameraImage.setImageBitmap(bitmap)
                    recogniseText(bitmap)
                }
            } else {
                Toast.makeText(this, "Error Occurred while capturing the Image", Toast.LENGTH_SHORT).show()
            }
        }

        // Handle selecting an image from the gallery
        selectImageLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                result.data?.data?.let { uri ->
                    val bitmap = MediaStore.Images.Media.getBitmap(contentResolver, uri)
                    viewBinding.ivCameraImage.setImageBitmap(bitmap)
                    recogniseText(bitmap)
                }
            } else {
                Toast.makeText(this, "Image selection cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        // Button listeners
        viewBinding.btnCaptureImage.setOnClickListener {
            requestPermissionLauncher.launch(android.Manifest.permission.CAMERA)
        }

        viewBinding.btnSelectImage.setOnClickListener {
            selectImageFromGallery()
        }
    }

    private fun createImageFile(): File {
        val timeStamp: String = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val storageDir: File? = getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile(
            "JPEG_${timeStamp}_",
            ".jpg",
            storageDir
        ).apply {
            currentPhotoPath = absolutePath
        }
    }

    private fun captureImage() {
        val photoFile: File? = try {
            createImageFile()
        } catch (ex: IOException) {
            Toast.makeText(this, "Error Occurred while creating the File", Toast.LENGTH_SHORT).show()
            null
        }

        photoFile?.also {
            val photoURI: Uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", it)
            takePictureLauncher.launch(photoURI)
        }
    }

    private fun selectImageFromGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        selectImageLauncher.launch(intent)
    }

    private fun recogniseText(bitmap: Bitmap) {
        val image = InputImage.fromBitmap(bitmap, 0)
        val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        recognizer.process(image)
            .addOnSuccessListener { visionText ->
                viewBinding.tvResultText.text = visionText.text
                viewBinding.tvResultText.movementMethod = ScrollingMovementMethod()
                viewBinding.btnCopyResultText.visibility = Button.VISIBLE
                viewBinding.btnCopyResultText.setOnClickListener {
                    val clipboard = ContextCompat.getSystemService(this, ClipboardManager::class.java)
                    val clip = android.content.ClipData.newPlainText("Copied Text", visionText.text)
                    clipboard?.setPrimaryClip(clip)
                    Toast.makeText(this, "Text Copied to Clipboard", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, "Failed to recognize text: ${e.message}", Toast.LENGTH_SHORT).show()
            }
    }
}
