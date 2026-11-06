package com.github.chsssssss.eonje.data.migration

import org.junit.Assert.assertEquals
import org.junit.Test

class MigrationStateTest {
    @Test
    fun 건너뛴_항목에서_게시물_ID만_꺼낸다() {
        val state = MigrationState(
            skipped = setOf(
                "POSTS:post-1:PERMISSION_DENIED",
                "PLACES:place-1:PERMISSION_DENIED",
                "FOLDERS:folder-1:INVALID_ARGUMENT",
                "POSTS:post-2:UNREADABLE",
            ),
        )
        assertEquals(setOf("post-1", "post-2"), state.skippedPostIds)
        assertEquals(emptySet<String>(), MigrationState().skippedPostIds)
    }

    @Test
    fun 문서_ID에_콜론이_있어도_ID를_온전히_꺼낸다() {
        // "종류:ID:사유"에서 ID는 가운데다. (UUID엔 콜론이 없지만 형식이 깨지지 않는지 확인)
        assertEquals(setOf("a"), MigrationState(skipped = setOf("POSTS:a:X")).skippedPostIds)
        assertEquals(emptySet<String>(), MigrationState(skipped = setOf("POSTS:broken")).skippedPostIds)
    }
}
