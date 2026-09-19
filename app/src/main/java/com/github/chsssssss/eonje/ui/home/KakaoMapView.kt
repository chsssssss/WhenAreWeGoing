package com.github.chsssssss.eonje.ui.home

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
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
private const val NAME_TEXT_PIXEL_OFFSET_Y = 36f // 아이콘 아래로 이름을 밀어내는 픽셀 오프셋
private const val MARKER_DIAMETER_DP = 18f // 일반 마커 지름
private const val HIGHLIGHT_MARKER_DIAMETER_DP = 30f // 선택된 핀 모양 마커 지름 — 원 뷰포트(24) 기준 스케일 대상
private const val PIN_TIP_Y_FRACTION = 22f / 24f // 핀 뾰족한 끝의 세로 위치(뷰포트 24 기준) — 앵커포인트로 쓴다

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
        // 마커는 폴더별 색상+이모지 조합마다 다르게 생겨서 정적 리소스 대신 Canvas로 직접 그린다.
        // 같은 폴더의 장소가 여럿이면 비트맵을 매번 새로 만들 필요 없이 이 안에서 재사용한다.
        val styleCache = mutableMapOf<Triple<Int, String, Boolean>, LabelStyles>()
        fun stylesFor(color: Int, icon: String, highlighted: Boolean): LabelStyles =
            styleCache.getOrPut(Triple(color, icon, highlighted)) {
                // 핀 모양은 뾰족한 끝이 좌표를 가리켜야 해서 앵커가 하단인 반면, 일반 원형 마커는
                // 중심이 곧 좌표라 앵커가 중앙이다.
                val style = if (highlighted) {
                    LabelStyle.from(context.pinMarkerBitmap(color, icon)).setAnchorPoint(0.5f, PIN_TIP_Y_FRACTION)
                } else {
                    LabelStyle.from(context.circleMarkerBitmap(color, icon)).setAnchorPoint(0.5f, 0.5f)
                }
                labelManager.addLabelStyles(LabelStyles.from(style))!!
            }
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
                .setStyles(stylesFor(place.folderColor, place.folderIcon, isHighlighted))
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

/** 색이 있는 동그라미 배경 위에 이모지 아이콘을 얹은 마커를 직접 그린다 — 폴더마다 색·아이콘이 달라서
 * 정적 드로어블로는 표현이 안 된다. 이모지는 시스템 폰트로 그려지므로 별도 벡터 리소스가 필요 없다. */
private fun Context.circleMarkerBitmap(colorInt: Int, icon: String): Bitmap {
    val density = resources.displayMetrics.density
    val diameterPx = MARKER_DIAMETER_DP * density
    val strokePx = 2f * density
    val size = diameterPx.toInt().coerceAtLeast(1)
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val center = size / 2f
    val radius = center - strokePx / 2f

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt; style = Paint.Style.FILL }
    canvas.drawCircle(center, center, radius, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = strokePx
    }
    canvas.drawCircle(center, center, radius, strokePaint)

    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size * 0.42f
    }
    val textY = center - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(icon, center, textY, textPaint)

    return bitmap
}

/** 선택된 마커는 위가 둥글고 아래가 뾰족한 전형적인 핀(물방울) 모양으로 그린다 — 예전에 쓰던
 * 정적 드로어블(ic_map_pin.xml)과 같은 경로를 폴더 색으로 채워서 재현하고, 둥근 머리 부분
 * 가운데에 폴더 이모지 아이콘을 얹는다. 뾰족한 끝이 좌표를 가리키므로 앵커는 [PIN_TIP_Y_FRACTION]
 * (하단)을 쓴다 — [stylesFor] 참고. */
private fun Context.pinMarkerBitmap(colorInt: Int, icon: String): Bitmap {
    val density = resources.displayMetrics.density
    val size = (HIGHLIGHT_MARKER_DIAMETER_DP * density).toInt().coerceAtLeast(1)
    val scale = size / 24f
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)

    // ic_map_pin.xml의 pathData(M12,4 C7.58,4 4,7.58 4,12 C4,16.42 12,22 12,22 C12,22 20,16.42 20,12
    // C20,7.58 16.42,4 12,4 Z)를 그대로 옮기되 24 단위 뷰포트를 비트맵 크기에 맞게 스케일한다.
    val path = Path().apply {
        moveTo(12f * scale, 4f * scale)
        cubicTo(7.58f * scale, 4f * scale, 4f * scale, 7.58f * scale, 4f * scale, 12f * scale)
        cubicTo(4f * scale, 16.42f * scale, 12f * scale, 22f * scale, 12f * scale, 22f * scale)
        cubicTo(12f * scale, 22f * scale, 20f * scale, 16.42f * scale, 20f * scale, 12f * scale)
        cubicTo(20f * scale, 7.58f * scale, 16.42f * scale, 4f * scale, 12f * scale, 4f * scale)
        close()
    }

    val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colorInt; style = Paint.Style.FILL }
    canvas.drawPath(path, fillPaint)

    val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        style = Paint.Style.STROKE
        strokeWidth = 1.5f * density
    }
    canvas.drawPath(path, strokePaint)

    // 둥근 머리 부분의 중심은 원본 뷰포트 기준 (12, 12).
    val headCenterX = 12f * scale
    val headCenterY = 12f * scale
    val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        textAlign = Paint.Align.CENTER
        textSize = size * 0.28f
    }
    val textY = headCenterY - (textPaint.descent() + textPaint.ascent()) / 2f
    canvas.drawText(icon, headCenterX, textY, textPaint)

    return bitmap
}
