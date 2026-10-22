package com.github.chsssssss.eonje.ui.components

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * 인스타그램 게시물의 공유 버튼과 같은 모양의 종이비행기(외곽선, 머리가 오른쪽 위).
 * Material의 Send 아이콘은 방향·모양이 달라서 사용자가 실제 버튼과 바로 연결 짓기 어렵다 — 안내 화면 전용.
 * 선 색은 [androidx.compose.material3.Icon]의 tint로 덮어쓴다.
 */
val PaperPlaneIcon: ImageVector by lazy {
    ImageVector.Builder(
        name = "PaperPlane",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f,
    ).apply {
        val stroke = SolidColor(Color.Black)
        // 날개 접힌 선: 꼬리 안쪽에서 머리까지
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(22f, 3f)
            lineTo(9.218f, 10.083f)
        }
        // 몸체 외곽선
        path(stroke = stroke, strokeLineWidth = 2f, strokeLineCap = StrokeCap.Round, strokeLineJoin = StrokeJoin.Round) {
            moveTo(11.698f, 20.334f)
            lineTo(22f, 3.001f)
            lineTo(2f, 3.001f)
            lineTo(9.218f, 10.084f)
            close()
        }
    }.build()
}
