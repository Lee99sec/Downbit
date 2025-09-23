package com.example.myapplication.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
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
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject

@Composable
fun InfoTabContent(symbol: String, koreanName: String, price: String) {
    // 코인 상세 정보 상태
    var coinInfo by remember { mutableStateOf<CoinDetailInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var apiError by remember { mutableStateOf<String?>(null) }

    // 코인 정보 로드
    LaunchedEffect(symbol) {
        try {
            apiError = null
            val client = OkHttpClient()
            val request = Request.Builder()
                .url("https://api.bithumb.com/public/ticker/${symbol}_KRW")
                .get()
                .addHeader("accept", "application/json")
                .build()

            val response = withContext(Dispatchers.IO) {
                client.newCall(request).execute()
            }

            if (response.isSuccessful) {
                val responseBody = response.body?.string()
                if (responseBody != null) {
                    println("API Response: $responseBody") // 디버깅용

                    val jsonResponse = JSONObject(responseBody)
                    val status = jsonResponse.getString("status")

                    if (status == "0000") { // 빗썸 API 성공 코드
                        val data = jsonResponse.getJSONObject("data")

                        coinInfo = CoinDetailInfo(
                            market = "${symbol}_KRW",
                            openingPrice = data.getString("opening_price").toDouble(),
                            highPrice = data.getString("max_price").toDouble(),
                            lowPrice = data.getString("min_price").toDouble(),
                            tradePrice = data.getString("closing_price").toDouble(),
                            prevClosingPrice = data.getString("prev_closing_price").toDouble(),
                            change = determineChange(
                                data.getString("closing_price").toDouble(),
                                data.getString("prev_closing_price").toDouble()
                            ),
                            changePrice = kotlin.math.abs(
                                data.getString("closing_price").toDouble() -
                                        data.getString("prev_closing_price").toDouble()
                            ),
                            changeRate = kotlin.math.abs(
                                data.getString("fluctate_rate_24H").toDouble()
                            ),
                            accTradePrice24h = data.getString("acc_trade_value_24H").toDouble(),
                            accTradeVolume24h = data.getString("units_traded_24H").toDouble(),
                            highest52WeekPrice = data.getString("max_price").toDouble(), // 빗썸에는 52주 데이터가 없어서 일일 최고가로 대체
                            highest52WeekDate = getCurrentDate(),
                            lowest52WeekPrice = data.getString("min_price").toDouble(), // 빗썸에는 52주 데이터가 없어서 일일 최저가로 대체
                            lowest52WeekDate = getCurrentDate()
                        )
                        println("Coin info loaded successfully for: ${symbol}_KRW")
                    } else {
                        val message = jsonResponse.optString("message", "Unknown error")
                        if (message.contains("Invalid currency") || status == "5600") {
                            apiError = "NOT_LISTED"
                            println("Coin not supported on Bithumb: $symbol")
                        } else {
                            apiError = "API_ERROR"
                            println("Bithumb API error: $message")
                        }
                    }
                }
            } else {
                apiError = "API_ERROR"
                println("API request failed with code: ${response.code}")
                println("Response message: ${response.message}")
            }
            response.close()
            isLoading = false
        } catch (e: Exception) {
            apiError = "NETWORK_ERROR"
            println("Error loading coin info: ${e.message}")
            e.printStackTrace()
            isLoading = false
        }
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(color = redMain)
                Spacer(modifier = Modifier.height(8.dp))
                Text("정보를 불러오는 중...", fontSize = 14.sp, color = Color.Gray)
            }
        }
    } else if (coinInfo != null) {
        // 기존 코인 정보 표시 코드 유지
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .padding(bottom = 56.dp), // 바텀 네비게이션 바 높이만큼 추가 패딩
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // 기본 정보
            item {
                InfoCard(
                    title = "기본 정보",
                    items = listOf(
                        "코인명" to koreanName,
                        "심볼" to symbol,
                        "마켓" to coinInfo!!.market
                    )
                )
            }

            // 가격 정보
            item {
                InfoCard(
                    title = "가격 정보",
                    items = listOf(
                        "현재가" to "${formatPrice(coinInfo!!.tradePrice.toString())} KRW",
                        "시가" to "${formatPrice(coinInfo!!.openingPrice.toString())} KRW",
                        "고가" to "${formatPrice(coinInfo!!.highPrice.toString())} KRW",
                        "저가" to "${formatPrice(coinInfo!!.lowPrice.toString())} KRW",
                        "전일종가" to "${formatPrice(coinInfo!!.prevClosingPrice.toString())} KRW"
                    )
                )
            }

            // 변동 정보
            item {
                val changeColor = when (coinInfo!!.change) {
                    "RISE" -> Color(0xFFD32F2F)
                    "FALL" -> Color(0xFF1976D2)
                    else -> Color.Gray
                }
                val changeSymbol = when (coinInfo!!.change) {
                    "RISE" -> "+"
                    "FALL" -> "-"
                    else -> ""
                }

                InfoCard(
                    title = "변동 정보",
                    items = listOf(
                        "등락" to when(coinInfo!!.change) {
                            "RISE" -> "상승"
                            "FALL" -> "하락"
                            "EVEN" -> "보합"
                            else -> coinInfo!!.change
                        },
                        "변동금액" to "${changeSymbol}${formatPrice(coinInfo!!.changePrice.toString())} KRW",
                        "변동률" to "${changeSymbol}${String.format("%.2f", coinInfo!!.changeRate)}%"
                    ),
                    valueColor = changeColor
                )
            }

            // 거래 정보
            item {
                InfoCard(
                    title = "24시간 거래 정보",
                    items = listOf(
                        "거래대금" to "${formatPrice((coinInfo!!.accTradePrice24h / 1000000).toString())}M KRW",
                        "거래량" to String.format("%.4f", coinInfo!!.accTradeVolume24h)
                    )
                )
            }

            // 일일 최고/최저 (빗썸은 52주 데이터가 없음)
            item {
                InfoCard(
                    title = "당일 최고/최저",
                    items = listOf(
                        "당일 최고가" to "${formatPrice(coinInfo!!.highest52WeekPrice.toString())} KRW",
                        "당일 최저가" to "${formatPrice(coinInfo!!.lowest52WeekPrice.toString())} KRW",
                        "기준일" to coinInfo!!.highest52WeekDate
                    )
                )
            }
        }
    } else {
        // 에러 메시지 표시
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = 56.dp), // 바텀 네비게이션 바 높이만큼 추가 패딩
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                when (apiError) {
                    "NOT_LISTED" -> {
                        Icon(
                            painter = painterResource(id = android.R.drawable.ic_dialog_info),
                            contentDescription = "정보",
                            tint = Color(0xFFFF9800),
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "상장되지 않은 코인",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFFF9800)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "$symbol($koreanName)는 현재 빗썸에 상장되지 않은 코인입니다.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "다른 거래소에서 거래 중일 수 있습니다.",
                            fontSize = 12.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                    "API_ERROR" -> {
                        Text(
                            text = "API 서버 오류",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "서버에서 데이터를 가져올 수 없습니다.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                    "NETWORK_ERROR" -> {
                        Text(
                            text = "네트워크 오류",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFFD32F2F)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "네트워크 연결을 확인해주세요.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                    else -> {
                        Text(
                            text = "정보를 불러올 수 없습니다",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "심볼: $symbol",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }
        }
    }
}

@Composable
fun InfoCard(
    title: String,
    items: List<Pair<String, String>>,
    valueColor: Color = Color.Black
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White,
        shape = RoundedCornerShape(12.dp),
        elevation = 2.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = redMain,
                modifier = Modifier.padding(bottom = 12.dp)
            )

            items.forEach { (label, value) ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = label,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = value,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = valueColor,
                        textAlign = TextAlign.End,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

// 헬퍼 함수들
private fun determineChange(currentPrice: Double, prevPrice: Double): String {
    return when {
        currentPrice > prevPrice -> "RISE"
        currentPrice < prevPrice -> "FALL"
        else -> "EVEN"
    }
}

private fun getCurrentDate(): String {
    val formatter = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.getDefault())
    return formatter.format(java.util.Date())
}

// 코인 상세 정보 데이터 클래스
data class CoinDetailInfo(
    val market: String,
    val openingPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val tradePrice: Double,
    val prevClosingPrice: Double,
    val change: String,
    val changePrice: Double,
    val changeRate: Double,
    val accTradePrice24h: Double,
    val accTradeVolume24h: Double,
    val highest52WeekPrice: Double,
    val highest52WeekDate: String,
    val lowest52WeekPrice: Double,
    val lowest52WeekDate: String
)