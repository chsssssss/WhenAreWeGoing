package com.github.chsssssss.eonje.data.debug

import com.github.chsssssss.eonje.data.local.EonjeDatabase
import com.github.chsssssss.eonje.data.local.ExtractedCandidateDao
import com.github.chsssssss.eonje.data.local.ExtractedCandidateEntity
import com.github.chsssssss.eonje.data.local.FolderDao
import com.github.chsssssss.eonje.data.local.FolderEntity
import com.github.chsssssss.eonje.data.local.PlaceDao
import com.github.chsssssss.eonje.data.local.PlaceEntity
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRef
import com.github.chsssssss.eonje.data.local.PostPlaceCrossRefDao
import com.github.chsssssss.eonje.data.local.SavedPostDao
import com.github.chsssssss.eonje.data.local.SavedPostEntity
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

private const val HOUR_MS = 60 * 60 * 1000L
private const val DAY_MS = 24 * HOUR_MS

/** 실제 인스타 썸네일 대신 쓰는 번들 리소스 — PlaceholderImage가 쓰는 Coil이 android.resource:// URI도 그대로 로드한다. */
private const val SEED_THUMBNAIL_URI =
    "android.resource://com.github.chsssssss.eonje/drawable/placeholder_thumb_sample"

/**
 * 스크린샷 촬영용으로 대구 지역 더미 데이터를 채워 넣는다 — SettingsScreen에서 디버그 빌드일 때만
 * 노출되는 버튼으로 호출된다. 현재 저장된 데이터는 전부 지우고 새로 채운다.
 *
 * 이 앱엔 태그 개념이 없어서(폴더로 대체됨, CLAUDE.md F6 참고) 요청받은 "태그"는 [PlaceEntity.category]에
 * 옮겨 담았다. 계정 핸들(@daegu_matjip 등)도 어느 화면에서든 표시되는 자리가 없어서(인박스 카드·게시물
 * 행 모두 URL과 저장 시각만 보여줌) [SavedPostEntity.caption]에 참고용으로만 적어뒀다 — 실제 화면엔
 * 안 보인다.
 */
