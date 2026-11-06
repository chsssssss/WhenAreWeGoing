package com.github.chsssssss.eonje.domain.util

import java.security.MessageDigest
import java.util.UUID

/**
 * 공유된 게시물을 식별하는 키. 고유 작업 이름, 중복 확인, 게시물 문서 ID가 전부 이 키에서 나온다.
 * shortcode가 있으면 그것, 없으면 URL의 해시다.
 */
object ShareKey {
    fun of(shortcode: String?, url: String): String =
        shortcode?.takeIf { it.isNotBlank() } ?: "u-" + sha256Hex(url).take(HASH_LENGTH)

    /**
     * 키에서 만든 결정적 UUID. 직접 저장하든 Worker가 저장하든, 같은 공유는 같은 문서가 돼서 중복 게시물이 생기지 않는다.
     */
    fun postId(key: String): String = UUID.nameUUIDFromBytes("eonje-post:$key".toByteArray()).toString()

    private fun sha256Hex(text: String): String =
        MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    private const val HASH_LENGTH = 16
}
