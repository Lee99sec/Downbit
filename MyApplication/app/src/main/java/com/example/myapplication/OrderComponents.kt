// OrderComponents.kt
package com.example.myapplication.screen

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.KeyboardType
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import com.example.myapplication.LoginPage
import com.example.myapplication.security.E2EEncryptionUtils
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// 암호화된 요청 데이터 클래스 (E2E 암호화용)
data class EncryptedBuyOrderRequest(
    val e2edata: String
)

// 매수 주문 응답 데이터 클래스
data class BuyOrderResponse(
    val cash: Double,
    val coin: Double
)

// 주문 관련 데이터 클래스
data class OrderData(
    val quantity: String = "0",
    val price: String = "0",
    val totalAmount: String = "0"
)

// 주문 정보 응답 데이터 클래스
data class OrderInfoResponse(
    val cash: Double,
    val coin: Double
)

// 가격 포맷팅 함수 (소수점 3자리까지)
fun formatPriceWithThreeDecimals(price: Double): String {
    return String.format("%,.3f", price)
}

// 가격을 정수부와 소수부로 분리하는 함수
fun splitPriceDecimal(price: Double): Pair<String, String> {
    val formatted = String.format("%,.3f", price)
    val parts = formatted.split(".")
    return if (parts.size == 2) {
        Pair(parts[0], ".${parts[1]}")
    } else {
        Pair(formatted, "")
    }
}

// 기존 Composable 함수들
@Composable
fun OrderSubTabs(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    tabTitles: List<String>
) {
    TabRow(
        selectedTabIndex = selectedTab,
        backgroundColor = Color.White,
        contentColor = Color.Red,
        modifier = Modifier.height(40.dp)
    ) {
        tabTitles.forEachIndexed { index, title ->
            Tab(
                selected = selectedTab == index,
                onClick = { onTabSelected(index) },
                text = {
                    Text(
                        text = title,
                        color = if (selectedTab == index) Color.Red else Color.Gray,
                        fontSize = 12.sp
                    )
                }
            )
        }
    }
}

@Composable
fun AvailableBalanceSection(
    label: String,
    amount: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp, color = Color.Black)
        Text(text = amount, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Color.Black)
    }
}

@Composable
fun AvailableBalanceSectionWithDecimal(
    label: String,
    amount: Double,
    unit: String
) {
    val (integerPart, decimalPart) = splitPriceDecimal(amount)

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, fontSize = 16.sp, color = Color.Black)

        Text(
            text = buildAnnotatedString {
                withStyle(style = SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) {
                    append(integerPart)
                }
                if (decimalPart.isNotEmpty()) {
                    withStyle(style = SpanStyle(color = Color.Black.copy(alpha = 0.40f), fontWeight = FontWeight.Bold)) {
                        append(decimalPart)
                    }
                }
                withStyle(style = SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) {
                    append(" $unit")
                }
            },
            fontSize = 18.sp
        )
    }
}

@Composable
fun LimitBuyContent(
    orderData: OrderData,
    onOrderDataChange: (OrderData) -> Unit,
    currentPrice: String
) {
    Column {
        Text("지정가 매수 기능은 준비중입니다.", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        OrderButton("지정가 매수 (준비중)", Color.Gray) {
            // 준비중
        }
    }
}

@Composable
fun AutoBuyContent(
    orderData: OrderData,
    onOrderDataChange: (OrderData) -> Unit,
    currentPrice: String
) {
    Column {
        Text("자동 매수 기능은 준비중입니다.", fontSize = 16.sp, color = Color.Gray)
        Spacer(modifier = Modifier.height(16.dp))
        OrderButton("자동매수 설정 (준비중)", Color.Gray) {
            // 준비중
        }
    }
}

@Composable
fun OrderButton(
    text: String,
    color: Color,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp),
        colors = ButtonDefaults.buttonColors(backgroundColor = color, contentColor = Color.White),
        shape = RoundedCornerShape(8.dp)
    ) {
        Text(text, fontSize = 16.sp, fontWeight = FontWeight.Bold)
    }
}

// 서버 요청 포함한 매수 화면
@Composable
fun BuyOrderScreen(
    userId: Int,
    token: String,
    currentPrice: String
) {
    BuyOrderContent(currentPrice = currentPrice)
}

