package com.example.myapplication
//myPage.kt
import AuthTokenManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.FloatingActionButton
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Scaffold
import androidx.compose.material.Text
import androidx.compose.material.TextButton
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.ExitToApp
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import org.json.JSONObject

// 마이페이지용 Base64 파일 데이터 클래스
data class ProfileImageDto(
    val fileName: String,
    val base64Data: String, // base64 encoded content
    val contentType: String // mimeType
)

// 프로필 업데이트 요청 데이터 클래스 (새로운 API 스펙)
data class MyPageUpdateRequest(
    val newNickname: String? = null, // 필수 아님
    val newPicture: ProfileImageDto? = null // 필수 아님
)

// 프로필 이미지 응답 데이터 클래스
data class ProfilePictureResponse(
    val fileName: String,
    val contentType: String,
    val base64Data: String
)

// MyPage용 API 응답 데이터 클래스 (API 스펙에 맞춤)
data class MyPageInfoResponse(
    val nickname: String?,
    val realName: String?,
    val username: String?,
    val phone: String?,
    val picture: ProfilePictureResponse? // 객체 형태로 변경
)

// 사용자 정보 데이터 클래스
data class UserProfile(
    val nickname: String = "",
    val password: String = "",
    val phoneNumber: String = "",
    val fullName: String = "",
    val email: String = "",
    val profileImageUri: Uri? = null
)

