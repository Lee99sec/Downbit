package com.example.myapplication

import AuthTokenManager
import SecureBaseActivity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Base64
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.Button
import androidx.compose.material.ButtonDefaults
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.Icon
import androidx.compose.material.IconButton
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.material.TextFieldDefaults
import androidx.compose.material.TopAppBar
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

// 데이터 클래스 정의
data class FileUploadData(
    val fileName: String,
    val contentType: String,
    val base64Data: String
)

data class QnaRequest(
    val title: String,
    val content: String,
    val files: List<FileUploadData>
)

class ContactActivity : SecureBaseActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            Surface(modifier = Modifier.fillMaxSize()) {
                ContactScreen()
            }
        }
    }
}

@Composable
fun ContactScreen() {
    var titleText by remember { mutableStateOf(TextFieldValue()) }
    var inquiryText by remember { mutableStateOf(TextFieldValue()) }
    var attachedFiles by remember { mutableStateOf(listOf<FileUploadData>()) }
    var isLoading by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    // AuthTokenManager 인스턴스 생성
    val authManager = remember { AuthTokenManager(context) }

    // 빨간색 색상 정의
    val redColor = Color(0xFFD32F2F)

    // 파일 선택 런처
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetMultipleContents()
    ) { uris ->
        scope.launch {
            val newFiles = mutableListOf<FileUploadData>()

            uris.forEach { uri ->
                try {
                    val file = convertUriToFileData(context, uri)
                    if (file != null) {
                        newFiles.add(file)
                    }
                } catch (e: Exception) {
                    Log.e("ContactScreen", "파일 변환 오류: ${e.message}")
                    Toast.makeText(context, "파일 처리 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
                }
            }

            attachedFiles = attachedFiles + newFiles
        }
    }

    Column(
        modifier = Modifier.fillMaxSize()
    ) {
        // 상단 앱바
        TopAppBar(
            title = { Text("1:1 문의하기", color = Color.White) },
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
            backgroundColor = redColor
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "상담 문의 처리를 위해 필요한 정보만 기입해주세요. 요청드리지 않은 개인정보 입력 시 상담이 중단될 수 있습니다.",
                fontSize = 14.sp,
                color = Color.Gray
            )

            OutlinedTextField(
                value = titleText,
                onValueChange = { titleText = it },
                label = { Text("문의 제목") },
                modifier = Modifier.fillMaxWidth(),
                enabled = !isLoading,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = redColor,
                    focusedLabelColor = redColor,
                    cursorColor = redColor
                )
            )

            OutlinedTextField(
                value = inquiryText,
                onValueChange = { inquiryText = it },
                label = { Text("문의 내용") },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp),
                enabled = !isLoading,
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = redColor,
                    focusedLabelColor = redColor,
                    cursorColor = redColor
                )
            )

            FileAttachSection(
                attachedFiles = attachedFiles,
                onAddFile = {
                    filePickerLauncher.launch("image/*")
                },
                onRemoveFile = { index ->
                    attachedFiles = attachedFiles.filterIndexed { i, _ -> i != index }
                },
                isLoading = isLoading,
                redColor = redColor
            )

            Spacer(modifier = Modifier.height(24.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Button(
                    onClick = {
                        (context as? ComponentActivity)?.finish()
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = Color.LightGray),
                    enabled = !isLoading
                ) {
                    Text("취소", color = Color.Black)
                }

                Button(
                    onClick = {
                        if (titleText.text.isBlank() || inquiryText.text.isBlank()) {
                            Toast.makeText(context, "제목과 내용을 입력해주세요.", Toast.LENGTH_SHORT).show()
                            return@Button
                        }

                        scope.launch {
                            isLoading = true
                            try {
                                val success = submitInquiry(
                                    authManager = authManager,
                                    context = context,
                                    title = titleText.text,
                                    content = inquiryText.text,
                                    files = attachedFiles
                                )

                                // submitInquiry에서 이미 성공/실패 메시지 처리하므로
                                // 여기서는 성공 시에만 화면 종료
                                if (success) {
                                    (context as? ComponentActivity)?.finish()
                                }
                            } catch (e: Exception) {
                                Log.e("ContactScreen", "문의 접수 오류: ${e.message}")
                                Toast.makeText(context, "네트워크 연결을 확인해주세요.", Toast.LENGTH_SHORT).show()
                            } finally {
                                isLoading = false
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(backgroundColor = redColor),
                    enabled = !isLoading
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Text("문의 보내기", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
fun FileAttachSection(
    attachedFiles: List<FileUploadData>,
    onAddFile: () -> Unit,
    onRemoveFile: (Int) -> Unit,
    isLoading: Boolean,
    redColor: Color
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = "첨부 파일 (${attachedFiles.size}개)",
            fontSize = 14.sp,
            color = Color.Gray
        )

        Button(
            onClick = onAddFile,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(backgroundColor = redColor),
            enabled = !isLoading
        ) {
            Text("+ 파일추가", color = Color.White)
        }

        if (attachedFiles.isNotEmpty()) {
            LazyColumn(
                modifier = Modifier.heightIn(max = 150.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                itemsIndexed(attachedFiles) { index, file ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "- ${file.fileName}",
                            fontSize = 12.sp,
                            color = Color.DarkGray,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onRemoveFile(index) },
                            enabled = !isLoading
                        ) {
                            Icon(
                                imageVector = Icons.Default.Close,
                                contentDescription = "파일 삭제",
                                tint = redColor,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        Text(
            text = "- 문의사항과 관련한 자료를 첨부하실 수 있습니다.\n- 개인정보가 포함된 자료나 불필요한 자료를 제출하시면 삭제됩니다.\n- 이미지 파일만 업로드 가능합니다 (JPEG, PNG, GIF, WebP)",
            fontSize = 12.sp,
            color = Color.Gray,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}

// URI를 FileUploadData로 변환하는 함수
suspend fun convertUriToFileData(context: Context, uri: Uri): FileUploadData? {
    return withContext(Dispatchers.IO) {
        try {
            val contentResolver = context.contentResolver
            val inputStream = contentResolver.openInputStream(uri)
            val mimeType = contentResolver.getType(uri) ?: "image/jpeg"

            // 파일 크기 체크 (5MB)
            val fileSize = inputStream?.available() ?: 0
            if (fileSize > 5 * 1024 * 1024) {
                throw Exception("파일 크기가 5MB를 초과합니다.")
            }

            // 이미지 파일 타입 체크
            if (!mimeType.startsWith("image/")) {
                throw Exception("이미지 파일만 업로드 가능합니다.")
            }

            val bytes = inputStream?.readBytes()
            inputStream?.close()

            if (bytes != null) {
                val base64String = Base64.encodeToString(bytes, Base64.NO_WRAP)
                val fileName = getFileName(context, uri) ?: "image_${System.currentTimeMillis()}"

                FileUploadData(
                    fileName = fileName,
                    contentType = mimeType,
                    base64Data = base64String
                )
            } else {
                null
            }
        } catch (e: Exception) {
            Log.e("FileConverter", "파일 변환 실패: ${e.message}")
            null
        }
    }
}

// 파일명 추출 함수
fun getFileName(context: Context, uri: Uri): String? {
    val cursor = context.contentResolver.query(uri, null, null, null, null)
    return cursor?.use {
        val nameIndex = it.getColumnIndex(MediaStore.Images.Media.DISPLAY_NAME)
        if (it.moveToFirst() && nameIndex >= 0) {
            it.getString(nameIndex)
        } else {
            null
        }
    }
}

// AuthTokenManager를 사용한 로그인 페이지 이동 함수
private fun navigateToLoginFromContact(authManager: AuthTokenManager, context: Context) {
    Log.d("ContactActivity", "AuthTokenManager로 로그인 페이지 이동")

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

// AuthTokenManager를 사용한 API 호출 함수
suspend fun submitInquiry(
    authManager: AuthTokenManager,
    context: Context,
    title: String,
    content: String,
    files: List<FileUploadData>
): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            Log.d("ContactActivity", "AuthTokenManager로 문의 접수 시작")
            Log.d("ContactActivity", "제목: $title")
            Log.d("ContactActivity", "첨부파일 수: ${files.size}")

            // JSON 요청 본문 구성
            val requestJson = JSONObject().apply {
                put("title", title)
                put("content", content)

                // files 배열 추가
                val filesArray = JSONArray()
                files.forEach { file ->
                    val fileObj = JSONObject().apply {
                        put("fileName", file.fileName)
                        put("contentType", file.contentType)
                        put("base64Data", file.base64Data)
                    }
                    filesArray.put(fileObj)
                }
                put("files", filesArray)
            }

            Log.d("ContactActivity", "요청 본문 준비 완료")

            // AuthTokenManager를 통해 인증된 API 요청
            val result = authManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.HTTP_BASE_URL}/qna/write",
                method = "POST",
                requestBody = requestJson.toString()
            )

            if (result.isSuccess) {
                Log.d("ContactActivity", "문의 접수 성공")
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "문의가 성공적으로 접수되었습니다.", Toast.LENGTH_SHORT).show()
                }
                true
            } else {
                // AuthTokenManager가 모든 갱신을 시도한 후 실패한 경우
                val error = result.exceptionOrNull()
                Log.e("ContactActivity", "문의 접수 실패: ${error?.message}")

                withContext(Dispatchers.Main) {
                    when {
                        error?.message?.contains("재로그인") == true -> {
                            Toast.makeText(context, "세션이 만료되었습니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                            navigateToLoginFromContact(authManager, context)
                        }
                        error?.message?.contains("권한") == true -> {
                            Toast.makeText(context, "문의 작성 권한이 없습니다.", Toast.LENGTH_SHORT).show()
                        }
                        error?.message?.contains("네트워크") == true -> {
                            Toast.makeText(context, "네트워크 연결을 확인해주세요.", Toast.LENGTH_SHORT).show()
                        }
                        error?.message?.contains("지원하지 않는 HTTP 메소드") == true -> {
                            Toast.makeText(context, "서버 설정 오류입니다.", Toast.LENGTH_SHORT).show()
                        }
                        else -> {
                            // 에러 메시지에서 구체적인 내용 추출 시도
                            val errorMessage = error?.message?.let { msg ->
                                when {
                                    msg.contains("이미지") -> "이미지 파일만 업로드 가능합니다."
                                    msg.contains("크기") || msg.contains("size") -> "파일 크기가 너무 큽니다. (최대 5MB)"
                                    msg.contains("파일명") || msg.contains("filename") -> "잘못된 파일명입니다."
                                    msg.contains("제목") || msg.contains("title") -> "제목을 입력해주세요."
                                    msg.contains("내용") || msg.contains("content") -> "내용을 입력해주세요."
                                    else -> "문의 접수에 실패했습니다."
                                }
                            } ?: "문의 접수에 실패했습니다."
                            Toast.makeText(context, errorMessage, Toast.LENGTH_LONG).show()
                        }
                    }
                }
                false
            }
        } catch (e: Exception) {
            Log.e("ContactActivity", "문의 접수 예외 발생", e)
            withContext(Dispatchers.Main) {
                Toast.makeText(context, "오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }
}