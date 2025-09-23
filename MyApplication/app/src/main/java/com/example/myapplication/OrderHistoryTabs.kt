// OrderHistoryTabs.kt
package com.example.myapplication.screen

import AuthTokenManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.example.myapplication.LoginPage

// 거래 로그 데이터 클래스 (기존 ApiService와 동일한 구조)
data class TradeLogItem(
    val type: String, // "BUY" or "SELL"
    val name: String,
    val symbol: String,
    val amount: Double,
    val orderPrice: Double,
    val orderTime: String
)

// TradeLogResponse 클래스 (ApiService와 호환성을 위해 필요)
data class TradeLogResponse(
    val tradeLog: List<TradeLogItem>
)

// AuthTokenManager를 사용한 거래 로그 데이터 가져오는 함수
suspend fun fetchTradeLog(symbol: String, context: Context): List<TradeLogItem> {
    return try {
        val authManager = AuthTokenManager(context)

        // 토큰 가져오기
        val accessToken = try {
            authManager.getValidAccessToken()
        } catch (e: Exception) {
            android.util.Log.e("TradeLog", "토큰 가져오기 실패: ${e.message}")
            return emptyList()
        }

        if (accessToken == null) {
            android.util.Log.e("TradeLog", "토큰이 null입니다")
            return emptyList()
        }

        // 기존 ApiService 사용
        val response = withContext(Dispatchers.IO) {
            com.example.myapplication.RetrofitClient.apiService.getTradeLog(symbol, accessToken)
        }

        if (response.isSuccessful) {
            val tradeLogList = response.body()?.tradeLog ?: emptyList()
            android.util.Log.d("TradeLog", "거래 내역 조회 성공: ${tradeLogList.size}개")

            // 최신순으로 정렬 (orderTime 기준 내림차순)
            tradeLogList.sortedWith(compareByDescending<TradeLogItem> { tradeLog ->
                try {
                    val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                    dateFormat.parse(tradeLog.orderTime)
                } catch (e: Exception) {
                    try {
                        // 백업 포맷 시도 (초 단위까지만)
                        val backupFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                        backupFormat.parse(tradeLog.orderTime)
                    } catch (e2: Exception) {
                        Date(0) // 파싱 실패시 가장 오래된 날짜로 설정
                    }
                }
            })
        } else {
            android.util.Log.e("TradeLog", "거래 내역 조회 실패: ${response.code()}")

            // 401 오류 시 토큰 갱신 시도
            if (response.code() == 401) {
                android.util.Log.d("TradeLog", "401 오류 - AuthTokenManager를 통한 토큰 갱신 시도")

                // AuthTokenManager의 makeAuthenticatedRequest를 통해 토큰 갱신 유도
                val refreshResult = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/order/info/$symbol",
                    method = "GET"
                )

                refreshResult.fold(
                    onSuccess = {
                        android.util.Log.d("TradeLog", "토큰 갱신 성공 - 거래 내역 재조회")
                        // 새 토큰으로 재시도
                        val newAccessToken = authManager.getValidAccessToken()
                        if (newAccessToken != null) {
                            val retryResponse = withContext(Dispatchers.IO) {
                                com.example.myapplication.RetrofitClient.apiService.getTradeLog(symbol, newAccessToken)
                            }

                            if (retryResponse.isSuccessful) {
                                val tradeLogList = retryResponse.body()?.tradeLog ?: emptyList()
                                android.util.Log.d("TradeLog", "재시도 성공: ${tradeLogList.size}개")
                                return@fold tradeLogList.sortedWith(compareByDescending<TradeLogItem> { tradeLog ->
                                    try {
                                        val dateFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
                                        dateFormat.parse(tradeLog.orderTime)
                                    } catch (e: Exception) {
                                        try {
                                            val backupFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                                            backupFormat.parse(tradeLog.orderTime)
                                        } catch (e2: Exception) {
                                            Date(0)
                                        }
                                    }
                                })
                            }
                        }
                        emptyList<TradeLogItem>()
                    },
                    onFailure = { exception ->
                        android.util.Log.e("TradeLog", "토큰 갱신 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""
                        if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                            val intent = Intent(context, LoginPage::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        }
                        emptyList<TradeLogItem>()
                    }
                )
            } else {
                emptyList()
            }
        }
    } catch (e: Exception) {
        android.util.Log.e("TradeLog", "거래 내역 조회 예외: ${e.message}")
        emptyList()
    }
}

