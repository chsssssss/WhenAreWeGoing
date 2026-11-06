package com.github.chsssssss.eonje.domain.model

import com.github.chsssssss.eonje.domain.util.TextLimit

/**
 * 폴더 이름의 길이 제한. 입력창은 [MAX_LENGTH]자까지만 받는다. Firestore 보안 규칙의 상한(100자)보다 일부러 작게 잡았다 —
 * 규칙에 닿을 일이 없게 하고, 칩·시트에 들어갈 이름이 지나치게 길어지지 않게 하려는 것이다. 규칙 상한은 바꾸지 않는다.
 */
object FolderName {
    const val MAX_LENGTH = 50

    /** 입력·저장 전에 길이를 맞춘다(붙여넣기로 길게 들어온 이름 포함). */
    fun clamp(text: String): String = TextLimit.safeTake(text, MAX_LENGTH)
}
