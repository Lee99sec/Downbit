package com.example.myapplication

import SecureBaseActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VpnKey
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.gson.annotations.SerializedName
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import com.example.myapplication.security.E2EEncryptionUtils
// API Request/Response 모델
data class FindUsernameRequest(
    @SerializedName("name") val name: String,
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class FindUsernameResponse(
    @SerializedName("username") val username: String
)

data class FindPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("phoneNumber") val phoneNumber: String
)

data class ResetPasswordRequest(
    @SerializedName("email") val email: String,
    @SerializedName("newPassword") val newPassword: String
)

data class SmsRequestFind(
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("purpose") val purpose: String? = null
)

data class SmsVerifyRequestFind(
    @SerializedName("phoneNumber") val phoneNumber: String,
    @SerializedName("verificationCode") val verificationCode: String
)

// 전화번호 시각 변환 클래스 (이 파일 전용)
class PhoneVisualTransformationFind : VisualTransformation {
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

// 전화번호 포맷팅 함수 - 이 파일에서만 사용하는 독립적인 함수
private fun formatPhoneNumber(phone: String): String {
    val digits = phone.filter { it.isDigit() }
    return when (digits.length) {
        10 -> "${digits.substring(0, 3)}-${digits.substring(3, 6)}-${digits.substring(6)}"
        11 -> "${digits.substring(0, 3)}-${digits.substring(3, 7)}-${digits.substring(7)}"
        else -> phone
    }
}

// 비밀번호 유효성 검사 - 이 파일에서만 사용하는 독립적인 함수
private fun isValidPasswordFind(pwd: String): Boolean {
    if (pwd.length < 8) return false
    val hasLetter = pwd.any { it.isLetter() }
    val hasDigit = pwd.any { it.isDigit() }
    val hasSpecial = pwd.any { !it.isLetterOrDigit() }
    return hasLetter && hasDigit && hasSpecial
}

private fun calculatePasswordStrength(pwd: String): Int {
    var strength = 0
    if (pwd.length >= 8) strength++
    if (pwd.length >= 12) strength++
    if (pwd.any { it.isLetter() }) strength++
    if (pwd.any { it.isDigit() }) strength++
    if (pwd.any { !it.isLetterOrDigit() }) strength++
    if (pwd.any { it.isUpperCase() } && pwd.any { it.isLowerCase() }) strength++
    return minOf(strength, 4)
}

class FindAccountActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FindAccountScreen()
                }
            }
        }
    }
}

