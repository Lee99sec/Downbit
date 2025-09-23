package com.example.myapplication.screen

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.EaseInQuart
import androidx.compose.animation.core.EaseOutQuart
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.R
import com.example.myapplication.RetrofitClient
import com.example.myapplication.LoginPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import kotlin.system.exitProcess

val redMain = Color(0xFFE53E3E)

data class CoinInfo(
    val symbol: String,
    val koreanName: String,
    val price: String,
    val changeRate: String
)

data class HoldingCoinInfo(
    val symbol: String,
    val koreanName: String,
    val price: Double,
    val amount: Double
)

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}

fun formatPrice(price: String): String {
    return try {
        val number = price.toDouble()
        val formatter = DecimalFormat("###,##0.###")
        formatter.format(number)
    } catch (e: Exception) {
        price
    }
}

fun formatDecimal(value: Double): String {
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

// AuthTokenManager를 사용한 사용자 보유 자산 API 호출 (토큰 갱신 로직 포함)
suspend fun fetchUserAssets(context: Context): Map<String, Double> = withContext(Dispatchers.IO) {
    try {
        val authManager = AuthTokenManager(context)

        // AuthTokenManager의 makeAuthenticatedRequest 사용하여 자동 토큰 갱신 지원
        val result = authManager.makeAuthenticatedRequest(
            url = "${AuthTokenManager.BASE_URL}/mywallet",
            method = "GET"
        )

        result.fold(
            onSuccess = { responseData ->
                Log.d("ExchangeHome", "사용자 자산 조회 성공")

                val jsonObject = JSONObject(responseData)
                val myWalletArray = jsonObject.getJSONArray("myWallet")

                val assetMap = mutableMapOf<String, Double>()
                for (i in 0 until myWalletArray.length()) {
                    val assetJson = myWalletArray.getJSONObject(i)
                    val symbol = assetJson.getString("symbol")
                    val amount = assetJson.getDouble("amount")
                    if (amount > 0) { // 보유량이 0보다 큰 것만 추가
                        assetMap[symbol] = amount
                    }
                }

                Log.d("ExchangeHome", "파싱 완료: ${assetMap.size}개 자산")
                assetMap
            },
            onFailure = { exception ->
                Log.e("ExchangeHome", "사용자 자산 조회 실패: ${exception.message}")
                val errorMessage = exception.message ?: ""

                // 세션 만료나 재로그인 필요 시 로그인 페이지로 이동
                if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(context, LoginPage::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    }
                }

                emptyMap()
            }
        )
    } catch (e: Exception) {
        Log.e("ExchangeHome", "사용자 자산 조회 예외: ${e.message}")
        emptyMap()
    }
}

@Composable
fun getFlashingPriceColor(changeDir: String?): Color {
    var flashColor by remember { mutableStateOf(Color.Unspecified) }
    LaunchedEffect(changeDir) {
        if (changeDir == "up") {
            flashColor = Color.Red
            delay(300)
            flashColor = Color.Unspecified
        } else if (changeDir == "down") {
            flashColor = Color.Blue
            delay(300)
            flashColor = Color.Unspecified
        }
    }
    return flashColor
}

