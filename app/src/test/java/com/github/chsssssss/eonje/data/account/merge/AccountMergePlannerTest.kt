package com.github.chsssssss.eonje.data.account.merge

import com.github.chsssssss.eonje.domain.util.ShareKey
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountMergePlannerTest {

    private fun folder(id: String, name: String, createdAt: Long = 1L) = FolderSnap(id, name, 0xFF1E88E5.toInt(), "☕", createdAt)

    private fun place(id: String, kakao: String? = "k-$id", folderId: String? = null, postIds: List<String> = emptyList(), createdAt: Long = 1L) =
        PlaceSnap(id, "가게 $id", "서울", 37.5, 127.0, kakao, "음식점", "RESOLVED", createdAt, 2L, folderId, postIds)

    private fun post(id: String, shortcode: String? = "SC-$id", placeIds: List<String> = emptyList(), candidates: List<CandidateSnap> = emptyList(), url: String = "https://www.instagram.com/p/${shortcode ?: id}/") =
        PostSnap(id, shortcode, url, "캡션 $id", "RESOLVED", 0, 1L, null, placeIds, candidates)

    private fun candidate(rank: Int) = CandidateSnap(0, "후보", rank, "kc-$rank", "후보 $rank", "주소", null, 37.5, 127.0)

    private fun targetPost(id: String, shortcode: String?, placeIds: Set<String> = emptySet(), url: String = "https://www.instagram.com/p/${shortcode ?: id}/") =
        TargetPost(id, ShareKey.of(shortcode, url), placeIds)

    // ── 게시물: 문서 ID가 아니라 shortcode(없으면 u- 해시 키)로 중복 판단 ──

    @Test
    fun 같은_shortcode가_양쪽에_있으면_문서_ID가_달라도_계정_쪽을_쓰고_익명_쪽은_버린다() {
        val local = MergeSnapshot(posts = listOf(post("anon-1", shortcode = "SAME"), post("anon-2", shortcode = "ONLY-LOCAL")))
        val target = TargetSnapshot(posts = listOf(targetPost("acct-uuid-9", "SAME"))) // 문서 ID가 다르다(Room에서 옮긴 게시물 등)

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(listOf("anon-2"), plan.postWrites.map { it.id })
        assertEquals(1, plan.discardedPostCount)
        assertEquals(listOf(ThumbnailAdoption("anon-1", "acct-uuid-9")), plan.thumbnailAdoptions)
    }

    @Test
    fun 같은_shortcode이고_문서_ID도_같으면_버리되_썸네일_옮기기는_필요_없다() {
        val local = MergeSnapshot(posts = listOf(post("same-id", shortcode = "SAME")))
        val target = TargetSnapshot(posts = listOf(targetPost("same-id", "SAME")))

        val plan = AccountMergePlanner.plan(local, target)

        assertTrue(plan.postWrites.isEmpty())
        assertEquals(1, plan.discardedPostCount)
        assertTrue(plan.thumbnailAdoptions.isEmpty())
    }

    @Test
    fun shortcode가_없으면_URL_해시_키로_같은_게시물을_찾는다() {
        val url = "https://example.com/some-post"
        val local = MergeSnapshot(posts = listOf(post("a", shortcode = null, url = url), post("b", shortcode = null, url = "https://example.com/other")))
        val target = TargetSnapshot(posts = listOf(targetPost("t", shortcode = null, url = url)))

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(listOf("b"), plan.postWrites.map { it.id })
    }

    @Test
    fun 게시물은_후보를_포함해_통째로_옮긴다() {
        val local = MergeSnapshot(posts = listOf(post("p1", candidates = listOf(candidate(0), candidate(1), candidate(2)))))

        val plan = AccountMergePlanner.plan(local, TargetSnapshot())

        assertEquals(3, plan.postWrites.single().candidates.size)
        assertEquals(listOf(0, 1, 2), plan.postWrites.single().candidates.map { it.rank })
    }

    // ── 폴더 ──

    @Test
    fun 폴더는_이름이_같으면_계정_쪽을_쓰고_장소의_folderId를_다시_매핑한다() {
        val local = MergeSnapshot(
            folders = listOf(folder("f-anon", "성수 맛집"), folder("f-new", "회사 근처", createdAt = 2L)),
            places = listOf(place("p1", folderId = "f-anon"), place("p2", folderId = "f-new")),
        )
        val target = TargetSnapshot(folders = listOf(TargetFolder("f-acct", "성수 맛집")))

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(listOf("f-new"), plan.folderWrites.map { it.id })
        assertEquals("f-acct", plan.placeWrites.first { it.id == "p1" }.folderId)
        assertEquals("f-new", plan.placeWrites.first { it.id == "p2" }.folderId)
    }

    @Test
    fun 폴더_ID가_계정의_다른_폴더와_겹치면_결정적인_새_ID를_쓴다() {
        val local = MergeSnapshot(folders = listOf(folder("dup", "새 폴더")))
        val target = TargetSnapshot(folders = listOf(TargetFolder("dup", "다른 이름")))

        val first = AccountMergePlanner.plan(local, target)
        val second = AccountMergePlanner.plan(local, target)

        assertNotEquals("dup", first.folderWrites.single().id)
        assertEquals("다시 계산해도 같은 ID", first.folderWrites.single().id, second.folderWrites.single().id)
    }

    // ── 장소 ──

    @Test
    fun 장소는_kakaoPlaceId가_같으면_계정_쪽을_쓰고_새_장소만_만든다() {
        val local = MergeSnapshot(places = listOf(place("p-same", kakao = "K1"), place("p-new", kakao = "K2")))
        val target = TargetSnapshot(places = listOf(TargetPlace("acct-place", "K1", deleted = false)))

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(listOf("p-new"), plan.placeWrites.map { it.id })
    }

    @Test
    fun 같은_kakaoPlaceId의_묘비는_그_문서를_되살린다() {
        val local = MergeSnapshot(places = listOf(place("p-local", kakao = "K1")))
        val target = TargetSnapshot(places = listOf(TargetPlace("tomb-id", "K1", deleted = true)))

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(listOf("tomb-id"), plan.placeWrites.map { it.id })
        assertEquals("가게 p-local", plan.placeWrites.single().name)
    }

    @Test
    fun kakaoPlaceId가_없으면_항상_새로_만들고_ID가_겹치면_결정적인_새_ID를_쓴다() {
        val local = MergeSnapshot(places = listOf(place("dup", kakao = null)))
        val target = TargetSnapshot(places = listOf(TargetPlace("dup", null, deleted = false)))

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(1, plan.placeWrites.size)
        assertNotEquals("dup", plan.placeWrites.single().id)
        assertEquals(plan.placeWrites.single().id, AccountMergePlanner.plan(local, target).placeWrites.single().id)
    }

    // ── 연결 매핑 ──

    @Test
    fun 새_장소의_postIds와_살아남는_게시물의_placeIds가_서로_일관된다() {
        val local = MergeSnapshot(
            places = listOf(place("p1", postIds = listOf("s1"))),
            posts = listOf(post("s1", placeIds = listOf("p1"))),
        )

        val plan = AccountMergePlanner.plan(local, TargetSnapshot())

        assertEquals(listOf("s1"), plan.placeWrites.single().postIds)
        assertEquals(listOf("p1"), plan.postWrites.single().placeIds)
    }

    @Test
    fun 계정에_이미_있는_장소로_매핑되면_게시물의_placeIds가_계정_장소_ID가_되고_장소에_연결을_더한다() {
        val local = MergeSnapshot(
            places = listOf(place("p-anon", kakao = "K1", postIds = listOf("s1"))),
            posts = listOf(post("s1", placeIds = listOf("p-anon"))),
        )
        val target = TargetSnapshot(places = listOf(TargetPlace("p-acct", "K1", deleted = false)))

        val plan = AccountMergePlanner.plan(local, target)

        assertTrue(plan.placeWrites.isEmpty())
        assertEquals(listOf("p-acct"), plan.postWrites.single().placeIds)
        assertEquals(listOf(PlaceLink("p-acct", listOf("s1"))), plan.placeLinks)
    }

    @Test
    fun 버려진_게시물은_새_장소에_연결하지_않는다() {
        // 익명 게시물 s-dup은 계정에 같은 shortcode가 있어서 버려진다. 계정 쪽 게시물은 건드리지 않으므로 새 장소에 연결할 수 없다.
        val local = MergeSnapshot(
            places = listOf(place("p1", postIds = listOf("s-dup", "s-keep"))),
            posts = listOf(post("s-dup", shortcode = "DUP", placeIds = listOf("p1")), post("s-keep", placeIds = listOf("p1"))),
        )
        val target = TargetSnapshot(posts = listOf(targetPost("t-dup", "DUP")))

        val plan = AccountMergePlanner.plan(local, target)

        assertEquals(listOf("s-keep"), plan.placeWrites.single().postIds)
        assertEquals(listOf("s-keep"), plan.postWrites.map { it.id })
        assertFalse(plan.placeWrites.single().postIds.contains("t-dup"))
    }

    @Test
    fun 장소_쪽에만_있는_연결은_살아남는_게시물에_채워진다() {
        val local = MergeSnapshot(
            places = listOf(place("p1", postIds = listOf("s1"))),
            posts = listOf(post("s1", placeIds = emptyList())),
        )

        val plan = AccountMergePlanner.plan(local, TargetSnapshot())

        assertEquals(listOf("p1"), plan.postWrites.single().placeIds)
    }

    @Test
    fun 같은_입력이면_같은_계획이_나온다() {
        val local = MergeSnapshot(
            folders = listOf(folder("f1", "A"), folder("f2", "B")),
            places = listOf(place("p1", folderId = "f1", postIds = listOf("s1")), place("p2")),
            posts = listOf(post("s1", placeIds = listOf("p1")), post("s2")),
        )
        val target = TargetSnapshot(
            folders = listOf(TargetFolder("tf", "B")),
            places = listOf(TargetPlace("tp", "k-p2", deleted = false)),
            posts = listOf(targetPost("ts", "SC-s2")),
        )

        assertEquals(AccountMergePlanner.plan(local, target), AccountMergePlanner.plan(local, target))
    }

    @Test
    fun 로컬_안에서_같은_kakaoPlaceId와_같은_게시물_키가_겹쳐도_한_번만_쓴다() {
        val local = MergeSnapshot(
            places = listOf(place("p1", kakao = "K", createdAt = 1L), place("p2", kakao = "K", createdAt = 2L)),
            posts = listOf(post("a", shortcode = "S", placeIds = listOf("p1")), post("b", shortcode = "S", placeIds = listOf("p2"))),
        )

        val plan = AccountMergePlanner.plan(local, TargetSnapshot())

        assertEquals(1, plan.placeWrites.size)
        assertEquals(1, plan.postWrites.size)
        assertEquals(listOf(ThumbnailAdoption("b", "a")), plan.thumbnailAdoptions)
    }

    @Test
    fun 빈_스냅샷은_아무것도_쓰지_않는다() {
        val plan = AccountMergePlanner.plan(MergeSnapshot(), TargetSnapshot())

        assertTrue(plan.folderWrites.isEmpty() && plan.placeWrites.isEmpty() && plan.postWrites.isEmpty() && plan.placeLinks.isEmpty())
    }
}
