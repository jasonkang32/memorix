package com.mebonsoft.memorix.feature.onboarding

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

@Composable
fun OnboardingScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pages = MemorixOnboardingContent.pages
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        HorizontalPager(
            state = pagerState,
            modifier = Modifier.fillMaxSize(),
        ) { pageIndex ->
            OnboardingPage(page = pages[pageIndex])
        }

        Row(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 24.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                pages.indices.forEach { index ->
                    val dotWidth by animateDpAsState(
                        targetValue = if (index == pagerState.currentPage) 20.dp else 8.dp,
                        label = "onboarding-dot-width",
                    )
                    val dotColor by animateColorAsState(
                        targetValue = if (index == pagerState.currentPage) Color.White else Color.White.copy(alpha = 0.4f),
                        label = "onboarding-dot-color",
                    )
                    Box(
                        modifier = Modifier
                            .padding(horizontal = 3.dp)
                            .width(dotWidth)
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp))
                            .background(dotColor),
                    )
                }
            }

            val isLast = pagerState.currentPage == pages.lastIndex
            FilledTonalButton(
                onClick = {
                    if (isLast) {
                        onDone()
                    } else {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    }
                },
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = if (isLast) Color.White else Color.White.copy(alpha = 0.25f),
                    contentColor = if (isLast) Color(0xFF00897B) else Color.White,
                ),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(if (isLast) "시작하기" else "다음", fontWeight = FontWeight.Bold)
            }
        }

        if (pagerState.currentPage < pages.lastIndex) {
            TextButton(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 34.dp, end = 12.dp),
                onClick = onDone,
            ) {
                Text("건너뛰기", color = Color.White.copy(alpha = 0.72f))
            }
        }
    }
}

@Composable
private fun OnboardingPage(page: OnboardingPage) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.linearGradient(
                    colors = page.gradientColors,
                ),
            )
            .padding(horizontal = 32.dp, vertical = 60.dp),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
        ) {
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(Color.White.copy(alpha = 0.15f)),
                contentAlignment = Alignment.Center,
            ) {
                Text(page.emoji, fontSize = 52.sp)
            }
            Spacer(modifier = Modifier.weight(1f))
            Text(
                text = page.title,
                style = MaterialTheme.typography.headlineMedium.copy(
                    color = Color.White,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 36.sp,
                    letterSpacing = (-0.5).sp,
                ),
            )
            Spacer(modifier = Modifier.height(16.dp))
            Text(
                text = page.description,
                style = MaterialTheme.typography.bodyLarge.copy(
                    color = Color.White,
                    lineHeight = 25.sp,
                ),
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.weight(2f))
        }
    }
}
