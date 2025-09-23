package com.example.myapplication

import AuthTokenManager
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import android.content.Intent
import com.example.myapplication.LoginPage
import org.json.JSONObject
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody

// PIN 검증 요청 DTO (PinInputDialog 전용)
data class PinVerifyRequest(val token: String, val pin: String)

// PIN 상태 관리
sealed class PinState {
    object Idle : PinState()
    object Loading : PinState()
    object Success : PinState()
    data class Error(val message: String) : PinState()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PinInputDialog(
    isVisible: Boolean,
    title: String,
    authManager: AuthTokenManager, // token 파라미터를 authManager로 변경
    onDismiss: () -> Unit,
    onPinVerified: (Boolean) -> Unit
) {
    val context = LocalContext.current

    if (isVisible) {
        var pinInput by remember { mutableStateOf("") }
        var pinState by remember { mutableStateOf<PinState>(PinState.Idle) }
        var pinNumbers by remember { mutableStateOf((1..9).shuffled()) }

        val scope = rememberCoroutineScope()
        val redMain = Color(0xFFD32F2F)

        // PIN 검증 함수 - 토큰 갱신 로직 추가
        suspend fun verifyPinWithServer(pin: String): Boolean {
            return try {
                val response = withTimeout(10000) {
                    // 첫 번째 시도
                    val firstResult = makeHttpPinRequest(pin, authManager)

                    if (firstResult.isFailure && firstResult.exceptionOrNull()?.message?.contains("401") == true) {
                        // 401 에러 - 토큰 갱신 시도
                        android.util.Log.d("PinVerification", "401 에러 - 토큰 갱신 시도")

                        val refreshResult = authManager.makeAuthenticatedRequest(
                            url = "${AuthTokenManager.BASE_URL}/user/info",
                            method = "GET"
                        )

                        if (refreshResult.isSuccess) {
                            android.util.Log.d("PinVerification", "토큰 갱신 성공 - PIN 재시도")
                            // 토큰 갱신 후 재시도
                            makeHttpPinRequest(pin, authManager)
                        } else {
                            android.util.Log.e("PinVerification", "토큰 갱신 실패")
                            Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다"))
                        }
                    } else {
                        firstResult
                    }
                }

                when {
                    response.isSuccess -> {
                        val isValid = response.getOrNull() ?: false
                        if (!isValid) {
                            pinState = PinState.Error("PIN이 일치하지 않습니다")
                        }
                        isValid
                    }
                    else -> {
                        val errorMsg = response.exceptionOrNull()?.message ?: ""
                        when {
                            errorMsg.contains("세션이 만료") || errorMsg.contains("재로그인") -> {
                                val intent = Intent(context, LoginPage::class.java)
                                intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                                context.startActivity(intent)
                                pinState = PinState.Error("세션이 만료되어 재로그인이 필요합니다")
                            }
                            errorMsg.contains("404") -> {
                                pinState = PinState.Error("사용자를 찾을 수 없습니다")
                            }
                            else -> {
                                pinState = PinState.Error("서버 오류가 발생했습니다")
                            }
                        }
                        false
                    }
                }
            } catch (e: TimeoutCancellationException) {
                pinState = PinState.Error("요청 시간이 초과되었습니다")
                false
            } catch (e: java.net.UnknownHostException) {
                pinState = PinState.Error("네트워크 연결을 확인해주세요")
                false
            } catch (e: java.net.SocketTimeoutException) {
                pinState = PinState.Error("서버 응답이 지연되고 있습니다")
                false
            } catch (e: Exception) {
                pinState = PinState.Error("네트워크 오류: ${e.message}")
                false
            }
        }

        // PIN 입력이 4자리가 되면 자동으로 검증
        LaunchedEffect(pinInput) {
            if (pinInput.length == 4) {
                pinState = PinState.Loading

                val isValid = verifyPinWithServer(pinInput)

                if (isValid) {
                    pinState = PinState.Success
                    delay(300)
                    onPinVerified(true)
                    // 성공 시 상태 초기화
                    pinInput = ""
                    pinState = PinState.Idle
                } else {
                    pinInput = ""
                    // 숫자 재배치
                    pinNumbers = (1..9).shuffled()
                }
            }
        }

        // 다이얼로그가 닫힐 때 상태 초기화
        DisposableEffect(isVisible) {
            if (!isVisible) {
                pinInput = ""
                pinState = PinState.Idle
            }
            onDispose { }
        }

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
                            title,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = redMain
                        )
                        IconButton(
                            onClick = {
                                pinInput = ""
                                pinState = PinState.Idle
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
                        text = when (pinState) {
                            is PinState.Loading -> "PIN 검증 중..."
                            else -> "4자리 PIN을 입력해주세요"
                        },
                        fontSize = 14.sp,
                        color = Color.Gray
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    // PIN 입력 표시 (회원가입 스타일)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.padding(bottom = 24.dp)
                    ) {
                        repeat(4) { index ->
                            val dotColor = when (pinState) {
                                is PinState.Success -> Color(0xFF2E7D32)
                                is PinState.Error -> redMain
                                is PinState.Loading -> redMain
                                else -> if (index < pinInput.length) redMain else Color.Gray
                            }

                            Box(
                                modifier = Modifier
                                    .size(50.dp)
                                    .border(
                                        2.dp,
                                        dotColor,
                                        RoundedCornerShape(8.dp)
                                    )
                                    .background(
                                        if (index < pinInput.length) dotColor.copy(alpha = 0.1f) else Color.Transparent,
                                        RoundedCornerShape(8.dp)
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (index < pinInput.length) {
                                    Text(
                                        "●",
                                        fontSize = 24.sp,
                                        color = dotColor
                                    )
                                }
                            }
                        }
                    }

                    // 상태별 메시지 표시
                    when (val state = pinState) {
                        is PinState.Loading -> {
                            CircularProgressIndicator(
                                color = redMain,
                                modifier = Modifier.size(24.dp),
                                strokeWidth = 2.dp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is PinState.Success -> {
                            Text(
                                text = "✓ 인증 성공",
                                fontSize = 12.sp,
                                color = Color(0xFF2E7D32),
                                textAlign = TextAlign.Center,
                                fontWeight = FontWeight.Medium
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        is PinState.Error -> {
                            Text(
                                text = state.message,
                                fontSize = 12.sp,
                                color = redMain,
                                textAlign = TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                        else -> {
                            Spacer(modifier = Modifier.height(8.dp))
                        }
                    }

                    // 회원가입 스타일의 숫자 키패드
                    val isInputEnabled = pinState !is PinState.Loading && pinInput.length < 4

                    Column(
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        // 1-9 숫자 (3x3 그리드)
                        for (row in 0..2) {
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                for (col in 0..2) {
                                    val number = pinNumbers[row * 3 + col]
                                    NumberButton(
                                        number = number.toString(),
                                        enabled = isInputEnabled,
                                        onClick = {
                                            if (pinInput.length < 4) {
                                                pinInput += number
                                                pinNumbers = (1..9).shuffled()
                                                if (pinState is PinState.Error) {
                                                    pinState = PinState.Idle
                                                }
                                            }
                                        },
                                        redMain = redMain
                                    )
                                }
                            }
                        }

                        // 하단 버튼들 (0, 삭제, 전체삭제)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 0 버튼
                            NumberButton(
                                number = "0",
                                enabled = isInputEnabled,
                                onClick = {
                                    if (pinInput.length < 4) {
                                        pinInput += "0"
                                        pinNumbers = (1..9).shuffled()
                                        if (pinState is PinState.Error) {
                                            pinState = PinState.Idle
                                        }
                                    }
                                },
                                redMain = redMain
                            )

                            // 삭제 버튼
                            Button(
                                onClick = {
                                    if (pinInput.isNotEmpty()) {
                                        pinInput = pinInput.dropLast(1)
                                        if (pinState is PinState.Error) {
                                            pinState = PinState.Idle
                                        }
                                    }
                                },
                                modifier = Modifier
                                    .size(65.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = redMain.copy(alpha = 0.1f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                enabled = pinState !is PinState.Loading && pinInput.isNotEmpty()
                            ) {
                                Text(
                                    "⌫",
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = redMain
                                )
                            }

                            // 전체삭제 버튼
                            Button(
                                onClick = {
                                    pinInput = ""
                                    pinNumbers = (1..9).shuffled()
                                    if (pinState is PinState.Error) {
                                        pinState = PinState.Idle
                                    }
                                },
                                modifier = Modifier
                                    .size(65.dp)
                                    .clip(RoundedCornerShape(8.dp)),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = redMain.copy(alpha = 0.1f)
                                ),
                                elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
                                enabled = pinState !is PinState.Loading && pinInput.isNotEmpty()
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

                    // 취소 버튼
                    OutlinedButton(
                        onClick = {
                            pinInput = ""
                            pinState = PinState.Idle
                            onDismiss()
                        },
                        enabled = pinState !is PinState.Loading,
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = redMain)
                    ) {
                        Text("취소")
                    }
                }
            }
        }
    }
}

// PIN HTTP 요청 함수
private suspend fun makeHttpPinRequest(pin: String, authManager: AuthTokenManager): Result<Boolean> {
    return withContext(Dispatchers.IO) {
        try {
            val accessToken = authManager.getValidAccessToken()
                ?: return@withContext Result.failure(Exception("로그인이 필요합니다"))

            val requestJson = JSONObject().apply {
                put("pin", pin)
                put("token", accessToken)
            }

            val client = OkHttpClient()
            val mediaType = "application/json".toMediaType()
            val requestBody = requestJson.toString().toRequestBody(mediaType)

            val request = Request.Builder()
                .url("${AuthTokenManager.BASE_URL}/auth/pin")
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()

            response.use {
                when (response.code) {
                    200 -> {
                        android.util.Log.d("PinVerification", "PIN 인증 성공")
                        Result.success(true)
                    }
                    400, 403 -> {
                        android.util.Log.w("PinVerification", "잘못된 PIN")
                        Result.success(false)
                    }
                    401 -> {
                        android.util.Log.w("PinVerification", "401 에러 발생")
                        Result.failure(Exception("401 Unauthorized"))
                    }
                    404 -> {
                        android.util.Log.w("PinVerification", "사용자를 찾을 수 없음")
                        Result.failure(Exception("사용자를 찾을 수 없습니다"))
                    }
                    else -> {
                        android.util.Log.e("PinVerification", "서버 오류: ${response.code}")
                        Result.failure(Exception("서버 오류: ${response.code}"))
                    }
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("PinVerification", "네트워크 오류: ${e.message}")
            Result.failure(Exception("네트워크 오류: ${e.message}"))
        }
    }
}

@Composable
private fun NumberButton(
    number: String,
    enabled: Boolean,
    onClick: () -> Unit,
    redMain: Color
) {
    var isPressed by remember { mutableStateOf(false) }
    val scale by animateFloatAsState(
        targetValue = if (isPressed) 0.95f else 1f,
        animationSpec = tween(100)
    )

    Button(
        onClick = {
            isPressed = true
            onClick()
        },
        modifier = Modifier
            .size(65.dp)
            .clip(RoundedCornerShape(8.dp)),
        colors = ButtonDefaults.buttonColors(
            containerColor = Color(0xFFF5F5F5),
            disabledContainerColor = Color(0xFFF5F5F5).copy(alpha = 0.5f)
        ),
        elevation = ButtonDefaults.buttonElevation(defaultElevation = 2.dp),
        enabled = enabled
    ) {
        Text(
            number,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = if (enabled) redMain else redMain.copy(alpha = 0.5f)
        )
    }

    LaunchedEffect(isPressed) {
        if (isPressed) {
            delay(100)
            isPressed = false
        }
    }
}