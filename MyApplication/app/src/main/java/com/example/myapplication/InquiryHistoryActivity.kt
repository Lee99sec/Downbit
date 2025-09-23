package com.example.myapplication

import AuthTokenManager
import SecureBaseActivity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Tab
import androidx.compose.material.TabRow
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class InquiryHistoryActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    InquiryHistoryScreen()
                }
            }
        }
    }
}

// API Response 데이터 클래스
data class QnaItem(
    val title: String,
    val answered: Boolean,  // 서버 응답에 맞춰 answered로 변경
    val qnaId: Long,
    val createdAt: String
)

data class QnaListResponse(
    val qnaList: List<QnaItem>
)

@Composable
fun InquiryHistoryScreen() {
    val context = LocalContext.current
    val redMain = Color(0xFFD32F2F)
    val greenComplete = Color(0xFF4CAF50)  // 답변완료용 초록색

    var allInquiries by remember { mutableStateOf<List<QnaItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(true) }
    var errorMessage by remember { mutableStateOf<String?>(null) }
    var selectedTabIndex by remember { mutableStateOf(0) }

    val tabTitles = listOf("답변대기", "답변완료")

    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    // 날짜 문자열을 Date로 변환하는 함수
    fun parseDateTime(dateTimeString: String): Date {
        return try {
            // ISO 8601 형식 파싱: "2025-08-03T23:28:36.89752"
            val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSS", Locale.getDefault())
            inputFormat.parse(dateTimeString) ?: Date(0)
        } catch (e: Exception) {
            try {
                // 다른 형식으로 시도 (밀리초 없는 경우)
                val inputFormat2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
                inputFormat2.parse(dateTimeString) ?: Date(0)
            } catch (e2: Exception) {
                // 파싱 실패시 현재 시간으로 설정
                Date()
            }
        }
    }

    // AuthTokenManager를 사용한 API 호출 함수
    fun loadInquiries() {
        isLoading = true
        errorMessage = null

        CoroutineScope(Dispatchers.Main).launch {
            try {
                Log.d("InquiryHistory", "AuthTokenManager로 문의내역 로드 시작")

                // AuthTokenManager를 통해 인증된 API 요청
                val result = authManager.makeAuthenticatedRequest(
                    url = "${AuthTokenManager.BASE_URL}/qna/list",
                    method = "GET"
                )

                if (result.isSuccess) {
                    val responseData = result.getOrNull() ?: ""
                    Log.d("InquiryHistory", "API 응답 데이터: $responseData")

                    val jsonResponse = JSONObject(responseData)
                    val qnaListArray = jsonResponse.getJSONArray("qnaList")

                    // JSON 배열을 QnaItem 리스트로 변환
                    val qnaList = mutableListOf<QnaItem>()
                    for (i in 0 until qnaListArray.length()) {
                        val qnaObject = qnaListArray.getJSONObject(i)
                        qnaList.add(
                            QnaItem(
                                title = qnaObject.getString("title"),
                                answered = qnaObject.getBoolean("answered"), // 필드명 수정
                                qnaId = qnaObject.getLong("qnaId"),
                                createdAt = qnaObject.getString("createdAt")
                            )
                        )
                    }

                    // 최신순 정렬 (createdAt 기준 내림차순)
                    allInquiries = qnaList.sortedByDescending {
                        parseDateTime(it.createdAt)
                    }

                    Log.d("InquiryHistory", "문의내역 로드 성공: ${allInquiries.size}개")
                } else {
                    // AuthTokenManager가 모든 갱신을 시도한 후 실패한 경우
                    val error = result.exceptionOrNull()
                    Log.e("InquiryHistory", "문의내역 로드 실패: ${error?.message}")

                    when {
                        error?.message?.contains("재로그인") == true -> {
                            errorMessage = "세션이 만료되었습니다. 다시 로그인해주세요."
                            // 로그인 페이지로 이동하는 로직 추가 가능
                        }
                        error?.message?.contains("권한") == true -> {
                            errorMessage = "접근 권한이 없습니다."
                        }
                        error?.message?.contains("네트워크") == true -> {
                            errorMessage = "네트워크 연결을 확인해주세요."
                        }
                        else -> {
                            errorMessage = "문의내역을 불러오는데 실패했습니다."
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("InquiryHistory", "문의내역 로딩 예외 발생", e)
                errorMessage = "오류가 발생했습니다: ${e.message}"
            } finally {
                isLoading = false
            }
        }
    }

    // 화면 진입 시 데이터 로드
    LaunchedEffect(Unit) {
        loadInquiries()
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // Top App Bar
        TopAppBar(
            title = {
                Text("문의 내역", color = Color.White)
            },
            backgroundColor = redMain,
            navigationIcon = {
                IconButton(onClick = {
                    // 뒤로가기 처리 - Activity 종료
                    if (context is ComponentActivity) {
                        context.finish()
                    }
                }) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                }
            }
        )

        // Tabs
        TabRow(selectedTabIndex = selectedTabIndex, backgroundColor = Color.White, contentColor = redMain) {
            tabTitles.forEachIndexed { index, title ->
                Tab(
                    selected = selectedTabIndex == index,
                    onClick = { selectedTabIndex = index },
                    text = {
                        Text(
                            text = title,
                            color = if (selectedTabIndex == index) redMain else Color.Gray,
                            fontWeight = if (selectedTabIndex == index) FontWeight.Bold else FontWeight.Normal
                        )
                    }
                )
            }
        }

        // 컨텐츠 영역
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                isLoading -> {
                    // 로딩 상태
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = redMain
                    )
                }

                errorMessage != null -> {
                    // 에러 상태
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage!!,
                            color = Color.Red,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadInquiries() },
                            colors = ButtonDefaults.buttonColors(backgroundColor = redMain)
                        ) {
                            Text("다시 시도", color = Color.White)
                        }
                    }
                }

                else -> {
                    // 데이터 표시 - 탭에 따른 필터링 (이미 최신순으로 정렬된 상태)
                    val filteredInquiries = when (selectedTabIndex) {
                        0 -> allInquiries.filter { !it.answered } // 답변대기 (answered = false)
                        1 -> allInquiries.filter { it.answered }  // 답변완료 (answered = true)
                        else -> allInquiries
                    }

                    if (filteredInquiries.isEmpty()) {
                        // 빈 목록 상태
                        Column(
                            modifier = Modifier.align(Alignment.Center),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = if (selectedTabIndex == 0) "답변 대기 중인 문의가 없습니다" else "답변 완료된 문의가 없습니다",
                                color = Color.Gray,
                                fontSize = 16.sp
                            )
                        }
                    } else {
                        // 문의 목록 표시
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp)
                        ) {
                            items(filteredInquiries) { item ->
                                InquiryItemCard(item = item, redMain = redMain, greenComplete = greenComplete)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun InquiryItemCard(item: QnaItem, redMain: Color, greenComplete: Color) {
    val context = LocalContext.current

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .clickable {
                // 상세페이지로 이동
                val intent = Intent(context, QnaDetailActivity::class.java)
                intent.putExtra("qnaId", item.qnaId)
                context.startActivity(intent)
            },
        elevation = 4.dp
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            // 제목과 상태
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )

                // 답변 상태 배지 - answered에 따라 색상과 텍스트 변경
                Card(
                    backgroundColor = if (item.answered) greenComplete else redMain,  // true면 초록, false면 빨강
                    modifier = Modifier.padding(start = 8.dp)
                ) {
                    Text(
                        text = if (item.answered) "답변완료" else "답변대기",  // true면 답변완료, false면 답변대기
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 작성일시
            Text(
                text = formatInquiryDateTime(item.createdAt),
                fontSize = 14.sp,
                color = Color.Gray
            )
        }
    }
}

// 문의 날짜 포맷팅 함수
private fun formatInquiryDateTime(dateTimeString: String): String {
    return try {
        // ISO 8601 형식 파싱: "2025-08-03T23:28:36.89752"
        val inputFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSSS", Locale.getDefault())
        val outputFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())

        val date = inputFormat.parse(dateTimeString)
        outputFormat.format(date ?: Date())
    } catch (e: Exception) {
        try {
            // 다른 형식으로 시도 (밀리초 없는 경우)
            val inputFormat2 = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("yyyy.MM.dd HH:mm", Locale.getDefault())

            val date = inputFormat2.parse(dateTimeString)
            outputFormat.format(date ?: Date())
        } catch (e2: Exception) {
            // 모든 파싱 실패시 원본 반환
            dateTimeString
        }
    }
}