package com.example.myapplication

import AuthTokenManager
import SecureBaseActivity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import coil.compose.AsyncImage
import coil.request.ImageRequest
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Locale

// 커스텀 빨간색 정의
val CustomRed = Color(0xFFD32F2F)

// 데이터 클래스 정의 (기존과 다른 이름으로 변경)
data class QnaFileInfo(
    val fileId: Long,
    val originalFileName: String,
    val storedFileName: String,
    val fileSize: Long,
    val contentType: String,
    val createdAt: String
)

data class QnaDetailInfo(
    val title: String,
    val content: String,
    val createdAt: String,
    val ansContent: String?,
    val files: List<QnaFileInfo>
)

class QnaDetailActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Intent에서 qnaId 받기
        val qnaId = intent.getLongExtra("qnaId", -1L)

        if (qnaId == -1L) {
            Toast.makeText(this, "문의 정보를 찾을 수 없습니다.", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                QnaDetailScreen(qnaId = qnaId)
            }
        }
    }
}

@Composable
fun QnaDetailScreen(qnaId: Long) {
    var qnaDetail by remember { mutableStateOf<QnaDetailInfo?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    // 화면 진입 시 데이터 로드
    LaunchedEffect(qnaId) {
        scope.launch {
            isLoading = true
            errorMessage = null

            try {
                Log.d("QnaDetail", "AuthTokenManager로 문의 상세 조회 시작: qnaId=$qnaId")

                // AuthTokenManager를 통해 인증된 API 요청
                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/qna/detail/$qnaId",
                    method = "GET"
                )

                if (result.isSuccess) {
                    val responseData = result.getOrNull() ?: ""
                    Log.d("QnaDetail", "API 응답 데이터: $responseData")

                    val jsonObject = JSONObject(responseData)

                    // 파일 배열 파싱
                    val filesArray = jsonObject.getJSONArray("files")
                    val files = mutableListOf<QnaFileInfo>()

                    for (i in 0 until filesArray.length()) {
                        val fileObj = filesArray.getJSONObject(i)
                        files.add(
                            QnaFileInfo(
                                fileId = fileObj.getLong("fileId"),
                                originalFileName = fileObj.getString("originalFileName"),
                                storedFileName = fileObj.getString("storedFileName"),
                                fileSize = fileObj.getLong("fileSize"),
                                contentType = fileObj.getString("contentType"),
                                createdAt = fileObj.getString("createdAt")
                            )
                        )
                    }

                    qnaDetail = QnaDetailInfo(
                        title = jsonObject.getString("title"),
                        content = jsonObject.getString("content"),
                        createdAt = jsonObject.getString("createdAt"),
                        ansContent = if (jsonObject.isNull("ansContent")) null else jsonObject.getString("ansContent"),
                        files = files
                    )

                    Log.d("QnaDetail", "문의 상세 조회 성공: 제목=${qnaDetail!!.title}, 파일수=${files.size}")
                } else {
                    // AuthTokenManager가 모든 갱신을 시도한 후 실패한 경우
                    val error = result.exceptionOrNull()
                    Log.e("QnaDetail", "문의 상세 조회 실패: ${error?.message}")

                    when {
                        error?.message?.contains("재로그인") == true -> {
                            errorMessage = "세션이 만료되었습니다. 다시 로그인해주세요."
                            navigateToLoginFromDetail(authManager, context)
                        }
                        error?.message?.contains("권한") == true -> {
                            errorMessage = "문의 조회 권한이 없습니다."
                        }
                        error?.message?.contains("네트워크") == true -> {
                            errorMessage = "네트워크 연결을 확인해주세요."
                        }
                        else -> {
                            errorMessage = "문의 내용을 불러올 수 없습니다."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("QnaDetail", "문의 상세 조회 예외 발생", e)
                errorMessage = "오류가 발생했습니다: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 상단 앱바
        TopAppBar(
            title = { Text("문의 상세", color = Color.White) },
            navigationIcon = {
                IconButton(onClick = {
                    (context as? ComponentActivity)?.finish()
                }) {
                    Icon(
                        imageVector = Icons.Default.ArrowBack,
                        contentDescription = "뒤로가기",
                        tint = Color.White
                    )
                }
            },
            backgroundColor = CustomRed
        )

        // 내용
        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(color = CustomRed)
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = {
                                scope.launch {
                                    isLoading = true
                                    errorMessage = null
                                    try {
                                        val result = authManager.makeAuthenticatedRequest(
                                            url = "${AuthTokenManager.BASE_URL}/qna/detail/$qnaId",
                                            method = "GET"
                                        )

                                        if (result.isSuccess) {
                                            val responseData = result.getOrNull() ?: ""
                                            val jsonObject = JSONObject(responseData)

                                            val filesArray = jsonObject.getJSONArray("files")
                                            val files = mutableListOf<QnaFileInfo>()

                                            for (i in 0 until filesArray.length()) {
                                                val fileObj = filesArray.getJSONObject(i)
                                                files.add(
                                                    QnaFileInfo(
                                                        fileId = fileObj.getLong("fileId"),
                                                        originalFileName = fileObj.getString("originalFileName"),
                                                        storedFileName = fileObj.getString("storedFileName"),
                                                        fileSize = fileObj.getLong("fileSize"),
                                                        contentType = fileObj.getString("contentType"),
                                                        createdAt = fileObj.getString("createdAt")
                                                    )
                                                )
                                            }

                                            qnaDetail = QnaDetailInfo(
                                                title = jsonObject.getString("title"),
                                                content = jsonObject.getString("content"),
                                                createdAt = jsonObject.getString("createdAt"),
                                                ansContent = if (jsonObject.isNull("ansContent")) null else jsonObject.getString("ansContent"),
                                                files = files
                                            )
                                        } else {
                                            errorMessage = "문의 내용을 불러올 수 없습니다."
                                        }
                                    } catch (e: Exception) {
                                        errorMessage = "오류가 발생했습니다."
                                    } finally {
                                        isLoading = false
                                    }
                                }
                            },
                            colors = ButtonDefaults.buttonColors(backgroundColor = CustomRed)
                        ) {
                            Text("다시 시도", color = Color.White)
                        }
                    }
                }
            }
            qnaDetail != null -> {
                QnaDetailContent(qnaDetail = qnaDetail!!, authManager = authManager)
            }
        }
    }
}

