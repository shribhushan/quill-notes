package com.quillnotes.ui.screens.onboarding

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CloudSync
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.quillnotes.R
import com.quillnotes.ui.components.ScreenGutter
import com.quillnotes.ui.components.heroBrush
import kotlinx.coroutines.launch

private class Page(val icon: ImageVector, val title: Int, val body: Int)

private val pages = listOf(
    Page(Icons.Rounded.EditNote, R.string.onboarding_1_title, R.string.onboarding_1_body),
    Page(Icons.Rounded.Lock, R.string.onboarding_2_title, R.string.onboarding_2_body),
    Page(Icons.Rounded.CloudSync, R.string.onboarding_3_title, R.string.onboarding_3_body)
)

/** Three short pages on first launch: what Quill is, why it is private, and how backup works. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(onFinish: () -> Unit, modifier: Modifier = Modifier) {
    val scheme = MaterialTheme.colorScheme
    val pager = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val last = pager.currentPage == pages.lastIndex

    Column(
        modifier
            .fillMaxSize()
            .background(scheme.background)
            .statusBarsPadding()
            .navigationBarsPadding()
    ) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), horizontalArrangement = Arrangement.End) {
            if (!last) TextButton(onClick = onFinish) { Text(stringResource(R.string.onboarding_skip)) }
            else Spacer(Modifier.height(48.dp))
        }

        HorizontalPager(state = pager, modifier = Modifier.weight(1f)) { index ->
            val page = pages[index]
            Column(
                Modifier.fillMaxSize().padding(horizontal = ScreenGutter + 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                Box(
                    Modifier.size(144.dp).clip(CircleShape).background(heroBrush()),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(page.icon, contentDescription = null, tint = scheme.onSurface, modifier = Modifier.size(64.dp))
                }
                Spacer(Modifier.height(36.dp))
                Text(
                    stringResource(page.title),
                    style = MaterialTheme.typography.displaySmall,
                    color = scheme.onBackground,
                    textAlign = TextAlign.Center
                )
                Spacer(Modifier.height(14.dp))
                Text(
                    stringResource(page.body),
                    style = MaterialTheme.typography.bodyLarge,
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }

        Row(Modifier.fillMaxWidth().padding(bottom = 20.dp), horizontalArrangement = Arrangement.Center) {
            repeat(pages.size) { i ->
                val active = i == pager.currentPage
                Box(
                    Modifier
                        .padding(horizontal = 4.dp)
                        .size(width = if (active) 22.dp else 8.dp, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (active) scheme.onBackground else scheme.outlineVariant)
                )
            }
        }
        Button(
            onClick = { if (last) onFinish() else scope.launch { pager.animateScrollToPage(pager.currentPage + 1) } },
            colors = ButtonDefaults.buttonColors(containerColor = scheme.onBackground, contentColor = scheme.background),
            modifier = Modifier.fillMaxWidth().padding(horizontal = ScreenGutter).padding(bottom = 24.dp).height(54.dp)
        ) {
            Text(stringResource(if (last) R.string.onboarding_start else R.string.onboarding_next), style = MaterialTheme.typography.titleMedium)
        }
    }
}