@Composable
fun ExchangeHomeScreen(
    onCoinClick: (String, String, String) -> Unit = { _, _, _ -> }
) {
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    // 뒤로가기 두 번 누르기 관련 상태
    var backPressedTime by remember { mutableStateOf(0L) }
    val backPressedInterval = 2000L // 2초

    var coinList by remember { mutableStateOf<List<CoinInfo>>(emptyList()) }
    var originalCoinList by remember { mutableStateOf<List<CoinInfo>>(emptyList()) }
    var previousPrices by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var changedCoins by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var isSearchVisible by remember { mutableStateOf(false) }

    // 정렬 관련 상태
    var sortBy by remember { mutableStateOf("none") } // "name", "change", "price", "amount", "none"
    var sortAscending by remember { mutableStateOf(true) }

    // 탭 관련 상태
    var selectedTab by remember { mutableStateOf(0) } // 0: 원화, 1: 보유
    val tabs = listOf("원화", "보유")

    // 보유 코인 관련 상태
    var userAssets by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }
    var holdingCoins by remember { mutableStateOf<List<HoldingCoinInfo>>(emptyList()) }
    var holdingCoinPrices by remember { mutableStateOf<Map<String, Double>>(emptyMap()) }

    // 네비게이션 상태 추가
    var showCoinDetail by remember { mutableStateOf(false) }
    var selectedCoin by remember { mutableStateOf<CoinInfo?>(null) }

    val selectedSymbols = listOf(
        "BTC", "ETH", "XRP", "USDT", "DOGE", "SOL", "ENA", "PEPE", "SHIB", "SUI",
        "WLD", "SPK", "BONK", "ES", "XLM", "CFX", "ONDO", "FORT", "BABY", "WOO"
    )

    // 뒤로가기 버튼 처리
    BackHandler(enabled = true) {
        if (showCoinDetail) {
            // 상세 화면에서 뒤로가기 - 바로 홈으로
            showCoinDetail = false
            selectedCoin = null
        } else {
            // 홈 화면에서 뒤로가기 - 두 번 눌러서 종료
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
    }

    // 정렬 함수
    val sortCoinList = { coins: List<CoinInfo> ->
        when (sortBy) {
            "name" -> {
                if (sortAscending) coins.sortedBy { it.koreanName }
                else coins.sortedByDescending { it.koreanName }
            }
            "change" -> {
                if (sortAscending) coins.sortedBy { it.changeRate.toDoubleOrNull() ?: 0.0 }
                else coins.sortedByDescending { it.changeRate.toDoubleOrNull() ?: 0.0 }
            }
            "price" -> {
                if (sortAscending) coins.sortedBy { it.price.replace(",", "").toDoubleOrNull() ?: 0.0 }
                else coins.sortedByDescending { it.price.replace(",", "").toDoubleOrNull() ?: 0.0 }
            }
            else -> coins
        }
    }

    val sortHoldingCoinList = { coins: List<HoldingCoinInfo> ->
        when (sortBy) {
            "name" -> {
                if (sortAscending) coins.sortedBy { it.koreanName }
                else coins.sortedByDescending { it.koreanName }
            }
            "amount" -> {
                if (sortAscending) coins.sortedBy { it.amount }
                else coins.sortedByDescending { it.amount }
            }
            "price" -> {
                if (sortAscending) coins.sortedBy { it.price }
                else coins.sortedByDescending { it.price }
            }
            else -> coins
        }
    }

    // 원화 탭용 코인 데이터 가져오기
    val fetchCoins: (Boolean) -> Unit = { showLoading ->
        if (showLoading) isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isNetworkAvailable(context)) {
                    withContext(Dispatchers.Main) {
                        if (showLoading) isLoading = false
                    }
                    return@launch
                }

                val nameResponse = URL("https://api.bithumb.com/v1/market/all").readText()
                val nameData = JSONArray(nameResponse)
                val symbolToKoreanMap = mutableMapOf<String, String>()
                for (i in 0 until nameData.length()) {
                    val item = nameData.getJSONObject(i)
                    val market = item.getString("market")
                    val koreanName = item.getString("korean_name")
                    if (market.startsWith("KRW-")) {
                        val symbol = market.removePrefix("KRW-")
                        symbolToKoreanMap[symbol] = koreanName
                    }
                }

                val priceResponse = URL("https://api.bithumb.com/public/ticker/ALL_KRW").readText()
                val priceJson = JSONObject(priceResponse)
                val priceData = priceJson.getJSONObject("data")

                val resultList = mutableListOf<CoinInfo>()
                val newPrices = mutableMapOf<String, String>()
                val changedMap = mutableMapOf<String, String>()

                for (key in priceData.keys()) {
                    if (key == "date" || !selectedSymbols.contains(key)) continue
                    val coinObj = priceData.getJSONObject(key)
                    val price = coinObj.getString("closing_price")
                    val changeRate = coinObj.getString("fluctate_rate_24H")
                    val koreanName = symbolToKoreanMap[key] ?: key
                    resultList.add(CoinInfo(key, koreanName, price, changeRate))

                    val previous = previousPrices[key]?.toDoubleOrNull()
                    val current = price.toDoubleOrNull()
                    if (previous != null && current != null && previous != current) {
                        changedMap[key] = if (current > previous) "up" else "down"
                    }
                    newPrices[key] = price
                }

                withContext(Dispatchers.Main) {
                    previousPrices = newPrices
                    changedCoins = changedMap
                    originalCoinList = resultList
                    // 원화 탭이 선택되어 있고 검색 중이 아닐 때만 coinList 업데이트
                    if (selectedTab == 0) {
                        val filtered = if (searchQuery.isEmpty()) {
                            resultList
                        } else {
                            resultList.filter {
                                it.koreanName.contains(searchQuery, ignoreCase = true) ||
                                        it.symbol.contains(searchQuery, ignoreCase = true)
                            }
                        }
                        coinList = sortCoinList(filtered)
                    }
                    if (showLoading) isLoading = false
                }
            } catch (e: Exception) {
                Log.e("ExchangeHome", "에러 발생: ${e.message}")
                withContext(Dispatchers.Main) {
                    if (showLoading) isLoading = false
                }
            }
        }
    }

    // AuthTokenManager를 사용한 보유 코인 데이터 가져오기 (토큰 갱신 포함)
    val fetchHoldingCoins: () -> Unit = {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isNetworkAvailable(context)) {
                    Log.d("ExchangeHome", "네트워크 없음")
                    return@launch
                }

                // AuthTokenManager를 통해 사용자 보유 자산 조회 (자동 토큰 갱신 포함)
                val assets = fetchUserAssets(context)

                if (assets.isEmpty()) {
                    Log.d("ExchangeHome", "보유 자산이 없거나 조회 실패")
                    withContext(Dispatchers.Main) {
                        userAssets = emptyMap()
                        holdingCoins = emptyList()
                    }
                    return@launch
                }

                // 빗썸 API에서 가격 정보 가져오기
                val priceResponse = URL("https://api.bithumb.com/public/ticker/ALL_KRW").readText()
                val priceJson = JSONObject(priceResponse)
                val priceData = priceJson.getJSONObject("data")

                // 한글명 가져오기
                val nameResponse = URL("https://api.bithumb.com/v1/market/all").readText()
                val nameData = JSONArray(nameResponse)
                val symbolToKoreanMap = mutableMapOf<String, String>()
                for (i in 0 until nameData.length()) {
                    val item = nameData.getJSONObject(i)
                    val market = item.getString("market")
                    val koreanName = item.getString("korean_name")
                    if (market.startsWith("KRW-")) {
                        val symbol = market.removePrefix("KRW-")
                        symbolToKoreanMap[symbol] = koreanName
                    }
                }

                val holdingList = mutableListOf<HoldingCoinInfo>()
                val priceMap = mutableMapOf<String, Double>()
                val changedMap = mutableMapOf<String, String>()

                for ((symbol, amount) in assets) {
                    if (priceData.has(symbol)) {
                        try {
                            val coinObj = priceData.getJSONObject(symbol)
                            val priceStr = coinObj.getString("closing_price")
                            val price = priceStr.replace(",", "").toDoubleOrNull()

                            if (price != null && price > 0) {
                                val koreanName = symbolToKoreanMap[symbol] ?: symbol
                                holdingList.add(HoldingCoinInfo(symbol, koreanName, price, amount))

                                // 이전 가격과 비교하여 변동 방향 확인
                                val previousPrice = holdingCoinPrices[symbol]
                                if (previousPrice != null && previousPrice != price) {
                                    changedMap[symbol] = if (price > previousPrice) "up" else "down"
                                }
                                priceMap[symbol] = price
                            }
                        } catch (e: Exception) {
                            Log.e("ExchangeHome", "$symbol 가격 파싱 실패: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    userAssets = assets
                    val sortedHoldingList = sortHoldingCoinList(holdingList)
                    holdingCoins = sortedHoldingList
                    holdingCoinPrices = priceMap
                    // 보유 코인의 가격 변동도 changedCoins에 반영
                    changedCoins = changedCoins + changedMap
                    Log.d("ExchangeHome", "보유 코인 업데이트: ${holdingList.size}개")
                }

            } catch (e: Exception) {
                Log.e("ExchangeHome", "보유 코인 데이터 수집 실패", e)
                withContext(Dispatchers.Main) {
                    userAssets = emptyMap()
                    holdingCoins = emptyList()
                }
            }
        }
    }

    // 검색 필터링 함수
    val filterCoins = { query: String ->
        when (selectedTab) {
            0 -> { // 원화 탭
                val filtered = if (query.isEmpty()) {
                    originalCoinList
                } else {
                    originalCoinList.filter {
                        it.koreanName.contains(query, ignoreCase = true) ||
                                it.symbol.contains(query, ignoreCase = true)
                    }
                }
                coinList = sortCoinList(filtered)
            }
            1 -> { // 보유 탭 - 검색 기능은 추후 필요시 구현
                Unit
            }
        }
    }

    LaunchedEffect(Unit) {
        fetchCoins(true)
        fetchHoldingCoins() // AuthTokenManager 사용으로 토큰 체크 자동화
    }

    LaunchedEffect(true) {
        while (true) {
            delay(3000)
            if (selectedTab == 0) {
                fetchCoins(false)
            } else if (selectedTab == 1) {
                fetchHoldingCoins() // AuthTokenManager가 자동으로 토큰 체크 및 갱신
            }
        }
    }

    // 검색 쿼리 변경 시 필터링
    LaunchedEffect(searchQuery) {
        filterCoins(searchQuery)
    }

    // 탭 변경 시 검색 초기화
    LaunchedEffect(selectedTab) {
        searchQuery = ""
        isSearchVisible = false
        keyboardController?.hide()
        sortBy = "none" // 탭 변경 시 정렬도 초기화
        sortAscending = true
    }

    // 화면 전환 로직 (슬라이드 애니메이션 적용)
    AnimatedVisibility(
        visible = showCoinDetail && selectedCoin != null,
        enter = slideInHorizontally(
            initialOffsetX = { it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        CoinDetailScreen(
            symbol = selectedCoin?.symbol ?: "",
            koreanName = selectedCoin?.koreanName ?: "",
            price = selectedCoin?.price ?: "",
            onBackClick = {
                showCoinDetail = false
                selectedCoin = null
            }
        )
    }

    // 거래소 홈 화면
    AnimatedVisibility(
        visible = !showCoinDetail,
        enter = slideInHorizontally(
            initialOffsetX = { -it },
            animationSpec = tween(300)
        ) + fadeIn(animationSpec = tween(300)),
        exit = slideOutHorizontally(
            targetOffsetX = { -it },
            animationSpec = tween(300)
        ) + fadeOut(animationSpec = tween(300))
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            // ── 상단: 로고와 검색창 ──
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(72.dp)
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 로고는 항상 표시
                Image(
                    painter = painterResource(id = R.drawable.downbit_logo2),
                    contentDescription = "Logo",
                    modifier = Modifier.height(50.dp),
                    contentScale = ContentScale.Fit
                )

                Spacer(modifier = Modifier.width(16.dp))

                // 검색창 영역
                AnimatedVisibility(
                    visible = isSearchVisible,
                    enter = slideInHorizontally(
                        initialOffsetX = { full -> full },
                        animationSpec = tween(
                            durationMillis = 400,
                            easing = EaseOutQuart
                        )
                    ) + fadeIn(
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = EaseOutQuart
                        )
                    ),
                    exit = slideOutHorizontally(
                        targetOffsetX = { full -> full },
                        animationSpec = tween(
                            durationMillis = 350,
                            easing = EaseInQuart
                        )
                    ) + fadeOut(
                        animationSpec = tween(
                            durationMillis = 300,
                            easing = EaseInQuart
                        )
                    )
                ) {
                    OutlinedTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        singleLine = true,
                        placeholder = {
                            Text(
                                if (selectedTab == 0) "코인명 또는 심볼 검색"
                                else "보유 코인 검색"
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = {
                                isSearchVisible = false
                                searchQuery = ""
                                keyboardController?.hide()
                            }) {
                                Icon(Icons.Default.Close, contentDescription = "검색 닫기")
                            }
                        },
                        keyboardOptions = KeyboardOptions(
                            imeAction = ImeAction.Search
                        ),
                        keyboardActions = KeyboardActions(
                            onSearch = {
                                keyboardController?.hide()
                            }
                        ),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = redMain,
                            cursorColor = redMain
                        )
                    )
                }

                // 검색창이 보이지 않을 때는 Spacer로 공간을 채우고 검색 아이콘 표시
                if (!isSearchVisible) {
                    Spacer(modifier = Modifier.weight(1f))
                }

                // 검색 아이콘 (검색창이 꺼져있을 때만 표시)
                AnimatedVisibility(
                    visible = !isSearchVisible,
                    enter = fadeIn(
                        animationSpec = tween(
                            durationMillis = 250,
                            delayMillis = 100,
                            easing = EaseOutQuart
                        )
                    ),
                    exit = fadeOut(
                        animationSpec = tween(
                            durationMillis = 200,
                            easing = EaseInQuart
                        )
                    )
                ) {
                    IconButton(onClick = { isSearchVisible = true }) {
                        Icon(
                            painter = painterResource(id = R.drawable.ic_search),
                            contentDescription = "검색",
                            tint = redMain
                        )
                    }
                }
            }

            // ── 탭 ──
            TabRow(
                selectedTabIndex = selectedTab,
                backgroundColor = Color.White,
                contentColor = redMain
            ) {
                tabs.forEachIndexed { idx, title ->
                    Tab(
                        selected = idx == selectedTab,
                        onClick = {
                            selectedTab = idx
                        },
                        text = {
                            Text(
                                title,
                                fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                            )
                        },
                        selectedContentColor = redMain,
                        unselectedContentColor = Color.Gray
                    )
                }
            }

            // 검색 결과 카운트 표시 (검색 중일 때만)
            if (isSearchVisible && searchQuery.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    val count = when (selectedTab) {
                        0 -> coinList.size
                        1 -> holdingCoins.filter {
                            it.koreanName.contains(searchQuery, ignoreCase = true) ||
                                    it.symbol.contains(searchQuery, ignoreCase = true)
                        }.size
                        else -> 0
                    }
                    Text(
                        "검색 결과: ${count}개",
                        color = redMain,
                        fontSize = 14.sp
                    )
                }
            }

            // 헤더 표시
            val headerBackgroundColor = Color(0xFFF0EEEE)
            Box(modifier = Modifier.fillMaxWidth().background(headerBackgroundColor)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp, horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 자산명 헤더
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (sortBy == "name") {
                                    sortAscending = !sortAscending
                                } else {
                                    sortBy = "name"
                                    sortAscending = true
                                }
                                // 정렬 적용
                                if (selectedTab == 0) {
                                    coinList = sortCoinList(coinList)
                                } else {
                                    holdingCoins = sortHoldingCoinList(holdingCoins)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            "자산명",
                            fontSize = 14.sp,
                            color = redMain
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (sortBy == "name") {
                                if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = "정렬",
                            tint = if (sortBy == "name") redMain else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    if (selectedTab == 0) {
                        // 변동 헤더 (원화 탭)
                        Row(
                            modifier = Modifier
                                .weight(0.6f)
                                .clickable {
                                    if (sortBy == "change") {
                                        sortAscending = !sortAscending
                                    } else {
                                        sortBy = "change"
                                        sortAscending = true
                                    }
                                    coinList = sortCoinList(coinList)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "변동",
                                fontSize = 14.sp,
                                color = redMain
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (sortBy == "change") {
                                    if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = "정렬",
                                tint = if (sortBy == "change") redMain else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    } else {
                        // 보유량 헤더 (보유 탭)
                        Row(
                            modifier = Modifier
                                .weight(0.8f)
                                .clickable {
                                    if (sortBy == "amount") {
                                        sortAscending = !sortAscending
                                    } else {
                                        sortBy = "amount"
                                        sortAscending = true
                                    }
                                    holdingCoins = sortHoldingCoinList(holdingCoins)
                                },
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                "보유량",
                                fontSize = 14.sp,
                                color = redMain
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (sortBy == "amount") {
                                    if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                                } else {
                                    Icons.Default.KeyboardArrowDown
                                },
                                contentDescription = "정렬",
                                tint = if (sortBy == "amount") redMain else Color.Gray,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    // 현재가 헤더
                    Row(
                        modifier = Modifier
                            .weight(1f)
                            .clickable {
                                if (sortBy == "price") {
                                    sortAscending = !sortAscending
                                } else {
                                    sortBy = "price"
                                    sortAscending = true
                                }
                                // 정렬 적용
                                if (selectedTab == 0) {
                                    coinList = sortCoinList(coinList)
                                } else {
                                    holdingCoins = sortHoldingCoinList(holdingCoins)
                                }
                            },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.End
                    ) {
                        Text(
                            "현재가(KRW)",
                            fontSize = 14.sp,
                            color = redMain
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            imageVector = if (sortBy == "price") {
                                if (sortAscending) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown
                            } else {
                                Icons.Default.KeyboardArrowDown
                            },
                            contentDescription = "정렬",
                            tint = if (sortBy == "price") redMain else Color.Gray,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }
            Divider()

            if (isLoading && selectedTab == 0) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                    color = redMain
                )
            } else {
                when (selectedTab) {
                    0 -> { // 원화 탭
                        if (searchQuery.isNotEmpty() && coinList.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    "검색 결과가 없습니다",
                                    fontSize = 18.sp,
                                    color = Color.Gray
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "'$searchQuery'에 해당하는 코인을 찾을 수 없습니다",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.padding(bottom = 60.dp),
                                contentPadding = PaddingValues(bottom = 0.dp)
                            ) {
                                items(coinList) { coin ->
                                    val changeDir = changedCoins[coin.symbol]
                                    val priceColor = getFlashingPriceColor(changeDir)
                                    val rateColor = try {
                                        if (coin.changeRate.toDouble() < 0) Color.Blue else Color.Red
                                    } catch (_: Exception) {
                                        Color.Unspecified
                                    }
                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    selectedCoin = coin
                                                    showCoinDetail = true
                                                }
                                                .padding(vertical = 6.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${coin.koreanName} (${coin.symbol})", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Text("${coin.changeRate}%", fontSize = 14.sp, color = rateColor, modifier = Modifier.weight(0.6f), textAlign = TextAlign.End)
                                            Text(formatPrice(coin.price), fontSize = 14.sp, color = priceColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    }
                    1 -> { // 보유 탭
                        val filteredHoldingCoins = if (searchQuery.isEmpty()) {
                            sortHoldingCoinList(holdingCoins)
                        } else {
                            val filtered = holdingCoins.filter {
                                it.koreanName.contains(searchQuery, ignoreCase = true) ||
                                        it.symbol.contains(searchQuery, ignoreCase = true)
                            }
                            sortHoldingCoinList(filtered)
                        }

                        if (filteredHoldingCoins.isEmpty()) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(32.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Text(
                                    if (searchQuery.isNotEmpty()) "검색 결과가 없습니다" else "보유 중인 코인이 없습니다",
                                    fontSize = 18.sp,
                                    color = Color.Gray
                                )
                                if (searchQuery.isNotEmpty()) {
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "'$searchQuery'에 해당하는 보유 코인을 찾을 수 없습니다",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.padding(bottom = 60.dp),
                                contentPadding = PaddingValues(bottom = 0.dp)
                            ) {
                                items(filteredHoldingCoins) { coin ->
                                    val changeDir = changedCoins[coin.symbol]
                                    val priceColor = getFlashingPriceColor(changeDir)

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .clickable {
                                                    // 보유 코인을 CoinInfo 형태로 변환하여 상세 화면으로 이동
                                                    selectedCoin = CoinInfo(
                                                        symbol = coin.symbol,
                                                        koreanName = coin.koreanName,
                                                        price = coin.price.toString(),
                                                        changeRate = "0.00" // 보유 탭에서는 변동률 표시하지 않음
                                                    )
                                                    showCoinDetail = true
                                                }
                                                .padding(vertical = 6.dp, horizontal = 16.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text("${coin.koreanName} (${coin.symbol})", fontSize = 14.sp, modifier = Modifier.weight(1f))
                                            Text("${formatDecimal(coin.amount)}", fontSize = 14.sp, modifier = Modifier.weight(0.8f), textAlign = TextAlign.End)
                                            Text("₩${formatDecimal(coin.price)}", fontSize = 14.sp, color = priceColor, modifier = Modifier.weight(1f), textAlign = TextAlign.End)
                                        }
                                        Divider()
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}