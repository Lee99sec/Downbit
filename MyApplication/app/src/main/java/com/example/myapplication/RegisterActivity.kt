package com.example.myapplication

// RetrofitClient import - 실제 패키지 경로에 맞게 수정 필요
// import com.example.myapplication.network.RetrofitClient

import SecureBaseActivity
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.Gson
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.myapplication.security.E2EEncryptionUtils

data class RegisterRequest(
    @SerializedName("username") val username: String,
    @SerializedName("password") val password: String,
    @SerializedName("jumin") val jumin: String,
    @SerializedName("phone") val phone: String,
    @SerializedName("realName") val realName: String,
    @SerializedName("pin") val pin: String
)

data class SmsRequest(
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class SmsVerifyRequest(
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("verificationCode") val verificationCode: String
)

class RegisterActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    RegisterScreen()
                }
            }
        }
    }
}

@Composable
fun RegisterScreen() {
    val redMain = Color(0xFFD32F2F)
    val context = LocalContext.current
    var currentStep by remember { mutableStateOf("consent") }

    var privacyConsent by remember { mutableStateOf(false) }
    var uniqueIdConsent by remember { mutableStateOf(false) }
    var thirdPartyConsent by remember { mutableStateOf(false) }
    var marketingConsent by remember { mutableStateOf(false) }

    when (currentStep) {
        "consent" -> {
            PrivacyConsentScreen(
                redMain = redMain,
                privacyConsent = privacyConsent,
                uniqueIdConsent = uniqueIdConsent,
                thirdPartyConsent = thirdPartyConsent,
                marketingConsent = marketingConsent,
                onPrivacyConsentChange = { privacyConsent = it },
                onUniqueIdConsentChange = { uniqueIdConsent = it },
                onThirdPartyConsentChange = { thirdPartyConsent = it },
                onMarketingConsentChange = { marketingConsent = it },
                onNext = { currentStep = "register" },
                onBack = {
                    (context as? ComponentActivity)?.finish()
                }
            )
        }
        "register" -> {
            PersonalInfoInputScreen(
                redMain = redMain,
                marketingConsent = marketingConsent,
                onBack = { currentStep = "consent" }
            )
        }
    }
}

