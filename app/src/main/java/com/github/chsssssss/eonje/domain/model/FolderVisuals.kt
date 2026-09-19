package com.github.chsssssss.eonje.domain.model

/**
 * 폴더 생성 시 고를 수 있는 마커 색상·아이콘 팔레트. 지도 마커는 동그라미 배경(색)에
 * 이모지 하나를 얹어 그리는데, 이모지는 시스템 폰트로 그려지므로 벡터 리소스 없이도
 * Compose UI(피커)와 Canvas(마커 비트맵) 양쪽에서 그대로 재사용할 수 있다.
 */
object FolderVisuals {
    const val DEFAULT_COLOR = 0xFFE53935.toInt()
    const val DEFAULT_ICON = "📍"

    // 지도 배경(연한 회색·베이지)에서도 눈에 잘 띄도록 채도 높은 색으로만 구성.
    val colorPalette = listOf(
        0xFFE53935.toInt(), // 빨강
        0xFFFB8C00.toInt(), // 주황
        0xFFFDD835.toInt(), // 노랑
        0xFF43A047.toInt(), // 초록
        0xFF00ACC1.toInt(), // 청록
        0xFF1E88E5.toInt(), // 파랑
        0xFF5E35B1.toInt(), // 보라
        0xFFD81B60.toInt(), // 핑크
    )

    val iconOptions = listOf("📍", "🍽️", "☕", "🍺", "🛍️", "⭐", "❤️", "🏠")
}
