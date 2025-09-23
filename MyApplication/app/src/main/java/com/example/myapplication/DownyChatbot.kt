package com.example.myapplication

import AuthTokenManager
import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.QuestionMark
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Send
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.myapplication.LoginPage
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

// 챗봇 메시지 데이터 클래스
data class ChatMessage(
    val id: Long = System.currentTimeMillis(),
    val content: String,
    val isUser: Boolean,
    val timestamp: String = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date()),
    val messageType: MessageType = MessageType.NORMAL
)

enum class MessageType {
    NORMAL, HELP, INFO, ERROR, SUCCESS
}

// 챗봇 상태를 관리하는 싱글톤 클래스 (완전히 새로운 구현)
object ChatBotStateManager {
    private var messageList = mutableStateOf(
        listOf(
            ChatMessage(
                content = "안녕하세요! 저는 다우니 AI입니다 🤖\n\nCoinTrader 앱에 대해 궁금한 것이 있으시면 언제든지 물어보세요!",
                isUser = false,
                messageType = MessageType.NORMAL
            )
        )
    )

    private var typingState = mutableStateOf(false)
    private var statusText = mutableStateOf("연결 확인 중...")
    private var healthyState = mutableStateOf(false)
    private var userProfileBitmap = mutableStateOf<android.graphics.Bitmap?>(null)

    val messages by messageList
    val isTyping by typingState
    val chatbotStatus by statusText
    val isHealthy by healthyState
    val profileBitmap by userProfileBitmap

    fun addNewMessage(message: ChatMessage) {
        messageList.value = messageList.value + message
    }

    fun setTypingIndicator(typing: Boolean) {
        typingState.value = typing
    }

    fun setConnectionStatus(status: String, healthy: Boolean) {
        statusText.value = status
        healthyState.value = healthy
    }

    fun setUserProfileBitmap(bitmap: android.graphics.Bitmap?) {
        userProfileBitmap.value = bitmap
    }

    fun resetMessages() {
        messageList.value = listOf(
            ChatMessage(
                content = "안녕하세요! 저는 다우니 AI입니다 🤖\n\nCoinTrader 앱에 대해 궁금한 것이 있으시면 언제든지 물어보세요!",
                isUser = false,
                messageType = MessageType.NORMAL
            )
        )
    }
}

