package com.github.chsssssss.eonje.ui.navigation

object EonjeDestinations {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val SETTINGS = "settings"
    const val RESOLVE_POST_ID_ARG = "postId"
    const val RESOLVE = "resolve/{$RESOLVE_POST_ID_ARG}"

    fun resolveRoute(postId: String) = "resolve/$postId"

    val topLevelRoutes = setOf(HOME, INBOX, SETTINGS)
}