@Composable
fun BuyOrderContent(
    availableBalance: Int = 0,
    currentPrice: String = "0",
    symbol: String = "BTC"
) {
    val context = LocalContext.current
    // AuthTokenManager 사용
    val authManager = remember { AuthTokenManager(context) }

    var balance by remember { mutableStateOf(0.0) }
    var coinBalance by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedSubTab by remember { mutableStateOf(0) }
    val subTabTitles = listOf("지정", "시장", "자동")
    var orderData by remember { mutableStateOf(OrderData()) }

    // 주문 정보 조회 함수 - AuthTokenManager 사용
    suspend fun fetchOrderInfo() {
        try {
            // GET /order/info/{symbol}?token=JWT토큰값 형태로 요청
            val result = authManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.BASE_URL}/order/info/$symbol",
                method = "GET"
            )

            result.fold(
                onSuccess = { responseData ->
                    try {
                        val jsonResponse = JSONObject(responseData)
                        balance = jsonResponse.getDouble("cash")
                        coinBalance = jsonResponse.getDouble("coin")
                        android.util.Log.d("OrderInfo", "주문 정보 조회 성공 - 잔고: $balance, 코인: $coinBalance")
                    } catch (e: Exception) {
                        android.util.Log.e("OrderInfo", "JSON 파싱 오류: ${e.message}")
                    }
                },
                onFailure = { exception ->
                    android.util.Log.e("OrderInfo", "주문 정보 조회 실패: ${exception.message}")
                    val errorMessage = exception.message ?: ""
                    if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                        // 토큰 관리자가 이미 로그아웃 처리함
                        val intent = Intent(context, LoginPage::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    }
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("OrderInfo", "주문 정보 조회 예외: ${e.message}")
        }
    }

    // symbol이 변경될 때마다 새로 조회
    LaunchedEffect(symbol) {
        isLoading = true
        fetchOrderInfo()
        isLoading = false
    }

    // 주문 성공 콜백 - 잔고 업데이트
    val onOrderSuccess: (Double, Double) -> Unit = { newCash, newCoin ->
        balance = newCash
        coinBalance = newCoin
    }

    Column(modifier = Modifier.fillMaxSize()) {
        OrderSubTabs(
            selectedTab = selectedSubTab,
            onTabSelected = { selectedSubTab = it },
            tabTitles = subTabTitles
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .padding(bottom = 80.dp)
        ) {
            if (isLoading) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(text = "주문가능", fontSize = 16.sp, color = Color.Black)
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.Red
                    )
                }
            } else {
                Column {
                    AvailableBalanceSectionWithDecimal(
                        label = "주문가능",
                        amount = balance,
                        unit = "원"
                    )

                    Spacer(modifier = Modifier.height(8.dp))
                    AvailableBalanceSection(
                        label = "보유 ${symbol}",
                        amount = "${String.format("%.6f", coinBalance)} 개"
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            when (selectedSubTab) {
                0 -> LimitBuyContent(orderData, { orderData = it }, currentPrice)
                1 -> MarketBuyContent(
                    orderData = orderData,
                    onOrderDataChange = { orderData = it },
                    currentPrice = currentPrice,
                    availableCash = balance,
                    symbol = symbol,
                    onOrderSuccess = onOrderSuccess,
                    authManager = authManager
                )
                2 -> AutoBuyContent(orderData, { orderData = it }, currentPrice)
            }
        }
    }
}

