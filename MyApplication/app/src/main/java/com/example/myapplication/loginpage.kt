package com.example.myapplication
import AuthTokenManager
import com.example.myapplication.security.E2EEncryptionUtils
import SecureBaseActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.Checkbox
import androidx.compose.material.CheckboxDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.example.myapplication.security.SecurityModule
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.util.concurrent.TimeUnit
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Build
import java.net.SocketTimeoutException
import java.net.UnknownHostException

data class LoginRequest(
    val username: String,
    val password: String
)

data class LoginResponse(
    val accessToken: String,
    val refreshToken: String
)

data class RefreshRequest(
    val refreshToken: String
)

data class CheckResult(
    val name: String,
    val passed: Boolean,
    val hasWarning: Boolean = false
)

object RetrofitClient {
    public const val BASE_URL = "http://43.200.50.27"

    private var appContext: android.content.Context? = null
    private var authTokenManager: AuthTokenManager? = null

    fun init(context: android.content.Context) {
        appContext = context.applicationContext
        authTokenManager = AuthTokenManager(context)
    }


    private val authInterceptor = Interceptor { chain ->
        val originalRequest = chain.request()
        val requestBuilder = originalRequest.newBuilder()

        val isAuthEndpoint = originalRequest.url.toString().contains("/auth/")
        val hasTokenParam = originalRequest.url.queryParameter("token") != null
        val hasNoAutoTokenHeader = originalRequest.header("No-Auto-Token") != null

        if (!isAuthEndpoint && !hasTokenParam && !hasNoAutoTokenHeader) {
            val accessToken = runBlocking {
                try {
                    authTokenManager?.getValidAccessToken()
                } catch (e: Exception) {
                    null
                }
            }

            accessToken?.let {
                val url = originalRequest.url.newBuilder()
                    .addQueryParameter("token", it)
                    .build()
                requestBuilder.url(url)
            }
        }

        if (hasNoAutoTokenHeader) {
            requestBuilder.removeHeader("No-Auto-Token")
        }

        val request = requestBuilder.build()
        val response = chain.proceed(request)

        if (response.code == 401 && !isAuthEndpoint) {
            response.close()

            val newToken = runBlocking {
                try {
                    authTokenManager?.getValidAccessToken()
                } catch (e: Exception) {
                    null
                }
            }

            newToken?.let { token ->
                val newUrl = originalRequest.url.newBuilder()
                    .removeAllQueryParameters("token")
                    .addQueryParameter("token", token)
                    .build()

                val newRequest = originalRequest.newBuilder()
                    .url(newUrl)
                    .build()
                return@Interceptor chain.proceed(newRequest)
            }
        }

        return@Interceptor response
    }

    private val loggingInterceptor = HttpLoggingInterceptor { message ->
        android.util.Log.d("OkHttp", message)
    }.apply {
        level = HttpLoggingInterceptor.Level.BODY
    }

    private val client = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .addInterceptor(authInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val apiService: ApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(client)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(ApiService::class.java)
    }
}

class LoginPage : SecureBaseActivity() {
    private var isSecurityCheckPassed = false
    private lateinit var authManager: AuthTokenManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        RetrofitClient.init(this)

        authManager = AuthTokenManager(this)

        migrateTokensIfNeeded()

