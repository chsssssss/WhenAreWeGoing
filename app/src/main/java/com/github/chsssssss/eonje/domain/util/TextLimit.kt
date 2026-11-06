package com.github.chsssssss.eonje.domain.util

/** 글자 수 상한에 맞추는 자르기. */
object TextLimit {
    /**
     * [max]자(UTF-16 단위)까지 자른다. 이모지 같은 서로게이트 쌍의 한가운데서 자르지 않는다(깨진 글자가 남지 않게 쌍째로 뺀다).
     * UTF-16 단위로 [max] 이하면 유니코드 코드포인트 수로도 [max] 이하라서, 어느 쪽으로 세는 상한이든 지킨다.
     */
    fun safeTake(text: String, max: Int): String {
        if (text.length <= max) return text
        var end = max
        if (end > 0 && Character.isHighSurrogate(text[end - 1])) end--
        return text.substring(0, end)
    }
}
