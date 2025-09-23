// File: DepositScreen.kt
package com.example.myapplication

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.AlertDialog
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Locale
import kotlin.system.exitProcess
import com.example.myapplication.security.E2EEncryptionUtils
import com.example.myapplication.LoginPage
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import com.example.myapplication.WalletScreen

// --- 암호화된 요청 DTOs ---
data class EncryptedDepositRequest(
    val e2edata: String
)

data class EncryptedWithdrawRequest(
    val e2edata: String
)

// --- DTOs (must match ApiService.kt) ---
data class LinkAccountRequest(
    val token: String,
    val accountNumber: String,
    val bankName: String,
    val name: String // 예금주명 추가
)

data class WithdrawRequest(
    val token: String,
    val userId: Long,
    val amount: Double
)

data class DepositRequest(
    val token: String,
    val userId: Long,
    val amount: Double
)

// 계좌 삭제 요청 DTO - Body로 전송
data class DeleteAccountRequest(
    val token: String
)

// 수정된 계좌 삭제 응답 DTO - API 명세서에 맞게 조정
data class DeleteAccountResponse(
    val message: String,
    val warning: String? = null
)

// 수정된 계좌 삭제 오류 응답 DTO - API 명세서에 맞게 조정
data class DeleteAccountErrorResponse(
    val error: String,
    val errorCode: String,
    val suggestion: String
)

// 사용자 정보 응답 DTO
data class UserInfoResponse(
    val cash: Double,
    val userId: Long,
    val username: String?
)

// 계좌 목록 응답 DTO
data class AccountResponse(
    val accountNumber: String,
    val bankName: String
)

// 수정된 거래 내역 응답 DTO - 서버 API 명세에 맞춤
data class DepositWithdrawHistoryResponse(
    val history: List<DepositWithdrawLog>
)

// 수정된 거래 내역 아이템 DTO
data class DepositWithdrawLog(
    val type: String,        // "DEPOSIT" 또는 "WITHDRAW"
    val amount: Double,      // 거래 금액
    val timestamp: String,   // "2025-08-07T10:30:00" 형식
    val status: String       // "COMPLETED" 또는 "FAILED"
)

// --- Domain models ---
data class BankAccount(
    val id: Int,
    val bankName: String,
    val accountNumber: String,
    val accountHolder: String,
    val isDefault: Boolean = false
)

enum class TransactionType { DEPOSIT, WITHDRAW }
enum class TransactionStatus { COMPLETED, FAILED }

data class Transaction(
    val id: Int,
    val type: TransactionType,
    val amount: Long,
    val date: String,
    val time: String,
    val status: TransactionStatus,
    val bankInfo: String? = null
)

// PIN 인증이 필요한 거래 타입 정의
enum class PendingTransactionType { DEPOSIT, WITHDRAW }

