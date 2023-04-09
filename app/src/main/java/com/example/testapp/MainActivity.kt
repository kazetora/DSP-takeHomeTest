package com.example.testapp

import android.Manifest
import android.annotation.SuppressLint
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.location.Location
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.livedata.observeAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.firebase.firestore.FirebaseFirestore
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine


class MainActivity : ComponentActivity() {
    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var viewModel: DataViewModel
    private var extractedString = ""
    private var distance = 0.0
    private var timeToTravel = 0


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        setContent {
            val navController = rememberNavController()
            val callback: (String, (StoreData) -> Unit)->Unit = this::getLocation
            val viewModel: DataViewModel = viewModel(key = "dataViewModel")
            this.viewModel = viewModel
            NavHost(navController, startDestination = Screen.FirstScreen.route) {
                composable(Screen.FirstScreen.route) {
                    FirstScreen(navController,::onPictureTaken, callback, viewModel)
                }
                composable(Screen.SecondScreen.route) {
                    SecondScreen(navController, viewModel)
                }
            }
//            MyApp {
//                TakePictureButton(onPictureTaken = ::onPictureTaken, this::getLocation)
//            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun getLocation(extractedText: String, callback: (StoreData) -> Unit) {
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
                    val timeToTravel = (distance / AVERAGE_SPEED_KPH) * 60
                    Log.d("MainActivity", " Latitude: ${location.latitude}, Longitude: ${location.longitude}, distance: $distance, timeToTravel: $timeToTravel, text: $extractedText")
                    val storeData = StoreData(extractedText, distance, timeToTravel.toInt())
                    callback(storeData)
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
                getLocation(this.extractedString){ data ->
                    updateViewModel(this.viewModel, data)
                }
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
fun FirstScreen(navController: NavHostController, onPictureTaken: (DataViewModel, File, (String, (StoreData)->Unit) -> Unit) -> Unit, getLocationCallback: (String, (StoreData) -> Unit) -> Unit, viewModel: DataViewModel) {
//    val viewModel: DataViewModel = viewModel(key = viewModelKey)
    val context = LocalContext.current
    val extractedText by viewModel.extractedText.observeAsState("")
    val takePictureLauncher = rememberLauncherForActivityResult(ActivityResultContracts.TakePicturePreview()) {bitmap ->

        val pictureFile = File(context.cacheDir, "photo_${System.currentTimeMillis()}.jpg")
        if (bitmap != null) {
            pictureFile.outputStream().use { outputStream ->
                val desiredWidth = 800
                val desiredHeight = ((bitmap.height) * (desiredWidth.toFloat()/bitmap.width.toFloat())).toInt()
                val resizedBitmap = Bitmap.createScaledBitmap(bitmap, desiredWidth, desiredHeight, true)
                resizedBitmap.compress(Bitmap.CompressFormat.JPEG, 85, outputStream)
            }

            onPictureTaken(viewModel, pictureFile, getLocationCallback)
        } else {
            Log.e("MainActivity", "Image is Null")
        }

//        navController.navigate(Screen.SecondScreen.route)
    }
    LaunchedEffect(extractedText) {
        if(extractedText.isNotEmpty()) {
            navController.navigate(Screen.SecondScreen.route)
        }
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
//        TakePictureButton(navController, onPictureTaken = ::onPictureTaken, callback)
//    }
//}
@Composable
fun SecondScreen(navController: NavHostController, viewModel: DataViewModel) {
//    val backStackEntry = navController.currentBackStackEntry
//    val viewModel: DataViewModel = viewModel(key = viewModelKey)
    val extractedText by viewModel.extractedText.observeAsState("")
    val distance by viewModel.distance.observeAsState(0.0)
    val estimatedTime by viewModel.estimatedTime.observeAsState(0)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("Extracted Text: $extractedText")
        Text("Distance: $distance km")
        Text("Estimated Time: $estimatedTime minutes")

        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            // Navigate back to FirstScreen
            viewModel.updateExtractedText("")
            navController.navigate(Screen.FirstScreen.route)
        }) {
            Text("Back to Take Picture")
        }
    }
}

fun onPictureTaken(viewModel: DataViewModel, pictureFile: File, getLocation: (String, (StoreData) -> Unit) -> Unit) {
    val bitmap = BitmapFactory.decodeFile(pictureFile.absolutePath)

    val inputImage = InputImage.fromBitmap(bitmap, 0)


    CoroutineScope(Dispatchers.Main).launch {
        try {
            val extractedText = withContext(Dispatchers.IO) {
                extractText(inputImage)
            }
//            Log.d("MainActivity", "Extracted text: $extractedText")
            getLocation(extractedText) { data ->
                updateViewModel(viewModel, data)
            }

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

private fun updateViewModel(viewModel: DataViewModel, data: StoreData) {
    viewModel.updateExtractedText(data.extractedText)
    viewModel.updateDistance(data.distance)
    viewModel.updateEstimatedTime(data.timeToTravel)
}

private fun tmpGetLocation(p1: String, p2:(StoreData) -> Unit)  {

}

@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    val navController = rememberNavController()
    val viewModel: DataViewModel = viewModel()
    MyApp {
        FirstScreen(navController, onPictureTaken = ::onPictureTaken, ::tmpGetLocation, viewModel)
    }
}