@Composable
fun QnaDetailContent(qnaDetail: QnaDetailInfo, authManager: AuthTokenManager) {
    var selectedImageFile by remember { mutableStateOf<QnaFileInfo?>(null) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // 문의 정보 (제목, 작성일시, 내용, 첨부파일을 하나로 통합)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // 제목과 작성일시를 한 줄로
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "문의 제목",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold,
                            color = CustomRed
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = qnaDetail.title,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Text(
                        text = formatQnaDateTime(qnaDetail.createdAt),
                        fontSize = 12.sp,
                        color = Color.Gray
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))

                // 구분선
                Divider(
                    color = Color.Gray.copy(alpha = 0.3f),
                    thickness = 1.dp
                )

                Spacer(modifier = Modifier.height(12.dp))

                // 내용
                Text(
                    text = "문의 내용",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CustomRed
                )
                Spacer(modifier = Modifier.height(4.dp))
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = 100.dp) // 최소 높이 설정
                ) {
                    Text(
                        text = qnaDetail.content,
                        fontSize = 14.sp,
                        lineHeight = 20.sp,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // 첨부파일 (파일이 있을 때만 표시)
                if (qnaDetail.files.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = "첨부파일 (${qnaDetail.files.size}개)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = CustomRed
                    )
                    Spacer(modifier = Modifier.height(4.dp))

                    qnaDetail.files.forEach { file ->
                        FileItem(
                            file = file,
                            authManager = authManager,
                            onImageClick = { selectedImageFile = file }
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                    }
                }
            }
        }

        // 답변 내용 (별도 카드)
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = 2.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                Text(
                    text = "답변 내용",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = CustomRed
                )
                Spacer(modifier = Modifier.height(8.dp))

                if (qnaDetail.ansContent != null) {
                    Text(
                        text = qnaDetail.ansContent,
                        fontSize = 14.sp,
                        lineHeight = 20.sp
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = Color.Gray.copy(alpha = 0.1f),
                                shape = RoundedCornerShape(4.dp)
                            )
                            .padding(16.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "답변 대기 중입니다.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }

    // 이미지 미리보기 다이얼로그
    selectedImageFile?.let { file ->
        ImagePreviewDialog(
            file = file,
            authManager = authManager,
            onDismiss = { selectedImageFile = null }
        )
    }
}

@Composable
fun FileItem(
    file: QnaFileInfo,
    authManager: AuthTokenManager,
    onImageClick: () -> Unit
) {
    val context = LocalContext.current
    val isImage = file.contentType.startsWith("image/")

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                if (isImage) {
                    onImageClick()
                } else {
                    downloadFile(context, file, authManager)
                }
            }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (isImage) Icons.Default.Image else Icons.Default.AttachFile,
            contentDescription = if (isImage) "이미지 파일" else "첨부파일",
            tint = if (isImage) Color.Blue else Color.Gray,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = file.originalFileName,
                fontSize = 12.sp,
                color = Color.DarkGray
            )
            Text(
                text = "${formatQnaFileSize(file.fileSize)} • ${file.contentType}",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        if (isImage) {
            Text(
                text = "미리보기",
                fontSize = 10.sp,
                color = Color.Blue,
                modifier = Modifier.padding(end = 4.dp)
            )
        } else {
            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "다운로드",
                tint = Color.Gray,
                modifier = Modifier.size(14.dp)
            )
        }
    }
}

