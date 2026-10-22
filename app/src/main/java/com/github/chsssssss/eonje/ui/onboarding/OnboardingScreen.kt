package com.github.chsssssss.eonje.ui.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Inbox
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import com.github.chsssssss.eonje.ui.components.FilledPillButton
import com.github.chsssssss.eonje.ui.components.PaperPlaneIcon
import com.github.chsssssss.eonje.ui.theme.EonjeColors
import com.github.chsssssss.eonje.ui.theme.EonjeTheme
import com.github.chsssssss.eonje.ui.theme.Typography
import kotlinx.coroutines.launch

internal data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val body: String,
)

internal val OnboardingPages = listOf(
    OnboardingPage(
        icon = Icons.Filled.PhotoLibrary,
        title = "인스타에서 맛집 게시물을 열어요",
        body = "가보고 싶은 가게가 나온 게시물이나 릴스를 보고 있다면 그대로 두세요.",
    ),
    OnboardingPage(
        icon = PaperPlaneIcon,
        title = "공유 버튼에서 '언제가지'를 골라요",
        body = "공유(종이비행기) 버튼 → 다른 앱으로 공유에서 언제가지를 선택하면 끝이에요. 앱을 열 필요도 없어요.",
    ),
    OnboardingPage(
        icon = Icons.Filled.Inbox,
        title = "가게는 알아서 찾아 저장해요",
        body = "게시물 속 가게를 찾아 지도에 저장해요. 못 찾은 게시물은 인박스에 모아둘 테니 나중에 직접 골라주세요.",
    ),
)

@Composable
fun OnboardingScreen(
    onFinish: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: OnboardingViewModel = hiltViewModel(),
) {
    val onFinishState by rememberUpdatedState(onFinish)
    LaunchedEffect(Unit) {
        viewModel.finished.collect { onFinishState() }
    }

    OnboardingContent(
        onSkip = viewModel::onSkip,
        onComplete = viewModel::onComplete,
        modifier = modifier,
    )
}

@Composable
private fun OnboardingContent(
    onSkip: (page: Int) -> Unit,
    onComplete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pagerState = rememberPagerState(pageCount = { OnboardingPages.size })
    val scope = rememberCoroutineScope()
    val isLastPage = pagerState.currentPage == OnboardingPages.lastIndex

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(EonjeColors.background)
            .padding(horizontal = 24.dp, vertical = 16.dp),
    ) {
        // 마지막 장에서도 자리는 유지해서 버튼이 사라질 때 레이아웃이 튀지 않게 한다.
        Box(modifier = Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
            if (!isLastPage) {
                TextButton(onClick = { onSkip(pagerState.currentPage) }) {
                    Text("건너뛰기", style = Typography.labelLarge, color = EonjeColors.textMuted)
                }
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier.weight(1f).fillMaxWidth(),
        ) { page ->
            OnboardingPageContent(OnboardingPages[page])
        }

        PageIndicator(pagerState = pagerState, modifier = Modifier.align(Alignment.CenterHorizontally))

        FilledPillButton(
            text = if (isLastPage) "시작하기" else "다음",
            onClick = {
                if (isLastPage) {
                    onComplete()
                } else {
                    scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 24.dp),
        )
    }
}

@Composable
private fun OnboardingPageContent(page: OnboardingPage) {
    Column(
        modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(EonjeColors.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            Icon(page.icon, contentDescription = null, tint = EonjeColors.accent, modifier = Modifier.size(52.dp))
        }
        Text(
            text = page.title,
            style = Typography.titleLarge,
            color = EonjeColors.textPrimary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 36.dp),
        )
        Text(
            text = page.body,
            style = Typography.bodyLarge,
            color = EonjeColors.textSecondary,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun PageIndicator(pagerState: PagerState, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(pagerState.pageCount) { index ->
            val selected = pagerState.currentPage == index
            val width by animateDpAsState(if (selected) 20.dp else 8.dp, label = "indicatorWidth")
            val color by animateColorAsState(
                if (selected) EonjeColors.accent else EonjeColors.border,
                label = "indicatorColor",
            )
            Box(
                modifier = Modifier
                    .height(8.dp)
                    .width(width)
                    .clip(RoundedCornerShape(4.dp))
                    .background(color),
            )
        }
    }
}

@Preview(showBackground = true, backgroundColor = 0xFF121316)
@Composable
private fun OnboardingScreenPreview() {
    EonjeTheme {
        OnboardingContent(onSkip = {}, onComplete = {})
    }
}