@Composable
fun DepositScreen() {
    val context = LocalContext.current

    // AuthTokenManager 사용
    val authManager = remember { AuthTokenManager(context) }

    // 뒤로가기 두 번 누르기 관련 상태 추가
    var backPressedTime by remember { mutableStateOf(0L) }
    val backPressedInterval = 2000L // 2초

    val scope = rememberCoroutineScope()
    val redMain = Color(0xFFD32F2F)

    // 탭 상태
    var selectedTab by remember { mutableStateOf(0) }
    val tabs = listOf("입출금", "계좌관리", "지갑") // "지갑" 탭 추가

    // 거래 내역 & 계좌 목록 (빈 상태로 초기화) - 타입 명시
    var transactionList by remember { mutableStateOf<List<Transaction>>(emptyList()) }
    var accountList by remember { mutableStateOf<List<BankAccount>>(emptyList()) }

    // 사용자 정보 및 로딩 상태
    var currentBalance by remember { mutableStateOf(0.0) }
    var isLoadingUserInfo by remember { mutableStateOf(false) }
    var userInfoError by remember { mutableStateOf("") }

    // 계좌 목록 로딩 상태 추가
    var isLoadingAccounts by remember { mutableStateOf(false) }
    var accountsError by remember { mutableStateOf("") }

    // 거래 내역 로딩 상태 추가
    var isLoadingTransactions by remember { mutableStateOf(false) }
    var transactionsError by remember { mutableStateOf("") }

    // SwipeRefresh 상태 추가 (입출금 탭 전용)
    var isRefreshing by remember { mutableStateOf(false) }

    // 입금 Dialog 상태
    var showDepositDialog by remember { mutableStateOf(false) }
    var depositAmount by remember { mutableStateOf("") }
    var depositLoading by remember { mutableStateOf(false) }
    var depositError by remember { mutableStateOf("") }

    // 출금 Dialog 상태
    var showWithdrawDialog by remember { mutableStateOf(false) }
    var withdrawAmount by remember { mutableStateOf("") }
    var withdrawLoading by remember { mutableStateOf(false) }
    var withdrawError by remember { mutableStateOf("") }
    var selectedAccount by remember { mutableStateOf<BankAccount?>(null) }

    // PIN 인증 관련 상태
    var showPinDialog by remember { mutableStateOf(false) }
    var pendingTransactionType by remember { mutableStateOf<PendingTransactionType?>(null) }
    var pendingAmount by remember { mutableStateOf(0L) }
    var pendingAccount by remember { mutableStateOf<BankAccount?>(null) }

    // 계좌 연결 Dialog 상태 (수정 기능 제거)
    var showAccountDialog by remember { mutableStateOf(false) }
    var accountBankName by remember { mutableStateOf("") }
    var accountNumber by remember { mutableStateOf("") }
    var accountHolderName by remember { mutableStateOf("") } // 예금주명 추가
    var accountLoading by remember { mutableStateOf(false) }
    var accountError by remember { mutableStateOf("") }
    var showBankDropdown by remember { mutableStateOf(false) }

    // 계좌 삭제 관련 상태
    var showDeleteConfirmDialog by remember { mutableStateOf(false) }
    var accountToDelete by remember { mutableStateOf<BankAccount?>(null) }
    var isDeleting by remember { mutableStateOf(false) }
    var deleteError by remember { mutableStateOf("") }

    // PIN 다이얼로그용 토큰 상태
    var pinDialogToken by remember { mutableStateOf("") }

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

    // 은행 목록
    val bankList = listOf("신한은행", "국민은행", "우리은행", "농협은행", "하나은행")

    // 입력 정규식 - 숫자만 허용
    val numberRegex = remember { Regex("^\\d*$") }

    // 상한 금액 - 20억원으로 변경
    val maxAmount = 2_000_000_000L

    // 숫자를 천 단위 콤마 포맷으로 변환하는 함수
    fun formatNumberWithCommas(number: String): String {
        if (number.isEmpty()) return ""
        val cleanNumber = number.replace(",", "")
        return try {
            val longValue = cleanNumber.toLong()
            String.format(Locale.KOREA, "%,d", longValue)
        } catch (e: NumberFormatException) {
            number
        }
    }

    // 콤마가 포함된 문자열에서 숫자만 추출하는 함수
    fun extractNumberFromFormatted(formattedNumber: String): String {
        return formattedNumber.replace(",", "")
    }

    // 날짜 형식 변환 함수 - 안전성 강화
    fun formatTimestamp(timestamp: String): Pair<String, String> {
        return try {
            // "2025-08-07T10:30:00" -> "2025-08-07", "10:30"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.KOREA)
            val date = inputFormat.parse(timestamp)

            if (date != null) {
                val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.KOREA)
                val timeFormat = SimpleDateFormat("HH:mm", Locale.KOREA)
                Pair(dateFormat.format(date), timeFormat.format(date))
            } else {
                throw Exception("날짜 파싱 실패")
            }
        } catch (e: Exception) {
            android.util.Log.d("DateFormat", "날짜 파싱 오류: $timestamp - ${e.message}")
            // 파싱 실패 시 문자열 처리로 폴백
            try {
                if (timestamp.contains("T") && timestamp.length >= 16) {
                    val datePart = timestamp.substringBefore("T")
                    val timePart = timestamp.substringAfter("T").substring(0, 5)
                    Pair(datePart, timePart)
                } else {
                    Pair("알 수 없음", "00:00")
                }
            } catch (e2: Exception) {
                Pair("알 수 없음", "00:00")
            }
        }
    }

    // 계좌번호 유효성 검사 함수 - 10~14자리 숫자
    fun validateAccountNumber(accountNum: String): Boolean {
        return accountNum.matches(numberRegex) && accountNum.length in 10..14
    }

    // AuthTokenManager를 사용하는 계좌 삭제 함수
    fun deleteAccount(account: BankAccount) {
        scope.launch {
            isDeleting = true
            deleteError = ""
            try {
                android.util.Log.d("DeleteAccount", "계좌 삭제 시작")

                // AuthTokenManager를 통한 요청 - JSON Body로 토큰 전송
                val requestJson = JSONObject().apply {
                    put("token", authManager.getValidAccessToken() ?: "")
                }

                val result = withContext(Dispatchers.IO) {
                    try {
                        val client = OkHttpClient()
                        val mediaType = "application/json".toMediaType()
                        val requestBody = requestJson.toString().toRequestBody(mediaType)

                        val request = Request.Builder()
                            .url("${AuthTokenManager.BASE_URL}/user/account")
                            .delete(requestBody)
                            .build()

                        val response = client.newCall(request).execute()

                        response.use {
                            when (response.code) {
                                200 -> {
                                    val responseData = response.body?.string() ?: ""
                                    Result.success(responseData)
                                }
                                401 -> {
                                    Result.failure(Exception("토큰 인증 실패"))
                                }
                                400 -> {
                                    val errorBody = response.body?.string() ?: ""
                                    Result.failure(Exception("잘못된 요청: $errorBody"))
                                }
                                else -> {
                                    Result.failure(Exception("서버 오류: ${response.code}"))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        Result.failure(e)
                    }
                }

                result.fold(
                    onSuccess = { responseData ->
                        android.util.Log.d("DeleteAccount", "계좌 삭제 성공: $responseData")

                        // 로컬에서 계좌 삭제
                        accountList = accountList.filter { it.id != account.id }

                        // 삭제된 계좌가 기본 계좌였다면 다른 계좌를 기본으로 설정
                        if (account.isDefault && accountList.isNotEmpty()) {
                            accountList = accountList.mapIndexed { index, acc ->
                                if (index == 0) acc.copy(isDefault = true) else acc
                            }
                        }

                        // 다이얼로그 닫기
                        showDeleteConfirmDialog = false
                        accountToDelete = null

                        Toast.makeText(context, "계좌가 성공적으로 삭제되었습니다", Toast.LENGTH_SHORT).show()
                    },
                    onFailure = { exception ->
                        android.util.Log.e("DeleteAccount", "계좌 삭제 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""

                        deleteError = when {
                            errorMessage.contains("토큰 인증") -> {
                                // 세션 만료 시 로그인 화면으로 이동
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                                "세션이 만료되어 재로그인이 필요합니다"
                            }
                            errorMessage.contains("400") -> "삭제할 계좌 정보가 없거나 잘못된 요청입니다"
                            errorMessage.contains("403") -> "계좌 삭제 권한이 없습니다"
                            errorMessage.contains("404") -> "삭제할 계좌를 찾을 수 없습니다"
                            else -> "계좌 삭제 실패: $errorMessage"
                        }
                    }
                )
            } catch (e: Exception) {
                deleteError = "네트워크 오류: ${e.message}"
                android.util.Log.e("DeleteAccount", "계좌 삭제 네트워크 오류: ${e.message}")
            } finally {
                isDeleting = false
            }
        }
    }

    // 예금주명 유효성 검사 함수
    fun validateAccountHolderName(name: String): Boolean {
        return name.isNotBlank() && name.length >= 2 && name.length <= 10
    }

    // AuthTokenManager를 사용하는 사용자 정보 로딩 함수
    fun loadUserInfo() {
        scope.launch {
            isLoadingUserInfo = true
            userInfoError = ""
            try {
                android.util.Log.d("UserInfo", "사용자 정보 조회 시작")
                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/user/info",
                    method = "GET"
                )

                result.fold(
                    onSuccess = { responseData ->
                        try {
                            val jsonResponse = JSONObject(responseData)
                            val previousBalance = currentBalance
                            currentBalance = jsonResponse.getDouble("cash")
                            android.util.Log.d("UserInfo", "잔액 업데이트: ${String.format(Locale.KOREA, "%,.0f", previousBalance)}원 → ${String.format(Locale.KOREA, "%,.0f", currentBalance)}원")
                        } catch (e: Exception) {
                            userInfoError = "사용자 정보 파싱 오류: ${e.message}"
                            android.util.Log.e("UserInfo", "JSON 파싱 오류: ${e.message}")
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("UserInfo", "사용자 정보 조회 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""
                        if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                            userInfoError = "세션이 만료되었습니다. 다시 로그인해주세요."
                            val intent = Intent(context, LoginPage::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        } else {
                            userInfoError = "사용자 정보 조회 실패: $errorMessage"
                        }
                    }
                )
            } catch (e: Exception) {
                userInfoError = "네트워크 오류: ${e.message}"
                android.util.Log.e("UserInfo", "사용자 정보 조회 예외: ${e.message}")
            } finally {
                isLoadingUserInfo = false
            }
        }
    }

    // AuthTokenManager를 사용하는 계좌 목록 로딩 함수
    fun loadAccountList() {
        scope.launch {
            isLoadingAccounts = true
            accountsError = ""
            try {
                android.util.Log.d("AccountList", "계좌 목록 조회 시작")

                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/user/account",
                    method = "GET"
                )

                result.fold(
                    onSuccess = { responseData ->
                        try {
                            val jsonResponse = JSONObject(responseData)
                            android.util.Log.d("AccountList", "계좌 응답: $responseData")

                            val accountNumber = jsonResponse.optString("accountNumber", "")
                            val bankName = jsonResponse.optString("bankName", "")

                            if (accountNumber.isBlank() || bankName.isBlank()) {
                                accountList = emptyList()
                                android.util.Log.d("AccountList", "등록된 계좌가 없습니다")
                            } else {
                                val bankAccount = BankAccount(
                                    id = 1,
                                    bankName = bankName,
                                    accountNumber = accountNumber,
                                    accountHolder = "예금주",
                                    isDefault = true
                                )
                                accountList = listOf(bankAccount)
                                android.util.Log.d("AccountList", "계좌 정보 로드: $bankName $accountNumber")
                            }
                        } catch (e: Exception) {
                            accountsError = "계좌 목록 파싱 오류: ${e.message}"
                            android.util.Log.e("AccountList", "JSON 파싱 오류: ${e.message}")
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("AccountList", "계좌 목록 조회 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""
                        if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                            accountsError = "세션이 만료되었습니다. 다시 로그인해주세요."
                            val intent = Intent(context, LoginPage::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        } else {
                            accountsError = "계좌 목록 조회 실패: $errorMessage"
                        }
                    }
                )
            } catch (e: Exception) {
                accountsError = "네트워크 오류: ${e.message}"
                android.util.Log.e("AccountList", "계좌 목록 조회 예외: ${e.message}")
            } finally {
                isLoadingAccounts = false
            }
        }
    }

    // AuthTokenManager를 사용하는 거래 내역 로딩 함수
    fun loadTransactionHistory() {
        scope.launch {
            isLoadingTransactions = true
            transactionsError = ""
            try {
                android.util.Log.d("TransactionHistory", "거래 내역 조회 시작")

                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/deposit-withdraw",
                    method = "GET"
                )

                result.fold(
                    onSuccess = { responseData ->
                        try {
                            val jsonResponse = JSONObject(responseData)
                            android.util.Log.d("TransactionHistory", "거래 내역 응답: $responseData")

                            val historyArray = jsonResponse.getJSONArray("history")

                            if (historyArray.length() == 0) {
                                transactionList = emptyList()
                                android.util.Log.d("TransactionHistory", "거래 내역이 비어있습니다")
                            } else {
                                val transactions = mutableListOf<Transaction>()
                                for (i in 0 until historyArray.length()) {
                                    val historyItem = historyArray.getJSONObject(i)
                                    val type = historyItem.getString("type")
                                    val amount = historyItem.getDouble("amount")
                                    val timestamp = historyItem.getString("timestamp")
                                    val status = historyItem.getString("status")

                                    val dateTimePair = formatTimestamp(timestamp)
                                    val date = dateTimePair.first
                                    val time = dateTimePair.second

                                    transactions.add(
                                        Transaction(
                                            id = i + 1,
                                            type = when (type.uppercase()) {
                                                "DEPOSIT" -> TransactionType.DEPOSIT
                                                "WITHDRAW" -> TransactionType.WITHDRAW
                                                else -> TransactionType.DEPOSIT
                                            },
                                            amount = amount.toLong(),
                                            date = date,
                                            time = time,
                                            status = when (status.uppercase()) {
                                                "COMPLETED" -> TransactionStatus.COMPLETED
                                                "FAILED" -> TransactionStatus.FAILED
                                                else -> TransactionStatus.COMPLETED
                                            }
                                        )
                                    )
                                }
                                transactionList = transactions
                                android.util.Log.d("TransactionHistory", "거래 내역 로드 완료 - ${transactions.size}개 항목")
                            }
                        } catch (e: Exception) {
                            transactionsError = "거래 내역 파싱 오류: ${e.message}"
                            android.util.Log.e("TransactionHistory", "JSON 파싱 오류: ${e.message}")
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("TransactionHistory", "거래 내역 조회 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""
                        if (errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인")) {
                            transactionsError = "세션이 만료되었습니다. 다시 로그인해주세요."
                            val intent = Intent(context, LoginPage::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                        } else {
                            transactionsError = "거래 내역 조회 실패: $errorMessage"
                        }
                    }
                )
            } catch (e: Exception) {
                transactionsError = "네트워크 오류: ${e.message}"
                android.util.Log.e("TransactionHistory", "거래 내역 조회 예외: ${e.message}")
            } finally {
                isLoadingTransactions = false
            }
        }
    }

    // 전체 데이터 새로고침 함수 (입출금 탭 전용)
    fun refreshAllData() {
        isRefreshing = true
        scope.launch {
            try {
                loadUserInfo()
                loadAccountList()
                loadTransactionHistory()
            } finally {
                isRefreshing = false
            }
        }
    }

    // AuthTokenManager를 사용하는 E2E 암호화 입금 처리 함수
    fun processDepositAfterPin(amount: Long) {
        scope.launch {
            depositLoading = true
            depositError = ""
            try {
                // 토큰 만료 시 자동 갱신 후 재시도하는 함수
                suspend fun performEncryptedDepositRequest(retryCount: Int = 0): Result<String> {
                    // AuthTokenManager에서 유효한 토큰 가져오기
                    val accessToken = try {
                        authManager.getValidAccessToken()
                    } catch (e: Exception) {
                        android.util.Log.e("DepositRequest", "토큰 가져오기 실패: ${e.message}")
                        return Result.failure(Exception("인증 오류가 발생했습니다"))
                    }

                    if (accessToken == null) {
                        return Result.failure(Exception("로그인이 필요합니다"))
                    }

                    // 토큰과 amount 함께 E2E 암호화
                    val encryptedData = try {
                        E2EEncryptionUtils.encryptData(
                            "token" to accessToken,
                            "amount" to amount.toDouble()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("DepositRequest", "E2E 암호화 실패: ${e.message}")
                        return Result.failure(Exception("암호화 처리 오류: ${e.message}"))
                    }

                    // 암호화된 요청 객체 생성
                    val requestJson = JSONObject().apply {
                        put("e2edata", encryptedData)
                    }

                    android.util.Log.d("DepositRequest", "입금 요청 시도 $retryCount - amount: $amount")

                    // 직접 HTTP 요청
                    return withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient()
                            val mediaType = "application/json".toMediaType()
                            val requestBody = requestJson.toString().toRequestBody(mediaType)

                            val request = Request.Builder()
                                .url("${AuthTokenManager.BASE_URL}/deposit")
                                .post(requestBody)
                                .build()

                            val response = client.newCall(request).execute()

                            response.use {
                                when (response.code) {
                                    200, 201 -> {
                                        val responseData = response.body?.string() ?: ""
                                        android.util.Log.d("DepositRequest", "입금 요청 성공")
                                        Result.success(responseData)
                                    }
                                    401 -> {
                                        android.util.Log.w("DepositRequest", "401 에러 발생 - 토큰 만료")
                                        if (retryCount == 0) {
                                            android.util.Log.d("DepositRequest", "토큰 갱신 후 재시도")
                                            // 토큰 갱신을 위한 임시 요청
                                            val refreshResult = authManager.makeAuthenticatedRequest(
                                                url = "${AuthTokenManager.BASE_URL}/user/info",
                                                method = "GET"
                                            )

                                            refreshResult.fold(
                                                onSuccess = {
                                                    android.util.Log.d("DepositRequest", "토큰 갱신 성공 - 원래 요청 재시도")
                                                    performEncryptedDepositRequest(retryCount + 1)
                                                },
                                                onFailure = { exception ->
                                                    android.util.Log.e("DepositRequest", "토큰 갱신 실패: ${exception.message}")
                                                    val errorMsg = exception.message ?: ""
                                                    if (errorMsg.contains("세션이 만료") || errorMsg.contains("재로그인")) {
                                                        Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다"))
                                                    } else {
                                                        Result.failure(Exception("토큰 갱신 실패"))
                                                    }
                                                }
                                            )
                                        } else {
                                            android.util.Log.e("DepositRequest", "재시도 후에도 401 - 완전 만료")
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
                val result = performEncryptedDepositRequest()

                result.fold(
                    onSuccess = { responseData ->
                        android.util.Log.d("DepositRequest", "입금 완료: $responseData")
                        Toast.makeText(context, "입금이 완료되었습니다!", Toast.LENGTH_SHORT).show()

                        // 입금 성공 시 사용자 정보와 거래 내역 다시 로드
                        loadUserInfo()
                        loadTransactionHistory()

                        // Dialog 닫기
                        showDepositDialog = false
                        depositAmount = ""

                        // 최신 잔고 정보 재조회
                        kotlinx.coroutines.delay(500)
                        loadUserInfo()
                    },
                    onFailure = { exception ->
                        android.util.Log.e("DepositRequest", "입금 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""

                        depositError = when {
                            errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인") -> {
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                                "세션이 만료되어 재로그인이 필요합니다"
                            }
                            errorMessage.contains("500") -> "최대 입금 금액을 초과했습니다. 금액을 확인해주세요."
                            errorMessage.contains("400") -> "잘못된 요청입니다. 입력 정보를 확인해주세요."
                            errorMessage.contains("403") -> "입금 권한이 없습니다."
                            else -> "입금 실패: $errorMessage"
                        }
                    }
                )
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("암호화", true) == true -> "암호화 오류: ${e.message}"
                    e.message?.contains("network", true) == true -> "네트워크 오류: ${e.message}"
                    else -> "입금 처리 오류: ${e.message}"
                }
                depositError = errorMessage
                android.util.Log.e("DepositRequest", "입금 처리 예외: ${e.message}")
            } finally {
                depositLoading = false
            }
        }
    }

    // AuthTokenManager를 사용하는 E2E 암호화 출금 처리 함수
    fun processWithdrawAfterPin(amount: Long, account: BankAccount) {
        scope.launch {
            withdrawLoading = true
            withdrawError = ""
            try {
                // 토큰 만료 시 자동 갱신 후 재시도하는 함수
                suspend fun performEncryptedWithdrawRequest(retryCount: Int = 0): Result<String> {
                    // AuthTokenManager에서 유효한 토큰 가져오기
                    val accessToken = try {
                        authManager.getValidAccessToken()
                    } catch (e: Exception) {
                        android.util.Log.e("WithdrawRequest", "토큰 가져오기 실패: ${e.message}")
                        return Result.failure(Exception("인증 오류가 발생했습니다"))
                    }

                    if (accessToken == null) {
                        return Result.failure(Exception("로그인이 필요합니다"))
                    }

                    // 토큰과 amount 함께 E2E 암호화
                    val encryptedData = try {
                        E2EEncryptionUtils.encryptData(
                            "token" to accessToken,
                            "amount" to amount.toDouble()
                        )
                    } catch (e: Exception) {
                        android.util.Log.e("WithdrawRequest", "E2E 암호화 실패: ${e.message}")
                        return Result.failure(Exception("암호화 처리 오류: ${e.message}"))
                    }

                    // 암호화된 요청 객체 생성
                    val requestJson = JSONObject().apply {
                        put("e2edata", encryptedData)
                    }

                    android.util.Log.d("WithdrawRequest", "출금 요청 시도 $retryCount - amount: $amount")

                    // 직접 HTTP 요청
                    return withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient()
                            val mediaType = "application/json".toMediaType()
                            val requestBody = requestJson.toString().toRequestBody(mediaType)

                            val request = Request.Builder()
                                .url("${AuthTokenManager.BASE_URL}/withdraw")
                                .post(requestBody)
                                .build()

                            val response = client.newCall(request).execute()

                            response.use {
                                when (response.code) {
                                    200, 201 -> {
                                        val responseData = response.body?.string() ?: ""
                                        android.util.Log.d("WithdrawRequest", "출금 요청 성공")
                                        Result.success(responseData)
                                    }
                                    401 -> {
                                        android.util.Log.w("WithdrawRequest", "401 에러 발생 - 토큰 만료")
                                        if (retryCount == 0) {
                                            android.util.Log.d("WithdrawRequest", "토큰 갱신 후 재시도")
                                            // 토큰 갱신을 위한 임시 요청
                                            val refreshResult = authManager.makeAuthenticatedRequest(
                                                url = "${AuthTokenManager.BASE_URL}/user/info",
                                                method = "GET"
                                            )

                                            refreshResult.fold(
                                                onSuccess = {
                                                    android.util.Log.d("WithdrawRequest", "토큰 갱신 성공 - 원래 요청 재시도")
                                                    performEncryptedWithdrawRequest(retryCount + 1)
                                                },
                                                onFailure = { exception ->
                                                    android.util.Log.e("WithdrawRequest", "토큰 갱신 실패: ${exception.message}")
                                                    val errorMsg = exception.message ?: ""
                                                    if (errorMsg.contains("세션이 만료") || errorMsg.contains("재로그인")) {
                                                        Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다"))
                                                    } else {
                                                        Result.failure(Exception("토큰 갱신 실패"))
                                                    }
                                                }
                                            )
                                        } else {
                                            android.util.Log.e("WithdrawRequest", "재시도 후에도 401 - 완전 만료")
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
                val result = performEncryptedWithdrawRequest()

                result.fold(
                    onSuccess = { responseData ->
                        android.util.Log.d("WithdrawRequest", "출금 완료: $responseData")
                        Toast.makeText(context, "출금이 완료되었습니다!", Toast.LENGTH_SHORT).show()

                        // 출금 성공 시 사용자 정보와 거래 내역 다시 로드
                        loadUserInfo()
                        loadTransactionHistory()

                        // Dialog 닫기
                        showWithdrawDialog = false
                        withdrawAmount = ""

                        // 최신 잔고 정보 재조회
                        kotlinx.coroutines.delay(500)
                        loadUserInfo()
                    },
                    onFailure = { exception ->
                        android.util.Log.e("WithdrawRequest", "출금 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""

                        withdrawError = when {
                            errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인") -> {
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                                "세션이 만료되어 재로그인이 필요합니다"
                            }
                            errorMessage.contains("500") -> "최대 출금 금액을 초과했습니다. 금액을 확인해주세요."
                            errorMessage.contains("400") -> "잘못된 요청입니다. 입력 정보를 확인해주세요."
                            errorMessage.contains("403") -> "출금 권한이 없습니다."
                            errorMessage.contains("404") -> "계좌 정보를 찾을 수 없습니다."
                            errorMessage.contains("409") -> "잔액이 부족합니다."
                            else -> "출금 실패: $errorMessage"
                        }
                    }
                )
            } catch (e: Exception) {
                val errorMessage = when {
                    e.message?.contains("암호화", true) == true -> "암호화 오류: ${e.message}"
                    e.message?.contains("network", true) == true -> "네트워크 오류: ${e.message}"
                    else -> "출금 처리 오류: ${e.message}"
                }
                withdrawError = errorMessage
                android.util.Log.e("WithdrawRequest", "출금 처리 예외: ${e.message}")
            } finally {
                withdrawLoading = false
            }
        }
    }

    // PIN 인증 결과 처리
    fun onPinVerificationResult(success: Boolean) {
        showPinDialog = false

        if (success) {
            // PIN 인증 성공 - 실제 거래 실행
            when (pendingTransactionType) {
                PendingTransactionType.DEPOSIT -> {
                    processDepositAfterPin(pendingAmount)
                }
                PendingTransactionType.WITHDRAW -> {
                    val account = pendingAccount
                    if (account != null) {
                        processWithdrawAfterPin(pendingAmount, account)
                    }
                }
                null -> {
                    // 예상치 못한 상황
                    depositError = "거래 정보가 없습니다"
                    withdrawError = "거래 정보가 없습니다"
                }
            }
        } else {
            // PIN 인증 실패
            when (pendingTransactionType) {
                PendingTransactionType.DEPOSIT -> {
                    depositError = "PIN 인증에 실패했습니다"
                }
                PendingTransactionType.WITHDRAW -> {
                    withdrawError = "PIN 인증에 실패했습니다"
                }
                null -> {}
            }
        }

        // 대기 상태 초기화
        pendingTransactionType = null
        pendingAmount = 0L
        pendingAccount = null
    }


    // 앱 시작 시 사용자 정보, 계좌 목록, 거래 내역 로드
    LaunchedEffect(Unit) {
        loadUserInfo()
        loadAccountList()
        loadTransactionHistory()
    }

    // 입출금 탭으로 이동할 때 거래 내역 새로고침
    LaunchedEffect(selectedTab) {
        if (selectedTab == 0) {
            loadTransactionHistory()
        } else if (selectedTab == 1) {
            loadAccountList()
        }
    }

    // SwipeRefresh 상태 관리 (입출금 탭 전용)
    val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

    Column(Modifier.fillMaxSize()) {
        // 페이지 제목
        Text(
            "입출금",
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFD32F2F),
            modifier = Modifier.padding(16.dp)
        )

        // 탭 Row
        TabRow(
            selectedTabIndex = selectedTab,
            backgroundColor = Color.White,
            contentColor = Color(0xFFD32F2F)
        ) {
            tabs.forEachIndexed { idx, title ->
                Tab(
                    selected = selectedTab == idx,
                    onClick = { selectedTab = idx },
                    text = {
                        Text(
                            title,
                            fontWeight = if (selectedTab == idx) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        when (selectedTab) {
            0 -> {
                // 입출금 탭 - SwipeRefresh 적용
                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = { refreshAllData() },
                    indicator = { state, trigger ->
                        SwipeRefreshIndicator(
                            state = state,
                            refreshTriggerDistance = trigger,
                            backgroundColor = Color.White,
                            contentColor = redMain
                        )
                    }
                ) {
                    // 입출금 탭 - LazyColumn으로 변경
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                    ) {
                        // 잔액 정보 카드
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = Color(0xFFF5F5F5),
                                elevation = 4.dp
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text("총 보유", fontSize = 14.sp, color = Color.Gray)

                                    Spacer(modifier = Modifier.height(8.dp))

                                    // 로딩 상태 처리
                                    if (isLoadingUserInfo) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(20.dp),
                                                color = redMain,
                                                strokeWidth = 2.dp
                                            )
                                            Spacer(Modifier.width(8.dp))
                                            Text(
                                                "잔액 조회 중...",
                                                fontSize = 16.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    } else if (userInfoError.isNotEmpty()) {
                                        Column {
                                            Text(
                                                "잔액 조회 실패",
                                                fontSize = 16.sp,
                                                color = redMain
                                            )
                                            Text(
                                                userInfoError,
                                                fontSize = 12.sp,
                                                color = Color.Gray,
                                                modifier = Modifier.padding(top = 2.dp)
                                            )
                                            // 재시도 버튼
                                            TextButton(
                                                onClick = { loadUserInfo() },
                                                modifier = Modifier.padding(top = 4.dp)
                                            ) {
                                                Text("다시 시도", color = redMain, fontSize = 12.sp)
                                            }
                                        }
                                    } else {
                                        Text(
                                            String.format(Locale.KOREA, "%,.0f원", currentBalance),
                                            fontSize = 28.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }

                        // 입금/출금 버튼
                        item {
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                OutlinedButton(
                                    onClick = {
                                        // 계좌가 없을 때 적절한 처리
                                        if (accountList.isEmpty()) {
                                            withdrawError = "등록된 계좌가 없습니다. 먼저 계좌를 등록해주세요."
                                            return@OutlinedButton
                                        }
                                        selectedAccount = accountList.find { it.isDefault } ?: accountList.firstOrNull()
                                        withdrawAmount = ""
                                        withdrawError = ""
                                        showWithdrawDialog = true
                                    },
                                    enabled = accountList.isNotEmpty() && !isLoadingUserInfo,
                                    colors = ButtonDefaults.outlinedButtonColors(
                                        contentColor = Color(0xFFD32F2F)
                                    ),
                                    border = BorderStroke(1.dp, Color(0xFFD32F2F))
                                ) {
                                    Text("원화 출금하기")
                                }
                                Button(
                                    onClick = {
                                        if (accountList.isEmpty()) {
                                            depositError = "등록된 계좌가 없습니다. 먼저 계좌를 등록해주세요."
                                            return@Button
                                        }
                                        depositAmount = ""
                                        depositError = ""
                                        showDepositDialog = true
                                    },
                                    colors = ButtonDefaults.buttonColors(backgroundColor = redMain),
                                    enabled = accountList.isNotEmpty() && !isLoadingUserInfo
                                ) {
                                    Text("원화 입금하기", color = Color.White)
                                }
                            }
                        }

                        // 오류 메시지 표시
                        if (depositError.isNotEmpty() && !showDepositDialog) {
                            item {
                                Text(
                                    depositError,
                                    color = redMain,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        if (withdrawError.isNotEmpty() && !showWithdrawDialog) {
                            item {
                                Text(
                                    withdrawError,
                                    color = redMain,
                                    fontSize = 12.sp,
                                    modifier = Modifier.padding(horizontal = 4.dp)
                                )
                            }
                        }

                        // 구분선
                        item {
                            Divider(color = Color.LightGray)
                        }

                        // 최근 거래 내역 제목과 새로고침 버튼
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("최근 거래 내역", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                                if (!isLoadingTransactions) {
                                    TextButton(
                                        onClick = { loadTransactionHistory() }
                                    ) {
                                        Text("새로고침", color = redMain, fontSize = 12.sp)
                                    }
                                }
                            }
                        }

                        // 거래 내역 로딩 상태 처리
                        if (isLoadingTransactions) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        CircularProgressIndicator(
                                            color = redMain,
                                            strokeWidth = 2.dp
                                        )
                                        Spacer(Modifier.height(8.dp))
                                        Text(
                                            "거래 내역 조회 중...",
                                            fontSize = 14.sp,
                                            color = Color.Gray
                                        )
                                    }
                                }
                            }
                        } else if (transactionsError.isNotEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                        Text(
                                            "거래 내역 조회 실패",
                                            fontSize = 16.sp,
                                            color = redMain
                                        )
                                        Text(
                                            transactionsError,
                                            fontSize = 12.sp,
                                            color = Color.Gray,
                                            modifier = Modifier.padding(top = 2.dp)
                                        )
                                        TextButton(
                                            onClick = { loadTransactionHistory() },
                                            modifier = Modifier.padding(top = 4.dp)
                                        ) {
                                            Text("다시 시도", color = redMain, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }
                        } else if (transactionList.isEmpty()) {
                            // 거래 내역이 없을 때 표시할 메시지
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().padding(32.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        "거래 내역이 없습니다",
                                        fontSize = 16.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        } else {
                            items(transactionList) { transaction ->
                                TransactionListItem(transaction)
                            }
                        }
                    }
                }
            }
            1 -> {
                // 계좌관리 탭 - 일반 LazyColumn (SwipeRefresh 없음)
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                ) {
                    // 계좌 관리 헤더
                    item {
                        Row(
                            Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("등록된 계좌", fontSize = 18.sp, fontWeight = FontWeight.Bold)
                            IconButton(
                                onClick = {
                                    accountBankName = ""
                                    accountNumber = ""
                                    accountHolderName = ""
                                    accountError = ""
                                    showAccountDialog = true
                                },
                                enabled = accountList.isEmpty() // 계좌가 없을 때만 활성화
                            ) {
                                Icon(
                                    Icons.Default.Add,
                                    contentDescription = "계좌 추가",
                                    tint = if (accountList.isEmpty()) Color(0xFFD32F2F) else Color.Gray
                                )
                            }
                        }
                    }

                    // 계좌 목록 로딩 상태 처리
                    if (isLoadingAccounts) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    CircularProgressIndicator(
                                        color = redMain,
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "계좌 목록 조회 중...",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    } else if (accountsError.isNotEmpty()) {
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "계좌 목록 조회 실패",
                                        fontSize = 16.sp,
                                        color = redMain
                                    )
                                    Text(
                                        accountsError,
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 2.dp)
                                    )
                                    TextButton(
                                        onClick = { loadAccountList() },
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Text("다시 시도", color = redMain, fontSize = 12.sp)
                                    }
                                }
                            }
                        }
                    } else if (accountList.isEmpty()) {
                        // 등록된 계좌가 없을 때 표시할 메시지
                        item {
                            Box(
                                modifier = Modifier.fillMaxWidth().padding(32.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(
                                        "등록된 계좌가 없습니다",
                                        fontSize = 16.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Text(
                                        "계좌를 추가해주세요",
                                        fontSize = 14.sp,
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    } else {
                        items(accountList) { account ->
                            AccountListItem(
                                account = account,
                                onDelete = {
                                    // 삭제 확인 다이얼로그 표시
                                    accountToDelete = account
                                    showDeleteConfirmDialog = true
                                },
                                onSetDefault = {
                                    accountList = accountList.map { a -> a.copy(isDefault = a.id == account.id) }
                                }
                            )
                        }
                    }
                }
            }
            2 -> {
                // 4. 새로운 지갑 탭 추가
                WalletScreen()
            }
        }
    }

    // --- PIN 입력 다이얼로그 ---
    PinInputDialog(
        isVisible = showPinDialog,
        title = when (pendingTransactionType) {
            PendingTransactionType.DEPOSIT -> "입금 인증"
            PendingTransactionType.WITHDRAW -> "출금 인증"
            null -> "PIN 인증"
        },
        authManager = authManager, // token 대신 authManager 전달
        onDismiss = {
            showPinDialog = false
            pendingTransactionType = null
            pendingAmount = 0L
            pendingAccount = null
        },
        onPinVerified = ::onPinVerificationResult
    )

    // --- 입금 다이얼로그 ---
    if (showDepositDialog) {
        val defaultAccount = accountList.find { it.isDefault } ?: accountList.firstOrNull()

        AlertDialog(
            onDismissRequest = {
                showDepositDialog = false
                depositError = ""
                depositAmount = ""
            },
            title = { Text("무통장 입금") },
            text = {
                Column {
                    defaultAccount?.let { account ->
                        Text(
                            "출금 계좌: ${account.bankName} ${account.accountNumber}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = depositAmount,
                        onValueChange = { newValue ->
                            // 숫자만 허용하고 20억원 이하만 허용
                            val cleanValue = newValue.filter { it.isDigit() }
                            val inputAmount = cleanValue.toLongOrNull() ?: 0L
                            if (inputAmount <= maxAmount) {
                                depositAmount = cleanValue
                                depositError = ""
                            }
                        },
                        label = { Text("입금 금액") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = depositError.isNotEmpty(),
                        trailingIcon = { Text("원", color = Color.Gray) },
                        visualTransformation = AmountVisualTransformation(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFD32F2F),
                            focusedLabelColor = Color(0xFFD32F2F),
                            cursorColor = Color(0xFFD32F2F)
                        )
                    )
                    Text(
                        text = "입금 한도: 1회 최대 ${String.format(Locale.KOREA, "%,d", maxAmount)}원",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )
                    if (depositError.isNotEmpty()) {
                        Text(depositError, color = redMain, fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // 입력값 검증 강화
                    val amount = depositAmount.toLongOrNull() ?: 0L
                    when {
                        depositAmount.isBlank() -> {
                            depositError = "입금 금액을 입력해주세요"
                            return@Button
                        }
                        amount <= 0 -> {
                            depositError = "0원보다 큰 금액을 입력해주세요"
                            return@Button
                        }
                        amount > maxAmount -> {
                            depositError = "입금 한도는 ${String.format(Locale.KOREA, "%,d", maxAmount)}원입니다"
                            return@Button
                        }
                    }

                    // PIN 인증을 위해 대기 상태로 설정
                    pendingTransactionType = PendingTransactionType.DEPOSIT
                    pendingAmount = amount
                    pendingAccount = defaultAccount
                    showPinDialog = true

                }, enabled = !depositLoading && depositAmount.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))) {
                    if (depositLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("PIN 인증", color = Color.White)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showDepositDialog = false
                    depositAmount = ""
                    depositError = ""
                },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F))) {
                    Text("취소")
                }
            }
        )
    }

    // --- 계좌 삭제 확인 다이얼로그 ---
    if (showDeleteConfirmDialog && accountToDelete != null) {
        AlertDialog(
            onDismissRequest = {
                showDeleteConfirmDialog = false
                accountToDelete = null
                deleteError = ""
            },
            title = {
                Text(
                    "계좌 삭제 확인",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    color = redMain
                )
            },
            text = {
                Column {
                    Text(
                        "다음 계좌를 삭제하시겠습니까?",
                        fontSize = 16.sp,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFF5F5F5),
                        elevation = 2.dp
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                accountToDelete!!.bankName,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                accountToDelete!!.accountNumber,
                                fontSize = 14.sp,
                                color = Color.Gray,
                                modifier = Modifier.padding(top = 2.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color(0xFFFFF3E0),
                        elevation = 1.dp
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "⚠️",
                                fontSize = 16.sp,
                                modifier = Modifier.padding(end = 8.dp)
                            )
                            Text(
                                "삭제된 계좌는 복구할 수 없습니다.",
                                fontSize = 12.sp,
                                color = Color(0xFFE65100)
                            )
                        }
                    }

                    if (deleteError.isNotEmpty()) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            deleteError,
                            color = redMain,
                            fontSize = 12.sp
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        accountToDelete?.let { account ->
                            deleteAccount(account)
                        }
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.buttonColors(backgroundColor = redMain)
                ) {
                    if (isDeleting) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("삭제", color = Color.White)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showDeleteConfirmDialog = false
                        accountToDelete = null
                        deleteError = ""
                    },
                    enabled = !isDeleting,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color.Gray
                    ),
                    border = BorderStroke(1.dp, Color.Gray)
                ) {
                    Text("취소")
                }
            }
        )
    }

    // --- 출금 다이얼로그 ---
    if (showWithdrawDialog && selectedAccount != null) {
        AlertDialog(
            onDismissRequest = {
                showWithdrawDialog = false
                withdrawError = ""
                withdrawAmount = ""
            },
            title = { Text("원화 출금") },
            text = {
                Column(modifier = Modifier.padding(bottom = 0.dp)) {
                    selectedAccount?.let { account ->
                        Text(
                            "입금 계좌: ${account.bankName} ${account.accountNumber}",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                    }
                    OutlinedTextField(
                        value = withdrawAmount,
                        onValueChange = { newValue ->
                            // 숫자만 허용하고 20억원 이하만 허용
                            val cleanValue = newValue.filter { it.isDigit() }
                            val inputAmount = cleanValue.toLongOrNull() ?: 0L
                            if (inputAmount <= maxAmount) {
                                withdrawAmount = cleanValue
                                withdrawError = ""
                            }
                        },
                        label = { Text("출금 금액") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        isError = withdrawError.isNotEmpty(),
                        trailingIcon = { Text("원", color = Color.Gray) },
                        visualTransformation = AmountVisualTransformation(),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFD32F2F),
                            focusedLabelColor = Color(0xFFD32F2F),
                            cursorColor = Color(0xFFD32F2F)
                        )
                    )
                    Text(
                        text = "출금 한도: 1회 최대 ${String.format(Locale.KOREA, "%,d", maxAmount)}원",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )

                    // 최대 출금 금액 버튼
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(top = 0.dp),
                        horizontalArrangement = Arrangement.End
                    ) {
                        TextButton(
                            onClick = {
                                // 최대 출금 가능 금액을 보유액과 출금 한도 중 작은 값으로 설정
                                val userBalance = currentBalance.toLong()
                                val maxWithdrawAmount = minOf(userBalance, maxAmount)

                                if (maxWithdrawAmount > 0) {
                                    withdrawAmount = maxWithdrawAmount.toString()
                                    withdrawError = ""
                                } else {
                                    withdrawError = "출금 가능한 금액이 없습니다"
                                }
                            },
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text(
                                "최대 출금",
                                color = Color(0xFFD32F2F),
                                fontSize = 12.sp
                            )
                        }
                    }
                    if (withdrawError.isNotEmpty()) {
                        Text(
                            withdrawError,
                            color = redMain,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    // 출금 금액 검증
                    val amt = withdrawAmount.toLongOrNull() ?: 0L

                    when {
                        withdrawAmount.isBlank() -> {
                            withdrawError = "출금 금액을 입력해주세요"
                            return@Button
                        }
                        amt <= 0 -> {
                            withdrawError = "0원보다 큰 금액을 입력해주세요"
                            return@Button
                        }
                        amt > maxAmount -> {
                            withdrawError = "출금 한도는 ${String.format(Locale.KOREA, "%,d", maxAmount)}원입니다"
                            return@Button
                        }
                        amt.toDouble() > currentBalance -> {
                            withdrawError = "잔액이 부족합니다"
                            return@Button
                        }
                    }

                    // PIN 인증을 위해 대기 상태로 설정
                    pendingTransactionType = PendingTransactionType.WITHDRAW
                    pendingAmount = amt
                    pendingAccount = selectedAccount
                    showPinDialog = true

                }, enabled = !withdrawLoading && withdrawAmount.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color(0xFFD32F2F))) {
                    if (withdrawLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("PIN 인증", color = Color.White)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(onClick = {
                    showWithdrawDialog = false
                    withdrawAmount = ""
                    withdrawError = ""
                },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F))) {
                    Text("취소")
                }
            }
        )
    }

    // --- 계좌 연결 다이얼로그 ---
    if (showAccountDialog) {
        // AuthTokenManager를 사용하는 계좌 연결 함수
        fun linkAccount() {
            scope.launch {
                accountLoading = true
                accountError = ""
                try {
                    android.util.Log.d("LinkAccount", "계좌 연결 시작")

                    // AuthTokenManager를 통한 요청 - JSON Body로 토큰 전송
                    val requestJson = JSONObject().apply {
                        put("token", authManager.getValidAccessToken() ?: "")
                        put("accountNumber", accountNumber)
                        put("bankName", accountBankName)
                        put("name", accountHolderName)
                    }

                    val result = withContext(Dispatchers.IO) {
                        try {
                            val client = OkHttpClient()
                            val mediaType = "application/json".toMediaType()
                            val requestBody = requestJson.toString().toRequestBody(mediaType)

                            val request = Request.Builder()
                                .url("${AuthTokenManager.BASE_URL}/user/account")
                                .post(requestBody)
                                .build()

                            val response = client.newCall(request).execute()

                            response.use {
                                when (response.code) {
                                    200 -> {
                                        val responseData = response.body?.string() ?: ""
                                        Result.success(responseData)
                                    }
                                    401 -> {
                                        Result.failure(Exception("토큰 검증 실패 또는 유효하지 않은 로그인 정보"))
                                    }
                                    403 -> {
                                        Result.failure(Exception("예금주명과 계정의 실명이 일치하지 않습니다"))
                                    }
                                    409 -> {
                                        Result.failure(Exception("이미 계좌 연결이 되어 있습니다"))
                                    }
                                    else -> {
                                        Result.failure(Exception("연결 실패: ${response.code}"))
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Result.failure(e)
                        }
                    }

                    result.fold(
                        onSuccess = { responseData ->
                            android.util.Log.d("LinkAccount", "계좌 연결 성공: $responseData")
                            Toast.makeText(context, "계좌가 성공적으로 연결되었습니다!", Toast.LENGTH_SHORT).show()

                            // 성공 시 계좌 목록 새로고침
                            loadAccountList()
                            showAccountDialog = false
                            showBankDropdown = false
                            accountBankName = ""
                            accountNumber = ""
                            accountHolderName = ""
                        },
                        onFailure = { exception ->
                            android.util.Log.e("LinkAccount", "계좌 연결 실패: ${exception.message}")
                            val errorMessage = exception.message ?: ""

                            accountError = when {
                                errorMessage.contains("토큰 검증") -> {
                                    // 세션 만료 시 로그인 화면으로 이동
                                    val intent = Intent(context, LoginPage::class.java)
                                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                    context.startActivity(intent)
                                    "세션이 만료되어 재로그인이 필요합니다"
                                }
                                errorMessage.contains("예금주명") -> "예금주명과 계정의 실명이 일치하지 않습니다"
                                errorMessage.contains("이미 계좌") -> "이미 계좌 연결이 되어 있습니다"
                                else -> errorMessage
                            }
                        }
                    )
                } catch (e: Exception) {
                    accountError = "네트워크 오류: ${e.message}"
                    android.util.Log.e("LinkAccount", "계좌 연결 네트워크 오류: ${e.message}")
                } finally {
                    accountLoading = false
                }
            }
        }

        AlertDialog(
            onDismissRequest = {
                showAccountDialog = false
                showBankDropdown = false
                accountError = ""
                accountBankName = ""
                accountNumber = ""
                accountHolderName = ""
            },
            title = { Text("계좌 연결") },
            text = {
                Column {
                    // 은행 선택 드롭다운
                    Box {
                        OutlinedTextField(
                            value = accountBankName,
                            onValueChange = { },
                            label = { Text("은행명") },
                            readOnly = true,
                            trailingIcon = {
                                IconButton(onClick = { showBankDropdown = !showBankDropdown }) {
                                    Icon(
                                        if (showBankDropdown) Icons.Default.KeyboardArrowUp
                                        else Icons.Default.KeyboardArrowDown,
                                        contentDescription = "은행 선택"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            isError = accountError.contains("은행"),
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = Color(0xFFD32F2F),
                                focusedLabelColor = Color(0xFFD32F2F),
                                cursorColor = Color(0xFFD32F2F)
                            )
                        )

                        DropdownMenu(
                            expanded = showBankDropdown,
                            onDismissRequest = { showBankDropdown = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            bankList.forEach { bank ->
                                DropdownMenuItem(
                                    onClick = {
                                        accountBankName = bank
                                        showBankDropdown = false
                                        accountError = ""
                                    }
                                ) {
                                    Text(bank)
                                }
                            }
                        }
                    }

                    Spacer(Modifier.height(8.dp))

                    // 계좌번호 입력
                    OutlinedTextField(
                        value = accountNumber,
                        onValueChange = { input ->
                            // 숫자만 허용하고 최대 14자리 제한
                            val digitsOnly = input.filter { it.isDigit() }
                            if (digitsOnly.length <= 14) {
                                accountNumber = digitsOnly
                                accountError = ""
                            }
                        },
                        label = { Text("계좌번호") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = accountError.contains("계좌번호"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFD32F2F),
                            focusedLabelColor = Color(0xFFD32F2F),
                            cursorColor = Color(0xFFD32F2F)
                        )
                    )

                    Spacer(Modifier.height(8.dp))

                    // 예금주명 입력
                    OutlinedTextField(
                        value = accountHolderName,
                        onValueChange = { input ->
                            // 한글, 영문만 허용하고 최대 10자리 제한
                            val filteredInput = input.filter { it.isLetter() || it.isWhitespace() }
                            if (filteredInput.length <= 10) {
                                accountHolderName = filteredInput
                                accountError = ""
                            }
                        },
                        label = { Text("예금주명") },
                        modifier = Modifier.fillMaxWidth(),
                        isError = accountError.contains("예금주"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = Color(0xFFD32F2F),
                            focusedLabelColor = Color(0xFFD32F2F),
                            cursorColor = Color(0xFFD32F2F)
                        )
                    )

                    // 계좌번호 입력 가이드 텍스트
                    Text(
                        text = "계좌번호는 10-14자리 숫자로 입력해주세요",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                    )

                    // 예금주명 가이드 텍스트
                    Text(
                        text = "예금주명은 가입 시 등록한 실명과 정확히 일치해야 합니다",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                    )

                    if (accountError.isNotEmpty()) {
                        Spacer(Modifier.height(4.dp))
                        Text(
                            accountError,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        // 입력값 검증
                        when {
                            accountBankName.isBlank() -> {
                                accountError = "은행을 선택해주세요"
                                return@Button
                            }
                            accountNumber.isBlank() -> {
                                accountError = "계좌번호를 입력해주세요"
                                return@Button
                            }
                            !validateAccountNumber(accountNumber) -> {
                                accountError = "계좌번호는 10-14자리 숫자로 입력해주세요"
                                return@Button
                            }
                            accountHolderName.isBlank() -> {
                                accountError = "예금주명을 입력해주세요"
                                return@Button
                            }
                            !validateAccountHolderName(accountHolderName) -> {
                                accountError = "예금주명은 2-10자리로 입력해주세요"
                                return@Button
                            }
                            accountList.any { it.accountNumber == accountNumber } -> {
                                accountError = "이미 등록된 계좌번호입니다"
                                return@Button
                            }
                        }

                        linkAccount()
                    },
                    enabled = !accountLoading,
                    colors = ButtonDefaults.buttonColors(backgroundColor = redMain)
                ) {
                    if (accountLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White
                        )
                    } else {
                        Text("연결", color = Color.White)
                    }
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = {
                        showAccountDialog = false
                        showBankDropdown = false
                        accountError = ""
                        accountBankName = ""
                        accountNumber = ""
                        accountHolderName = ""
                    },
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFD32F2F)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFD32F2F))
                ) {
                    Text("취소")
                }
            }
        )
    }
}

@Composable
fun TransactionListItem(transaction: Transaction) {
    val redMain = Color(0xFFD32F2F)
    val greenMain = Color(0xFF4CAF50)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = 1.dp
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = if (transaction.type == TransactionType.DEPOSIT) "입금" else "출금",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = if (transaction.type == TransactionType.DEPOSIT) greenMain else redMain
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Card(
                        backgroundColor = when (transaction.status) {
                            TransactionStatus.COMPLETED -> Color(0xFF4CAF50)
                            TransactionStatus.FAILED -> Color(0xFFD32F2F)
                        }
                    ) {
                        Text(
                            text = when (transaction.status) {
                                TransactionStatus.COMPLETED -> "완료"
                                TransactionStatus.FAILED -> "실패"
                            },
                            color = Color.White,
                            fontSize = 10.sp,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = "${transaction.date} ${transaction.time}",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(top = 2.dp)
                )
                transaction.bankInfo?.let { bankInfo ->
                    Text(
                        text = bankInfo,
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = "${if (transaction.type == TransactionType.DEPOSIT) "+" else "-"}${String.format(Locale.KOREA, "%,d", transaction.amount)}원",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = if (transaction.type == TransactionType.DEPOSIT) greenMain else redMain
            )
        }
    }
}

// 금액 입력용 VisualTransformation - 천 단위 콤마 추가
class AmountVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = if (digits.isEmpty()) {
            ""
        } else {
            try {
                val number = digits.toLong()
                String.format(Locale.KOREA, "%,d", number)
            } catch (e: NumberFormatException) {
                digits
            }
        }

        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                if (offset == 0) return 0
                if (digits.isEmpty()) return 0

                // 원본 텍스트의 offset을 포맷된 텍스트의 offset으로 변환
                val beforeOffset = digits.substring(0, minOf(offset, digits.length))
                val formattedBefore = if (beforeOffset.isEmpty()) {
                    ""
                } else {
                    try {
                        val number = beforeOffset.toLong()
                        String.format(Locale.KOREA, "%,d", number)
                    } catch (e: NumberFormatException) {
                        beforeOffset
                    }
                }
                return formattedBefore.length
            }

            override fun transformedToOriginal(offset: Int): Int {
                if (offset == 0) return 0
                if (formatted.isEmpty()) return 0

                // 포맷된 텍스트의 offset을 원본 텍스트의 offset으로 변환
                val beforeOffset = formatted.substring(0, minOf(offset, formatted.length))
                val digitsOnly = beforeOffset.filter { it.isDigit() }
                return digitsOnly.length
            }
        }

        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@Composable
fun AccountListItem(
    account: BankAccount,
    onDelete: () -> Unit,
    onSetDefault: () -> Unit
) {
    val redMain = Color(0xFFD32F2F)
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        elevation = 2.dp
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
                            text = account.bankName,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                        if (account.isDefault) {
                            Spacer(modifier = Modifier.width(8.dp))
                            Card(backgroundColor = redMain) {
                                Text(
                                    text = "기본",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = account.accountNumber,
                        fontSize = 14.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(top = 2.dp)
                    )
                }
                // 삭제 버튼만 남김
                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "계좌 삭제",
                        modifier = Modifier.size(20.dp),
                        tint = Color.Gray
                    )
                }
            }
            if (!account.isDefault) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onSetDefault,
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = redMain)
                ) {
                    Text("기본 계좌로 설정", fontSize = 14.sp)
                }
            }
        }
    }
}