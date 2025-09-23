import kotlinx.coroutines.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.TimeUnit

/**
 * OAuth 2.0 토큰 관리 및 자동 갱신을 담당하는 클래스
 * - Access Token 자동 갱신 (한 번만!)
 * - 토큰 저장/불러오기
 * - API 요청 시 토큰 자동 첨부
 * - HTTP/HTTPS 둘 다 지원
 *
 * 주의: 로그인은 LoginPage에서 처리하고, 이 클래스는 토큰 관리만 담당
 */
class AuthTokenManager(private val context: Context) {

    companion object {
        private const val PREF_NAME = "auth_tokens"
        private const val KEY_ACCESS_TOKEN = "access_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_TOKEN_EXPIRY = "token_expiry"
        const val BASE_URL = "https://www.downbit.net"
        const val HTTP_BASE_URL = "http://www.downbit.net"  // HTTP용 BASE_URL 추가
    }

    private val sharedPrefs: SharedPreferences =
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    private val httpClient = OkHttpClient()

    // 토큰 갱신 중 중복 요청 방지를 위한 Mutex
    private val refreshMutex = Mutex()

    // 현재 갱신 중인지 체크하는 플래그
    private var isRefreshing = false

    /**
     * addTokenToRequestBody 함수에도 디버깅 추가
     */
    private fun addTokenToRequestBody(originalBody: String?, token: String): String {
        android.util.Log.d("AuthTokenManager", "🔧 토큰을 요청 본문에 추가")
        android.util.Log.d("AuthTokenManager", "  - 원본 본문 크기: ${originalBody?.length ?: 0}")
        android.util.Log.d("AuthTokenManager", "  - 토큰 길이: ${token.length}")

        val result = if (originalBody.isNullOrEmpty()) {
            android.util.Log.d("AuthTokenManager", "  - 빈 본문에 토큰만 추가")
            JSONObject().apply {
                put("token", token)
            }.toString()
        } else {
            try {
                android.util.Log.d("AuthTokenManager", "  - 기존 JSON에 토큰 추가 시도")
                val jsonObject = JSONObject(originalBody)
                jsonObject.put("token", token)
                val result = jsonObject.toString()
                android.util.Log.d("AuthTokenManager", "  - JSON 토큰 추가 성공")
                result
            } catch (e: Exception) {
                android.util.Log.w("AuthTokenManager", "  - JSON 파싱 실패, 새 객체 생성: ${e.message}")
                JSONObject().apply {
                    put("token", token)
                    put("originalBody", originalBody)
                }.toString()
            }
        }

        android.util.Log.d("AuthTokenManager", "  - 최종 본문 크기: ${result.length}")
        android.util.Log.d("AuthTokenManager", "  - 최종 본문 시작: ${result.take(100)}...")

        return result
    }

    /**
     * 토큰 갱신 결과를 나타내는 enum
     */
    enum class TokenRefreshResult {
        SUCCESS,           // 갱신 성공
        REFRESH_EXPIRED,   // Refresh token 만료 - 재로그인 필요
        NETWORK_ERROR,     // 네트워크 오류 - 재시도 가능
        SERVER_ERROR       // 서버 오류
    }

    /**
     * 로그인 성공 후 토큰들을 저장하는 메서드
     * LoginPage에서 로그인 성공 시 호출
     */
    fun saveTokens(accessToken: String, refreshToken: String, expiresIn: Long) {
        val expiryTime = System.currentTimeMillis() + (expiresIn * 1000)

        sharedPrefs.edit().apply {
            putString(KEY_ACCESS_TOKEN, accessToken)
            putString(KEY_REFRESH_TOKEN, refreshToken)
            putLong(KEY_TOKEN_EXPIRY, expiryTime)
            apply()
        }
    }