// 시간 포맷팅 함수
fun formatOrderTime(orderTime: String): String {
    return try {
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSSS", Locale.getDefault())
        val outputFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        val date = inputFormat.parse(orderTime)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        try {
            // 백업 포맷 시도
            val backupInputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
            val date = backupInputFormat.parse(orderTime)
            outputFormat.format(date ?: Date())
        } catch (e2: Exception) {
            orderTime.substringBefore('T')
        }
    }
}

// 가격 포맷팅 함수 (콤마 추가)
fun formatPriceWithComma(price: Double): String {
    return String.format("%,.0f", price)
}

// 체결가격용 포맷팅 함수 (소수점 3자리까지)
fun formatExecutedPrice(price: Double): String {
    return String.format("%,.3f", price)
}

// 체결 완료 주문 컨텐츠
@Composable
fun CompletedOrdersContent(tradeLogList: List<TradeLogItem>, isLoading: Boolean) {
    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
    } else if (tradeLogList.isEmpty()) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Text("체결된 거래 내역이 없습니다", color = Color.Gray)
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            contentPadding = PaddingValues(bottom = 80.dp) // 메뉴바 고려한 하단 여백
        ) {
            items(tradeLogList) { tradeLog ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                ) {
                    // 매수/매도와 시간
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = if (tradeLog.type == "BUY") "매수" else "매도",
                            fontSize = 16.sp,
                            color = if (tradeLog.type == "BUY") Color.Red else Color.Blue
                        )
                        Text(
                            text = formatOrderTime(tradeLog.orderTime),
                            fontSize = 12.sp,
                            color = Color.Gray
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    // 마켓
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "마켓",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${tradeLog.symbol}/KRW",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }

                    // 체결가격 (소수점 3자리까지 표시)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "체결가격",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${formatExecutedPrice(tradeLog.orderPrice)} 원",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }

                    // 체결수량
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "체결수량",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${String.format("%.8f", tradeLog.amount)} ${tradeLog.symbol}",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }

                    // 체결금액 (소수점 3자리까지 표시)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "체결금액",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${formatExecutedPrice(tradeLog.orderPrice * tradeLog.amount)} 원",
                            fontSize = 14.sp,
                            color = Color.Black
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // 구분선 추가 (마지막 아이템이 아닐 때만)
                    if (tradeLogList.indexOf(tradeLog) < tradeLogList.size - 1) {
                        Divider(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            thickness = 1.dp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

// 미체결 주문 컨텐츠
@Composable
fun PendingOrdersContent() {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "미체결 주문이 없습니다",
                fontSize = 16.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "현재는 시장가 주문만 지원되어\n모든 주문이 즉시 체결됩니다",
                fontSize = 14.sp,
                color = Color.Gray.copy(alpha = 0.7f)
            )
        }
    }
}

// 메인 주문 내역 탭 컨텐츠
@Composable
fun OrderHistoryTabsContent(symbol: String = "BTC") {
    var selectedHistoryTab by remember { mutableStateOf(0) }
    val historyTabTitles = listOf("체결", "미체결")
    val context = LocalContext.current

    // 거래 로그 상태
    var tradeLogList by remember { mutableStateOf<List<TradeLogItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // 컴포넌트가 시작될 때 데이터 로드
    LaunchedEffect(symbol) {
        isLoading = true
        tradeLogList = fetchTradeLog(symbol, context)
        isLoading = false
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 체결/미체결 탭
        TabRow(
            selectedTabIndex = selectedHistoryTab,
            backgroundColor = Color.White
        ) {
            historyTabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedHistoryTab == index,
                    onClick = { selectedHistoryTab = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedHistoryTab == index) Color.Red else Color.Gray
                        )
                    }
                )
            }
        }

        // 각 탭별 컨텐츠
        when (selectedHistoryTab) {
            0 -> CompletedOrdersContent(tradeLogList, isLoading)
            1 -> PendingOrdersContent()
        }
    }
}