@Composable
fun ImagePreviewDialog(
    file: QnaFileInfo,
    authManager: AuthTokenManager,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current

    // AuthTokenManager에서 토큰 가져오기 (동기적으로)
    val jwtToken = remember {
        authManager.getStoredTokens().first ?: ""
    }

    val imageUrl = "${AuthTokenManager.BASE_URL}/files/image/${file.fileId}?token=$jwtToken"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.9f))
                .clickable { onDismiss() }
        ) {
            // 닫기 버튼
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color.White,
                    modifier = Modifier.size(32.dp)
                )
            }

            // 이미지
            AsyncImage(
                model = ImageRequest.Builder(context)
                    .data(imageUrl)
                    .addHeader("Authorization", "Bearer $jwtToken")
                    .crossfade(true)
                    .build(),
                contentDescription = file.originalFileName,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(32.dp),
                contentScale = ContentScale.Fit
            )

            // 파일명 표시
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(16.dp),
                backgroundColor = Color.Black.copy(alpha = 0.7f)
            ) {
                Text(
                    text = file.originalFileName,
                    color = Color.White,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(16.dp)
                )
            }
        }
    }
}

// AuthTokenManager를 사용한 파일 다운로드 함수
fun downloadFile(context: Context, file: QnaFileInfo, authManager: AuthTokenManager) {
    try {
        val jwtToken = authManager.getStoredTokens().first
        if (jwtToken.isNullOrEmpty()) {
            Toast.makeText(context, "로그인이 필요합니다.", Toast.LENGTH_SHORT).show()
            return
        }

        // 파일 다운로드 URL 구성
        val downloadUrl = "${AuthTokenManager.BASE_URL}/files/download/${file.fileId}?token=$jwtToken"

        // 브라우저로 다운로드 URL 열기
        val intent = Intent(Intent.ACTION_VIEW, android.net.Uri.parse(downloadUrl))
        try {
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "파일을 열 수 없습니다.", Toast.LENGTH_SHORT).show()
            Log.e("FileDownload", "파일 다운로드 오류: ${e.message}")
        }

    } catch (e: Exception) {
        Toast.makeText(context, "파일 다운로드 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        Log.e("FileDownload", "파일 다운로드 오류: ${e.message}")
    }
}

// AuthTokenManager를 사용한 로그인 페이지 이동 함수
private fun navigateToLoginFromDetail(authManager: AuthTokenManager, context: Context) {
    Log.d("QnaDetail", "AuthTokenManager로 로그인 페이지 이동")

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

    val intent = Intent(context, LoginPage::class.java)
    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
    context.startActivity(intent)
}

// 날짜 시간 포맷팅 함수 (SimpleDateFormat으로 변경하여 호환성 개선)
private fun formatQnaDateTime(dateTimeString: String): String {
    return try {
        // ISO 8601 형식의 날짜를 파싱하기 위한 여러 패턴 시도
        val patterns = arrayOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        val outputFormat = SimpleDateFormat("yyyy년 MM월 dd일 HH:mm", Locale.KOREA)

        for (pattern in patterns) {
            try {
                val inputFormat = SimpleDateFormat(pattern, Locale.getDefault())
                val date = inputFormat.parse(dateTimeString)
                if (date != null) {
                    return outputFormat.format(date)
                }
            } catch (e: Exception) {
                // 다음 패턴 시도
                continue
            }
        }

        // 모든 패턴이 실패한 경우 원본 반환
        dateTimeString
    } catch (e: Exception) {
        Log.e("DateFormat", "날짜 포맷 오류: ${e.message}")
        dateTimeString
    }
}

// 파일 크기 포맷팅 함수 (소수점 포함하여 더 정확한 표시)
private fun formatQnaFileSize(bytes: Long): String {
    return when {
        bytes >= 1024 * 1024 * 1024 -> String.format("%.1fGB", bytes / (1024.0 * 1024.0 * 1024.0))
        bytes >= 1024 * 1024 -> String.format("%.1fMB", bytes / (1024.0 * 1024.0))
        bytes >= 1024 -> String.format("%.1fKB", bytes / 1024.0)
        else -> "${bytes}B"
    }
}