@Composable
fun MyPageScreen(context: Context = LocalContext.current) {
    var userProfile by remember { mutableStateOf(UserProfile()) }
    var isEditing by remember { mutableStateOf(false) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var selectedImageUri by remember { mutableStateOf<Uri?>(null) }

    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    // 프로필 이미지 선택 런처
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            selectedImageUri = it
            userProfile = userProfile.copy(profileImageUri = it)
            // URI를 Bitmap으로 변환
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val inputStream: InputStream? = context.contentResolver.openInputStream(it)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    withContext(Dispatchers.Main) {
                        profileBitmap = bitmap
                    }
                } catch (e: Exception) {
                    Log.e("MyPage", "이미지 로딩 실패", e)
                }
            }
        }
    }

    // AuthTokenManager를 사용한 사용자 정보 로드 함수
    fun loadUserInfo() {
        isLoading = true
        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d("MyPage", "AuthTokenManager로 사용자 정보 로드 시작")

                // AuthTokenManager를 통해 인증된 API 요청
                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/mypage/info",
                    method = "GET"
                )

                if (result.isSuccess) {
                    val responseData = result.getOrNull() ?: ""
                    val jsonResponse = JSONObject(responseData)

                    userProfile = UserProfile(
                        nickname = jsonResponse.optString("nickname", ""),
                        fullName = jsonResponse.optString("realName", ""),
                        email = jsonResponse.optString("username", ""),
                        phoneNumber = jsonResponse.optString("phone", ""),
                        password = "********" // 보안상 표시하지 않음
                    )

                    // 프로필 이미지 초기화
                    profileBitmap = null

                    // 프로필 이미지가 있다면 로드
                    val pictureObject = jsonResponse.optJSONObject("picture")
                    pictureObject?.let { picture ->
                        val base64Data = picture.optString("base64Data", "")
                        if (base64Data.isNotEmpty()) {
                            Log.d("MyPage", "프로필 이미지 로드 시작: ${picture.optString("fileName")}")
                            loadImageFromBase64(base64Data, context) { bitmap ->
                                profileBitmap = bitmap
                            }
                        }
                    }

                    Log.d("MyPage", "사용자 정보 로드 성공: ${userProfile.nickname}")
                } else {
                    // AuthTokenManager가 모든 갱신을 시도한 후 실패한 경우
                    val error = result.exceptionOrNull()
                    Log.e("MyPage", "사용자 정보 로드 실패: ${error?.message}")

                    if (error?.message?.contains("재로그인") == true) {
                        Toast.makeText(context, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                        navigateToLoginWithAuth(authManager, context)
                    } else {
                        Toast.makeText(context, "정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MyPage", "사용자 정보 로딩 실패", e)
            } finally {
                isLoading = false
            }
        }
    }

    // 초기 사용자 정보 로드
    LaunchedEffect(Unit) {
        loadUserInfo()
    }

    if (isLoading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator(color = Color(0xFFD32F2F))
        }
        return
    }

    Scaffold(
        topBar = {
            TopAppBar(
                backgroundColor = Color.White,
                elevation = 0.dp
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // 뒤로가기 버튼
                    IconButton(
                        onClick = {
                            if (context is androidx.activity.ComponentActivity) {
                                context.finish()
                            }
                        }
                    ) {
                        Icon(
                            Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color(0xFFD32F2F)
                        )
                    }

                    // 마이페이지 제목
                    Text(
                        "마이페이지",
                        fontSize = 26.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        }
    ) { paddingValues ->
        // 스크롤 가능한 콘텐츠 영역
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .verticalScroll(rememberScrollState())
        ) {
            // 프로필 이미지 섹션
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                // 편집 버튼을 완전히 독립적으로 오버레이 배치
                Box(modifier = Modifier.fillMaxWidth()) {
                    // 메인 프로필 콘텐츠
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(20.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Box(
                            contentAlignment = Alignment.BottomEnd
                        ) {
                            // 프로필 이미지
                            if (profileBitmap != null) {
                                Image(
                                    bitmap = profileBitmap!!.asImageBitmap(),
                                    contentDescription = "프로필 이미지",
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(Color.Gray),
                                    contentScale = ContentScale.Crop
                                )
                            } else {
                                Box(
                                    modifier = Modifier
                                        .size(100.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFE0E0E0)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        Icons.Default.Person,
                                        contentDescription = "기본 프로필",
                                        modifier = Modifier.size(50.dp),
                                        tint = Color.Gray
                                    )
                                }
                            }

                            // 편집 중일 때만 카메라 버튼 표시
                            if (isEditing) {
                                FloatingActionButton(
                                    onClick = { imagePickerLauncher.launch("image/*") },
                                    modifier = Modifier.size(32.dp),
                                    backgroundColor = Color(0xFFD32F2F)
                                ) {
                                    Icon(
                                        Icons.Default.Edit,
                                        contentDescription = "프로필 이미지 변경",
                                        modifier = Modifier.size(16.dp),
                                        tint = Color.White
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        Text(
                            userProfile.fullName.ifEmpty { "사용자" },
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Bold
                        )

                        // 닉네임 편집 가능
                        if (isEditing) {
                            OutlinedTextField(
                                value = userProfile.nickname,
                                onValueChange = { userProfile = userProfile.copy(nickname = it) },
                                label = { Text("닉네임") },
                                singleLine = true,
                                colors = TextFieldDefaults.outlinedTextFieldColors(
                                    focusedBorderColor = Color(0xFFD32F2F),
                                    cursorColor = Color(0xFFD32F2F)
                                ),
                                modifier = Modifier.padding(top = 8.dp)
                            )
                        } else {
                            Text(
                                userProfile.nickname.ifEmpty { "닉네임 없음" },
                                fontSize = 14.sp,
                                color = Color.Gray
                            )
                        }
                    }

                    // 편집 버튼 - 완전한 오버레이 방식
                    TextButton(
                        onClick = {
                            if (isEditing) {
                                // 변경사항 저장 - 닉네임과 프로필 이미지만
                                saveUserProfile(userProfile, selectedImageUri, authManager, context) {
                                    // 저장 완료 후 편집 모드 종료 및 데이터 새로고침
                                    isEditing = false
                                    selectedImageUri = null
                                    loadUserInfo()
                                }
                            } else {
                                isEditing = true
                            }
                        },
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(8.dp)
                            .wrapContentSize()
                    ) {
                        Text(
                            if (isEditing) "저장" else "편집",
                            color = Color(0xFFD32F2F),
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // 사용자 정보 섹션 (읽기 전용)
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                elevation = 4.dp,
                shape = RoundedCornerShape(12.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        "개인정보",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 성명 (읽기 전용)
                    ProfileField(
                        label = "성명",
                        value = userProfile.fullName,
                        isEditing = false,
                        onValueChange = { },
                        icon = Icons.Default.AccountCircle
                    )

                    // 이메일 (읽기 전용)
                    ProfileField(
                        label = "이메일",
                        value = userProfile.email,
                        isEditing = false,
                        onValueChange = { },
                        icon = Icons.Default.Email
                    )

                    // 전화번호 (읽기 전용)
                    ProfileField(
                        label = "전화번호",
                        value = userProfile.phoneNumber,
                        isEditing = false,
                        onValueChange = { },
                        icon = Icons.Default.Phone
                    )
                }
            }

            // 기타 옵션 (카드 디자인 제거, 평평하게)
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    "기타",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                // 비밀번호 변경
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // 비밀번호 변경 페이지로 이동
                            val intent = android.content.Intent(context, PasswordChangeActivity::class.java)
                            context.startActivity(intent)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Lock,
                        contentDescription = "비밀번호 변경",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        "비밀번호 변경",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 로그아웃
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            logout(authManager, context)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.ExitToApp,
                        contentDescription = "로그아웃",
                        tint = Color(0xFFD32F2F),
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        "로그아웃",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium
                    )
                }

                // 회원탈퇴
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            // DeleteAccount.kt의 deleteAccount 함수 호출
                            deleteAccount(context)
                        }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.DeleteForever,
                        contentDescription = "회원탈퇴",
                        tint = Color.Red,
                        modifier = Modifier.size(24.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    Text(
                        "회원탈퇴",
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Red
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))
        }
    }
}

@Composable
fun ProfileField(
    label: String,
    value: String,
    isEditing: Boolean,
    onValueChange: (String) -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    keyboardType: KeyboardType = KeyboardType.Text
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
    ) {
        // 라벨과 아이콘을 첫 번째 줄에
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                icon,
                contentDescription = label,
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Text(
                label,
                fontSize = 12.sp,
                color = Color.Gray,
                fontWeight = FontWeight.Medium
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        // 값을 두 번째 줄에 - 아이콘과 정렬되도록 들여쓰기
        Text(
            value.ifEmpty { "정보 없음" },
            fontSize = 14.sp,
            color = if (value.isEmpty()) Color.Gray else Color.Black,
            modifier = Modifier.padding(start = 32.dp)
        )
    }
}

// Base64 문자열에서 직접 Bitmap 로드하는 함수
private fun loadImageFromBase64(base64Data: String, context: Context, onLoaded: (Bitmap?) -> Unit) {
    CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d("MyPage", "Base64 이미지 로딩 시작, 데이터 길이: ${base64Data.length}")

            val bitmap = decodeBase64ToBitmap(base64Data)

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    Log.d("MyPage", "프로필 이미지 로딩 성공: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("MyPage", "프로필 이미지 로딩 실패")
                }
                onLoaded(bitmap)
            }
        } catch (e: Exception) {
            Log.e("MyPage", "Base64 이미지 로딩 실패: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onLoaded(null)
            }
        }
    }
}

