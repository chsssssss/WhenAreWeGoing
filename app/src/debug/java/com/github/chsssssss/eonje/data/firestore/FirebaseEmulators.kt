package com.github.chsssssss.eonje.data.firestore

import com.github.chsssssss.eonje.BuildConfig
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore

/** firebase.json의 emulators 포트와 같아야 한다. */
const val FIRESTORE_EMULATOR_PORT = 8080
const val AUTH_EMULATOR_PORT = 9099

/**
 * 디버그 빌드 전용. `-PfirebaseEmulator=true`로 빌드했을 때만 Auth·Firestore를 로컬 Emulator Suite에 연결한다.
 * Firestore 설정(영구 캐시)을 먼저 지정한 **다음에** 불러야 한다 — useEmulator는 호스트·SSL 설정을 덮어쓰고,
 * 그 뒤에 설정을 다시 지정하면 운영 서버를 가리키게 된다. 릴리스 소스셋의 같은 함수는 아무것도 하지 않는다.
 */
internal fun connectFirebaseEmulators(auth: FirebaseAuth, firestore: FirebaseFirestore) {
    if (!BuildConfig.USE_FIREBASE_EMULATOR) return
    firestore.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, FIRESTORE_EMULATOR_PORT)
    auth.useEmulator(BuildConfig.FIREBASE_EMULATOR_HOST, AUTH_EMULATOR_PORT)
}
