package com.github.chsssssss.eonje.data.firestore

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsssssss.eonje.domain.model.ExtractedCandidate
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.domain.model.FolderVisuals
import com.github.chsssssss.eonje.domain.model.Place
import com.github.chsssssss.eonje.domain.model.PlaceCandidate
import com.github.chsssssss.eonje.domain.model.ResolveStatus
import com.github.chsssssss.eonje.domain.model.SavedPost
import com.github.chsssssss.eonje.domain.model.UnresolvedReason
import com.google.firebase.firestore.DocumentReference
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Source
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import com.google.firebase.Timestamp
import java.util.Date
import org.junit.Assert.fail
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * firestore.rules를 Emulator Suite(`scripts/start-emulators.sh`, firebase.json이 firestore.rules를 로드한다)에서 확인한다.
 *
 * **정상 문서는 하드코딩하지 않고 앱이 쓰는 매퍼(FirestoreMappers의 toFields/toMap)가 만든 것을 그대로 쓴다.** 그래서 앱의
 * 필드가 바뀌면 규칙과 어긋나는 즉시 이 테스트가 깨진다. 거부 케이스는 매퍼 출력에 한 군데만 망가뜨려서 만든다.
 * 쓰기는 서버 확정까지 await한다 — 거부되면 PERMISSION_DENIED로 실패한다.
 */
@RunWith(AndroidJUnit4::class)
class FirestoreRulesTest {
    private val env = FirebaseEmulatorEnv
    private val firestore get() = env.firestore
    private val auth get() = env.auth
    private lateinit var uid: String

    @Before
    fun setUp() = runBlocking<Unit> {
        assumeTrue("Firebase Emulator Suite가 호스트에서 실행 중이어야 한다", env.reachable())
        firestore.enableNetwork().await()
        auth.signOut()
        uid = auth.signInAnonymously().await().user!!.uid
    }

    @After
    fun tearDown() {
        if (env.isStarted) runCatching { runBlocking { firestore.enableNetwork().await() } }
    }

    // ── 정상: 앱이 만드는 문서는 전부 통과한다 ──

    @Test
    fun 본인_데이터는_읽기_쓰기_삭제가_모두_허용된다() = runBlocking<Unit> {
        val folder = firestore.folders(uid).document("folder-1")
        val place = firestore.places(uid).document("place-1")
        val post = firestore.posts(uid).document("post-1")

        folder.set(folder().toFields()).await()
        place.set(place().toFields()).await()
        post.set(post().toFields(listOf("place-1"), listOf(candidate("post-1", 0, 0)))).await()

        assertTrue(folder.get(Source.SERVER).await().exists())
        assertTrue(place.get(Source.SERVER).await().exists())
        assertEquals(1, firestore.posts(uid).get(Source.SERVER).await().size())

        place.update(placeUpdate("folderId" to "folder-1")).await()
        post.update("status", ResolveStatus.RESOLVED.name).await()

        folder.delete().await(); place.delete().await(); post.delete().await()
        assertFalse(post.get(Source.SERVER).await().exists())
    }

    @Test
    fun 앱이_만드는_모든_status와_unresolvedReason_조합이_통과한다() = runBlocking<Unit> {
        // enum에 값을 추가하고 규칙을 안 고치면 여기서 깨진다.
        for (status in ResolveStatus.values()) {
            firestore.places(uid).document("p-${status.name}").set(place(status = status).toFields()).await()
            for (reason in listOf<UnresolvedReason?>(null) + UnresolvedReason.values()) {
                val id = "s-${status.name}-${reason?.name}"
                firestore.posts(uid).document(id).set(post(status = status, reason = reason).toFields(emptyList(), emptyList())).await()
            }
        }
        for (reason in UnresolvedReason.values()) {
            // 상태 변경은 이 두 필드만 update한다(FirestoreSavedPostRepository.updateStatus)
            val ref = firestore.posts(uid).document("s-${ResolveStatus.UNRESOLVED.name}-null")
            ref.update(mapOf("status" to ResolveStatus.UNRESOLVED.name, "unresolvedReason" to reason.name, "extractedCount" to 0)).await()
        }
    }

