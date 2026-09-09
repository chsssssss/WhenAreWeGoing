package com.github.chsssssss.eonje.share

import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostResult
import com.github.chsssssss.eonje.domain.usecase.SaveSharedPostUseCase
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

@AndroidEntryPoint
class ShareReceiverActivity : ComponentActivity() {

    @Inject
    lateinit var saveSharedPostUseCase: SaveSharedPostUseCase

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val sharedText = intent
            ?.takeIf { it.action == Intent.ACTION_SEND && it.type == "text/plain" }
            ?.getStringExtra(Intent.EXTRA_TEXT)

        if (sharedText == null) {
            finish()
            return
        }

        lifecycleScope.launch {
            val result = saveSharedPostUseCase(sharedText)
            showToast(result)
            finish()
        }
    }

    private fun showToast(result: SaveSharedPostResult) {
        val message = when (result) {
            SaveSharedPostResult.Saved -> "저장됨"
            SaveSharedPostResult.AlreadySaved -> "이미 저장된 게시물이에요"
            SaveSharedPostResult.NoUrlFound -> "인스타그램 링크를 찾을 수 없어요"
        }
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}
