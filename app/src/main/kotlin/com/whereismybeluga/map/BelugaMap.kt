package com.whereismybeluga.map

import android.content.Context
import android.graphics.Color
import android.view.Gravity
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.whereismybeluga.data.Coordinate
import com.whereismybeluga.ui.CameraState
import org.maplibre.android.MapLibre
import org.maplibre.android.camera.CameraPosition
import org.maplibre.android.camera.CameraUpdateFactory
import org.maplibre.android.geometry.LatLng
import org.maplibre.android.geometry.LatLngBounds
import org.maplibre.android.maps.MapLibreMap
import org.maplibre.android.maps.MapView
import org.maplibre.android.maps.Style
import org.maplibre.android.style.expressions.Expression
import org.maplibre.android.style.layers.CircleLayer
import org.maplibre.android.style.layers.PropertyFactory
import org.maplibre.android.style.layers.SymbolLayer
import org.maplibre.android.style.sources.GeoJsonSource
import org.maplibre.geojson.Feature
import org.maplibre.geojson.FeatureCollection
import org.maplibre.geojson.Point

// OpenFreeMap: free OpenStreetMap-based vector tiles, no API key, commercial use allowed.
private const val STYLE_LIGHT = "https://tiles.openfreemap.org/styles/liberty"
private const val STYLE_DARK = "https://tiles.openfreemap.org/styles/dark"

private const val MARKER_SOURCE = "belugas"
private const val MARKER_LAYER = "belugas"
private const val USER_SOURCE = "user"
private const val USER_LAYER = "user"

private const val ANIMATION_MS = 500
private const val AIRCRAFT_ZOOM = 5.0

/** Centered over Western Europe, where most Beluga shuttle routes happen. */
val INITIAL_CAMERA = CameraState(Coordinate(48.5, 4.0), 3.6)

data class MapMarker(
    val key: String,
    val position: Coordinate,
    val kind: MarkerKind,
    val facingRight: Boolean = false,
    /** Number of aircraft the marker stands for. */
    val count: Int = 1,
) {
    val imageName: String get() = "${kind.name}-${if (facingRight) "r" else "l"}-$count"
}

/** Space taken by the panels drawn over the map, in pixels. */
data class MapInsets(val top: Int, val bottom: Int)

/** Lets the screen move the camera of a [BelugaMap]. */
class MapController {
    internal var holder: MapHolder? = null

    fun center(position: Coordinate) {
        holder?.center(position)
    }

    /** Frames all the given points, clear of the panels drawn over the map. */
    fun fit(points: List<Coordinate>) {
        holder?.fit(points)
    }
}

@Composable
fun BelugaMap(
    markers: List<MapMarker>,
    userLocation: Coordinate?,
    dark: Boolean,
    palette: MarkerPalette,
    insets: MapInsets,
    initialCamera: CameraState,
    controller: MapController,
    onMarkerClick: (String) -> Unit,
    onMapClick: () -> Unit,
    onMapLongClick: (Coordinate) -> Unit,
    onCameraIdle: (CameraState) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val holder = remember {
        MapHolder(context, if (dark) STYLE_DARK else STYLE_LIGHT, palette, initialCamera)
    }
    SideEffect {
        holder.onMarkerClick = onMarkerClick
        holder.onMapClick = onMapClick
        holder.onMapLongClick = onMapLongClick
        holder.onCameraIdle = onCameraIdle
        holder.setInsets(insets)
        holder.setContent(markers, userLocation)
    }

    val lifecycle = LocalLifecycleOwner.current.lifecycle
    DisposableEffect(lifecycle, holder) {
        controller.holder = holder
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_START -> holder.mapView.onStart()
                Lifecycle.Event.ON_RESUME -> holder.mapView.onResume()
                Lifecycle.Event.ON_PAUSE -> holder.mapView.onPause()
                Lifecycle.Event.ON_STOP -> holder.mapView.onStop()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            controller.holder = null
            holder.mapView.onDestroy()
        }
    }

    AndroidView(factory = { holder.mapView }, modifier = modifier)
}

