package com.example.myapplication

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.AlertDialog
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.URL
import kotlin.system.exitProcess

// KRW 전용 포맷팅 함수 추가
fun formatKRW(value: Double): String {
    return String.format("%,.0f", value)
}

@Composable
fun AssetScreen(
    context: Context = LocalContext.current,
    onNavigateToDeposit: (() -> Unit)? = null
) {
    var selectedTabIndex by remember { mutableStateOf(0) }
    val tabs = listOf("보유자산", "거래내역")

    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    // 뒤로가기 두 번 누르기 관련 상태 추가
    var backPressedTime by remember { mutableStateOf(0L) }
    val backPressedInterval = 2000L // 2초

    // 뒤로가기 버튼 처리 추가
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < backPressedInterval) {
            // 두 번째 뒤로가기 - 앱 종료
            exitProcess(0)
        } else {
            // 첫 번째 뒤로가기 - 토스트 메시지 표시
            backPressedTime = currentTime
            Toast.makeText(context, "한 번 더 누르면 앱이 종료됩니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 탭 인덱스 변경 감지
    Log.d("AssetScreen", "현재 selectedTabIndex: $selectedTabIndex")

    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            "자산 현황",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F),
            modifier = Modifier.padding(16.dp)
        )

        TabRow(
            selectedTabIndex = selectedTabIndex,
            backgroundColor = Color.White,
            contentColor = Color(0xFFD32F2F)
        ) {
            tabs.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = {
                        Log.d("AssetScreen", "탭 클릭: $index ($title)")
                        selectedTabIndex = index
                    },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        Log.d("AssetScreen", "when 분기 전 - selectedTabIndex: $selectedTabIndex")
        when (selectedTabIndex) {
            0 -> {
                Log.d("AssetScreen", "보유자산 탭 선택됨")
                HoldingsTab(context, onNavigateToDeposit, authManager)
            }
            1 -> {
                Log.d("AssetScreen", "거래내역 탭 선택됨")
                TransactionHistoryTab(authManager)
            }
            else -> {
                Log.d("AssetScreen", "알 수 없는 탭 인덱스: $selectedTabIndex")
            }
        }
    }
}

// AuthTokenManager를 사용한 API 호출 함수들
suspend fun fetchUserAssetsWithAuth(authManager: AuthTokenManager, context: Context): Map<String, Double> {
    val result = authManager.makeAuthenticatedRequest(
        url = "${AuthTokenManager.BASE_URL}/mywallet",
        method = "GET"
    )

    return if (result.isSuccess) {
        val responseData = result.getOrNull() ?: ""
        Log.d("AssetScreen", "DB API 응답 데이터: $responseData")

        val jsonObject = JSONObject(responseData)
        val myWalletArray = jsonObject.getJSONArray("myWallet")

        val assetMap = mutableMapOf<String, Double>()
        for (i in 0 until myWalletArray.length()) {
            val assetJson = myWalletArray.getJSONObject(i)
            val symbol = assetJson.getString("symbol")
            val amount = assetJson.getDouble("amount")
            assetMap[symbol] = amount
        }

        Log.d("AssetScreen", "파싱 완료: ${assetMap.size}개 자산")
        assetMap
    } else {
        val error = result.exceptionOrNull()
        Log.e("AssetScreen", "DB API 호출 실패: ${error?.message}")

        if (error?.message?.contains("재로그인") == true) {
            withContext(Dispatchers.Main) {
                handleTokenExpirationWithAuth(authManager, context)
            }
        }
        throw Exception(error?.message ?: "자산 조회 실패")
    }
}

