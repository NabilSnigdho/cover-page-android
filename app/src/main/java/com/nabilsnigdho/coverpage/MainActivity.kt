package com.nabilsnigdho.coverpage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Base64
import android.util.Log
import android.webkit.WebView
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.viewinterop.AndroidView
import com.nabilsnigdho.coverpage.ui.theme.CoverPageTheme
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Alignment
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.FileInputStream
import java.io.IOException

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            CoverPageTheme {
                WebViewScreen("https://www.nabilsnigdho.dev/cover-page")
            }
        }
    }

    // Handle permission result
    @Deprecated("Deprecated in Java")
    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == STORAGE_PERMISSION_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, proceed with saving
                // Assuming you have the file reference here, call the save function (you may need to store the file)
            } else {
                // Permission denied, notify the user
                Toast.makeText(this, "Permission denied, cannot save file", Toast.LENGTH_SHORT).show()
            }
        }
    }
}

@Composable
fun WebViewScreen(url: String) {
    var pdfFile by remember { mutableStateOf<File?>(null) }
    var showBottomSheet by remember { mutableStateOf(false) }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            WebView(context).apply {
                webViewClient  = WebViewClient()
                settings.javaScriptEnabled = true // Enable JavaScript if needed
                settings.domStorageEnabled = true
                settings.userAgentString = "Android APP"
                loadUrl(url)

                // Set Download Listener
                setDownloadListener { downloadUrl, _, _, mimeType, _ ->
                    // Check if it's a PDF file (or adjust for other file types)
                    Log.d("WebViewDownload", "Download URL: $downloadUrl $mimeType")
                    if (mimeType == "application/pdf" || downloadUrl.startsWith("blob:")) {
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // Download the file and save it to a temporary location
                                pdfFile = downloadTempFile(downloadUrl, context.cacheDir)

                                showBottomSheet = true
                            } catch (e: Exception) {
                                e.printStackTrace()
                            }
                        }
                    }
                }
            }
        }
    )

    if (showBottomSheet && pdfFile != null) {
        FileOptionsBottomSheet(
            pdfFile = pdfFile!!,
            onDismiss = { showBottomSheet = false }
        )
    }
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CoverPageTheme {
        WebViewScreen("http://localhost:3000")
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FileOptionsBottomSheet(pdfFile: File?, onDismiss: () -> Unit) {
    val context = LocalContext.current

    ModalBottomSheet(onDismissRequest = { onDismiss() }) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(text = "File Options", style = MaterialTheme.typography.headlineMedium)

            Spacer(modifier = Modifier.height(16.dp))

            Button(
                onClick = {
                    pdfFile?.let { checkAndSaveFile(context, it, it.name) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Save to Disk")
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    pdfFile?.let { shareFileWithFileProvider(context, it) }
                    onDismiss()
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(text = "Share")
            }
        }
    }
}

private suspend fun downloadTempFile(urlString: String, cacheDir: File): File {
    val (prefix, base64Data) = urlString.split(",")
    val fileName = prefix.split("name=").getOrNull(1) ?: "cover-page.pdf"

    // Decode the base64 data
    val fileData = Base64.decode(base64Data, Base64.DEFAULT)

    // Save the file to the cache directory
    val file = File(cacheDir, fileName)
    FileOutputStream(file).use {
        it.write(fileData)
    }
    return file
}

private fun shareFileWithFileProvider(context: Context, file: File) {
    // Use FileProvider to get the URI
    val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    val shareIntent = Intent().apply {
        action = Intent.ACTION_SEND
        type = "application/pdf"
        putExtra(Intent.EXTRA_STREAM, uri)
        addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION) // Grant permission to read the URI
    }

    // Start the share intent
    context.startActivity(Intent.createChooser(shareIntent, "Share PDF"))
}

private val STORAGE_PERMISSION_CODE = 1001

// Check and Request Permission Based on Android Version
fun checkAndSaveFile(context: Context, sourceFile: File, destinationFileName: String) {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        // For Android 10 and above, use Scoped Storage (no permission needed)
        saveFileToDownloadsScopedStorage(context, sourceFile, destinationFileName)
    } else {
        // For Android 9 and below, request permission if not granted
        if (ContextCompat.checkSelfPermission(
                context, Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(
                context as Activity,
                arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE),
                STORAGE_PERMISSION_CODE
            )
        } else {
            // Permission already granted, proceed with saving
            saveFileToDownloadsLegacy(context, sourceFile, destinationFileName)
        }
    }
}

fun saveFileToDownloadsScopedStorage(context: Context, sourceFile: File, destinationFileName: String) {
    val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val destinationFile = File(downloadsFolder, destinationFileName)

    try {
        FileInputStream(sourceFile).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
        Toast.makeText(context, "File saved to Downloads", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving file", Toast.LENGTH_SHORT).show()
    }
}

fun saveFileToDownloadsLegacy(context: Context, sourceFile: File, destinationFileName: String) {
    val downloadsFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
    val destinationFile = File(downloadsFolder, destinationFileName)

    try {
        FileInputStream(sourceFile).use { inputStream ->
            FileOutputStream(destinationFile).use { outputStream ->
                val buffer = ByteArray(1024)
                var length: Int
                while (inputStream.read(buffer).also { length = it } > 0) {
                    outputStream.write(buffer, 0, length)
                }
            }
        }
        Toast.makeText(context, "File saved to Downloads", Toast.LENGTH_SHORT).show()
    } catch (e: IOException) {
        e.printStackTrace()
        Toast.makeText(context, "Error saving file", Toast.LENGTH_SHORT).show()
    }
}
