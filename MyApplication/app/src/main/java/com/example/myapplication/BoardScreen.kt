package com.example.myapplication
import AuthTokenManager
// Android 관련 import
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.Base64
import android.content.Context
import android.content.Intent
import android.widget.Toast
import android.graphics.Bitmap

// Compose 관련 import
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.BorderStroke

// ViewModel 및 기타
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.delay
import androidx.activity.compose.BackHandler
import androidx.activity.result.contract.ActivityResultContracts.OpenMultipleDocuments
import kotlin.system.exitProcess
import androidx.compose.runtime.DisposableEffect
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job

// 외부 라이브러리
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.SwipeRefreshIndicator
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState

import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.DpOffset

/**
 * 게시판 메인 화면을 구성하는 Composable 함수
 * 게시글 목록, 상세보기, 작성/수정 기능을 포함
 */
@Composable
fun BoardScreen(paddingValues: PaddingValues = PaddingValues()) {
    // 앱 전체에서 사용하는 메인 컬러 정의
    val redMain = Color(0xFFD32F2F)
    val context = LocalContext.current
    val authTokenManager = remember { AuthTokenManager(context) }
    val viewModel: BoardViewModel = viewModel {
        BoardViewModel(context, authTokenManager)  // 두 번째 파라미터 추가
    }
    val scope = rememberCoroutineScope()

    // ViewModel에서 관리하는 상태들을 구독
    val summaries by viewModel.summaries.collectAsState()
    val myPosts by viewModel.myPosts.collectAsState()
    val selected by viewModel.selectedPost.collectAsState()
    val comments by viewModel.comments.collectAsState()
    val attachedFiles by viewModel.attachedFiles.collectAsState()
    val isPosting by viewModel.isPosting.collectAsState()
    val errorMessage by viewModel.errorMessage.collectAsState()

    // UI 로컬 상태들 - 화면 내에서만 관리되는 상태
    var searchQuery by remember { mutableStateOf("") }
    var selectedTab by remember { mutableStateOf(0) } // 0: 자유게시판, 1: 내 게시글
    var screenMode by remember { mutableStateOf("list") } // list, detail, write, edit
    var isSearchVisible by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
    var newTitle by remember { mutableStateOf("") }
    var newContent by remember { mutableStateOf("") }
    var editPostId by remember { mutableStateOf(0L) }

    // 삭제 확인 다이얼로그 상태
    var confirmDeletePostId by remember { mutableStateOf<Long?>(null) }
    var confirmDeleteCommentId by remember { mutableStateOf<Long?>(null) }

    // 댓글 수정 관련 상태들
    var showEditCommentDialog by remember { mutableStateOf(false) }
    var editCommentContent by remember { mutableStateOf("") }
    var editCommentId by remember { mutableStateOf(0L) }

    // 뒷버튼 두 번 눌러서 앱 종료 처리
    var backPressedTime by remember { mutableStateOf(0L) }
    val backPressedInterval = 2000L

    // ViewModel에서 현재 정렬 상태 구독
    val currentSort by viewModel.currentSortType.collectAsState()

    // 시스템 뒷버튼 처리
    BackHandler(enabled = true) {
        when (screenMode) {
            "list" -> {
                // 메인 화면에서는 두 번 눌러야 앱 종료
                val currentTime = System.currentTimeMillis()
                if (currentTime - backPressedTime < backPressedInterval) {
                    exitProcess(0)
                } else {
                    backPressedTime = currentTime
                    Toast.makeText(context, "한 번 더 누르면 앱이 종료됩니다", Toast.LENGTH_SHORT).show()
                }
            }
            "detail" -> {
                // 상세보기에서는 목록으로 돌아가기
                viewModel.clearSelection()
                screenMode = "list"
            }
            "write", "edit" -> {
                // 작성/수정에서는 이전 화면으로 돌아가기
                screenMode = if (screenMode == "edit") "detail" else "list"
                viewModel.clearFiles()
            }
        }
    }

    // 파일 선택 런처 - 이미지 파일만 선택 가능
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = OpenMultipleDocuments(),
        onResult = { uris: List<Uri> ->
            if (uris.isNotEmpty()) {
                // 파일 추가 및 결과 메시지 처리
                val (addedCount, messages) = viewModel.addFiles(uris)
                messages.forEach { message ->
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
                // 파일 권한 유지 설정
                if (addedCount > 0) {
                    val cr = context.contentResolver
                    uris.forEach { uri ->
                        try {
                            cr.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        } catch (_: Exception) {}
                    }
                }
            }
        }
    )

    // 새로고침 함수 - 현재 탭에 따라 다른 데이터 로드
    val refreshData: () -> Unit = {
        isRefreshing = true
        scope.launch {
            try {
                if (selectedTab == 0) {
                    viewModel.loadSummaries()
                } else {
                    viewModel.loadMyPosts()
                }
            } finally {
                isRefreshing = false
            }
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {

        // 상세보기 화면 - 슬라이드 애니메이션으로 표시
        AnimatedVisibility(
            visible = screenMode == "detail" && selected != null,
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            selected?.let { post ->
                DetailScreen(
                    post = post,
                    comments = comments,
                    onBack = {
                        viewModel.clearSelection()
                        // 목록 새로고침 추가
                        viewModel.loadSummaries()
                        viewModel.loadMyPosts()
                        screenMode = "list"
                    },
                    onSendComment = { txt -> scope.launch { viewModel.createComment(post.postId, txt) } },
                    onDeleteComment = { commentId -> confirmDeleteCommentId = commentId },
                    onEditPost = { id, t, c ->
                        editPostId = id; newTitle = t; newContent = c
                        viewModel.startEditWithSelectedPost()
                        screenMode = "edit"
                    },
                    onEditComment = { cid, content ->
                        editCommentId = cid; editCommentContent = content; showEditCommentDialog = true
                    },
                    onDeletePost = { postId -> confirmDeletePostId = postId },
                    isOwner = viewModel.isOwnerOfSelectedPost(),
                    viewModel = viewModel,
                    paddingValues = paddingValues
                )
            }
        }

        // 작성/수정 화면 - 슬라이드 애니메이션으로 표시
        AnimatedVisibility(
            visible = screenMode == "write" || screenMode == "edit",
            enter = slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            val isEdit = screenMode == "edit"
            WriteEditScreen(
                title = if (isEdit) "게시글 수정" else "게시글 작성",
                titleValue = newTitle,
                contentValue = newContent,
                attachedFiles = attachedFiles,
                isPosting = isPosting,
                paddingValues = paddingValues,
                onTitleChange = { newTitle = it },
                onContentChange = { newContent = it },
                onRemoveAttachedFile = { attachedFile -> viewModel.removeAttachedFile(attachedFile) },
                onBack = {
                    screenMode = if (isEdit) "detail" else "list"
                    viewModel.clearFiles()
                },
                onSave = {
                    if (isEdit) {
                        viewModel.editPost(editPostId, newTitle, newContent) { screenMode = "detail" }
                    } else {
                        viewModel.createPost(newTitle, newContent) { screenMode = "list" }
                    }
                },
                saveButtonText = if (isEdit) "수정" else "등록",
                onAddFiles = { filePickerLauncher.launch(arrayOf("image/*")) },
                viewModel = viewModel
            )
        }

        // 메인 목록 화면 - 기본 화면
        AnimatedVisibility(
            visible = screenMode == "list",
            enter = slideInHorizontally(initialOffsetX = { -it }, animationSpec = tween(300)) + fadeIn(animationSpec = tween(300)),
            exit = slideOutHorizontally(targetOffsetX = { -it }, animationSpec = tween(300)) + fadeOut(animationSpec = tween(300))
        ) {
            Column(Modifier.fillMaxSize().padding(paddingValues)) {
                // 상단 헤더 - 제목, 검색, 글쓰기 버튼
                Row(
                    modifier = Modifier.fillMaxWidth().height(72.dp).padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("커뮤니티", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = redMain)
                    Spacer(modifier = Modifier.width(16.dp))

                    // 검색창 - 토글 방식으로 표시/숨김
                    AnimatedVisibility(visible = isSearchVisible) {
                        OutlinedTextField(
                            value = searchQuery,
                            onValueChange = {
                                searchQuery = it
                                // 검색어 변경 시 실시간 검색 수행
                                scope.launch {
                                    if (selectedTab == 0) viewModel.searchPosts(it)
                                    else viewModel.searchMyPosts(it)
                                }
                            },
                            modifier = Modifier.weight(1f).height(56.dp),
                            singleLine = true,
                            placeholder = { Text(if (selectedTab == 0) "게시물 검색" else "내 게시글 검색") },
                            colors = TextFieldDefaults.outlinedTextFieldColors(
                                focusedBorderColor = redMain,
                                cursorColor = redMain,
                            ),
                            trailingIcon = {
                                IconButton(onClick = {
                                    // 검색창 닫기 및 초기화
                                    isSearchVisible = false
                                    searchQuery = ""
                                    scope.launch {
                                        if (selectedTab == 0) viewModel.loadSummaries()
                                        else viewModel.loadMyPosts()
                                    }
                                }) {
                                    Icon(Icons.Default.Close, contentDescription = "검색 닫기")
                                }
                            }
                        )
                    }

                    if (!isSearchVisible) {
                        Spacer(modifier = Modifier.weight(1f))
                    }

                    // 검색 아이콘 - 검색창이 닫혀있을 때만 표시
                    AnimatedVisibility(visible = !isSearchVisible) {
                        IconButton(onClick = { isSearchVisible = true }) {
                            Icon(Icons.Default.Search, contentDescription = "검색", tint = redMain)
                        }
                    }

                    // 글쓰기 버튼
                    IconButton(onClick = {
                        screenMode = "write"
                        newTitle = ""
                        newContent = ""
                        viewModel.clearFiles()
                    }) {
                        Icon(Icons.Default.Add, contentDescription = "글쓰기", tint = redMain)
                    }
                }

                // 탭 UI - 자유게시판 / 내 게시글
                val tabs = listOf("자유게시판", "내 게시글")
                TabRow(
                    selectedTabIndex = selectedTab,
                    backgroundColor = Color.White,
                    contentColor = redMain
                ) {
                    tabs.forEachIndexed { idx, title ->
                        Tab(
                            selected = idx == selectedTab,
                            onClick = {
                                // 탭 변경 시 검색 초기화 및 데이터 로드
                                selectedTab = idx
                                searchQuery = ""
                                isSearchVisible = false
                                scope.launch {
                                    if (idx == 0) viewModel.loadSummaries()
                                    else viewModel.loadMyPosts()
                                }
                            },
                            text = { Text(title) },
                            selectedContentColor = redMain,
                            unselectedContentColor = Color.Gray
                        )
                    }
                }

                // 새로 추가: 정렬 섹션
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    color = Color(0xFFFAFAFA),
                    elevation = 1.dp
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // 게시물 개수 표시
                        val posts = if (selectedTab == 0) summaries else myPosts
                        Text(
                            text = "${if (selectedTab == 0) "전체" else "내"} 게시물 ${posts.size}개",
                            fontSize = 13.sp,
                            color = Color(0xFF666666),
                            fontWeight = FontWeight.Medium
                        )

                        // 정렬 드롭다운
                        SortDropdown(
                            currentSort = currentSort,
                            onSortChange = { newSort ->
                                println("🎯 드롭다운에서 선택: ${newSort.displayName}")
                                viewModel.setSortType(newSort)
                            }
                        )
                    }
                }

                // 게시글 리스트 - 새로고침 기능 포함
                val posts = if (selectedTab == 0) summaries else myPosts
                val swipeRefreshState = rememberSwipeRefreshState(isRefreshing)

                SwipeRefresh(
                    state = swipeRefreshState,
                    onRefresh = refreshData,
                    indicator = { state, trigger ->
                        SwipeRefreshIndicator(
                            state = state,
                            refreshTriggerDistance = trigger,
                            backgroundColor = Color.White,
                            contentColor = redMain
                        )
                    }
                ) {
                    LazyColumn(
                        Modifier.fillMaxSize().padding(horizontal = 16.dp),
                        contentPadding = PaddingValues(top = 8.dp, bottom = 80.dp)
                    ) {
                        // 게시글 목록 표시
                        items(posts) { post ->
                            PostItem(
                                post = post,
                                onPostClick = {
                                    viewModel.selectPost(post.postId)
                                    screenMode = "detail"
                                }
                            )
                            Divider()
                        }

                        // 게시물이 없을 때 안내 메시지
                        if (posts.isEmpty()) {
                            item {
                                Box(
                                    modifier = Modifier.fillMaxWidth().height(200.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        if (selectedTab == 0) "게시물이 없습니다." else "작성한 게시물이 없습니다.",
                                        color = Color.Gray
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }

        // 에러 메시지 토스트 표시
        LaunchedEffect(errorMessage) {
            errorMessage?.let { message ->
                Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                viewModel.clearError()
            }
        }
    }

    // 게시글 삭제 확인 다이얼로그
    if (confirmDeletePostId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeletePostId = null },
            title = { Text("게시글 삭제") },
            text = { Text("정말 삭제하시겠어요? 이 작업은 되돌릴 수 없습니다.") },
            confirmButton = {
                TextButton(onClick = {
                    val id = confirmDeletePostId!!
                    confirmDeletePostId = null
                    scope.launch {
                        viewModel.deletePost(id)
                        viewModel.clearSelection()
                        screenMode = "list"
                    }
                }) { Text("삭제", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeletePostId = null }) { Text("취소") }
            }
        )
    }

    // 댓글 삭제 확인 다이얼로그
    if (confirmDeleteCommentId != null) {
        AlertDialog(
            onDismissRequest = { confirmDeleteCommentId = null },
            title = { Text("댓글 삭제") },
            text = { Text("정말 삭제하시겠어요?") },
            confirmButton = {
                TextButton(onClick = {
                    val cid = confirmDeleteCommentId!!
                    confirmDeleteCommentId = null
                    scope.launch {
                        selected?.let { viewModel.deleteComment(it.postId, cid) }
                    }
                }) { Text("삭제", color = Color.Red) }
            },
            dismissButton = {
                TextButton(onClick = { confirmDeleteCommentId = null }) { Text("취소") }
            }
        )
    }

    // 댓글 수정 다이얼로그
    if (showEditCommentDialog) {
        AlertDialog(
            onDismissRequest = { showEditCommentDialog = false },
            title = { Text("댓글 수정") },
            text = {
                OutlinedTextField(
                    value = editCommentContent,
                    onValueChange = { editCommentContent = it },
                    label = { Text("댓글 내용") },
                    modifier = Modifier.fillMaxWidth().height(100.dp),
                    colors = TextFieldDefaults.outlinedTextFieldColors(
                        focusedBorderColor = redMain,
                        cursorColor = redMain,
                        focusedLabelColor = redMain
                    )
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            selected?.let {
                                viewModel.editComment(it.postId, editCommentId, editCommentContent)
                            }
                        }
                        showEditCommentDialog = false
                    },
                    enabled = editCommentContent.isNotBlank(),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = redMain,
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Text("수정")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEditCommentDialog = false }) {
                    Text("취소")
                }
            }
        )
    }
}

/**
 * 정렬 드롭다운 컴포넌트
 */
@Composable
private fun SortDropdown(
    currentSort: SortType,
    onSortChange: (SortType) -> Unit,
    modifier: Modifier = Modifier
) {
    val redMain = Color(0xFFD32F2F)
    var expanded by remember { mutableStateOf(false) }

    Box(modifier = modifier) {
        // 드롭다운 트리거 버튼
        OutlinedButton(
            onClick = { expanded = true },
            modifier = Modifier.height(40.dp),
            border = BorderStroke(1.dp, Color(0xFFE0E0E0)),
            colors = ButtonDefaults.outlinedButtonColors(
                backgroundColor = Color.White,
                contentColor = Color(0xFF666666)
            ),
            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
        ) {
            Icon(
                imageVector = getSortIcon(currentSort),
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF666666)
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = currentSort.displayName,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium
            )
            Spacer(Modifier.width(4.dp))
            Icon(
                imageVector = if (expanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                contentDescription = null,
                modifier = Modifier.size(16.dp),
                tint = Color(0xFF666666)
            )
        }

        // 드롭다운 메뉴
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.width(160.dp),
            offset = DpOffset(0.dp, 4.dp)
        ) {
            SortType.values().forEach { option ->
                DropdownMenuItem(
                    onClick = {
                        onSortChange(option)
                        expanded = false
                    },
                    modifier = Modifier.height(44.dp)
                ) {
                    Icon(
                        imageVector = getSortIcon(option),
                        contentDescription = null,
                        modifier = Modifier.size(18.dp),
                        tint = if (option == currentSort) redMain else Color(0xFF666666)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = option.displayName,
                        fontSize = 14.sp,
                        color = if (option == currentSort) redMain else Color(0xFF333333),
                        fontWeight = if (option == currentSort) FontWeight.Bold else FontWeight.Normal
                    )

                    // 현재 선택된 항목에 체크 표시
                    if (option == currentSort) {
                        Spacer(Modifier.weight(1f))
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                            tint = redMain
                        )
                    }
                }
            }
        }
    }
}

