package com.example.myapplication

import retrofit2.Retrofit
import retrofit2.Response
import retrofit2.http.*
import retrofit2.converter.gson.GsonConverterFactory

/**
 * 게시글 목록에서 표시할 요약 정보를 담는 데이터 클래스
 */
data class PostSummaryResponse(
    val authorUsername: String,
    val createdAt: String,
    val postId: Long,
    val title: String,
    val commentCount: Int,
    val isContainingImg: Boolean,
    val nickname: String,
    val viewCount: Int
)

/**
 * 여러 게시글 요약 정보를 담는 컨테이너 클래스
 */
data class PostSummariesResponse(
    val postSummaries: List<PostSummaryResponse>
)

/**
 * 게시글 상세 정보를 담는 데이터 클래스
 */
data class PostDetailResponse(
    val authorUsername: String,
    val content: String,
    val createdAt: String,
    val postId: Long,
    val title: String,
    val commentCount: Int,
    val files: List<FileInfo>? = null,
    val nickname: String,
    val authorProfilePic: String?,
    val viewCount: Int
)

/**
 * 첨부 파일 정보를 담는 데이터 클래스
 */
data class FileInfo(
    val fileId: Long,
    val originalFileName: String,
    val storedFileName: String,
    val fileSize: Long,
    val contentType: String,
    val createdAt: String
)

/**
 * 내 게시글 조회 요청을 위한 데이터 클래스
 */
data class MyPostsRequest(
    val token: String,
    val sort: String? = null
)

/**
 * 댓글 정보를 담는 응답 데이터 클래스
 */
data class CommentResponse(
    val id: Long,
    val postId: Long,
    val userId: Long,
    val username: String,
    val content: String,
    val createdAt: String,
    val nickname: String
)

/**
 * 새 게시글 작성 요청을 위한 데이터 클래스
 */
data class WritePostRequest(
    val token: String,
    val title: String,
    val content: String,
    val category: String,
    val files: List<Base64FileDto>? = null
)

/**
 * Base64로 인코딩된 파일 데이터를 전송하기 위한 DTO
 */
data class Base64FileDto(
    val fileName: String,
    val base64Data: String,
    val contentType: String
)

/**
 * 게시글 수정 요청을 위한 데이터 클래스
 */
data class EditPostRequest(
    val token: String,
    val title: String,
    val content: String,
    val category: String,
    val files: List<Base64FileDto>? = null,
    val deleteFileIds: List<Long>? = null
)

/**
 * 댓글 작성 요청을 위한 데이터 클래스
 */
data class WriteCommentRequest(
    val token: String,
    val content: String
)

/**
 * 댓글 수정 요청을 위한 데이터 클래스
 */
data class EditCommentRequest(
    val token: String,
    val content: String
)

/**
 * 게시글 삭제 요청을 위한 데이터 클래스
 */
data class DeletePostRequest(val token: String)

/**
 * 댓글 삭제 요청을 위한 데이터 클래스
 */
data class DeleteCommentRequest(val token: String)

/**
 * 게시판 관련 API 엔드포인트들을 정의하는 인터페이스
 */
interface BoardApi {
    companion object {
        // 기존 HTTPS용 인스턴스 생성 메소드
        fun create(): BoardApi {
            return Retrofit.Builder()
                .baseUrl(RetrofitClient.BASE_URL) // HTTPS URL
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BoardApi::class.java)
        }

        // HTTP용 인스턴스 생성 메소드 추가
        fun createForWrite(): BoardApi {

            return Retrofit.Builder()
                .baseUrl("http://www.downbit.net") // HTTP URL
                .addConverterFactory(GsonConverterFactory.create())
                .build()
                .create(BoardApi::class.java)
        }
    }

    // 통합된 sort 파라미터 사용
    @GET("/board/list")
    suspend fun getPostSummaries(
        @Query("sort") sort: String? = null
    ): PostSummariesResponse

    @GET("/board/search")
    suspend fun searchPosts(
        @Query("keyword") keyword: String,
        @Query("sort") sort: String? = null
    ): PostSummariesResponse

    @POST("/board/myposts")
    suspend fun getMyPosts(
        @Body request: MyPostsRequest,
    ): PostSummariesResponse

    @GET("/board/post/{postId}")
    suspend fun getPostDetail(@Path("postId") postId: Long): PostDetailResponse

    @GET("/board/{postId}/comments")
    suspend fun getComments(@Path("postId") postId: Long): List<CommentResponse>

    @POST("/board/write")
    suspend fun writePost(@Body request: WritePostRequest): Response<Unit>

    @PATCH("/board/post/{postId}")
    suspend fun editPost(
        @Path("postId") postId: Long,
        @Body request: EditPostRequest
    ): Response<Unit>

    @POST("/board/{postId}/comments")
    suspend fun writeComment(
        @Path("postId") postId: Long,
        @Body request: WriteCommentRequest
    ): Response<Unit>

    @PATCH("/board/comments/{commentId}")
    suspend fun editComment(
        @Path("commentId") commentId: Long,
        @Body request: EditCommentRequest
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "/board/post/{postId}", hasBody = true)
    suspend fun deletePost(
        @Path("postId") postId: Long,
        @Body request: DeletePostRequest
    ): Response<Unit>

    @HTTP(method = "DELETE", path = "/board/comments/{commentId}", hasBody = true)
    suspend fun deleteComment(
        @Path("commentId") commentId: Long,
        @Body request: DeleteCommentRequest
    ): Response<Unit>

    @GET("/files/download/{fileId}")
    suspend fun downloadFile(@Path("fileId") fileId: Long): Response<okhttp3.ResponseBody>
}