package com.github.chsssssss.eonje.ui.navigation

object EonjeDestinations {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val SETTINGS = "settings"
    const val RESOLVE_POST_ID_ARG = "postId"
    const val RESOLVE = "resolve/{$RESOLVE_POST_ID_ARG}"

    fun resolveRoute(postId: String) = "resolve/$postId"

    const val ONBOARDING_ENTRY_ARG = "entry"
    const val ONBOARDING = "onboarding?$ONBOARDING_ENTRY_ARG={$ONBOARDING_ENTRY_ARG}"

    /** [entry]는 [com.github.chsssssss.eonje.data.analytics.Events.OnboardingEntry] 값 — 진입 경로를 이벤트에 남긴다. */
    fun onboardingRoute(entry: String) = "onboarding?$ONBOARDING_ENTRY_ARG=$entry"

    val topLevelRoutes = setOf(HOME, INBOX, SETTINGS)
}