/**
 * 정렬 타입에 따른 아이콘 반환 (통합 방식)
 */
private fun getSortIcon(sortType: SortType): ImageVector {
    return when (sortType) {
        SortType.RECENT_CREATED -> Icons.Default.Schedule
        SortType.OLDEST_CREATED -> Icons.Default.History
        SortType.HIGH_VIEW_COUNT -> Icons.Default.TrendingUp
        SortType.LOW_VIEW_COUNT -> Icons.Default.TrendingDown
        SortType.TITLE_ASC -> Icons.Default.SortByAlpha
        SortType.TITLE_DESC -> Icons.Default.TextRotateVertical
    }
}

/**
 * 게시글 아이템 컴포넌트 - 목록에서 개별 게시글을 표시
 */
@Composable
private fun PostItem(
    post: PostSummaryResponse,
    onPostClick: () -> Unit
) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { onPostClick() }
            .padding(vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 게시글 정보 영역
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                // 게시글 제목
                Text(
                    text = post.title,
                    fontSize = 16.sp,
                    modifier = Modifier.weight(1f, fill = false),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 이미지 첨부 표시 아이콘
                if (post.isContainingImg) {
                    Spacer(Modifier.width(6.dp))
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = "이미지 첨부됨",
                        tint = Color.Gray,
                        modifier = Modifier.size(16.dp)
                    )
                }
            }

            // 작성자 정보 및 작성 시간
            Row {
                Text("${post.nickname} ", style = MaterialTheme.typography.body2)
                RelativeTimeText(
                    createdAt = post.createdAt,
                    style = MaterialTheme.typography.body2
                )
                Text(text=" 조회 ${post.viewCount}", style = MaterialTheme.typography.body2)
            }
        }

        // 댓글 수 표시 박스
        Box(
            modifier = Modifier
                .padding(start = 8.dp)
                .width(48.dp)
                .height(56.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(Color(0xFFF2F3F5)),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
                modifier = Modifier.padding(vertical = 6.dp)
            ) {
                Text(
                    text = post.commentCount.toString(),
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = Color(0xFF202124)
                )
                Spacer(Modifier.height(2.dp))
                Text(
                    text = "댓글",
                    fontSize = 12.sp,
                    color = Color(0xFF80868B)
                )
            }
        }
    }
}