    @Test
    fun 이전의_프로브_문서와_고쳐서_옮긴_문서는_규칙을_통과한다() = runBlocking<Unit> {
        // 프로브: 앱의 매퍼가 만든 문서 그대로
        firestore.posts(uid).document(com.github.chsssssss.eonje.data.migration.RoomToFirestoreMigrator.PROBE_ID)
            .set(com.github.chsssssss.eonje.data.migration.RoomToFirestoreMigrator.probeFields(1_700_000_000_000L)).await()
        firestore.posts(uid).document(com.github.chsssssss.eonje.data.migration.RoomToFirestoreMigrator.PROBE_ID).delete().await()
        // 보정기가 만든 값은 상한 경계(이름 100자·200자·300자, 캡션 20,000자)이고 규칙이 그대로 받는다
        val folder = com.github.chsssssss.eonje.data.migration.LegacyDocSanitizer.folder(folder(name = "가".repeat(101))).value
        firestore.folders(uid).document("f").set(folder.toFields()).await()
        val unnamed = com.github.chsssssss.eonje.data.migration.LegacyDocSanitizer.folder(folder(name = "  ")).value
        firestore.folders(uid).document("f2").set(unnamed.toFields()).await()
        val p = com.github.chsssssss.eonje.data.migration.LegacyDocSanitizer.place(
            place().copy(name = "가".repeat(999), address = "나".repeat(999), category = "다".repeat(999)),
        ).value
        firestore.places(uid).document("p").set(p.toFields()).await()
        val post = com.github.chsssssss.eonje.data.migration.LegacyDocSanitizer.post(post().copy(caption = "가".repeat(99_999))).value
        firestore.posts(uid).document("q").set(post.toFields(emptyList(), emptyList())).await()
    }

    @Test
    fun 선택_필드가_전부_null이거나_없어도_통과한다() = runBlocking<Unit> {
        val bare = Place("bare", null, null, null, null, null, null, ResolveStatus.PENDING, 1L, null, null)
        firestore.places(uid).document("bare").set(bare.toFields()).await()
        firestore.posts(uid).document("bare").set(post(shortcode = null, caption = null).toFields(emptyList(), emptyList())).await()
        // 후보·연결이 없는 게시물은 placeIds/candidates 필드 자체가 없다(이전·신규 공통)
        assertFalse(post().toFields(emptyList(), emptyList()).containsKey("placeIds"))
    }

    @Test
    fun 폴더_팔레트의_모든_색과_아이콘_그리고_한글_이름이_통과한다() = runBlocking<Unit> {
        FolderVisuals.colorPalette.forEachIndexed { i, color ->
            val icon = FolderVisuals.iconOptions[i % FolderVisuals.iconOptions.size]
            firestore.folders(uid).document("f$i").set(folder(name = "성수 맛집 $i", color = color, icon = icon).toFields()).await()
        }
        firestore.folders(uid).document("def").set(Folder("def", "기본", 1L).toFields()).await()
    }

    @Test
    fun 부분_update와_배열_연산과_WriteBatch가_통과한다() = runBlocking<Unit> {
        val post = firestore.posts(uid).document("p")
        post.set(post().toFields()).await()

        post.update("placeIds", FieldValue.arrayUnion("place-1")).await()
        post.update("placeIds", FieldValue.arrayUnion("place-2")).await()
        post.update("placeIds", FieldValue.arrayRemove("place-1")).await()
        post.update("candidates", listOf(candidate("p", 0, 0).toMap(), candidate("p", 0, 1).toMap())).await()
        post.update("candidates", emptyList<Any>()).await()
        post.update(mapOf("caption" to "새 캡션", "extractedCount" to 2)).await()

        val batch = firestore.batch()
        (1..30).forEach { n -> batch.set(firestore.places(uid).document("bp$n"), place(id = "bp$n").toFields()) }
        (1..30).forEach { n -> batch.update(firestore.places(uid).document("bp$n"), "folderId", null) }
        batch.update(post, "placeIds", FieldValue.arrayRemove("place-2"))
        batch.delete(firestore.places(uid).document("bp1"))
        batch.commit().await()
        assertEquals(29, firestore.places(uid).get(Source.SERVER).await().size())
    }