/** Owns the MapLibre view and keeps its sources in sync with the latest content. */
internal class MapHolder(
    context: Context,
    styleUrl: String,
    palette: MarkerPalette,
    initialCamera: CameraState,
) {
    private val density = context.resources.displayMetrics.density
    private val icons = MarkerIcons(density, palette)

    var onMarkerClick: (String) -> Unit = {}
    var onMapClick: () -> Unit = {}
    var onMapLongClick: (Coordinate) -> Unit = {}
    var onCameraIdle: (CameraState) -> Unit = {}

    private var map: MapLibreMap? = null
    private var style: Style? = null
    private var markers: List<MapMarker> = emptyList()
    private var userLocation: Coordinate? = null
    private var insets = MapInsets(0, 0)

    val mapView: MapView

    init {
        MapLibre.getInstance(context)
        // MapLibre otherwise asks Android whether the network is up, which needs the
        // ACCESS_NETWORK_STATE permission the app does not request. Failed tile
        // downloads are retried anyway.
        MapLibre.setConnected(true)
        mapView = MapView(context)
        mapView.onCreate(null)
        mapView.getMapAsync { map ->
            this.map = map
            map.cameraPosition = CameraPosition.Builder()
                .target(initialCamera.center.toLatLng())
                .zoom(initialCamera.zoom)
                .build()
            map.uiSettings.isRotateGesturesEnabled = false
            map.uiSettings.isTiltGesturesEnabled = false
            map.uiSettings.isCompassEnabled = false
            applyInsets()
            map.addOnCameraIdleListener {
                val position = map.cameraPosition
                val target = position.target ?: return@addOnCameraIdleListener
                onCameraIdle(CameraState(Coordinate(target.latitude, target.longitude), position.zoom))
            }
            map.addOnMapClickListener { point ->
                val hit = map.queryRenderedFeatures(map.projection.toScreenLocation(point), MARKER_LAYER)
                val key = hit.firstOrNull()?.getStringProperty("key")
                if (key != null) onMarkerClick(key) else onMapClick()
                true
            }
            map.addOnMapLongClickListener { point ->
                onMapLongClick(Coordinate(point.latitude, point.longitude.wrapLongitude()))
                true
            }
            map.setStyle(Style.Builder().fromUri(styleUrl)) { style ->
                style.addSource(GeoJsonSource(MARKER_SOURCE))
                style.addLayer(
                    SymbolLayer(MARKER_LAYER, MARKER_SOURCE).withProperties(
                        PropertyFactory.iconImage(Expression.get("image")),
                        PropertyFactory.iconAllowOverlap(true),
                        PropertyFactory.iconIgnorePlacement(true),
                        // Higher keys are drawn last, i.e. on top.
                        PropertyFactory.symbolSortKey(Expression.get("order")),
                    ),
                )
                // Added last so the user dot stays visible on top of a nearby aircraft;
                // taps only look at the marker layer, so it never steals them.
                style.addSource(GeoJsonSource(USER_SOURCE))
                style.addLayer(
                    CircleLayer(USER_LAYER, USER_SOURCE).withProperties(
                        PropertyFactory.circleRadius(7f),
                        PropertyFactory.circleColor(Color.rgb(26, 115, 232)),
                        PropertyFactory.circleStrokeColor(Color.WHITE),
                        PropertyFactory.circleStrokeWidth(2.5f),
                    ),
                )
                this.style = style
                render()
            }
        }
    }

    fun setInsets(insets: MapInsets) {
        if (insets == this.insets) return
        this.insets = insets
        applyInsets()
    }

    /** Keeps the OpenStreetMap attribution visible, just above the bottom panel. */
    private fun applyInsets() {
        val ui = map?.uiSettings ?: return
        val margin = (8 * density).toInt()
        ui.attributionGravity = Gravity.BOTTOM or Gravity.END
        ui.setAttributionMargins(0, 0, margin, insets.bottom + margin)
        ui.logoGravity = Gravity.BOTTOM or Gravity.START
        ui.setLogoMargins(margin, 0, 0, insets.bottom + margin)
    }

    fun setContent(markers: List<MapMarker>, userLocation: Coordinate?) {
        if (markers == this.markers && userLocation == this.userLocation) return
        this.markers = markers
        this.userLocation = userLocation
        render()
    }

    private fun render() {
        val style = style ?: return
        for (marker in markers) {
            if (style.getImage(marker.imageName) == null) {
                style.addImage(marker.imageName, icons.render(marker.kind, marker.facingRight, marker.count))
            }
        }
        style.getSourceAs<GeoJsonSource>(MARKER_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(
                markers.map { marker ->
                    Feature.fromGeometry(marker.position.toPoint()).apply {
                        addStringProperty("key", marker.key)
                        addStringProperty("image", marker.imageName)
                        addNumberProperty("order", marker.kind.ordinal)
                    }
                },
            ),
        )
        style.getSourceAs<GeoJsonSource>(USER_SOURCE)?.setGeoJson(
            FeatureCollection.fromFeatures(listOfNotNull(userLocation?.let { Feature.fromGeometry(it.toPoint()) })),
        )
    }

    fun center(position: Coordinate) {
        map?.animateCamera(CameraUpdateFactory.newLatLngZoom(position.toLatLng(), AIRCRAFT_ZOOM), ANIMATION_MS)
    }

    fun fit(points: List<Coordinate>) {
        val distinct = points.distinct()
        when {
            distinct.isEmpty() -> Unit
            // A single point (e.g. whole fleet parked at base) would zoom all the way in.
            distinct.size == 1 -> center(distinct.first())
            else -> {
                val bounds = LatLngBounds.Builder().includes(distinct.map { it.toLatLng() }).build()
                val side = (50 * density).toInt()
                val vertical = (40 * density).toInt()
                map?.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                        bounds, side, insets.top + vertical, side, insets.bottom + vertical,
                    ),
                    ANIMATION_MS,
                )
            }
        }
    }

    /** The map can be scrolled past the antimeridian: bring longitudes back into [-180, 180]. */
    private fun Double.wrapLongitude() = ((this + 180.0) % 360.0 + 360.0) % 360.0 - 180.0

    private fun Coordinate.toLatLng() = LatLng(latitude, longitude)
    private fun Coordinate.toPoint() = Point.fromLngLat(longitude, latitude)
}
