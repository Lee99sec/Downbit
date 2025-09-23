package com.example.myapplication

import SecureBaseActivity
import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.compose.setContent
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Download
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NoticeDetailActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val noticeId = intent.getIntExtra("noticeId", -1)
        val noticeTitle = intent.getStringExtra("noticeTitle") ?: ""
        val noticeDate = intent.getStringExtra("noticeDate") ?: ""

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoticeDetailScreen(
                        noticeId = noticeId,
                        noticeTitle = noticeTitle,
                        noticeDate = noticeDate
                    )
                }
            }
        }
    }

    // DownloadManager를 사용한 파일 다운로드 함수s
    fun downloadFileWithDownloadManager(fileId: Int, fileName: String) {
        try {
            val downloadUrl = "${RetrofitClient.BASE_URL}/notice-files/download/$fileId"

            val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
                setTitle("파일 다운로드")
                setDescription("$fileName 다운로드 중...")
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, fileName)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)

                // 추가 헤더 설정 (필요시)
                // addRequestHeader("Authorization", "Bearer your-token")
            }

            val downloadManager = getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            Toast.makeText(this, "다운로드를 시작합니다.", Toast.LENGTH_SHORT).show()

            println("✅ DownloadManager로 다운로드 시작: $fileName (Download ID: $downloadId)")
            println("🌐 다운로드 URL: $downloadUrl")

        } catch (e: Exception) {
            Toast.makeText(this, "다운로드 실패: ${e.message}", Toast.LENGTH_SHORT).show()
            println("💥 DownloadManager 다운로드 오류: ${e.message}")
            e.printStackTrace()
        }
    }
}

// 파일 정보 데이터 클래스
data class NoticeFileInfo(
    val fileId: Int,
    val originalFileName: String,
    val fileSize: Long,
    val contentType: String,
    val createdAt: String
)

data class NoticeDetail(
    val title: String,
    val content: String,
    val createdAt: String,
    val updatedAt: String? = null,
    val author: String? = null,
    val fileInfo: NoticeFileInfo? = null
)

