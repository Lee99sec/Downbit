package com.example.myapplication

import SecureBaseActivity
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.MaterialTheme
import androidx.compose.material.Scaffold
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.runtime.Composable
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

class FaqActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    FaqScreen()
                }
            }
        }
    }
}

@Composable
fun FaqScreen() {
    val redMain = Color(0xFFD32F2F)
    val context = LocalContext.current // 현재 Activity 컨텍스트
    val faqs = listOf(
        "회원가입은 어떻게 하나요?" to "다운비트 앱에서 휴대폰 번호 인증 후 가입 가능합니다.",
        "비밀번호를 잊어버렸어요." to "로그인 화면의 '비밀번호 찾기'를 이용해주세요.",
        "코인을 구매하려면 어떻게 하나요?" to "원화 입금 후 거래소에서 구매하실 수 있습니다.",
        "입금은 얼마나 걸리나요?" to "은행 점검시간 외에는 대부분 1~3분 내 처리됩니다.",
        "수수료는 얼마인가요?" to "거래 수수료는 0.05%입니다.(오픈 이벤트 0% 진행중)",
        "거래소 이용 시간은 어떻게 되나요?" to "다운비트 거래소는 연중무휴 24시간 운영됩니다.",
        "PIN 인증은 어떻게 설정하나요?" to "회원가입 시 PIN 인증을 등록할 수 있습니다.",
        "원화 출금은 얼마나 걸리나요?" to "출금 신청 후 영업일 기준 1~2일 내 처리됩니다.",
        "계정을 탈퇴하려면 어떻게 해야 하나요?" to "마이페이지 > 회원탈퇴 메뉴에서 직접 탈퇴할 수 있습니다.",
        "고객센터는 어떻게 문의하나요?" to "1:1 문의하기 또는 자주하는 질문 메뉴를 이용해 주세요."
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        "자주하는 질문",
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                },
                backgroundColor = redMain,
                elevation = 4.dp,
                navigationIcon = {
                    IconButton(onClick = {
                        (context as? ComponentActivity)?.finish() // 뒤로가기 처리
                    }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "뒤로가기",
                            tint = Color.White
                        )
                    }
                }
            )
        },
        content = { innerPadding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(16.dp)
            ) {
                faqs.forEach { (question, answer) ->
                    FaqItem(question = question, answer = answer)
                    Spacer(modifier = Modifier.height(12.dp))
                }
            }
        }
    )
}


@Composable
fun FaqItem(question: String, answer: String) {
    var expanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(10.dp),
        elevation = 4.dp,
        color = Color(0xFFFBEAEA),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { expanded = !expanded }
            .animateContentSize()
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    text = "Q. $question",
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 16.sp,
                    color = Color(0xFF333333)
                )
                Icon(
                    imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                    contentDescription = "Toggle FAQ"
                )
            }

            if (expanded) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "A. $answer",
                    fontSize = 14.sp,
                    color = Color(0xFF555555),
                    lineHeight = 20.sp
                )
            }
        }
    }
}