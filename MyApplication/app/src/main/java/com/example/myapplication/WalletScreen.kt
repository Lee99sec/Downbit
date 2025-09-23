// WalletScreen.kt - 송금 내역 상세정보 기능 추가 및 색상/아이콘 개선
package com.example.myapplication

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.QrCode
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CallReceived
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import android.graphics.Bitmap
import android.widget.Toast
import com.example.myapplication.LoginPage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.*

// ZXing 라이브러리 import
import com.google.zxing.BarcodeFormat
import com.google.zxing.WriterException
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.common.BitMatrix

// 색상 상수 정의
private val SendColor = Color(0xFFE53E3E)      // 더 진한 빨간색 (송금)
private val ReceiveColor = Color(0xFF38A169)   // 더 진한 녹색 (입금)
private val SendColorLight = Color(0xFFFC8181) // 연한 빨간색 (송금 배경)
private val ReceiveColorLight = Color(0xFF68D391) // 연한 녹색 (입금 배경)

// 지갑 생성 응답 데이터 클래스
data class CreateWalletResponse(
    val address: String
)

// 송금 내역 데이터 클래스 (상세 정보 추가)
data class RemittanceHistory(
    val type: String,        // "SEND" 또는 "RECEIVE"
    val targetAddress: String,
    val symbol: String,
    val amount: java.math.BigDecimal,
    val timestamp: String = "",  // 거래 시간
    val transactionHash: String = "",  // 거래 해시 (있는 경우)
    val status: String = "완료"  // 거래 상태
)

// 송금 내역 응답 데이터 클래스
data class RemittanceHistoryResponse(
    val tradeList: List<RemittanceHistory>
)

// 송금 내역 조회 함수
suspend fun fetchRemittanceHistory(context: Context): List<RemittanceHistory> = withContext(Dispatchers.IO) {
    try {
        val authManager = AuthTokenManager(context)

        val result = authManager.makeAuthenticatedRequest(
            url = "${AuthTokenManager.BASE_URL}/remittance/log",
            method = "GET"
        )

        result.fold(
            onSuccess = { responseData ->
                Log.d("WalletScreen", "송금 내역 조회 성공: $responseData")

                try {
                    val jsonObject = JSONObject(responseData)
                    val tradeListArray = jsonObject.getJSONArray("tradeList")

                    val historyList = mutableListOf<RemittanceHistory>()
                    for (i in 0 until tradeListArray.length()) {
                        val item = tradeListArray.getJSONObject(i)
                        historyList.add(
                            RemittanceHistory(
                                type = item.getString("type"),
                                targetAddress = item.getString("targetAddress"),
                                symbol = item.getString("symbol"),
                                amount = java.math.BigDecimal(item.getString("amount")),
                                timestamp = item.optString("timestamp", ""),
                                transactionHash = item.optString("transactionHash", ""),
                                status = item.optString("status", "완료")
                            )
                        )
                    }

                    Log.d("WalletScreen", "송금 내역 파싱 완료: ${historyList.size}개")
                    historyList
                } catch (e: Exception) {
                    Log.e("WalletScreen", "송금 내역 파싱 실패: ${e.message}")
                    emptyList()
                }
            },
            onFailure = { exception ->
                Log.e("WalletScreen", "송금 내역 조회 실패: ${exception.message}")
                val errorMessage = exception.message ?: ""

                if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                    withContext(Dispatchers.Main) {
                        val intent = Intent(context, LoginPage::class.java)
                        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        context.startActivity(intent)
                    }
                }
                emptyList()
            }
        )
    } catch (e: Exception) {
        Log.e("WalletScreen", "송금 내역 조회 예외: ${e.message}")
        emptyList()
    }
}