@Composable
fun PrivacyConsentScreen(
    redMain: Color,
    privacyConsent: Boolean,
    uniqueIdConsent: Boolean,
    thirdPartyConsent: Boolean,
    marketingConsent: Boolean,
    onPrivacyConsentChange: (Boolean) -> Unit,
    onUniqueIdConsentChange: (Boolean) -> Unit,
    onThirdPartyConsentChange: (Boolean) -> Unit,
    onMarketingConsentChange: (Boolean) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val lazyListState = rememberLazyListState()
    var hasScrolledToBottom by remember { mutableStateOf(false) }

    var showPrivacyDetails by remember { mutableStateOf(false) }
    var showUniqueIdDetails by remember { mutableStateOf(false) }
    var showThirdPartyDetails by remember { mutableStateOf(false) }
    var showMarketingDetails by remember { mutableStateOf(false) }

    var allConsent by remember { mutableStateOf(false) }

    val isScrolledToEnd by remember {
        derivedStateOf {
            val layoutInfo = lazyListState.layoutInfo
            val visibleItemsInfo = layoutInfo.visibleItemsInfo
            if (visibleItemsInfo.isEmpty()) {
                false
            } else {
                val lastVisibleItem = visibleItemsInfo.last()
                val viewportHeight = layoutInfo.viewportEndOffset + layoutInfo.viewportStartOffset
                lastVisibleItem.index == layoutInfo.totalItemsCount - 1 &&
                        lastVisibleItem.offset + lastVisibleItem.size <= viewportHeight
            }
        }
    }

    LaunchedEffect(isScrolledToEnd) {
        if (isScrolledToEnd) {
            hasScrolledToBottom = true
        }
    }

    val isRequiredConsentsChecked = privacyConsent && uniqueIdConsent && thirdPartyConsent
    val canProceed = hasScrolledToBottom && isRequiredConsentsChecked

    LaunchedEffect(allConsent) {
        if (allConsent) {
            onPrivacyConsentChange(true)
            onUniqueIdConsentChange(true)
            onThirdPartyConsentChange(true)
            onMarketingConsentChange(true)
        }
    }

    LaunchedEffect(privacyConsent, uniqueIdConsent, thirdPartyConsent, marketingConsent) {
        allConsent = privacyConsent && uniqueIdConsent && thirdPartyConsent && marketingConsent
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = redMain
                )
            }
            Text(
                "개인정보 수집 및 이용 동의",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = redMain,
                modifier = Modifier.weight(1f)
            )
        }

        LinearProgressIndicator(
            progress = { 0.5f },
            color = redMain,
            modifier = Modifier.fillMaxWidth()
        )

        LazyColumn(
            state = lazyListState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(24.dp)
        ) {
            item {
                Text(
                    "서비스 이용을 위해 아래 개인정보 수집 및 이용에 대한 동의가 필요합니다.",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 24.dp)
                )
            }

            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { allConsent = !allConsent }
                                .padding(vertical = 12.dp)
                        ) {
                            Checkbox(
                                checked = allConsent,
                                onCheckedChange = { allConsent = it },
                                colors = CheckboxDefaults.colors(checkedColor = redMain)
                            )
                            Text(
                                "전체 동의",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(start = 12.dp)
                            )
                        }

                        HorizontalDivider(color = Color.Gray.copy(alpha = 0.3f), thickness = 1.dp)

                        ConsentItem(
                            isChecked = privacyConsent,
                            onCheckedChange = onPrivacyConsentChange,
                            title = "[필수] 개인정보 수집 및 이용에 동의합니다.",
                            isExpanded = showPrivacyDetails,
                            onExpandToggle = { showPrivacyDetails = !showPrivacyDetails },
                            redMain = redMain
                        ) {
                            ConsentDetailContent(
                                title = "개인정보 수집 및 이용 동의",
                                description = "회원가입 및 본인확인 절차를 위해 아래와 같이 개인정보를 수집·이용합니다.",
                                items = listOf(
                                    "수집 항목: 이메일 주소, 비밀번호, PIN 번호, 실명, 주민등록번호, 휴대전화번호",
                                    "이용 목적: 회원 식별 및 본인확인, 계정 생성 및 로그인 관리",
                                    "보유 기간: 회원 탈퇴 시까지"
                                )
                            )
                        }

                        ConsentItem(
                            isChecked = uniqueIdConsent,
                            onCheckedChange = onUniqueIdConsentChange,
                            title = "[필수] 고유식별정보 수집 및 이용에 동의합니다.",
                            isExpanded = showUniqueIdDetails,
                            onExpandToggle = { showUniqueIdDetails = !showUniqueIdDetails },
                            redMain = redMain
                        ) {
                            ConsentDetailContent(
                                title = "고유식별정보 수집 및 이용 동의",
                                description = "서비스 제공을 위하여 고유식별정보를 수집·이용합니다.",
                                items = listOf(
                                    "수집 항목: 주민등록번호",
                                    "이용 목적: 실명 확인, 중복 가입 방지",
                                    "보유 기간: 관련 법령에서 정한 기간까지"
                                )
                            )
                        }

                        ConsentItem(
                            isChecked = thirdPartyConsent,
                            onCheckedChange = onThirdPartyConsentChange,
                            title = "[필수] 개인정보 제3자 제공에 동의합니다.",
                            isExpanded = showThirdPartyDetails,
                            onExpandToggle = { showThirdPartyDetails = !showThirdPartyDetails },
                            redMain = redMain
                        ) {
                            ConsentDetailContent(
                                title = "개인정보 제3자 제공 동의",
                                description = "서비스 제공을 위해 개인정보를 제3자에게 제공합니다.",
                                items = listOf(
                                    "제공받는 자: 본인확인기관",
                                    "제공 항목: 실명, 주민등록번호, 휴대전화번호",
                                    "제공 목적: 본인확인"
                                )
                            )
                        }

                        ConsentItem(
                            isChecked = marketingConsent,
                            onCheckedChange = onMarketingConsentChange,
                            title = "[선택] 마케팅 정보 수신에 동의합니다.",
                            isExpanded = showMarketingDetails,
                            onExpandToggle = { showMarketingDetails = !showMarketingDetails },
                            redMain = redMain
                        ) {
                            ConsentDetailContent(
                                title = "마케팅 정보 수신 동의",
                                description = "마케팅 정보 수신에 동의하시면 혜택을 받으실 수 있습니다.",
                                items = listOf(
                                    "수집 항목: 이메일, 휴대전화번호",
                                    "이용 목적: 이벤트 및 혜택 안내",
                                    "보유 기간: 동의 철회 시까지"
                                )
                            )
                        }
                    }
                }
            }

            item {
                Spacer(modifier = Modifier.height(24.dp))
                if (hasScrolledToBottom) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "✓ 개인정보 처리방침을 모두 확인했습니다",
                                color = Color(0xFF2E7D32),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "📄 개인정보 처리방침을 끝까지 읽어주세요",
                                color = Color(0xFFE65100),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
                Spacer(modifier = Modifier.height(100.dp))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                if (!hasScrolledToBottom) {
                    Text(
                        "📄 개인정보 처리방침을 끝까지 읽어주세요",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                    )
                }

                Button(
                    onClick = {
                        when {
                            !hasScrolledToBottom -> {
                                Toast.makeText(context, "개인정보 처리방침을 끝까지 읽어주세요", Toast.LENGTH_SHORT).show()
                            }
                            !isRequiredConsentsChecked -> {
                                Toast.makeText(context, "필수 개인정보 동의 항목을 체크해주세요", Toast.LENGTH_SHORT).show()
                            }
                            else -> {
                                onNext()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (canProceed) redMain else Color.Gray
                    ),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text(
                        if (canProceed) "개인정보 입력하기" else "동의 후 진행 가능",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun PersonalInfoInputScreen(
    redMain: Color,
    marketingConsent: Boolean,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current

    var name by remember { mutableStateOf("") }
    var rrnFront by remember { mutableStateOf("") }  // 주민번호 앞자리
    var rrnBack by remember { mutableStateOf("") }   // 주민번호 뒷자리
    var phoneRaw by remember { mutableStateOf("") }
    var isPhoneVerified by remember { mutableStateOf(false) }
    var verificationCode by remember { mutableStateOf("") }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var verificationTimer by remember { mutableStateOf(0) }
    var canResendCode by remember { mutableStateOf(true) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var passwordConfirm by remember { mutableStateOf("") }
    var pin by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var passwordConfirmVisible by remember { mutableStateOf(false) }
    var showRRNBack by remember { mutableStateOf(false) }
    var showPinDialog by remember { mutableStateOf(false) }

    // 이메일 자동완성 관련
    var showEmailSuggestions by remember { mutableStateOf(false) }
    var emailLocalPart by remember { mutableStateOf("") }  // @ 앞부분

    // 주민번호 포커스 관련
    val rrnFrontFocusRequester = remember { FocusRequester() }
    val rrnBackFocusRequester = remember { FocusRequester() }

    var pinNumbers by remember { mutableStateOf((1..9).shuffled()) }

    // 주민번호 합치기
    val rrnRaw = rrnFront + rrnBack

    val isValidRRN = rrnRaw.length == 13
    val isValidPhone = phoneRaw.matches(Regex("^01[016789][0-9]{7,8}$"))
    val isValidEmail = android.util.Patterns.EMAIL_ADDRESS.matcher(email).matches()
    val isValidName = name.isNotBlank()
    val isValidPin = pin.length == 4



    // 이메일 도메인 목록
    val emailDomains = listOf(
        "@gmail.com",
        "@naver.com",
        "@daum.net",
        "@kakao.com",
        "@hanmail.net",
        "@nate.com",
        "@outlook.com",
        "@yahoo.com"
    )

    // 비밀번호 강도 계산 함수
    fun calculatePasswordStrength(pwd: String): Int {
        var strength = 0
        if (pwd.length >= 8) strength++
        if (pwd.length >= 12) strength++
        if (pwd.any { it.isLetter() }) strength++
        if (pwd.any { it.isDigit() }) strength++
        if (pwd.any { !it.isLetterOrDigit() }) strength++
        if (pwd.any { it.isUpperCase() } && pwd.any { it.isLowerCase() }) strength++
        return minOf(strength, 4)  // 최대 4단계
    }

    fun isValidPassword(pwd: String): Boolean {
        if (pwd.length < 8) return false
        val hasLetter = pwd.any { it.isLetter() }
        val hasDigit = pwd.any { it.isDigit() }
        val hasSpecial = pwd.any { !it.isLetterOrDigit() }
        return hasLetter && hasDigit && hasSpecial
    }

    // 비밀번호 강도 레벨
    val passwordStrengthLevel = calculatePasswordStrength(password)
    val passwordStrengthText = when (passwordStrengthLevel) {
        0 -> if (password.isEmpty()) "" else "매우 약함"
        1 -> "약함"
        2 -> "보통"
        3 -> "강함"
        4 -> "매우 강함"
        else -> ""
    }

    val passwordStrengthColor = when (passwordStrengthLevel) {
        0 -> Color(0xFFD32F2F)  // 빨강
        1 -> Color(0xFFFF6B35)  // 주황
        2 -> Color(0xFFFFA726)  // 노랑
        3 -> Color(0xFF66BB6A)  // 연두
        4 -> Color(0xFF2E7D32)  // 초록
        else -> Color.Gray
    }

    // 비밀번호 일치 여부 확인
    val passwordsMatch = password == passwordConfirm && password.isNotEmpty()
    val passwordMatchMessage = when {
        passwordConfirm.isEmpty() -> ""
        password == passwordConfirm -> "✓ 비밀번호가 일치합니다"
        else -> "비밀번호가 일치하지 않습니다"
    }

    // 이메일 입력 처리
    LaunchedEffect(email) {
        if (email.contains("@")) {
            val parts = email.split("@")
            emailLocalPart = parts[0]
            showEmailSuggestions = parts.getOrNull(1)?.isEmpty() == true
        } else {
            emailLocalPart = email
            showEmailSuggestions = false
        }
    }

    // 주민번호 앞자리 6자리 입력 시 자동 포커스 이동
    LaunchedEffect(rrnFront) {
        if (rrnFront.length == 6) {
            rrnBackFocusRequester.requestFocus()
        }
    }
    // 260번째 줄 근처, performRegister 함수 바로 위에 추가
    fun startVerificationTimer() {
        CoroutineScope(Dispatchers.Main).launch {
            verificationTimer = 300  // 5분
            canResendCode = false

            while (verificationTimer > 0) {
                delay(1000)
                verificationTimer--

                if (verificationTimer == 0) {
                    canResendCode = true
                }
            }
        }
    }
    // SMS 발송 함수
    fun sendSmsCode(phone: String) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                isLoading = true  // 로딩 상태 추가

                // 전화번호 포맷팅
                val formattedPhone = formatPhoneNumber(phone)

                val response = RetrofitClient.apiService.sendSmsVerification(
                    SmsRequest(formattedPhone)
                )

                withContext(Dispatchers.Main) {
                    isLoading = false

                    if (response.isSuccessful) {
                        Toast.makeText(context, "인증번호가 발송되었습니다", Toast.LENGTH_SHORT).show()
                        verificationTimer = 300  // 5분 타이머
                        canResendCode = false  // 재전송 방지
                        showVerificationDialog = true  // 다이얼로그 자동 표시

                        // 타이머 시작
                        startVerificationTimer()
                    } else {
                        errorMessage = when (response.code()) {
                            400 -> "잘못된 전화번호 형식입니다"
                            429 -> "일일 SMS 발송 한도를 초과했습니다"
                            500 -> "서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요"
                            else -> "SMS 발송 실패: ${response.message()}"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMessage = when (e) {
                        is java.net.SocketTimeoutException -> "연결 시간이 초과되었습니다"
                        is java.net.UnknownHostException -> "인터넷 연결을 확인해주세요"
                        else -> "네트워크 오류: ${e.localizedMessage}"
                    }
                    Log.e("RegisterActivity", "SMS 발송 실패", e)
                }
            }
        }
    }

    // SMS 인증 확인 함수
    fun verifySmsCode(phone: String, code: String, onSuccess: () -> Unit) {
        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 전화번호 포맷팅
                val formattedPhone = when {
                    phone.length == 10 -> "${phone.substring(0, 3)}-${phone.substring(3, 6)}-${phone.substring(6)}"
                    phone.length == 11 -> "${phone.substring(0, 3)}-${phone.substring(3, 7)}-${phone.substring(7)}"
                    else -> phone
                }

                val response = RetrofitClient.apiService.verifySmsCode(
                    SmsVerifyRequest(formattedPhone, code)
                )

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Toast.makeText(context, "인증이 완료되었습니다", Toast.LENGTH_SHORT).show()
                        onSuccess()
                    } else {
                        errorMessage = when (response.code()) {
                            400 -> "인증번호가 만료되었거나 유효하지 않습니다"
                            401 -> "인증번호가 일치하지 않습니다"
                            else -> "인증 실패: ${response.code()}"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    errorMessage = "네트워크 오류: ${e.message}"
                }
            }
        }
    }

    // 기존 performRegister 함수는 그대로 유지

    fun performRegister(onResult: (Boolean) -> Unit) {
        val formattedJumin = if (rrnRaw.length == 13) {
            "${rrnRaw.substring(0, 6)}-${rrnRaw.substring(6)}"
        } else rrnRaw

        val formattedPhone = when {
            phoneRaw.length == 10 -> "${phoneRaw.substring(0, 3)}-${phoneRaw.substring(3, 6)}-${phoneRaw.substring(6)}"
            phoneRaw.length == 11 -> "${phoneRaw.substring(0, 3)}-${phoneRaw.substring(3, 7)}-${phoneRaw.substring(7)}"
            else -> phoneRaw
        }

        Log.d("RegisterActivity", "=== E2E 암호화 전 데이터 ===")
        Log.d("RegisterActivity", "이름: '$name'")
        Log.d("RegisterActivity", "이메일: '$email'")
        Log.d("RegisterActivity", "전화번호: '$formattedPhone'")
        Log.d("RegisterActivity", "주민번호: '${formattedJumin.take(8)}***'")  // 보안상 일부만 로깅
        Log.d("RegisterActivity", "PIN 길이: ${pin.length}")

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // E2E 암호화 적용
                val dataToEncrypt = mapOf(
                    "username" to email,
                    "password" to password,
                    "jumin" to formattedJumin,
                    "phone" to formattedPhone,
                    "realName" to name,
                    "pin" to pin
                )

                Log.d("RegisterActivity", "=== 암호화 시작 ===")
                val encryptedData = E2EEncryptionUtils.encryptData(dataToEncrypt)
                Log.d("RegisterActivity", "암호화 성공")
                Log.d("RegisterActivity", "암호화된 데이터 길이: ${encryptedData.length}")
                Log.d("RegisterActivity", "암호화된 데이터 미리보기: ${encryptedData.take(50)}...")

                val encryptedRequest = mapOf("e2edata" to encryptedData)
                Log.d("RegisterActivity", "=== API 요청 시작 ===")

                val response = RetrofitClient.apiService.register(encryptedRequest)

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful) {
                        Log.d("RegisterActivity", "=== 회원가입 성공 ===")
                        Log.d("RegisterActivity", "응답 코드: ${response.code()}")
                        onResult(true)
                    } else {
                        val errorBody = response.errorBody()?.string()
                        Log.e("RegisterActivity", "=== 회원가입 실패 ===")
                        Log.e("RegisterActivity", "응답 코드: ${response.code()}")
                        Log.e("RegisterActivity", "에러 내용: $errorBody")
                        errorMessage = "회원가입 실패: ${response.code()}"
                        onResult(false)
                    }
                }
            } catch (e: Exception) {
                Log.e("RegisterActivity", "=== 네트워크 오류 발생 ===")
                Log.e("RegisterActivity", "오류 타입: ${e.javaClass.simpleName}")
                Log.e("RegisterActivity", "오류 메시지: ${e.message}")
                Log.e("RegisterActivity", "스택 트레이스:", e)

                withContext(Dispatchers.Main) {
                    errorMessage = "네트워크 오류: ${e.message}"
                    onResult(false)
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = redMain
                )
            }
            Text(
                "개인정보 입력",
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                color = redMain,
                modifier = Modifier.weight(1f)
            )
        }

        LinearProgressIndicator(
            progress = { 1.0f },
            color = redMain,
            modifier = Modifier.fillMaxWidth()
        )

        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8))
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "✓ 개인정보 수집 및 이용 동의 완료",
                    color = Color(0xFF2E7D32),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.weight(1f))
                if (marketingConsent) {
                    Text(
                        "✓ 마케팅 수신 동의",
                        color = Color(0xFF2E7D32),
                        fontSize = 12.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "회원가입을 위해\n개인정보를 입력해주세요",
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3F2FD)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "🔒",
                        fontSize = 20.sp,
                        modifier = Modifier.padding(end = 12.dp)
                    )
                    Column {
                        Text(
                            "안전한 서버 보안",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1565C0)
                        )
                        Text(
                            "모든 개인정보는 서버에서 안전하게 암호화되어 저장됩니다",
                            fontSize = 12.sp,
                            color = Color(0xFF1976D2)
                        )
                    }
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it.replace(" ", "") },
                label = { Text("이름", color = redMain) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 주민번호 앞자리와 뒷자리 분리 입력
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = rrnFront,
                    onValueChange = {
                        if (it.length <= 6) {
                            rrnFront = it.filter { c -> c.isDigit() }
                        }
                    },
                    label = { Text("주민번호 앞자리", color = redMain) },
                    placeholder = { Text("6자리", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Next
                    ),
                    keyboardActions = KeyboardActions(
                        onNext = { rrnBackFocusRequester.requestFocus() }
                    ),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(rrnFrontFocusRequester),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain
                    )
                )

                Text(
                    "-",
                    fontSize = 24.sp,
                    color = Color.Gray,
                    modifier = Modifier.align(Alignment.CenterVertically)
                )

                OutlinedTextField(
                    value = rrnBack,
                    onValueChange = {
                        if (it.length <= 7) {
                            rrnBack = it.filter { c -> c.isDigit() }
                        }
                    },
                    label = { Text("뒷자리", color = redMain) },
                    placeholder = { Text("7자리", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier
                        .weight(1f)
                        .focusRequester(rrnBackFocusRequester),
                    visualTransformation = if (showRRNBack) {
                        VisualTransformation.None
                    } else {
                        PasswordVisualTransformation('●')
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain
                    ),
                    trailingIcon = {
                        IconButton(onClick = { showRRNBack = !showRRNBack }) {
                            Icon(
                                imageVector = if (showRRNBack) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (showRRNBack) "숨기기" else "보기",
                                tint = redMain
                            )
                        }
                    }
                )
            }

            Text(
                "서버에서 안전하게 암호화됩니다",
                fontSize = 12.sp,
                color = Color.Gray,
                modifier = Modifier.padding(start = 4.dp, top = 4.dp)
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = phoneRaw,
                onValueChange = { phoneRaw = it.filter { c -> c.isDigit() }.take(11) },
                label = { Text("전화번호", color = redMain) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = PhoneVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain
                )
            )
// 374번째 줄 ) 다음에 추가
// 375번째 줄에 새로 삽입할 코드:

// 전화번호 인증 버튼 및 상태 표시
            if (!isPhoneVerified) {
                Button(
                    onClick = {
                        if (isValidPhone) {
                            showVerificationDialog = true
                            sendSmsCode(phoneRaw)  // SMS 발송 함수 호출
                        } else {
                            errorMessage = "올바른 전화번호를 입력해주세요"
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = redMain),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Icon(
                        Icons.Default.Phone,
                        contentDescription = "인증",
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("인증번호 발송", fontSize = 14.sp)
                }
            } else {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E8)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Default.Check,
                            contentDescription = "완료",
                            tint = Color(0xFF2E7D32),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "전화번호 인증이 완료되었습니다",
                            color = Color(0xFF2E7D32),
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
            Spacer(modifier = Modifier.height(16.dp))

            // 이메일 입력 및 자동완성
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = email,
                    onValueChange = {
                        email = it
                        showEmailSuggestions = it.contains("@") && !it.endsWith("@") == false
                    },
                    label = { Text("이메일 (아이디)", color = redMain) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain
                    )
                )

                // 이메일 도메인 자동완성 제안
                if (showEmailSuggestions && emailLocalPart.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column {
                            emailDomains.forEach { domain ->
                                Text(
                                    text = emailLocalPart + domain,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            email = emailLocalPart + domain
                                            showEmailSuggestions = false
                                            focusManager.clearFocus()
                                        }
                                        .padding(horizontal = 16.dp, vertical = 12.dp),
                                    fontSize = 14.sp,
                                    color = Color(0xFF333333)
                                )
                                if (domain != emailDomains.last()) {
                                    HorizontalDivider(
                                        color = Color.Gray.copy(alpha = 0.2f),
                                        thickness = 0.5.dp
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 비밀번호 입력 필드 with 강도 측정기
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("비밀번호", color = redMain) },
                    placeholder = { Text("8자 이상, 문자/숫자/특수문자 포함", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordVisible = !passwordVisible }) {
                            Icon(
                                imageVector = if (passwordVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordVisible) "비밀번호 숨기기" else "비밀번호 보기",
                                tint = redMain
                            )
                        }
                    }
                )

                if (password.isNotEmpty()) {
                    // 비밀번호 강도 표시 바
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 4.dp, vertical = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            repeat(4) { index ->
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(2.dp))
                                        .background(
                                            if (index < passwordStrengthLevel)
                                                passwordStrengthColor
                                            else
                                                Color.Gray.copy(alpha = 0.3f)
                                        )
                                )
                            }
                        }

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                passwordStrengthText,
                                fontSize = 12.sp,
                                color = passwordStrengthColor,
                                fontWeight = FontWeight.Medium
                            )

                            // 비밀번호 요구사항 체크리스트
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val hasLength = password.length >= 8
                                val hasLetter = password.any { it.isLetter() }
                                val hasDigit = password.any { it.isDigit() }
                                val hasSpecial = password.any { !it.isLetterOrDigit() }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasLength) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (hasLength) Color(0xFF2E7D32) else Color.Gray
                                    )
                                    Text(
                                        "8자+",
                                        fontSize = 10.sp,
                                        color = if (hasLength) Color(0xFF2E7D32) else Color.Gray
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasLetter) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (hasLetter) Color(0xFF2E7D32) else Color.Gray
                                    )
                                    Text(
                                        "문자",
                                        fontSize = 10.sp,
                                        color = if (hasLetter) Color(0xFF2E7D32) else Color.Gray
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasDigit) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (hasDigit) Color(0xFF2E7D32) else Color.Gray
                                    )
                                    Text(
                                        "숫자",
                                        fontSize = 10.sp,
                                        color = if (hasDigit) Color(0xFF2E7D32) else Color.Gray
                                    )
                                }

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(
                                        imageVector = if (hasSpecial) Icons.Default.Check else Icons.Default.Close,
                                        contentDescription = null,
                                        modifier = Modifier.size(12.dp),
                                        tint = if (hasSpecial) Color(0xFF2E7D32) else Color.Gray
                                    )
                                    Text(
                                        "특수",
                                        fontSize = 10.sp,
                                        color = if (hasSpecial) Color(0xFF2E7D32) else Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // 비밀번호 확인 입력 필드
            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = passwordConfirm,
                    onValueChange = { passwordConfirm = it },
                    label = { Text("비밀번호 확인", color = redMain) },
                    placeholder = { Text("비밀번호를 다시 입력하세요", color = Color.Gray) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (passwordConfirmVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (passwordConfirm.isNotEmpty() && passwordsMatch) Color(0xFF2E7D32) else redMain,
                        unfocusedBorderColor = if (passwordConfirm.isNotEmpty() && !passwordsMatch) Color(0xFFE65100) else Color.Gray,
                        cursorColor = redMain
                    ),
                    trailingIcon = {
                        IconButton(onClick = { passwordConfirmVisible = !passwordConfirmVisible }) {
                            Icon(
                                imageVector = if (passwordConfirmVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = if (passwordConfirmVisible) "비밀번호 숨기기" else "비밀번호 보기",
                                tint = redMain
                            )
                        }
                    }
                )

                if (passwordConfirm.isNotEmpty()) {
                    Text(
                        passwordMatchMessage,
                        fontSize = 12.sp,
                        color = if (passwordsMatch) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showPinDialog = true },
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (pin.length == 4) Color(0xFFE8F5E8) else Color.White
                )
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "PIN 번호",
                            fontSize = 14.sp,
                            color = redMain,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (pin.length == 4) "●●●●" else "4자리 PIN 번호를 설정하세요",
                            fontSize = 16.sp,
                            color = if (pin.length == 4) Color(0xFF2E7D32) else Color.Gray,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                    if (pin.length == 4) {
                        Text(
                            "✓",
                            fontSize = 20.sp,
                            color = Color(0xFF2E7D32),
                            fontWeight = FontWeight.Bold
                        )
                    } else {
                        Text(
                            "설정",
                            fontSize = 14.sp,
                            color = redMain,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            if (errorMessage.isNotBlank()) {
                Text(errorMessage, color = Color.Red, fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                if (isLoading) {
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = redMain)
                    }
                } else {
                    Button(
                        onClick = {
                            when {
                                !isValidName -> errorMessage = "이름을 입력해주세요"
                                !isValidRRN -> errorMessage = "주민등록번호 13자리를 입력해주세요"
                                !isValidPhone -> errorMessage = "유효한 전화번호를 입력해주세요"
                                !isPhoneVerified -> errorMessage = "전화번호 인증을 완료해주세요"
                                !isValidEmail -> errorMessage = "유효한 이메일을 입력해주세요"
                                !isValidPassword(password) -> errorMessage = "비밀번호는 8자 이상, 문자/숫자/특수문자를 포함해야 합니다"
                                !passwordsMatch -> errorMessage = "비밀번호가 일치하지 않습니다"  // 비밀번호 일치 확인
                                !isValidPin -> errorMessage = "PIN 번호 4자리를 입력해주세요"
                                else -> {
                                    errorMessage = ""
                                    isLoading = true
                                    CoroutineScope(Dispatchers.Main).launch {
                                        performRegister { success ->
                                            isLoading = false
                                            if (success) {
                                                Toast.makeText(context, "회원가입이 완료되었습니다", Toast.LENGTH_LONG).show()
                                                (context as? ComponentActivity)?.finish()
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = redMain),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text(
                            "가입하기",
                            color = Color.White,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }

    if (showPinDialog) {
        PinInputDialog(
            pin = pin,
            onPinChange = { pin = it },
            pinNumbers = pinNumbers,
            onPinNumbersChange = { pinNumbers = it },
            onDismiss = {
                showPinDialog = false
                pinNumbers = (1..9).shuffled()
            },
            redMain = redMain
        )
    }
    if (showVerificationDialog) {
        PhoneVerificationDialog(
            phoneNumber = phoneRaw,
            onVerified = {
                isPhoneVerified = true
                showVerificationDialog = false
            },
            onDismiss = { showVerificationDialog = false },
            redMain = redMain
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinInputDialog(
    pin: String,
    onPinChange: (String) -> Unit,
    pinNumbers: List<Int>,
    onPinNumbersChange: (List<Int>) -> Unit,
    onDismiss: () -> Unit,
    redMain: Color
) {
    var localPin by remember { mutableStateOf(pin) }

    BasicAlertDialog(
        onDismissRequest = { /* 다이얼로그 밖 클릭해도 닫히지 않도록 */ }
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "PIN 번호 설정",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = redMain
                    )
                    IconButton(
                        onClick = {
                            onPinChange(localPin)
                            onDismiss()
                        },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Text(
                            "✕",
                            fontSize = 18.sp,
                            color = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    "보안을 위해 4자리 PIN을 설정하세요",
                    fontSize = 14.sp,
                    color = Color.Gray
                )

                Spacer(modifier = Modifier.height(24.dp))
                //ㅇㅇ
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.padding(bottom = 24.dp)
                ) {
                    repeat(4) { index ->
                        Box(
                            modifier = Modifier
                                .size(50.dp)
                                .border(
                                    2.dp,
                                    if (index < localPin.length) redMain else Color.Gray,
                                    RoundedCornerShape(8.dp)
                                )
                                .background(
                                    if (index < localPin.length) redMain.copy(alpha = 0.1f) else Color.Transparent,
                                    RoundedCornerShape(8.dp)
                                ),
                            contentAlignment = Alignment.Center
                        ) {
                            if (index < localPin.length) {
                                Text(
                                    "●",
                                    fontSize = 24.sp,
                                    color = redMain
                                )
                            }
                        }
                    }
                }

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (row in 0..2) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            for (col in 0..2) {
                                val number = pinNumbers[row * 3 + col]
                                Button(
                                    onClick = {
                                        if (localPin.length < 4) {
                                            localPin += number
                                            onPinNumbersChange((1..9).shuffled())
                                            if (localPin.length == 4) {
                                                onPinChange(localPin)
                                                CoroutineScope(Dispatchers.Main).launch {
                                                    delay(300)
                                                    onDismiss()
                                                }
                                            }
                                        }
                                    },
                                    modifier = Modifier
                                        .size(65.dp)
                                        .clip(RoundedCornerShape(8.dp)),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = Color(0xFFF5F5F5)
                                    ),
                                    elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                                ) {
                                    Text(
                                        number.toString(),
                                        fontSize = 18.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = redMain
                                    )
                                }
                            }
                        }
                    }

                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = {
                                if (localPin.length < 4) {
                                    localPin += "0"
                                    onPinNumbersChange((1..9).shuffled())
                                    if (localPin.length == 4) {
                                        onPinChange(localPin)
                                        CoroutineScope(Dispatchers.Main).launch {
                                            delay(300)
                                            onDismiss()
                                        }
                                    }
                                }
                            },
                            modifier = Modifier
                                .size(65.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                "0",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = redMain
                            )
                        }

                        Button(
                            onClick = {
                                if (localPin.isNotEmpty()) {
                                    localPin = localPin.dropLast(1)
                                }
                            },
                            modifier = Modifier
                                .size(65.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = redMain.copy(alpha = 0.1f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                "⌫",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = redMain
                            )
                        }

                        Button(
                            onClick = {
                                localPin = ""
                                onPinNumbersChange((1..9).shuffled())
                            },
                            modifier = Modifier
                                .size(65.dp)
                                .clip(RoundedCornerShape(8.dp)),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = redMain.copy(alpha = 0.1f)
                            ),
                            elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp)
                        ) {
                            Text(
                                "C",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                color = redMain
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (localPin.length == 4) {
                    Text(
                        "✓ PIN 설정 완료",
                        fontSize = 14.sp,
                        color = Color(0xFF2E7D32),
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun ConsentItem(
    isChecked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    title: String,
    isExpanded: Boolean,
    onExpandToggle: () -> Unit,
    redMain: Color,
    content: @Composable () -> Unit
) {
    Column {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!isChecked) }
                .padding(vertical = 8.dp)
        ) {
            Checkbox(
                checked = isChecked,
                onCheckedChange = onCheckedChange,
                colors = CheckboxDefaults.colors(checkedColor = redMain)
            )
            Text(
                title,
                fontSize = 14.sp,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 8.dp)
            )
            IconButton(
                onClick = onExpandToggle,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = redMain
                )
            }
        }

        if (isExpanded) {
            content()
        }
    }
}

@Composable
fun ConsentDetailContent(
    title: String,
    description: String,
    items: List<String>
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFFF8F9FA))
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                title,
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                description,
                fontSize = 12.sp,
                color = Color(0xFF666666)
            )
            Spacer(modifier = Modifier.height(12.dp))
            items.forEach { item ->
                Text(
                    "• $item",
                    fontSize = 12.sp,
                    color = Color(0xFF666666),
                    modifier = Modifier.padding(bottom = 4.dp)
                )
            }
        }
    }
}

class MaskedRRNVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = when {
            digits.length <= 6 -> digits
            else -> {
                val front = digits.take(6)
                val back = digits.drop(6).take(7)
                val maskedBack = back.mapIndexed { index, _ ->
                    if (index == 0) back[0] else '●'
                }.joinToString("")
                "$front-$maskedBack"
            }
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return if (offset <= 6) offset else offset + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                return if (offset <= 6) offset else offset - 1
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

class RRNVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = when {
            digits.length <= 6 -> digits
            else -> digits.take(6) + "-" + digits.drop(6).take(7)
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return if (offset <= 6) offset else offset + 1
            }

            override fun transformedToOriginal(offset: Int): Int {
                return if (offset <= 6) offset else offset - 1
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

class PhoneVisualTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val digits = text.text.filter { it.isDigit() }
        val formatted = when {
            digits.length < 4 -> digits
            digits.length < 8 -> "${digits.substring(0, 3)}-${digits.substring(3)}"
            digits.length <= 11 -> "${digits.substring(0, 3)}-${digits.substring(3, minOf(7, digits.length))}-${if (digits.length > 7) digits.substring(7) else ""}"
            else -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7, 11)}"
        }
        val offsetMapping = object : OffsetMapping {
            override fun originalToTransformed(offset: Int): Int {
                return when {
                    offset <= 3 -> offset
                    offset <= 7 -> offset + 1
                    offset <= 11 -> offset + 2
                    else -> formatted.length
                }
            }
            override fun transformedToOriginal(offset: Int): Int {
                return when {
                    offset <= 3 -> offset
                    offset <= 8 -> offset - 1
                    offset <= 13 -> offset - 2
                    else -> digits.length
                }
            }
        }
        return TransformedText(AnnotatedString(formatted), offsetMapping)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhoneVerificationDialog(
    phoneNumber: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    redMain: Color
) {
    var code by remember { mutableStateOf("") }
    var timer by remember { mutableStateOf(300) } // 5분
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf("") }
    var canResend by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // 타이머 효과
    LaunchedEffect(timer) {
        if (timer > 0) {
            delay(1000)
            timer--
        } else {
            canResend = true  // 타이머 종료 시 재전송 가능
        }
    }

    // 인증 코드 확인 함수
    fun verifyCode() {
        if (code.length != 6) {
            errorMsg = "6자리 인증번호를 입력해주세요"
            return
        }

        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val formattedPhone = formatPhoneNumber(phoneNumber)
                val response = RetrofitClient.apiService.verifySmsCode(
                    SmsVerifyRequest(formattedPhone, code)
                )

                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (response.isSuccessful) {
                        Toast.makeText(context, "인증이 완료되었습니다", Toast.LENGTH_SHORT).show()
                        onVerified()
                    } else {
                        errorMsg = when (response.code()) {
                            400 -> "인증번호가 만료되었습니다"
                            401 -> "인증번호가 일치하지 않습니다"
                            404 -> "인증 요청을 찾을 수 없습니다"
                            else -> "인증 실패: ${response.message()}"
                        }
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg = "네트워크 오류가 발생했습니다"
                }
            }
        }
    }

    // 재전송 함수
    fun resendCode() {
        isLoading = true
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val formattedPhone = formatPhoneNumber(phoneNumber)
                val response = RetrofitClient.apiService.sendSmsVerification(
                    SmsRequest(formattedPhone)
                )

                withContext(Dispatchers.Main) {
                    isLoading = false
                    if (response.isSuccessful) {
                        Toast.makeText(context, "인증번호가 재발송되었습니다", Toast.LENGTH_SHORT).show()
                        timer = 300  // 타이머 리셋
                        canResend = false
                        code = ""  // 입력 필드 초기화
                        errorMsg = ""
                    } else {
                        errorMsg = "재발송 실패. 잠시 후 다시 시도해주세요"
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    isLoading = false
                    errorMsg = "네트워크 오류가 발생했습니다"
                }
            }
        }
    }

    BasicAlertDialog(onDismissRequest = { /* 백버튼으로 닫기 방지 */ }) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "전화번호 인증",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = redMain
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(
                            Icons.Default.Close,
                            contentDescription = "닫기",
                            tint = Color.Gray
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // 전화번호 표시
                Text(
                    "인증번호를 발송했습니다",
                    fontSize = 14.sp,
                    color = Color.Gray
                )
                Text(
                    formatPhoneNumber(phoneNumber),
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Spacer(modifier = Modifier.height(20.dp))

                // 타이머 표시
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = if (timer < 60) Color(0xFFFFEBEE) else Color(0xFFF5F5F5)
                    )
                ) {
                    Text(
                        text = "${timer / 60}:${String.format("%02d", timer % 60)}",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (timer < 60) Color.Red else redMain,
                        modifier = Modifier.padding(vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 인증번호 입력 필드
                OutlinedTextField(
                    value = code,
                    onValueChange = {
                        if (it.all { char -> char.isDigit() } && it.length <= 6) {
                            code = it
                            errorMsg = ""  // 입력 시 에러 메시지 초기화
                        }
                    },
                    label = { Text("인증번호 6자리") },
                    placeholder = { Text("") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    keyboardActions = KeyboardActions(
                        onDone = { verifyCode() }
                    ),
                    singleLine = true,
                    isError = errorMsg.isNotEmpty(),
                    modifier = Modifier.fillMaxWidth(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain
                    )
                )

                // 에러 메시지
                if (errorMsg.isNotEmpty()) {
                    Text(
                        text = errorMsg,
                        color = Color.Red,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // 버튼들
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    // 재전송 버튼
                    OutlinedButton(
                        onClick = { resendCode() },
                        enabled = canResend && !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = redMain
                        ),
                        border = BorderStroke(1.dp, if (canResend) redMain else Color.Gray)
                    ) {
                        Text(
                            text = if (canResend) "재전송" else "재전송 대기",
                            fontSize = 14.sp
                        )
                    }

                    // 확인 버튼
                    Button(
                        onClick = { verifyCode() },
                        enabled = code.length == 6 && !isLoading,
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = redMain,
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("확인", fontSize = 14.sp)
                        }
                    }
                }

                // 안내 텍스트
                Text(
                    text = "인증번호가 오지 않나요? 스팸함을 확인해주세요.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(top = 12.dp)
                )
            }
        }
    }
}

private fun formatPhoneNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when (digits.length) {
        10 -> "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
        11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
        else -> phone
    }
}