/**
 * 상대적 시간을 표시하는 Composable 함수
 * 1분마다 자동으로 업데이트됨
 */
@Composable
private fun RelativeTimeText(
    createdAt: String,
    style: TextStyle = MaterialTheme.typography.body2,
    color: Color = Color.Unspecified
) {
    var timeText by remember(createdAt) { mutableStateOf(getRelativeTimeKorean(createdAt)) }

    // 1분마다 시간 업데이트
    LaunchedEffect(createdAt) {
        while (true) {
            delay(60_000)
            timeText = getRelativeTimeKorean(createdAt)
        }
    }
    Text(timeText, style = style, color = color)
}

/**
 * 게시글 작성/수정을 위한 화면 Composable 함수
 */
@Composable
private fun WriteEditScreen(
    title: String,
    titleValue: String,
    contentValue: String,
    attachedFiles: List<AttachedFile>,
    isPosting: Boolean,
    paddingValues: PaddingValues,
    onTitleChange: (String) -> Unit,
    onContentChange: (String) -> Unit,
    onRemoveAttachedFile: (AttachedFile) -> Unit,
    onBack: () -> Unit,
    onSave: () -> Unit,
    saveButtonText: String,
    onAddFiles: () -> Unit,
    viewModel: BoardViewModel
) {
    val redMain = Color(0xFFD32F2F)
    val maxFiles = 1 // 최대 첨부 파일 개수
    val canAddMore = attachedFiles.size < maxFiles
    val context = LocalContext.current

    // 용량 초과 파일이 있는지 확인 (5MB 제한)
    val hasOversizedFiles = attachedFiles.any { file ->
        when (file) {
            is AttachedFile.NewFile -> {
                val fileSize = getFileSizeFromUri(context, file.uri)
                fileSize > (5L * 1024 * 1024)
            }
            is AttachedFile.ExistingFile -> false
        }
    }

    Column(Modifier.fillMaxSize().padding(paddingValues)) {
        // 상단 앱바 - 뒤로가기, 제목, 저장 버튼
        TopAppBar(
            title = {
                Text(
                    text = title,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            navigationIcon = {
                IconButton(onClick = onBack) {
                    Icon(Icons.Default.ArrowBack, contentDescription = "뒤로")
                }
            },
            actions = {
                TextButton(
                    onClick = onSave,
                    // 저장 버튼 활성화 조건: 제목/내용 입력, 전송중 아님, 용량 초과 파일 없음
                    enabled = titleValue.isNotBlank() && contentValue.isNotBlank() && !isPosting && !hasOversizedFiles,
                    modifier = Modifier.padding(end = 8.dp),
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = if (titleValue.isNotBlank() && contentValue.isNotBlank() && !isPosting && !hasOversizedFiles) {
                            redMain
                        } else {
                            Color.Gray
                        },
                        disabledContentColor = Color.Gray
                    )
                ) {
                    Text(
                        text = saveButtonText,
                        color = if (titleValue.isNotBlank() && contentValue.isNotBlank() && !isPosting && !hasOversizedFiles) {
                            redMain
                        } else {
                            Color.Gray
                        }
                    )
                }
            },
            backgroundColor = Color.White,
            contentColor = Color.Black,
            elevation = 4.dp
        )

        Column(Modifier.fillMaxSize().padding(16.dp).padding(bottom = 80.dp)) {
            // 제목 입력 필드 (100자 제한)
            OutlinedTextField(
                value = titleValue,
                onValueChange = { newValue ->
                    if (newValue.length <= 100) {
                        onTitleChange(newValue)
                    }
                },
                label = { Text("제목 (${titleValue.length}/100)") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain,
                    focusedLabelColor = redMain
                )
            )

            Spacer(Modifier.height(16.dp))

            // 용량 초과 경고 메시지
            if (hasOversizedFiles) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = Color(0xFFFFEBEE),
                    border = BorderStroke(1.dp, Color.Red),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.Warning, contentDescription = null, tint = Color.Red, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "5MB를 초과하는 파일이 있습니다. 해당 파일을 제거하거나 크기를 줄여주세요.",
                            color = Color.Red,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 첨부 파일 표시
            if (attachedFiles.isNotEmpty()) {
                AttachedFilesCard(
                    attachedFiles = attachedFiles,
                    maxFiles = maxFiles,
                    onRemoveAttachedFile = onRemoveAttachedFile,
                    viewModel = viewModel
                )
                Spacer(Modifier.height(16.dp))
            }

            // 게시물 작성 중 상태 표시
            if (isPosting) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    backgroundColor = redMain.copy(alpha = 0.1f)
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("게시물 작성 중...", fontSize = 12.sp, color = redMain)
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth(), color = redMain)
                    }
                }
                Spacer(Modifier.height(16.dp))
            }

            // 파일 첨부 버튼
            Button(
                onClick = onAddFiles,
                modifier = Modifier.fillMaxWidth(),
                enabled = !isPosting && canAddMore,
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = if (canAddMore) redMain else Color.Gray,
                    contentColor = Color.White,
                    disabledBackgroundColor = Color.Gray,
                    disabledContentColor = Color.White.copy(alpha = 0.5f)
                )
            ) {
                Icon(Icons.Default.Image, contentDescription = null, tint = Color.White)
                Spacer(Modifier.width(8.dp))
                Text(if (canAddMore) "이미지 첨부" else "이미지 첨부 (최대 1개)")
            }

            // 파일 제한 안내
            Spacer(Modifier.height(8.dp))
            Text(
                text = if (attachedFiles.size >= maxFiles) {
                    "이미지는 1개만 첨부할 수 있습니다"
                } else {
                    "• 이미지 파일만 첨부 가능 (JPG, PNG, GIF 등)\n• 파일당 최대 5MB까지 업로드 가능"
                },
                fontSize = if (attachedFiles.size >= maxFiles) 12.sp else 11.sp,
                color = if (attachedFiles.size >= maxFiles) Color.Red else Color.Gray,
                modifier = Modifier.fillMaxWidth(),
                textAlign = TextAlign.Center,
                lineHeight = 14.sp
            )

            Spacer(Modifier.height(16.dp))

            // 내용 입력 필드 (500자 제한)
            OutlinedTextField(
                value = contentValue,
                onValueChange = { newValue ->
                    if (newValue.length <= 500) {
                        onContentChange(newValue)
                    }
                },
                label = { Text("내용 (${contentValue.length}/500)") },
                modifier = Modifier.fillMaxWidth().weight(1f),
                colors = TextFieldDefaults.outlinedTextFieldColors(
                    focusedBorderColor = redMain,
                    cursorColor = redMain,
                    focusedLabelColor = redMain
                )
            )
        }
    }
}

