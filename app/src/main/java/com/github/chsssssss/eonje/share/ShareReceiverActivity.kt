package com.github.chsssssss.eonje.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.github.chsssssss.eonje.data.analytics.Events
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText == null) {
            finish()
            return
        }

        // F1은 항상 성공해야 한다 — Room 저장 하나뿐이라 밀리초 단위이므로 finish() 전에 동기적으로 끝낸다.
        val result = runBlocking { saveSharedPostUseCase(sharedText) }
        if (result == SaveSharedPostResult.Saved) {
            analytics.logEvent(Events.SHARE_RECEIVED, null)
        }
        showToast(result)
        finish()
    }

    private fun showToast(result: SaveSharedPostResult) {
        val message = when (result) {
            SaveSharedPostResult.Saved -> "저장됨"
            SaveSharedPostResult.AlreadySaved -> "이미 저장된 게시물이에요"
            SaveSharedPostResult.NoUrlFound -> "인스타그램 게시물 링크를 찾을 수 없어요"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
