package com.example.myapplication

import AuthTokenManager
import SecureBaseActivity
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject

class PasswordChangeActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    PasswordChangeScreen()
                }
            }
        }
    }
}

@Composable
fun PasswordChangeScreen(context: Context = LocalContext.current) {
    val redMain = Color(0xFFD32F2F)
    val scrollState = rememberScrollState()

    var currentPassword by remember { mutableStateOf("") }
    var newPassword by remember { mutableStateOf("") }
    var newPasswordConfirm by remember { mutableStateOf("") }
    var showCurrentPassword by remember { mutableStateOf(false) }
    var showNewPassword by remember { mutableStateOf(false) }
    var showNewPasswordConfirm by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }

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
        0 -> Color(0xFFD32F2F)  // 빨강
        1 -> Color(0xFFFF6B35)  // 주황
        2 -> Color(0xFFFFA726)  // 노랑
        3 -> Color(0xFF66BB6A)  // 연두
        4 -> Color(0xFF2E7D32)  // 초록
        else -> Color.Gray
    }

    // 비밀번호 일치 여부 확인
    val passwordsMatch = newPassword == newPasswordConfirm && newPassword.isNotEmpty()
    val passwordMatchMessage = when {
        newPasswordConfirm.isEmpty() -> ""
        newPassword == newPasswordConfirm -> "✓ 비밀번호가 일치합니다"
        else -> "비밀번호가 일치하지 않습니다"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 상단 헤더 (고정)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "비밀번호 변경",
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                color = redMain
            )

            IconButton(
                onClick = {
                    if (context is ComponentActivity) {
                        context.finish()
                    }
                }
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = redMain
                )
            }
        }

        // 스크롤 가능한 컨텐츠
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .verticalScroll(scrollState)
                .padding(horizontal = 16.dp)
        ) {
            Spacer(modifier = Modifier.height(24.dp))

            // 현재 비밀번호
            Text(
                "현재 비밀번호",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            OutlinedTextField(
                value = currentPassword,
                onValueChange = { currentPassword = it },
                modifier = Modifier.fillMaxWidth(),
                visualTransformation = if (showCurrentPassword) VisualTransformation.None else PasswordVisualTransformation(),
                trailingIcon = {
                    IconButton(onClick = { showCurrentPassword = !showCurrentPassword }) {
                        Icon(
                            if (showCurrentPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = "비밀번호 표시/숨김",
                            tint = redMain
                        )
                    }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
            )

            Spacer(modifier = Modifier.height(24.dp))

            // 새 비밀번호
            Text(
                "새 비밀번호",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newPassword,
                    onValueChange = { newPassword = it },
                    placeholder = { Text("8자 이상, 문자/숫자/특수문자 포함", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNewPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPassword = !showNewPassword }) {
                            Icon(
                                if (showNewPassword) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "비밀번호 표시/숨김",
                                tint = redMain
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                if (newPassword.isNotEmpty()) {
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

            Spacer(modifier = Modifier.height(24.dp))

            // 새 비밀번호 확인
            Text(
                "새 비밀번호 확인",
                fontSize = 14.sp,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            Column(modifier = Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = newPasswordConfirm,
                    onValueChange = { newPasswordConfirm = it },
                    placeholder = { Text("새 비밀번호를 다시 입력하세요", color = Color.Gray) },
                    modifier = Modifier.fillMaxWidth(),
                    visualTransformation = if (showNewPasswordConfirm) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showNewPasswordConfirm = !showNewPasswordConfirm }) {
                            Icon(
                                if (showNewPasswordConfirm) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                contentDescription = "비밀번호 표시/숨김",
                                tint = redMain
                            )
                        }
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = if (newPasswordConfirm.isNotEmpty() && passwordsMatch) Color(0xFF2E7D32) else redMain,
                        unfocusedBorderColor = if (newPasswordConfirm.isNotEmpty() && !passwordsMatch) Color(0xFFE65100) else Color.Gray,
                        cursorColor = redMain
                    ),
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password)
                )

                if (newPasswordConfirm.isNotEmpty()) {
                    Text(
                        passwordMatchMessage,
                        fontSize = 12.sp,
                        color = if (passwordsMatch) Color(0xFF2E7D32) else Color(0xFFE65100),
                        modifier = Modifier.padding(start = 4.dp, top = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // 안내 텍스트
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5)),
                shape = RoundedCornerShape(8.dp)
            ) {
                Column(
                    modifier = Modifier.padding(16.dp)
                ) {
                    Text(
                        "📋 비밀번호 변경 안내",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = redMain,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )
                    Text(
                        "• 반드시 본인의 현재 비밀번호를 입력해주세요",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "• 새 비밀번호는 8자 이상, 문자/숫자/특수문자를 포함해야 합니다",
                        fontSize = 12.sp,
                        color = Color.Gray,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        "• 계정 보안을 위해 정기적으로 비밀번호를 변경해주세요",
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }

        // 하단 버튼 영역 (고정)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(Color.White)
                .padding(16.dp)
        ) {
            // 변경 버튼
            Button(
                onClick = {
                    when {
                        currentPassword.isEmpty() -> {
                            Toast.makeText(context, "현재 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                        }
                        newPassword.isEmpty() -> {
                            Toast.makeText(context, "새 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                        }
                        !isValidPassword(newPassword) -> {
                            Toast.makeText(
                                context,
                                "새 비밀번호는 8자 이상, 문자/숫자/특수문자를 포함해야 합니다",
                                Toast.LENGTH_LONG
                            ).show()
                        }
                        newPasswordConfirm.isEmpty() -> {
                            Toast.makeText(context, "새 비밀번호 확인을 입력해주세요", Toast.LENGTH_SHORT).show()
                        }
                        !passwordsMatch -> {
                            Toast.makeText(context, "새 비밀번호가 일치하지 않습니다", Toast.LENGTH_SHORT).show()
                        }
                        currentPassword == newPassword -> {
                            Toast.makeText(context, "현재 비밀번호와 새 비밀번호가 같습니다", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            isLoading = true
                            changePassword(
                                currentPassword = currentPassword,
                                newPassword = newPassword,
                                context = context,
                                onComplete = { isLoading = false }
                            )
                        }
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = redMain,
                    disabledContainerColor = Color.Gray
                ),
                enabled = !isLoading
            ) {
                if (isLoading) {
                    CircularProgressIndicator(
                        color = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        "비밀번호 변경",
                        color = Color.White,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

// AuthTokenManager를 사용한 비밀번호 변경 함수
private fun changePassword(
    currentPassword: String,
    newPassword: String,
    context: Context,
    onComplete: () -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            // AuthTokenManager 인스턴스 생성
            val authTokenManager = AuthTokenManager(context)

            // 로그인 여부 확인
            if (!authTokenManager.isLoggedIn()) {
                Toast.makeText(context, "로그인이 필요합니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                onComplete()
                return@launch
            }

            // 비밀번호 변경 요청 JSON 생성
            val requestJson = JSONObject().apply {
                put("curPassword", currentPassword)
                put("newPassword", newPassword)
            }

            Log.d("PasswordChange", "비밀번호 변경 요청 시작")

            // AuthTokenManager를 사용하여 API 호출 (토큰은 자동으로 추가됨)
            val result = authTokenManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.BASE_URL}/mypage/edit/password",
                method = "PATCH",
                requestBody = requestJson.toString()
            )

            // 결과 처리
            result.fold(
                onSuccess = { responseData ->
                    Log.d("PasswordChange", "비밀번호 변경 성공: $responseData")
                    Toast.makeText(context, "비밀번호가 성공적으로 변경되었습니다.", Toast.LENGTH_SHORT).show()

                    // 보안을 위해 로그아웃 처리
                    authTokenManager.logout()

                    // 자동 로그인 설정 해제
                    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit().apply {
                        putBoolean("auto_login_enabled", false)
                        apply()
                    }

                    // 로그인 페이지로 이동
                    val intent = android.content.Intent(context, LoginPage::class.java)
                    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)

                    Toast.makeText(context, "보안을 위해 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                    onComplete()
                },
                onFailure = { exception ->
                    Log.e("PasswordChange", "비밀번호 변경 실패: ${exception.message}")

                    // 에러 메시지 파싱 및 표시
                    val errorMessage = when {
                        exception.message?.contains("403") == true ||
                                exception.message?.contains("권한") == true -> {
                            "현재 비밀번호가 올바르지 않습니다."
                        }
                        exception.message?.contains("401") == true ||
                                exception.message?.contains("세션") == true -> {
                            "세션이 만료되었습니다. 다시 로그인해주세요."
                        }
                        exception.message?.contains("네트워크") == true -> {
                            "네트워크 연결을 확인해주세요."
                        }
                        else -> {
                            "비밀번호 변경 중 오류가 발생했습니다."
                        }
                    }

                    Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                    onComplete()
                }
            )
        } catch (e: Exception) {
            Log.e("PasswordChange", "예상치 못한 오류 발생", e)
            Toast.makeText(context, "비밀번호 변경 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
            onComplete()
        }
    }
}