suspend fun fetchCashBalanceWithAuth(authManager: AuthTokenManager, context: Context): Double {
    val result = authManager.makeAuthenticatedRequest(
        url = "${AuthTokenManager.BASE_URL}/user/info",
        method = "GET"
    )

    return if (result.isSuccess) {
        val responseData = result.getOrNull() ?: ""
        Log.d("AssetScreen", "User Info 응답 데이터: $responseData")

        val jsonObject = JSONObject(responseData)
        val cashAmount = jsonObject.optDouble("cash", 0.0)

        Log.d("AssetScreen", "현금 잔고: $cashAmount")
        cashAmount
    } else {
        val error = result.exceptionOrNull()
        Log.e("AssetScreen", "User Info API 호출 실패: ${error?.message}")

        if (error?.message?.contains("재로그인") == true) {
            withContext(Dispatchers.Main) {
                handleTokenExpirationWithAuth(authManager, context)
            }
        }
        throw Exception(error?.message ?: "현금 잔고 조회 실패")
    }
}

suspend fun fetchTradeLogWithAuth(authManager: AuthTokenManager, context: Context): List<TradeLogData> {
    val result = authManager.makeAuthenticatedRequest(
        url = "${AuthTokenManager.BASE_URL}/tradelog",
        method = "GET"
    )

    return if (result.isSuccess) {
        val responseData = result.getOrNull() ?: ""
        Log.d("AssetScreen", "TradeLog 응답 데이터: $responseData")

        val jsonObject = JSONObject(responseData)
        val tradeLogArray = jsonObject.getJSONArray("tradeLog")

        val tradeList = mutableListOf<TradeLogData>()
        for (i in 0 until tradeLogArray.length()) {
            val tradeJson = tradeLogArray.getJSONObject(i)

            val type = tradeJson.getString("type")
            val name = tradeJson.getString("name")
            val symbol = tradeJson.getString("symbol")
            val amount = tradeJson.getDouble("amount")
            val orderPrice = tradeJson.getDouble("orderPrice")
            val orderTime = tradeJson.getString("orderTime")

            Log.d("AssetScreen", "거래 파싱: $symbol - amount: $amount, orderPrice: $orderPrice")

            val trade = TradeLogData(
                type = type,
                name = name,
                symbol = symbol,
                amount = amount,
                orderPrice = orderPrice,
                orderTime = orderTime
            )
            tradeList.add(trade)
        }

        Log.d("AssetScreen", "거래내역 파싱 완료: ${tradeList.size}개")
        tradeList
    } else {
        val error = result.exceptionOrNull()
        Log.e("AssetScreen", "TradeLog API 호출 실패: ${error?.message}")

        if (error?.message?.contains("재로그인") == true) {
            withContext(Dispatchers.Main) {
                handleTokenExpirationWithAuth(authManager, context)
            }
        }
        throw Exception(error?.message ?: "거래내역 조회 실패")
    }
}