/**
 * 첨부 파일 카드 컴포넌트 - 첨부된 파일들을 목록으로 표시
 */
@Composable
private fun AttachedFilesCard(
    attachedFiles: List<AttachedFile>,
    maxFiles: Int,
    onRemoveAttachedFile: (AttachedFile) -> Unit,
    viewModel: BoardViewModel
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface
    ) {
        Column(Modifier.padding(12.dp)) {
            // 첨부파일 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Attachment, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("첨부된 파일", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }

                // 파일 개수 표시
                Text(
                    "${attachedFiles.size}/${maxFiles}",
                    fontSize = 12.sp,
                    color = if (attachedFiles.size >= maxFiles) Color.Red else Color.Gray,
                    fontWeight = if (attachedFiles.size >= maxFiles) FontWeight.Bold else FontWeight.Normal
                )
            }
            Spacer(Modifier.height(8.dp))

            // 첨부된 파일 목록
            attachedFiles.forEachIndexed { index, attachedFile ->
                when (attachedFile) {
                    is AttachedFile.ExistingFile -> {
                        ExistingFileItem(
                            fileInfo = attachedFile.fileInfo,
                            onRemove = { onRemoveAttachedFile(attachedFile) }
                        )
                    }
                    is AttachedFile.NewFile -> {
                        NewFileItem(
                            uri = attachedFile.uri,
                            index = index,
                            viewModel = viewModel,
                            onRemove = { onRemoveAttachedFile(attachedFile) }
                        )
                    }
                }
                if (index < attachedFiles.size - 1) {
                    Spacer(Modifier.height(4.dp))
                }
            }
        }
    }
}

/**
 * 새로 추가된 파일을 표시하는 아이템 Composable
 */