// AuthTokenManager를 사용하면서 토큰도 함께 E2E 암호화하는 MarketBuyContent
@Composable
fun MarketBuyContent(
    orderData: OrderData,
    onOrderDataChange: (OrderData) -> Unit,
    currentPrice: String = "0",
    availableCash: Double = 0.0,
    symbol: String = "BTC",
    onOrderSuccess: (Double, Double) -> Unit,
    authManager: AuthTokenManager
) {
    val context = LocalContext.current
    var isOrderLoading by remember { mutableStateOf(false) }
    var isQuantityMode by remember { mutableStateOf(true) }
    var amountInput by remember { mutableStateOf("0") }

    // 현재가를 숫자로 변환
    val currentPriceValue = currentPrice.replace(",", "").toDoubleOrNull() ?: 0.0

    // 수량이 변경될 때마다 총액 자동 계산
    val calculatedAmount = try {
        if (isQuantityMode) {
            val quantity = orderData.quantity.toDoubleOrNull() ?: 0.0
            quantity * currentPriceValue
        } else {
            amountInput.toDoubleOrNull() ?: 0.0
        }
    } catch (e: Exception) {
        0.0
    }

    // 금액 모드에서 실제 매수 수량 계산
    val calculatedQuantity = try {
        if (isQuantityMode) {
            orderData.quantity.toDoubleOrNull() ?: 0.0
        } else {
            val amount = amountInput.toDoubleOrNull() ?: 0.0
            if (currentPriceValue > 0) amount / currentPriceValue else 0.0
        }
    } catch (e: Exception) {
        0.0
    }

    // 수량을 소수점 5자리까지 버림 처리하는 함수
    fun truncateToFiveDecimals(value: Double): String {
        val multiplier = 100000.0
        val truncated = kotlin.math.floor(value * multiplier) / multiplier
        return String.format("%.5f", truncated).trimEnd('0').trimEnd('.')
    }

    // 금액을 정수 부분만 버림 처리하는 함수
    fun truncateToInteger(value: Double): String {
        return kotlin.math.floor(value).toLong().toString()
    }

    Column {
        // 수량/금액 입력 칸
        Column {
            // 수량/금액 탭 선택
            Row(
                modifier = Modifier
                    .padding(bottom = 6.dp)
                    .background(Color(0xFFF0F0F0), RoundedCornerShape(6.dp))
                    .padding(2.dp),
                horizontalArrangement = Arrangement.spacedBy(0.dp)
            ) {
                // 수량 탭
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (isQuantityMode) Color.White else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            if (!isQuantityMode) {
                                isQuantityMode = true
                                onOrderDataChange(orderData.copy(quantity = "0"))
                                amountInput = "0"
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "수량",
                        fontSize = 14.sp,
                        fontWeight = if (isQuantityMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (isQuantityMode) Color.Black else Color.Gray
                    )
                }

                // 금액 탭
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .background(
                            if (!isQuantityMode) Color.White else Color.Transparent,
                            RoundedCornerShape(4.dp)
                        )
                        .clickable {
                            if (isQuantityMode) {
                                isQuantityMode = false
                                onOrderDataChange(orderData.copy(quantity = "0"))
                                amountInput = "0"
                            }
                        }
                        .padding(vertical = 8.dp, horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "금액",
                        fontSize = 14.sp,
                        fontWeight = if (!isQuantityMode) FontWeight.Bold else FontWeight.Normal,
                        color = if (!isQuantityMode) Color.Black else Color.Gray
                    )
                }
            }

            OutlinedTextField(
                value = if (isQuantityMode) orderData.quantity else {
                    val amount = amountInput.toDoubleOrNull() ?: 0.0
                    if (amount == 0.0) "" else String.format("%,.0f", amount)
                },
                onValueChange = { newValue ->
                    if (isQuantityMode) {
                        // 수량 모드 처리
                        val filteredValue = newValue.filter { it.isDigit() || it == '.' }
                        val processedValue = when {
                            filteredValue.startsWith("0") && filteredValue.length > 1 && !filteredValue.startsWith("0.") -> {
                                filteredValue.dropWhile { it == '0' }.ifEmpty { "0" }
                            }
                            else -> filteredValue
                        }
                        val parts = processedValue.split(".")
                        val formattedValue = when {
                            parts.size > 2 -> orderData.quantity
                            parts.size == 2 && parts[1].length > 6 -> {
                                "${parts[0]}.${parts[1].take(6)}"
                            }
                            else -> processedValue
                        }
                        onOrderDataChange(orderData.copy(quantity = formattedValue))
                    } else {
                        // 금액 모드 처리
                        val numericValue = newValue.replace(",", "").filter { it.isDigit() }
                        val processedValue = when {
                            numericValue.isEmpty() -> "0"
                            numericValue.startsWith("0") && numericValue.length > 1 -> {
                                numericValue.dropWhile { it == '0' }.ifEmpty { "0" }
                            }
                            else -> numericValue
                        }
                        amountInput = processedValue
                    }
                },
                label = {
                    Text(
                        if (isQuantityMode) "수량을 입력하세요" else "금액을 입력하세요 (원)"
                    )
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = Color.Red,
                    cursorColor = Color.Red
                )
            )

            // 퍼센트 선택 버튼들
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val percentages = listOf(10, 25, 50, 75)

                percentages.forEach { percentage ->
                    Button(
                        onClick = {
                            if (isQuantityMode) {
                                if (currentPriceValue > 0 && availableCash > 0) {
                                    val targetAmount = availableCash * (percentage / 100.0)
                                    val quantity = targetAmount / currentPriceValue
                                    val truncatedQuantity = truncateToFiveDecimals(quantity)
                                    onOrderDataChange(orderData.copy(quantity = truncatedQuantity))
                                }
                            } else {
                                val targetAmount = availableCash * (percentage / 100.0)
                                val truncatedAmount = truncateToInteger(targetAmount)
                                amountInput = truncatedAmount
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color.LightGray,
                            contentColor = Color.Black
                        ),
                        elevation = ButtonDefaults.elevation(0.dp)
                    ) {
                        Text(text = "$percentage%", fontSize = 12.sp)
                    }
                }
            }

            // 초기화 및 전체 버튼
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = {
                        if (isQuantityMode) {
                            onOrderDataChange(orderData.copy(quantity = "0"))
                        } else {
                            amountInput = "0"
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color.Gray,
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Text(text = "초기화", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }

                Button(
                    onClick = {
                        if (isQuantityMode) {
                            if (currentPriceValue > 0 && availableCash > 0) {
                                val maxQuantity = availableCash / currentPriceValue
                                val truncatedQuantity = truncateToFiveDecimals(maxQuantity)
                                onOrderDataChange(orderData.copy(quantity = truncatedQuantity))
                            }
                        } else {
                            val truncatedAmount = truncateToInteger(availableCash)
                            amountInput = truncatedAmount
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        backgroundColor = Color(0xFFE53E3E),
                        contentColor = Color.White
                    ),
                    elevation = ButtonDefaults.elevation(0.dp)
                ) {
                    Text(text = "전체", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 현재가와 주문정보 박스
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color(0xFFF8F9FA), RoundedCornerShape(8.dp))
                .padding(12.dp)
        ) {
            // 현재가
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("현재가", fontSize = 16.sp, color = Color.Black)

                val (integerPart, decimalPart) = splitPriceDecimal(currentPriceValue)
                Text(
                    text = buildAnnotatedString {
                        withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append(integerPart)
                        }
                        if (decimalPart.isNotEmpty()) {
                            withStyle(style = SpanStyle(color = Color.Red.copy(alpha = 0.40f), fontWeight = FontWeight.Bold)) {
                                append(decimalPart)
                            }
                        }
                        withStyle(style = SpanStyle(color = Color.Red, fontWeight = FontWeight.Bold)) {
                            append(" 원")
                        }
                    },
                    fontSize = 18.sp
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            // 수량 모드: 주문금액 표시
            if (isQuantityMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("주문금액", fontSize = 16.sp, color = Color.Black)

                    val (amountIntegerPart, amountDecimalPart) = splitPriceDecimal(calculatedAmount)
                    Text(
                        text = buildAnnotatedString {
                            withStyle(style = SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) {
                                append(amountIntegerPart)
                            }
                            if (amountDecimalPart.isNotEmpty()) {
                                withStyle(style = SpanStyle(color = Color.Black.copy(alpha = 0.40f), fontWeight = FontWeight.Bold)) {
                                    append(amountDecimalPart)
                                }
                            }
                            withStyle(style = SpanStyle(color = Color.Black, fontWeight = FontWeight.Bold)) {
                                append(" 원")
                            }
                        },
                        fontSize = 16.sp
                    )
                }
            }

            // 금액 모드: 구매 수량 표시
            if (!isQuantityMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("구매수량", fontSize = 16.sp, color = Color.Black)
                    Text(
                        text = "${String.format("%.6f", calculatedQuantity).trimEnd('0').trimEnd('.')} $symbol",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 토큰까지 함께 E2E 암호화하는 시장가 매수 버튼
        Button(
            onClick = {
                val quantity = calculatedQuantity
                val orderAmount = calculatedAmount

                when {
                    quantity <= 0 -> {
                        Toast.makeText(context, "주문 수량을 입력해주세요", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    orderAmount < 5000 -> {
                        Toast.makeText(context, "최소 주문금액은 5,000원입니다", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    orderAmount > availableCash -> {
                        Toast.makeText(context, "보유 금액이 부족합니다", Toast.LENGTH_SHORT).show()
                        return@Button
                    }
                    !isOrderLoading -> {
                        isOrderLoading = true

                        CoroutineScope(Dispatchers.Main).launch {
                            try {
                                // 토큰 만료 시 자동 갱신 후 재시도하는 함수
                                suspend fun performEncryptedBuyRequest(retryCount: Int = 0): Result<String> {
                                    // AuthTokenManager에서 토큰 가져오기
                                    val accessToken = try {
                                        authManager.getValidAccessToken()
                                    } catch (e: Exception) {
                                        android.util.Log.e("OrderBuy", "토큰 가져오기 실패: ${e.message}")
                                        return Result.failure(Exception("인증 오류가 발생했습니다"))
                                    }

                                    if (accessToken == null) {
                                        return Result.failure(Exception("로그인이 필요합니다"))
                                    }

                                    // 토큰, symbol, amount 모두 함께 E2E 암호화
                                    val encryptedData = try {
                                        E2EEncryptionUtils.encryptData(
                                            "token" to accessToken,
                                            "symbol" to symbol,
                                            "amount" to quantity
                                        )
                                    } catch (e: Exception) {
                                        android.util.Log.e("OrderBuy", "E2E 암호화 실패: ${e.message}")
                                        return Result.failure(Exception("암호화 처리 오류: ${e.message}"))
                                    }

                                    // 암호화된 요청 객체 생성
                                    val requestJson = JSONObject().apply {
                                        put("e2edata", encryptedData)
                                    }

                                    android.util.Log.d("OrderBuy", "매수 요청 시도 $retryCount - symbol: $symbol, quantity: $quantity")
                                    android.util.Log.d("OrderBuy", "암호화된 데이터: $encryptedData")

                                    // 직접 HTTP 요청
                                    return withContext(Dispatchers.IO) {
                                        try {
                                            val client = OkHttpClient()
                                            val mediaType = "application/json".toMediaType()
                                            val requestBody = requestJson.toString().toRequestBody(mediaType)

                                            val request = Request.Builder()
                                                .url("${AuthTokenManager.BASE_URL}/order/buy")
                                                .post(requestBody)
                                                .build()

                                            val response = client.newCall(request).execute()

                                            response.use {
                                                when (response.code) {
                                                    200, 201 -> {
                                                        val responseData = response.body?.string() ?: ""
                                                        android.util.Log.d("OrderBuy", "매수 요청 성공")
                                                        Result.success(responseData)
                                                    }
                                                    401 -> {
                                                        android.util.Log.w("OrderBuy", "401 에러 발생 - 토큰 만료")
                                                        if (retryCount == 0) {
                                                            android.util.Log.d("OrderBuy", "토큰 갱신 후 재시도")
                                                            // AuthTokenManager의 makeAuthenticatedRequest를 통해 토큰 갱신 유도
                                                            // 임시 요청을 통해 401을 받고 자동 갱신 트리거
                                                            val refreshResult = authManager.makeAuthenticatedRequest(
                                                                url = "${AuthTokenManager.BASE_URL}/order/info/$symbol",
                                                                method = "GET"
                                                            )

                                                            refreshResult.fold(
                                                                onSuccess = {
                                                                    android.util.Log.d("OrderBuy", "토큰 갱신 성공 - 원래 요청 재시도")
                                                                    performEncryptedBuyRequest(retryCount + 1)
                                                                },
                                                                onFailure = { exception ->
                                                                    android.util.Log.e("OrderBuy", "토큰 갱신 실패: ${exception.message}")
                                                                    val errorMsg = exception.message ?: ""
                                                                    if (errorMsg.contains("세션이 만료") || errorMsg.contains("재로그인")) {
                                                                        Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다"))
                                                                    } else {
                                                                        Result.failure(Exception("토큰 갱신 실패"))
                                                                    }
                                                                }
                                                            )
                                                        } else {
                                                            android.util.Log.e("OrderBuy", "재시도 후에도 401 - 완전 만료")
                                                            Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다"))
                                                        }
                                                    }
                                                    else -> {
                                                        Result.failure(Exception("API 요청 실패: ${response.code}"))
                                                    }
                                                }
                                            }
                                        } catch (e: Exception) {
                                            Result.failure(e)
                                        }
                                    }
                                }

                                // 실제 요청 실행
                                val result = performEncryptedBuyRequest()

                                result.fold(
                                    onSuccess = { responseData ->
                                        try {
                                            android.util.Log.d("OrderBuy", "매수 응답 수신: $responseData")
                                            val jsonResponse = JSONObject(responseData)
                                            val newCash = jsonResponse.getDouble("cash")
                                            val newCoin = jsonResponse.getDouble("coin")

                                            Toast.makeText(context, "매수가 완료되었습니다!", Toast.LENGTH_SHORT).show()
                                            android.util.Log.d("OrderBuy", "매수 완료 - 새 잔고: $newCash, 새 코인: $newCoin")

                                            // 입력 초기화
                                            onOrderDataChange(OrderData())
                                            amountInput = "0"

                                            // 잔고 업데이트
                                            onOrderSuccess(newCash, newCoin)

                                            // 최신 잔고 정보 재조회
                                            kotlinx.coroutines.delay(500)
                                            val refreshResult = authManager.makeAuthenticatedRequest(
                                                url = "${AuthTokenManager.BASE_URL}/order/info/$symbol",
                                                method = "GET"
                                            )

                                            refreshResult.onSuccess { refreshData ->
                                                try {
                                                    val refreshJsonResponse = JSONObject(refreshData)
                                                    onOrderSuccess(
                                                        refreshJsonResponse.getDouble("cash"),
                                                        refreshJsonResponse.getDouble("coin")
                                                    )
                                                    android.util.Log.d("OrderRefresh", "잔고 재조회 완료")
                                                } catch (e: Exception) {
                                                    android.util.Log.e("OrderRefresh", "잔고 재조회 JSON 파싱 오류: ${e.message}")
                                                }
                                            }

                                        } catch (e: Exception) {
                                            android.util.Log.e("OrderResponse", "매수 응답 처리 오류: ${e.message}")
                                            Toast.makeText(context, "매수 응답 처리 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    onFailure = { exception ->
                                        android.util.Log.e("OrderBuy", "매수 요청 실패: ${exception.message}")
                                        val errorMessage = exception.message ?: ""

                                        when {
                                            errorMessage.contains("401") -> {
                                                Toast.makeText(context, "로그인이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                                                val intent = Intent(context, LoginPage::class.java)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                context.startActivity(intent)
                                            }
                                            errorMessage.contains("400") -> {
                                                Toast.makeText(context, "잘못된 주문 정보입니다", Toast.LENGTH_SHORT).show()
                                            }
                                            errorMessage.contains("403") -> {
                                                Toast.makeText(context, "주문 권한이 없습니다", Toast.LENGTH_SHORT).show()
                                            }
                                            errorMessage.contains("409") -> {
                                                Toast.makeText(context, "잔고가 부족합니다", Toast.LENGTH_SHORT).show()
                                            }
                                            errorMessage.contains("500") -> {
                                                Toast.makeText(context, "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요.", Toast.LENGTH_SHORT).show()
                                            }
                                            else -> {
                                                Toast.makeText(context, "매수 실패: $errorMessage", Toast.LENGTH_SHORT).show()
                                            }
                                        }
                                    }
                                )

                            } catch (e: Exception) {
                                android.util.Log.e("OrderBuy", "주문 처리 예외: ${e.message}")
                                Toast.makeText(context, "주문 처리 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isOrderLoading = false
                            }
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp),
            colors = ButtonDefaults.buttonColors(
                backgroundColor = if (isOrderLoading) Color.Gray else Color(0xFFE53E3E),
                contentColor = Color.White
            ),
            shape = RoundedCornerShape(8.dp),
            enabled = !isOrderLoading
        ) {
            if (isOrderLoading) {
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = Color.White,
                        strokeWidth = 2.dp
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("처리중...", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            } else {
                Text("시장가 매수", fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}