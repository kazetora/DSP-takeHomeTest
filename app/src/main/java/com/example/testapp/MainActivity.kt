package com.example.testapp

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import androidx.core.app.ActivityCompat
import androidx.navigation.NavHostController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine

class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private var extractedString = ""
    private var distance = 0.0
    private var timeToTravel = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            MyApp {
                TakePictureButton(onPictureTaken = ::onPictureTaken, this::getLocation)
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocation(extractedText: String) {
        this.extractedString = extractedText
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED
        ) {
            fusedLocationClient.lastLocation.addOnSuccessListener { location ->
                location?.let {
                    val targetLocation = Location("target")
                    targetLocation.latitude = TARGET_LAT
                    targetLocation.longitude = TARGET_LON
                    val distance = location.distanceTo(targetLocation) / 1000
                    val timeToTravel = ((distance/1000.0) / AVERAGE_SPEED_KPH) * 60
                    Log.d("MainActivity", " Latitude: ${location.latitude}, Longitude: ${location.longitude}, distance: $distance, timeToTravel: $timeToTravel, text: $extractedText")
                    uploadDataToFirestore(extractedText, distance, timeToTravel.toInt())
                }
            }
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                REQUEST_LOCATION_PERMISSION
            )
        }
    }



    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_LOCATION_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d("Main Activity", "Location access granted")
                getLocation(this.extractedString)
            }
        }
    }

    companion object {
        private const val REQUEST_LOCATION_PERMISSION = 1001
        private const val TARGET_LAT = -6.19070923716
        private const val TARGET_LON = 106.819788387
        private const val AVERAGE_SPEED_KPH = 50
    }
}

@Composable
fun MyApp(content: @Composable () -> Unit) {
    MaterialTheme {
        Surface(color = MaterialTheme.colors.background) {
            content()
        }
    }
}

@Composable
fun TakePictureButton(onPictureTaken: (File, (String) -> Unit) -> Unit, getLocationCallback: (String) -> Unit) {
    val context = LocalContext.current
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) {bitmap ->
        val pictureFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        pictureFile.outputStream().use { outputStream ->
            val desiredWidth = 800
            val desiredHeight = (bitmap.height * (desiredWidth.toFloat()/bitmap.width.toFloat())).toInt()
            val resizedBitmap = Bitmap.createScaledBitmap(bitmap, desiredWidth, desiredHeight, true)
            resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
        }

        onPictureTaken(pictureFile, getLocationCallback)

    }
    Column(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Button(onClick = { takePictureLauncher.launch(null) },
        colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(text = "Take Picture")
                Image(
                    painter = painterResource(id = R.drawable.baseline_camera_alt_24),
                    contentDescription = "Camera Icon",
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .size(32.dp)
                )
            }
        }
    }
}

//@Composable
//fun FirstScreen(navController: NavHostController, callback: (String) -> Unit) {
//    MyApp {
//        TakePictureButton(onPictureTaken = ::onPictureTaken, callback)
//    }
//}


fun onPictureTaken(pictureFile: File, getLocation: (String) -> Unit) {
    val bitmap = BitmapFactory.decodeFile(pictureFile.absolutePath)

    val inputImage = InputImage.fromBitmap(bitmap, 0)


    CoroutineScope(Dispatchers.Main).launch {
        try {
            val extractedText = withContext(Dispatchers.IO) {
                extractText(inputImage)
            }
//            Log.d("MainActivity", "Extracted text: $extractedText")
            getLocation(extractedText)

        } catch (exception: Exception) {
            Log.e("MainActivity", "Error extracting text: ", exception)

        }
    }
}

private suspend fun extractText(inputImage: InputImage): String = suspendCoroutine {continuation ->
    val options = TextRecognizerOptions.Builder().build()

    val recognizer = TextRecognition.getClient(options)

    recognizer.process(inputImage)
        .addOnSuccessListener {
            continuation.resume(it.text)
        }
        .addOnFailureListener{
            continuation.resumeWithException(it)
        }
}

private fun uploadDataToFirestore(extractedText: String, distance: Float, travelTimeMinutes: Int) {
    val db = FirebaseFirestore.getInstance()

    val data = hashMapOf(
        "extractedText" to extractedText,
        "distance" to distance,
        "travelTimeMinutes" to travelTimeMinutes
    )
    Log.d("Main Activity", "sending data")
    db.collection("data")
        .add(data)
        .addOnSuccessListener { documentReference ->
            Log.d("MainActivity", "DocumentSnapshot added with ID: ${documentReference.id}")
        }
        .addOnFailureListener { exception ->
            Log.w("MainActivity", "Error adding document", exception)
        }
}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val tmp:(String) -> Unit = {}
    MyApp {
        TakePictureButton(onPictureTaken = ::onPictureTaken, tmp)
    }
}