@Composable
private fun NewFileItem(
    uri: Uri,
    index: Int,
    viewModel: BoardViewModel,
    onRemove: () -> Unit
) {
    val context = LocalContext.current
    val fileSize = remember(uri) { getFileSizeFromUri(context, uri) }
    val isOversized = fileSize > (5L * 1024 * 1024) // 5MB 초과 확인
    val fileSizeText = if (fileSize > 0) {
        val mb = fileSize / (1024.0 * 1024.0)
        String.format("%.1f MB", mb)
    } else {
        "크기 알 수 없음"
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            Icons.Default.Attachment,
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = if (isOversized) Color.Red else Color(0xFF2196F3)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = uri.lastPathSegment ?: "파일 ${index + 1}",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    color = if (isOversized) Color.Red else Color.Black
                )

                // 용량 초과 경고 아이콘
                if (isOversized) {
                    Spacer(Modifier.width(4.dp))
                    Icon(
                        Icons.Default.Warning,
                        contentDescription = "용량 초과",
                        tint = Color.Red,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }

            Text(
                text = if (isOversized) {
                    "용량 초과 • $fileSizeText"
                } else {
                    "새로 추가된 파일 • $fileSizeText"
                },
                fontSize = 10.sp,
                color = if (isOversized) Color.Red else Color.Gray
            )

            if (isOversized) {
                Text(
                    text = "5MB를 초과하여 업로드할 수 없습니다",
                    fontSize = 9.sp,
                    color = Color.Red,
                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                )
            }
        }

        // 파일 제거 버튼
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "파일 제거",
                tint = Color.Red,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 기존 파일을 표시하는 아이템 Composable
 */
@Composable
private fun ExistingFileItem(
    fileInfo: FileInfo,
    onRemove: () -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = if (fileInfo.contentType.startsWith("image/")) {
                Icons.Default.Image
            } else {
                Icons.Default.Attachment
            },
            contentDescription = null,
            modifier = Modifier.size(16.dp),
            tint = Color(0xFF4CAF50)
        )

        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = fileInfo.originalFileName,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "기존 파일 • ${formatFileSize(fileInfo.fileSize)}",
                fontSize = 10.sp,
                color = Color.Gray
            )
        }

        // 파일 제거 버튼
        IconButton(
            onClick = onRemove,
            modifier = Modifier.size(24.dp)
        ) {
            Icon(
                Icons.Default.Close,
                contentDescription = "파일 제거",
                tint = Color.Red,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

/**
 * 게시글 상세보기 화면을 구성하는 Composable 함수
 */
@Composable
private fun DetailScreen(
    post: PostDetailResponse,
    comments: List<CommentResponse>,
    paddingValues: PaddingValues,
    viewModel: BoardViewModel,
    onBack: () -> Unit,
    onSendComment: (String) -> Unit,
    onDeleteComment: (Long) -> Unit,
    onEditPost: (Long, String, String) -> Unit,
    onEditComment: (Long, String) -> Unit,
    onDeletePost: (Long) -> Unit,
    isOwner: Boolean,
) {
    val redMain = Color(0xFFD32F2F)
    var draft by remember { mutableStateOf("") } // 댓글 입력 임시저장

    // 전체화면 이미지 뷰어 관련 상태들
    var showFullScreenImage by remember { mutableStateOf(false) }
    var fullScreenImageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var fullScreenImageName by remember { mutableStateOf("") }
    var fullScreenFileId by remember { mutableStateOf(0L) }

    val attachedFiles by viewModel.attachedFiles.collectAsState()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().padding(paddingValues)) {
            // 고정된 상단 헤더 - 뒤로가기, 제목, 수정/삭제 버튼
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                elevation = 4.dp
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "뒤로", tint = Color.Black)
                    }

                    Text(
                        text = "커뮤니티",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Black,
                        modifier = Modifier.weight(1f)
                    )

                    // 게시글 소유자만 수정/삭제 버튼 표시
                    if (isOwner) {
                        IconButton(onClick = {
                            viewModel.startEditWithSelectedPost()
                            onEditPost(post.postId, post.title, post.content)
                        }) {
                            Icon(Icons.Default.Edit, contentDescription = "게시글 수정", tint = Color.Gray)
                        }

                        IconButton(onClick = { onDeletePost(post.postId) }) {
                            Icon(Icons.Default.Delete, contentDescription = "게시글 삭제", tint = Color.Red)
                        }
                    }
                }
            }

            // 스크롤 가능한 전체 콘텐츠 영역
            LazyColumn(
                Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 140.dp)
            ) {
                item {
                    // 게시글 상세 내용
                    PostDetailContent(post = post)
                    Spacer(Modifier.height(20.dp))

                    // 첨부 파일 영역
                    val currentFiles = attachedFiles.filterIsInstance<AttachedFile.ExistingFile>().map { it.fileInfo }
                    if (currentFiles.isNotEmpty()) {
                        AttachedFilesSection(
                            files = currentFiles,
                            onImageClick = { bitmap, fileName, fileId ->
                                fullScreenImageBitmap = bitmap
                                fullScreenImageName = fileName
                                fullScreenFileId = fileId
                                showFullScreenImage = true
                            }
                        )
                        Spacer(Modifier.height(20.dp))
                    }

                    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp, modifier = Modifier.padding(vertical = 16.dp))

                    // 댓글 헤더
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Comment, contentDescription = null, tint = Color(0xFF666666), modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(
                            "댓글 ${comments.size}개",
                            style = MaterialTheme.typography.subtitle2.copy(fontSize = 14.sp),
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF666666)
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                }

                // 댓글 목록
                items(comments) { comment ->
                    CommentItem(
                        comment = comment,
                        viewModel = viewModel,
                        postId = post.postId,
                        onEdit = { onEditComment(comment.id, comment.content) },
                        onDelete = { onDeleteComment(comment.id) }
                    )
                }
            }

            // 하단 댓글 입력창 - 고정 위치
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = Color.White,
                elevation = 8.dp
            ) {
                Row(
                    Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 80.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("댓글을 입력하세요", color = Color(0xFF999999)) },
                        modifier = Modifier.weight(1f),
                        maxLines = 3,
                        shape = RoundedCornerShape(20.dp),
                        colors = TextFieldDefaults.outlinedTextFieldColors(
                            focusedBorderColor = redMain,
                            cursorColor = redMain,
                            backgroundColor = Color(0xFFF8F9FA)
                        ),
                    )

                    Spacer(Modifier.width(8.dp))

                    // 댓글 전송 버튼 - 내용이 있을 때만 활성화
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(
                                color = if (draft.isNotBlank()) redMain else Color(0xFFE0E0E0),
                                shape = CircleShape
                            )
                            .clickable(enabled = draft.isNotBlank()) {
                                if (draft.isNotBlank()) {
                                    onSendComment(draft)
                                    draft = ""
                                }
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            Icons.Default.Send,
                            contentDescription = "전송",
                            tint = if (draft.isNotBlank()) Color.White else Color(0xFF999999),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // 전체화면 이미지 뷰어
        if (showFullScreenImage && fullScreenImageBitmap != null) {
            FullScreenImageViewer(
                imageBitmap = fullScreenImageBitmap!!,
                fileName = fullScreenImageName,
                fileId = fullScreenFileId,
                onDismiss = {
                    showFullScreenImage = false
                    fullScreenImageBitmap = null
                    fullScreenImageName = ""
                    fullScreenFileId = 0L
                }
            )
        }
    }
}

/**
 * 게시글 상세 내용 컴포넌트 - 제목, 작성자 정보, 내용을 표시
 */
@Composable
private fun PostDetailContent(post: PostDetailResponse) {
    // 게시글 제목
    Text(
        text = post.title,
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
        color = Color.Black,
        lineHeight = 24.sp
    )

    Spacer(Modifier.height(12.dp))

    // 작성자 정보 영역
    Row(verticalAlignment = Alignment.CenterVertically) {
        // 프로필 이미지
        ProfileImage(
            base64Image = post.authorProfilePic,
            nickname = post.nickname,
            modifier = Modifier.size(60.dp)
        )

        Spacer(Modifier.width(12.dp))

        Column {
            Text(
                text = post.nickname ?: "알 수 없음",
                fontSize = 14.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Black
            )

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = post.authorUsername ?: "알 수 없음",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )

                Text(
                    text = " • ",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )

                RelativeTimeText(
                    createdAt = post.createdAt,
                    style = MaterialTheme.typography.caption.copy(fontSize = 12.sp),
                    color = Color(0xFF666666)
                )

                Text(
                    text = " • 조회 ${post.viewCount}",
                    fontSize = 12.sp,
                    color = Color(0xFF666666)
                )
            }
        }
    }

    Spacer(Modifier.height(16.dp))
    Divider(color = Color(0xFFE0E0E0), thickness = 1.dp)
    Spacer(Modifier.height(16.dp))

    // 게시글 내용
    Text(
        text = post.content,
        style = MaterialTheme.typography.body1.copy(
            fontSize = 15.sp,
            lineHeight = 22.sp
        ),
        color = Color.Black,
        modifier = Modifier.fillMaxWidth()
    )
}

/**
 * Base64로 인코딩된 프로필 이미지를 디코딩하여 표시하는 Composable
 */
