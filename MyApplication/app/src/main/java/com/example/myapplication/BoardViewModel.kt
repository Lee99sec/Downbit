package com.example.myapplication
import AuthTokenManager
import android.content.Context
import android.net.Uri
import android.util.Base64
import android.webkit.MimeTypeMap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.launch
import kotlinx.coroutines.delay
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.google.gson.GsonBuilder
import com.google.gson.FieldNamingPolicy
import com.example.myapplication.security.E2EEncryptionUtils
import org.json.JSONArray
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
/**
 * 게시물 정렬 기준을 정의하는 enum class
 */
enum class SortType(val sortValue: String, val displayName: String) {
    RECENT_CREATED("createdAt, DESC", "최신순"),
    OLDEST_CREATED("createdAt, ASC", "오래된순"),
    HIGH_VIEW_COUNT("viewCount, DESC", "조회수 높은순"),
    LOW_VIEW_COUNT("viewCount, ASC", "조회수 낮은순"),
    TITLE_ASC("title, ASC", "제목 가나다순"),
    TITLE_DESC("title, DESC", "제목 역순"),
}

/**
 * 기존 파일과 새 파일을 구분하기 위한 sealed class
 * - ExistingFile: 서버에 이미 업로드된 파일
 * - NewFile: 사용자가 새로 선택한 파일
 */
sealed class AttachedFile {
    data class ExistingFile(val fileInfo: FileInfo) : AttachedFile()
    data class NewFile(val uri: Uri) : AttachedFile()
}

/**
 * 게시판 기능을 관리하는 ViewModel 클래스
 * AuthTokenManager와 연동하여 토큰 자동 관리 및 갱신 지원
 */