    // ── 거부: 접근 ──

    @Test
    fun 다른_uid의_데이터는_읽기_쓰기_삭제_목록이_모두_거부된다() = runBlocking<Unit> {
        val mine = uid
        firestore.places(mine).document("secret").set(place(id = "secret").toFields()).await()
        firestore.posts(mine).document("secret").set(post().toFields()).await()

        auth.signOut()
        val intruder = auth.signInAnonymously().await().user!!.uid
        assertTrue(intruder != mine)

        assertDenied("남의 문서 읽기") { firestore.places(mine).document("secret").get(Source.SERVER).await() }
        assertDenied("남의 컬렉션 목록") { firestore.posts(mine).get(Source.SERVER).await() }
        assertDenied("남의 경로에 쓰기") { firestore.places(mine).document("x").set(place(id = "x").toFields()).await() }
        assertDenied("남의 문서 수정") { firestore.places(mine).document("secret").update("name", "해킹").await() }
        assertDenied("남의 문서 삭제") { firestore.posts(mine).document("secret").delete().await() }
        // 내 경로는 여전히 된다
        firestore.places(intruder).document("mine").set(place(id = "mine").toFields()).await()
    }

    @Test
    fun 로그인하지_않으면_읽기와_쓰기가_거부된다() = runBlocking<Unit> {
        firestore.places(uid).document("p").set(place().toFields()).await()
        val owner = uid
        auth.signOut()

        assertDenied("비로그인 읽기") { firestore.places(owner).document("p").get(Source.SERVER).await() }
        assertDenied("비로그인 목록") { firestore.places(owner).get(Source.SERVER).await() }
        assertDenied("비로그인 쓰기") { firestore.places(owner).document("q").set(place(id = "q").toFields()).await() }
        assertDenied("비로그인 삭제") { firestore.places(owner).document("p").delete().await() }
    }

    @Test
    fun users_밖의_경로와_허용하지_않은_하위_경로는_거부된다() = runBlocking<Unit> {
        val ok = mapOf("a" to 1)
        assertDenied("최상위 다른 컬렉션 쓰기") { firestore.collection("public").document("x").set(ok).await() }
        assertDenied("최상위 다른 컬렉션 읽기") { firestore.collection("public").document("x").get(Source.SERVER).await() }
        assertDenied("users 컬렉션 목록") { firestore.collection("users").get(Source.SERVER).await() }
        assertDenied("users/{uid} 문서 자체 쓰기") { firestore.userDoc(uid).set(ok).await() }
        assertDenied("users/{uid} 문서 자체 읽기") { firestore.userDoc(uid).get(Source.SERVER).await() }
        assertDenied("허용하지 않은 컬렉션") { firestore.userDoc(uid).collection("secrets").document("x").set(ok).await() }
        assertDenied("posts 하위 컬렉션") { firestore.posts(uid).document("p").collection("sub").document("y").set(ok).await() }
        assertDenied("공유용 최상위 컬렉션은 아직 없다") { firestore.collection("shares").document("x").set(ok).await() }
    }

    // ── 연결된(비익명) 사용자 ──