// Base64 문자열을 Bitmap으로 변환하는 함수
private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
    return try {
        // Base64 문자열에서 불필요한 공백과 개행 문자 제거
        val cleanBase64 = base64String.replace("\\s+".toRegex(), "")

        Log.d("MyPage", "Base64 디코딩 시작, 정리된 데이터 길이: ${cleanBase64.length}")

        // Base64 디코딩
        val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        Log.d("MyPage", "Base64 디코딩 완료, 바이트 배열 크기: ${decodedBytes.size}")

        // 바이트 배열을 Bitmap으로 변환
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

        if (bitmap != null) {
            Log.d("MyPage", "Bitmap 변환 성공: ${bitmap.width}x${bitmap.height}")
        } else {
            Log.e("MyPage", "Bitmap 변환 실패: BitmapFactory.decodeByteArray 결과가 null")
        }

        bitmap
    } catch (e: IllegalArgumentException) {
        Log.e("MyPage", "Base64 디코딩 실패 - 잘못된 Base64 형식: ${e.message}")
        null
    } catch (e: Exception) {
        Log.e("MyPage", "Bitmap 변환 실패: ${e.message}", e)
        null
    }
}

// AuthTokenManager를 사용한 사용자 정보 저장 함수
private fun saveUserProfile(
    userProfile: UserProfile,
    selectedImageUri: Uri?,
    authManager: AuthTokenManager,
    context: Context,
    onComplete: () -> Unit
) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            Log.d("MyPage", "AuthTokenManager로 프로필 업데이트 시작")

            // 프로필 이미지를 ProfileImageDto로 변환 (선택적)
            var profileImageDto: ProfileImageDto? = null
            selectedImageUri?.let { uri ->
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val bitmap = BitmapFactory.decodeStream(inputStream)
                    val outputStream = java.io.ByteArrayOutputStream()
                    bitmap.compress(Bitmap.CompressFormat.JPEG, 80, outputStream)
                    val byteArray = outputStream.toByteArray()
                    val base64Data = android.util.Base64.encodeToString(byteArray, android.util.Base64.NO_WRAP)

                    // 파일명 생성 (현재 시간 기반)
                    val fileName = "profile_${System.currentTimeMillis()}.jpg"

                    profileImageDto = ProfileImageDto(
                        fileName = fileName,
                        base64Data = base64Data,
                        contentType = "image/jpeg"
                    )

                    Log.d("MyPage", "이미지 변환 성공 - 파일명: $fileName, 데이터 크기: ${base64Data.length}")
                } catch (e: Exception) {
                    Log.e("MyPage", "이미지 변환 실패", e)
                    Toast.makeText(context, "이미지 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            // API 요청 데이터 생성
            val updateRequest = MyPageUpdateRequest(
                newNickname = if (userProfile.nickname.isNotEmpty()) userProfile.nickname else null,
                newPicture = profileImageDto
            )

            // JSON으로 변환
            val requestJson = JSONObject().apply {
                updateRequest.newNickname?.let { put("newNickname", it) }
                updateRequest.newPicture?.let { picture ->
                    put("newPicture", JSONObject().apply {
                        put("fileName", picture.fileName)
                        put("base64Data", picture.base64Data)
                        put("contentType", picture.contentType)
                    })
                }
            }

            Log.d("MyPage", "프로필 업데이트 요청 데이터:")
            Log.d("MyPage", "- 닉네임: ${updateRequest.newNickname}")
            Log.d("MyPage", "- 프로필 이미지: ${if (updateRequest.newPicture != null) "있음 (${updateRequest.newPicture.fileName})" else "없음"}")

            // AuthTokenManager를 통해 인증된 API 요청
            val result = authManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.HTTP_BASE_URL}/mypage/edit",
                method = "PATCH",
                requestBody = requestJson.toString()
            )

            if (result.isSuccess) {
                Toast.makeText(context, "프로필이 저장되었습니다.", Toast.LENGTH_SHORT).show()
                Log.d("MyPage", "프로필 업데이트 성공 (AuthTokenManager)")
            } else {
                val error = result.exceptionOrNull()
                Log.e("MyPage", "프로필 업데이트 실패: ${error?.message}")

                if (error?.message?.contains("재로그인") == true) {
                    Toast.makeText(context, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                    navigateToLoginWithAuth(authManager, context)
                } else {
                    Toast.makeText(context, "저장에 실패했습니다.", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Toast.makeText(context, "저장 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("MyPage", "프로필 저장 실패", e)
        } finally {
            onComplete()
        }
    }
}

// AuthTokenManager를 사용한 로그아웃 함수
private fun logout(authManager: AuthTokenManager, context: Context) {
    Log.d("MyPage", "AuthTokenManager로 로그아웃 시작")

    // AuthTokenManager로 토큰 정리
    authManager.logout()

    // app_prefs도 정리 (기존 호환성 유지)
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPref.edit().apply {
        remove("access_token")
        remove("refresh_token")
        putBoolean("auto_login_enabled", false)
        apply()
    }

    // 로그인 페이지로 이동
    val intent = android.content.Intent(context, LoginPage::class.java)
    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)

    Toast.makeText(context, "로그아웃되었습니다.", Toast.LENGTH_SHORT).show()
    Log.d("MyPage", "로그아웃 및 자동 로그인 해제 완료")
}

// AuthTokenManager를 사용한 로그인 페이지 이동 함수
private fun navigateToLoginWithAuth(authManager: AuthTokenManager, context: Context) {
    Log.d("MyPage", "AuthTokenManager로 로그인 페이지 이동")

    // AuthTokenManager로 토큰 정리
    authManager.logout()

    // app_prefs도 정리
    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
    sharedPref.edit().apply {
        remove("access_token")
        remove("refresh_token")
        putBoolean("auto_login_enabled", false)
        apply()
    }

    val intent = android.content.Intent(context, LoginPage::class.java)
    intent.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
}