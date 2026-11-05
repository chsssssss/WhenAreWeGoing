package com.github.chsssssss.eonje.domain.model

/**
 * 장소를 묶는 폴더. 저장소(Room, 이후 Firestore)와 무관한 순수 모델이다.
 * [color]는 지도 마커 배경색(ARGB Int), [iconKey]는 마커에 얹는 이모지다 — [FolderVisuals] 팔레트에서 고른다.
 */
data class Folder(
    val id: String,
    val name: String,
    val createdAt: Long,
    val color: Int = FolderVisuals.DEFAULT_COLOR,
    val iconKey: String = FolderVisuals.DEFAULT_ICON,
)