    @Test
    fun 계정을_연결한_사용자도_같은_uid로_같은_규칙을_통과하고_다른_사용자는_여전히_막힌다() = runBlocking<Unit> {
        place("before-link", place().toFields())
        val token = """{"sub":"rules-linked-${System.nanoTime()}","email":"rules-${System.nanoTime()}@example.com","email_verified":true}"""
        auth.currentUser!!.linkWithCredential(com.google.firebase.auth.GoogleAuthProvider.getCredential(token, null)).await()
        auth.currentUser!!.getIdToken(true).await() // 요청의 auth 토큰이 비익명(provider=google.com)으로 바뀐 걸 반영
        assertEquals("연결은 uid를 바꾸지 않는다", uid, auth.currentUser!!.uid)
        assertFalse(auth.currentUser!!.isAnonymous)

        // 연결 전에 쓴 문서를 읽고, 연결 뒤에도 앱이 만드는 모든 종류의 문서를 쓸 수 있다
        assertTrue(firestore.places(uid).document("before-link").get(Source.SERVER).await().exists())
        place("after-link", place().toFields())
        post("after-link", post().toFields(listOf("after-link"), listOf(candidate("after-link", 0, 0))))
        folder("after-link", folder().toFields())
        firestore.places(uid).document("after-link").update(placeUpdate("folderId" to "f")).await()
        // 남의 경로는 연결된 사용자도 막힌다
        assertDenied("남의 경로에 쓰기") { firestore.places("someone-else").document("x").set(place().toFields()).await() }
        assertDenied("남의 문서 읽기") { firestore.places("someone-else").document("x").get(Source.SERVER).await() }
    }

    // ── 거부: 문서 모양 ──

    @Test
    fun 허용되지_않은_필드는_거부된다() = runBlocking<Unit> {
        val goodPost = post().toFields(emptyList(), emptyList())
        assertDenied("게시물에 thumbnailUrl(file:// 경로는 저장하지 않는다)") { post("a", goodPost + ("thumbnailUrl" to "file:///x.jpg")) }
        assertDenied("게시물에 임의 필드") { post("a", goodPost + ("isAdmin" to true)) }
        assertDenied("장소에 memo(옮기지 않는 필드)") { place("a", place().toFields() + ("memo" to "비밀")) }
        assertDenied("폴더에 임의 필드") { folder("a", folder().toFields() + ("owner" to "x")) }
        assertDenied("update로 필드 추가") {
            firestore.posts(uid).document("ok").set(goodPost).await()
            firestore.posts(uid).document("ok").update("extra", 1).await()
        }
    }

    @Test
    fun 잘못된_status와_unresolvedReason은_거부된다() = runBlocking<Unit> {
        val good = post().toFields(emptyList(), emptyList())
        assertDenied("소문자 status") { post("a", good + ("status" to "resolved")) }
        assertDenied("없는 status") { post("a", good + ("status" to "DONE")) }
        assertDenied("숫자 status") { post("a", good + ("status" to 1)) }
        assertDenied("없는 unresolvedReason") { post("a", good + ("unresolvedReason" to "BECAUSE")) }
        assertDenied("장소의 없는 status") { place("a", place().toFields() + ("status" to "GONE")) }
        firestore.posts(uid).document("ok").set(good).await()
        assertDenied("update로 잘못된 status") { firestore.posts(uid).document("ok").update("status", "NOPE").await() }
    }

    @Test
    fun 장소는_updatedAt을_서버_시각으로_써야_하고_묘비는_허용된다() = runBlocking<Unit> {
        val good = place().toFields()
        place("ok", good)
        // updatedAt이 없거나, 서버 시각이 아니면(기기가 만든 값) 거부 — 델타 동기화가 그 변경을 놓치지 않게
        assertDenied("updatedAt 없음") { place("a", good - "updatedAt") }
        assertDenied("기기가 만든 updatedAt(과거 시각)") { place("a", good + ("updatedAt" to Timestamp(Date(1_000L)))) }
        assertDenied("숫자 updatedAt") { place("a", good + ("updatedAt" to 1L)) }
        assertDenied("updatedAt 없이 update(이전 값이 그대로 남는다)") { firestore.places(uid).document("ok").update("folderId", "f").await() }
        firestore.places(uid).document("ok").update(placeUpdate("folderId" to "f")).await() // updatedAt과 함께면 통과

        // 묘비: 삭제 표시 + 만료 시각. kakaoPlaceId는 남긴다
        place("tomb", tombstoneFields("k-1", 1_700_000_000_000L))
        val tomb = firestore.places(uid).document("tomb").get(Source.SERVER).await()
        assertTrue(tomb.isTombstone())
        assertNull(tomb.toPlaceOrNull())
        assertDenied("deleted 없이 expireAt") { place("a", good + ("expireAt" to Timestamp(Date(2_000_000_000_000L)))) }
        assertDenied("deleted가 bool이 아님") { place("a", good + ("deleted" to "yes")) }
        assertDenied("expireAt이 timestamp가 아님") { place("a", tombstoneFields("k-1", 1L) + ("expireAt" to "내일")) }
    }

