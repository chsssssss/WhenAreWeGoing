package com.github.chsssssss.eonje.domain.util

/** 공유받은 인스타 게시물 링크. [canonicalUrl]은 쿼리·트래킹 토큰을 뺀 `https://www.instagram.com/{p|reel|tv}/{shortcode}/` 형태. */
data class InstagramPostUrl(val shortcode: String, val canonicalUrl: String)

object InstagramUrlParser {

    private val URL_REGEX = Regex("""https?://\S+""")

    // 호스트를 앵커링해서 "notinstagram.com/p/..."나 다른 URL의 쿼리에 섞인 링크는 받지 않는다.
    // http://, m.instagram.com, instagr.am 형태도 받아서 아래 canonicalUrl로 통일한다 — fetchInstagramMeta는
    // https://(www.)instagram.com/ 만 받아들이므로, 그대로 넘기면 재시도만 반복하다 실패한다.
    private val POST_URL_REGEX = Regex(
        """^https?://(?:(?:www|m)\.)?(?:instagram\.com|instagr\.am)/(p|reel|tv)/([A-Za-z0-9_-]+)""",
        RegexOption.IGNORE_CASE,
    )

    fun findUrl(text: String): String? = URL_REGEX.find(text)?.value

    /** [text] 안의 URL 중 첫 번째 인스타 게시물(/p/, /reel/, /tv/) 링크. 게시물 링크가 없으면 null. */
    fun findPostUrl(text: String): InstagramPostUrl? =
        URL_REGEX.findAll(text).firstNotNullOfOrNull { parsePostUrl(it.value) }

    fun parsePostUrl(url: String): InstagramPostUrl? {
        val match = POST_URL_REGEX.find(url) ?: return null
        val (type, shortcode) = match.destructured
        return InstagramPostUrl(
            shortcode = shortcode,
            canonicalUrl = "https://www.instagram.com/${type.lowercase()}/$shortcode/",
        )
    }

    fun stripQueryParams(url: String): String {
        val queryIndex = url.indexOf('?')
        return if (queryIndex >= 0) url.substring(0, queryIndex) else url
    }

    fun extractShortcode(url: String): String? = parsePostUrl(url)?.shortcode
}
