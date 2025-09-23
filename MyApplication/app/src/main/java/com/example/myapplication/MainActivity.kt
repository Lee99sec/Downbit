package com.example.myapplication

import SecureBaseActivity
import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.BottomNavigation
import androidx.compose.material.BottomNavigationItem
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.AttachMoney
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import com.example.myapplication.screen.ExchangeHomeScreen

class MainActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Edge-to-edge 활성화
        WindowCompat.setDecorFitsSystemWindows(window, false)

        RetrofitClient.init(this)
        val navigateToTab = intent.getStringExtra("navigate_to_tab")
        setContent {
            MaterialTheme {
                MainScreen(initialTab = navigateToTab)
            }
        }
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        val navigateToTab = intent?.getStringExtra("navigate_to_tab")
        if (!navigateToTab.isNullOrEmpty()) {
            setIntent(intent)
        }
    }
}

@SuppressLint("UnusedMaterialScaffoldPaddingParameter")
@Composable
fun MainScreen(initialTab: String? = null) {
    var selectedTab by remember { mutableStateOf(initialTab ?: "거래소") }
    var showChatbot by remember { mutableStateOf(false) }

    LaunchedEffect(initialTab) {
        if (!initialTab.isNullOrEmpty()) {
            selectedTab = initialTab
        }
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.systemBars), // 모든 기종에서 일관된 패딩 적용
        bottomBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                elevation = 8.dp
            ) {
                BottomNavigation(
                    backgroundColor = Color.White,
                    elevation = 0.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.ShowChart, contentDescription = null) },
                        label = { Text("거래소") },
                        selected = selectedTab == "거래소",
                        onClick = { selectedTab = "거래소" },
                        selectedContentColor = Color(0xFFD32F2F),
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.Forum, contentDescription = null) },
                        label = { Text("커뮤니티") },
                        selected = selectedTab == "커뮤니티",
                        onClick = { selectedTab = "커뮤니티" },
                        selectedContentColor = Color(0xFFD32F2F),
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.AccountBalanceWallet, contentDescription = null) },
                        label = { Text("자산현황") },
                        selected = selectedTab == "자산현황",
                        onClick = { selectedTab = "자산현황" },
                        selectedContentColor = Color(0xFFD32F2F),
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.AttachMoney, contentDescription = null) },
                        label = { Text("입출금") },
                        selected = selectedTab == "입출금",
                        onClick = { selectedTab = "입출금" },
                        selectedContentColor = Color(0xFFD32F2F),
                        unselectedContentColor = Color.Gray
                    )
                    BottomNavigationItem(
                        icon = { Icon(Icons.Default.MoreHoriz, contentDescription = null) },
                        label = { Text("더보기") },
                        selected = selectedTab == "더보기",
                        onClick = { selectedTab = "더보기" },
                        selectedContentColor = Color(0xFFD32F2F),
                        unselectedContentColor = Color.Gray
                    )
                }
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.fillMaxSize()) {
            when (selectedTab) {
                "거래소" -> ExchangeHomeScreen()
                "커뮤니티" -> BoardScreen()
                "자산현황" -> AssetScreen(
                    onNavigateToDeposit = {
                        selectedTab = "입출금"
                    }
                )
                "입출금" -> DepositScreen()
                "더보기" -> MoreScreen()
            }

            DraggableFloatingChatButton(
                onClick = {
                    showChatbot = !showChatbot
                },
                showBadge = !showChatbot
            )

            if (showChatbot) {
                DownyChatbotScreen(
                    onClose = { showChatbot = false }
                )
            }
        }
    }
}