    @Test
    fun 길이와_크기_상한을_넘으면_거부된다() = runBlocking<Unit> {
        val goodPost = post().toFields(emptyList(), emptyList())
        assertDenied("폴더 이름 101자") { folder("a", folder().toFields() + ("name" to "가".repeat(101))) }
        assertDenied("빈 폴더 이름") { folder("a", folder().toFields() + ("name" to "")) }
        assertDenied("폴더 아이콘 33자") { folder("a", folder().toFields() + ("iconKey" to "x".repeat(33))) }
        assertDenied("장소 이름 201자") { place("a", place().toFields() + ("name" to "가".repeat(201))) }
        assertDenied("장소 주소 301자") { place("a", place().toFields() + ("address" to "가".repeat(301))) }
        assertDenied("캡션 20001자") { post("a", goodPost + ("caption" to "가".repeat(20_001))) }
        assertDenied("URL 501자") { post("a", goodPost + ("instagramUrl" to "https://www.instagram.com/p/" + "a".repeat(500))) }
        assertDenied("placeIds 201개") { post("a", goodPost + ("placeIds" to (1..201).map { "p$it" })) }
        assertDenied("candidates 501개") { post("a", goodPost + ("candidates" to (1..501).map { candidate("a", it, 0).toMap() })) }
        assertDenied("placeIds가 배열이 아님") { post("a", goodPost + ("placeIds" to "place-1")) }
        assertDenied("장소 postIds 201개") { place("a", place().toFields() + ("postIds" to (1..201).map { "p$it" })) }
        assertDenied("장소 postIds가 배열이 아님") { place("a", place().toFields() + ("postIds" to "post-1")) }
        // 경계값은 통과: 캡션 20000자, placeIds 200개, 장소 이름 200자
        post("edge", goodPost + ("caption" to "가".repeat(20_000)) + ("placeIds" to (1..200).map { "p$it" }))
        place("edge", place().toFields() + ("name" to "가".repeat(200)))
        place("edge-post-ids", place().copy(postIds = (1..200).map { "p$it" }).toFields())
    }

    @Test
    fun 좌표_범위와_숫자_시간_타입이_틀리면_거부된다() = runBlocking<Unit> {
        val good = place().toFields()
        assertDenied("위도 91") { place("a", good + ("latitude" to 91.0)) }
        assertDenied("경도 -181") { place("a", good + ("longitude" to -181.0)) }
        assertDenied("위도가 문자열") { place("a", good + ("latitude" to "37.5")) }
        assertDenied("createdAt이 문자열") { place("a", good + ("createdAt" to "어제")) }
        assertDenied("resolvedAt이 문자열") { place("a", good + ("resolvedAt" to "x")) }
        assertDenied("게시물 createdAt이 문자열") { post("a", post().toFields(emptyList(), emptyList()) + ("createdAt" to "x")) }
        assertDenied("extractedCount 음수") { post("a", post().toFields(emptyList(), emptyList()) + ("extractedCount" to -1)) }
        assertDenied("폴더 color가 문자열") { folder("a", folder().toFields() + ("color" to "red")) }
        // 경계값은 통과
        place("edge1", good + ("latitude" to 90.0) + ("longitude" to -180.0))
        place("edge2", good + ("latitude" to -90) + ("longitude" to 180))
    }