        performSecurityCheck()
    }

    private fun migrateTokensIfNeeded() {
        val sharedPref = getSharedPreferences("app_prefs", 0)
        val oldAccessToken = sharedPref.getString("access_token", null)
        val oldRefreshToken = sharedPref.getString("refresh_token", null)

        if (!oldAccessToken.isNullOrEmpty() && !oldRefreshToken.isNullOrEmpty()) {
            authManager.saveTokens(
                accessToken = oldAccessToken,
                refreshToken = oldRefreshToken,
                expiresIn = 600L
            )

            sharedPref.edit().apply {
                remove("access_token")
                remove("refresh_token")
                apply()
            }
        }
    }

    private fun performSecurityCheck() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    var currentScreen by remember { mutableStateOf(ScreenState.SECURITY_CHECK) }
                    var securityMessage by remember { mutableStateOf("보안 환경 확인 중") }

                    var checkProgress by remember { mutableStateOf(0f) }
                    var currentCheckItem by remember { mutableStateOf("시스템 초기화") }
                    var checkResults by remember { mutableStateOf<List<CheckResult>>(emptyList()) }

                    var showSecurityDialog by remember { mutableStateOf(false) }
                    var securityDialogMessage by remember { mutableStateOf("") }

                    LaunchedEffect(Unit) {
                        val checkItems = listOf(
                            "보안 모듈 로드" to 0.16f,
                            "루팅 상태 확인" to 0.33f,
                            "디버깅 환경 체크" to 0.5f,
                            "Frida 탐지" to 0.66f,
                            "무결성 검증" to 0.83f,
                            "보안 정책 적용" to 1.0f
                        )

                        val warningMessages = mutableListOf<String>()
                        val results = mutableListOf<CheckResult>()

                        currentCheckItem = checkItems[0].first
                        checkProgress = checkItems[0].second
                        delay(300)

                        val isModuleReady = SecurityModule.isReady()
                        results.add(CheckResult("보안 모듈", isModuleReady, !isModuleReady))

                        if (!isModuleReady) {
                            warningMessages.add("• 보안 모듈 로드 실패")
                        }

                        currentCheckItem = checkItems[1].first
                        checkProgress = checkItems[1].second
                        delay(300)

                        val isRooted = try {
                            SecurityModule.checkRoot()
                        } catch (e: Exception) {
                            false
                        }
                        results.add(CheckResult("루팅 체크", !isRooted, isRooted))
                        if (isRooted) warningMessages.add("• 루팅된 기기")

                        currentCheckItem = checkItems[2].first
                        checkProgress = checkItems[2].second
                        delay(300)

                        val isDebugging = try {
                            SecurityModule.checkDebugging()
                        } catch (e: Exception) {
                            false
                        }
                        results.add(CheckResult("디버깅", !isDebugging, isDebugging))
                        if (isDebugging) warningMessages.add("• 디버깅 모드")

                        currentCheckItem = checkItems[3].first
                        checkProgress = checkItems[3].second
                        delay(300)

                        val isFridaDetected = try {
                            SecurityModule.checkFrida()
                        } catch (e: Exception) {
                            false
                        }
                        results.add(CheckResult("Frida 탐지", !isFridaDetected, isFridaDetected))
                        if (isFridaDetected) warningMessages.add("• Frida 탐지")

                        currentCheckItem = checkItems[4].first
                        checkProgress = checkItems[4].second
                        delay(300)

                        currentCheckItem = checkItems[4].first
                        checkProgress = checkItems[4].second
                        delay(300)

                        val isIntegrityValid = try {
                            SecurityModule.checkIntegrity(this@LoginPage)
                        } catch (e: Exception) {
                            true
                        }
                        results.add(CheckResult("무결성", isIntegrityValid, !isIntegrityValid))
                        if (!isIntegrityValid) warningMessages.add("• 무결성 검증 실패")

                        currentCheckItem = checkItems[5].first
                        checkProgress = checkItems[5].second
                        checkResults = results
                        delay(500)

                        if (warningMessages.isNotEmpty()) {
                            isSecurityCheckPassed = false

                            securityDialogMessage = """
                                다음 보안 위험이 감지되었습니다:
                                
                                ${warningMessages.joinToString("\n")}
                                
                                계속 진행하시겠습니까?
                            """.trimIndent()

                            showSecurityDialog = true

                        } else {
                            isSecurityCheckPassed = true
                            securityMessage = "보안 체크 완료"
                            delay(800)

                            if (shouldAutoLogin()) {
                                currentScreen = ScreenState.AUTO_LOGIN
                            } else {
                                currentScreen = ScreenState.LOGIN
                            }
                        }

                    }

                    if (showSecurityDialog) {
                        ModernSecurityDialog(
                            message = securityDialogMessage,
                            onContinue = {
                                showSecurityDialog = false
                                isSecurityCheckPassed = true

                                currentScreen = if (shouldAutoLogin()) {
                                    ScreenState.AUTO_LOGIN
                                } else {
                                    ScreenState.LOGIN
                                }
                            },
                            onExit = {
                                showSecurityDialog = false
                                finishAndRemoveTask()
                                System.exit(0)
                            }
                        )
                    }

                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            fadeIn(animationSpec = tween(500)) togetherWith
                                    fadeOut(animationSpec = tween(300))
                        }
                    ) { screen ->
                        when (screen) {
                            ScreenState.SECURITY_CHECK -> {
                                ModernSecurityCheckScreen(
                                    message = securityMessage,
                                    progress = checkProgress,
                                    currentItem = currentCheckItem,
                                    checkResults = checkResults
                                )
                            }
                            ScreenState.AUTO_LOGIN -> {
                                ModernAutoLoginScreen()
                            }
                            ScreenState.LOGIN -> {
                                LoginScreen()
                            }
                        }
                    }
                }
            }
        }
    }

    @Composable
    fun ModernSecurityCheckScreen(
        message: String,
        progress: Float,
        currentItem: String,
        checkResults: List<CheckResult>
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF5F5F5)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.padding(32.dp)
            ) {
                AnimatedVisibility(
                    visible = true,
                    enter = fadeIn(animationSpec = tween(1000))
                ) {
                    Image(
                        painter = painterResource(id = R.drawable.downbit_logo),
                        contentDescription = "DownBit 로고",
                        modifier = Modifier
                            .height(120.dp)
                            .padding(bottom = 32.dp)
                    )
                }

                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier.size(120.dp)
                ) {
                    CircularProgressAnimated(
                        progress = progress,
                        modifier = Modifier.fillMaxSize()
                    )

                    Icon(
                        imageVector = Icons.Default.Shield,
                        contentDescription = "보안",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(40.dp)
                    )
                }

                Spacer(modifier = Modifier.height(32.dp))

                Text(
                    text = message,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF333333)
                )

                Spacer(modifier = Modifier.height(8.dp))

                AnimatedContent(
                    targetState = currentItem,
                    transitionSpec = {
                        slideInVertically { height -> height } + fadeIn() togetherWith
                                slideOutVertically { height -> -height } + fadeOut()
                    }
                ) { item ->
                    Text(
                        text = item,
                        fontSize = 14.sp,
                        color = Color(0xFF666666)
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                if (checkResults.isNotEmpty()) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        backgroundColor = Color.White,
                        elevation = 2.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp)
                        ) {
                            checkResults.forEach { result ->
                                CheckResultItem(result)
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "${(progress * 100).toInt()}%",
                    fontSize = 12.sp,
                    color = Color(0xFF999999),
                    fontFamily = FontFamily.Monospace
                )
            }
        }
    }

    @Composable
    fun CircularProgressAnimated(
        progress: Float,
        modifier: Modifier = Modifier
    ) {
        val animatedProgress by animateFloatAsState(
            targetValue = progress,
            animationSpec = tween(
                durationMillis = 600,
                easing = FastOutSlowInEasing
            )
        )

        Canvas(modifier = modifier) {
            val strokeWidth = 4.dp.toPx()
            val radius = (size.minDimension - strokeWidth) / 2
            val center = Offset(size.width / 2, size.height / 2)

            drawCircle(
                color = Color(0xFFE0E0E0),
                radius = radius,
                center = center,
                style = Stroke(strokeWidth)
            )

            drawArc(
                color = Color(0xFFD32F2F),
                startAngle = -90f,
                sweepAngle = 360f * animatedProgress,
                useCenter = false,
                topLeft = Offset(center.x - radius, center.y - radius),
                size = Size(radius * 2, radius * 2),
                style = Stroke(strokeWidth, cap = StrokeCap.Round)
            )
        }
    }

    @Composable
    fun CheckResultItem(result: CheckResult) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = result.name,
                fontSize = 14.sp,
                color = Color(0xFF666666)
            )

            AnimatedVisibility(
                visible = true,
                enter = scaleIn(animationSpec = tween(300))
            ) {
                Icon(
                    imageVector = if (result.passed) Icons.Default.CheckCircle else Icons.Default.Warning,
                    contentDescription = if (result.passed) "통과" else "경고",
                    tint = if (result.passed) Color(0xFF4CAF50) else Color(0xFFFF9800),
                    modifier = Modifier.size(20.dp)
                )
            }
        }
    }

    @Composable
    fun ModernAutoLoginScreen() {
        LaunchedEffect(Unit) {
            delay(500)
            performAutoLogin()
        }
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            Color.White,
                            Color(0xFFF5F5F5)
                        )
                    )
                ),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val infiniteTransition = rememberInfiniteTransition()
                val scale by infiniteTransition.animateFloat(
                    initialValue = 1f,
                    targetValue = 1.1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(1000),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Image(
                    painter = painterResource(id = R.drawable.downbit_logo),
                    contentDescription = "DownBit 로고",
                    modifier = Modifier
                        .height(180.dp)
                        .scale(scale)
                        .padding(bottom = 32.dp)
                )

                LoadingDotsAnimation()

                Spacer(modifier = Modifier.height(24.dp))

                Text(
                    "자동 로그인 중",
                    fontSize = 16.sp,
                    color = Color(0xFF666666),
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }

    @Composable
    fun LoadingDotsAnimation() {
        val infiniteTransition = rememberInfiniteTransition()

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            repeat(3) { index ->
                val delay = index * 100
                val alpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = delay),
                        repeatMode = RepeatMode.Reverse
                    )
                )

                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .background(
                            Color(0xFFD32F2F).copy(alpha = alpha),
                            shape = CircleShape
                        )
                )
            }
        }
    }

    @Composable
    fun ModernSecurityDialog(
        message: String,
        onContinue: () -> Unit,
        onExit: () -> Unit
    ) {
        Dialog(onDismissRequest = { }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 32.dp),
                contentAlignment = Alignment.Center
            ) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .wrapContentHeight(),
                    shape = RoundedCornerShape(16.dp),
                    backgroundColor = Color.White,
                    elevation = 8.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Warning,
                            contentDescription = "경고",
                            tint = Color(0xFFFF6B00),
                            modifier = Modifier.size(48.dp)
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = "보안 경고",
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF333333),
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth()
                        )

                        Spacer(modifier = Modifier.height(16.dp))

                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "다음 보안 위험이 감지되",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                            Text(
                                text = "었습니다:",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )

                            Spacer(modifier = Modifier.height(12.dp))

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                if (message.contains("디버깅")) {
                                    Text(
                                        text = "• 디버깅 모드",
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                                if (message.contains("루팅")) {
                                    Text(
                                        text = "• 루팅된 기기",
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                                if (message.contains("무결성")) {
                                    Text(
                                        text = "• 무결성 검증 실패",
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                                if (message.contains("Frida")) {
                                    Text(
                                        text = "• Frida 탐지",
                                        fontSize = 14.sp,
                                        color = Color(0xFF666666)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))

                            Text(
                                text = "계속 진행하시겠습니까?",
                                fontSize = 14.sp,
                                color = Color(0xFF666666),
                                textAlign = TextAlign.Center
                            )
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 8.dp),
                            horizontalArrangement = Arrangement.Center
                        ) {
                            OutlinedButton(
                                onClick = onExit,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 6.dp),
                                colors = ButtonDefaults.outlinedButtonColors(
                                    contentColor = Color(0xFF666666)
                                )
                            ) {
                                Text(
                                    text = "종료",
                                    textAlign = TextAlign.Center
                                )
                            }

                            Button(
                                onClick = onContinue,
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(start = 6.dp),
                                colors = ButtonDefaults.buttonColors(
                                    backgroundColor = Color(0xFFD32F2F)
                                )
                            ) {
                                Text(
                                    text = "계속",
                                    color = Color.White,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    private fun shouldAutoLogin(): Boolean {
        if (!isSecurityCheckPassed) {
            return false
        }

        val sharedPref = getSharedPreferences("app_prefs", 0)
        val autoLoginEnabled = sharedPref.getBoolean("auto_login_enabled", false)

        val (_, refreshToken) = authManager.getStoredTokens()

        return autoLoginEnabled && !refreshToken.isNullOrEmpty()
    }

    private fun performAutoLogin() {
        val sharedPref = getSharedPreferences("app_prefs", 0)
        val autoLoginEnabled = sharedPref.getBoolean("auto_login_enabled", false)

        if (!autoLoginEnabled) {
            showLoginScreen()
            return
        }

        CoroutineScope(Dispatchers.Main).launch {
            try {
                val (_, refreshToken) = authManager.getStoredTokens()
                if (refreshToken.isNullOrEmpty()) {
                    showLoginScreen()
                    return@launch
                }

                val refreshRequest = RefreshRequest(refreshToken)
                val response = RetrofitClient.apiService.autoLogin(refreshRequest)

                when (response.code()) {
                    200 -> {
                        val loginResponse = response.body()
                        if (loginResponse != null) {
                            authManager.saveTokens(
                                accessToken = loginResponse.accessToken,
                                refreshToken = loginResponse.refreshToken,
                                expiresIn = 600L
                            )

                            val intent = Intent(this@LoginPage, MainActivity::class.java)
                            startActivity(intent)
                            finish()
                        } else {
                            handleAutoLoginFailure()
                        }
                    }
                    401 -> {
                        handleAutoLoginFailure()
                    }
                    403 -> {
                        Toast.makeText(this@LoginPage, "탈퇴한 회원입니다", Toast.LENGTH_SHORT).show()
                        handleAutoLoginFailure()
                    }
                    404 -> {
                        handleAutoLoginFailure()
                    }
                    500 -> {
                        Toast.makeText(this@LoginPage, "서버 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                        showLoginScreen()
                    }
                    else -> {
                        Toast.makeText(this@LoginPage, "로그인 오류가 발생했습니다", Toast.LENGTH_SHORT).show()
                        showLoginScreen()
                    }
                }

            } catch (e: Exception) {
                handleAutoLoginFailure()
            }
        }
    }

    private fun handleAutoLoginFailure() {
        authManager.logout()

        val sharedPref = getSharedPreferences("app_prefs", 0)
        sharedPref.edit().apply {
            putBoolean("auto_login_enabled", false)
            apply()
        }

        showLoginScreen()
    }

    private fun showLoginScreen() {
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    LoginScreen()
                }
            }
        }
    }

    @Composable
    fun LoginScreen() {
        val context = LocalContext.current
        var email by remember { mutableStateOf("") }
        var password by remember { mutableStateOf("") }
        var isLoading by remember { mutableStateOf(false) }

        val sharedPref = context.getSharedPreferences("app_prefs", 0)
        var autoLoginEnabled by remember {
            mutableStateOf(sharedPref.getBoolean("auto_login_enabled", false))
        }

        val redMain = Color(0xFFD32F2F)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            verticalArrangement = Arrangement.Top,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Image(
                painter = painterResource(id = R.drawable.downbit_logo),
                contentDescription = "DownBit 로고",
                modifier = Modifier
                    .height(300.dp)
                    .padding(bottom = 10.dp)
            )

            Spacer(modifier = Modifier.height(10.dp))

            OutlinedTextField(
                value = email,
                onValueChange = { email = it },
                label = { Text("아이디", color = redMain) },
                leadingIcon = { Icon(Icons.Default.Person, contentDescription = "아이디 아이콘", tint = redMain) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("비밀번호", color = redMain) },
                leadingIcon = { Icon(Icons.Default.Lock, contentDescription = null, tint = redMain) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                visualTransformation = PasswordVisualTransformation(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain
                )
            )

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = autoLoginEnabled,
                    onCheckedChange = {
                        autoLoginEnabled = it
                        sharedPref.edit().putBoolean("auto_login_enabled", it).apply()
                    },
                    colors = CheckboxDefaults.colors(
                        checkedColor = redMain,
                        uncheckedColor = redMain
                    )
                )
                Text("자동 로그인", color = redMain)
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (isLoading) {
                CircularProgressIndicator(color = redMain)
            } else {
                Button(
                    onClick = {
                        if (!isSecurityCheckPassed) {
                            Toast.makeText(context, "보안 체크가 완료되지 않았습니다", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        if (email.isNotEmpty() && password.isNotEmpty()) {
                            isLoading = true
                            CoroutineScope(Dispatchers.Main).launch {
                                try {
                                    val encryptedData = E2EEncryptionUtils.encryptData(
                                        "username" to email,
                                        "password" to password
                                    )

                                    val encryptedRequest = mapOf("e2edata" to encryptedData)
                                    val response = RetrofitClient.apiService.login(encryptedRequest)

                                    if (response.isSuccessful) {
                                        val loginResponse = response.body()
                                        loginResponse?.let { responseBody ->
                                            authManager.saveTokens(
                                                accessToken = responseBody.accessToken,
                                                refreshToken = responseBody.refreshToken,
                                                expiresIn = 600L
                                            )

                                            val sharedPref = context.getSharedPreferences("app_prefs", 0)
                                            sharedPref.edit().apply {
                                                putBoolean("auto_login_enabled", autoLoginEnabled)
                                                apply()
                                            }

                                            val intent = Intent(context, MainActivity::class.java)
                                            context.startActivity(intent)
                                            (context as ComponentActivity).finish()
                                        } ?: run {
                                            Toast.makeText(context, "로그인 응답이 비어있습니다", Toast.LENGTH_SHORT).show()
                                        }
                                    } else {
                                        when (response.code()) {
                                            401 -> Toast.makeText(context, "아이디 또는 비밀번호가 틀렸습니다", Toast.LENGTH_SHORT).show()
                                            403 -> Toast.makeText(context, "탈퇴한 회원입니다", Toast.LENGTH_SHORT).show()
                                            else -> Toast.makeText(context, "로그인 실패: ${response.code()}", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                } catch (e: Exception) {
                                    Toast.makeText(context, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                                } finally {
                                    isLoading = false
                                }
                            }
                        } else {
                            Toast.makeText(context, "아이디와 비밀번호를 입력해주세요", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(backgroundColor = redMain)
                ) {
                    Text("로그인", color = Color.White)
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    "ID/PW 찾기",
                    color = redMain,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, FindAccountActivity::class.java))
                    }
                )
                Text(
                    "회원가입",
                    color = redMain,
                    modifier = Modifier.clickable {
                        context.startActivity(Intent(context, RegisterActivity::class.java))
                    }
                )
            }
        }
    }

    enum class ScreenState {
        SECURITY_CHECK,
        AUTO_LOGIN,
        LOGIN
    }
}