// AuthTokenManager를 사용한 토큰 만료 처리 함수
fun handleTokenExpirationWithAuth(authManager: AuthTokenManager, context: Context, message: String = "인증이 만료되었습니다. 다시 로그인해주세요.") {
    Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    Log.e("TokenValidation", "토큰 인증 실패")

    // AuthTokenManager로 토큰 정리
    authManager.logout()

    // app_prefs도 정리 (기존 호환성 유지)
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPref.edit().apply {
        remove("access_token")
        remove("refresh_token")
        putBoolean("auto_login_enabled", false)
        apply()
    }

    // 로그인 페이지로 이동 (기존 액티비티 모두 제거)
    val intent = Intent(context, LoginPage::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
}

// 빗썸 API 호출 함수들 (기존 그대로 유지)
suspend fun fetchBithumbPrices(symbols: Set<String>): Map<String, Double> = withContext(Dispatchers.IO) {
    try {
        Log.d("AssetScreen", "빗썸 API 호출: ${symbols.size}개 심볼")

        val response = URL("https://api.bithumb.com/public/ticker/ALL_KRW").readText()
        val json = JSONObject(response)

        if (json.getString("status") != "0000") {
            throw Exception("빗썸 API 오류: ${json.optString("message", "알 수 없는 오류")}")
        }

        val data = json.getJSONObject("data")
        val priceMap = mutableMapOf<String, Double>()

        for (symbol in symbols) {
            try {
                if (data.has(symbol)) {
                    val coinData = data.getJSONObject(symbol)
                    val price = coinData.getString("closing_price").replace(",", "").toDoubleOrNull()
                    if (price != null && price > 0) {
                        priceMap[symbol] = price
                    }
                } else {
                    Log.w("AssetScreen", "$symbol 데이터를 찾을 수 없습니다.")
                }
            } catch (e: Exception) {
                Log.e("AssetScreen", "$symbol 파싱 중 오류: ${e.message}")
            }
        }

        Log.d("AssetScreen", "빗썸 가격 조회 완료: ${priceMap.size}개")
        priceMap
    } catch (e: Exception) {
        Log.e("AssetScreen", "빗썸 API 호출 실패", e)
        throw e
    }
}

suspend fun fetchKoreanNames(symbols: Set<String>): Map<String, String> = withContext(Dispatchers.IO) {
    try {
        val koreanNames = mutableMapOf<String, String>()
        val marketResponse = URL("https://api.bithumb.com/v1/market/all").readText()
        val marketJson = JSONObject(marketResponse)

        if (marketJson.has("data")) {
            val marketData = marketJson.getJSONArray("data")
            for (i in 0 until marketData.length()) {
                val item = marketData.getJSONObject(i)
                val market = item.getString("market")
                val koreanName = item.getString("korean_name")
                if (market.startsWith("KRW-")) {
                    val symbol = market.removePrefix("KRW-")
                    if (symbols.contains(symbol)) {
                        koreanNames[symbol] = koreanName
                    }
                }
            }
        }

        // 백업 한글명
        val backupNames = mapOf(
            "BTC" to "비트코인", "ETH" to "이더리움", "XRP" to "엑스알피(리플)",
            "USDT" to "테더", "DOGE" to "도지코인", "SOL" to "솔라나", "ENA" to "에테나",
            "PEPE" to "페페", "SHIB" to "시바이누", "SUI" to "수이", "WLD" to "월드코인",
            "SPK" to "스파크", "BONK" to "봉크", "ES" to "이클립스", "XLM" to "스텔라루멘",
            "CFX" to "콘플럭스", "ONDO" to "온도 파이낸스", "FORT" to "포르타",
            "BABY" to "바빌론", "WOO" to "우"
        )

        symbols.forEach { symbol ->
            if (!koreanNames.containsKey(symbol)) {
                koreanNames[symbol] = backupNames[symbol] ?: symbol
            }
        }

        koreanNames
    } catch (e: Exception) {
        Log.w("AssetScreen", "한글명 조회 실패: ${e.message}")
        symbols.associateWith { symbol ->
            mapOf(
                "BTC" to "비트코인", "ETH" to "이더리움", "XRP" to "엑스알피(리플)",
                "USDT" to "테더", "DOGE" to "도지코인", "SOL" to "솔라나", "ENA" to "에테나",
                "PEPE" to "페페", "SHIB" to "시바이누", "SUI" to "수이", "WLD" to "월드코인",
                "SPK" to "스파크", "BONK" to "봉크", "ES" to "이클립스", "XLM" to "스텔라루멘",
                "CFX" to "콘플럭스", "ONDO" to "온도 파이낸스", "FORT" to "포르타",
                "BABY" to "바빌론", "WOO" to "우"
            )[symbol] ?: symbol
        }
    }
}

fun calculateAverageBuyPrice(trades: List<TradeLogData>, symbol: String): Double {
    val symbolTrades = trades.filter { it.symbol == symbol }

    if (symbolTrades.isEmpty()) return 0.0

    var totalBuyValue = 0.0
    var totalBuyAmount = 0.0
    var currentHolding = 0.0

    val sortedTrades = symbolTrades.sortedBy { it.orderTime }

    for (trade in sortedTrades) {
        when (trade.type) {
            "BUY" -> {
                totalBuyValue += trade.amount * trade.orderPrice
                totalBuyAmount += trade.amount
                currentHolding += trade.amount
            }
            "SELL" -> {
                if (currentHolding > 0) {
                    val sellRatio = minOf(trade.amount / currentHolding, 1.0)
                    totalBuyValue *= (1.0 - sellRatio)
                    totalBuyAmount *= (1.0 - sellRatio)
                    currentHolding -= trade.amount

                    if (currentHolding <= 0.001) {
                        totalBuyValue = 0.0
                        totalBuyAmount = 0.0
                        currentHolding = 0.0
                    }
                }
            }
        }
    }

    return if (totalBuyAmount > 0) totalBuyValue / totalBuyAmount else 0.0
}

@Composable
fun HoldingsTab(
    context: Context,
    onNavigateToDeposit: (() -> Unit)? = null,
    authManager: AuthTokenManager
) {
    // DB 데이터 상태
    var userAssets by remember { mutableStateOf(mapOf<String, Double>()) }
    var krwBalance by remember { mutableStateOf(0.0) }
    var tradeHistory by remember { mutableStateOf(listOf<TradeLogData>()) }

    // 빗썸 실시간 데이터 상태
    var currentPrices by remember { mutableStateOf(mapOf<String, Double>()) }
    var koreanNames by remember { mutableStateOf(mapOf<String, String>()) }

    // 최종 표시 데이터
    var coinHoldings by remember { mutableStateOf(listOf<AssetData>()) }

    // UI 상태
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var isRefreshing by remember { mutableStateOf(false) }
    var dbDataLoaded by remember { mutableStateOf(false) }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    // AuthTokenManager를 사용한 DB 데이터 가져오기
    val fetchDbData = {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isNetworkAvailable(context)) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "인터넷 연결이 없습니다."
                        showErrorDialog = true
                        isRefreshing = false
                    }
                    return@launch
                }

                Log.d("AssetScreen", "AuthTokenManager로 DB 데이터 가져오기 시작")

                val trades = try {
                    fetchTradeLogWithAuth(authManager, context)
                } catch (e: Exception) {
                    Log.e("AssetScreen", "거래내역 조회 실패: ${e.message}")
                    emptyList()
                }

                val assets = try {
                    fetchUserAssetsWithAuth(authManager, context)
                } catch (e: Exception) {
                    Log.e("AssetScreen", "사용자 자산 조회 실패: ${e.message}")
                    if (e.message?.contains("재로그인") != true) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "자산 조회 실패: ${e.message}"
                            showErrorDialog = true
                            isRefreshing = false
                        }
                    }
                    return@launch
                }

                val cash = try {
                    fetchCashBalanceWithAuth(authManager, context)
                } catch (e: Exception) {
                    Log.e("AssetScreen", "현금 잔고 조회 실패: ${e.message}")
                    if (e.message?.contains("재로그인") != true) {
                        0.0
                    } else {
                        return@launch
                    }
                }

                withContext(Dispatchers.Main) {
                    userAssets = assets
                    krwBalance = cash
                    tradeHistory = trades
                    dbDataLoaded = true
                    isRefreshing = false
                    Log.d("AssetScreen", "DB 데이터 업데이트 완료")
                }

            } catch (e: Exception) {
                Log.e("AssetScreen", "DB 데이터 수집 실패", e)
                if (e.message?.contains("재로그인") != true) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "DB 데이터 조회 실패: ${e.message}"
                        showErrorDialog = true
                        isRefreshing = false
                    }
                }
            }
        }
    }

    // 빗썸 실시간 가격 가져오기
    val fetchBithumbData = {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isNetworkAvailable(context) || userAssets.isEmpty()) {
                    return@launch
                }

                val symbols = userAssets.keys.toSet()
                Log.d("AssetScreen", "빗썸 실시간 데이터 가져오기: ${symbols.size}개 심볼")

                val prices = try {
                    fetchBithumbPrices(symbols)
                } catch (e: Exception) {
                    Log.e("AssetScreen", "가격 조회 실패: ${e.message}")
                    return@launch
                }

                val names = if (koreanNames.isEmpty()) {
                    try {
                        fetchKoreanNames(symbols)
                    } catch (e: Exception) {
                        Log.e("AssetScreen", "한글명 조회 실패: ${e.message}")
                        emptyMap()
                    }
                } else {
                    koreanNames
                }

                withContext(Dispatchers.Main) {
                    currentPrices = prices
                    if (names.isNotEmpty()) {
                        koreanNames = names
                    }
                    Log.d("AssetScreen", "빗썸 실시간 데이터 업데이트 완료")
                }

            } catch (e: Exception) {
                Log.e("AssetScreen", "빗썸 데이터 수집 실패", e)
            }
        }
    }

    // 최종 코인 홀딩 데이터 생성
    LaunchedEffect(userAssets, currentPrices, koreanNames, tradeHistory) {
        if (userAssets.isNotEmpty() && currentPrices.isNotEmpty()) {
            val resultList = mutableListOf<AssetData>()

            for ((symbol, amount) in userAssets) {
                val price = currentPrices[symbol]
                if (price != null && price > 0 && amount > 0) {
                    val avgBuyPrice = calculateAverageBuyPrice(tradeHistory, symbol)
                    val koreanName = koreanNames[symbol] ?: symbol
                    val evaluationValue = price * amount

                    if (amount >= 0.000001 && evaluationValue >= 1.0) {
                        resultList.add(AssetData(symbol, koreanName, price, amount, avgBuyPrice))
                    }
                }
            }

            coinHoldings = resultList
            Log.d("AssetScreen", "최종 코인 홀딩 업데이트: ${resultList.size}개")
        }
    }

    // 초기 DB 데이터 로드
    LaunchedEffect(Unit) {
        fetchDbData()
    }

    // 빗썸 실시간 데이터 (3초마다)
    LaunchedEffect(dbDataLoaded) {
        if (dbDataLoaded) {
            fetchBithumbData()
            while (true) {
                delay(3000)
                fetchBithumbData()
            }
        }
    }

    val coinTotalValue = coinHoldings.sumOf { it.price * it.amount }
    val coinTotalBuyValue = coinHoldings.sumOf { it.avgBuyPrice * it.amount }
    val coinProfitLoss = coinTotalValue - coinTotalBuyValue
    val totalValue = coinTotalValue + krwBalance

    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = {
            isRefreshing = true
            fetchDbData()
        },
        indicator = { state, trigger ->
            SwipeRefreshIndicator(
                state = state,
                refreshTriggerDistance = trigger,
                backgroundColor = Color.White,
                contentColor = Color(0xFFD32F2F)
            )
        }
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
        ) {
            item {
                MainAssetBoard(
                    krwBalance = krwBalance,
                    totalAsset = totalValue,
                    onNavigateToDeposit = onNavigateToDeposit
                )
            }

            item {
                AssetDetailCard(
                    krwBalance = krwBalance,
                    coinTotalValue = coinTotalValue,
                    totalBuyValue = coinTotalBuyValue,
                    profitLoss = coinProfitLoss
                )
            }

            item {
                AssetPieChart(
                    krwBalance = krwBalance,
                    coinHoldings = coinHoldings
                )
            }

            items(coinHoldings) { asset ->
                AssetItem(asset)
                Divider(color = Color.LightGray, thickness = 0.5.dp)
            }

            if (coinHoldings.isEmpty() && dbDataLoaded) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("보유 코인이 없습니다.", color = Color.Gray)
                    }
                }
            }

            if (!dbDataLoaded) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Color(0xFFD32F2F))
                    }
                }
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("오류") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = false
                    isRefreshing = true
                    fetchDbData()
                }) {
                    Text("재시도")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
fun MainAssetBoard(
    krwBalance: Double,
    totalAsset: Double,
    onNavigateToDeposit: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color(0xFFF5F5F5),
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "내 보유자산",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "총 보유자산",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        formatKRW(totalAsset) + " KRW",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "보유 KRW",
                        fontSize = 12.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        formatKRW(krwBalance) + " KRW",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF333333)
                    )
                }
            }
        }
    }
}

