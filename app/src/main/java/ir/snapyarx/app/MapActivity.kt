package ir.snapyarx.app

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import okhttp3.OkHttpClient
import org.maplibre.android.MapLibre
import org.maplibre.android.annotations.Marker
import org.maplibre.android.annotations.MarkerOptions
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.maps.MapView
import org.maplibre.android.module.http.HttpRequestUtil

private val MapBg = Color(0xFF07130F)
private val MapGreen = Color(0xFF19C784)
private val MapGold = Color(0xFFD6B56D)
private val MapWhite = Color(0xFFF4F8F5)
private val MapMuted = Color(0xFFB4C4BC)

class MapActivity : ComponentActivity() {
    private lateinit var mapView: MapView
    private var mapReady by mutableStateOf(false)
    private var destinationLabel by mutableStateOf("برای انتخاب مقصد، روی نقشه لمس کنید")
    private var maplibreMap: org.maplibre.android.maps.MapLibreMap? = null
    private var destinationMarker: Marker? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val apiKey = BuildConfig.NESHAN_API_KEY.trim()
        MapLibre.getInstance(this)

        if (apiKey.isNotEmpty()) {
            val client = OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val host = request.url.host
                    if ((host == "neshan.org" || host.endsWith(".neshan.org")) &&
                        request.url.queryParameter("key") == null
                    ) {
                        val url = request.url.newBuilder()
                            .addQueryParameter("key", apiKey)
                            .build()
                        chain.proceed(request.newBuilder().url(url).build())
                    } else {
                        chain.proceed(request)
                    }
                }
                .build()
            HttpRequestUtil.setOkHttpClient(client)
        }

        mapView = MapView(this)
        mapView.onCreate(savedInstanceState)

        mapView.getMapAsync { map ->
            maplibreMap = map
            if (apiKey.isNotEmpty()) {
                map.setStyle("https://static.neshan.org/sdk/maplibre/styles/dark.json?key=$apiKey") {
                    mapReady = true
                }
                map.cameraPosition = CameraPosition.Builder()
                    .target(LatLng(35.6892, 51.3890))
                    .zoom(12.0)
                    .build()

                map.addOnMapClickListener { point ->
                    destinationMarker?.let { map.removeMarker(it) }
                    destinationMarker = map.addMarker(
                        MarkerOptions()
                            .position(point)
                            .title("مقصد سفر")
                            .snippet("مقصد انتخابی شما")
                    )
                    destinationLabel = "مقصد انتخاب شد: %.5f ، %.5f".format(
                        point.latitude,
                        point.longitude
                    )
                    true
                }
            }
        }

        setContent {
            MaterialTheme(colorScheme = darkColorScheme(
                primary = MapGreen,
                background = MapBg,
                surface = Color(0xFF13221C),
                onPrimary = MapBg,
                onBackground = MapWhite,
                onSurface = MapWhite
            )) {
                MapPage(
                    mapView = mapView,
                    mapReady = mapReady,
                    destinationLabel = destinationLabel,
                    missingKey = apiKey.isEmpty(),
                    onBack = { finish() }
                )
            }
        }
    }

    override fun onStart() {
        super.onStart()
        if (::mapView.isInitialized) mapView.onStart()
    }

    override fun onResume() {
        super.onResume()
        if (::mapView.isInitialized) mapView.onResume()
    }

    override fun onPause() {
        if (::mapView.isInitialized) mapView.onPause()
        super.onPause()
    }

    override fun onStop() {
        if (::mapView.isInitialized) mapView.onStop()
        super.onStop()
    }

    override fun onLowMemory() {
        super.onLowMemory()
        if (::mapView.isInitialized) mapView.onLowMemory()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        if (::mapView.isInitialized) mapView.onSaveInstanceState(outState)
    }

    override fun onDestroy() {
        if (::mapView.isInitialized) mapView.onDestroy()
        super.onDestroy()
    }
}

@Composable
private fun MapPage(
    mapView: MapView,
    mapReady: Boolean,
    destinationLabel: String,
    missingKey: Boolean,
    onBack: () -> Unit
) {
    Box(Modifier.fillMaxSize().background(MapBg)) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize()
        )

        Row(
            Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .statusBarsPadding()
                .padding(horizontal = 16.dp, vertical = 12.dp)
                .background(Color(0xE613211B), RoundedCornerShape(22.dp))
                .padding(horizontal = 16.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.Default.ArrowBack, contentDescription = "بازگشت", tint = MapWhite)
            }
            Column(Modifier.weight(1f)) {
                Text("نقشه سفر اسنپ‌یار", color = MapWhite, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("نقشه نشان • انتخاب مقصد", color = MapMuted, fontSize = 11.sp)
            }
            Icon(Icons.Default.Map, contentDescription = null, tint = MapGold, modifier = Modifier.size(25.dp))
        }

        Card(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .padding(14.dp),
            shape = RoundedCornerShape(28.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xF213211B)),
            elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
        ) {
            Column(Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier.size(38.dp).background(MapGreen.copy(alpha = .15f), RoundedCornerShape(13.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(Icons.Default.Place, contentDescription = null, tint = MapGreen)
                    }
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("مقصد سفر", color = MapWhite, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                        Text(destinationLabel, color = MapMuted, fontSize = 11.sp)
                    }
                }
                Spacer(Modifier.height(14.dp))
                if (missingKey) {
                    Text(
                        "برای نمایش نقشه، کلید API نشان باید در تنظیمات امن GitHub ثبت شود.",
                        color = MapGold,
                        fontSize = 12.sp
                    )
                } else if (!mapReady) {
                    LinearProgressIndicator(
                        modifier = Modifier.fillMaxWidth(),
                        color = MapGreen,
                        trackColor = MapGreen.copy(alpha = .15f)
                    )
                    Spacer(Modifier.height(6.dp))
                    Text("در حال بارگذاری نقشه نشان…", color = MapMuted, fontSize = 11.sp)
                } else {
                    Text(
                        "برای تعیین مقصد، نقطه موردنظر را روی نقشه لمس کنید.",
                        color = MapGreen,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(10.dp))
                Text(
                    "© نشان • MapLibre",
                    color = MapMuted.copy(alpha = .75f),
                    fontSize = 9.sp
                )
            }
        }
    }
}
