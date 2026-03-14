package com.meshnet.app.mesh

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.meshnet.app.routing.NearbyPeer
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

@Composable
fun MapScreen(
    peers: List<NearbyPeer>,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val mapView = remember {
        MapView(context).apply {
            id = android.view.View.generateViewId()
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(true)
            controller.setZoom(16.0)
            controller.setCenter(GeoPoint(10.3244, 76.9956)) // Default center
        }
    }

    // Initialize OSMDroid
    Configuration.getInstance().load(
        context,
        context.getSharedPreferences("osmdroid", Context.MODE_PRIVATE)
    )

    val myLocationOverlay = remember {
        MyLocationNewOverlay(GpsMyLocationProvider(context), mapView).apply {
            enableMyLocation()
            enableFollowLocation()
        }
    }

    DisposableEffect(Unit) {
        mapView.overlays.add(myLocationOverlay)
        onDispose {
            myLocationOverlay.disableMyLocation()
            mapView.onDetach()
        }
    }

    // Update Peer Markers
    LaunchedEffect(peers) {
        // Clear existing markers (excluding my location)
        val toRemove = mapView.overlays.filterIsInstance<Marker>()
        mapView.overlays.removeAll(toRemove)

        peers.forEach { peer ->
            // In a real app, peers would provide their own GPS coords in the NearbyPeer object
            // For demo, we'll place them slightly offset from center if they don't have coords
            val marker = Marker(mapView).apply {
                position = GeoPoint(10.3244 + (Math.random() - 0.5) * 0.01, 76.9956 + (Math.random() - 0.5) * 0.01)
                title = peer.deviceName
                snippet = "Battery: ${peer.batteryLevel}%"
                setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
            }
            mapView.overlays.add(marker)
        }
        mapView.invalidate()
    }

    AndroidView(
        factory = { mapView },
        modifier = modifier
    )
}
