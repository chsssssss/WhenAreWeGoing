package com.github.chsssssss.eonje.data.remote

/**
 * Firebase AI Logic은 이름과 무관하게 "기본(default) FirebaseApp"만 찾아서 쓰기 때문에
 * (named app으로 초기화하면 내부 validateResponse가 "Default FirebaseApp is not initialized"로 죽는다),
 * Gemini 전용 프로젝트를 default 앱으로 두고, Apify 게이트웨이용 프로젝트(wherewegoing-a5493)는
 * 이 이름의 named app으로 따로 초기화한다.
 */
internal const val WHEREWEGOING_FIREBASE_APP_NAME = "wherewegoing"
