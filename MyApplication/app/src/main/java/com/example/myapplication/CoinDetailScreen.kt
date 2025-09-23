package com.example.myapplication.screen

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL


@Composable
fun CoinDetailScreen(symbol: String, koreanName: String, price: String, onBackClick: () -> Unit = {}) {
    var selectedTab by remember { mutableStateOf(0) }
    val tabTitles = listOf("차트", "주문", "정보")

    // 실시간 현재가 상태
    var currentPrice by remember { mutableStateOf(price) }

    // 뒤로가기 버튼 처리 - 항상 이전 화면으로 이동
    BackHandler(enabled = true) {
        onBackClick()
    }

    // 현재가 실시간 업데이트 (5초마다)
    LaunchedEffect(symbol) {
        while (true) {
            try {
                val tickerResponse = withContext(Dispatchers.IO) {
                    URL("https://api.bithumb.com/public/ticker/${symbol}_KRW").readText()
                }
                val tickerJson = JSONObject(tickerResponse)
                val tickerData = tickerJson.getJSONObject("data")
                val newPrice = tickerData.getString("closing_price")
                currentPrice = newPrice
            } catch (e: Exception) {
                // 에러 발생 시 기존 가격 유지
            }
            kotlinx.coroutines.delay(5000) // 5초 간격
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = { Text("$koreanName ($symbol)", color = redMain, fontSize = 18.sp) },
            navigationIcon = {
                IconButton(onClick = onBackClick) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로가기", tint = redMain)
                }
            },
            backgroundColor = Color.White,
            elevation = 4.dp
        )

        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    Text("$koreanName ($symbol/KRW)", fontSize = 20.sp)
                    // 실시간 업데이트되는 현재가 표시
                    Text("${formatPrice(currentPrice)} KRW", fontSize = 24.sp, color = redMain)
                }
            }
        }

        // 상단 메인 탭 (차트/주문/정보)
        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color.White,
            contentColor = redMain
        ) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTab == index,
                    onClick = { selectedTab = index },
                    text = {
                        Text(title, color = if (selectedTab == index) redMain else Color.Gray)
                    }
                )
            }
        }

        // 탭별 컨텐츠
        when (selectedTab) {
            0 -> com.example.myapplication.components.ChartTabContent(symbol = symbol, currentPrice = currentPrice)
            1 -> OrderTabContent(currentPrice = currentPrice, symbol = symbol)
            2 -> InfoTabContent(symbol, koreanName, currentPrice) // CoinInfoTab.kt에서 import
        }
    }
}

// OrderTabContent - OrderComponents.kt와 OrderComponents2.kt 호출
@Composable
fun OrderTabContent(
    currentPrice: String = "0",
    symbol: String = "BTC"
) {
    var selectedOrderTab by remember { mutableStateOf(0) }
    val orderTabTitles = listOf("매수", "매도", "내역")

    Column(modifier = Modifier.fillMaxSize()) {
        // 매수/매도/내역 탭
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            orderTabTitles.forEachIndexed { index, title ->
                Card(
                    modifier = Modifier
                        .weight(1f)
                        .clickable { selectedOrderTab = index },
                    backgroundColor = when {
                        selectedOrderTab == index && index == 0 -> redMain  // 매수는 빨간색
                        selectedOrderTab == index && index == 1 -> Color(0xFF085C9B)  // 매도는 파란색
                        selectedOrderTab == index -> Color(0xFF2B9332)  // 내역은 초록색
                        else -> Color(0xFFF5F5F5)
                    },
                    elevation = if (selectedOrderTab == index) 4.dp else 1.dp,
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Box(
                        modifier = Modifier.padding(vertical = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = title,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedOrderTab == index) Color.White else Color.Gray
                        )
                    }
                }
            }
        }

        // 각 탭별 컨텐츠
        when (selectedOrderTab) {
            0 -> {
                // 매수 탭 - OrderComponents.kt의 BuyOrderContent 호출
                key(symbol) {
                    com.example.myapplication.screen.BuyOrderContent(
                        currentPrice = currentPrice,
                        symbol = symbol
                    )
                }
            }
            1 -> {
                // 매도 탭 - OrderComponents2.kt의 SellOrderContent 호출
                key(symbol) {
                    com.example.myapplication.screen.SellOrderContent(
                        currentPrice = currentPrice,
                        symbol = symbol
                    )
                }
            }
            2 -> OrderHistoryContent(symbol = symbol)
        }
    }
}

@Composable
fun OrderHistoryContent(symbol: String = "BTC") {
    OrderHistoryTabsContent(symbol = symbol)
}