@Singleton
class DaeguDummyDataSeeder @Inject constructor(
    private val database: EonjeDatabase,
    private val folderDao: FolderDao,
    private val placeDao: PlaceDao,
    private val savedPostDao: SavedPostDao,
    private val crossRefDao: PostPlaceCrossRefDao,
    private val extractedCandidateDao: ExtractedCandidateDao,
) {
    suspend fun seed() = withContext(Dispatchers.IO) {
        database.clearAllTables()

        val now = System.currentTimeMillis()

        val folderMatjip = folder("동성로 맛집", 0xFFE53935.toInt(), "🍽️", now)
        val folderBars = folder("동성로 술집", 0xFF5E35B1.toInt(), "🍺", now)
        val folderDate = folder("수성못 데이트", 0xFFD81B60.toInt(), "❤️", now)
        val folderTravel = folder("대구 여행 후보", 0xFF1E88E5.toInt(), "⭐", now)
        val folderSolo = folder("혼밥 맛집", 0xFFFB8C00.toInt(), "🏠", now)
        val folderCafe = folder("카페", 0xFF00ACC1.toInt(), "☕", now)
        listOf(folderMatjip, folderBars, folderDate, folderTravel, folderSolo, folderCafe)
            .forEach { folderDao.insert(it) }

        seedMatjipFolder(folderMatjip.id, now)

        listOf(
            place("곱창필동", "대구 남구 대명동", 35.8489, 128.5820, "곱창 · 막창", folderBars.id, now),
            place("야시골목 생맥주", "대구 중구 동성로", 35.8701, 128.5952, "포차 · 안주", folderBars.id, now),
            place("동성로 포차거리", "대구 중구 동성로2가", 35.8710, 128.5960, "노포 · 소주", folderBars.id, now),
            place("종로 닭똥집골목", "대구 중구 종로", 35.8704, 128.5978, "야식 · 닭똥집", folderBars.id, now),
        ).forEach { insertPlaceWithThumbnail(it) }

        listOf(
            place("수성못 오리배 선착장", "대구 수성구 두산동", 35.8281, 128.6238, "데이트 · 산책", folderDate.id, now),
            place("수성못 카페거리", "대구 수성구 수성동4가", 35.8300, 128.6220, "노을 · 야경", folderDate.id, now),
            place("든든이분식 수성못점", "대구 수성구 지산동", 35.8270, 128.6255, "분식 · 야시장", folderDate.id, now),
        ).forEach { insertPlaceWithThumbnail(it) }

        listOf(
            place("김광석다시그리기길", "대구 중구 대봉동", 35.8631, 128.6022, "벽화거리 · 포토스팟", folderTravel.id, now),
            place("서문시장", "대구 중구 대신동", 35.8687, 128.5820, "전통시장 · 야시장", folderTravel.id, now),
            place("대구 근대골목", "대구 중구 계산동", 35.8714, 128.5920, "근대건축 · 산책", folderTravel.id, now),
            place("앞산 전망대", "대구 남구 대명동", 35.8323, 128.5766, "야경 · 전망대", folderTravel.id, now),
            place("팔공산 케이블카", "대구 동구 신용동", 35.9502, 128.6883, "등산 · 케이블카", folderTravel.id, now),
        ).forEach { insertPlaceWithThumbnail(it) }

        listOf(
            place("반월당 혼밥식당", "대구 중구 남산동", 35.8623, 128.5904, "1인석 · 백반", folderSolo.id, now),
            place("교동든든국수", "대구 중구 교동", 35.8722, 128.5955, "국수 · 혼밥", folderSolo.id, now),
            place("대명동 돈까스", "대구 중구 대명동", 35.8500, 128.5830, "돈까스 · 혼밥", folderSolo.id, now),
        ).forEach { insertPlaceWithThumbnail(it) }

        listOf(
            place("삼덕상회", "대구 중구 삼덕동", 35.8664, 128.6014, "루프탑 · 브런치", folderCafe.id, now),
            place("대구백화점 카페거리", "대구 중구 동성로", 35.8695, 128.5940, "디저트 · 베이커리", folderCafe.id, now),
            place("북성로 대장간커피", "대구 중구 북성로", 35.8722, 128.5904, "빈티지 · 로스터리", folderCafe.id, now),
            place("수성못 스타벅스", "대구 수성구 두산동", 35.8290, 128.6230, "뷰카페", folderCafe.id, now),
        ).forEach { insertPlaceWithThumbnail(it) }

        seedInboxItems(now)
    }

    /** "동성로 맛집" 폴더 — 상세 요청이 있었던 미성당(메모·저장 출처 포함) + 추가 2곳. */
    private suspend fun seedMatjipFolder(folderId: String, now: Long) {
        val miseongdang = place(
            name = "미성당",
            address = "대구 중구 종로2가",
            lat = 35.8706,
            lng = 128.5972,
            category = "웨이팅 많음 · 점심 · 노포",
            folderId = folderId,
            createdAt = now,
            memo = "동생이 추천해준 곳. 오픈런 아니면 웨이팅 30분 각오",
        )
        placeDao.insert(miseongdang)

        val post = SavedPostEntity(
            id = UUID.randomUUID().toString(),
            shortcode = "Cdg0001",
            instagramUrl = "https://www.instagram.com/p/Cdg0001xyz/",
            caption = "@daegu_matjip 게시물에서 저장 — 대구 노포 맛집 추천",
            thumbnailUrl = SEED_THUMBNAIL_URI,
            status = ResolveStatus.RESOLVED,
            extractedCount = 1,
            createdAt = now,
        )
        savedPostDao.insert(post)
        crossRefDao.insert(PostPlaceCrossRef(post.id, miseongdang.id))

        listOf(
            place("우래옥 동성로점", "대구 중구 동성로", 35.8698, 128.5946, "평양냉면 · 노포", folderId, now),
            place("국일따로국밥", "대구 중구 태평로", 35.8697, 128.5885, "국밥 · 노포", folderId, now),
        ).forEach { insertPlaceWithThumbnail(it) }
    }

    /** 장소를 넣고, 목록/상세 화면에 썸네일이 보이도록 플레이스홀더 이미지가 달린 게시물을 하나 연결해준다. */
    private suspend fun insertPlaceWithThumbnail(place: PlaceEntity) {
        placeDao.insert(place)
        val post = SavedPostEntity(
            id = UUID.randomUUID().toString(),
            shortcode = "seed-${place.id.take(8)}",
            instagramUrl = "https://www.instagram.com/p/seed_${place.id.take(8)}/",
            caption = "${place.name} 게시물에서 저장",
            thumbnailUrl = SEED_THUMBNAIL_URI,
            status = ResolveStatus.RESOLVED,
            extractedCount = 1,
            createdAt = place.createdAt,
        )
        savedPostDao.insert(post)
        crossRefDao.insert(PostPlaceCrossRef(post.id, place.id))
    }

    /** 인박스(미확정) 3건 + 방금 자동 확정된 장소 1건. */
    private suspend fun seedInboxItems(now: Long) {
        // 1. 상호명 인식 실패 — 2시간 전
        savedPostDao.insert(
            SavedPostEntity(
                id = UUID.randomUUID().toString(),
                shortcode = "Cdg1001",
                instagramUrl = "https://www.instagram.com/p/Cdg1001abc/",
                caption = "@daegu_matjip 게시물",
                thumbnailUrl = SEED_THUMBNAIL_URI,
                status = ResolveStatus.UNRESOLVED,
                extractedCount = 0,
                createdAt = now - 2 * HOUR_MS,
                unresolvedReason = UnresolvedReason.PLACE_NOT_FOUND,
            )
        )

        // 2. 추정 장소명 "동성로 골목집" — 후보 확인 필요, 어제
        val post2Id = UUID.randomUUID().toString()
        savedPostDao.insert(
            SavedPostEntity(
                id = post2Id,
                shortcode = "Cdg1002",
                instagramUrl = "https://www.instagram.com/p/Cdg1002abc/",
                caption = "@daegu_foodie 게시물",
                thumbnailUrl = SEED_THUMBNAIL_URI,
                status = ResolveStatus.NEEDS_REVIEW,
                extractedCount = 1,
                createdAt = now - DAY_MS,
            )
        )
        extractedCandidateDao.insertAll(
            listOf(
                candidate(post2Id, 0, "동성로 골목집", 0, "동성로 골목집 본점", "대구 중구 동성로3가", 35.8708, 128.5965),
                candidate(post2Id, 0, "동성로 골목집", 1, "동성로 골목집 종로점", "대구 중구 종로1가", 35.8700, 128.5980),
            )
        )

        // 3. 리스트형 게시물 — 장소 3곳 (게시물 1개 : 장소 여럿, 다대다), 3일 전
        val post3Id = UUID.randomUUID().toString()
        savedPostDao.insert(
            SavedPostEntity(
                id = post3Id,
                shortcode = "Cdg1003",
                instagramUrl = "https://www.instagram.com/p/Cdg1003abc/",
                caption = "@daegu_nightlife 게시물",
                thumbnailUrl = SEED_THUMBNAIL_URI,
                status = ResolveStatus.NEEDS_REVIEW,
                extractedCount = 3,
                createdAt = now - 3 * DAY_MS,
            )
        )
        extractedCandidateDao.insertAll(
            listOf(
                candidate(post3Id, 0, "동성로 술집 A", 0, "동성로 술집 A", "대구 중구 동성로", 35.8703, 128.5955),
                candidate(post3Id, 1, "동성로 술집 B", 0, "동성로 술집 B", "대구 중구 동성로2가", 35.8712, 128.5962),
                candidate(post3Id, 2, "동성로 술집 C", 0, "동성로 술집 C", "대구 중구 종로", 35.8706, 128.5975),
            )
        )

        // 4. 자동 인식돼 방금 확정된 장소 — RESOLVED라 인박스가 아니라 홈(미분류)에 바로 보인다
        val post4Id = UUID.randomUUID().toString()
        val place4 = place(
            name = "안지랑 곱창골목",
            address = "대구 남구 대명동",
            lat = 35.8365,
            lng = 128.5677,
            category = "곱창 · 자동인식",
            folderId = null,
            createdAt = now - 3 * DAY_MS,
        )
        placeDao.insert(place4)
        savedPostDao.insert(
            SavedPostEntity(
                id = post4Id,
                shortcode = "Cdg1004",
                instagramUrl = "https://www.instagram.com/p/Cdg1004abc/",
                caption = "@gopchang_daegu 게시물",
                thumbnailUrl = SEED_THUMBNAIL_URI,
                status = ResolveStatus.RESOLVED,
                extractedCount = 1,
                createdAt = now - 3 * DAY_MS,
            )
        )
        crossRefDao.insert(PostPlaceCrossRef(post4Id, place4.id))
    }

    private fun folder(name: String, color: Int, icon: String, now: Long) = FolderEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        createdAt = now,
        color = color,
        iconKey = icon,
    )

    private fun place(
        name: String,
        address: String,
        lat: Double,
        lng: Double,
        category: String,
        folderId: String?,
        createdAt: Long,
        memo: String? = null,
    ) = PlaceEntity(
        id = UUID.randomUUID().toString(),
        name = name,
        address = address,
        latitude = lat,
        longitude = lng,
        kakaoPlaceId = "seed-kakao-${UUID.randomUUID()}",
        category = category,
        memo = memo,
        status = ResolveStatus.RESOLVED,
        createdAt = createdAt,
        resolvedAt = createdAt,
        folderId = folderId,
    )

    private fun candidate(
        postId: String,
        extractionIndex: Int,
        extractedName: String,
        rank: Int,
        name: String,
        address: String,
        lat: Double,
        lng: Double,
    ) = ExtractedCandidateEntity(
        postId = postId,
        extractionIndex = extractionIndex,
        extractedName = extractedName,
        rank = rank,
        kakaoPlaceId = "seed-kakao-${UUID.randomUUID()}",
        name = name,
        address = address,
        category = null,
        latitude = lat,
        longitude = lng,
    )
}
