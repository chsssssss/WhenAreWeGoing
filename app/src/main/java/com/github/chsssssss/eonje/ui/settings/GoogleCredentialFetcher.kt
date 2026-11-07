package com.github.chsssssss.eonje.ui.settings

import android.content.Context
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import com.github.chsssssss.eonje.BuildConfig
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import kotlinx.coroutines.CancellationException

sealed interface GoogleTokenResult {
    /** Google ID 토큰. 로그·Analytics에 남기지 않고 곧바로 Firebase Auth에 넘긴다. */
    data class Token(val idToken: String) : GoogleTokenResult
    data object Cancelled : GoogleTokenResult
    data object Failed : GoogleTokenResult
}

/**
 * Credential Manager로 Google 계정을 골라 ID 토큰을 받는다(연결·재인증 공용). Activity 컨텍스트가 필요해서 화면에서 부른다.
 * [webClientId]는 wherewegoing 프로젝트의 OAuth 웹 클라이언트 ID여야 한다.
 */
class GoogleCredentialFetcher(private val webClientId: String = BuildConfig.GOOGLE_WEB_CLIENT_ID) {

    val isConfigured: Boolean get() = webClientId.isNotBlank()

    suspend fun fetch(context: Context): GoogleTokenResult {
        if (!isConfigured) return GoogleTokenResult.Failed
        return try {
            val request = GetCredentialRequest.Builder()
                .addCredentialOption(GetSignInWithGoogleOption.Builder(webClientId).build())
                .build()
            val credential = CredentialManager.create(context).getCredential(context, request).credential
            if (credential is CustomCredential && credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL) {
                GoogleTokenResult.Token(GoogleIdTokenCredential.createFrom(credential.data).idToken)
            } else {
                GoogleTokenResult.Failed
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: GetCredentialCancellationException) {
            GoogleTokenResult.Cancelled
        } catch (e: GetCredentialException) {
            GoogleTokenResult.Failed
        } catch (e: Exception) {
            GoogleTokenResult.Failed
        }
    }
}
