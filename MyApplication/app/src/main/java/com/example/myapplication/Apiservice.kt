package com.example.myapplication
//import com.example.myapplication.screen.BuyOrderRequest
import com.example.myapplication.screen.BuyOrderResponse
import com.example.myapplication.screen.OrderInfoResponse
//import com.example.myapplication.screen.SellOrderRequest
import com.example.myapplication.screen.SellOrderResponse
import com.example.myapplication.screen.TradeLogResponse
import com.example.myapplication.screen.EncryptedBuyOrderRequest
import com.example.myapplication.screen.EncryptedSellOrderRequest
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.GET
import retrofit2.http.HTTP
import retrofit2.http.Headers
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import retrofit2.http.Query

interface ApiService {
    @POST("/auth/login") //로그인 엔드포인트
    suspend fun login(@Body encryptedRequest: Map<String, String>): Response<LoginResponse>

    @POST("/auth/auto-login")
    suspend fun autoLogin(@Body refreshRequest: RefreshRequest): Response<LoginResponse>

    @POST("/auth/register")
    suspend fun register(@Body encryptedRequest: Map<String, String>): Response<Unit>

    @POST("/user/account") // 계좌 연결
    suspend fun linkAccount(@Body request: LinkAccountRequest): Response<Void>

    // 지갑 생성 API 추가
    @POST("/create/wallet")
    suspend fun createWallet(@Query("token") token: String): Response<CreateWalletResponse>

    // 기존 지갑 주소 조회 API 추가
    @GET("/address")
    suspend fun getWalletAddress(@Query("token") token: String): Response<CreateWalletResponse>

    // 암호화된 입출금 요청 API - 토큰 자동 추가 제외
    @Headers("No-Auto-Token: true")
    @POST("/deposit") // 암호화된 입금 요청
    suspend fun depositEncrypted(@Body request: EncryptedDepositRequest): Response<Void>

    @Headers("No-Auto-Token: true")
    @POST("/withdraw") // 암호화된 출금 요청
    suspend fun withdrawEncrypted(@Body request: EncryptedWithdrawRequest): Response<Void>

    @GET("user/info") // cash 보유액
    suspend fun getUserInfo(@Query("token") token: String): Response<UserInfoResponse>

    @GET("/mypage/info")
    suspend fun getMyPageInfo(@Query("token") token: String): Response<MyPageInfoResponse>

    @GET("/user/account")
    suspend fun getAccountInfo(@Query("token") token: String): Response<AccountResponse>

    @GET("/user/info")
    suspend fun getMoreUserInfo(@Query("token") token: String): Response<MoreUserInfoResponse>

    // QnA 작성 API 추가 (HTTP 통신)
    @POST("http://www.downbit.net/qna/write")
    suspend fun submitQna(@Body request: QnaRequest): Response<Unit>

    @PATCH("http://www.downbit.net/mypage/edit")
    suspend fun updateProfileHttp(@Body request: MyPageUpdateRequest): Response<Unit>

    @GET("/order/info/{symbol}")
    suspend fun getOrderInfo(
        @Path("symbol") symbol: String,
        @Query("token") token: String
    ): Response<OrderInfoResponse>

    @GET("/tradelog/{symbol}")
    suspend fun getTradeLog(
        @Path("symbol") symbol: String,
        @Query("token") token: String
    ): Response<TradeLogResponse>

    @GET("/deposit-withdraw") // 입출금 내역 가져오기
    suspend fun getDepositWithdrawHistory(@Query("token") token: String): Response<DepositWithdrawHistoryResponse>

    @POST("/auth/pin") //PIN인증
    suspend fun verifyPin(@Body request: PinVerifyRequest): Response<Unit>

    @HTTP(method = "DELETE", path = "user/account", hasBody = true)
    suspend fun deleteAccount(@Body request: DeleteAccountRequest): Response<DeleteAccountResponse>

    //회원가입용 인증
    @POST("/auth/sms")
    suspend fun sendSmsVerification(@Body request: SmsRequest): Response<Unit>

    @POST("/auth/sms/verify")
    suspend fun verifySmsCode(@Body request: SmsVerifyRequest): Response<Unit>

    //찾기용 인증
    @POST("/auth/sms/find")
    suspend fun sendSmsVerificationFind(@Body request: SmsRequestFind): Response<Unit>

    @POST("/auth/sms/verify/find")
    suspend fun verifySmsCodeFind(@Body request: SmsVerifyRequestFind): Response<Unit>

    // ID 찾기 - E2E 암호화 적용
    @POST("/auth/find-id")
    suspend fun findId(@Body encryptedRequest: Map<String, String>): Response<FindUsernameResponse>

    // 비밀번호 찾기 - 본인 확인 - E2E 암호화 적용
    @POST("/auth/find-password")
    suspend fun findPassword(@Body encryptedRequest: Map<String, String>): Response<Unit>

    // 비밀번호 재설정 - E2E 암호화 적용
    @POST("/auth/reset-password")
    suspend fun resetPassword(@Body encryptedRequest: Map<String, String>): Response<Unit>

    // QnA 목록 조회 API 추가
    @GET("qna/list")
    suspend fun getQnaList(@Query("token") token: String): Response<QnaListResponse>

    // QnA 상세 조회 API 추가
    @GET("qna/detail/{qnaId}")
    suspend fun getQnaDetail(
        @Path("qnaId") qnaId: Long,
        @Query("token") token: String
    ): Response<QnaDetailInfo>

    // 새로 추가: 암호화된 매수 주문
    @POST("/order/buy")
    suspend fun buyOrderEncrypted(@Body request: EncryptedBuyOrderRequest): Response<BuyOrderResponse>

    // 새로 추가: 암호화된 매도 주문 (E2E 보안)
    @POST("/order/sell")
    suspend fun sellOrderEncrypted(@Body request: EncryptedSellOrderRequest): Response<SellOrderResponse>

    @POST("/remittance")
    suspend fun sendRemittance(@Body request: RemittanceRequest): Response<RemittanceResponse>

    @GET("/remittance/log")
    suspend fun getRemittanceHistory(@Query("token") token: String): Response<RemittanceHistoryResponse>
}