// 거래 상세 정보 다이얼로그 컴포저블
@Composable
fun TransactionDetailDialog(
    transaction: RemittanceHistory,
    onDismiss: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current
    val redMain = Color(0xFFD32F2F)

    // 거래 타입에 따른 색상 설정
    val transactionColor = if (transaction.type == "SEND") SendColor else ReceiveColor
    val transactionIcon = if (transaction.type == "SEND") Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(16.dp),
            backgroundColor = Color.White,
            elevation = 8.dp
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "거래 상세정보",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black
                    )
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 거래 타입 및 상태
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 화살표 아이콘만 표시 (배경 제거)
                        Icon(
                            transactionIcon,
                            contentDescription = if (transaction.type == "SEND") "송금" else "입금",
                            tint = transactionColor,
                            modifier = Modifier.size(32.dp)
                        )
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                if (transaction.type == "SEND") "송금" else "입금",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = transactionColor
                            )
                            Text(
                                transaction.status,
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // 금액 표시
                    Text(
                        "${if (transaction.type == "SEND") "-" else "+"}${transaction.amount} ${transaction.symbol}",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = transactionColor
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
                Divider(color = Color.LightGray, thickness = 1.dp)
                Spacer(modifier = Modifier.height(16.dp))

                // 상세 정보 섹션
                DetailInfoRow(
                    label = if (transaction.type == "SEND") "받는 주소" else "보낸 주소",
                    value = transaction.targetAddress,
                    onCopy = {
                        clipboardManager.setText(AnnotatedString(transaction.targetAddress))
                        Toast.makeText(context, "주소가 복사되었습니다", Toast.LENGTH_SHORT).show()
                    },
                    isCopyable = true
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailInfoRow(
                    label = "코인 종류",
                    value = transaction.symbol,
                    onCopy = null,
                    isCopyable = false
                )

                Spacer(modifier = Modifier.height(16.dp))

                DetailInfoRow(
                    label = "거래 금액",
                    value = "${transaction.amount} ${transaction.symbol}",
                    onCopy = null,
                    isCopyable = false
                )

                if (transaction.timestamp.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailInfoRow(
                        label = "거래 시간",
                        value = formatTimestamp(transaction.timestamp),
                        onCopy = null,
                        isCopyable = false
                    )
                }

                if (transaction.transactionHash.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    DetailInfoRow(
                        label = "거래 해시",
                        value = transaction.transactionHash,
                        onCopy = {
                            clipboardManager.setText(AnnotatedString(transaction.transactionHash))
                            Toast.makeText(context, "거래 해시가 복사되었습니다", Toast.LENGTH_SHORT).show()
                        },
                        isCopyable = true
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // 닫기 버튼
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = redMain),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        "확인",
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}

// 상세 정보 행 컴포저블
@Composable
private fun DetailInfoRow(
    label: String,
    value: String,
    onCopy: (() -> Unit)?,
    isCopyable: Boolean
) {
    Column(
        modifier = Modifier.fillMaxWidth()
    ) {
        Text(
            text = label,
            fontSize = 14.sp,
            color = Color.Gray,
            fontWeight = FontWeight.Medium
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isCopyable && value.length > 20)
                    "${value.take(10)}...${value.takeLast(10)}"
                else value,
                fontSize = 16.sp,
                color = Color.Black,
                fontFamily = if (isCopyable) FontFamily.Monospace else FontFamily.Default,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.weight(1f)
            )
            if (isCopyable && onCopy != null) {
                IconButton(
                    onClick = onCopy,
                    modifier = Modifier.size(24.dp)
                ) {
                    Icon(
                        Icons.Default.ContentCopy,
                        contentDescription = "복사",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

// 타임스탬프 포맷 함수
private fun formatTimestamp(timestamp: String): String {
    return try {
        // API에서 받은 타임스탬프 형식에 맞게 조정
        val sdf = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
        val date = sdf.parse(timestamp)
        val displayFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.getDefault())
        displayFormat.format(date ?: Date())
    } catch (e: Exception) {
        timestamp // 파싱 실패시 원본 반환
    }
}

@Composable
fun WalletScreen() {
    val context = LocalContext.current
    val clipboardManager = LocalClipboardManager.current
    val redMain = Color(0xFFD32F2F)

    // 지갑 관련 상태들
    var walletBalance by remember { mutableStateOf(0.0) }
    var isLoading by remember { mutableStateOf(true) }
    var walletAddress by remember { mutableStateOf<String?>(null) }
    var qrCodeBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isGeneratingAddress by remember { mutableStateOf(false) }
    var showSendScreen by remember { mutableStateOf(false) }
    var createWalletError by remember { mutableStateOf("") }

    // 송금 내역 관련 상태들
    var remittanceHistory by remember { mutableStateOf<List<RemittanceHistory>>(emptyList()) }
    var isLoadingHistory by remember { mutableStateOf(false) }

    // 거래 상세정보 다이얼로그 상태
    var showTransactionDetail by remember { mutableStateOf(false) }
    var selectedTransaction by remember { mutableStateOf<RemittanceHistory?>(null) }

    // 실제 QR 코드 생성 함수
    fun generateQRCode(text: String): ImageBitmap? {
        return try {
            val writer = QRCodeWriter()
            val bitMatrix: BitMatrix = writer.encode(text, BarcodeFormat.QR_CODE, 300, 300)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)

            for (x in 0 until width) {
                for (y in 0 until height) {
                    bitmap.setPixel(
                        x, y,
                        if (bitMatrix[x, y]) android.graphics.Color.BLACK
                        else android.graphics.Color.WHITE
                    )
                }
            }

            bitmap.asImageBitmap()
        } catch (e: WriterException) {
            Log.e("WalletScreen", "QR 코드 생성 실패: ${e.message}")
            null
        }
    }

    // 송금 내역 로드 함수
    fun loadRemittanceHistory() {
        if (walletAddress == null) return

        isLoadingHistory = true
        CoroutineScope(Dispatchers.IO).launch {
            val history = fetchRemittanceHistory(context)
            withContext(Dispatchers.Main) {
                remittanceHistory = history
                isLoadingHistory = false
            }
        }
    }

    // 기존 지갑 주소 확인 함수
    fun checkExistingWallet() {
        isLoading = true
        createWalletError = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authManager = AuthTokenManager(context)

                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/address",
                    method = "GET"
                )

                result.fold(
                    onSuccess = { responseData ->
                        Log.d("WalletScreen", "지갑 주소 조회 성공: $responseData")

                        try {
                            val jsonObject = JSONObject(responseData)
                            val address = jsonObject.getString("address")

                            withContext(Dispatchers.Main) {
                                if (address.isNotBlank() && address.length == 34) {
                                    walletAddress = address
                                    qrCodeBitmap = generateQRCode(address)
                                    Log.d("WalletScreen", "기존 지갑 주소 발견: $address")
                                } else {
                                    walletAddress = null
                                    Log.d("WalletScreen", "기존 지갑 없음")
                                }
                                isLoading = false
                            }
                        } catch (e: Exception) {
                            Log.e("WalletScreen", "응답 파싱 실패: ${e.message}")
                            withContext(Dispatchers.Main) {
                                walletAddress = null
                                isLoading = false
                            }
                        }
                    },
                    onFailure = { exception ->
                        Log.e("WalletScreen", "지갑 주소 조회 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""

                        withContext(Dispatchers.Main) {
                            if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                            } else {
                                walletAddress = null
                            }
                            isLoading = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("WalletScreen", "지갑 주소 조회 요청 예외: ${e.message}")
                withContext(Dispatchers.Main) {
                    walletAddress = null
                    isLoading = false
                }
            }
        }
    }

    // 지갑 생성 함수
    fun createWallet() {
        isGeneratingAddress = true
        createWalletError = ""

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val authManager = AuthTokenManager(context)

                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/create/wallet",
                    method = "POST"
                )

                result.fold(
                    onSuccess = { responseData ->
                        Log.d("WalletScreen", "지갑 생성 성공: $responseData")

                        try {
                            val jsonObject = JSONObject(responseData)
                            val address = jsonObject.getString("address")

                            withContext(Dispatchers.Main) {
                                walletAddress = address
                                qrCodeBitmap = generateQRCode(address)
                                isGeneratingAddress = false
                                Toast.makeText(context, "지갑이 성공적으로 생성되었습니다!", Toast.LENGTH_SHORT).show()
                                Log.d("WalletScreen", "QR 코드 생성 완료: $address")
                            }
                        } catch (e: Exception) {
                            Log.e("WalletScreen", "응답 파싱 실패: ${e.message}")
                            withContext(Dispatchers.Main) {
                                createWalletError = "지갑 주소 파싱 실패"
                                isGeneratingAddress = false
                            }
                        }
                    },
                    onFailure = { exception ->
                        Log.e("WalletScreen", "지갑 생성 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""

                        withContext(Dispatchers.Main) {
                            if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                            } else {
                                createWalletError = "지갑 생성 실패: $errorMessage"
                            }
                            isGeneratingAddress = false
                        }
                    }
                )
            } catch (e: Exception) {
                Log.e("WalletScreen", "지갑 생성 요청 예외: ${e.message}")
                withContext(Dispatchers.Main) {
                    createWalletError = "네트워크 오류가 발생했습니다"
                    isGeneratingAddress = false
                }
            }
        }
    }

    // 주소 복사 함수
    fun copyAddress() {
        walletAddress?.let { address ->
            clipboardManager.setText(AnnotatedString(address))
            Toast.makeText(context, "지갑 주소가 복사되었습니다", Toast.LENGTH_SHORT).show()
        }
    }

    // 화면 진입 시 기존 지갑 확인
    LaunchedEffect(Unit) {
        checkExistingWallet()
    }

    // 지갑 주소가 있을 때 내역도 로드
    LaunchedEffect(walletAddress) {
        if (walletAddress != null) {
            loadRemittanceHistory()
        }
    }

    // 슬라이드 애니메이션을 위한 Box
    Box(modifier = Modifier.fillMaxSize()) {
        // 지갑 메인 화면
        AnimatedVisibility(
            visible = !showSendScreen,
            enter = slideInHorizontally(
                initialOffsetX = { -it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { -it },
                animationSpec = tween(300)
            )
        ) {
            WalletMainScreen(
                isLoading = isLoading,
                walletAddress = walletAddress,
                qrCodeBitmap = qrCodeBitmap,
                isGeneratingAddress = isGeneratingAddress,
                createWalletError = createWalletError,
                remittanceHistory = remittanceHistory,
                isLoadingHistory = isLoadingHistory,
                redMain = redMain,
                onCreateWallet = { createWallet() },
                onCopyAddress = { copyAddress() },
                onShowSendScreen = { showSendScreen = true },
                onRetryCreate = {
                    createWalletError = ""
                    createWallet()
                },
                onRefreshHistory = { loadRemittanceHistory() },
                onTransactionClick = { transaction ->
                    selectedTransaction = transaction
                    showTransactionDetail = true
                }
            )
        }

        // 송금 화면
        AnimatedVisibility(
            visible = showSendScreen,
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = tween(300)
            ),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = tween(300)
            )
        ) {
            SendScreen(
                onBackClick = {
                    showSendScreen = false
                    // 송금 완료 후 돌아왔을 때 내역 새로고침
                    loadRemittanceHistory()
                }
            )
        }
    }

    // 거래 상세정보 다이얼로그
    if (showTransactionDetail && selectedTransaction != null) {
        TransactionDetailDialog(
            transaction = selectedTransaction!!,
            onDismiss = {
                showTransactionDetail = false
                selectedTransaction = null
            }
        )
    }
}

@Composable
private fun WalletMainScreen(
    isLoading: Boolean,
    walletAddress: String?,
    qrCodeBitmap: ImageBitmap?,
    isGeneratingAddress: Boolean,
    createWalletError: String,
    remittanceHistory: List<RemittanceHistory>,
    isLoadingHistory: Boolean,
    redMain: Color,
    onCreateWallet: () -> Unit,
    onCopyAddress: () -> Unit,
    onShowSendScreen: () -> Unit,
    onRetryCreate: () -> Unit,
    onRefreshHistory: () -> Unit,
    onTransactionClick: (RemittanceHistory) -> Unit
) {
    val context = LocalContext.current

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
    ) {
        // 로딩 중일 때
        if (isLoading) {
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 80.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(
                            color = redMain,
                            strokeWidth = 3.dp,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            "지갑 정보를 확인하는 중...",
                            fontSize = 16.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        } else if (walletAddress == null) {
            // 지갑이 없을 때 - 지갑 생성 버튼만 표시
            item {
                Box(
                    modifier = Modifier.fillMaxWidth().padding(vertical = 40.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Default.AccountBalanceWallet,
                            contentDescription = "지갑",
                            tint = Color.Gray,
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "디지털 지갑이 없습니다",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium,
                            color = Color.Gray
                        )

                        Spacer(modifier = Modifier.height(8.dp))

                        Text(
                            "지갑을 생성하여 디지털 거래를 시작하세요",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = onCreateWallet,
                            enabled = !isGeneratingAddress,
                            colors = ButtonDefaults.buttonColors(backgroundColor = redMain),
                            modifier = Modifier.fillMaxWidth(0.8f)
                        ) {
                            if (isGeneratingAddress) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    CircularProgressIndicator(
                                        color = Color.White,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("생성 중...", color = Color.White)
                                }
                            } else {
                                Text("지갑 생성하기", color = Color.White, fontSize = 16.sp)
                            }
                        }

                        // 에러 메시지 표시
                        if (createWalletError.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                createWalletError,
                                color = redMain,
                                fontSize = 14.sp,
                                textAlign = TextAlign.Center
                            )
                            TextButton(onClick = onRetryCreate) {
                                Text("다시 시도", color = redMain)
                            }
                        }
                    }
                }
            }
        } else {
            // 지갑이 있을 때 - 전체 지갑 UI 표시

            // 지갑 카드 (QR 코드와 지갑 주소)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFF1A1A1A),
                    elevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        // 실제 QR 코드 표시
                        qrCodeBitmap?.let { bitmap ->
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .background(Color.White, RoundedCornerShape(12.dp))
                                    .border(1.dp, Color.Gray, RoundedCornerShape(12.dp))
                                    .padding(8.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Image(
                                    bitmap = bitmap,
                                    contentDescription = "지갑 주소 QR 코드",
                                    modifier = Modifier.fillMaxSize()
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                        } ?: run {
                            // QR 코드 생성 실패시 표시
                            Box(
                                modifier = Modifier
                                    .size(140.dp)
                                    .background(Color.LightGray, RoundedCornerShape(12.dp)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    "QR 코드\n생성 실패",
                                    textAlign = TextAlign.Center,
                                    color = Color.Gray
                                )
                            }
                            Spacer(modifier = Modifier.height(16.dp))
                        }

                        // 지갑 주소 표시
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "내 지갑 주소",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                    color = Color.Gray
                                )
                                IconButton(
                                    onClick = onCopyAddress,
                                    modifier = Modifier.size(28.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ContentCopy,
                                        contentDescription = "주소 복사",
                                        tint = Color.White,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            // 지갑 주소 표시
                            Text(
                                text = walletAddress,
                                fontSize = 14.sp,
                                fontFamily = FontFamily.Monospace,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                textAlign = TextAlign.Center,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        Color(0xFF2A2A2A),
                                        RoundedCornerShape(12.dp)
                                    )
                                    .padding(16.dp)
                            )
                        }
                    }
                }
            }

            // 지갑 기능 버튼들
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    // QR코드 스캔
                    OutlinedButton(
                        onClick = {
                            Toast.makeText(context, "QR 스캔 기능은 준비 중입니다", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f).padding(end = 8.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = redMain
                        ),
                        border = BorderStroke(1.dp, redMain)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.QrCode,
                                contentDescription = "QR 스캔",
                                tint = redMain,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "QR 스캔",
                                fontSize = 12.sp
                            )
                        }
                    }

                    // 송금
                    Button(
                        onClick = onShowSendScreen,
                        modifier = Modifier.weight(1f).padding(start = 8.dp),
                        colors = ButtonDefaults.buttonColors(backgroundColor = redMain)
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Default.Send,
                                contentDescription = "송금",
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "송금",
                                fontSize = 12.sp,
                                color = Color.White
                            )
                        }
                    }
                }
            }

            // 구분선
            item {
                Divider(
                    color = Color.LightGray,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
            }

            // 최근 거래 내역 섹션 헤더
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "최근 지갑 거래",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (!isLoadingHistory && remittanceHistory.isNotEmpty()) {
                        TextButton(onClick = onRefreshHistory) {
                            Text("새로고침", color = redMain, fontSize = 12.sp)
                        }
                    }
                }
            }

            // 거래 내역 표시
            item {
                if (isLoadingHistory) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = redMain)
                    }
                } else if (remittanceHistory.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                "아직 지갑 거래 내역이 없습니다",
                                fontSize = 16.sp,
                                color = Color.Gray
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "QR 스캔이나 송금을 통해 거래를 시작해보세요",
                                fontSize = 14.sp,
                                color = Color.Gray,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    // 실제 거래 내역 표시 (클릭 가능) - 개선된 색상 및 아이콘
                    Column {
                        remittanceHistory.take(10).forEach { history ->
                            val transactionColor = if (history.type == "SEND") SendColor else ReceiveColor
                            val transactionColorLight = if (history.type == "SEND") SendColorLight else ReceiveColorLight
                            val transactionIcon = if (history.type == "SEND") Icons.Default.ArrowUpward else Icons.Default.ArrowDownward

                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp)
                                    .clickable {
                                        onTransactionClick(history)
                                    },
                                backgroundColor = Color.White,
                                elevation = 2.dp,
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    // 송금/입금 아이콘과 정보 (개선된 디자인)
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        // 화살표 아이콘만 표시 (원형 배경 제거)
                                        Icon(
                                            transactionIcon,
                                            contentDescription = if (history.type == "SEND") "송금" else "입금",
                                            tint = transactionColor,
                                            modifier = Modifier.size(28.dp)
                                        )
                                        Spacer(modifier = Modifier.width(12.dp))
                                        Column {
                                            Text(
                                                if (history.type == "SEND") "송금" else "입금",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = transactionColor
                                            )
                                            Text(
                                                "${history.targetAddress.take(10)}...${history.targetAddress.takeLast(6)}",
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    // 금액 정보와 상세보기 표시
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(
                                            horizontalAlignment = Alignment.End
                                        ) {
                                            Text(
                                                "${if (history.type == "SEND") "-" else "+"}${history.amount} ${history.symbol}",
                                                fontSize = 16.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = transactionColor
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Icon(
                                            Icons.Default.Info,
                                            contentDescription = "상세정보",
                                            tint = Color.Gray,
                                            modifier = Modifier.size(16.dp)
                                        )
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