@Composable
fun AssetDetailCard(
    krwBalance: Double,
    coinTotalValue: Double,
    totalBuyValue: Double,
    profitLoss: Double
) {
    val profitLossRate = if (totalBuyValue > 0) (profitLoss / totalBuyValue) * 100 else 0.0
    val profitLossColor = if (profitLoss >= 0) Color(0xFFFF5722) else Color(0xFF2196F3)
    val profitLossSign = if (profitLoss >= 0) "+" else "-"

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "자산 상세 정보",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "총평가",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        formatKRW(coinTotalValue) + " KRW",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "총매수",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        formatKRW(totalBuyValue) + " KRW",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "주문가능",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        formatKRW(krwBalance) + " KRW",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color(0xFF333333)
                    )
                }

                Column(
                    modifier = Modifier.weight(1f),
                    horizontalAlignment = Alignment.End
                ) {
                    Text(
                        "평가손익",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        "${profitLossSign}${formatKRW(kotlin.math.abs(profitLoss))} KRW",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = profitLossColor
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        "수익률",
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                    Text(
                        "${profitLossSign}${String.format("%.2f", kotlin.math.abs(profitLossRate))}%",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = profitLossColor
                    )
                }
            }
        }
    }
}

@Composable
fun AssetPieChart(
    krwBalance: Double,
    coinHoldings: List<AssetData>
) {
    val totalValue = krwBalance + coinHoldings.sumOf { it.price * it.amount }

    if (totalValue <= 0) return

    val chartData = mutableListOf<ChartData>()

    if (krwBalance > 0) {
        val percentage = (krwBalance / totalValue) * 100
        chartData.add(ChartData("KRW", "원화", percentage, Color(0xFF2196F3)))
    }

    val colors = listOf(
        Color(0xFF8BC34A), Color(0xFFFF9800), Color(0xFF9C27B0),
        Color(0xFFE91E63), Color(0xFF00BCD4), Color(0xFFFFEB3B),
        Color(0xFF795548), Color(0xFF607D8B), Color(0xFF4CAF50),
        Color(0xFFF44336), Color(0xFF3F51B5), Color(0xFF009688)
    )

    coinHoldings.forEachIndexed { index, asset ->
        val value = asset.price * asset.amount
        if (value > 0) {
            val percentage = (value / totalValue) * 100
            val color = colors[index % colors.size]
            chartData.add(ChartData(asset.symbol, asset.koreanName, percentage, color))
        }
    }

    chartData.sortByDescending { it.percentage }

    val filteredChartData = mutableListOf<ChartData>()
    var etcPercentage = 0.0

    chartData.forEach { data ->
        if (data.percentage >= 0.5) {
            filteredChartData.add(data)
        } else {
            etcPercentage += data.percentage
        }
    }

    if (etcPercentage > 0) {
        filteredChartData.add(ChartData("ETC", "기타", etcPercentage, Color(0xFF9E9E9E)))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                "보유자산 포트폴리오",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier.size(140.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val centerX = size.width / 2
                        val centerY = size.height / 2
                        val outerRadius = minOf(centerX, centerY) * 0.9f
                        val innerRadius = outerRadius * 0.6f

                        var startAngle = 0f

                        filteredChartData.forEach { data ->
                            val sweepAngle = (data.percentage / 100 * 360).toFloat()

                            drawArc(
                                color = data.color,
                                startAngle = startAngle,
                                sweepAngle = sweepAngle,
                                useCenter = true,
                                topLeft = Offset(centerX - outerRadius, centerY - outerRadius),
                                size = Size(outerRadius * 2, outerRadius * 2)
                            )

                            startAngle += sweepAngle
                        }

                        drawCircle(
                            color = androidx.compose.ui.graphics.Color.White,
                            radius = innerRadius,
                            center = Offset(centerX, centerY)
                        )
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filteredChartData.forEach { data ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(12.dp)
                                    .clip(CircleShape)
                            ) {
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(data.color)
                                }
                            }
                            Text(
                                data.symbol,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.width(40.dp)
                            )
                            Text(
                                "${String.format("%.1f", data.percentage)}%",
                                fontSize = 12.sp,
                                color = Color.Gray
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TransactionHistoryTab(authManager: AuthTokenManager) {
    val context = LocalContext.current

    var transactions by remember { mutableStateOf(listOf<TradeLogData>()) }
    var isLoading by remember { mutableStateOf(true) }
    var showErrorDialog by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    fun isNetworkAvailable(context: Context): Boolean {
        val connectivityManager = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = connectivityManager.activeNetwork ?: return false
        val capabilities = connectivityManager.getNetworkCapabilities(network) ?: return false
        return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    val fetchTransactions = {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isNetworkAvailable(context)) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "인터넷 연결이 없습니다."
                        showErrorDialog = true
                        isLoading = false
                    }
                    return@launch
                }

                val tradeData = try {
                    fetchTradeLogWithAuth(authManager, context)
                } catch (e: Exception) {
                    Log.e("TransactionHistory", "거래내역 조회 실패: ${e.message}")
                    if (e.message?.contains("재로그인") != true) {
                        withContext(Dispatchers.Main) {
                            errorMessage = "거래내역 조회 실패: ${e.message}"
                            showErrorDialog = true
                            isLoading = false
                        }
                    }
                    return@launch
                }

                withContext(Dispatchers.Main) {
                    transactions = tradeData.sortedByDescending { it.orderTime }
                    isLoading = false
                }

            } catch (e: Exception) {
                Log.e("TransactionHistory", "데이터 수집 실패", e)
                if (e.message?.contains("재로그인") != true) {
                    withContext(Dispatchers.Main) {
                        errorMessage = "에러: ${e.message}"
                        showErrorDialog = true
                        isLoading = false
                    }
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchTransactions()
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFD32F2F))
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
        ) {
            items(transactions) { transaction ->
                TransactionHistoryItem(transaction)
            }

            if (transactions.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(200.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("거래 내역이 없습니다.", color = Color.Gray)
                    }
                }
            }
        }
    }

    if (showErrorDialog) {
        AlertDialog(
            onDismissRequest = { showErrorDialog = false },
            title = { Text("오류") },
            text = { Text(errorMessage) },
            confirmButton = {
                TextButton(onClick = {
                    showErrorDialog = false
                    isLoading = true
                    fetchTransactions()
                }) {
                    Text("재시도")
                }
            },
            dismissButton = {
                TextButton(onClick = { showErrorDialog = false }) {
                    Text("확인")
                }
            }
        )
    }
}

