package com.github.chsssssss.eonje.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
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
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.github.chsssssss.eonje.R
import com.github.chsssssss.eonje.domain.model.GeoPoint
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.LatLng
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.label.LabelTextStyle

private val DEFAULT_CENTER: LatLng = LatLng.from(37.5665, 126.9780) // 좌표가 없을 때의 기본 위치: 서울시청
private const val MY_LOCATION_LABEL_ID = "__my_location__" // 장소 마커와 같은 레이어에 있어서 클릭 처리에서 걸러내야 한다
private const val NAME_TEXT_MIN_ZOOM_LEVEL = 14 // 이 레벨보다 축소하면 이름이 안 보이고 마커 아이콘만 남는다
private const val NAME_LABEL_ID_SUFFIX = "__name" // 이름 라벨은 아이콘과 같은 좌표에 별도 라벨로 붙인다
private const val NAME_TEXT_PIXEL_OFFSET_Y = 70f // 아이콘 아래로 이름을 밀어내는 픽셀 오프셋

@Composable
fun KakaoMapView(
    places: List<HomePlace>,
    highlightedId: String?,
    currentLocation: GeoPoint?,
    onPlaceClick: (String?) -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val onPlaceClickState = rememberUpdatedState(onPlaceClick)
    val highlightedIdState = rememberUpdatedState(highlightedId)
    var kakaoMap by remember { mutableStateOf<KakaoMap?>(null) }
    // 위치를 처음 잡았을 때 한 번만 그쪽으로 옮긴다 — 이후 사용자가 지도를 움직인 걸 되돌리지 않도록.
    var didCenterOnLocation by remember { mutableStateOf(false) }

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
                            if (label.labelId == MY_LOCATION_LABEL_ID) return@setOnLabelClickListener false
                            // 이미 선택된 마커를 다시 누르면 선택 해제(토글)한다.
                            val next = if (label.labelId == highlightedIdState.value) null else label.labelId
                            onPlaceClickState.value(next)
                            true
                        }
                        // 마커가 아닌 지도 빈 곳을 탭하면 선택을 해제해서 하단 정보 카드를 닫는다.
                        map.setOnMapClickListener { _, _, _, _ ->
                            onPlaceClickState.value(null)
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

    LaunchedEffect(kakaoMap, places, highlightedId, currentLocation) {
        val map = kakaoMap ?: return@LaunchedEffect
        val labelManager = map.labelManager ?: return@LaunchedEffect
        val layer = labelManager.layer ?: return@LaunchedEffect
        layer.removeAll()

        // LabelStyle.from(Int)는 내부적으로 리소스를 비트맵으로 디코딩하는데 VectorDrawable(XML)은
        // 그 경로로 디코딩이 안 돼서 조용히 빈 라벨이 된다(에러도 없이 안 보임) — 직접 비트맵으로 변환해서 넘긴다.
        // 아이콘 라벨 안에 텍스트를 같이 넣으면 LabelStyle.padding/setTextGravity/setAnchorPoint 어느 것도
        // 아이콘-텍스트 간격에 영향을 주지 않는다(카카오맵 SDK의 알려진 제약) — 이름을 별도 라벨로 분리하고
        // Label.changePixelOffset()으로 아래로 밀어내는 방식으로 우회한다.
        val defaultIconStyles = labelManager.addLabelStyles(
            LabelStyles.from(LabelStyle.from(context.vectorDrawableToBitmap(R.drawable.ic_map_pin)))
        )
        val highlightedIconStyles = labelManager.addLabelStyles(
            LabelStyles.from(LabelStyle.from(context.vectorDrawableToBitmap(R.drawable.ic_map_pin_highlighted)))
        )
        val nameTextStyle = LabelTextStyle.from(22, Color.BLACK, 4, Color.WHITE)
        // 줌 레벨별 스타일 두 개: 축소된 상태(0)에서는 빈 스타일(텍스트 없음), 어느 정도 확대했을 때만 이름을 보여준다.
        val nameStyles = labelManager.addLabelStyles(
            LabelStyles.from(
                LabelStyle.from().setZoomLevel(0),
                LabelStyle.from(nameTextStyle).setZoomLevel(NAME_TEXT_MIN_ZOOM_LEVEL),
            )
        )

        var focusLatLng: LatLng? = null
        places.forEach { place ->
            val latLng = place.toLatLngOrNull() ?: return@forEach
            val isHighlighted = place.id == highlightedId
            val iconOptions = LabelOptions.from(place.id, latLng)
                .setStyles(if (isHighlighted) highlightedIconStyles else defaultIconStyles)
            layer.addLabel(iconOptions)
            if (isHighlighted) focusLatLng = latLng

            val nameOptions = LabelOptions.from(place.id + NAME_LABEL_ID_SUFFIX, latLng)
                .setStyles(nameStyles)
                .setClickable(false)
                .setTexts(LabelTextBuilder().setTexts(place.name))
            layer.addLabel(nameOptions)?.changePixelOffset(0f, NAME_TEXT_PIXEL_OFFSET_Y)
        }

        val myLatLng = currentLocation?.let { LatLng.from(it.latitude, it.longitude) }
        if (myLatLng != null) {
            val myStyles = labelManager.addLabelStyles(
                LabelStyles.from(LabelStyle.from(context.vectorDrawableToBitmap(R.drawable.ic_my_location)))
            )
            layer.addLabel(LabelOptions.from(MY_LOCATION_LABEL_ID, myLatLng).setStyles(myStyles))
        }

        val focus = focusLatLng
        when {
            // 선택된 마커가 있으면 항상 그쪽이 우선.
            focus != null -> map.moveCamera(CameraUpdateFactory.newCenterPosition(focus))
            // 지도를 처음 열 때는 현재 위치를 중심으로.
            !didCenterOnLocation && myLatLng != null -> {
                map.moveCamera(CameraUpdateFactory.newCenterPosition(myLatLng))
                didCenterOnLocation = true
            }
            // 위치를 못 받았으면(권한 거부 등) 저장된 장소라도 보이게 한다.
            !didCenterOnLocation -> places.firstNotNullOfOrNull { it.toLatLngOrNull() }
                ?.let { map.moveCamera(CameraUpdateFactory.newCenterPosition(it)) }
        }
    }

    AndroidView(modifier = modifier.fillMaxSize(), factory = { mapView })
}

private fun HomePlace.toLatLngOrNull(): LatLng? {
    val lat = latitude ?: return null
    val lng = longitude ?: return null
    return LatLng.from(lat, lng)
}

private fun Context.vectorDrawableToBitmap(resId: Int): Bitmap {
    val drawable = requireNotNull(ContextCompat.getDrawable(this, resId))
    val width = drawable.intrinsicWidth.coerceAtLeast(1)
    val height = drawable.intrinsicHeight.coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    drawable.setBounds(0, 0, width, height)
    drawable.draw(Canvas(bitmap))
    return bitmap
}
