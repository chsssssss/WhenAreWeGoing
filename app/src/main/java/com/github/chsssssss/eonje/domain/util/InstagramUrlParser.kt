package com.github.chsssssss.eonje.domain.util

object InstagramUrlParser {

    private val URL_REGEX = Regex("""https?://\S+""")
    private val SHORTCODE_REGEX = Regex("""instagram\.com/(?:p|reel|tv)/([A-Za-z0-9_-]+)""")

    fun findUrl(text: String): String? = URL_REGEX.find(text)?.value

    fun stripQueryParams(url: String): String {
        val queryIndex = url.indexOf('?')
        return if (queryIndex >= 0) url.substring(0, queryIndex) else url
    }

    fun extractShortcode(url: String): String? =
        SHORTCODE_REGEX.find(url)?.groupValues?.get(1)
}