@Composable
fun DraggableFloatingChatButton(
    onClick: () -> Unit,
    showBadge: Boolean = false,
    buttonPosition: Pair<Float, Float> = Pair(0f, 0f),
    onPositionChange: (Float, Float) -> Unit = { _, _ -> },
    bottomBarHeight: Float? = null
) {
    val density = LocalDensity.current
    val configuration = LocalConfiguration.current
    val screenWidth = with(density) { configuration.screenWidthDp.dp.toPx() }
    val screenHeight = with(density) { configuration.screenHeightDp.dp.toPx() }

    val buttonSize = 64.dp
    val buttonSizePx = with(density) { buttonSize.toPx() }

    // 화면 비율 확인
    val aspectRatio = configuration.screenHeightDp.toFloat() / configuration.screenWidthDp.toFloat()
    val isWideScreen = aspectRatio > 2.1
    val isStandardScreen = aspectRatio <= 1.9

    // 메뉴바 바로 위까지만 허용하도록 엄격한 제한 설정
    val strictBottomLimit = with(density) {
        when {
            isWideScreen -> {
                // 21:9 화면: 메뉴바(56dp) + 시스템바 + 넉넉한 여백(60dp)
                val menuBarHeight = 56.dp.toPx()
                val systemBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx()
                val strictMargin = 60.dp.toPx() // 더 큰 여백으로 확실히 가리지 않게

                menuBarHeight + systemBarHeight + strictMargin
            }
            isStandardScreen -> {
                // 16:9 화면: 메뉴바만 + 안전 여백
                val menuBarHeight = 56.dp.toPx()
                val strictMargin = 40.dp.toPx()

                menuBarHeight + strictMargin
            }
            else -> {
                // 기타 화면
                val menuBarHeight = 56.dp.toPx()
                val systemBarHeight = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding().toPx() * 0.5f
                val strictMargin = 50.dp.toPx()

                menuBarHeight + systemBarHeight + strictMargin
            }
        }
    }

    // 버튼이 완전히 보이는 최대 Y 위치 계산
    val maxAllowedY = screenHeight - buttonSizePx - strictBottomLimit

    // 초기 위치 설정 (우측 상단 쪽으로 더 위로)
    var offsetX by remember {
        mutableStateOf(screenWidth - buttonSizePx - with(density) { 16.dp.toPx() })
    }
    var offsetY by remember {
        mutableStateOf(maxAllowedY - with(density) { 100.dp.toPx() }) // 초기 위치를 더 위로
    }

    // 초기 위치가 화면 밖으로 나가지 않도록 보정
    LaunchedEffect(Unit) {
        offsetY = offsetY.coerceIn(0f, maxAllowedY)
    }

    // 위치 변경 시 부모에게 알림
    LaunchedEffect(offsetX, offsetY) {
        onPositionChange(offsetX, offsetY)
    }

    Box(
        modifier = Modifier
            .offset { IntOffset(offsetX.roundToInt(), offsetY.roundToInt()) }
            .size(buttonSize)
            .pointerInput(Unit) {
                detectDragGestures { _, dragAmount ->
                    val newX = offsetX + dragAmount.x
                    val newY = offsetY + dragAmount.y

                    // X축 제한: 화면 좌우 경계
                    offsetX = newX.coerceIn(0f, screenWidth - buttonSizePx)

                    // Y축 제한: 상단 0부터 엄격한 하단 제한까지만
                    offsetY = newY.coerceIn(0f, maxAllowedY)

                    // 드래그 중 위치 확인
                    println("드래그 중 - Y: $offsetY / 최대: $maxAllowedY")
                }
            }
            .zIndex(9f)
    ) {
        // 챗봇 버튼 이미지
        Image(
            painter = painterResource(id = R.drawable.downy_icon),
            contentDescription = "다우니 AI",
            modifier = Modifier
                .size(buttonSize)
                .clip(CircleShape)
                .clickable { onClick() }
        )

        // 알림 배지
        AnimatedVisibility(
            visible = showBadge,
            enter = scaleIn() + fadeIn(),
            exit = scaleOut() + fadeOut()
        ) {
            Box(
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-8).dp, y = 8.dp)
                    .size(20.dp)
                    .background(Color(0xFFFF4444), CircleShape)
                    .shadow(4.dp, CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.Default.QuestionMark,
                    contentDescription = "도움말",
                    tint = Color.White,
                    modifier = Modifier.size(12.dp)
                )
            }
        }
    }
}

