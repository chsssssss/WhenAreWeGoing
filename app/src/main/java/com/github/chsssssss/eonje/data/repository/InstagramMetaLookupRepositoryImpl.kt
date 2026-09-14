package com.github.chsssssss.eonje.data.repository

import android.content.Context
import com.github.chsssssss.eonje.BuildConfig
import com.github.chsssssss.eonje.data.remote.WHEREWEGOING_FIREBASE_APP_NAME
import com.github.chsssssss.eonje.domain.model.InstagramPostMeta
import com.github.chsssssss.eonje.domain.repository.InstagramMetaLookupRepository
import com.google.firebase.Firebase
import com.google.firebase.FirebaseApp
import com.google.firebase.FirebaseOptions
import com.google.firebase.functions.FirebaseFunctions
import com.google.firebase.functions.functions
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.tasks.await
import javax.inject.Inject

class InstagramMetaLookupRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
) : InstagramMetaLookupRepository {

    private val functions: FirebaseFunctions? by lazy { buildFunctions() }

    override suspend fun fetchMeta(url: String): Result<InstagramPostMeta?> {
        val functions = functions
            ?: return Result.failure(IllegalStateException("Firebase 프로젝트가 아직 설정되지 않았어요"))
        return try {
            val result = functions.getHttpsCallable("fetchInstagramMeta")
                .call(mapOf("url" to url))
                .await()
            val data = result.data as? Map<*, *>
            val username = data?.get("username") as? String
            if (username.isNullOrBlank()) return Result.success(null)
            Result.success(
                InstagramPostMeta(
                    username = username,
                    caption = data["caption"] as? String,
                    imageUrl = data["imageUrl"] as? String,
                )
            )
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun buildFunctions(): FirebaseFunctions? {
        val projectId = BuildConfig.FIREBASE_PROJECT_ID
        val applicationId = BuildConfig.FIREBASE_APPLICATION_ID
        val apiKey = BuildConfig.FIREBASE_API_KEY
        if (projectId.isBlank() || applicationId.isBlank() || apiKey.isBlank()) return null

        val app = FirebaseApp.getApps(context).firstOrNull { it.name == WHEREWEGOING_FIREBASE_APP_NAME }
            ?: FirebaseApp.initializeApp(
                context,
                FirebaseOptions.Builder()
                    .setProjectId(projectId)
                    .setApplicationId(applicationId)
                    .setApiKey(apiKey)
                    .build(),
                WHEREWEGOING_FIREBASE_APP_NAME,
            )

        return Firebase.functions(app, "asia-northeast3")
    }
}
