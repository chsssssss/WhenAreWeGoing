package com.github.chsssssss.eonje.data.account.merge

import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File

class AccountMergeJournalTest {
    private lateinit var dir: File
    private lateinit var file: File
    private lateinit var journal: AccountMergeJournal

    @Before
    fun setUp() {
        dir = File(System.getProperty("java.io.tmpdir"), "journal-test-${System.nanoTime()}").apply { mkdirs() }
        file = File(dir, AccountMergeJournal.FILE_NAME)
        journal = AccountMergeJournal(file)
    }

    @After
    fun tearDown() {
        dir.deleteRecursively()
    }

    private fun data(stage: MergeStage = MergeStage.SNAPSHOT) = JournalData(
        fromUid = "anon",
        stage = stage,
        snapshot = MergeSnapshot(
            folders = listOf(FolderSnap("f", "폴더", 1, "☕", 1L)),
            posts = listOf(PostSnap("p", "SC", "https://www.instagram.com/p/SC/", "한글 캡션 😀", "RESOLVED", 0, 1L, null, listOf("pl"), listOf(CandidateSnap(0, "x", 0, "k", "n", "a", null, 1.0, 2.0)))),
        ),
    )

    @Test
    fun 저장한_저널을_그대로_읽고_지우면_없어진다() = runBlocking {
        assertFalse(journal.exists())
        assertFalse(journal.isActive())

        journal.write(data())

        assertTrue(journal.isActive())
        assertEquals(data(), journal.read())
        journal.delete()
        assertFalse(journal.exists())
        assertNull(journal.read())
    }

    @Test
    fun 계획과_단계까지_저장하고_덮어쓸_수_있다() = runBlocking {
        val plan = MergePlan(emptyList(), emptyList(), listOf(PlaceLink("p", listOf("s"))), emptyList(), listOf(ThumbnailAdoption("a", "b")), 1)
        journal.write(data(MergeStage.SWITCHING))
        journal.write(data(MergeStage.MERGING).copy(targetUid = "acct", plan = plan))

        val read = journal.read()!!

        assertEquals(MergeStage.MERGING, read.stage)
        assertEquals("acct", read.targetUid)
        assertEquals(plan, read.plan)
        assertFalse("임시 파일이 남지 않는다", File(dir, "${AccountMergeJournal.FILE_NAME}.tmp").exists())
    }

    @Test
    fun 읽을_수_없는_저널은_지워서_썸네일_정리가_영영_막히지_않게_한다() = runBlocking {
        file.writeText("{ 깨진 json")

        assertNull(journal.read())

        assertFalse(file.exists())
        assertNotNull(journal) // 계속 쓸 수 있다
        journal.write(data())
        assertEquals(data(), journal.read())
    }
}