    @Test
    fun 필수_필드가_빠지거나_문서_ID가_올바르지_않으면_거부된다() = runBlocking<Unit> {
        assertDenied("게시물 instagramUrl 없음") { post("a", post().toFields(emptyList(), emptyList()) - "instagramUrl") }
        assertDenied("게시물 status 없음") { post("a", post().toFields(emptyList(), emptyList()) - "status") }
        assertDenied("장소 status 없음") { place("a", place().toFields() - "status") }
        assertDenied("폴더 name 없음") { folder("a", folder().toFields() - "name") }
        assertDenied("공백이 든 문서 ID") { place("bad id", place().toFields()) }
        assertDenied("너무 긴 문서 ID") { place("a".repeat(101), place().toFields()) }
    }

    @Test
    fun WriteBatch는_하나라도_거부되면_전체가_거부된다() = runBlocking<Unit> {
        val batch = firestore.batch()
        batch.set(firestore.places(uid).document("ok-1"), place(id = "ok-1").toFields())
        batch.set(firestore.places(uid).document("bad"), place(id = "bad").toFields() + ("status" to "NOPE"))
        assertDenied("배치 안의 잘못된 문서") { batch.commit().await() }
        assertFalse("나머지 문서도 만들어지지 않는다", firestore.places(uid).document("ok-1").get(Source.SERVER).await().exists())
    }

    // ── 도구 ──

    private suspend fun place(id: String, fields: Map<String, Any?>) = firestore.places(uid).document(id).set(fields).await()
    private suspend fun post(id: String, fields: Map<String, Any?>) = firestore.posts(uid).document(id).set(fields).await()
    private suspend fun folder(id: String, fields: Map<String, Any?>) = firestore.folders(uid).document(id).set(fields).await()

    private suspend fun assertDenied(what: String, block: suspend () -> Unit) {
        try {
            block()
        } catch (e: FirebaseFirestoreException) {
            assertEquals("$what — PERMISSION_DENIED여야 한다: ${e.message}", FirebaseFirestoreException.Code.PERMISSION_DENIED, e.code)
            return
        }
        fail("거부돼야 하는데 통과했다: $what")
    }

    private fun folder(name: String = "성수 맛집", color: Int = FolderVisuals.DEFAULT_COLOR, icon: String = FolderVisuals.DEFAULT_ICON) =
        Folder(id = "folder-1", name = name, createdAt = 1_700_000_000_000L, color = color, iconKey = icon)

    private fun place(id: String = "place-1", status: ResolveStatus = ResolveStatus.RESOLVED) = Place(
        id = id, name = "성수칼국수 본점", address = "서울 성동구 성수동 1", latitude = 37.5446, longitude = 127.0557,
        kakaoPlaceId = "k-1", category = "음식점 > 한식", status = status, createdAt = 1_700_000_000_000L,
        resolvedAt = 1_700_000_000_500L, folderId = null,
    )

    private fun post(
        status: ResolveStatus = ResolveStatus.PENDING,
        reason: UnresolvedReason? = null,
        shortcode: String? = "ABC123",
        caption: String? = "캡션",
    ) = SavedPost(
        id = "post-1", shortcode = shortcode, instagramUrl = "https://www.instagram.com/p/ABC123/", caption = caption,
        thumbnailUrl = "file:///local/only.jpg", // 문서엔 안 들어간다(toFields가 빼는지도 함께 확인된다)
        status = status, extractedCount = 1, createdAt = 1_700_000_000_000L, unresolvedReason = reason,
    )

    private fun candidate(postId: String, index: Int, rank: Int) = ExtractedCandidate(
        postId = postId, extractionIndex = index, extractedName = "추출 $index", rank = rank,
        place = PlaceCandidate("k-$index-$rank", "후보 $index-$rank", "서울 마포구 $rank", "음식점 > 양식", 37.5 + rank * 0.001, 127.0 + index * 0.001),
    )

    @Suppress("unused")
    private fun DocumentReference.unused() = Unit
}
