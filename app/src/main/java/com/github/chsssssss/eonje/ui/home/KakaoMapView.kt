package com.github.chsssssss.eonje.ui.home

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.chsssssss.eonje.R
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles

private val DEFAULT_CENTER: LatLng = LatLng.from(37.5665, 126.9780) // 좌표가 없을 때의 기본 위치: 서울시청

@Composable
fun KakaoMapView(
    places: List<HomePlace>,
    highlightedId: String?,
    onPlaceClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onPlaceClickState = rememberUpdatedState(onPlaceClick)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }

    val mapView = remember {
        MapView(context).apply {
            start(
                object : MapLifeCycleCallback() {
                    override fun onMapDestroy() = Unit
                    override fun onMapError(error: Exception) = Unit
                },
                object : KakaoMapReadyCallback() {
                    override fun onMapReady(map: KakaoMap) {
                        kakaoMap = map
                        map.setOnLabelClickListener { _, _, label ->
                            onPlaceClickState.value(label.labelId)
                            true
                        }
                    }

                    override fun getPosition(): LatLng = DEFAULT_CENTER

                    override fun getZoomLevel(): Int = 15
                },
            )
        }
    }

    DisposableEffect(lifecycleOwner, mapView) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> mapView.resume()
                Lifecycle.Event.ON_PAUSE -> mapView.pause()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(kakaoMap, places, highlightedId) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.layer ?: return@LaunchedEffect
        layer.removeAll()

        val defaultStyles = labelManager.addLabelStyles(
            LabelStyles.from(LabelStyle.from(R.drawable.ic_map_pin))
        )
        val highlightedStyles = labelManager.addLabelStyles(
            LabelStyles.from(LabelStyle.from(R.drawable.ic_map_pin_highlighted))
        )

        var focusLatLng: LatLng? = null
        places.forEach { place ->
            val latLng = place.toLatLngOrNull() ?: return@forEach
            val isHighlighted = place.id == highlightedId
            val options = LabelOptions.from(place.id, latLng)
                .setStyles(if (isHighlighted) highlightedStyles else defaultStyles)
            layer.addLabel(options)
            if (isHighlighted) focusLatLng = latLng
        }

        (focusLatLng ?: places.firstNotNullOfOrNull { it.toLatLngOrNull() })?.let {
            map.moveCamera(CameraUpdateFactory.newCenterPosition(it))
        }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { mapView })
}

private fun HomePlace.toLatLngOrNull(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return LatLng.from(lat, lng)
}
