// SendScreen.kt - 송금 기능 완성 버전 (E2E 암호화 적용)
package com.example.myapplication

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.util.Log
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.LoginPage
import com.example.myapplication.security.E2EEncryptionUtils
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.util.Locale

// 코인 정보 데이터 클래스
data class CoinInfo(
    val symbol: String,
    val name: String,
    val balance: Double,
    val price: Double
)

// 송금 요청 데이터 클래스
data class RemittanceRequest(
    val token: String,
    val symbol: String,
    val address: String,
    val amount: java.math.BigDecimal
)

// 송금 응답 데이터 클래스
data class RemittanceResponse(
    val success: Boolean,
    val message: String? = null,
    val transactionId: String? = null
)

fun isNetworkAvailable(context: Context): Boolean {
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val capabilities = cm.getNetworkCapabilities(network) ?: return false
    return capabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
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

suspend fun sendRemittance(
    context: Context,
    symbol: String,
    address: String,
    amount: String
): Result<String> = withContext(Dispatchers.IO) {
    try {
        val authManager = AuthTokenManager(context)

        // 토큰 만료 시 자동 갱신 후 재시도하는 함수
        suspend fun performEncryptedRemittanceRequest(retryCount: Int = 0): Result<String> {
            // AuthTokenManager에서 토큰 가져오기
            val accessToken = try {
                authManager.getValidAccessToken()
            } catch (e: Exception) {
                Log.e("SendScreen", "토큰 가져오기 실패: ${e.message}")
                return Result.failure(Exception("인증 오류가 발생했습니다"))
            }

            if (accessToken == null) {
                return Result.failure(Exception("로그인이 필요합니다"))
            }

            // 토큰, symbol, address, amount 모두 함께 E2E 암호화
            val encryptedData = try {
                E2EEncryptionUtils.encryptData(
                    "token" to accessToken,
                    "symbol" to symbol,
                    "address" to address,
                    "amount" to amount.toBigDecimal()
                )
            } catch (e: Exception) {
                Log.e("SendScreen", "E2E 암호화 실패: ${e.message}")
                return Result.failure(Exception("암호화 처리 오류: ${e.message}"))
            }

            // 암호화된 요청 객체 생성
            val requestJson = JSONObject().apply {
                put("e2edata", encryptedData)
            }

            Log.d("SendScreen", "송금 요청 시도 $retryCount - symbol: $symbol, address: $address, amount: $amount")
            Log.d("SendScreen", "암호화된 데이터: $encryptedData")

            // 직접 HTTP 요청
            return try {
                val client = OkHttpClient()
                val mediaType = "application/json".toMediaType()
                val requestBody = requestJson.toString().toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("${AuthTokenManager.BASE_URL}/remittance")
                    .post(requestBody)
                    .build()

                val response = client.newCall(request).execute()

                response.use {
                    when (response.code) {
                        200, 201 -> {
                            val responseData = response.body?.string() ?: ""
                            Log.d("SendScreen", "송금 요청 성공")
                            Result.success("송금이 완료되었습니다")
                        }
                        401 -> {
                            Log.w("SendScreen", "401 에러 발생 - 토큰 만료")
                            if (retryCount == 0) {
                                Log.d("SendScreen", "토큰 갱신 후 재시도")
                                // AuthTokenManager의 makeAuthenticatedRequest를 통해 토큰 갱신 유도
                                val refreshResult = authManager.makeAuthenticatedRequest(
                                    url = "${AuthTokenManager.BASE_URL}/mywallet",
                                    method = "GET"
                                )

                                refreshResult.fold(
                                    onSuccess = {
                                        Log.d("SendScreen", "토큰 갱신 성공 - 원래 요청 재시도")
                                        performEncryptedRemittanceRequest(retryCount + 1)
                                    },
                                    onFailure = { exception ->
                                        Log.e("SendScreen", "토큰 갱신 실패: ${exception.message}")
                                        val errorMsg = exception.message ?: ""
                                        if (errorMsg.contains("세션이 만료") || errorMsg.contains("재로그인")) {
                                            withContext(Dispatchers.Main) {
                                                val intent = Intent(context, LoginPage::class.java)
                                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                                context.startActivity(intent)
                                            }
                                            Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다"))
                                        } else {
                                            Result.failure(Exception("토큰 갱신 실패"))
                                        }
                                    }
                                )
                            } else {
                                Log.e("SendScreen", "재시도 후에도 401 - 완전 만료")
                                withContext(Dispatchers.Main) {
                                    val intent = Intent(context, LoginPage::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    context.startActivity(intent)
                                }
                                Result.failure(Exception("액세스 토큰이 만료되었습니다"))
                            }
                        }
                        403 -> {
                            Result.failure(Exception("보유 코인이 부족합니다"))
                        }
                        404 -> {
                            Result.failure(Exception("지갑 정보를 찾을 수 없습니다"))
                        }
                        410 -> {
                            withContext(Dispatchers.Main) {
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                            }
                            Result.failure(Exception("리프레시 토큰이 만료되었습니다. 재로그인이 필요합니다"))
                        }
                        else -> {
                            Result.failure(Exception("송금 처리 중 오류가 발생했습니다: ${response.code}"))
                        }
                    }
                }
            } catch (e: Exception) {
                Result.failure(e)
            }
        }

        // 실제 요청 실행
        performEncryptedRemittanceRequest()

    } catch (e: Exception) {
        Log.e("SendScreen", "송금 요청 예외: ${e.message}")
        Result.failure(Exception("네트워크 오류가 발생했습니다: ${e.message}"))
    }
}

suspend fun fetchUserAssets(context: Context): Map<String, Double> = withContext(Dispatchers.IO) {
    try {
        val authManager = AuthTokenManager(context)

        val result = authManager.makeAuthenticatedRequest(
            url = "${AuthTokenManager.BASE_URL}/mywallet",
            method = "GET"
        )

        result.fold(
            onSuccess = { responseData ->
                Log.d("SendScreen", "사용자 자산 조회 성공")

                val jsonObject = JSONObject(responseData)
                val myWalletArray = jsonObject.getJSONArray("myWallet")

                val assetMap = mutableMapOf<String, Double>()
                for (i in 0 until myWalletArray.length()) {
                    val assetJson = myWalletArray.getJSONObject(i)
                    val symbol = assetJson.getString("symbol")
                    val amount = assetJson.getDouble("amount")
                    if (amount > 0) {
                        assetMap[symbol] = amount
                    }
                }

                Log.d("SendScreen", "파싱 완료: ${assetMap.size}개 자산")
                assetMap
            },
            onFailure = { exception ->
                Log.e("SendScreen", "사용자 자산 조회 실패: ${exception.message}")
                val errorMessage = exception.message ?: ""

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
        Log.e("SendScreen", "사용자 자산 조회 예외: ${e.message}")
        emptyMap()
    }
}

@Composable
fun SendScreen(
    onBackClick: () -> Unit
) {
    val context = LocalContext.current
    val redMain = Color(0xFFD32F2F)

    // 송금 관련 상태들
    var recipientAddress by remember { mutableStateOf("") }
    var selectedCoin by remember { mutableStateOf<CoinInfo?>(null) }
    var sendAmount by remember { mutableStateOf("") }
    var showCoinSelector by remember { mutableStateOf(false) }

    // QR 스캔 관련 상태
    var showQrScanner by remember { mutableStateOf(false) }

    // 송금 처리 상태
    var isSending by remember { mutableStateOf(false) }

    // 실제 보유 코인 데이터
    var holdingCoins by remember { mutableStateOf<List<CoinInfo>>(emptyList()) }
    var isLoadingCoins by remember { mutableStateOf(false) }
    var coinsError by remember { mutableStateOf("") }

    // 수수료 (고정값)
    val fee = 0.0

    // 총 출금액 계산
    val totalAmount = (sendAmount.toDoubleOrNull() ?: 0.0) + fee

    // 송금 버튼 활성화 조건 체크
    val canSend = recipientAddress.isNotBlank() &&
            selectedCoin != null &&
            sendAmount.toDoubleOrNull() != null &&
            sendAmount.toDoubleOrNull()!! > 0

    // 보유 코인 데이터 로딩 함수
    fun loadHoldingCoins() {
        isLoadingCoins = true
        coinsError = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                if (!isNetworkAvailable(context)) {
                    withContext(Dispatchers.Main) {
                        coinsError = "네트워크 연결을 확인해주세요"
                        isLoadingCoins = false
                    }
                    return@launch
                }

                val userAssets = fetchUserAssets(context)

                if (userAssets.isEmpty()) {
                    withContext(Dispatchers.Main) {
                        holdingCoins = emptyList()
                        isLoadingCoins = false
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

                val coinList = mutableListOf<CoinInfo>()

                for ((symbol, amount) in userAssets) {
                    if (priceData.has(symbol)) {
                        try {
                            val coinObj = priceData.getJSONObject(symbol)
                            val priceStr = coinObj.getString("closing_price")
                            val price = priceStr.replace(",", "").toDoubleOrNull()

                            if (price != null && price > 0) {
                                val koreanName = symbolToKoreanMap[symbol] ?: symbol
                                coinList.add(
                                    CoinInfo(
                                        symbol = symbol,
                                        name = koreanName,
                                        balance = amount,
                                        price = price
                                    )
                                )
                            }
                        } catch (e: Exception) {
                            Log.e("SendScreen", "$symbol 가격 파싱 실패: ${e.message}")
                        }
                    }
                }

                withContext(Dispatchers.Main) {
                    holdingCoins = coinList.sortedByDescending { it.balance * it.price }
                    isLoadingCoins = false
                    Log.d("SendScreen", "보유 코인 로드 완료: ${coinList.size}개")
                }

            } catch (e: Exception) {
                Log.e("SendScreen", "보유 코인 데이터 로딩 실패", e)
                withContext(Dispatchers.Main) {
                    coinsError = "보유 코인 정보를 불러올 수 없습니다: ${e.message}"
                    isLoadingCoins = false
                }
            }
        }
    }

    // 송금 처리 함수
    fun processSend() {
        if (!canSend || selectedCoin == null) {
            Toast.makeText(context, "송금 정보를 모두 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        if (!isNetworkAvailable(context)) {
            Toast.makeText(context, "네트워크 연결을 확인해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        val amountValue = sendAmount.toDoubleOrNull()
        if (amountValue == null || amountValue <= 0) {
            Toast.makeText(context, "올바른 수량을 입력해주세요", Toast.LENGTH_SHORT).show()
            return
        }

        if (amountValue > selectedCoin!!.balance) {
            Toast.makeText(context, "보유 수량이 부족합니다", Toast.LENGTH_SHORT).show()
            return
        }

        isSending = true

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val result = sendRemittance(
                    context = context,
                    symbol = selectedCoin!!.symbol,
                    address = recipientAddress.trim(),
                    amount = sendAmount
                )

                withContext(Dispatchers.Main) {
                    isSending = false
                    result.fold(
                        onSuccess = { message ->
                            Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                            // 송금 성공 시 폼 초기화
                            recipientAddress = ""
                            selectedCoin = null
                            sendAmount = ""
                            // 보유 코인 정보 다시 로드
                            loadHoldingCoins()
                        },
                        onFailure = { exception ->
                            Toast.makeText(context, exception.message, Toast.LENGTH_LONG).show()
                        }
                    )
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isSending = false
                    Toast.makeText(context, "송금 처리 중 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // 화면 로드 시 보유 코인 정보 가져오기
    LaunchedEffect(Unit) {
        loadHoldingCoins()
    }

    // 전체 화면 Column
    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 헤더 (뒤로가기 화살표만) - 패딩 줄임
        Surface(
            color = Color.White,
            elevation = 0.dp
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onBackClick,
                    modifier = Modifier.size(36.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = redMain,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // 스크롤 가능한 내용 영역
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = 8.dp,
                bottom = 100.dp // 하단 메뉴바 공간 고려해서 더 넓게
            ),
            verticalArrangement = Arrangement.spacedBy(16.dp) // 간격 줄임
        ) {
            // 받을 주소 입력
            item {
                Column {
                    Text(
                        "받을 주소",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        OutlinedTextField(
                            value = recipientAddress,
                            onValueChange = { recipientAddress = it },
                            placeholder = { Text("주소를 입력하거나 QR 스캔", fontSize = 14.sp) },
                            modifier = Modifier.weight(1f),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = redMain,
                                focusedLabelColor = redMain,
                                cursorColor = redMain
                            ),
                            maxLines = 2
                        )

                        Spacer(modifier = Modifier.width(8.dp))

                        OutlinedButton(
                            onClick = {
                                showQrScanner = true
                            },
                            modifier = Modifier.size(48.dp),
                            contentPadding = PaddingValues(0.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = redMain
                            ),
                            border = BorderStroke(1.dp, redMain)
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "QR 스캔",
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }

            // 코인 선택
            item {
                Column {
                    Text(
                        "보낼 코인 선택",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    OutlinedButton(
                        onClick = {
                            if (holdingCoins.isNotEmpty()) {
                                showCoinSelector = true
                            } else {
                                Toast.makeText(context, "보유 중인 코인이 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = if (holdingCoins.isNotEmpty()) redMain else Color.Gray
                        ),
                        border = BorderStroke(
                            1.dp,
                            if (holdingCoins.isNotEmpty()) redMain else Color.Gray
                        )
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (selectedCoin != null) {
                                Text(
                                    "${selectedCoin!!.name} (${selectedCoin!!.symbol})",
                                    fontSize = 14.sp,
                                    color = redMain
                                )
                            } else {
                                Text(
                                    if (isLoadingCoins) "코인 정보 로딩 중..."
                                    else if (holdingCoins.isEmpty()) "보유 중인 코인이 없습니다"
                                    else "코인을 선택해주세요",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }

                            if (isLoadingCoins) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = redMain,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(
                                    Icons.Default.KeyboardArrowDown,
                                    contentDescription = "선택",
                                    tint = if (holdingCoins.isNotEmpty()) redMain else Color.Gray
                                )
                            }
                        }
                    }
                }
            }

            // 에러 메시지 표시
            if (coinsError.isNotEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            "오류",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = redMain
                        )
                        Text(
                            coinsError,
                            fontSize = 12.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                        TextButton(
                            onClick = { loadHoldingCoins() },
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            Text("다시 시도", color = redMain, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 코인 선택 후에만 표시되는 영역들
            selectedCoin?.let { coin ->
                // 보낼 수량 입력
                item {
                    Column {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "보낼 수량",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Row(
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "송금 가능: ${formatDecimal(coin.balance)} ${coin.symbol}",
                                    fontSize = 12.sp,
                                    color = Color.Gray
                                )
                                TextButton(
                                    onClick = { sendAmount = formatDecimal(coin.balance) },
                                    modifier = Modifier.padding(0.dp),
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp)
                                ) {
                                    Text(
                                        "최대",
                                        color = redMain,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        OutlinedTextField(
                            value = sendAmount,
                            onValueChange = { newValue ->
                                val filtered = newValue.filter { it.isDigit() || it == '.' }
                                if (filtered.count { it == '.' } <= 1) {
                                    sendAmount = filtered
                                }
                            },
                            placeholder = { Text("0.00") },
                            modifier = Modifier.fillMaxWidth(),
                            trailingIcon = { Text(coin.symbol, color = Color.Gray) },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = redMain,
                                focusedLabelColor = redMain,
                                cursorColor = redMain
                            )
                        )
                    }
                }

                // 수수료 및 총 출금액 정보
                item {
                    Column {
                        Divider(color = Color.LightGray, thickness = 1.dp)

                        Spacer(modifier = Modifier.height(12.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "수수료",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                            Text(
                                "$fee ${coin.symbol}",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }

                        Spacer(modifier = Modifier.height(8.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                "총 출금",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "${formatDecimal(totalAmount)} ${coin.symbol}",
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = redMain
                            )
                        }
                    }
                }

                // 송금하기 버튼 - QR 스캔 버튼과 같은 형식
                item {
                    Column {
                        Spacer(modifier = Modifier.height(16.dp))

                        OutlinedButton(
                            onClick = {
                                processSend()
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp),
                            enabled = !isSending,
                            colors = ButtonDefaults.outlinedButtonColors(
                                backgroundColor = if (canSend && !isSending) redMain else Color.LightGray,
                                contentColor = if (canSend && !isSending) Color.White else Color.Gray
                            ),
                            border = BorderStroke(
                                1.dp,
                                if (canSend && !isSending) redMain else Color.Gray
                            )
                        ) {
                            if (isSending) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.Center
                                ) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        color = Color.Gray,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        "송금 중...",
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            } else {
                                Text(
                                    "송금하기",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // QR 스캐너 다이얼로그
    if (showQrScanner) {
        QRCodeScannerDialog(
            onDismiss = { showQrScanner = false },
            onQrCodeScanned = { scannedAddress ->
                recipientAddress = scannedAddress
                showQrScanner = false
                Toast.makeText(context, "QR 코드가 스캔되었습니다", Toast.LENGTH_SHORT).show()
            }
        )
    }

    // 코인 선택 다이얼로그
    if (showCoinSelector) {
        Dialog(
            onDismissRequest = { showCoinSelector = false }
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 200.dp, max = 500.dp),
                backgroundColor = Color.White,
                elevation = 8.dp
            ) {
                Column(
                    modifier = Modifier.padding(20.dp)
                ) {
                    Text(
                        "보유 중인 코인",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    if (holdingCoins.isEmpty()) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                "보유 중인 코인이 없습니다",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "먼저 코인을 구매해주세요",
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    } else {
                        LazyColumn(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(300.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            items(holdingCoins) { coin ->
                                Card(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCoin = coin
                                            sendAmount = ""
                                            showCoinSelector = false
                                        },
                                    backgroundColor = if (selectedCoin == coin) Color(0xFFFFF3F3) else Color(0xFFF8F8F8),
                                    elevation = 1.dp
                                ) {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(16.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column {
                                            Text(
                                                "${coin.name} (${coin.symbol})",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                            Text(
                                                "보유: ${formatDecimal(coin.balance)} ${coin.symbol}",
                                                fontSize = 14.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                        }

                                        Text(
                                            "${String.format(Locale.KOREA, "%,.0f", coin.price)}원",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = { showCoinSelector = false }
                        ) {
                            Text(
                                "취소",
                                color = Color.Gray,
                                fontSize = 14.sp
                            )
                        }
                    }
                }
            }
        }
    }
}