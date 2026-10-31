package com.github.chsssssss.eonje.widget

import android.content.Context
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.random.Random

/** 오늘 위젯에 보여줄 장소. [previousPlaceId]는 직전 날짜에 보여준 장소 — 이틀 연속 같은 곳이 나오지 않게 제외한다. */
internal data class TodayPick(
    val date: String,
    val placeId: String,
    val previousPlaceId: String?,
)

/**
 * 순수 선택 로직(테스트 대상). 같은 날짜에 이미 고른 장소가 아직 후보에 있으면 그대로 유지하고,
 * 날짜가 바뀌었거나 고른 장소가 삭제됐으면 새로 고른다. 후보가 하나뿐이면 연속이어도 어쩔 수 없이 그걸 쓴다.
 */
internal fun chooseTodayPick(
    candidateIds: List<String>,
    today: String,
    stored: TodayPick?,
    random: Random = Random.Default,
): TodayPick? {
    if (candidateIds.isEmpty()) return null
    if (stored != null && stored.date == today && stored.placeId in candidateIds) return stored

    // 오늘 이미 골랐는데 그 장소가 삭제된 경우엔 "어제 장소"를 그대로 이어받는다.
    val yesterdayPlaceId = if (stored?.date == today) stored.previousPlaceId else stored?.placeId
    val pool = candidateIds.filter { it != yesterdayPlaceId }.ifEmpty { candidateIds }
    return TodayPick(date = today, placeId = pool.random(random), previousPlaceId = yesterdayPlaceId)
}

/**
 * 주소에서 동네 이름을 뽑는다. 지번 주소면 동·읍·면, 도로명 주소(저장값 대부분)엔 동이 없어서 구·군, 그다음 시.
 * 예) "서울 성동구 성수이로 7길 10" → "성동구", "서울 성동구 성수동2가 300" → "성수동2가"
 */
internal fun neighborhoodOf(address: String?): String? {
    val tokens = address?.split(Regex("\\s+"))?.filter { it.isNotBlank() }.orEmpty()
    return tokens.firstOrNull { DONG.matches(it) }
        ?: tokens.firstOrNull { GU.matches(it) }
        ?: tokens.firstOrNull { SI.matches(it) }
}

private val DONG = Regex("^[가-힣]+(동|읍|면)$|^[가-힣]+동\\d*가$")
private val GU = Regex("^[가-힣]+(구|군)$")
private val SI = Regex("^[가-힣]+시$")

/**
 * 오늘의 선택을 기기에 저장한다 — 위젯이 다시 그려져도(앱에서 장소 저장 등) 같은 날엔 같은 장소가 유지되도록.
 * 위젯 인스턴스가 여러 개면 provideGlance가 동시에 돌 수 있어서 읽기-선택-쓰기를 Mutex로 묶는다.
 */
internal object TodayPickStore {
    private const val PREFS = "today_place_widget"
    private const val KEY_DATE = "date"
    private const val KEY_PLACE_ID = "place_id"
    private const val KEY_PREVIOUS_PLACE_ID = "previous_place_id"

    private val mutex = Mutex()

    suspend fun resolve(context: Context, candidateIds: List<String>, today: String): TodayPick? =
        mutex.withLock {
            val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            val stored = run {
                val date = prefs.getString(KEY_DATE, null) ?: return@run null
                val placeId = prefs.getString(KEY_PLACE_ID, null) ?: return@run null
                TodayPick(date, placeId, prefs.getString(KEY_PREVIOUS_PLACE_ID, null))
            }
            val pick = chooseTodayPick(candidateIds, today, stored)
            if (pick != null && pick != stored) {
                prefs.edit()
                    .putString(KEY_DATE, pick.date)
                    .putString(KEY_PLACE_ID, pick.placeId)
                    .putString(KEY_PREVIOUS_PLACE_ID, pick.previousPlaceId)
                    .apply()
            }
            pick
        }
}