class BoardViewModel(
    private val context: Context,
    private val authTokenManager: AuthTokenManager
) : ViewModel() {

    // 토큰 상태를 실시간으로 확인하는 메서드 추가
    fun checkTokenStatus() {
        println("🔍 AuthTokenManager 로그인 상태: ${authTokenManager.isLoggedIn()}")
        val (accessToken, refreshToken) = authTokenManager.getStoredTokens()
        println("🔍 Access Token: ${accessToken?.take(20)}...")
        println("🔍 Refresh Token: ${refreshToken?.take(20)}...")
    }

    // 토큰 상태가 변경될 때 다시 로드하는 메서드
    fun refreshAfterLogin() {
        if (authTokenManager.isLoggedIn()) {
            println("✅ 로그인 후 데이터 로드 시작")
            loadCurrentUser()
            loadSummaries()
            loadMyPosts()
        }
    }

    private val gson = GsonBuilder()
        .setFieldNamingPolicy(FieldNamingPolicy.IDENTITY)
        .serializeNulls() // null 값도 직렬화
        .create()

    // ===== 상태 관리를 위한 StateFlow들 =====

    // 전체 게시글 목록 상태
    private val _summaries = MutableStateFlow<List<PostSummaryResponse>>(emptyList())
    val summaries: StateFlow<List<PostSummaryResponse>> = _summaries.asStateFlow()

    // 내 게시글 목록 상태
    private val _myPosts = MutableStateFlow<List<PostSummaryResponse>>(emptyList())
    val myPosts: StateFlow<List<PostSummaryResponse>> = _myPosts.asStateFlow()

    // 선택된 게시글 상세 정보 상태
    private val _selectedPost = MutableStateFlow<PostDetailResponse?>(null)
    val selectedPost: StateFlow<PostDetailResponse?> = _selectedPost.asStateFlow()

    // 댓글 목록 상태
    private val _comments = MutableStateFlow<List<CommentResponse>>(emptyList())
    val comments: StateFlow<List<CommentResponse>> = _comments.asStateFlow()

    // 게시글 작성/수정 중 로딩 상태
    private val _isPosting = MutableStateFlow(false)
    val isPosting: StateFlow<Boolean> = _isPosting.asStateFlow()

    // 첨부 파일 목록 상태
    private val _attachedFiles = MutableStateFlow<List<AttachedFile>>(emptyList())
    val attachedFiles: StateFlow<List<AttachedFile>> = _attachedFiles.asStateFlow()

    // 에러 메시지 상태
    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    // 현재 사용자 정보 상태
    private val _currentUser = MutableStateFlow<String?>(null)
    val currentUser: StateFlow<String?> = _currentUser.asStateFlow()

    // 수정 시작 시점의 원래 존재했던 파일 ID 스냅샷 (수정 시 파일 삭제 감지용)
    private var originalExistingFileIds: Set<Long> = emptySet()

    // 현재 정렬 기준 상태
    private val _currentSortType = MutableStateFlow(SortType.RECENT_CREATED)
    val currentSortType: StateFlow<SortType> = _currentSortType.asStateFlow()

    /**
     * 정렬 기준 변경
     */
    fun setSortType(sortType: SortType) {
        _currentSortType.value = sortType

        // 현재 로드된 목록들을 새로운 기준으로 재정렬
        loadSummaries()
        loadMyPosts()
    }

    // 레거시 호환성을 위한 selectedFiles (새로 추가된 파일의 URI만 반환)
    val selectedFiles: StateFlow<List<Uri>> =
        _attachedFiles.asStateFlow().map { attachedFiles ->
            attachedFiles.filterIsInstance<AttachedFile.NewFile>().map { it.uri }
        }.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(),
            initialValue = emptyList()
        )

    // ===== 상수 정의 =====
    companion object {
        private const val MAX_ATTACHED_FILES = 1 // 최대 첨부 파일 개수
        private const val MAX_FILE_SIZE_MB = 5L // 최대 파일 크기 (MB)
        private const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024 // 바이트 변환

        // 허용되는 이미지 파일 확장자
        private val ALLOWED_IMAGE_EXTENSIONS = setOf(
            "jpg", "jpeg", "png", "gif", "bmp", "webp", "heic", "heif"
        )

        // 허용되는 이미지 MIME 타입
        private val ALLOWED_IMAGE_MIME_TYPES = setOf(
            "image/jpeg", "image/jpg", "image/png", "image/gif",
            "image/bmp", "image/webp", "image/heic", "image/heif"
        )
    }

    // ViewModel 초기화 시 사용자 정보 및 게시글 목록 로드
    init {
        // 디버깅 정보 추가
        println("🔍 AuthTokenManager 로그인 상태: ${authTokenManager.isLoggedIn()}")

        val (accessToken, refreshToken) = authTokenManager.getStoredTokens()
        println("🔍 Access Token: ${accessToken?.take(20)}...")
        println("🔍 Refresh Token: ${refreshToken?.take(20)}...")

        if (authTokenManager.isLoggedIn()) {
            println("✅ 로그인 상태 - API 호출 시작")
            loadCurrentUser()
            loadSummaries()
            loadMyPosts()
        } else {
            println("❌ 로그인되지 않은 상태 - API 호출 생략")
        }
    }

    // ===== 에러 메시지 관리 =====

    /**
     * 에러 메시지를 설정하여 UI에 표시
     */
    fun showError(message: String) {
        _errorMessage.value = message
    }

    /**
     * 에러 메시지를 초기화 (토스트 표시 후 호출)
     */
    fun clearError() {
        _errorMessage.value = null
    }

    // ===== 사용자 인증 관련 =====

    /**
     * 현재 사용자 정보를 로드
     * 내 게시글 API를 통해 사용자명을 가져오고 SharedPreferences에 저장
     */
    private fun loadCurrentUser() {
        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                println("❌ loadCurrentUser: 토큰이 없음")
                return@launch
            }

            val request = MyPostsRequest(token = accessToken, sort = null)
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/myposts",
                "POST",
                requestJson
            ).onSuccess { responseJson ->
                try {
                    val response = gson.fromJson(responseJson, PostSummariesResponse::class.java)
                    if (response.postSummaries.isNotEmpty()) {
                        val username = response.postSummaries.first().authorUsername
                        _currentUser.value = username
                        context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE)
                            .edit()
                            .putString("username", username)
                            .apply()
                    } else {
                        // 내 게시글이 없는 경우 저장된 사용자명 사용
                        val savedUsername = context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE)
                            .getString("username", null)
                        _currentUser.value = savedUsername
                    }
                } catch (e: Exception) {
                    val savedUsername = context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE)
                        .getString("username", null)
                    _currentUser.value = savedUsername
                }
            }.onFailure {
                // API 실패 시 저장된 사용자명 사용
                val savedUsername = context.getSharedPreferences("auth_tokens", Context.MODE_PRIVATE)
                    .getString("username", null)
                _currentUser.value = savedUsername
            }
        }
    }

    /**
     * 게시글 작성자인지 확인
     */
    fun isPostAuthor(post: PostDetailResponse): Boolean {
        return currentUser.value == post.authorUsername
    }

    /**
     * 댓글 작성자인지 확인
     */
    fun isCommentAuthor(comment: CommentResponse): Boolean {
        return currentUser.value == comment.username
    }

    /**
     * 현재 선택된 게시글의 소유자인지 확인
     */
    fun isOwnerOfSelectedPost(): Boolean {
        val selectedPost = _selectedPost.value ?: return false
        return isPostAuthor(selectedPost)
    }

    // ===== 유틸리티 함수들 =====

    /**
     * 게시글 목록을 생성일 기준 내림차순으로 정렬
     */
    private fun sortPostsByCreatedAt(posts: List<PostSummaryResponse>): List<PostSummaryResponse> {
        return posts.sortedByDescending { post ->
            parseDate(post.createdAt)
        }
    }

    /**
     * 댓글 목록을 생성일 기준 내림차순으로 정렬
     */
    private fun sortCommentsByCreatedAt(comments: List<CommentResponse>): List<CommentResponse> {
        return comments.sortedByDescending { comment ->
            parseDate(comment.createdAt)
        }
    }

    /**
     * 다양한 날짜 형식의 문자열을 Long 타입 timestamp로 변환
     * 여러 패턴을 시도하여 파싱 성공률 향상
     */
    private fun parseDate(dateString: String): Long {
        val patterns = listOf(
            "yyyy-MM-dd'T'HH:mm:ss.SSSSSS",
            "yyyy-MM-dd'T'HH:mm:ss.SSS",
            "yyyy-MM-dd'T'HH:mm:ss",
            "yyyy-MM-dd HH:mm:ss.SSSSSS",
            "yyyy-MM-dd HH:mm:ss.SSS",
            "yyyy-MM-dd HH:mm:ss",
            "yyyy-MM-dd"
        )

        // 각 패턴을 순서대로 시도
        for (pattern in patterns) {
            try {
                val formatter = java.text.SimpleDateFormat(pattern, java.util.Locale.getDefault())
                return formatter.parse(dateString)?.time ?: 0L
            } catch (_: Exception) {
                continue // 실패 시 다음 패턴 시도
            }
        }
        return 0L // 모든 패턴 실패 시 기본값 반환
    }

    // ===== 게시판 핵심 기능들 =====

    /**
     * 전체 게시글 목록을 서버에서 로드
     */
    fun loadSummaries() {
        viewModelScope.launch {
            val sortValue = _currentSortType.value.sortValue
            val url = "${AuthTokenManager.BASE_URL}/board/list?sort=$sortValue"

            authTokenManager.makeAuthenticatedRequest(url, "GET")
                .onSuccess { responseJson ->
                    try {
                        val response = gson.fromJson(responseJson, PostSummariesResponse::class.java)
                        _summaries.value = response.postSummaries
                    } catch (e: Exception) {
                        showError("데이터 처리 중 오류가 발생했습니다")
                    }
                }
                .onFailure { exception ->
                    showError(exception.message ?: "게시글 목록을 불러올 수 없습니다")
                }
        }
    }

    /**
     * 내 게시글 목록을 서버에서 로드
     */
    fun loadMyPosts() {
        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                showError("로그인이 필요합니다")
                return@launch
            }

            val request = MyPostsRequest(
                token = accessToken,
                sort = _currentSortType.value.sortValue
            )
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/myposts",
                "POST",
                requestJson
            ).onSuccess { responseJson ->
                try {
                    val response = gson.fromJson(responseJson, PostSummariesResponse::class.java)
                    _myPosts.value = response.postSummaries
                } catch (e: Exception) {
                    showError("내 게시글을 불러올 수 없습니다")
                }
            }.onFailure { exception ->
                showError(exception.message ?: "내 게시글을 불러올 수 없습니다")
            }
        }
    }

    /**
     * 전체 게시글에서 키워드 검색
     * 빈 키워드인 경우 전체 목록 다시 로드
     */
    fun searchPosts(keyword: String) {
        if (keyword.isBlank()) {
            loadSummaries()
            return
        }

        viewModelScope.launch {
            val url = "${AuthTokenManager.BASE_URL}/board/search?keyword=$keyword&sort=${_currentSortType.value.sortValue}"

            authTokenManager.makeAuthenticatedRequest(url, "GET")
                .onSuccess { responseJson ->
                    try {
                        val response = gson.fromJson(responseJson, PostSummariesResponse::class.java)
                        _summaries.value = response.postSummaries
                    } catch (e: Exception) {
                        showError("검색 결과 처리 중 오류가 발생했습니다")
                    }
                }
                .onFailure { exception ->
                    showError(exception.message ?: "검색에 실패했습니다")
                }
        }
    }

    /**
     * 내 게시글에서 키워드 검색 (클라이언트 사이드 필터링)
     * 제목과 작성자명에서 검색
     */
    fun searchMyPosts(keyword: String) {
        if (keyword.isBlank()) {
            loadMyPosts()
            return
        }

        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                showError("로그인이 필요합니다")
                return@launch
            }

            val request = MyPostsRequest(
                token = accessToken,
                sort = _currentSortType.value.sortValue
            )
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/myposts",
                "POST",
                requestJson
            ).onSuccess { responseJson ->
                try {
                    val response = gson.fromJson(responseJson, PostSummariesResponse::class.java)
                    val filtered = response.postSummaries.filter {
                        it.title.contains(keyword, ignoreCase = true) ||
                                it.authorUsername.contains(keyword, ignoreCase = true)
                    }
                    _myPosts.value = filtered
                } catch (e: Exception) {
                    showError("검색 처리 중 오류가 발생했습니다")
                }
            }.onFailure { exception ->
                showError(exception.message ?: "내 게시글 검색에 실패했습니다")
            }
        }
    }

    /**
     * 특정 게시글 선택 시 상세 정보와 댓글 로드
     * 수정 모드가 아닐 때만 첨부 파일 상태를 서버 데이터로 갱신
     */
    fun selectPost(postId: Long) {
        viewModelScope.launch {
            try {
                // 게시글 상세 정보 요청
                val detailResult = authTokenManager.makeAuthenticatedRequest(
                    "${AuthTokenManager.BASE_URL}/board/post/$postId"
                )

                // 댓글 목록 요청
                val commentsResult = authTokenManager.makeAuthenticatedRequest(
                    "${AuthTokenManager.BASE_URL}/board/$postId/comments"
                )

                if (detailResult.isSuccess && commentsResult.isSuccess) {
                    val detail = gson.fromJson(detailResult.getOrNull(), PostDetailResponse::class.java)
                    val commentsType = object : TypeToken<List<CommentResponse>>() {}.type
                    val comments = gson.fromJson<List<CommentResponse>>(commentsResult.getOrNull(), commentsType)

                    _selectedPost.value = detail
                    _comments.value = sortCommentsByCreatedAt(comments)

                    // 수정 모드가 아닐 때만 파일 상태를 서버 데이터로 덮어씀
                    if (originalExistingFileIds.isEmpty()) {
                        loadExistingFiles(detail)
                    }

                    // 게시물 조회 후 목록 새로고침 (조회수 업데이트 반영)
                    delay(100)
                    loadSummaries()
                    loadMyPosts()
                } else {
                    showError("게시글을 불러올 수 없습니다")
                }
            } catch (e: Exception) {
                showError("게시글을 불러올 수 없습니다: ${e.message}")
            }
        }
    }

    /**
     * 서버에서 받은 게시글 상세 정보의 첨부 파일들을 AttachedFile 목록으로 변환
     */
    private fun loadExistingFiles(postDetail: PostDetailResponse) {
        val existingFiles = postDetail.files?.map { fileInfo ->
            AttachedFile.ExistingFile(fileInfo)
        } ?: emptyList()
        _attachedFiles.value = existingFiles
    }

    /**
     * 게시글 선택 해제 및 관련 상태 초기화
     */
    fun clearSelection() {
        _selectedPost.value = null
        _comments.value = emptyList()
    }

    // ===== 파일 첨부 관리 =====

    /**
     * URI가 이미지 파일인지 확인
     * MIME 타입과 파일 확장자 모두 체크
     */
    private fun isImageFile(uri: Uri): Boolean {
        val mimeType = getMimeType(uri)
        if (mimeType != null && ALLOWED_IMAGE_MIME_TYPES.contains(mimeType.lowercase())) {
            return true
        }

        val fileName = getFileName(uri) ?: uri.lastPathSegment ?: ""
        val extension = fileName.substringAfterLast(".", "").lowercase()
        return ALLOWED_IMAGE_EXTENSIONS.contains(extension)
    }

    /**
     * 여러 파일을 한 번에 추가
     * 이미지 파일만 허용하고, 크기 제한 및 개수 제한 검증
     * @return Pair<추가된 파일 수, 메시지 목록>
     */
    fun addFiles(uris: List<Uri>): Pair<Int, List<String>> {
        val currentFiles = _attachedFiles.value
        val currentCount = currentFiles.size

        // 최대 개수 초과 체크
        if (currentCount >= MAX_ATTACHED_FILES) {
            return Pair(0, listOf("파일은 1개까지만 첨부 가능합니다"))
        }

        val messages = mutableListOf<String>()
        val validImageFiles = mutableListOf<Uri>()
        val nonImageFiles = mutableListOf<String>()
        val oversizedFiles = mutableListOf<String>()

        // 각 파일에 대해 유효성 검사
        uris.forEach { uri ->
            val fileName = getFileName(uri) ?: uri.lastPathSegment ?: "알 수 없는 파일"

            // 이미지 파일이 아닐 경우
            if (!isImageFile(uri)) {
                nonImageFiles.add(fileName)
                return@forEach
            }

            // 파일 크기 체크
            val fileSize = getFileSize(uri)
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                val fileSizeStr = formatFileSizeToMB(fileSize)
                oversizedFiles.add("$fileName ($fileSizeStr)")
                return@forEach
            }

            // 유효한 파일로 분류
            validImageFiles.add(uri)
        }

        // 에러 메시지 생성
        if (oversizedFiles.isNotEmpty()) {
            messages.add(
                if (oversizedFiles.size == 1) {
                    "${oversizedFiles.first()}는 ${MAX_FILE_SIZE_MB}MB를 초과합니다"
                } else {
                    "${oversizedFiles.size}개 파일이 ${MAX_FILE_SIZE_MB}MB를 초과합니다"
                }
            )
        }

        if (nonImageFiles.isNotEmpty()) {
            messages.add(
                if (nonImageFiles.size == 1) {
                    "'${nonImageFiles.first()}'는 이미지 파일이 아닙니다"
                } else {
                    "${nonImageFiles.size}개 파일이 이미지 파일이 아닙니다"
                }
            )
        }

        // 유효한 파일들을 첨부 목록에 추가
        if (validImageFiles.isNotEmpty()) {
            val availableSlots = MAX_ATTACHED_FILES - currentCount
            val filesToAdd = validImageFiles.take(availableSlots)

            if (filesToAdd.size < validImageFiles.size) {
                val message = "이미지 파일 ${validImageFiles.size}개 중 ${filesToAdd.size}개만 첨부됩니다 (1개 제한)"
                android.util.Log.w("BoardViewModel", "⚠️ $message")
                messages.add(message)
            }

            val newFiles = filesToAdd.map { uri -> AttachedFile.NewFile(uri) }
            _attachedFiles.value = currentFiles + newFiles

            if (filesToAdd.isNotEmpty()) {
                messages.add("이미지 파일이 첨부되었습니다")
            }
        }

        return Pair(validImageFiles.size, messages)
    }

    /**
     * 현재 첨부된 파일들의 크기 검증
     * 새로 추가된 파일만 검증 (기존 파일은 이미 서버에 업로드되어 검증됨)
     */
    private fun validateFileSizes(): Pair<Boolean, String?> {
        val oversizedFiles = mutableListOf<String>()

        _attachedFiles.value.forEach { attachedFile ->
            when (attachedFile) {
                is AttachedFile.NewFile -> {
                    val fileSize = getFileSize(attachedFile.uri)
                    if (fileSize > MAX_FILE_SIZE_BYTES) {
                        val fileName = getFileName(attachedFile.uri) ?: "알 수 없는 파일"
                        val fileSizeStr = formatFileSizeToMB(fileSize)
                        oversizedFiles.add("$fileName ($fileSizeStr)")
                    }
                }
                is AttachedFile.ExistingFile -> {
                    // 기존 파일은 이미 서버에 업로드된 상태이므로 검증 생략
                }
            }
        }

        return if (oversizedFiles.isNotEmpty()) {
            val message = if (oversizedFiles.size == 1) {
                "${oversizedFiles.first()}가 ${MAX_FILE_SIZE_MB}MB를 초과합니다"
            } else {
                "${oversizedFiles.size}개 파일이 ${MAX_FILE_SIZE_MB}MB를 초과합니다"
            }
            Pair(false, message)
        } else {
            Pair(true, null)
        }
    }

    /**
     * 특정 첨부 파일 제거
     */
    fun removeAttachedFile(file: AttachedFile) {
        _attachedFiles.update { currentFiles ->
            currentFiles - file
        }
    }

    /**
     * URI로 파일 제거 (레거시 호환용)
     */
    fun removeFile(uri: Uri) {
        val currentFiles = _attachedFiles.value.toMutableList()
        currentFiles.removeAll { file ->
            file is AttachedFile.NewFile && file.uri == uri
        }
        _attachedFiles.value = currentFiles
    }

    /**
     * 모든 첨부 파일 제거
     */
    fun clearFiles() {
        _attachedFiles.value = emptyList()
    }

    /**
     * 게시글 수정 시작 시 기존 첨부 파일들을 로드하고 원본 상태 저장
     */
    fun startEditWithSelectedPost() {
        val detail = selectedPost.value ?: return
        val existing = detail.files.orEmpty().map { AttachedFile.ExistingFile(it) }
        _attachedFiles.value = existing
        originalExistingFileIds = detail.files?.map { it.fileId }?.toSet() ?: emptySet()
    }

    /**
     * 수정 완료 후 파일 상태 초기화
     */
    private fun clearEditFiles() {
        _attachedFiles.value = emptyList()
        originalExistingFileIds = emptySet()
    }

    // ===== 게시글 작성/수정/삭제 =====

    /**
     * E2E 암호화를 적용한 게시글 작성 (수정된 버전)
     */
    fun createPost(title: String, content: String, category: String = "COMMUNITY", onComplete: () -> Unit = {}) {
        val plainTextContent = content.replace(Regex("<[^>]*>"), "").trim()
        if (title.isBlank() || plainTextContent.isBlank()) return

        viewModelScope.launch {
            _isPosting.value = true

            try {
                android.util.Log.d("BoardViewModel", "🔄 게시글 작성 시작")

                val accessToken = authTokenManager.getValidAccessToken()
                if (accessToken == null) {
                    android.util.Log.e("BoardViewModel", "❌ AccessToken이 null!")
                    showError("로그인이 필요합니다")
                    return@launch
                }
                android.util.Log.d("BoardViewModel", "✅ AccessToken 확인: ${accessToken.take(20)}...")

                // 파일 크기 검증
                val (isValid, errorMessage) = validateFileSizes()
                if (!isValid) {
                    android.util.Log.e("BoardViewModel", "❌ 파일 크기 검증 실패: $errorMessage")
                    showError(errorMessage ?: "파일 크기 제한을 초과했습니다")
                    return@launch
                }
                android.util.Log.d("BoardViewModel", "✅ 파일 크기 검증 통과")

                // 현재 첨부된 파일들 상태 확인
                android.util.Log.d("BoardViewModel", "📁 현재 첨부된 파일 개수: ${_attachedFiles.value.size}")
                _attachedFiles.value.forEachIndexed { index, file ->
                    when (file) {
                        is AttachedFile.NewFile -> {
                            val fileName = getFileName(file.uri) ?: "unknown"
                            val fileSize = getFileSize(file.uri)
                            android.util.Log.d("BoardViewModel", "  [$index] 새 파일: $fileName (${formatFileSizeToMB(fileSize)})")
                        }
                        is AttachedFile.ExistingFile -> {
                            android.util.Log.d("BoardViewModel", "  [$index] 기존 파일: ${file.fileInfo.originalFileName}")
                        }
                    }
                }

                // 새로 추가된 파일들을 Base64로 변환
                val newFiles = _attachedFiles.value.filterIsInstance<AttachedFile.NewFile>()
                android.util.Log.d("BoardViewModel", "🔄 Base64 변환 시작 - 새 파일 개수: ${newFiles.size}")

                val fileDataList = mutableListOf<Base64FileDto>()
                newFiles.forEachIndexed { index, newFile ->
                    android.util.Log.d("BoardViewModel", "🔄 파일 [$index] 변환 시작: ${getFileName(newFile.uri)}")

                    val fileData = uriToFileData(newFile.uri)
                    if (fileData != null) {
                        android.util.Log.d("BoardViewModel", "✅ 파일 [$index] 변환 성공:")
                        android.util.Log.d("BoardViewModel", "  - 파일명: ${fileData.fileName}")
                        android.util.Log.d("BoardViewModel", "  - MIME 타입: ${fileData.contentType}")
                        android.util.Log.d("BoardViewModel", "  - Base64 데이터 길이: ${fileData.base64Data.length}")
                        android.util.Log.d("BoardViewModel", "  - Base64 시작 부분: ${fileData.base64Data.take(50)}...")
                        fileDataList.add(fileData)
                    } else {
                        android.util.Log.e("BoardViewModel", "❌ 파일 [$index] 변환 실패: ${getFileName(newFile.uri)}")
                    }
                }

                android.util.Log.d("BoardViewModel", "📊 Base64 변환 완료 - 성공: ${fileDataList.size}/${newFiles.size}")

                // E2E 암호화 적용 - 토큰까지 포함하여 모든 데이터를 암호화
                android.util.Log.d("BoardViewModel", "🔐 E2E 암호화 시작 (토큰 포함)")

                // 모든 데이터를 암호화 (토큰 포함)
                val dataToEncrypt = mutableMapOf<String, Any>()
                dataToEncrypt["token"] = accessToken
                dataToEncrypt["title"] = title
                dataToEncrypt["content"] = content
                dataToEncrypt["category"] = category

                // 파일이 있는 경우 JSONArray로 변환하여 E2E와 호환되게 처리
                if (fileDataList.isNotEmpty()) {
                    android.util.Log.d("BoardViewModel", "📎 파일 데이터를 JSON 호환 형식으로 변환")

                    // Gson으로 미리 JSON 문자열로 변환
                    val filesJsonString = gson.toJson(fileDataList.map { fileDto ->
                        mapOf(
                            "fileName" to fileDto.fileName,
                            "contentType" to fileDto.contentType,
                            "base64Data" to fileDto.base64Data
                        )
                    })

                    android.util.Log.d("BoardViewModel", "📎 Files JSON: ${filesJsonString.take(200)}...")

                    // JSON 문자열을 다시 JSONArray로 파싱하여 E2E와 호환되게 만들기
                    val filesJsonArray = org.json.JSONArray(filesJsonString)
                    dataToEncrypt["files"] = filesJsonArray

                    android.util.Log.d("BoardViewModel", "📎 JSONArray 변환 완료: ${filesJsonArray.length()}개 파일")
                } else {
                    android.util.Log.d("BoardViewModel", "📎 첨부할 파일 없음")
                }

                android.util.Log.d("BoardViewModel", "🔐 암호화할 데이터 키들: ${dataToEncrypt.keys}")

                val encryptedData = E2EEncryptionUtils.encryptData(dataToEncrypt)
                android.util.Log.d("BoardViewModel", "✅ E2E 암호화 완료")
                android.util.Log.d("BoardViewModel", "🔐 암호화된 데이터 길이: ${encryptedData.length}")
                android.util.Log.d("BoardViewModel", "🔐 암호화된 데이터 시작 부분: ${encryptedData.take(100)}...")

                val encryptedRequest = mapOf("e2edata" to encryptedData)
                val requestJson = gson.toJson(encryptedRequest)

                android.util.Log.d("BoardViewModel", "📤 최종 요청 JSON 생성")
                android.util.Log.d("BoardViewModel", "📤 요청 JSON 길이: ${requestJson.length}")
                android.util.Log.d("BoardViewModel", "📤 요청 JSON 시작 부분: ${requestJson.take(200)}...")

                // AuthTokenManager 우회하고 직접 HTTP 요청 (토큰 중복 방지)
                android.util.Log.d("BoardViewModel", "🌐 직접 HTTP 요청 시작: ${AuthTokenManager.HTTP_BASE_URL}/board/write")

                val client = OkHttpClient()
                val mediaType = "application/json".toMediaType()
                val body = requestJson.toRequestBody(mediaType)

                val request = Request.Builder()
                    .url("${AuthTokenManager.HTTP_BASE_URL}/board/write")
                    .post(body)
                    .addHeader("Content-Type", "application/json")
                    .build()

                withContext(Dispatchers.IO) {
                    val response = client.newCall(request).execute()

                    response.use {
                        android.util.Log.d("BoardViewModel", "📥 HTTP 응답 수신:")
                        android.util.Log.d("BoardViewModel", "  - 상태 코드: ${response.code}")
                        android.util.Log.d("BoardViewModel", "  - 상태 메시지: ${response.message}")

                        if (response.isSuccessful) {
                            val responseData = response.body?.string() ?: ""
                            android.util.Log.d("BoardViewModel", "✅ 서버 응답 성공: $responseData")

                            // UI 업데이트는 메인 스레드에서
                            withContext(Dispatchers.Main) {
                                loadSummaries()
                                loadMyPosts()
                                clearFiles()
                                onComplete()
                            }
                        } else {
                            val errorBody = response.body?.string() ?: "Unknown error"
                            android.util.Log.e("BoardViewModel", "❌ 서버 오류: ${response.code} - $errorBody")

                            withContext(Dispatchers.Main) {
                                showError("게시물 작성에 실패했습니다: ${response.code}")
                            }
                        }
                    }
                }

            } catch (e: Exception) {
                android.util.Log.e("BoardViewModel", "❌ 게시글 작성 중 예외 발생")
                android.util.Log.e("BoardViewModel", "❌ 예외 메시지: ${e.message}")
                android.util.Log.e("BoardViewModel", "❌ 스택 트레이스: ${e.stackTraceToString()}")
                showError("게시물 작성 중 오류가 발생했습니다: ${e.message}")
            } finally {
                _isPosting.value = false
                android.util.Log.d("BoardViewModel", "🏁 게시글 작성 프로세스 종료")
            }
        }
    }

    /**
     * 기존 게시글 수정
     * 삭제된 파일과 새로 추가된 파일을 모두 처리
     */
    fun editPost(
        postId: Long,
        title: String,
        content: String,
        category: String = "COMMUNITY",
        onComplete: () -> Unit = {}
    ) {
        if (title.isBlank() || content.isBlank()) return

        viewModelScope.launch {
            _isPosting.value = true
            try {
                // 토큰을 직접 가져와서 설정
                val accessToken = authTokenManager.getValidAccessToken()
                android.util.Log.d("BoardViewModel", "🔍 EditPost 토큰 확인: ${accessToken?.take(20)}...")
                if (accessToken == null) {
                    android.util.Log.e("BoardViewModel", "❌ AccessToken이 null!")
                    showError("로그인이 필요합니다")
                    return@launch
                }

                // 파일 크기 검증
                val (isValid, errorMessage) = validateFileSizes()
                if (!isValid) {
                    showError(errorMessage ?: "파일 크기 제한을 초과했습니다")
                    return@launch
                }

                // 현재 유지되는 기존 파일 ID들
                val keptExistingIds: Set<Long> = _attachedFiles.value
                    .filterIsInstance<AttachedFile.ExistingFile>()
                    .map { it.fileInfo.fileId }
                    .toSet()

                // 삭제된 파일 ID들 (원본에 있었지만 현재 없는 것들)
                val removedIds: List<Long> = (originalExistingFileIds - keptExistingIds).toList()

                // 새로 추가된 파일들을 Base64로 변환
                val newFileDtos: List<Base64FileDto> = _attachedFiles.value
                    .filterIsInstance<AttachedFile.NewFile>()
                    .mapNotNull { uriToFileData(it.uri) }

                val request = EditPostRequest(
                    token = accessToken,
                    title = title,
                    content = content,
                    category = category,
                    files = if (newFileDtos.isNotEmpty()) newFileDtos else null,
                    deleteFileIds = if (removedIds.isNotEmpty()) removedIds else null
                )
                android.util.Log.d("BoardViewModel", "Request 객체 내용:")
                android.util.Log.d("BoardViewModel", "- token: '${request.token}'")
                android.util.Log.d("BoardViewModel", "- title: '${request.title}'")
                android.util.Log.d("BoardViewModel", "- content 길이: ${request.content.length}")
                android.util.Log.d("BoardViewModel", "- files 개수: ${request.files?.size ?: 0}")
                android.util.Log.d("BoardViewModel", "- deleteFileIds 개수: ${request.deleteFileIds?.size ?: 0}")

                val requestJson = gson.toJson(request)
                android.util.Log.d("BoardViewModel", "직렬화된 JSON: $requestJson")
                // JSON에 토큰이 제대로 포함되었는지 확인
                val hasToken = requestJson.contains("\"token\":")
                android.util.Log.d("BoardViewModel", "JSON에 토큰 포함 여부: $hasToken")
                android.util.Log.d("BoardViewModel", "최종 JSON: $requestJson")

                if (!hasToken) {
                    android.util.Log.e("BoardViewModel", "경고: JSON에 토큰이 없음!")
                }

                authTokenManager.makeAuthenticatedRequest(
                    "${AuthTokenManager.HTTP_BASE_URL}/board/post/$postId",
                    "PATCH",
                    requestJson

                ).onSuccess {
                    // 성공 시 상태 초기화 및 새로고침
                    clearEditFiles()
                    delay(200) // 서버 반영 대기
                    selectPost(postId) // 상세 정보 다시 로드
                    loadSummaries()
                    loadMyPosts()
                    onComplete()
                }.onFailure { exception ->
                    showError("게시물 수정에 실패했습니다: ${exception.message}")
                }
            } catch (e: Exception) {
                showError("게시물 수정에 실패했습니다: ${e.message}")
            } finally {
                _isPosting.value = false
            }
        }
    }

    /**
     * 게시글 삭제
     */
    fun deletePost(postId: Long) {
        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                showError("로그인이 필요합니다")
                return@launch
            }

            val request = DeletePostRequest(token = accessToken)
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/post/$postId",
                "DELETE",
                requestJson
            ).onSuccess {
                // 성공 시 목록 새로고침
                loadSummaries()
                loadMyPosts()
            }.onFailure { exception ->
                showError("게시글 삭제에 실패했습니다: ${exception.message}")
            }
        }
    }

    // ===== 댓글 관련 기능들 =====

    /**
     * 새 댓글 작성
     */
    fun createComment(postId: Long, content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                showError("로그인이 필요합니다")
                return@launch
            }

            val request = WriteCommentRequest(token = accessToken, content = content)
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/$postId/comments",
                "POST",
                requestJson
            ).onSuccess {
                // 성공 시 댓글 목록 및 게시글 정보 새로고침
                refreshPostAndComments(postId)
            }.onFailure { exception ->
                showError("댓글 작성에 실패했습니다: ${exception.message}")
            }
        }
    }

    /**
     * 댓글 수정
     */
    fun editComment(postId: Long, commentId: Long, content: String) {
        if (content.isBlank()) return

        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                showError("로그인이 필요합니다")
                return@launch
            }

            val request = EditCommentRequest(token = accessToken, content = content)
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/comments/$commentId",
                "PATCH",
                requestJson
            ).onSuccess {
                refreshPostAndComments(postId)
            }.onFailure { exception ->
                showError("댓글 수정에 실패했습니다: ${exception.message}")
            }
        }
    }

    /**
     * 댓글 삭제
     */
    fun deleteComment(postId: Long, commentId: Long) {
        viewModelScope.launch {
            // 토큰을 직접 가져와서 설정
            val accessToken = authTokenManager.getValidAccessToken()
            if (accessToken == null) {
                showError("로그인이 필요합니다")
                return@launch
            }

            val request = DeleteCommentRequest(token = accessToken)
            val requestJson = gson.toJson(request)

            authTokenManager.makeAuthenticatedRequest(
                "${AuthTokenManager.BASE_URL}/board/comments/$commentId",
                "DELETE",
                requestJson
            ).onSuccess {
                refreshPostAndComments(postId)
            }.onFailure { exception ->
                showError("댓글 삭제에 실패했습니다: ${exception.message}")
            }
        }
    }

    /**
     * 댓글 작업 후 게시글과 댓글 정보를 새로고침하는 공통 함수
     * 중복 코드 제거를 위해 분리
     */
    private suspend fun refreshPostAndComments(postId: Long) {
        val commentsResult = authTokenManager.makeAuthenticatedRequest(
            "${AuthTokenManager.BASE_URL}/board/$postId/comments"
        )

        commentsResult.onSuccess { responseJson ->
            try {
                val commentsType = object : TypeToken<List<CommentResponse>>() {}.type
                val comments = gson.fromJson<List<CommentResponse>>(responseJson, commentsType)
                _comments.value = sortCommentsByCreatedAt(comments)
                loadSummaries()
                loadMyPosts()
                selectPost(postId)
            } catch (e: Exception) {
                showError("댓글 정보 업데이트 중 오류가 발생했습니다")
            }
        }.onFailure { exception ->
            showError("댓글 정보를 새로고침할 수 없습니다: ${exception.message}")
        }
    }

    // ===== 파일 처리 유틸리티 함수들 =====

    /**
     * URI를 Base64FileDto로 변환
     * 파일을 읽어서 Base64로 인코딩하고 메타데이터 추출
     */
    private fun uriToFileData(uri: Uri): Base64FileDto? {
        return try {
            val inputStream = context.contentResolver.openInputStream(uri)
            val fileName = getFileName(uri) ?: "temp_file_${System.currentTimeMillis()}"
            val contentType = getMimeType(uri) ?: "application/octet-stream"

            inputStream?.use { input ->
                val bytes = input.readBytes()
                val base64Data = Base64.encodeToString(bytes, Base64.NO_WRAP)
                Base64FileDto(
                    fileName = fileName,
                    base64Data = base64Data,
                    contentType = contentType
                )
            }
        } catch (e: Exception) {
            null
        }
    }

    /**
     * URI에서 MIME 타입 추출
     * content:// 스키마와 file:// 스키마 모두 지원
     */
    private fun getMimeType(uri: Uri): String? {
        return when (uri.scheme) {
            "content" -> context.contentResolver.getType(uri)
            "file" -> {
                val extension = MimeTypeMap.getFileExtensionFromUrl(uri.toString())
                MimeTypeMap.getSingleton().getMimeTypeFromExtension(extension.lowercase())
            }
            else -> null
        }
    }

    /**
     * URI에서 파일명 추출
     * ContentResolver를 통해 실제 파일명을 가져오거나 경로에서 추출
     */
    private fun getFileName(uri: Uri): String? {
        var result: String? = null

        // content:// 스키마인 경우 ContentResolver로 파일명 조회
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            cursor?.use {
                if (it.moveToFirst()) {
                    val index = it.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = it.getString(index)
                    }
                }
            }
        }

        // ContentResolver로 가져오지 못한 경우 경로에서 추출
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/')
            if (cut != -1) {
                result = result?.substring(cut!! + 1)
            }
        }
        return result
    }

    /**
     * URI에서 파일 크기 추출
     * content:// 스키마와 file:// 스키마 모두 지원
     */
    private fun getFileSize(uri: Uri): Long {
        return try {
            when (uri.scheme) {
                "content" -> {
                    val cursor = context.contentResolver.query(uri, null, null, null, null)
                    cursor?.use {
                        if (it.moveToFirst()) {
                            val sizeIndex = it.getColumnIndex(android.provider.OpenableColumns.SIZE)
                            if (sizeIndex >= 0) {
                                return it.getLong(sizeIndex)
                            }
                        }
                    }
                    0L
                }
                "file" -> {
                    val file = java.io.File(uri.path ?: "")
                    if (file.exists()) file.length() else 0L
                }
                else -> 0L
            }
        } catch (e: Exception) {
            0L
        }
    }

    /**
     * 바이트 크기를 MB 단위로 포맷팅
     */
    private fun formatFileSizeToMB(bytes: Long): String {
        val mb = bytes / (1024.0 * 1024.0)
        return String.format("%.1f MB", mb)
    }
}