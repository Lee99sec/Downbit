package com.example.myapplication

import SecureBaseActivity
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class NoticeActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NoticeListScreen()
                }
            }
        }
    }
}

data class Notice(
    val id: Int,
    val title: String,
    val date: String,
    val createdAt: String,
    val isNew: Boolean = false
)

@Composable
fun NoticeListScreen() {
    val redMain = Color(0xFFD32F2F)
    val activity = LocalContext.current as? ComponentActivity
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var notices by remember { mutableStateOf(listOf<Notice>()) }
    var readNotices by remember { mutableStateOf(setOf<Int>()) }
    var isLoading by remember { mutableStateOf(true) }

    // 공지사항 데이터 로드
    LaunchedEffect(Unit) {
        NoticeReadManager.init(context)
        activity?.lifecycleScope?.launch {
            try {
                val fetchedNotices = loadNoticesFromApi()
                Log.d("NoticeActivity", "로드된 공지사항 개수: ${fetchedNotices.size}")
                fetchedNotices.forEach { notice ->
                    Log.d("NoticeActivity", "공지: ID=${notice.id}, 제목=${notice.title}, 날짜=${notice.createdAt}")
                }

                notices = fetchedNotices

                // 🧹 3일 지난 공지사항의 읽음 상태 정리
                val oldNoticeIds = fetchedNotices
                    .filter { !NoticeUtils.isWithin3Days(it.createdAt) } // 3일 지난 공지사항들
                    .map { it.id } // ID만 추출

                if (oldNoticeIds.isNotEmpty()) {
                    Log.d("NoticeActivity", "3일 지난 공지사항 정리: $oldNoticeIds")
                    NoticeReadManager.cleanupOldNotices(oldNoticeIds)
                }

                // 읽은 공지사항들 로드
                val readIds = mutableSetOf<Int>()
                fetchedNotices.forEach { notice ->
                    if (NoticeReadManager.isRead(notice.id)) {
                        readIds.add(notice.id)
                    }
                }
                readNotices = readIds
            } catch (e: Exception) {
                Log.e("NoticeActivity", "공지사항 로드 실패", e)
            } finally {
                isLoading = false
            }
        }
    }

    Column(modifier = Modifier.padding(16.dp)) {
        // 헤더
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = "뒤로가기",
                tint = Color.Gray,
                modifier = Modifier
                    .size(24.dp)
                    .clickable { activity?.finish() }
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text("공지사항", fontSize = 24.sp, color = redMain, fontWeight = FontWeight.Bold)
        }

        // 검색창
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            label = { Text("공지 검색", color = redMain) },
            trailingIcon = {
                Icon(Icons.Default.Search, contentDescription = "검색", tint = redMain)
            },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Text),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            colors = TextFieldDefaults.outlinedTextFieldColors(
                focusedBorderColor = redMain,
                cursorColor = redMain
            )
        )

        // 로딩 또는 공지사항 리스트
        if (isLoading) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = redMain)
            }
        } else {
            // 검색된 공지사항들
            val filteredNotices = notices.filter {
                it.title.contains(searchQuery, ignoreCase = true)
            }

            LazyColumn {
                items(filteredNotices) { notice ->
                    NoticeItemCard(
                        notice = notice,
                        redMain = redMain,
                        showNewBadge = notice.isNew &&
                                !readNotices.contains(notice.id) &&
                                NoticeUtils.isWithin3Days(notice.createdAt),
                        onClick = {
                            // 읽음 처리
                            NoticeReadManager.markAsRead(notice.id)
                            readNotices = readNotices + notice.id

                            // 상세페이지로 이동
                            val intent = android.content.Intent(activity, NoticeDetailActivity::class.java).apply {
                                putExtra("noticeId", notice.id)
                                putExtra("noticeTitle", notice.title)
                                putExtra("noticeDate", notice.date)
                            }
                            activity?.startActivity(intent)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun NoticeItemCard(
    notice: Notice,
    redMain: Color,
    showNewBadge: Boolean,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 8.dp)
    ) {
        // 첫 번째 줄: NEW 뱃지 + 제목
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.fillMaxWidth()
        ) {
            // NEW 뱃지
            if (showNewBadge) {
                Text(
                    text = "NEW",
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(redMain, RoundedCornerShape(4.dp))
                        .padding(horizontal = 6.dp, vertical = 2.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
            }

            // 제목 (전체 너비 사용)
            Text(
                text = notice.title,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // 두 번째 줄: 날짜 (우측 정렬)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.End
        ) {
            Text(
                text = notice.date,
                fontSize = 12.sp,
                color = Color.Gray
            )
        }

        // 구분선
        Divider(
            color = Color.LightGray.copy(alpha = 0.4f),
            thickness = 1.dp,
            modifier = Modifier.padding(top = 8.dp)
        )
    }
}

// 🔥 새로 작성한 API 호출 함수 - 확실히 최신순 정렬
suspend fun loadNoticesFromApi(): List<Notice> {
    return withContext(Dispatchers.IO) {
        try {
            val url = URL("${RetrofitClient.BASE_URL}/notice/all")
            val connection = url.openConnection() as HttpURLConnection

            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            if (connection.responseCode == 200) {
                val responseText = connection.inputStream.bufferedReader().readText()
                Log.d("NoticeActivity", "API 응답: $responseText")

                val jsonObject = JSONObject(responseText)
                val jsonArray = jsonObject.getJSONArray("noticeList")

                val noticeList = mutableListOf<Notice>()

                // JSON 배열에서 데이터 추출
                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val noticeId = item.getInt("noticeId")
                    val title = item.getString("title")
                    val createdAt = item.getString("createdAt")
                    val date = createdAt.substring(0, 10) // yyyy-MM-dd

                    // 진짜 새로운 공지사항만 NEW 표시하도록
                    val isNewNotice = NoticeUtils.isWithin3Days(createdAt)
                    noticeList.add(Notice(noticeId, title, date, createdAt, isNewNotice))
                }

                // 🔥🔥🔥 createdAt 날짜 기준 내림차순 정렬 (최신 날짜가 위로)
                val sortedList = noticeList.sortedByDescending { it.createdAt }

                Log.d("NoticeActivity", "정렬 전 첫 번째: ${noticeList.firstOrNull()?.createdAt}")
                Log.d("NoticeActivity", "정렬 후 첫 번째: ${sortedList.firstOrNull()?.createdAt}")

                connection.disconnect()
                return@withContext sortedList

            } else {
                Log.e("NoticeActivity", "API 호출 실패: ${connection.responseCode}")
                connection.disconnect()
                return@withContext emptyList()
            }

        } catch (e: Exception) {
            Log.e("NoticeActivity", "API 호출 중 예외 발생", e)
            return@withContext emptyList()
        }
    }
}