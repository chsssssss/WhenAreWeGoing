package com.github.chsssssss.eonje.share

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.github.chsssssss.eonje.data.analytics.Events
import com.github.chsssssss.eonje.domain.repository.OnboardingRepository
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostResult
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostUseCase
import com.google.firebase.analytics.FirebaseAnalytics
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.runBlocking
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var saveSharedPostUseCase: SaveSharedPostUseCase

    @Inject
    lateinit var analytics: FirebaseAnalytics

    @Inject
    lateinit var onboardingRepository: OnboardingRepository

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText == null) {
            finish()
            return
        }

        // F1은 항상 성공해야 한다. 로그인돼 있으면 로컬 쓰기가 반영된 것까지 확인하고(밀리초), 로그인 전이면 기다리지 않고
        // URL을 WorkManager에 맡긴다 — 어느 쪽이든 finish() 이후 프로세스가 정리돼도 유실되지 않는다.
        val result = runBlocking { saveSharedPostUseCase(sharedText) }
        if (result == SaveSharedPostResult.Saved || result == SaveSharedPostResult.SavedLater) {
            analytics.logEvent(Events.SHARE_RECEIVED, null)
            logFirstShareAfterOnboarding()
        }
        showToast(result)
        finish()
    }

    // 측정용일 뿐이라 DataStore를 못 읽어도 저장 흐름에는 영향을 주지 않는다.
    private fun logFirstShareAfterOnboarding() {
        val isFirstShare = runCatching {
            runBlocking { onboardingRepository.consumeFirstShareAfterOnboarding() }
        }.getOrDefault(false)
        if (isFirstShare) {
            analytics.logEvent(Events.FIRST_SHARE_AFTER_ONBOARDING, null)
        }
    }

    private fun showToast(result: SaveSharedPostResult) {
        val message = when (result) {
            // 오프라인이면 저장은 됐지만 캡션 조회·장소 인식은 연결된 뒤에 시작된다는 점만 덧붙인다.
            SaveSharedPostResult.Saved, SaveSharedPostResult.SavedLater ->
                if (isOnline()) "저장했어요" else "저장했어요. 인터넷에 연결되면 정리를 시작해요"
            SaveSharedPostResult.AlreadySaved -> "이미 저장된 게시물이에요"
            SaveSharedPostResult.NoUrlFound -> "인스타그램 게시물 링크를 찾을 수 없어요"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }

    private fun isOnline(): Boolean {
        val manager = getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return false
        val capabilities = manager.getNetworkCapabilities(manager.activeNetwork) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) &&
            capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
    }
}