@Composable
fun NoticeDetailScreen(
    noticeId: Int,
    noticeTitle: String,
    noticeDate: String
) {
    val redMain = Color(0xFFD32F2F)
    val lightGray = Color(0xFFF5F5F5)
    val activity = LocalContext.current as? NoticeDetailActivity
    var noticeDetail by remember { mutableStateOf<NoticeDetail?>(null) }
    var isLoading by remember { mutableStateOf(true) }

    LaunchedEffect(noticeId) {
        (activity)?.lifecycleScope?.launch {
            // 임시 테스트용 더미 데이터
            if (noticeId == -1) {
                noticeDetail = NoticeDetail(
                    title = "테스트 공지사항",
                    content = "<p>이것은 <strong>테스트</strong> 내용입니다.</p><ul><li>첫 번째 항목</li><li>두 번째 항목</li></ul>",
                    createdAt = "2025-08-08T12:00:00",
                    updatedAt = "2025-08-08T12:00:00",
                    author = "관리자",
                    fileInfo = NoticeFileInfo(
                        fileId = 82,
                        originalFileName = "테스트파일.pdf",
                        fileSize = 64080,
                        contentType = "application/pdf",
                        createdAt = "2025-08-08T12:00:00"
                    )
                )
                isLoading = false
                return@launch
            }

            val detail = fetchNoticeDetailFromApi(noticeId)
            noticeDetail = detail
            isLoading = false
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.White)
    ) {
        // 상단 헤더
        TopAppBar(
            backgroundColor = Color.White,
            elevation = 2.dp,
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color.Gray,
                    modifier = Modifier
                        .size(24.dp)
                        .clickable {
                            activity?.finish()
                        }
                )
                Spacer(modifier = Modifier.width(16.dp))
                Text(
                    "공지사항",
                    fontSize = 20.sp,
                    color = redMain,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = redMain)
            }
        } else {
            noticeDetail?.let { detail ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp)
                ) {
                    // 공지사항 카드
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            // 헤더 영역
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = detail.title,
                                        fontSize = 22.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.Black,
                                        lineHeight = 28.sp
                                    )
                                }

                                Surface(
                                    color = redMain.copy(alpha = 0.1f),
                                    shape = RoundedCornerShape(20.dp)
                                ) {
                                    Text(
                                        text = "공지사항",
                                        fontSize = 12.sp,
                                        color = redMain,
                                        fontWeight = FontWeight.Medium,
                                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            // 메타 정보
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                detail.author?.let { author ->
                                    Text(
                                        text = "작성자: $author",
                                        fontSize = 14.sp,
                                        color = Color.Gray,
                                        fontWeight = FontWeight.Medium
                                    )
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                Text(
                                    text = "작성일: ${formatDateTime(detail.createdAt)}",
                                    fontSize = 14.sp,
                                    color = Color.Gray
                                )
                            }

                            detail.updatedAt?.let { updatedAt ->
                                if (updatedAt != detail.createdAt) {
                                    Text(
                                        text = "최종 수정: ${formatDateTime(updatedAt)}",
                                        fontSize = 12.sp,
                                        color = Color.Gray,
                                        modifier = Modifier.padding(top = 4.dp)
                                    )
                                }
                            }

                            Divider(
                                color = redMain.copy(alpha = 0.2f),
                                thickness = 1.dp,
                                modifier = Modifier.padding(vertical = 20.dp)
                            )

                            // 내용 영역 - HTML 렌더링 (WebView)
                            HtmlContentWebView(
                                htmlContent = detail.content,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    // 첨부파일 영역 (WebView 아래에 위치)
                    Spacer(modifier = Modifier.height(16.dp))

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        elevation = 4.dp,
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Column(
                            modifier = Modifier.padding(20.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(bottom = 16.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.AttachFile,
                                    contentDescription = "첨부파일",
                                    tint = redMain,
                                    modifier = Modifier.size(20.dp)
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "첨부파일 (${if (detail.fileInfo != null) 1 else 0}개)",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.Black
                                )
                            }

                            if (detail.fileInfo != null) {
                                NoticeAttachmentItem(
                                    fileInfo = detail.fileInfo,
                                    onDownloadClick = { fileId ->
                                        // DownloadManager를 사용한 파일 다운로드
                                        activity?.downloadFileWithDownloadManager(
                                            fileId,
                                            detail.fileInfo.originalFileName
                                        )
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                )
                            } else {
                                // 첨부파일이 없을 때 표시할 메시지
                                Text(
                                    text = "첨부파일이 없습니다.",
                                    fontSize = 14.sp,
                                    color = Color.Gray,
                                    modifier = Modifier.padding(vertical = 8.dp)
                                )
                            }
                        }
                    }

                    // 안내 메시지
                    Spacer(modifier = Modifier.height(16.dp))

                    Surface(
                        color = lightGray,
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "이 공지사항은 모든 사용자에게 중요한 내용입니다.\n문의사항이 있으시면 고객센터로 연락해주세요.",
                            fontSize = 14.sp,
                            color = Color.Gray,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(16.dp),
                            lineHeight = 20.sp
                        )
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "공지사항을 불러올 수 없습니다.",
                            fontSize = 16.sp,
                            color = Color.Gray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "네트워크 연결을 확인하고 다시 시도해주세요.",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HtmlContentWebView(
    htmlContent: String,
    modifier: Modifier = Modifier
) {
    var webViewHeight by remember { mutableStateOf(520.dp) }

    AndroidView(
        factory = { context ->
            WebView(context).apply {
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // 높이 측정 코드...
                    }
                }

                settings.apply {
                    javaScriptEnabled = true
                    defaultFontSize = 16

                    // 🔴 추가해야 할 중요한 설정들
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    javaScriptCanOpenWindowsAutomatically = true

                    // Android 5.0 이상에서 필요
                    if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.LOLLIPOP) {
                        mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    }
                }

                // WebChromeClient 추가 (콘솔 로그 확인용)
                webChromeClient = object : android.webkit.WebChromeClient() {
                    override fun onConsoleMessage(consoleMessage: android.webkit.ConsoleMessage?): Boolean {
                        android.util.Log.d("WebView", "${consoleMessage?.message()}")
                        return super.onConsoleMessage(consoleMessage)
                    }
                }

                val styledHtml = """
                    <html>
                    <head>
                        <meta name="viewport" content="width=device-width, initial-scale=1.0">
                        <meta http-equiv="Content-Security-Policy" content="default-src * 'unsafe-inline' 'unsafe-eval'; script-src * 'unsafe-inline' 'unsafe-eval'; img-src * data:;">
                        <style>
                            /* 기존 스타일... */
                        </style>
                    </head>
                    <body>
                        $htmlContent
                    </body>
                    </html>
                """.trimIndent()

                loadDataWithBaseURL(null, styledHtml, "text/html", "UTF-8", null)
            }
        },
        modifier = modifier.height(webViewHeight)
    )
}

