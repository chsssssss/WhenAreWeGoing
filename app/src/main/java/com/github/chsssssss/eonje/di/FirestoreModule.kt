package com.github.chsssssss.eonje.di

import com.github.chsssssss.eonje.data.firestore.AuthSession
import com.github.chsssssss.eonje.data.firestore.wherewegoingApp
import com.github.chsssssss.eonje.domain.repository.AccountRepository
import com.google.firebase.FirebaseApp
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import javax.inject.Singleton

/**
 * Firestore·Auth는 named FirebaseApp "wherewegoing"에만 붙인다. default 앱(Gemini 전용 Spark 프로젝트)에 붙으면 안 된다 —
 * 그래서 `Firebase.firestore`·`Firebase.auth` 같은 KTX 단축형 대신 항상 이 앱 인스턴스를 명시한다.
 */
@Module
@InstallIn(SingletonComponent::class)
object FirestoreModule {
    @Provides
    @Singleton
    @WherewegoingApp
    fun provideWherewegoingApp(): FirebaseApp = wherewegoingApp()

    @Provides
    @Singleton
    fun provideFirebaseAuth(@WherewegoingApp app: FirebaseApp): FirebaseAuth = FirebaseAuth.getInstance(app)

    @Provides
    @Singleton
    fun provideFirestore(@WherewegoingApp app: FirebaseApp): FirebaseFirestore = FirebaseFirestore.getInstance(app)

    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
}

/** Application.onCreate에서 Firebase 앱을 만든 **뒤에** 세션을 꺼내 쓰려고(Hilt 필드 주입은 그보다 먼저 일어난다). */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface AuthSessionEntryPoint {
    fun authSession(): AuthSession
    fun accountRepository(): AccountRepository

    @ApplicationScope
    fun applicationScope(): CoroutineScope
}