    /**
     * 유효한 Access Token 가져오기
     * 401 오류는 makeAuthenticatedRequest에서만 처리하도록 변경
     */
    suspend fun getValidAccessToken(): String? {
        return refreshMutex.withLock {
            val currentAccessToken = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)

            // 토큰이 없으면 바로 null 반환
            if (currentAccessToken == null) {
                return@withLock null
            }

            // 미리 갱신하지 않고 기존 토큰 그대로 반환
            // 401 에러가 나면 makeAuthenticatedRequest에서 처리
            currentAccessToken
        }
    }

    /**
     * URL이 유효한 downbit.net URL인지 확인 (HTTP/HTTPS 둘 다 허용)
     */
    private fun isValidDownbitUrl(url: String): Boolean {
        return url.startsWith("https://www.downbit.net") ||
                url.startsWith("http://www.downbit.net")
    }

    /**
     * 인증이 필요한 API 요청을 보내는 통합 메서드
     * 401 시에만 토큰 갱신하여 중복 요청 방지
     * HTTP/HTTPS 둘 다 지원
     */
    suspend fun makeAuthenticatedRequest(
        url: String,
        method: String = "GET",
        requestBody: String? = null,
        retryCount: Int = 0
    ): Result<String> {
        android.util.Log.d("AuthTokenManager", "=== API 요청 시작 ===")
        android.util.Log.d("AuthTokenManager", "URL: $url")
        android.util.Log.d("AuthTokenManager", "Method: $method")
        android.util.Log.d("AuthTokenManager", "RetryCount: $retryCount")
        android.util.Log.d("AuthTokenManager", "isRefreshing: $isRefreshing")

        // 요청 본문 크기 로깅 (보안상 내용은 일부만)
        if (requestBody != null) {
            android.util.Log.d("AuthTokenManager", "📤 요청 본문 정보:")
            android.util.Log.d("AuthTokenManager", "  - 크기: ${requestBody.length} 문자")
            android.util.Log.d("AuthTokenManager", "  - 시작 부분: ${requestBody.take(100)}...")

            // E2E 암호화된 요청인지 확인
            if (requestBody.contains("e2edata")) {
                android.util.Log.d("AuthTokenManager", "  - E2E 암호화 요청 감지")
            }
        }

        // URL 검증 - HTTP/HTTPS 둘 다 허용
        if (!isValidDownbitUrl(url)) {
            android.util.Log.e("AuthTokenManager", "유효하지 않은 URL: $url")
            return Result.failure(Exception("유효하지 않은 URL입니다."))
        }

        return withContext(Dispatchers.IO) {
            try {
                // Access Token 가져오기
                val accessToken = getValidAccessToken()
                    ?: return@withContext Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다."))

                android.util.Log.d("AuthTokenManager", "✅ AccessToken 획득 성공")

                val requestBuilder = Request.Builder()
                    .url(url)

                // HTTP 메서드에 따른 요청 설정
                when (method.uppercase()) {
                    "GET" -> {
                        android.util.Log.d("AuthTokenManager", "🔧 GET 요청 준비")
                        val urlWithToken = if (url.contains("?")) {
                            "$url&token=$accessToken"
                        } else {
                            "$url?token=$accessToken"
                        }
                        android.util.Log.d("AuthTokenManager", "  - 토큰이 추가된 URL: ${urlWithToken.take(100)}...")
                        requestBuilder.url(urlWithToken).get()
                    }
                    "POST" -> {
                        android.util.Log.d("AuthTokenManager", "🔧 POST 요청 준비")
                        val bodyWithToken = addTokenToRequestBody(requestBody, accessToken)

                        android.util.Log.d("AuthTokenManager", "📝 토큰 추가된 본문:")
                        android.util.Log.d("AuthTokenManager", "  - 크기: ${bodyWithToken.length} 문자")
                        android.util.Log.d("AuthTokenManager", "  - 시작 부분: ${bodyWithToken.take(150)}...")

                        val body = bodyWithToken.toRequestBody("application/json".toMediaType())
                        requestBuilder.post(body)
                    }
                    "PUT" -> {
                        android.util.Log.d("AuthTokenManager", "🔧 PUT 요청 준비")
                        val bodyWithToken = addTokenToRequestBody(requestBody, accessToken)
                        val body = bodyWithToken.toRequestBody("application/json".toMediaType())
                        requestBuilder.put(body)
                    }
                    "PATCH" -> {
                        android.util.Log.d("AuthTokenManager", "🔧 PATCH 요청 준비")
                        val bodyWithToken = addTokenToRequestBody(requestBody, accessToken)
                        android.util.Log.d("AuthTokenManager", "토큰 추가된 Body: ${bodyWithToken.take(100)}...")
                        val body = bodyWithToken.toRequestBody("application/json".toMediaType())
                        requestBuilder.patch(body)
                    }
                    "DELETE" -> {
                        android.util.Log.d("AuthTokenManager", "🔧 DELETE 요청 준비")
                        val bodyWithToken = addTokenToRequestBody(null, accessToken)
                        val body = bodyWithToken.toRequestBody("application/json".toMediaType())
                        requestBuilder.delete(body)
                    }
                }

                val request = requestBuilder.build()
                android.util.Log.d("AuthTokenManager", "🌐 HTTP 요청 전송 시작...")
                android.util.Log.d("AuthTokenManager", "📋 최종 요청 정보:")
                android.util.Log.d("AuthTokenManager", "  - URL: ${request.url}")
                android.util.Log.d("AuthTokenManager", "  - Method: ${request.method}")
                android.util.Log.d("AuthTokenManager", "  - Headers: ${request.headers}")

                // 요청 본문 크기 확인
                val requestBodySize = request.body?.contentLength() ?: 0
                android.util.Log.d("AuthTokenManager", "  - Body 크기: $requestBodySize bytes")

                val response = httpClient.newCall(request).execute()

                // response 사용 후 자동으로 닫히도록 use 블록 사용
                response.use {
                    android.util.Log.d("AuthTokenManager", "📥 HTTP 응답 수신:")
                    android.util.Log.d("AuthTokenManager", "  - 상태 코드: ${response.code}")
                    android.util.Log.d("AuthTokenManager", "  - 상태 메시지: ${response.message}")
                    android.util.Log.d("AuthTokenManager", "  - 응답 헤더: ${response.headers}")

                    // 응답 본문 미리 읽기 (한 번만 읽을 수 있으므로)
                    val responseBodyString = response.body?.string() ?: ""
                    android.util.Log.d("AuthTokenManager", "  - 응답 본문 크기: ${responseBodyString.length}")
                    android.util.Log.d("AuthTokenManager", "  - 응답 본문 내용: ${responseBodyString.take(500)}...")

                    when (response.code) {
                        200, 201 -> {
                            android.util.Log.d("AuthTokenManager", "✅ API 요청 성공!")
                            Result.success(responseBodyString)
                        }
                        401 -> {
                            android.util.Log.w("AuthTokenManager", "⚠️ 401 에러 발생 - 토큰 만료")

                            // 401 에러 시에만 토큰 갱신 시도 (한 번만)
                            if (retryCount == 0) {
                                android.util.Log.d("AuthTokenManager", "🔄 토큰 갱신 시도 시작...")

                                // 전역적으로 토큰 갱신 중복 방지
                                val refreshResult = refreshMutex.withLock {
                                    if (isRefreshing) {
                                        android.util.Log.w("AuthTokenManager", "⏳ 다른 스레드가 이미 토큰 갱신 중 - 대기")
                                        // 갱신이 완료될 때까지 대기
                                        while (isRefreshing) {
                                            delay(100)
                                        }
                                        // 갱신 완료 후 다시 토큰 확인
                                        val newToken = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
                                        if (newToken != null) {
                                            TokenRefreshResult.SUCCESS
                                        } else {
                                            TokenRefreshResult.REFRESH_EXPIRED
                                        }
                                    } else {
                                        isRefreshing = true
                                        try {
                                            refreshTokens()
                                        } finally {
                                            isRefreshing = false
                                        }
                                    }
                                }

                                android.util.Log.d("AuthTokenManager", "🔄 토큰 갱신 결과: $refreshResult")

                                when (refreshResult) {
                                    TokenRefreshResult.SUCCESS -> {
                                        android.util.Log.d("AuthTokenManager", "✅ 토큰 갱신 성공 - 원래 요청 재시도")
                                        // 갱신 성공 - 원래 요청 재시도
                                        makeAuthenticatedRequest(url, method, requestBody, retryCount + 1)
                                    }
                                    TokenRefreshResult.REFRESH_EXPIRED -> {
                                        android.util.Log.e("AuthTokenManager", "❌ Refresh Token 만료")
                                        Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다."))
                                    }
                                    TokenRefreshResult.NETWORK_ERROR -> {
                                        android.util.Log.e("AuthTokenManager", "❌ 토큰 갱신 네트워크 오류")
                                        Result.failure(Exception("네트워크 연결을 확인하고 다시 시도해주세요."))
                                    }
                                    TokenRefreshResult.SERVER_ERROR -> {
                                        android.util.Log.e("AuthTokenManager", "❌ 토큰 갱신 서버 오류")
                                        Result.failure(Exception("서버 오류가 발생했습니다. 잠시 후 다시 시도해주세요."))
                                    }
                                }
                            } else {
                                android.util.Log.e("AuthTokenManager", "❌ 이미 재시도했는데 또 401 - 완전 만료")
                                Result.failure(Exception("세션이 만료되어 재로그인이 필요합니다."))
                            }
                        }
                        403 -> {
                            android.util.Log.e("AuthTokenManager", "❌ 403 권한 오류")
                            android.util.Log.e("AuthTokenManager", "  - 응답 내용: $responseBodyString")
                            Result.failure(Exception("접근 권한이 없습니다."))
                        }
                        405 -> {
                            android.util.Log.e("AuthTokenManager", "❌ 405 Method Not Allowed")
                            android.util.Log.e("AuthTokenManager", "  - URL: $url, Method: $method")
                            android.util.Log.e("AuthTokenManager", "  - 응답 내용: $responseBodyString")
                            Result.failure(Exception("지원하지 않는 HTTP 메소드입니다."))
                        }
                        in 400..499 -> {
                            android.util.Log.e("AuthTokenManager", "❌ 클라이언트 오류: ${response.code}")
                            android.util.Log.e("AuthTokenManager", "  - 상태 메시지: ${response.message}")
                            android.util.Log.e("AuthTokenManager", "  - 응답 내용: $responseBodyString")
                            Result.failure(Exception("요청 오류입니다: ${response.code} ${response.message}"))
                        }
                        in 500..599 -> {
                            android.util.Log.e("AuthTokenManager", "❌ 서버 오류: ${response.code}")
                            android.util.Log.e("AuthTokenManager", "  - 상태 메시지: ${response.message}")
                            android.util.Log.e("AuthTokenManager", "  - 응답 내용: $responseBodyString")
                            android.util.Log.e("AuthTokenManager", "  - 서버 응답 헤더: ${response.headers}")

                            // 서버 오류의 구체적인 내용 확인
                            if (responseBodyString.isNotEmpty()) {
                                try {
                                    val errorJson = JSONObject(responseBodyString)
                                    android.util.Log.e("AuthTokenManager", "  - 서버 에러 상세: ${errorJson.toString(2)}")
                                } catch (e: Exception) {
                                    android.util.Log.e("AuthTokenManager", "  - 서버 에러 원문: $responseBodyString")
                                }
                            }

                            Result.failure(Exception("서버 오류입니다. 잠시 후 다시 시도해주세요."))
                        }
                        else -> {
                            android.util.Log.e("AuthTokenManager", "❌ 기타 오류: ${response.code}")
                            android.util.Log.e("AuthTokenManager", "  - 상태 메시지: ${response.message}")
                            android.util.Log.e("AuthTokenManager", "  - 응답 내용: $responseBodyString")
                            Result.failure(Exception("API 요청 실패: ${response.code}"))
                        }
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("AuthTokenManager", "❌ 타임아웃 오류: ${e.message}")
                android.util.Log.e("AuthTokenManager", "  - URL: $url")
                android.util.Log.e("AuthTokenManager", "  - 요청 크기: ${requestBody?.length ?: 0}")
                Result.failure(Exception("요청 시간이 초과되었습니다. 다시 시도해주세요."))
            } catch (e: java.io.IOException) {
                android.util.Log.e("AuthTokenManager", "❌ IO 오류: ${e.message}")
                android.util.Log.e("AuthTokenManager", "  - 스택 트레이스: ${e.stackTraceToString()}")
                Result.failure(Exception("네트워크 연결을 확인해주세요."))
            } catch (e: Exception) {
                android.util.Log.e("AuthTokenManager", "❌ 일반 오류: ${e.message}")
                android.util.Log.e("AuthTokenManager", "  - 예외 타입: ${e::class.simpleName}")
                android.util.Log.e("AuthTokenManager", "  - 스택 트레이스: ${e.stackTraceToString()}")
                Result.failure(Exception("오류가 발생했습니다: ${e.message}"))
            }
        }
    }

    /**
     * Refresh Token으로 새로운 Access Token 발급받기
     */
    private suspend fun refreshTokens(): TokenRefreshResult {
        android.util.Log.d("AuthTokenManager", "🔄 실제 토큰 갱신 API 호출 시작")

        return withContext(Dispatchers.IO) {
            try {
                val refreshToken = sharedPrefs.getString(KEY_REFRESH_TOKEN, null)

                if (refreshToken == null) {
                    android.util.Log.e("AuthTokenManager", "Refresh Token이 없음")
                    return@withContext TokenRefreshResult.REFRESH_EXPIRED
                }

                android.util.Log.d("AuthTokenManager", "Refresh Token 확인 완료")

                val refreshJson = JSONObject().apply {
                    put("refreshToken", refreshToken)
                }

                val request = Request.Builder()
                    .url("$BASE_URL/auth/refresh")  // 토큰 갱신은 HTTPS 사용
                    .post(refreshJson.toString().toRequestBody("application/json".toMediaType()))
                    .build()

                android.util.Log.d("AuthTokenManager", "🚀 토큰 갱신 API 요청 전송: $BASE_URL/auth/refresh")

                val response = httpClient.newCall(request).execute()

                // response 사용 후 자동으로 닫히도록 use 블록 사용
                response.use {
                    android.util.Log.d("AuthTokenManager", "토큰 갱신 API 응답 코드: ${response.code}")

                    when (response.code) {
                        200 -> {
                            val responseBody = response.body?.string()
                            android.util.Log.d("AuthTokenManager", "토큰 갱신 성공! 응답 데이터: $responseBody")

                            val jsonResponse = JSONObject(responseBody ?: "")

                            val newAccessToken = jsonResponse.getString("accessToken")
                            val newRefreshToken = jsonResponse.optString("refreshToken", refreshToken)
                            // expiresIn이 없으면 기본값 3600초(1시간) 사용
                            val expiresIn = jsonResponse.optLong("expiresIn", 3600L)

                            android.util.Log.d("AuthTokenManager", "새 토큰 정보 - expiresIn: $expiresIn")
                            saveTokens(newAccessToken, newRefreshToken, expiresIn)
                            android.util.Log.d("AuthTokenManager", "✅ 새 토큰 저장 완료")
                            TokenRefreshResult.SUCCESS
                        }
                        401 -> {
                            android.util.Log.e("AuthTokenManager", "Refresh Token도 만료됨")
                            clearTokens()
                            TokenRefreshResult.REFRESH_EXPIRED
                        }
                        in 500..599 -> {
                            android.util.Log.e("AuthTokenManager", "토큰 갱신 서버 오류: ${response.code}")
                            TokenRefreshResult.SERVER_ERROR
                        }
                        else -> {
                            android.util.Log.e("AuthTokenManager", "토큰 갱신 기타 오류: ${response.code}")
                            TokenRefreshResult.SERVER_ERROR
                        }
                    }
                }
            } catch (e: java.net.SocketTimeoutException) {
                android.util.Log.e("AuthTokenManager", "토큰 갱신 타임아웃: ${e.message}")
                TokenRefreshResult.NETWORK_ERROR
            } catch (e: java.io.IOException) {
                android.util.Log.e("AuthTokenManager", "토큰 갱신 IO 오류: ${e.message}")
                TokenRefreshResult.NETWORK_ERROR
            } catch (e: Exception) {
                android.util.Log.e("AuthTokenManager", "토큰 갱신 일반 오류: ${e.message}")
                TokenRefreshResult.SERVER_ERROR
            }
        }
    }

    /**
     * 사용자 정보 가져오기 (예시 API)
     */
    suspend fun getUserInfo(): Result<String> {
        return makeAuthenticatedRequest("$BASE_URL/user/info")
    }

    /**
     * 게시글 목록 가져오기 (예시 API)
     */
    suspend fun getPosts(): Result<String> {
        return makeAuthenticatedRequest("$BASE_URL/posts")
    }

    /**
     * 새 게시글 작성 (예시 API)
     */
    suspend fun createPost(title: String, content: String): Result<String> {
        val postJson = JSONObject().apply {
            put("title", title)
            put("content", content)
        }
        return makeAuthenticatedRequest(
            "$BASE_URL/posts",
            "POST",
            postJson.toString()
        )
    }

    /**
     * 현재 로그인 상태 확인
     */
    fun isLoggedIn(): Boolean {
        return sharedPrefs.getString(KEY_ACCESS_TOKEN, null) != null &&
                sharedPrefs.getString(KEY_REFRESH_TOKEN, null) != null
    }

    /**
     * 저장된 토큰들 가져오기 (자동로그인 체크용)
     */
    fun getStoredTokens(): Pair<String?, String?> {
        val accessToken = sharedPrefs.getString(KEY_ACCESS_TOKEN, null)
        val refreshToken = sharedPrefs.getString(KEY_REFRESH_TOKEN, null)
        return Pair(accessToken, refreshToken)
    }

    /**
     * 토큰 만료 시간 확인
     */
    fun isTokenExpired(): Boolean {
        val expiryTime = sharedPrefs.getLong(KEY_TOKEN_EXPIRY, 0)
        return System.currentTimeMillis() >= expiryTime
    }

    /**
     * 로그아웃 - 모든 토큰 삭제
     */
    fun logout() {
        clearTokens()
    }

    /**
     * 토큰 삭제 (내부용)
     */
    private fun clearTokens() {
        sharedPrefs.edit().clear().apply()
    }
}