@Composable
fun NoticeAttachmentItem(
    fileInfo: NoticeFileInfo,
    onDownloadClick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        color = Color(0xFFF5F5F5),
        shape = RoundedCornerShape(8.dp),
        modifier = modifier.clickable {
            onDownloadClick(fileInfo.fileId)
        }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = "파일",
                tint = Color.Gray,
                modifier = Modifier.size(20.dp)
            )

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = fileInfo.originalFileName,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.Black,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = "${formatFileSize(fileInfo.fileSize)} • ${formatContentType(fileInfo.contentType)}",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
            }

            Icon(
                imageVector = Icons.Default.Download,
                contentDescription = "다운로드",
                tint = Color(0xFFD32F2F),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

// API 파싱 함수
suspend fun fetchNoticeDetailFromApi(noticeId: Int): NoticeDetail? {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("${RetrofitClient.BASE_URL}/notice/$noticeId")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 10000
            connection.readTimeout = 10000
            connection.setRequestProperty("Accept", "application/json")
            connection.setRequestProperty("Content-Type", "application/json")

            println("🌐 API 요청: $url")
            println("📡 응답 코드: ${connection.responseCode}")

            when (connection.responseCode) {
                200 -> {
                    val stream = connection.inputStream.bufferedReader().readText()
                    println("📦 API 응답: $stream")

                    val jsonObject = JSONObject(stream)

                    // 필수 필드 확인
                    val title = jsonObject.optString("title", "제목 없음")
                    val content = jsonObject.optString("content", "내용 없음")
                    val createdAt = jsonObject.optString("createdAt", "")
                    val updatedAt = jsonObject.optString("updatedAt", null)
                    val author = jsonObject.optString("author", "관리자")

                    // fileInfo 객체 파싱
                    var fileInfo: NoticeFileInfo? = null
                    val fileInfoObj = jsonObject.optJSONObject("fileInfo")
                    if (fileInfoObj != null) {
                        try {
                            fileInfo = NoticeFileInfo(
                                fileId = fileInfoObj.optInt("fileId", 0),
                                originalFileName = fileInfoObj.optString("originalFileName", "파일"),
                                fileSize = fileInfoObj.optLong("fileSize", 0),
                                contentType = fileInfoObj.optString("contentType", "application/octet-stream"),
                                createdAt = fileInfoObj.optString("createdAt", "")
                            )
                        } catch (e: Exception) {
                            println("⚠️ 첨부파일 파싱 오류: ${e.message}")
                        }
                    }

                    println("✅ 파싱 성공 - 제목: $title, 첨부파일: ${if (fileInfo != null) "있음" else "없음"}")
                    NoticeDetail(title, content, createdAt, updatedAt, author, fileInfo)
                }
                404 -> {
                    println("❌ 공지사항을 찾을 수 없습니다 (404)")
                    null
                }
                else -> {
                    println("❌ 서버 오류: ${connection.responseCode}")
                    val errorStream = connection.errorStream?.bufferedReader()?.readText()
                    println("📄 오류 내용: $errorStream")
                    null
                }
            }
        } catch (e: Exception) {
            println("💥 API 호출 실패: ${e.message}")
            e.printStackTrace()
            null
        }
    }
}

// 파일 크기 포맷팅 함수
private fun formatFileSize(sizeInBytes: Long): String {
    return when {
        sizeInBytes < 1024 -> "$sizeInBytes B"
        sizeInBytes < 1024 * 1024 -> "${String.format("%.1f", sizeInBytes / 1024.0)} KB"
        sizeInBytes < 1024 * 1024 * 1024 -> "${String.format("%.1f", sizeInBytes / (1024.0 * 1024))} MB"
        else -> "${String.format("%.1f", sizeInBytes / (1024.0 * 1024 * 1024))} GB"
    }
}

// 콘텐츠 타입 포맷팅 함수
private fun formatContentType(contentType: String): String {
    return when {
        contentType.contains("pdf") -> "PDF"
        contentType.contains("image") -> "이미지"
        contentType.contains("text") -> "텍스트"
        contentType.contains("video") -> "비디오"
        contentType.contains("audio") -> "오디오"
        contentType.contains("zip") || contentType.contains("rar") -> "압축파일"
        contentType.contains("word") || contentType.contains("document") -> "문서"
        contentType.contains("excel") || contentType.contains("spreadsheet") -> "스프레드시트"
        contentType.contains("powerpoint") || contentType.contains("presentation") -> "프레젠테이션"
        else -> contentType.substringAfterLast("/").uppercase()
    }
}

// 날짜 포맷팅 함수
private fun formatDateTime(dateTimeString: String): String {
    return try {
        val dateTime = dateTimeString.substring(0, 16).replace("T", " ")
        dateTime
    } catch (e: Exception) {
        dateTimeString
    }
}