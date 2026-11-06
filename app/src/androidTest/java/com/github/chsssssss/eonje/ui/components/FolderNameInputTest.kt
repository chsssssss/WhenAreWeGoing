package com.github.chsssssss.eonje.ui.components

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.chsssssss.eonje.domain.model.Folder
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/** 폴더 이름을 받는 두 입력(폴더 생성 시트, 폴더 피커)이 50자에서 막히고 글자 수와 안내를 보여주는지 확인한다. */
@RunWith(AndroidJUnit4::class)
class FolderNameInputTest {
    @get:Rule
    val compose = createComposeRule()

    private val limitNotice = "폴더 이름은 50자까지 입력할 수 있어요"

    @Test
    fun 폴더_생성_시트는_50자에서_막고_글자_수와_안내를_보여준다() {
        var created: String? = null
        compose.setContent { EonjeTheme { AddFolderContent(onCreate = { name, _, _ -> created = name }) } }

        compose.onNode(hasSetTextAction()).performTextInput("가".repeat(30))
        compose.onNodeWithText("30/50").assertIsDisplayed()
        compose.onNodeWithText(limitNotice).assertDoesNotExist()

        // 상한을 넘는 입력(붙여넣기)은 50자까지만 들어간다
        compose.onNode(hasSetTextAction()).performTextReplacement("나".repeat(120))
        compose.onNodeWithText("나".repeat(50)).assertIsDisplayed()
        compose.onNodeWithText("나".repeat(51)).assertDoesNotExist()
        compose.onNodeWithText("50/50").assertIsDisplayed()
        compose.onNodeWithText(limitNotice).assertIsDisplayed()

        compose.onNodeWithText("폴더 만들기").performClick()
        compose.waitForIdle()
        assertEquals("저장되는 이름도 50자", "나".repeat(50), created)
    }

    @Test
    fun 폴더_피커의_새_폴더_이름도_50자에서_막고_안내를_보여준다() {
        var created: String? = null
        compose.setContent {
            EonjeTheme {
                FolderPickerContent(
                    folders = listOf(Folder("f1", "기존 폴더", 1L)),
                    selectedFolderId = null,
                    onSelectFolder = {},
                    onCreateFolder = { created = it },
                    onDeleteFolder = {},
                )
            }
        }

        compose.onNode(hasSetTextAction()).performTextInput("다".repeat(10))
        compose.onNodeWithText("10/50").assertIsDisplayed()

        compose.onNode(hasSetTextAction()).performTextReplacement("라".repeat(200))
        compose.onNodeWithText("50/50").assertIsDisplayed()
        compose.onNodeWithText(limitNotice).assertIsDisplayed()

        compose.onNodeWithContentDescription("폴더 추가").performClick()
        compose.waitForIdle()
        assertEquals("라".repeat(50), created)
    }
}