@Composable
private fun ProfileImage(
    base64Image: String?,
    nickname: String?,
    modifier: Modifier = Modifier
) {
    val redMain = Color(0xFFD32F2F)
    var imageBitmap by remember(base64Image) { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember(base64Image) { mutableStateOf(false) }
    var hasError by remember(base64Image) { mutableStateOf(false) }

    // Base64 이미지 디코딩 작업
    LaunchedEffect(base64Image) {
        if (!base64Image.isNullOrEmpty() && base64Image != "null") {
            isLoading = true
            hasError = false

            withContext(Dispatchers.IO) {
                try {
                    // Base64 헤더 제거 후 디코딩
                    val cleanBase64 = base64Image.substringAfter("base64,").ifEmpty { base64Image }

                    // Base64 문자열 유효성 검사
                    if (cleanBase64.isBlank()) {
                        withContext(Dispatchers.Main) {
                            hasError = true
                            isLoading = false
                        }
                        return@withContext
                    }

                    val imageBytes = try {
                        Base64.decode(cleanBase64, Base64.NO_WRAP)
                    } catch (e: IllegalArgumentException) {
                        // Base64 디코딩 실패 시 다른 플래그로 재시도
                        Base64.decode(cleanBase64, Base64.DEFAULT)
                    }

                    if (imageBytes.isEmpty()) {
                        withContext(Dispatchers.Main) {
                            hasError = true
                            isLoading = false
                        }
                        return@withContext
                    }

                    // 이미지 크기 최적화를 위한 옵션 설정
                    val options = BitmapFactory.Options().apply {
                        inJustDecodeBounds = true
                    }
                    BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                    // 실제 비트맵 생성 전 유효성 검사
                    if (options.outWidth <= 0 || options.outHeight <= 0) {
                        withContext(Dispatchers.Main) {
                            hasError = true
                            isLoading = false
                        }
                        return@withContext
                    }

                    options.inJustDecodeBounds = false
                    options.inSampleSize = calculateProfileSampleSize(options, 100, 100)
                    options.inPreferredConfig = Bitmap.Config.RGB_565

                    val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                    if (bitmap != null && !bitmap.isRecycled) {
                        withContext(Dispatchers.Main) {
                            imageBitmap = bitmap.asImageBitmap()
                            isLoading = false
                        }
                    } else {
                        withContext(Dispatchers.Main) {
                            hasError = true
                            isLoading = false
                        }
                    }
                } catch (e: Exception) {
                    withContext(Dispatchers.Main) {
                        hasError = true
                        isLoading = false
                    }
                }
            }
        } else {
            isLoading = false
            hasError = false
            imageBitmap = null
        }
    }

    Box(
        modifier = modifier.clip(CircleShape),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoading -> {
                // 로딩 중 표시
                Box(
                    modifier = Modifier.fillMaxSize().background(Color.Gray.copy(alpha = 0.3f)),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = redMain
                    )
                }
            }
            imageBitmap != null && !hasError -> {
                // 이미지 표시
                Image(
                    bitmap = imageBitmap!!,
                    contentDescription = "${nickname ?: "사용자"}의 프로필 이미지",
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            }
            else -> {
                // 기본 프로필 아이콘 표시
                Box(
                    modifier = Modifier.fillMaxSize().background(Color(0xFFE0E0E0)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Default.Person,
                        contentDescription = "기본 프로필",
                        modifier = Modifier.size(16.dp),
                        tint = Color.Gray
                    )
                }
            }
        }
    }
}

/**
 * 프로필 이미지용 샘플 크기 계산 함수 - 메모리 효율성을 위해 이미지 크기 조정
 */
private fun calculateProfileSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // 요구되는 크기보다 작아질 때까지 샘플 크기 증가
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * 게시글에 첨부된 파일들을 표시하는 섹션 Composable 함수
 */