@Composable
fun TransactionHistoryItem(transaction: TradeLogData) {
    val typeText = when (transaction.type) {
        "BUY" -> "매수"
        "SELL" -> "매도"
        else -> transaction.type
    }

    val typeColor = when (transaction.type) {
        "BUY" -> Color(0xFF4CAF50)
        "SELL" -> Color(0xFFF44336)
        else -> Color.Gray
    }

    val totalValue = transaction.amount * transaction.orderPrice

    val formattedDate = try {
        transaction.orderTime.replace("T", " ").substring(0, 19)
    } catch (e: Exception) {
        transaction.orderTime
    }

    // 수량과 가격에 대한 포맷팅 함수 (코인 수량은 소수점 유지)
    fun formatCoinAmount(value: Double): String {
        return when {
            value == 0.0 -> "0"
            value >= 1000 -> {
                val formatted = String.format("%.2f", value).trimEnd('0').trimEnd('.')
                if (formatted.contains('.')) {
                    val parts = formatted.split('.')
                    "${String.format("%,d", parts[0].toLong())}.${parts[1]}"
                } else {
                    String.format("%,d", formatted.toLong())
                }
            }
            else -> {
                val multiplier = 1000000.0 // 10^6
                val truncated = kotlin.math.floor(value * multiplier) / multiplier
                String.format("%.6f", truncated).trimEnd('0').trimEnd('.')
            }
        }
    }

    val amountText = "${formatCoinAmount(transaction.amount)} ${transaction.symbol}"
    val priceText = "${formatKRW(transaction.orderPrice)} KRW"
    val totalValueText = "${formatKRW(totalValue)} KRW"

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = 2.dp,
        backgroundColor = Color.White
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            typeText,
                            color = typeColor,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "${transaction.name} (${transaction.symbol})",
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "수량: $amountText",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                    Text(
                        "단가: $priceText",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        totalValueText,
                        fontWeight = FontWeight.Bold,
                        fontSize = 16.sp
                    )
                    Text(
                        formattedDate,
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}

@Composable
fun AssetItem(asset: AssetData) {
    val totalValue = asset.price * asset.amount
    val totalBuyValue = asset.avgBuyPrice * asset.amount
    val profitLoss = totalValue - totalBuyValue
    val profitLossRate = if (totalBuyValue > 0) (profitLoss / totalBuyValue) * 100 else 0.0
    val profitLossColor = if (profitLoss >= 0) Color(0xFFFF5722) else Color(0xFF2196F3)
    val profitLossSign = if (profitLoss >= 0) "+" else "-"

    // 코인 수량 전용 포맷팅 함수 (소수점 유지)
    fun formatCoinAmount(value: Double): String {
        return when {
            value == 0.0 -> "0"
            value >= 1e9 -> String.format("%,.0f", value)
            value >= 1e6 -> {
                val formatted = String.format("%.2f", value).trimEnd('0').trimEnd('.')
                if (formatted.contains('.')) {
                    val parts = formatted.split('.')
                    "${String.format("%,d", parts[0].toLong())}.${parts[1]}"
                } else {
                    String.format("%,d", formatted.toLong())
                }
            }
            value >= 1000 -> {
                val formatted = String.format("%.2f", value).trimEnd('0').trimEnd('.')
                if (formatted.contains('.')) {
                    val parts = formatted.split('.')
                    "${String.format("%,d", parts[0].toLong())}.${parts[1]}"
                } else {
                    String.format("%,d", formatted.toLong())
                }
            }
            value >= 1.0 -> String.format("%.6f", value).trimEnd('0').trimEnd('.')
            value >= 0.0001 -> String.format("%.8f", value).trimEnd('0').trimEnd('.')
            value > 0 -> String.format("%.12f", value).trimEnd('0').trimEnd('.')
            else -> "0"
        }
    }

    val amountText = "${formatCoinAmount(asset.amount)} ${asset.symbol}"
    val evaluationValueText = "${formatKRW(totalValue)} KRW"
    val avgBuyPriceText = "${formatKRW(asset.avgBuyPrice)} KRW"

    Column(modifier = Modifier.padding(20.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    asset.koreanName,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333)
                )
                Text(
                    asset.symbol,
                    fontSize = 14.sp,
                    color = Color.Gray
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "${profitLossSign}${formatKRW(kotlin.math.abs(profitLoss))} KRW",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = profitLossColor
                )
                Text(
                    "${profitLossSign}${String.format("%.2f", kotlin.math.abs(profitLossRate))}%",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = profitLossColor
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    "보유량",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Text(
                    amountText,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "평가금액",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Text(
                    evaluationValueText,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    "매수가",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
                Text(
                    avgBuyPriceText,
                    fontSize = 14.sp,
                    color = Color(0xFF333333),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

data class AssetData(
    val symbol: String,
    val koreanName: String,
    val price: Double,
    val amount: Double,
    val avgBuyPrice: Double = 0.0
)

data class TransactionData(
    val symbol: String,
    val koreanName: String,
    val type: String,
    val amount: Double,
    val price: Double,
    val date: String
)

data class TradeLogData(
    val type: String,
    val name: String,
    val symbol: String,
    val amount: Double,
    val orderPrice: Double,
    val orderTime: String
)

data class ChartData(
    val symbol: String,
    val name: String,
    val percentage: Double,
    val color: Color
)