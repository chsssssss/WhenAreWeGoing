package com.github.chsssssss.eonje.ui.navigation

object EonjeDestinations {
    const val HOME = "home"
    const val INBOX = "inbox"
    const val SETTINGS = "settings"
    const val ACCOUNTS = "accounts"
    const val RESOLVE_POST_ID_ARG = "postId"
    const val RESOLVE = "resolve/{$RESOLVE_POST_ID_ARG}"
    const val PLACE_DETAIL_ID_ARG = "placeId"
    const val PLACE_DETAIL = "placeDetail/{$PLACE_DETAIL_ID_ARG}"

    fun resolveRoute(postId: String) = "resolve/$postId"
    fun placeDetailRoute(placeId: String) = "placeDetail/$placeId"

    val topLevelRoutes = setOf(HOME, INBOX, SETTINGS)
}
