package com.example.myapplication

import AuthTokenManager
import SecureBaseActivity
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Divider
import androidx.compose.material.Icon
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Person
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.system.exitProcess

class MoreActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    MoreScreen()
                }
            }
        }
    }
}

data class RecentNotice(val noticeId: Int, val title: String, val createdAt: String = "", val isNew: Boolean = false)

// 사용자 정보 데이터 클래스
data class UserInfo(
    val realName: String = "",
    val profilePicture: String? = null
)

// 프로필 이미지 응답 데이터 클래스 (MyPage와 동일한 구조)
data class MoreProfilePictureResponse(
    val fileName: String,
    val contentType: String,
    val base64Data: String
)

// MoreUserInfoResponse.kt 파일 또는 기존 데이터 클래스 파일에 추가
data class MoreUserInfoResponse(
    val username: String?,
    val realName: String?,
    val phone: String?,
    val cash: Double?,
    val createdAt: String?,
    val picture: MoreProfilePictureResponse? // 객체 형태로 변경
)

@Composable
fun MoreScreen() {
    val redMain = Color(0xFFD32F2F)
    val dividerColor = Color.LightGray.copy(alpha = 0.3f)
    val context = LocalContext.current
    val activity = LocalContext.current as? ComponentActivity

    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    var recentNotices by remember { mutableStateOf(listOf<RecentNotice>()) }
    var hasNewNotice by remember { mutableStateOf(false) }
    var userInfo by remember { mutableStateOf(UserInfo()) }
    var profileBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var isLoadingUser by remember { mutableStateOf(true) }

    // 화면 재시작을 감지하기 위한 상태 (마이페이지에서 돌아올 때)
    var refreshTrigger by remember { mutableStateOf(0) }

    // 뒤로가기 두 번 누르기 관련 상태 추가
    var backPressedTime by remember { mutableStateOf(0L) }
    val backPressedInterval = 2000L // 2초

    // 뒤로가기 버튼 처리 추가
    BackHandler(enabled = true) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - backPressedTime < backPressedInterval) {
            // 두 번째 뒤로가기 - 앱 종료
            exitProcess(0)
        } else {
            // 첫 번째 뒤로가기 - 토스트 메시지 표시
            backPressedTime = currentTime
            Toast.makeText(context, "한 번 더 누르면 앱이 종료됩니다", Toast.LENGTH_SHORT).show()
        }
    }

    // AuthTokenManager를 사용한 사용자 정보 로드 함수 (수정됨)
    fun loadUserInfo() {
        activity?.lifecycleScope?.launch {
            isLoadingUser = true
            try {
                // AuthTokenManager를 통해 인증된 API 요청
                val result = authManager.makeAuthenticatedRequest(
                    url = "https://www.downbit.net/user/info",
                    method = "GET"
                )

                if (result.isSuccess) {
                    val responseData = result.getOrNull() ?: ""
                    val jsonResponse = JSONObject(responseData)

                    userInfo = UserInfo(
                        realName = jsonResponse.optString("realName", "사용자"),
                        profilePicture = null
                    )

                    // 프로필 이미지 초기화
                    profileBitmap = null

                    // 프로필 이미지가 있다면 로드
                    val pictureObject = jsonResponse.optJSONObject("picture")
                    pictureObject?.let { picture ->
                        val base64Data = picture.optString("base64Data", "")
                        if (base64Data.isNotEmpty()) {
                            Log.d("MoreScreen", "프로필 이미지 로드 시작: ${picture.optString("fileName")}")
                            loadImageFromBase64(base64Data) { bitmap ->
                                profileBitmap = bitmap
                            }
                        }
                    }

                    Log.d("MoreScreen", "사용자 정보 로드 성공: ${userInfo.realName}")
                } else {
                    // AuthTokenManager가 모든 갱신을 시도한 후 실패한 경우에만 로그인 화면 이동
                    val error = result.exceptionOrNull()
                    Log.e("MoreScreen", "사용자 정보 로드 실패: ${error?.message}")

                    // refresh token도 만료되어 완전히 갱신 불가능한 경우에만 로그인 화면으로
                    if (error?.message?.contains("재로그인") == true) {
                        Toast.makeText(context, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                        navigateToLoginWithAuth(authManager, context)
                    } else {
                        // 네트워크 오류 등 다른 경우는 그냥 에러만 표시
                        Toast.makeText(context, "정보를 불러올 수 없습니다.", Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                Toast.makeText(context, "네트워크 오류: ${e.message}", Toast.LENGTH_SHORT).show()
                Log.e("MoreScreen", "사용자 정보 로딩 실패", e)
            } finally {
                isLoadingUser = false
            }
        }
    }

    // 화면이 다시 표시될 때마다 사용자 정보 새로고침
    LaunchedEffect(refreshTrigger) {
        NoticeReadManager.init(context)

        // 사용자 정보와 공지사항을 병렬로 로드
        activity?.lifecycleScope?.launch {
            // 사용자 정보 로드 (AuthTokenManager 사용)
            loadUserInfo()

            // 공지사항 로드 (기존 방식 유지)
            val fetchedNotices = fetchRecentNoticesFromApi()
            recentNotices = fetchedNotices.take(5) // 최신 5개만 표시

            // 새 공지사항이 있는지 확인 (읽지 않은 + 3일 이내)
            hasNewNotice = fetchedNotices.any { notice ->
                notice.isNew && !NoticeReadManager.isRead(notice.noticeId) && NoticeUtils.isWithin3Days(notice.createdAt)
            }
        }
    }

    // 생명주기 감지 - 화면이 다시 보일 때 새로고침
    DisposableEffect(Unit) {
        val lifecycle = (context as ComponentActivity).lifecycle
        val observer = object : androidx.lifecycle.LifecycleEventObserver {
            override fun onStateChanged(source: androidx.lifecycle.LifecycleOwner, event: androidx.lifecycle.Lifecycle.Event) {
                when (event) {
                    androidx.lifecycle.Lifecycle.Event.ON_RESUME -> {
                        // 200ms 지연 후에 새로고침 (Activity 전환 완료 후)
                        kotlinx.coroutines.CoroutineScope(Dispatchers.Main).launch {
                            kotlinx.coroutines.delay(1000)
                            if (lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                                Log.d("MoreScreen", "화면 재시작 감지 - 사용자 정보 새로고침")
                                refreshTrigger++
                            }
                        }
                    }
                    else -> {}
                }
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
        }
    }

    // 스크롤 상태 생성
    val scrollState = rememberScrollState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 20.dp)
            .padding(bottom = 40.dp) // 하단 여백 추가로 잘림 방지
    ) {
        Spacer(modifier = Modifier.height(24.dp))

        // 사용자 프로필 섹션
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable {
                    context.startActivity(Intent(context, MyPageActivity::class.java))
                }
        ) {
            // 프로필 이미지
            if (profileBitmap != null) {
                Image(
                    bitmap = profileBitmap!!.asImageBitmap(),
                    contentDescription = "프로필 이미지",
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentScale = ContentScale.Crop
                )
            } else {
                Box(
                    modifier = Modifier
                        .size(60.dp)
                        .clip(CircleShape)
                        .background(Color.LightGray),
                    contentAlignment = Alignment.Center
                ) {
                    if (isLoadingUser) {
                        CircularProgressIndicator(
                            color = redMain,
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Person,
                            contentDescription = "기본 프로필",
                            modifier = Modifier.size(30.dp),
                            tint = Color.Gray
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.width(16.dp))

            Column {
                if (isLoadingUser) {
                    Text("로딩 중...", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                } else {
                    Text("${userInfo.realName} 님", fontSize = 19.sp, fontWeight = FontWeight.Bold)
                }
                Text("마이페이지 >", fontSize = 14.sp, color = Color.Gray)
            }
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = dividerColor)

        Spacer(modifier = Modifier.height(10.dp))

        // 공지사항 섹션 (빨간점 표시)
        SectionTitleWithNotification(
            title = "공지사항",
            redMain = redMain,
            hasNotification = hasNewNotice
        ) {
            context.startActivity(Intent(context, NoticeActivity::class.java))
        }

        // 최신 공지사항 5개 표시
        recentNotices.forEach { notice ->
            NoticeItem(
                title = notice.title,
                noticeId = notice.noticeId,
                showNewBadge = notice.isNew && !NoticeReadManager.isRead(notice.noticeId) && NoticeUtils.isWithin3Days(notice.createdAt),
                redMain = redMain
            )
        }

        // 공지사항이 없을 때 메시지
        if (recentNotices.isEmpty()) {
            Text(
                text = "공지사항이 없습니다",
                color = Color.Gray,
                fontSize = 14.sp,
                modifier = Modifier.padding(12.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = dividerColor)

        Spacer(modifier = Modifier.height(20.dp))

        // 고객센터 섹션
        SectionTitleWithoutArrow(title = "고객센터", redMain = redMain)

        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            SubMenuItem("자주하는 질문") {
                context.startActivity(Intent(context, FaqActivity::class.java))
            }
            SubMenuItem("1:1 문의하기") {
                context.startActivity(Intent(context, ContactActivity::class.java))
            }
            SubMenuItem("문의 내역") {
                context.startActivity(Intent(context, InquiryHistoryActivity::class.java))
            }
        }

        // 고객센터 연락처 정보 박스 (이용약관, 개인정보처리방침, Copyright 포함)
        Spacer(modifier = Modifier.height(16.dp))
        CustomerServiceContactBox(redMain = redMain)

        Spacer(modifier = Modifier.height(24.dp))
        Divider(color = dividerColor)

        Spacer(modifier = Modifier.height(20.dp))

        // 사업자 정보 섹션 (아코디언) - 업데이트된 버전
        CompanyInfoSection(redMain = redMain)

        // 추가 하단 여백 (화면 끝까지 스크롤 가능하도록)
        Spacer(modifier = Modifier.height(60.dp))
    }
}

@Composable
fun CustomerServiceContactBox(redMain: Color) {
    Surface(
        color = Color(0xFFF8F8F8),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = "고객센터 1588-6794",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = redMain,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "(평일 09:00~18:00/주말 및 공휴일 휴무/유료)",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "다운비트 라운지(방문 상담): 서울특별시 중구 퇴계로 234, 2층",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth(),
                lineHeight = 16.sp
            )
            Text(
                text = "(평일 09:00~18:00)",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "금융사고 전담 콜센터: 1533-1024",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = Color.Black,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = "(24시간 연중무휴/유료)",
                fontSize = 12.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(16.dp))

            // 개인정보처리방침 링크
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Text(
                    text = "이용약관",
                    fontSize = 12.sp,
                    color = Color.Gray,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        // 이용약관 페이지로 이동
                    }
                )
                Text(
                    text = " | ",
                    fontSize = 12.sp,
                    color = Color.Gray
                )
                Text(
                    text = "개인정보처리방침",
                    fontSize = 12.sp,
                    color = Color.Black,
                    fontWeight = FontWeight.Medium,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable {
                        // 개인정보처리방침 페이지로 이동
                    }
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 저작권 정보
            Text(
                text = "Copyright 2017 - 2025 Dunamu Inc. All rights reserved.",
                fontSize = 10.sp,
                color = Color.Gray,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
fun CompanyInfoSection(redMain: Color) {
    var isExpanded by remember { mutableStateOf(false) }

    Surface(
        color = Color(0xFFF8F8F8),
        shape = RoundedCornerShape(8.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column {
            // 헤더 부분 (클릭 가능)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { isExpanded = !isExpanded }
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "(주)다운비트코리아 사업자 정보",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    color = redMain
                )

                Icon(
                    imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = if (isExpanded) "접기" else "펼치기",
                    tint = Color.Gray,
                    modifier = Modifier.size(24.dp)
                )
            }

            // 확장된 내용 (애니메이션과 함께)
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                ) {
                    Divider(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // 사업자 정보 항목들
                    CompanyInfoItem("사업자 등록번호", "110-40-70200")
                    CompanyInfoItem("대표", "김다운")
                    CompanyInfoItem("호스팅 서비스", "주식회사 다운비트코리아")
                    CompanyInfoItem("통신판매업 신고번호", "2025-서울강남-69745")
                    CompanyInfoItem("사업자정보확인", "바로가기", isLink = true, redMain = redMain)

                    Spacer(modifier = Modifier.height(12.dp))

                    // 주소 정보
                    Text(
                        text = "주소",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = Color.Black,
                        modifier = Modifier.padding(bottom = 4.dp)
                    )
                    Text(
                        text = "서울특별시 중구 퇴계로 234, 2층 (다운비트타워)",
                        fontSize = 14.sp,
                        color = Color.Gray,
                        lineHeight = 20.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // API 서비스 정보 섹션 추가
                    Divider(
                        color = Color.LightGray.copy(alpha = 0.5f),
                        modifier = Modifier.padding(bottom = 12.dp)
                    )

                    Text(
                        text = "API 서비스",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = redMain,
                        modifier = Modifier.padding(bottom = 8.dp)
                    )

                    CompanyInfoItem("서비스 URL", "https://www.downbit.net")
                    CompanyInfoItem("코인 상장 신청", "/listing/submit-application")
                    CompanyInfoItem("기술 지원", "tech-support@downbit.net", isEmail = true)
                    CompanyInfoItem("상장 문의", "listing@downbit.net", isEmail = true)
                    CompanyInfoItem("API 문서", "바로가기", isLink = true, redMain = redMain, linkUrl = "https://www.downbit.net/api-docs")
                }
            }
        }
    }
}

@Composable
fun CompanyInfoItem(
    label: String,
    value: String,
    isLink: Boolean = false,
    isEmail: Boolean = false,
    redMain: Color = Color.Red,
    linkUrl: String = ""
) {
    val context = LocalContext.current

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = "$label :",
            fontSize = 14.sp,
            color = Color.Black,
            modifier = Modifier.weight(1f)
        )

        if (isLink || isEmail) {
            Text(
                text = value,
                fontSize = 14.sp,
                color = redMain,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable {
                    when {
                        isEmail -> {
                            // 이메일 인텐트
                            val emailIntent = Intent(Intent.ACTION_SENDTO).apply {
                                data = android.net.Uri.parse("mailto:$value")
                            }
                            try {
                                context.startActivity(emailIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "이메일 앱을 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                        isLink -> {
                            // 웹 브라우저로 링크 열기
                            val browserIntent = Intent(Intent.ACTION_VIEW).apply {
                                data = android.net.Uri.parse(linkUrl.ifEmpty { "https://www.ftc.go.kr/bizCommPop.do?wrkr_no=000-00-00000" })
                            }
                            try {
                                context.startActivity(browserIntent)
                            } catch (e: Exception) {
                                Toast.makeText(context, "브라우저를 찾을 수 없습니다", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            )
        } else {
            Text(
                text = value,
                fontSize = 14.sp,
                color = Color.Gray,
                textAlign = TextAlign.End
            )
        }
    }
}

@Composable
fun SectionTitleWithoutArrow(title: String, redMain: Color) {
    Text(
        text = title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = redMain,
        modifier = Modifier.fillMaxWidth()
    )
}

@Composable
fun SectionTitleWithNotification(
    title: String,
    redMain: Color,
    hasNotification: Boolean,
    onClick: (() -> Unit)? = null
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onClick != null) {
                onClick?.invoke()
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(title, fontSize = 18.sp, fontWeight = FontWeight.Bold, color = redMain)

            // 새 공지사항이 있으면 빨간점 표시
            if (hasNotification) {
                Spacer(modifier = Modifier.width(6.dp))
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(redMain, CircleShape)
                )
            }
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForwardIos,
            contentDescription = "$title 이동",
            tint = Color.Gray,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
fun SubMenuItem(title: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 12.dp, horizontal = 12.dp)
    ) {
        Text(title, fontSize = 16.sp)
    }
}

@Composable
fun NoticeItem(title: String, noticeId: Int, showNewBadge: Boolean, redMain: Color) {
    val context = LocalContext.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val intent = Intent(context, NoticeDetailActivity::class.java).apply {
                    putExtra("noticeId", noticeId)
                    putExtra("noticeTitle", title)
                    putExtra("noticeDate", "")
                }
                context.startActivity(intent)
            }
            .padding(vertical = 4.dp)
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 8.dp)
        ) {
            // NEW 뱃지
            if (showNewBadge) {
                Text(
                    text = "NEW",
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    modifier = Modifier
                        .background(redMain, RoundedCornerShape(3.dp))
                        .padding(horizontal = 4.dp, vertical = 1.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
            }

            Text(
                text = title,
                fontSize = 14.sp,
                color = Color.DarkGray,
                modifier = Modifier.weight(1f),
                maxLines = 1
            )
        }

        // 구분선
        Divider(
            color = Color.LightGray.copy(alpha = 0.3f),
            thickness = 0.5.dp,
            modifier = Modifier.padding(horizontal = 4.dp)
        )
    }
}

// AuthTokenManager를 사용한 로그인 페이지 이동 함수
private fun navigateToLoginWithAuth(authManager: AuthTokenManager, context: android.content.Context) {
    // AuthTokenManager로 토큰 정리
    authManager.logout()

    // app_prefs도 정리
    val sharedPref = context.getSharedPreferences("app_prefs", android.content.Context.MODE_PRIVATE)
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

// Base64 문자열에서 직접 Bitmap 로드하는 함수 (MyPage와 동일)
private fun loadImageFromBase64(base64Data: String, onLoaded: (Bitmap?) -> Unit) {
    kotlinx.coroutines.CoroutineScope(Dispatchers.IO).launch {
        try {
            Log.d("MoreScreen", "Base64 이미지 로딩 시작, 데이터 길이: ${base64Data.length}")

            val bitmap = decodeBase64ToBitmap(base64Data)

            withContext(Dispatchers.Main) {
                if (bitmap != null) {
                    Log.d("MoreScreen", "프로필 이미지 로딩 성공: ${bitmap.width}x${bitmap.height}")
                } else {
                    Log.e("MoreScreen", "프로필 이미지 로딩 실패")
                }
                onLoaded(bitmap)
            }
        } catch (e: Exception) {
            Log.e("MoreScreen", "Base64 이미지 로딩 실패: ${e.message}", e)
            withContext(Dispatchers.Main) {
                onLoaded(null)
            }
        }
    }
}

// Base64 문자열을 Bitmap으로 변환하는 함수 (MyPage와 동일)
private fun decodeBase64ToBitmap(base64String: String): Bitmap? {
    return try {
        // Base64 문자열에서 불필요한 공백과 개행 문자 제거
        val cleanBase64 = base64String.replace("\\s+".toRegex(), "")

        Log.d("MoreScreen", "Base64 디코딩 시작, 정리된 데이터 길이: ${cleanBase64.length}")

        // Base64 디코딩
        val decodedBytes = android.util.Base64.decode(cleanBase64, android.util.Base64.DEFAULT)
        Log.d("MoreScreen", "Base64 디코딩 완료, 바이트 배열 크기: ${decodedBytes.size}")

        // 바이트 배열을 Bitmap으로 변환
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

        if (bitmap != null) {
            Log.d("MoreScreen", "Bitmap 변환 성공: ${bitmap.width}x${bitmap.height}")
        } else {
            Log.e("MoreScreen", "Bitmap 변환 실패: BitmapFactory.decodeByteArray 결과가 null")
        }

        bitmap
    } catch (e: IllegalArgumentException) {
        Log.e("MoreScreen", "Base64 디코딩 실패 - 잘못된 Base64 형식: ${e.message}")
        null
    } catch (e: Exception) {
        Log.e("MoreScreen", "Bitmap 변환 실패: ${e.message}", e)
        null
    }
}

// 공지사항 API는 기존 방식 유지 (AuthTokenManager 적용 안함)
suspend fun fetchRecentNoticesFromApi(): List<RecentNotice> {
    return withContext(Dispatchers.IO) {
        val url = java.net.URL("${RetrofitClient.BASE_URL}/notice/recent")
        val connection = url.openConnection() as java.net.HttpURLConnection
        val notices = mutableListOf<RecentNotice>()

        try {
            connection.requestMethod = "GET"
            connection.connectTimeout = 5000
            connection.readTimeout = 5000

            Log.d("MoreScreen", "API 호출: ${url}")
            Log.d("MoreScreen", "응답 코드: ${connection.responseCode}")

            if (connection.responseCode == 200) {
                val stream = connection.inputStream.bufferedReader().readText()
                Log.d("MoreScreen", "API 응답: $stream")

                val jsonObject = JSONObject(stream)

                // JSON 구조 확인 - recentNotices 또는 noticeList일 수 있음
                val jsonArray = when {
                    jsonObject.has("recentNotices") -> jsonObject.getJSONArray("recentNotices")
                    jsonObject.has("noticeList") -> jsonObject.getJSONArray("noticeList")
                    else -> {
                        Log.e("MoreScreen", "JSON에서 공지사항 배열을 찾을 수 없음")
                        return@withContext emptyList()
                    }
                }

                for (i in 0 until jsonArray.length()) {
                    val item = jsonArray.getJSONObject(i)
                    val id = item.getInt("noticeId")
                    val title = item.getString("title")
                    val createdAt = item.optString("createdAt", "")

                    notices.add(RecentNotice(id, title, createdAt, true))
                }
            }
        } catch (e: Exception) {
            Log.e("MoreScreen", "API 호출 실패", e)
            e.printStackTrace()
        } finally {
            connection.disconnect()
        }

        // 최신 5개만 반환
        notices.take(5)
    }
}