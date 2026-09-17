package com.constrakr.ui.components

import android.graphics.Paint
import android.os.Handler
import android.os.Looper
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Terrain
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import org.osmdroid.events.MapListener
import org.osmdroid.events.ScrollEvent
import org.osmdroid.events.ZoomEvent
import org.osmdroid.tileprovider.tilesource.OnlineTileSourceBase
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.util.MapTileIndex
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Polygon

private enum class JobSiteMapLayer(val label: String) {
    STANDARD("Standard"),
    SATELLITE("Satellite"),
    HYBRID("Hybrid")
}

private val DEFAULT_CENTER = GeoPoint(14.5995, 120.9842)
private const val DEFAULT_ZOOM = 16.0

private val ESRI_SATELLITE = object : OnlineTileSourceBase(
    "ESRI.WorldImagery",
    0,
    19,
    256,
    "",
    arrayOf("https://server.arcgisonline.com/ArcGIS/rest/services/World_Imagery/MapServer/tile/")
) {
    override fun getTileURLString(pMapTileIndex: Long): String {
        return baseUrl + MapTileIndex.getZoom(pMapTileIndex) + "/" +
            MapTileIndex.getY(pMapTileIndex) + "/" +
            MapTileIndex.getX(pMapTileIndex)
    }
}

/** Free OpenStreetMap / ESRI tiles — no Google Maps API key required. */
@Composable
fun JobSiteMapPinEditor(
    latitude: Double,
    longitude: Double,
    onCoordinatesChanged: (Double, Double) -> Unit,
    radiusMeters: Double,
    recenterToken: Int = 0,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val hasPin = latitude != 0.0 || longitude != 0.0
    var mapLayer by remember { mutableStateOf(JobSiteMapLayer.STANDARD) }
    var layerMenuOpen by remember { mutableStateOf(false) }
    var ignoreCameraUpdates by remember { mutableStateOf(false) }
    val scrollHandler = remember { Handler(Looper.getMainLooper()) }

    val mapView = remember {
        MapView(context).apply {
            setMultiTouchControls(true)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setTileSource(TileSourceFactory.MAPNIK)
            minZoomLevel = 3.0
            maxZoomLevel = 20.0
            controller.setZoom(DEFAULT_ZOOM)
            controller.setCenter(if (hasPin) GeoPoint(latitude, longitude) else DEFAULT_CENTER)
        }
    }

    val radiusOverlay = remember {
        Polygon().apply {
            fillPaint.color = android.graphics.Color.argb(46, 13, 148, 136)
            outlinePaint.color = android.graphics.Color.argb(217, 13, 148, 136)
            outlinePaint.strokeWidth = 4f
            outlinePaint.style = Paint.Style.STROKE
        }
    }

    fun updateRadiusOverlay(center: GeoPoint) {
        if (!hasPin && latitude == 0.0 && longitude == 0.0) {
            mapView.overlays.remove(radiusOverlay)
            return
        }
        radiusOverlay.points = Polygon.pointsAsCircle(center, radiusMeters)
        if (!mapView.overlays.contains(radiusOverlay)) {
            mapView.overlays.add(0, radiusOverlay)
        }
        mapView.invalidate()
    }

    fun publishCenter() {
        if (ignoreCameraUpdates) return
        val center = mapView.mapCenter as GeoPoint
        onCoordinatesChanged(center.latitude, center.longitude)
        updateRadiusOverlay(center)
    }

    DisposableEffect(mapView) {
        val scrollEndRunnable = Runnable { publishCenter() }
        val mapListener = object : MapListener {
            override fun onScroll(event: ScrollEvent?): Boolean {
                scrollHandler.removeCallbacks(scrollEndRunnable)
                scrollHandler.postDelayed(scrollEndRunnable, 120)
                return false
            }

            override fun onZoom(event: ZoomEvent?): Boolean {
                scrollHandler.removeCallbacks(scrollEndRunnable)
                scrollHandler.postDelayed(scrollEndRunnable, 120)
                return false
            }
        }
        mapView.addMapListener(mapListener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> mapView.onPause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)
        mapView.onResume()

        onDispose {
            scrollHandler.removeCallbacks(scrollEndRunnable)
            mapView.removeMapListener(mapListener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            mapView.onPause()
            mapView.onDetach()
        }
    }

    LaunchedEffect(mapLayer) {
        when (mapLayer) {
            JobSiteMapLayer.STANDARD -> mapView.setTileSource(TileSourceFactory.MAPNIK)
            JobSiteMapLayer.SATELLITE -> mapView.setTileSource(ESRI_SATELLITE)
            JobSiteMapLayer.HYBRID -> mapView.setTileSource(TileSourceFactory.OpenTopo)
        }
        mapView.invalidate()
    }

    LaunchedEffect(recenterToken, latitude, longitude) {
        if (recenterToken <= 0 && !hasPin) return@LaunchedEffect
        if (latitude == 0.0 && longitude == 0.0) return@LaunchedEffect
        ignoreCameraUpdates = true
        val target = GeoPoint(latitude, longitude)
        mapView.controller.animateTo(target)
        updateRadiusOverlay(target)
        ignoreCameraUpdates = false
    }

    LaunchedEffect(radiusMeters, latitude, longitude) {
        val center = if (hasPin) GeoPoint(latitude, longitude) else mapView.mapCenter as GeoPoint
        updateRadiusOverlay(center)
    }

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant)
    ) {
        AndroidView(
            factory = { mapView },
            modifier = Modifier.fillMaxSize(),
            update = { view ->
                if (hasPin) {
                    updateRadiusOverlay(GeoPoint(latitude, longitude))
                }
                view.invalidate()
            }
        )

        Icon(
            imageVector = Icons.Default.LocationOn,
            contentDescription = "Map pin",
            tint = Color(0xFFE53935),
            modifier = Modifier
                .align(Alignment.Center)
                .padding(bottom = 18.dp)
        )

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(10.dp)
        ) {
            MapControlButton(onClick = { layerMenuOpen = true }) {
                Icon(
                    when (mapLayer) {
                        JobSiteMapLayer.STANDARD -> Icons.Default.Map
                        JobSiteMapLayer.SATELLITE -> Icons.Default.Terrain
                        JobSiteMapLayer.HYBRID -> Icons.Default.Layers
                    },
                    contentDescription = "Map layer"
                )
            }
            DropdownMenu(expanded = layerMenuOpen, onDismissRequest = { layerMenuOpen = false }) {
                JobSiteMapLayer.entries.forEach { layer ->
                    DropdownMenuItem(
                        text = { Text(layer.label) },
                        onClick = {
                            mapLayer = layer
                            layerMenuOpen = false
                        }
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(10.dp),
            verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)
        ) {
            MapControlButton(onClick = { mapView.controller.zoomIn() }) {
                Icon(Icons.Default.Add, contentDescription = "Zoom in")
            }
            MapControlButton(onClick = { mapView.controller.zoomOut() }) {
                Icon(Icons.Default.Remove, contentDescription = "Zoom out")
            }
        }
    }
}

@Composable
private fun MapControlButton(onClick: () -> Unit, content: @Composable () -> Unit) {
    Surface(
        onClick = onClick,
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.92f),
        shadowElevation = 2.dp
    ) {
        Box(Modifier.padding(8.dp), contentAlignment = Alignment.Center) {
            content()
        }
    }
}