@Composable
fun FindAccountScreen() {
    val redMain = Color(0xFFD32F2F)
    val context = LocalContext.current

    var showFindIdDialog by remember { mutableStateOf(false) }
    var showFindPasswordDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 헤더
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 48.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { (context as? ComponentActivity)?.finish() }
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = redMain
                )
            }
            Text(
                "ID / PW 찾기",
                fontSize = 24.sp,
                fontWeight = FontWeight.Bold,
                color = redMain,
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.width(48.dp))
        }

        // 설명 텍스트
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 32.dp),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    Icons.Default.Lock,
                    contentDescription = "보안",
                    tint = redMain,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text(
                    "계정 정보를 잊으셨나요?",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333),
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    "회원가입 시 등록한 정보로\n아이디와 비밀번호를 찾을 수 있습니다",
                    fontSize = 14.sp,
                    color = Color(0xFF666666),
                    textAlign = TextAlign.Center,
                    lineHeight = 20.sp,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        // ID 찾기 버튼
        Button(
            onClick = { showFindIdDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(containerColor = redMain),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.AccountCircle,
                    contentDescription = "ID 찾기",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "아이디 찾기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 비밀번호 찾기 버튼
        OutlinedButton(
            onClick = { showFindPasswordDialog = true },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = redMain
            ),
            border = BorderStroke(2.dp, redMain),
            shape = RoundedCornerShape(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.Center
            ) {
                Icon(
                    Icons.Default.VpnKey,
                    contentDescription = "비밀번호 찾기",
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Text(
                    "비밀번호 찾기",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        // 추가 안내
        Spacer(modifier = Modifier.height(32.dp))
        Text(
            "",
            fontSize = 12.sp,
            color = Color(0xFF999999)
        )
    }

    // ID 찾기 다이얼로그
    if (showFindIdDialog) {
        FindIdDialog(
            onDismiss = { showFindIdDialog = false },
            onPasswordFind = {
                showFindIdDialog = false
                showFindPasswordDialog = true
            },
            redMain = redMain
        )
    }

    // 비밀번호 찾기 다이얼로그
    if (showFindPasswordDialog) {
        FindPasswordDialog(
            onDismiss = { showFindPasswordDialog = false },
            redMain = redMain
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun FindIdDialog(
    onDismiss: () -> Unit,
    onPasswordFind: () -> Unit,  // 이 줄 추가
    redMain: Color
){
    val context = LocalContext.current
    var name by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var isPhoneVerified by remember { mutableStateOf(false) }
    var foundEmail by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "아이디 찾기",
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

                Spacer(modifier = Modifier.height(20.dp))

                if (foundEmail.isEmpty()) {
                    // 정보 입력 단계
                    Text(
                        "회원가입 시 등록한 정보를 입력해주세요",
                        fontSize = 14.sp,
                        color = Color(0xFF666666),
                        modifier = Modifier.padding(bottom = 20.dp)
                    )

                    // 이름 입력
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("이름") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = redMain,
                            cursorColor = redMain
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // 전화번호 입력
                    OutlinedTextField(
                        value = phoneNumber,
                        onValueChange = {
                            phoneNumber = it.filter { c -> c.isDigit() }.take(11)
                        },
                        label = { Text("전화번호") },
                        placeholder = { Text("") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                        visualTransformation = PhoneVisualTransformationFind(),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = redMain,
                            cursorColor = redMain
                        ),
                        trailingIcon = {
                            if (isPhoneVerified) {
                                Icon(
                                    Icons.Default.CheckCircle,
                                    contentDescription = "인증완료",
                                    tint = Color(0xFF4CAF50)
                                )
                            }
                        }
                    )

                    // 인증 버튼
                    if (!isPhoneVerified) {
                        Button(
                            onClick = {
                                if (phoneNumber.length >= 10) {
                                    isLoading = true
                                    CoroutineScope(Dispatchers.IO).launch {
                                        val success = sendSmsForFindId(formatPhoneNumber(phoneNumber))
                                        withContext(Dispatchers.Main) {
                                            if (success) {
                                                showVerificationDialog = true
                                                errorMessage = ""
                                            } else {
                                                errorMessage = "인증번호 발송에 실패했습니다"
                                            }
                                            isLoading = false
                                        }
                                    }
                                } else {
                                    errorMessage = "올바른 전화번호를 입력해주세요"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = redMain.copy(alpha = 0.8f)
                            ),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("인증번호 발송", fontSize = 14.sp)
                        }
                    }

                    if (errorMessage.isNotEmpty()) {
                        Text(
                            errorMessage,
                            color = Color.Red,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 찾기 버튼
                    Button(
                        onClick = {
                            if (name.isNotEmpty() && isPhoneVerified) {
                                isLoading = true
                                errorMessage = ""

                                // CoroutineScope 부분을 이렇게 교체
                                CoroutineScope(Dispatchers.IO).launch {
                                    try {
                                        Log.d("FindAccount", "=== E2E 암호화 ID 찾기 시작 ===")
                                        Log.d("FindAccount", "이름: '$name'")
                                        Log.d("FindAccount", "전화번호: '${formatPhoneNumber(phoneNumber)}'")

                                        // E2E 암호화 적용
                                        val encryptedData = E2EEncryptionUtils.encryptData(
                                            "name" to name,
                                            "phoneNumber" to formatPhoneNumber(phoneNumber)
                                        )

                                        Log.d("FindAccount", "암호화 완료")
                                        Log.d("FindAccount", "암호화된 데이터: ${encryptedData}")
                                        Log.d("FindAccount", "암호화된 데이터 길이: ${encryptedData.length}")

                                        // 암호화된 요청 생성
                                        val encryptedRequest = mapOf("e2edata" to encryptedData)
                                        // 백엔드 API 호출 (암호화된 요청)
                                        val response = RetrofitClient.apiService.findId(encryptedRequest)

                                        // UI 스레드에서 결과 처리
                                        withContext(Dispatchers.Main) {
                                            if (response.isSuccessful) {
                                                foundEmail = response.body()?.username ?: ""
                                                if (foundEmail.isEmpty()) {
                                                    errorMessage = "아이디를 찾을 수 없습니다"
                                                }
                                            } else {
                                                errorMessage = when (response.code()) {
                                                    401 -> "본인 인증이 필요합니다"
                                                    404 -> "해당 정보로 가입된 회원이 없습니다"
                                                    else -> "오류가 발생했습니다 (${response.code()})"
                                                }
                                            }
                                            isLoading = false
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            errorMessage = "네트워크 연결을 확인해주세요"
                                            isLoading = false
                                            Log.e("FindAccount", "Error finding ID: ${e.message}", e)
                                        }
                                    }
                                }
                            } else {
                                errorMessage = when {
                                    name.isEmpty() -> "이름을 입력해주세요"
                                    !isPhoneVerified -> "전화번호 인증을 완료해주세요"
                                    else -> "모든 정보를 입력하고 인증을 완료해주세요"
                                }
                            }
                        },
                        // 아래 modifier, enabled, colors 등은 그대로 유지
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        enabled = name.isNotEmpty() && isPhoneVerified && !isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = redMain,
                            disabledContainerColor = Color.Gray
                        )
                    ) {
                        // 아래 내용도 그대로 유지
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                        } else {
                            Text("아이디 찾기", fontWeight = FontWeight.Bold)
                        }
                    }
                } else {
                    // 결과 표시 단계
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(
                            Icons.Default.CheckCircle,
                            contentDescription = "성공",
                            tint = Color(0xFF4CAF50),
                            modifier = Modifier.size(64.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            "아이디를 찾았습니다!",
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Medium
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Card(
                            colors = CardDefaults.cardColors(
                                containerColor = Color(0xFFF5F5F5)
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                foundEmail,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                                color = redMain,
                                modifier = Modifier.padding(16.dp),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedButton(
                                onClick = onPasswordFind,  // onDismiss 대신 onPasswordFind로 변경
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = redMain
                                ),
                                border = BorderStroke(1.dp, redMain)
                            ) {
                                Text("비밀번호 찾기")  // "닫기" 대신 "비밀번호 찾기"로 변경
                            }

                            Button(
                                onClick = {
                                    (context as? ComponentActivity)?.finish()
                                },
                                modifier = Modifier.weight(1f),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = redMain
                                )
                            ) {
                                Text("로그인하기")
                            }
                        }
                    }
                }
            }
        }
    }

    // 전화번호 인증 다이얼로그
    if (showVerificationDialog) {
        PhoneVerificationDialogfind(
            phoneNumber = phoneNumber,
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
fun FindPasswordDialog(
    onDismiss: () -> Unit,
    redMain: Color
) {
    val context = LocalContext.current
    var email by remember { mutableStateOf("") }
    var phoneNumber by remember { mutableStateOf("") }
    var showVerificationDialog by remember { mutableStateOf(false) }
    var isPhoneVerified by remember { mutableStateOf(false) }
    var showResetPassword by remember { mutableStateOf(false) }
    var newPassword by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var passwordVisible by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }
    var resetSuccess by remember { mutableStateOf(false) }

    BasicAlertDialog(
        onDismissRequest = onDismiss
    ) {
        Card(
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.White),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(24.dp)
            ) {
                // 헤더
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        "비밀번호 찾기",
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

                Spacer(modifier = Modifier.height(20.dp))

                when {
                    resetSuccess -> {
                        // 비밀번호 재설정 성공
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                Icons.Default.CheckCircle,
                                contentDescription = "성공",
                                tint = Color(0xFF4CAF50),
                                modifier = Modifier.size(64.dp)
                            )

                            Spacer(modifier = Modifier.height(16.dp))

                            Text(
                                "비밀번호가 변경되었습니다!",
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Medium
                            )

                            Spacer(modifier = Modifier.height(8.dp))

                            Text(
                                "새로운 비밀번호로 로그인해주세요",
                                fontSize = 14.sp,
                                color = Color(0xFF666666)
                            )

                            Spacer(modifier = Modifier.height(24.dp))

                            Button(
                                onClick = {
                                    (context as? ComponentActivity)?.finish()
                                },
                                modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = redMain
                                )
                            ) {
                                Text("로그인하러 가기")
                            }
                        }
                    }

                    showResetPassword -> {
                        // 비밀번호 강도 계산
                        val passwordStrengthLevel = calculatePasswordStrength(newPassword)
                        val passwordStrengthText = when (passwordStrengthLevel) {
                            0 -> if (newPassword.isEmpty()) "" else "매우 약함"
                            1 -> "약함"
                            2 -> "보통"
                            3 -> "강함"
                            4 -> "매우 강함"
                            else -> ""
                        }
                        val passwordStrengthColor = when (passwordStrengthLevel) {
                            0 -> Color(0xFFD32F2F)
                            1 -> Color(0xFFFF6B35)
                            2 -> Color(0xFFFFA726)
                            3 -> Color(0xFF66BB6A)
                            4 -> Color(0xFF2E7D32)
                            else -> Color.Gray
                        }

                        val passwordsMatch = newPassword == confirmPassword && newPassword.isNotEmpty()

                        Text(
                            "새로운 비밀번호를 설정해주세요",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // 비밀번호 입력 with 강도 표시
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = newPassword,
                                onValueChange = { newPassword = it },
                                label = { Text("새 비밀번호", color = redMain) },
                                placeholder = { Text("8자 이상, 문자/숫자/특수문자 포함", color = Color.Gray) },
                                visualTransformation = if (passwordVisible)
                                    VisualTransformation.None else PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = redMain,
                                    cursorColor = redMain
                                ),
                                trailingIcon = {
                                    IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                        Icon(
                                            imageVector = if (passwordVisible)
                                                Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                            contentDescription = if (passwordVisible) "숨기기" else "보기",
                                            tint = redMain
                                        )
                                    }
                                }
                            )

                            if (newPassword.isNotEmpty()) {
                                // 강도 표시 바
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

                                        // 체크리스트
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            val hasLength = newPassword.length >= 8
                                            val hasLetter = newPassword.any { it.isLetter() }
                                            val hasDigit = newPassword.any { it.isDigit() }
                                            val hasSpecial = newPassword.any { !it.isLetterOrDigit() }

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

                        // 비밀번호 확인
                        Column(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = confirmPassword,
                                onValueChange = { confirmPassword = it },
                                label = { Text("비밀번호 확인", color = redMain) },
                                placeholder = { Text("비밀번호를 다시 입력하세요", color = Color.Gray) },
                                visualTransformation = PasswordVisualTransformation(),
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = if (confirmPassword.isNotEmpty() && passwordsMatch)
                                        Color(0xFF2E7D32) else redMain,
                                    unfocusedBorderColor = if (confirmPassword.isNotEmpty() && !passwordsMatch)
                                        Color(0xFFE65100) else Color.Gray,
                                    cursorColor = redMain
                                )
                            )

                            if (confirmPassword.isNotEmpty()) {
                                Text(
                                    if (passwordsMatch) "✓ 비밀번호가 일치합니다" else "비밀번호가 일치하지 않습니다",
                                    fontSize = 12.sp,
                                    color = if (passwordsMatch) Color(0xFF2E7D32) else Color(0xFFE65100),
                                    modifier = Modifier.padding(start = 16.dp, top = 4.dp)
                                )
                            }
                        }

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                errorMessage,
                                color = Color.Red,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Button(
                            onClick = {
                                if (isValidPasswordFind(newPassword) && passwordsMatch) {
                                    isLoading = true
                                    errorMessage = ""

                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            Log.d("FindAccount", "=== E2E 암호화 비밀번호 재설정 시작 ===")
                                            Log.d("FindAccount", "이메일: '$email'")
                                            Log.d("FindAccount", "새 비밀번호 길이: ${newPassword.length}")

                                            // E2E 암호화 적용
                                            val encryptedData = E2EEncryptionUtils.encryptData(
                                                "email" to email,
                                                "newPassword" to newPassword
                                            )

                                            Log.d("FindAccount", "암호화 완료")
                                            Log.d("FindAccount", "암호화된 데이터: ${encryptedData}")
                                            Log.d("FindAccount", "암호화된 데이터 길이: ${encryptedData.length}")

                                            // 암호화된 요청 생성
                                            val encryptedRequest = mapOf("e2edata" to encryptedData)

                                            // 실제 백엔드 API 호출 (암호화된 요청)
                                            val response = RetrofitClient.apiService.resetPassword(encryptedRequest)

                                            Log.d("FindAccount", "비밀번호 재설정 응답 코드: ${response.code()}")

                                            withContext(Dispatchers.Main) {
                                                if (response.isSuccessful) {
                                                    Log.d("FindAccount", "비밀번호 재설정 성공!")
                                                    resetSuccess = true
                                                } else {
                                                    errorMessage = when (response.code()) {
                                                        400 -> "잘못된 요청입니다"
                                                        401 -> "인증이 필요합니다"
                                                        404 -> "사용자를 찾을 수 없습니다"
                                                        else -> "비밀번호 변경 실패 (${response.code()})"
                                                    }
                                                    Log.e("FindAccount", "비밀번호 재설정 실패: $errorMessage")
                                                }
                                                isLoading = false
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FindAccount", "비밀번호 재설정 예외: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                errorMessage = "네트워크 오류가 발생했습니다"
                                                isLoading = false
                                            }
                                        }
                                    }
                                } else {
                                    errorMessage = when {
                                        !isValidPasswordFind(newPassword) -> "비밀번호는 8자 이상, 문자/숫자/특수문자를 포함해야 합니다"
                                        !passwordsMatch -> "비밀번호가 일치하지 않습니다"
                                        else -> "비밀번호를 확인해주세요"
                                    }
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = newPassword.isNotEmpty() && confirmPassword.isNotEmpty() && !isLoading,
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
                                Text("비밀번호 변경", fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    else -> {
                        // 정보 입력 단계
                        Text(
                            "가입 시 등록한 정보를 입력해주세요",
                            fontSize = 14.sp,
                            color = Color(0xFF666666),
                            modifier = Modifier.padding(bottom = 20.dp)
                        )

                        // 이메일 입력
                        OutlinedTextField(
                            value = email,
                            onValueChange = { email = it },
                            label = { Text("이메일 (아이디)") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = redMain,
                                cursorColor = redMain
                            )
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        // 전화번호 입력
                        OutlinedTextField(
                            value = phoneNumber,
                            onValueChange = {
                                phoneNumber = it.filter { c -> c.isDigit() }.take(11)
                            },
                            label = { Text("전화번호") },
                            placeholder = { Text("") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                            visualTransformation = PhoneVisualTransformationFind(),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = redMain,
                                cursorColor = redMain
                            ),
                            trailingIcon = {
                                if (isPhoneVerified) {
                                    Icon(
                                        Icons.Default.CheckCircle,
                                        contentDescription = "인증완료",
                                        tint = Color(0xFF4CAF50)
                                    )
                                }
                            }
                        )

                        // 인증 버튼
                        if (!isPhoneVerified) {
                            Button(
                                onClick = {
                                    if (phoneNumber.length >= 10) {
                                        isLoading = true
                                        CoroutineScope(Dispatchers.IO).launch {
                                            val success = sendSmsForPasswordReset(formatPhoneNumber(phoneNumber))
                                            withContext(Dispatchers.Main) {
                                                if (success) {
                                                    showVerificationDialog = true
                                                    errorMessage = ""
                                                } else {
                                                    errorMessage = "인증번호 발송에 실패했습니다"
                                                }
                                                isLoading = false
                                            }
                                        }
                                    } else {
                                        errorMessage = "올바른 전화번호를 입력해주세요"
                                    }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 12.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = redMain.copy(alpha = 0.8f)
                                ),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("인증번호 발송", fontSize = 14.sp)
                            }
                        }

                        if (errorMessage.isNotEmpty()) {
                            Text(
                                errorMessage,
                                color = Color.Red,
                                fontSize = 12.sp,
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // 다음 버튼
                        Button(
                            onClick = {
                                if (email.isNotEmpty() && isPhoneVerified) {
                                    isLoading = true
                                    errorMessage = ""

                                    CoroutineScope(Dispatchers.IO).launch {
                                        try {
                                            Log.d("FindAccount", "=== E2E 암호화 본인확인 시작 ===")
                                            Log.d("FindAccount", "이메일: '$email'")
                                            Log.d("FindAccount", "전화번호: '${formatPhoneNumber(phoneNumber)}'")

                                            // E2E 암호화 적용
                                            val encryptedData = E2EEncryptionUtils.encryptData(
                                                "email" to email,
                                                "phoneNumber" to formatPhoneNumber(phoneNumber)
                                            )

                                            Log.d("FindAccount", "암호화 완료")
                                            Log.d("FindAccount", "암호화된 데이터: ${encryptedData}")
                                            Log.d("FindAccount", "암호화된 데이터 길이: ${encryptedData.length}")

                                            // 암호화된 요청 생성
                                            val encryptedRequest = mapOf("e2edata" to encryptedData)

                                            // 백엔드에 본인 확인 요청 (암호화된 요청)
                                            val response = RetrofitClient.apiService.findPassword(encryptedRequest)

                                            Log.d("FindAccount", "본인확인 응답 코드: ${response.code()}")

                                            withContext(Dispatchers.Main) {
                                                if (response.isSuccessful) {
                                                    Log.d("FindAccount", "본인확인 성공 - 비밀번호 재설정 화면으로 이동")
                                                    showResetPassword = true
                                                } else {
                                                    errorMessage = when (response.code()) {
                                                        401 -> "본인 인증에 실패했습니다"
                                                        404 -> "해당 정보로 가입된 회원이 없습니다"
                                                        else -> "본인 확인 실패 (${response.code()})"
                                                    }
                                                    Log.e("FindAccount", "본인확인 실패: $errorMessage")
                                                }
                                                isLoading = false
                                            }
                                        } catch (e: Exception) {
                                            Log.e("FindAccount", "본인확인 예외: ${e.message}", e)
                                            withContext(Dispatchers.Main) {
                                                errorMessage = "네트워크 연결을 확인해주세요"
                                                isLoading = false
                                            }
                                        }
                                    }
                                } else {
                                    errorMessage = "모든 정보를 입력하고 인증을 완료해주세요"
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(48.dp),
                            enabled = email.isNotEmpty() && isPhoneVerified && !isLoading,
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
                                Text("다음", fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }

    // 전화번호 인증 다이얼로그
    if (showVerificationDialog) {
        PhoneVerificationDialogfind(
            phoneNumber = phoneNumber,
            onVerified = {
                isPhoneVerified = true
                showVerificationDialog = false
            },
            onDismiss = { showVerificationDialog = false },
            redMain = redMain
        )
    }
}

// SMS 발송 함수들
private suspend fun sendSmsForFindId(phoneNumber: String): Boolean {
    return try {
        Log.d("FindAccount", "=== SMS 발송 요청 (ID찾기) ===")
        Log.d("FindAccount", "전화번호 파라미터: $phoneNumber")
        Log.d("FindAccount", "purpose: find-id")

        val request = SmsRequestFind(phoneNumber = phoneNumber, purpose = "find-id")
        Log.d("FindAccount", "요청 객체: $request")

        val response = RetrofitClient.apiService.sendSmsVerificationFind(request)

        Log.d("FindAccount", "SMS 발송 응답 코드: ${response.code()}")
        Log.d("FindAccount", "SMS 발송 성공 여부: ${response.isSuccessful}")

        if (!response.isSuccessful) {
            Log.e("FindAccount", "SMS 발송 실패 에러: ${response.errorBody()?.string()}")
        }

        response.isSuccessful
    } catch (e: Exception) {
        Log.e("FindAccount", "SMS 발송 예외: ${e.message}", e)
        false
    }
}

private suspend fun sendSmsForPasswordReset(phoneNumber: String): Boolean {
    return try {
        val response = RetrofitClient.apiService.sendSmsVerificationFind(
            SmsRequestFind(phoneNumber = phoneNumber, purpose = "find-password")
        )
        response.isSuccessful
    } catch (e: Exception) {
        Log.e("FindAccount", "SMS 발송 실패: ${e.message}")
        false
    }
}

// 전화번호 인증 다이얼로그
@Composable
fun PhoneVerificationDialogfind(
    phoneNumber: String,
    onVerified: () -> Unit,
    onDismiss: () -> Unit,
    redMain: Color
) {
    var verificationCode by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    if (verificationCode.length == 6) {
                        isLoading = true
                        CoroutineScope(Dispatchers.IO).launch {
                            try {
                                // 로그 추가
                                Log.d("FindAccount", "=== 인증 확인 요청 ===")
                                Log.d("FindAccount", "원본 전화번호: $phoneNumber")

                                val phoneNumberDigits = formatPhoneNumber(phoneNumber)
                                Log.d("FindAccount", "숫자만 추출: $phoneNumberDigits")

                                val formattedPhone = formatPhoneNumber(phoneNumber)
                                Log.d("FindAccount", "포맷된 전화번호: $formattedPhone")

                                Log.d("FindAccount", "입력한 인증코드: $verificationCode")

                                // 실제 요청
                                val requestBody = SmsVerifyRequestFind(
                                    phoneNumber = phoneNumberDigits,  // 여기를 변경해서 테스트
                                    verificationCode = verificationCode
                                )
                                Log.d("FindAccount", "요청 객체: $requestBody")

                                val response = RetrofitClient.apiService.verifySmsCodeFind(requestBody)

                                Log.d("FindAccount", "=== 인증 확인 응답 ===")
                                Log.d("FindAccount", "응답 코드: ${response.code()}")
                                Log.d("FindAccount", "성공 여부: ${response.isSuccessful}")

                                if (!response.isSuccessful) {
                                    val errorBody = response.errorBody()?.string()
                                    Log.e("FindAccount", "에러 내용: $errorBody")
                                }

                                withContext(Dispatchers.Main) {
                                    if (response.isSuccessful) {
                                        Log.d("FindAccount", "인증 성공!")
                                        onVerified()
                                    } else {
                                        errorMessage = when(response.code()) {
                                            400 -> "잘못된 요청입니다"
                                            401 -> "인증번호가 올바르지 않습니다"
                                            404 -> "인증 요청을 찾을 수 없습니다"
                                            else -> "인증 실패 (${response.code()})"
                                        }
                                        Log.e("FindAccount", "인증 실패: $errorMessage")
                                    }
                                    isLoading = false
                                }
                            } catch (e: Exception) {
                                Log.e("FindAccount", "인증 예외 발생: ${e.message}", e)
                                e.printStackTrace()

                                withContext(Dispatchers.Main) {
                                    errorMessage = "인증 실패: ${e.message}"
                                    isLoading = false
                                }
                            }
                        }
                    } else {
                        errorMessage = "6자리 인증번호를 입력해주세요"
                    }
                },
                colors = ButtonDefaults.textButtonColors(contentColor = redMain)
            ) {
                Text("확인")

            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                colors = ButtonDefaults.textButtonColors(contentColor = Color.Gray)
            ) {
                Text("취소")
            }
        },
        title = {
            Text("전화번호 인증", fontSize = 18.sp, fontWeight = FontWeight.Bold)
        },
        text = {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(formatPhoneNumber(phoneNumber), fontSize = 14.sp)
                Spacer(modifier = Modifier.height(16.dp))

                // 인증번호 입력 필드 추가
                // 인증번호 입력 필드 수정
                OutlinedTextField(
                    value = verificationCode,
                    onValueChange = {
                        if (it.length <= 6 && it.all { char -> char.isDigit() }) {
                            verificationCode = it
                            // 입력하면 에러 초기화
                            if (errorMessage.isNotEmpty()) {
                                errorMessage = ""
                            }
                        }
                    },
                    label = { Text("인증번호") },
                    placeholder = { Text("") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    isError = errorMessage.isNotEmpty(),  // 에러 상태
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (errorMessage.isNotEmpty())
                            Color(0xFFD32F2F)  // 에러 시 빨간색
                        else
                            redMain,  // 정상 시 기본 색상
                        unfocusedBorderColor = if (errorMessage.isNotEmpty())
                            Color(0xFFD32F2F)  // 에러 시 빨간색
                        else
                            Color.Gray,
                        errorBorderColor = Color(0xFFD32F2F),  // 에러 테두리 색상
                        errorLabelColor = Color(0xFFD32F2F),   // 에러 라벨 색상
                        cursorColor = redMain
                    )
                )

                if (errorMessage.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        errorMessage,
                        color = Color.Red,
                        fontSize = 12.sp
                    )
                }
            }
        },
        containerColor = Color.White,
        shape = RoundedCornerShape(16.dp)
    )
}