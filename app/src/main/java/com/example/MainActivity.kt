package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.data.local.AppDatabase
import com.example.data.repository.VolleyRepository
import com.example.ui.screens.*
import com.example.ui.theme.*
import com.example.ui.viewmodel.NavigationTab
import com.example.ui.viewmodel.VolleyViewModel

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                val context = LocalContext.current
                val database = remember { AppDatabase.getDatabase(context) }
                val repository = remember { VolleyRepository(database) }
                val volleyViewModel: VolleyViewModel = viewModel { VolleyViewModel(repository) }

                VolleyAppRoot(viewModel = volleyViewModel)
            }
        }
    }
}

@Composable
fun VolleyAppRoot(viewModel: VolleyViewModel) {
    val currentTab by viewModel.currentTab.collectAsState()

    // BackHandler to return to Team tab if on other tabs
    BackHandler(enabled = currentTab != NavigationTab.TEAM) {
        viewModel.selectTab(NavigationTab.TEAM)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(VolleyBlack)) {
        val isWideScreen = maxWidth >= 600.dp

        if (isWideScreen) {
            // Tablet / Foldable / Landscape layout with scrollable NavigationRail
            Row(modifier = Modifier.fillMaxSize()) {
                NavigationRail(
                    containerColor = VolleyDarkSurface,
                    contentColor = VolleyYellow,
                    windowInsets = WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.Start),
                    header = {
                        Text(
                            "Volley\nTool",
                            color = VolleyYellow,
                            fontWeight = FontWeight.Black,
                            fontSize = 15.sp,
                            lineHeight = 18.sp,
                            modifier = Modifier.padding(vertical = 8.dp)
                        )
                    },
                    modifier = Modifier.fillMaxHeight().testTag("navigation_rail")
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .verticalScroll(rememberScrollState()),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(2.dp)
                    ) {
                        NavigationTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationRailItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = getTabIcon(tab),
                                        contentDescription = tab.label,
                                        tint = if (isSelected) Color.Black else VolleyWhite
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        fontSize = 11.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationRailItemDefaults.colors(
                                    indicatorColor = VolleyYellow,
                                    selectedTextColor = VolleyYellow,
                                    unselectedTextColor = VolleyWhite
                                ),
                                modifier = Modifier.testTag("nav_rail_${tab.name.lowercase()}")
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Vertical + WindowInsetsSides.End))
                ) {
                    ScreenContent(tab = currentTab, viewModel = viewModel)
                }
            }
        } else {
            // Phone Portrait layout: Column guarantees zero overlap between screen buttons and Bottom NavigationBar
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(VolleyBlack)
            ) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))
                ) {
                    ScreenContent(tab = currentTab, viewModel = viewModel)
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = VolleyDarkSurface,
                    shadowElevation = 12.dp,
                    border = BorderStroke(1.dp, VolleyGrayBorder)
                ) {
                    NavigationBar(
                        containerColor = VolleyDarkSurface,
                        contentColor = VolleyYellow,
                        tonalElevation = 8.dp,
                        windowInsets = WindowInsets.navigationBars,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("bottom_navigation_bar")
                    ) {
                        NavigationTab.values().forEach { tab ->
                            val isSelected = currentTab == tab
                            NavigationBarItem(
                                selected = isSelected,
                                onClick = { viewModel.selectTab(tab) },
                                icon = {
                                    Icon(
                                        imageVector = getTabIcon(tab),
                                        contentDescription = tab.label,
                                        tint = if (isSelected) Color.Black else VolleyWhite
                                    )
                                },
                                label = {
                                    Text(
                                        tab.label,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 10.sp,
                                        maxLines = 1,
                                        softWrap = false,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                colors = NavigationBarItemDefaults.colors(
                                    indicatorColor = VolleyYellow,
                                    selectedTextColor = VolleyYellow,
                                    unselectedTextColor = VolleyWhite
                                ),
                                modifier = Modifier.testTag("nav_bottom_${tab.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ScreenContent(tab: NavigationTab, viewModel: VolleyViewModel) {
    when (tab) {
        NavigationTab.TEAM -> TeamScreen(viewModel = viewModel)
        NavigationTab.SCORE -> ScoreboardScreen(viewModel = viewModel)
        NavigationTab.ROTATION -> RotationScreen(viewModel = viewModel)
        NavigationTab.TURNIER -> TournamentScreen(viewModel = viewModel)
        NavigationTab.TAKTIK -> TaktikScreen(viewModel = viewModel)
        NavigationTab.COIN_TOSS -> CoinTossScreen(viewModel = viewModel)
    }
}

fun getTabIcon(tab: NavigationTab) = when (tab) {
    NavigationTab.TEAM -> Icons.Default.Group
    NavigationTab.SCORE -> Icons.Default.SportsScore
    NavigationTab.ROTATION -> Icons.Default.Sync
    NavigationTab.TURNIER -> Icons.Default.EmojiEvents
    NavigationTab.TAKTIK -> Icons.Default.Draw
    NavigationTab.COIN_TOSS -> Icons.Default.Casino
}
