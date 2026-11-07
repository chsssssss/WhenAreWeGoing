package com.github.chsssssss.eonje.domain.repository

import kotlinx.coroutines.flow.StateFlow

/** 화면용 계정 상태. 이메일은 화면에만 보여주고 로그·Analytics에는 남기지 않는다. */
data class AccountState(
    val signedIn: Boolean = false,
    /** 익명이면 true(계정을 연결하지 않은 기본 상태). */
    val isAnonymous: Boolean = true,
    val email: String? = null,
) {
    val isLinked: Boolean get() = signedIn && !isAnonymous
}

sealed interface LinkResult {
    /** 연결됨. uid는 그대로라서 데이터를 옮길 필요가 없다. */
    data object Success : LinkResult

    /** 이 Google 계정이 이미 다른 uid에 연결돼 있다(다른 기기에서 쓰던 계정). */
    data object Collision : LinkResult
    data object NetworkError : LinkResult
    data object Failed : LinkResult
}

/** 충돌 로그인 전에 보여주는, 이 기기에 있는 데이터의 개수. 0이면 합칠 게 없다. */
data class MergePreview(val folders: Int, val places: Int, val posts: Int) {
    val isEmpty: Boolean get() = folders == 0 && places == 0 && posts == 0
}

/** 합쳐서 계정에 새로 쓴 개수(계정에 이미 있던 것과 겹쳐서 버린 것은 뺀다). */
data class MergeCounts(val folders: Int, val places: Int, val posts: Int)

sealed interface SwitchResult {
    /** 계정으로 전환했다. 합쳤으면 [merged]에 개수가 있다. */
    data class Switched(val merged: MergeCounts?) : SwitchResult

    /** 계정으로 전환했지만 합치기는 아직 끝나지 않았다(네트워크 등). 데이터는 이 기기에 안전하게 남아 있고 다음 시작 때 이어서 합친다. */
    data object SwitchedMergePending : SwitchResult
    data object NetworkError : SwitchResult
    data object Failed : SwitchResult
}

enum class DeleteStep { REAUTH, DATA, ACCOUNT }

sealed interface DeleteResult {
    data object Success : DeleteResult

    /** [step]에서 멈췄다. 다시 시도하면 이어서 끝난다(이미 지운 데이터는 다시 지울 게 없다). [requiresReauth]면 다시 로그인해야 한다. */
    data class Failed(val step: DeleteStep, val requiresReauth: Boolean = false) : DeleteResult
}

/** 계정 연결·로그아웃·삭제. 로그인을 강제하지 않는다 — 익명이 기본이고 이건 설정의 선택 기능이다. */
interface AccountRepository {
    val state: StateFlow<AccountState>

    /** 익명 계정에 Google 계정을 연결한다(uid 유지). [idToken]은 Credential Manager가 준 Google ID 토큰. */
    suspend fun linkWithGoogle(idToken: String): LinkResult

    /**
     * [LinkResult.Collision] 뒤에: 이 기기의 익명 데이터를 읽어 저널 파일에 남기고 개수를 돌려준다(전환하면 못 읽으므로 전환 **전에**).
     * 데이터가 없으면 저널 없이 [MergePreview.isEmpty]를 돌려준다. 읽지 못하면 null.
     */
    suspend fun prepareCollisionMerge(): MergePreview?

    /**
     * 이미 연결된 계정으로 전환한다. [merge]면 이 기기의 데이터를 계정에 합친다. 전환 직전에 Room 이전을 영구 종료한다.
     * 합치기는 저널이 있어서 도중에 끊겨도 다음 시작에 이어진다.
     */
    suspend fun switchToExistingAccount(merge: Boolean): SwitchResult

    /** 충돌 뒤 사용자가 연결을 취소했다. 저널을 지우고 익명 상태를 유지한다. */
    suspend fun cancelCollisionMerge()

    /** 앱 시작 때: 끊긴 합치기가 있으면 이어서 끝낸다. 합친 게 있으면 개수. */
    suspend fun resumePendingMerge(): MergeCounts?

    /**
     * 로그아웃하고 새 익명 계정으로 시작한다. 서버 데이터는 그대로이고 이 기기의 썸네일 등만 정리한다. 익명이면 아무것도 안 한다.
     * Room 이전은 영구 종료된다(Room 데이터는 지우지 않는다).
     */
    suspend fun signOut()

    /**
     * 계정과 서버 데이터를 지운다. 연결된 계정이면 [googleIdToken](재인증용)이 필요하고, 익명이면 null이어도 된다.
     * 사용자가 직접 요청한 삭제이므로 Room의 옛 데이터도 비운다.
     */
    suspend fun deleteAccount(googleIdToken: String?): DeleteResult
}