@Composable
private fun AttachedFilesSection(
    files: List<FileInfo>,
    onImageClick: (ImageBitmap, String, Long) -> Unit = { _, _, _ -> }
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 4.dp
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            // 첨부파일 헤더
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    Icons.Default.Attachment,
                    contentDescription = null,
                    tint = Color(0xFFD32F2F),
                    modifier = Modifier.size(20.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "첨부파일 (${files.size}개)",
                    style = MaterialTheme.typography.subtitle2,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(Modifier.height(12.dp))

            // 파일 목록 - 타입에 따라 다르게 표시
            files.forEach { file ->
                if (file.contentType.startsWith("image/")) {
                    ImageFileItem(
                        file = file,
                        onImageClick = { bitmap ->
                            onImageClick(bitmap, file.originalFileName, file.fileId)
                        }
                    )
                } else {
                    GeneralFileItem(file = file)
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

/**
 * 이미지 파일 표시 함수 - 서버에서 이미지를 다운로드하여 표시
 */
@Composable
private fun ImageFileItem(
    file: FileInfo,
    onImageClick: (ImageBitmap) -> Unit = {}
) {
    var imageBitmap by remember { mutableStateOf<ImageBitmap?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var hasError by remember { mutableStateOf(false) }

    // 파일 ID가 변경되면 이미지 다운로드 시작
    DisposableEffect(file.fileId) {
        var job: Job? = null

        job = CoroutineScope(Dispatchers.IO).launch {
            try {
                isLoading = true
                hasError = false

                // API를 통해 파일 다운로드
                val api = BoardApi.create()
                val response = api.downloadFile(file.fileId)

                if (response.isSuccessful && response.body() != null) {
                    val imageBytes = response.body()!!.bytes()

                    if (imageBytes.isNotEmpty()) {
                        // 이미지 디코딩 및 크기 최적화
                        val options = BitmapFactory.Options().apply {
                            inJustDecodeBounds = true
                        }
                        BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                        options.inJustDecodeBounds = false
                        options.inSampleSize = calculateInSampleSize(options, 800, 600)
                        options.inPreferredConfig = Bitmap.Config.RGB_565

                        val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, options)

                        if (bitmap != null && !bitmap.isRecycled) {
                            withContext(Dispatchers.Main) {
                                imageBitmap = bitmap.asImageBitmap()
                            }
                        } else {
                            hasError = true
                        }
                    } else {
                        hasError = true
                    }
                } else {
                    hasError = true
                }
            } catch (e: Exception) {
                hasError = true
            } finally {
                withContext(Dispatchers.Main) {
                    isLoading = false
                }
            }
        }

        // 컴포넌트가 해제될 때 작업 취소 및 메모리 정리
        onDispose {
            job?.cancel()
            imageBitmap = null
        }
    }

    when {
        isLoading -> {
            // 로딩 상태 표시
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Gray.copy(alpha = 0.2f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    CircularProgressIndicator(
                        color = Color(0xFFD32F2F),
                        modifier = Modifier.size(32.dp)
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("이미지 로딩 중...", style = MaterialTheme.typography.caption)
                }
            }
        }
        hasError -> {
            // 에러 상태 표시
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(150.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color.Red.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.Red,
                        modifier = Modifier.size(48.dp)
                    )
                    Text(
                        "이미지를 불러올 수 없습니다",
                        style = MaterialTheme.typography.caption,
                        color = Color.Red,
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
        imageBitmap != null -> {
            // 이미지 표시 - 클릭 시 전체화면으로 확대
            Image(
                bitmap = imageBitmap!!,
                contentDescription = null,
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .heightIn(min = 100.dp, max = 300.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .clickable { onImageClick(imageBitmap!!) },
                contentScale = ContentScale.Crop,
                alignment = Alignment.TopCenter
            )
        }
    }
}

/**
 * 이미지 샘플링 계산 함수 - 메모리 효율성을 위해 적절한 크기로 조정
 */
private fun calculateInSampleSize(
    options: BitmapFactory.Options,
    reqWidth: Int,
    reqHeight: Int
): Int {
    val height = options.outHeight
    val width = options.outWidth
    var inSampleSize = 1

    if (height > reqHeight || width > reqWidth) {
        val halfHeight = height / 2
        val halfWidth = width / 2

        // 요구되는 크기보다 작아질 때까지 샘플 크기를 2배씩 증가
        while (halfHeight / inSampleSize >= reqHeight && halfWidth / inSampleSize >= reqWidth) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}

/**
 * 일반 파일을 표시하는 아이템 Composable 함수
 * 이미지가 아닌 파일들에 대한 다운로드 링크 제공
 */
@Composable
private fun GeneralFileItem(file: FileInfo) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { /* TODO: 일반 파일 다운로드 로직 추가 */ }
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            Icons.Default.Attachment,
            contentDescription = null,
            tint = Color(0xFFD32F2F),
            modifier = Modifier.size(20.dp)
        )
        Spacer(Modifier.width(8.dp))

        Column(modifier = Modifier.weight(1f)) {
            // 파일명 표시
            Text(
                text = file.originalFileName,
                style = MaterialTheme.typography.body2,
                fontWeight = FontWeight.Medium
            )
            // 파일 타입과 크기 표시
            Row {
                Text(
                    text = file.contentType,
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = formatFileSize(file.fileSize),
                    style = MaterialTheme.typography.caption,
                    color = Color.Gray
                )
            }
        }

        // 다운로드 아이콘
        Icon(
            Icons.Default.Download,
            contentDescription = "다운로드",
            tint = Color(0xFFD32F2F),
            modifier = Modifier.size(20.dp)
        )
    }
}

/**
 * 개별 댓글을 표시하는 아이템 Composable 함수
 * 댓글 작성자만 수정/삭제 가능
 */
@Composable
private fun CommentItem(
    comment: CommentResponse,
    viewModel: BoardViewModel,
    postId: Long,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
        backgroundColor = MaterialTheme.colors.surface,
        elevation = 2.dp
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                // 댓글 내용 영역
                Column(modifier = Modifier.weight(1f)) {
                    // 작성자 닉네임
                    Text(
                        text = comment.nickname,
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                    // 작성자 사용자명
                    Text(
                        text = comment.username,
                        style = MaterialTheme.typography.subtitle2,
                        fontWeight = FontWeight.Bold
                    )
                    // 댓글 내용
                    Text(
                        text = comment.content,
                        style = MaterialTheme.typography.body2,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                    // 작성 시간
                    RelativeTimeText(
                        createdAt = comment.createdAt,
                        style = MaterialTheme.typography.caption,
                        color = Color.Gray
                    )
                }

                // 댓글 작성자만 수정/삭제 버튼 표시
                if (viewModel.isCommentAuthor(comment)) {
                    Row {
                        IconButton(
                            onClick = onEdit,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Edit,
                                contentDescription = "댓글 수정",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Gray
                            )
                        }

                        IconButton(
                            onClick = onDelete,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                Icons.Default.Delete,
                                contentDescription = "댓글 삭제",
                                modifier = Modifier.size(16.dp),
                                tint = Color.Red
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * 파일 크기를 사람이 읽기 쉬운 형태로 포맷팅하는 유틸리티 함수
 * bytes -> KB -> MB -> GB 단위로 변환
 */
private fun formatFileSize(bytes: Long): String {
    val kb = bytes / 1024.0
    val mb = kb / 1024.0
    val gb = mb / 1024.0

    return when {
        gb >= 1 -> String.format("%.1f GB", gb)
        mb >= 1 -> String.format("%.1f MB", mb)
        kb >= 1 -> String.format("%.1f KB", kb)
        else -> "$bytes bytes"
    }
}

/**
 * 개선된 전체화면 이미지 뷰어 Composable 함수
 * 긴 이미지에서도 다운로드 버튼이 항상 보이도록 개선
 */
@Composable
fun FullScreenImageViewer(
    imageBitmap: ImageBitmap,
    fileName: String,
    fileId: Long,
    onDismiss: () -> Unit
) {
    // 이미지 변환 상태들
    var scale by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // 다운로드 관련 상태들
    var isDownloading by remember { mutableStateOf(false) }
    var showSuccessMessage by remember { mutableStateOf(false) }
    var showErrorMessage by remember { mutableStateOf(false) }
    var notificationMessage by remember { mutableStateOf("") }

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // 더블탭으로 확대/축소
            .pointerInput(Unit) {
                detectTapGestures(
                    onDoubleTap = { offset ->
                        if (scale > 1f) {
                            // 축소
                            scale = 1f
                            offsetX = 0f
                            offsetY = 0f
                        } else {
                            // 확대
                            scale = 3f
                            offsetX = (size.width / 2 - offset.x) * 2f
                            offsetY = (size.height / 2 - offset.y) * 2f
                        }
                    }
                )
            }
            // 드래그로 이미지 이동 (확대된 상태에서만)
            .pointerInput(Unit) {
                detectDragGestures { change, dragAmount ->
                    if (scale > 1f) {
                        offsetX += dragAmount.x
                        offsetY += dragAmount.y

                        // 이동 범위 제한
                        val maxOffsetX = (size.width * (scale - 1)) / 2
                        val maxOffsetY = (size.height * (scale - 1)) / 2

                        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)
                        offsetY = offsetY.coerceIn(-maxOffsetY, maxOffsetY)
                    }
                }
            }
    ) {
        // 이미지 표시 (버튼들보다 아래 레이어)
        Image(
            bitmap = imageBitmap,
            contentDescription = fileName,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer(
                    scaleX = scale,
                    scaleY = scale,
                    translationX = offsetX,
                    translationY = offsetY
                ),
            contentScale = ContentScale.Fit
        )

        // 상단 버튼들 - 반투명 배경과 함께 항상 최상단에 표시
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxWidth()
                .padding(top = 16.dp),
            color = Color.Black.copy(alpha = 0.6f),
            shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 닫기 버튼
                IconButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .size(48.dp)
                        .background(Color.White.copy(alpha = 0.2f), CircleShape)
                ) {
                    Icon(
                        Icons.Default.ArrowBack,
                        contentDescription = "닫기",
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }

                // 파일명 표시
                Text(
                    text = fileName,
                    color = Color.White,
                    style = MaterialTheme.typography.body2,
                    modifier = Modifier.weight(1f).padding(horizontal = 16.dp),
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )

                // 다운로드 버튼 - 더 눈에 잘 띄도록 개선
                IconButton(
                    onClick = {
                        scope.launch {
                            isDownloading = true
                            downloadFileWithNotification(context, fileId, fileName) { success, message ->
                                isDownloading = false
                                notificationMessage = message
                                if (success) {
                                    showSuccessMessage = true
                                    showErrorMessage = false
                                } else {
                                    showSuccessMessage = false
                                    showErrorMessage = true
                                }
                            }
                        }
                    },
                    enabled = !isDownloading,
                    modifier = Modifier
                        .size(48.dp)
                        .background(
                            if (isDownloading) Color.Gray.copy(alpha = 0.5f)
                            else Color.Red.copy(alpha = 0.8f), // 빨간색으로 변경
                            CircleShape
                        )
                ) {
                    if (isDownloading) {
                        CircularProgressIndicator(
                            color = Color.White,
                            modifier = Modifier.size(24.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(
                            Icons.Default.Download,
                            contentDescription = "다운로드",
                            tint = Color.White,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }
            }
        }

        // 성공 알림 - 위치 조정
        AnimatedVisibility(
            visible = showSuccessMessage,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LaunchedEffect(showSuccessMessage) {
                if (showSuccessMessage) {
                    delay(3000)
                    showSuccessMessage = false
                }
            }

            Card(
                modifier = Modifier
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp) // 상단 버튼 아래로 위치 조정
                    .fillMaxWidth(),
                backgroundColor = Color(0xFF4CAF50),
                shape = RoundedCornerShape(8.dp),
                elevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Text(
                        text = "사진 저장이 완료되었습니다.",
                        color = Color.White,
                        style = MaterialTheme.typography.body1,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }

        // 실패 알림 - 위치 조정
        AnimatedVisibility(
            visible = showErrorMessage,
            enter = slideInVertically(
                initialOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeIn(),
            exit = slideOutVertically(
                targetOffsetY = { -it },
                animationSpec = tween(durationMillis = 300)
            ) + fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            LaunchedEffect(showErrorMessage) {
                if (showErrorMessage) {
                    delay(3000)
                    showErrorMessage = false
                }
            }

            Card(
                modifier = Modifier
                    .padding(top = 100.dp, start = 16.dp, end = 16.dp) // 상단 버튼 아래로 위치 조정
                    .fillMaxWidth(),
                backgroundColor = Color(0xFFE53E3E),
                shape = RoundedCornerShape(8.dp),
                elevation = 8.dp
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Default.Error,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "저장이 불가능합니다.",
                            color = Color.White,
                            style = MaterialTheme.typography.body1,
                            fontWeight = FontWeight.Medium
                        )
                        if (notificationMessage.isNotEmpty()) {
                            Text(
                                text = notificationMessage,
                                color = Color.White.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier.padding(top = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // 사용법 안내 - 하단으로 이동
        var showHint by remember { mutableStateOf(true) }

        if (showHint && !showSuccessMessage && !showErrorMessage) {
            LaunchedEffect(Unit) {
                delay(3000)
                showHint = false
            }

            AnimatedVisibility(
                visible = showHint,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter)
            ) {
                Surface(
                    modifier = Modifier
                        .padding(16.dp)
                        .fillMaxWidth(),
                    color = Color.Black.copy(alpha = 0.7f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = "더블탭으로 확대/축소, 드래그로 이동",
                        color = Color.White,
                        style = MaterialTheme.typography.caption,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

/**
 * 파일 다운로드 및 알림 표시 함수
 */
suspend fun downloadFileWithNotification(
    context: Context,
    fileId: Long,
    fileName: String,
    onResult: (Boolean, String) -> Unit
) {
    withContext(Dispatchers.IO) {
        try {
            val api = BoardApi.create()
            val response = api.downloadFile(fileId)

            if (response.isSuccessful && response.body() != null) {
                val responseBody = response.body()!!
                val fileBytes = responseBody.bytes()

                if (fileBytes.isNotEmpty()) {
                    val result = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
                        saveFileUsingMediaStoreSync(context, fileBytes, fileName)
                    } else {
                        saveFileToDownloadsSync(context, fileBytes, fileName)
                    }
                    withContext(Dispatchers.Main) {
                        onResult(result.first, result.second)
                    }
                } else {
                    withContext(Dispatchers.Main) {
                        onResult(false, "다운로드할 데이터가 없습니다")
                    }
                }
            } else {
                withContext(Dispatchers.Main) {
                    onResult(false, "HTTP ${response.code()}: ${response.message()}")
                }
            }
        } catch (e: Exception) {
            withContext(Dispatchers.Main) {
                onResult(false, "네트워크 오류: ${e.localizedMessage}")
            }
            e.printStackTrace()
        }
    }
}

/**
 * Android 10 이상에서 MediaStore를 사용하여 파일 저장
 */
@androidx.annotation.RequiresApi(android.os.Build.VERSION_CODES.Q)
private fun saveFileUsingMediaStoreSync(
    context: Context,
    fileBytes: ByteArray,
    fileName: String
): Pair<Boolean, String> {
    return try {
        val resolver = context.contentResolver

        val contentValues = android.content.ContentValues().apply {
            put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(android.provider.MediaStore.MediaColumns.MIME_TYPE, getMimeTypeFromFileName(fileName))
            put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
        }

        val uri = resolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, contentValues)

        if (uri != null) {
            resolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(fileBytes)
                outputStream.flush()
            }
            Pair(true, fileName)
        } else {
            Pair(false, "파일 저장 실패")
        }
    } catch (e: Exception) {
        Pair(false, "저장 오류: ${e.localizedMessage}")
    }
}

/**
 * Android 9 이하에서 기존 방식으로 파일 저장
 */
private fun saveFileToDownloadsSync(
    context: Context,
    fileBytes: ByteArray,
    fileName: String
): Pair<Boolean, String> {
    return try {
        val downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS)

        if (!downloadsDir.exists()) {
            downloadsDir.mkdirs()
        }

        val file = java.io.File(downloadsDir, fileName)

        var finalFile = file
        var counter = 1
        while (finalFile.exists()) {
            val nameWithoutExt = fileName.substringBeforeLast(".", fileName)
            val extension = fileName.substringAfterLast(".", "")

            finalFile = if (extension.isNotEmpty()) {
                java.io.File(downloadsDir, "${nameWithoutExt}($counter).$extension")
            } else {
                java.io.File(downloadsDir, "${nameWithoutExt}($counter)")
            }
            counter++
        }

        finalFile.writeBytes(fileBytes)

        // 미디어 스캔 실행
        android.media.MediaScannerConnection.scanFile(
            context,
            arrayOf(finalFile.absolutePath),
            null,
            null
        )

        Pair(true, finalFile.name)
    } catch (e: Exception) {
        Pair(false, "저장 오류: ${e.localizedMessage}")
    }
}

/**
 * 파일명에서 MIME 타입 추출
 */
private fun getMimeTypeFromFileName(fileName: String): String {
    val extension = fileName.substringAfterLast(".", "")
    return android.webkit.MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
        ?: "application/octet-stream"
}

/**
 * 한국어 상대시간 문자열을 생성하는 함수
 * "방금 전", "5분 전", "2시간 전" 등의 형태로 표시
 */
private fun getRelativeTimeKorean(createdAt: String): String {
    val epoch = parseToEpochMillis(createdAt) ?: return createdAt

    val now = System.currentTimeMillis()
    val diffMs = kotlin.math.max(0L, now - epoch)

    val min = diffMs / (1000 * 60)
    val hour = diffMs / (1000 * 60 * 60)
    val day = diffMs / (1000 * 60 * 60 * 24)

    return when {
        min < 1 -> "방금 전"
        min < 60 -> "${min}분 전"
        hour < 24 -> "${hour}시간 전"
        day < 7 -> "${day}일 전"
        else -> {
            // 7일 이상인 경우 날짜 형식으로 표시
            val calNow = java.util.Calendar.getInstance()
            val calPost = java.util.Calendar.getInstance().apply {
                timeInMillis = epoch
            }

            if (calNow.get(java.util.Calendar.YEAR) == calPost.get(java.util.Calendar.YEAR)) {
                // 같은 해인 경우 "월일" 형식
                java.text.SimpleDateFormat("M월 d일", java.util.Locale.getDefault())
                    .format(java.util.Date(epoch))
            } else {
                // 다른 해인 경우 "년월일" 형식
                java.text.SimpleDateFormat("yyyy년 M월 d일", java.util.Locale.getDefault())
                    .format(java.util.Date(epoch))
            }
        }
    }
}

/**
 * 날짜 문자열을 epoch milliseconds로 변환하는 함수
 * 다양한 날짜 형식을 지원
 */
private fun parseToEpochMillis(createdAt: String): Long? {
    val patterns = listOf(
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSSXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSXXX",
        "yyyy-MM-dd'T'HH:mm:ssXXX",
        "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
        "yyyy-MM-dd'T'HH:mm:ss.SSS",
        "yyyy-MM-dd'T'HH:mm:ss",
        "yyyy-MM-dd HH:mm:ss.SSSSSS",
        "yyyy-MM-dd HH:mm:ss.SSS",
        "yyyy-MM-dd HH:mm:ss",
        "yyyy-MM-dd"
    )

    for (pattern in patterns) {
        try {
            val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault()).apply {
                // TimeZone 설정 수정
                timeZone = if (pattern.contains("XXX")) {
                    java.util.TimeZone.getDefault()  // 로컬 시간대 사용
                } else {
                    java.util.TimeZone.getTimeZone("Asia/Seoul")  // 한국 시간대로 명시
                }
            }
            return formatter.parse(createdAt)?.time
        } catch (_: Exception) {
            continue
        }
    }
    return null
}

/**
 * URI에서 파일 크기를 가져오는 함수
 * content:// 스키마와 file:// 스키마 모두 지원
 */
private fun getFileSizeFromUri(context: Context, uri: Uri): Long {
    return try {
        when (uri.scheme) {
            "content" -> {
                // ContentResolver를 통해 파일 정보 조회
                val cursor = context.contentResolver.query(uri, null, null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                        if (sizeIndex >= 0) {
                            it.getLong(sizeIndex)
                        } else 0L
                    } else 0L
                } ?: 0L
            }
            "file" -> {
                // 파일 시스템에서 직접 크기 조회
                val file = java.io.File(uri.path ?: "")
                if (file.exists()) file.length() else 0L
            }
            else -> 0L
        }
    } catch (e: Exception) {
        0L
    }
}