@Composable
fun DownyChatbotScreen(
    onClose: () -> Unit,
    buttonPosition: Pair<Float, Float> = Pair(0f, 0f)
) {
    val context = LocalContext.current
    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    // 슬라이드 애니메이션을 위한 상태
    var isVisible by remember { mutableStateOf(false) }

    // 컴포저블이 처음 실행될 때 애니메이션 시작
    LaunchedEffect(Unit) {
        isVisible = true
    }

    // 슬라이드 애니메이션
    val slideOffset by animateFloatAsState(
        targetValue = if (isVisible) 0f else -1f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    )

    // 배경 투명도 애니메이션
    val backgroundAlpha by animateFloatAsState(
        targetValue = if (isVisible) 0.6f else 0f,
        animationSpec = tween(
            durationMillis = 300,
            easing = FastOutSlowInEasing
        )
    )

    var inputText by remember { mutableStateOf("") }

    val listState = rememberLazyListState()
    val scope = rememberCoroutineScope()

    // 사용자 프로필 이미지 로드 함수
    fun loadUserProfileImage() {
        scope.launch {
            try {
                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/user/info",
                    method = "GET"
                )

                if (result.isSuccess) {
                    val responseData = result.getOrNull() ?: ""
                    val jsonResponse = JSONObject(responseData)

                    // 프로필 이미지가 있다면 로드
                    val pictureObject = jsonResponse.optJSONObject("picture")
                    pictureObject?.let { picture ->
                        val base64Data = picture.optString("base64Data", "")
                        if (base64Data.isNotEmpty()) {
                            android.util.Log.d("ChatBot", "프로필 이미지 로드 시작")
                            // 직접 Base64 이미지 로딩 처리
                            scope.launch {
                                withContext(Dispatchers.IO) {
                                    try {
                                        val cleanBase64 = base64Data.replace("\\s+".toRegex(), "")
                                        val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
                                        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

                                        withContext(Dispatchers.Main) {
                                            ChatBotStateManager.setUserProfileBitmap(bitmap)
                                        }
                                    } catch (e: Exception) {
                                        android.util.Log.e("ChatBot", "Base64 이미지 로딩 실패: ${e.message}", e)
                                        withContext(Dispatchers.Main) {
                                            ChatBotStateManager.setUserProfileBitmap(null)
                                        }
                                    }
                                }
                            }
                        }
                    }
                } else {
                    android.util.Log.e("ChatBot", "사용자 정보 로드 실패")
                }
            } catch (e: Exception) {
                android.util.Log.e("ChatBot", "프로필 이미지 로딩 실패", e)
            }
        }
    }

    // 챗봇 헬스체크 함수 - AuthTokenManager 사용
    suspend fun checkChatbotHealth(): Boolean {
        return try {
            val result = authManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.BASE_URL}/chatbot/health",
                method = "GET"
            )

            result.fold(
                onSuccess = { response ->
                    response.trim() == "ok"
                },
                onFailure = { exception ->
                    android.util.Log.e("ChatBotHealth", "헬스체크 실패: ${exception.message}")
                    false
                }
            )
        } catch (e: Exception) {
            android.util.Log.e("ChatBotHealth", "헬스체크 예외: ${e.message}")
            false
        }
    }

    // 백엔드 API 호출 함수 - AuthTokenManager 사용 (개선된 버전)
    suspend fun sendMessageToBackend(question: String): String {
        return withContext(Dispatchers.IO) {
            try {
                // 요청 body 준비 (토큰은 AuthTokenManager가 자동으로 추가함)
                val requestJson = JSONObject().apply {
                    put("question", question)
                }

                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/chatbot/ask",
                    method = "POST",
                    requestBody = requestJson.toString()
                )

                result.fold(
                    onSuccess = { responseData ->
                        try {
                            val jsonResponse = JSONObject(responseData)
                            val answer = jsonResponse.getString("answer")
                            val needsHumanSupport = jsonResponse.optBoolean("needsHumanSupport", false)

                            if (needsHumanSupport) {
                                "$answer\n\n💬 더 자세한 도움이 필요하시면 고객센터에 문의해주세요."
                            } else {
                                answer
                            }
                        } catch (e: Exception) {
                            android.util.Log.e("ChatBot", "JSON 파싱 오류: ${e.message}")
                            "응답 처리 중 오류가 발생했습니다."
                        }
                    },
                    onFailure = { exception ->
                        android.util.Log.e("ChatBot", "챗봇 요청 실패: ${exception.message}")
                        val errorMessage = exception.message ?: ""

                        when {
                            errorMessage.contains("세션이 만료") || errorMessage.contains("재로그인") -> {
                                "세션이 만료되어 재로그인이 필요합니다."
                            }
                            errorMessage.contains("400") || errorMessage.contains("잘못된") -> {
                                "요청 형식이 올바르지 않습니다. 다시 시도해주세요."
                            }
                            errorMessage.contains("403") || errorMessage.contains("권한") -> {
                                "접근 권한이 없습니다. 관리자에게 문의해주세요."
                            }
                            errorMessage.contains("404") -> {
                                "챗봇 서비스를 찾을 수 없습니다. 잠시 후 다시 시도해주세요."
                            }
                            errorMessage.contains("500") || errorMessage.contains("서버") -> {
                                "서버에서 일시적인 오류가 발생했습니다. 잠시 후 다시 시도해주세요."
                            }
                            errorMessage.contains("네트워크") || errorMessage.contains("연결") -> {
                                errorMessage
                            }
                            else -> {
                                "예상치 못한 오류가 발생했습니다: $errorMessage"
                            }
                        }
                    }
                )
            } catch (e: Exception) {
                android.util.Log.e("ChatBot", "챗봇 요청 예외: ${e.message}")
                "네트워크 오류가 발생했습니다: ${e.message}"
            }
        }
    }

    // 챗봇 헬스체크 실행 (한번만 실행)
    LaunchedEffect(Unit) {
        if (ChatBotStateManager.chatbotStatus == "연결 확인 중...") {
            val healthStatus = checkChatbotHealth()
            ChatBotStateManager.setConnectionStatus(
                if (healthStatus) "온라인" else "오프라인",
                healthStatus
            )
        }

        // 사용자 프로필 이미지 로드
        loadUserProfileImage()
    }

    // 메시지 추가 시 스크롤
    LaunchedEffect(ChatBotStateManager.messages.size) {
        if (ChatBotStateManager.messages.isNotEmpty()) {
            listState.animateScrollToItem(ChatBotStateManager.messages.size - 1)
        }
    }

    // 닫기 함수 - 애니메이션과 함께
    fun closeWithAnimation() {
        scope.launch {
            isVisible = false
            delay(300) // 애니메이션 완료 대기
            onClose()
        }
    }

    // 메시지 전송 처리 함수
    fun handleSendMessage(messageContent: String) {
        if (!ChatBotStateManager.isTyping && ChatBotStateManager.isHealthy) {
            val userMessage = ChatMessage(
                content = messageContent,
                isUser = true
            )

            ChatBotStateManager.addNewMessage(userMessage)

            ChatBotStateManager.setTypingIndicator(true)
            scope.launch {
                delay((800..1500).random().toLong())
                val backendResponse = sendMessageToBackend(messageContent)
                ChatBotStateManager.setTypingIndicator(false)

                // 세션 만료 체크 및 로그인 페이지로 이동
                if (backendResponse.contains("세션이 만료") || backendResponse.contains("재로그인이 필요합니다")) {
                    val intent = Intent(context, LoginPage::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)
                    return@launch
                }

                val botMessage = ChatMessage(
                    content = backendResponse,
                    isUser = false,
                    messageType = when {
                        backendResponse.contains("오류") || backendResponse.contains("실패") -> MessageType.ERROR
                        backendResponse.contains("성공") || backendResponse.contains("완료") -> MessageType.SUCCESS
                        backendResponse.contains("도움말") || backendResponse.contains("💡") -> MessageType.HELP
                        backendResponse.contains("정보") || backendResponse.contains("ℹ️") -> MessageType.INFO
                        else -> MessageType.NORMAL
                    }
                )

                ChatBotStateManager.addNewMessage(botMessage)
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha))
            .clickable { closeWithAnimation() }
            .zIndex(10f),
        contentAlignment = Alignment.CenterStart
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.85f)
                .fillMaxHeight(0.8f)
                .graphicsLayer {
                    translationX = slideOffset * size.width
                }
                .clickable { },
            backgroundColor = Color.White,
            elevation = 16.dp,
            shape = RoundedCornerShape(topEnd = 20.dp, bottomEnd = 20.dp)
        ) {
            Column {
                // 헤더
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.linearGradient(
                                colors = listOf(
                                    Color(0xFFFF5722),
                                    Color(0xFFD32F2F)
                                )
                            )
                        )
                        .padding(20.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            // 헤더 다우니 아이콘
                            Image(
                                painter = painterResource(id = R.drawable.downy_icon),
                                contentDescription = "다우니 AI",
                                modifier = Modifier
                                    .size(50.dp)
                                    .clip(CircleShape)
                            )
                            Column {
                                Text(
                                    "다우니 AI",
                                    color = Color.White,
                                    fontSize = 22.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(
                                                if (ChatBotStateManager.isHealthy) Color(0xFF4CAF50) else Color(0xFFFF5722),
                                                CircleShape
                                            )
                                    )
                                    Text(
                                        ChatBotStateManager.chatbotStatus,
                                        color = Color.White.copy(alpha = 0.9f),
                                        fontSize = 12.sp
                                    )
                                }
                            }
                        }

                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 새로고침 버튼 - 연결 확인 + 대화 내용 초기화
                            IconButton(
                                onClick = {
                                    scope.launch {
                                        // 대화 내용 초기화
                                        ChatBotStateManager.resetMessages()

                                        // 연결 상태 확인
                                        ChatBotStateManager.setConnectionStatus("연결 확인 중...", false)
                                        val healthStatus = checkChatbotHealth()
                                        ChatBotStateManager.setConnectionStatus(
                                            if (healthStatus) "온라인" else "오프라인",
                                            healthStatus
                                        )
                                    }
                                }
                            ) {
                                Icon(
                                    Icons.Default.Refresh,
                                    contentDescription = "새로고침",
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }

                            IconButton(onClick = { closeWithAnimation() }) {
                                Icon(
                                    Icons.Default.Close,
                                    contentDescription = "닫기",
                                    tint = Color.White,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                        }
                    }
                }

                // 메시지 영역
                LazyColumn(
                    state = listState,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(Color(0xFFF8F9FA))
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(ChatBotStateManager.messages) { message ->
                        DownyMessageItem(message = message)
                    }

                    // 헬스체크 실패 시 상태 메시지
                    if (ChatBotStateManager.messages.size == 1 && !ChatBotStateManager.isHealthy) {
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                backgroundColor = Color(0xFFFFEBEE),
                                shape = RoundedCornerShape(12.dp),
                                elevation = 2.dp
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        Icons.Default.Warning,
                                        contentDescription = "경고",
                                        tint = Color(0xFFD32F2F),
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(
                                        "챗봇 서비스에 연결할 수 없습니다.",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFFD32F2F),
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        "네트워크 상태를 확인하고 다시 시도해주세요.",
                                        fontSize = 12.sp,
                                        color = Color(0xFF666666),
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }
                        }
                    }

                    // 타이핑 인디케이터
                    if (ChatBotStateManager.isTyping) {
                        item {
                            DownyTypingIndicator()
                        }
                    }
                }

                // 입력 영역
                DownyChatInputArea(
                    inputText = inputText,
                    onInputChange = { inputText = it },
                    enabled = ChatBotStateManager.isHealthy,
                    onSendMessage = {
                        if (inputText.isNotBlank()) {
                            val messageToSend = inputText
                            inputText = ""
                            handleSendMessage(messageToSend)
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun DownyMessageItem(message: ChatMessage) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (message.isUser) Arrangement.End else Arrangement.Start
    ) {
        if (!message.isUser) {
            Image(
                painter = painterResource(id = R.drawable.downy_icon),
                contentDescription = "다우니 AI",
                modifier = Modifier
                    .size(40.dp)
                    .clip(CircleShape)
            )
            Spacer(modifier = Modifier.width(8.dp))
        }

        Card(
            modifier = Modifier.widthIn(max = 260.dp),
            backgroundColor = when {
                message.isUser -> Color(0xFFD32F2F)
                message.messageType == MessageType.ERROR -> Color(0xFFFFEBEE)
                message.messageType == MessageType.SUCCESS -> Color(0xFFE8F5E8)
                message.messageType == MessageType.INFO -> Color(0xFFFFE0E0)
                message.messageType == MessageType.HELP -> Color(0xFFFFF3E0)
                else -> Color.White
            },
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (message.isUser) 18.dp else 6.dp,
                bottomEnd = if (message.isUser) 6.dp else 18.dp
            ),
            elevation = 3.dp
        ) {
            Column(
                modifier = Modifier.padding(14.dp)
            ) {
                Text(
                    text = message.content,
                    color = when {
                        message.isUser -> Color.White
                        message.messageType == MessageType.ERROR -> Color(0xFFB71C1C)
                        message.messageType == MessageType.SUCCESS -> Color(0xFF2E7D32)
                        message.messageType == MessageType.INFO -> Color(0xFFD32F2F)
                        message.messageType == MessageType.HELP -> Color(0xFFE65100)
                        else -> Color(0xFF2C2C2C)
                    },
                    fontSize = 15.sp,
                    lineHeight = 20.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = message.timestamp,
                    color = when {
                        message.isUser -> Color.White.copy(alpha = 0.8f)
                        else -> Color.Gray.copy(alpha = 0.7f)
                    },
                    fontSize = 11.sp,
                    textAlign = if (message.isUser) TextAlign.End else TextAlign.Start,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }

        if (message.isUser) {
            Spacer(modifier = Modifier.width(8.dp))

            // 사용자 프로필 이미지 또는 기본 아이콘
            if (ChatBotStateManager.profileBitmap != null) {
                Image(
                    bitmap = ChatBotStateManager.profileBitmap!!.asImageBitmap(),
                    contentDescription = "사용자 프로필",
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF90A4AE)),
                    contentScale = androidx.compose.ui.layout.ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .background(Color(0xFF90A4AE), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "사용자",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun DownyTypingIndicator() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Image(
            painter = painterResource(id = R.drawable.downy_icon),
            contentDescription = "다우니 AI",
            modifier = Modifier
                .size(40.dp)
                .clip(CircleShape)
        )

        Card(
            backgroundColor = Color.White,
            shape = RoundedCornerShape(18.dp, 18.dp, 18.dp, 6.dp),
            elevation = 3.dp
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                repeat(3) { index ->
                    val infiniteTransition = rememberInfiniteTransition()
                    val scale by infiniteTransition.animateFloat(
                        initialValue = 0.8f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(600),
                            repeatMode = RepeatMode.Reverse,
                            initialStartOffset = StartOffset(index * 200)
                        )
                    )

                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .graphicsLayer {
                                scaleX = scale
                                scaleY = scale
                            }
                            .background(Color(0xFFD32F2F), CircleShape)
                    )
                }
            }
        }
    }
}

@Composable
fun DownyChatInputArea(
    inputText: String,
    onInputChange: (String) -> Unit,
    enabled: Boolean = true,
    onSendMessage: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = Color.White,
        elevation = 8.dp,
        shape = RoundedCornerShape(0.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(
                modifier = Modifier.weight(1f),
                backgroundColor = Color(0xFFF8F9FA),
                shape = RoundedCornerShape(25.dp),
                elevation = 0.dp
            ) {
                OutlinedTextField(
                    value = inputText,
                    onValueChange = onInputChange,
                    modifier = Modifier.fillMaxWidth(),
                    enabled = enabled,
                    placeholder = {
                        Text(
                            if (enabled) "다우니에게 질문해보세요..." else "챗봇 서비스에 연결 중...",
                            color = Color.Gray,
                            fontSize = 14.sp
                        )
                    },
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        backgroundColor = Color.Transparent,
                        focusedBorderColor = Color.Transparent,
                        unfocusedBorderColor = Color.Transparent,
                        disabledBorderColor = Color.Transparent,
                        cursorColor = Color(0xFFD32F2F)
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (enabled && inputText.isNotBlank()) {
                                onSendMessage()
                            }
                        }
                    ),
                    singleLine = false,
                    maxLines = 4
                )
            }

            Button(
                onClick = onSendMessage,
                modifier = Modifier.size(50.dp),
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (enabled) Color(0xFFD32F2F) else Color.Gray,
                    contentColor = Color.White
                ),
                enabled = enabled && inputText.isNotBlank(),
                contentPadding = PaddingValues(0.dp),
                elevation = ButtonDefaults.elevation(6.dp)
            ) {
                Icon(
                    Icons.Default.Send,
                    contentDescription = "전송",
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }
}