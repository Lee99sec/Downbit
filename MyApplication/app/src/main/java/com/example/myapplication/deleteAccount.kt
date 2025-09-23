package com.example.myapplication

import AuthTokenManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.text.style.StyleSpan
import android.util.Log
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/**
 * 회원탈퇴 함수 - 메인 진입점
 */
fun deleteAccount(context: Context) {
    checkAssetsBeforeDelete(context)
}

/**
 * 자산 확인 후 탈퇴 프로세스 진행
 */
private fun checkAssetsBeforeDelete(context: Context) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val authTokenManager = AuthTokenManager(context)

            if (!authTokenManager.isLoggedIn()) {
                Toast.makeText(context, "로그인이 필요합니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            // 자산 확인
            val hasAssets = checkUserAssets(context)

            if (hasAssets) {
                showAssetWarningDialog(context)
            } else {
                showFirstDeleteDialog(context)
            }

        } catch (e: Exception) {
            Toast.makeText(context, "자산 확인 중 오류가 발생했습니다: ${e.message}", Toast.LENGTH_SHORT).show()
            Log.e("DeleteAccount", "자산 확인 실패", e)
        }
    }
}

/**
 * 사용자 자산 확인 - AuthTokenManager 사용
 */
private suspend fun checkUserAssets(context: Context): Boolean {
    return withContext(Dispatchers.IO) {
        try {
            val authTokenManager = AuthTokenManager(context)
            var hasCoinAssets = false
            var hasCashBalance = false

            // 1. 코인 자산 확인
            val walletResult = authTokenManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.BASE_URL}/mywallet",
                method = "GET"
            )

            walletResult.fold(
                onSuccess = { walletData ->
                    try {
                        val walletJson = JSONObject(walletData)
                        val myWalletArray = walletJson.getJSONArray("myWallet")

                        for (i in 0 until myWalletArray.length()) {
                            val assetJson = myWalletArray.getJSONObject(i)
                            val amount = assetJson.getDouble("amount")
                            if (amount > 0.001) {
                                hasCoinAssets = true
                                break
                            }
                        }
                    } catch (e: Exception) {
                        Log.e("DeleteAccount", "지갑 데이터 파싱 오류", e)
                    }
                },
                onFailure = { exception ->
                    Log.e("DeleteAccount", "지갑 확인 실패: ${exception.message}")
                }
            )

            // 2. 현금 잔고 확인
            val userResult = authTokenManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.BASE_URL}/user/info",
                method = "GET"
            )

            userResult.fold(
                onSuccess = { userData ->
                    try {
                        val userJson = JSONObject(userData)
                        val cashAmount = userJson.optDouble("cash", 0.0)

                        if (cashAmount > 1000) {
                            hasCashBalance = true
                        }
                    } catch (e: Exception) {
                        Log.e("DeleteAccount", "사용자 데이터 파싱 오류", e)
                    }
                },
                onFailure = { exception ->
                    Log.e("DeleteAccount", "사용자 정보 확인 실패: ${exception.message}")
                }
            )

            val hasAssets = hasCoinAssets || hasCashBalance
            Log.d("DeleteAccount", "자산 확인 결과 - 코인: $hasCoinAssets, 현금: $hasCashBalance, 총자산: $hasAssets")

            hasAssets

        } catch (e: Exception) {
            Log.e("DeleteAccount", "자산 확인 API 실패", e)
            true // 오류 발생 시 안전하게 자산이 있다고 가정
        }
    }
}

/**
 * 자산 보유 시 경고 다이얼로그
 */
private fun showAssetWarningDialog(context: Context) {
    val dialog = android.app.Dialog(context)

    val mainLayout = LinearLayout(context)
    mainLayout.orientation = LinearLayout.VERTICAL
    mainLayout.setPadding(60, 50, 60, 50)
    mainLayout.setBackgroundColor(Color.WHITE)

    val titleText = TextView(context)
    titleText.text = "탈퇴 불가"
    titleText.textSize = 22f
    titleText.setTypeface(null, Typeface.BOLD)
    titleText.setTextColor(Color.parseColor("#D32F2F"))
    titleText.gravity = Gravity.CENTER
    val titleParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    titleParams.bottomMargin = 55
    titleText.layoutParams = titleParams

    val messageText = TextView(context)
    messageText.text = "\n보유 자산이 있어 탈퇴할 수 없습니다\n자산 정리 후 다시 시도해주세요\n\n 해당 탭을 누르시면 이동합니다"
    messageText.textSize = 15f
    messageText.setTextColor(Color.parseColor("#333333"))
    messageText.setLineSpacing(1.6f * messageText.textSize, 1.0f)
    val messageParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    messageParams.bottomMargin = 20
    messageText.layoutParams = messageParams

    val itemsLayout = LinearLayout(context)
    itemsLayout.orientation = LinearLayout.VERTICAL
    itemsLayout.setPadding(20, 20, 20, 20)
    itemsLayout.setBackgroundColor(Color.parseColor("#F8F9FA"))
    val itemsParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    itemsParams.bottomMargin = 40
    itemsLayout.layoutParams = itemsParams

    // 자산 상태 확인 및 항목 생성
    checkAssetStatusAndCreateItems(context, itemsLayout, dialog)

    val buttonSpacer = android.view.View(context)
    val spacerParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 20)
    buttonSpacer.layoutParams = spacerParams

    val buttonContainer = LinearLayout(context)
    buttonContainer.orientation = LinearLayout.HORIZONTAL
    buttonContainer.gravity = Gravity.CENTER

    val okButton = Button(context)
    okButton.text = "확인"
    okButton.textSize = 16f
    okButton.setTextColor(Color.parseColor("#666666"))
    okButton.setBackgroundColor(Color.parseColor("#F0F0F0"))
    okButton.setTypeface(null, Typeface.BOLD)
    okButton.setPadding(20, 15, 20, 15)
    val buttonParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    okButton.layoutParams = buttonParams

    buttonContainer.addView(okButton)

    mainLayout.addView(titleText)
    mainLayout.addView(messageText)
    mainLayout.addView(itemsLayout)
    mainLayout.addView(buttonSpacer)
    mainLayout.addView(buttonContainer)

    dialog.setContentView(mainLayout)
    dialog.window?.apply {
        setLayout(850, ViewGroup.LayoutParams.WRAP_CONTENT)
        setBackgroundDrawableResource(android.R.drawable.dialog_frame)
    }

    okButton.setOnClickListener { dialog.dismiss() }

    dialog.show()
}

/**
 * 자산 상태 확인 후 항목 생성 - AuthTokenManager 사용
 */
private fun checkAssetStatusAndCreateItems(context: Context, itemsLayout: LinearLayout, dialog: android.app.Dialog) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val authTokenManager = AuthTokenManager(context)

            val (coinCleared, cashCleared) = if (authTokenManager.isLoggedIn()) {
                withContext(Dispatchers.IO) { checkAssetStatus(context) }
            } else {
                Pair(false, false)
            }

            // 코인 매도 항목
            val coinContainer = LinearLayout(context)
            coinContainer.orientation = LinearLayout.VERTICAL
            coinContainer.setPadding(0, 0, 0, 0)
            val coinContainerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            coinContainerParams.bottomMargin = 18
            coinContainer.layoutParams = coinContainerParams

            val coinItem = createAssetActionItem(
                context,
                if (coinCleared) "✓ 모든 코인을 매도해주세요" else "✗ 모든 코인을 매도해주세요",
                "",
                coinCleared
            ) {
                dialog.dismiss()
                navigateToMainTab(context, "거래소")
            }
            coinContainer.addView(coinItem)

            // 현금 출금 항목
            val cashContainer = LinearLayout(context)
            cashContainer.orientation = LinearLayout.VERTICAL
            cashContainer.setPadding(0, 0, 0, 0)
            val cashContainerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            cashContainerParams.bottomMargin = 18
            cashContainer.layoutParams = cashContainerParams

            val cashItem = createAssetActionItem(
                context,
                if (cashCleared) "✓ 현금 잔고를 출금해주세요" else "✗ 현금 잔고를 출금해주세요",
                "",
                cashCleared
            ) {
                dialog.dismiss()
                navigateToMainTab(context, "입출금")
            }
            cashContainer.addView(cashItem)

            // 자산 정리 항목
            val assetContainer = LinearLayout(context)
            assetContainer.orientation = LinearLayout.VERTICAL
            assetContainer.setPadding(0, 0, 0, 0)
            val assetContainerParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            assetContainer.layoutParams = assetContainerParams

            val allCleared = coinCleared && cashCleared
            val assetItem = createAssetActionItem(
                context,
                if (allCleared) "✓ 모든 자산을 정리해주세요" else "✗ 모든 자산을 정리해주세요",
                "",
                allCleared
            ) {
                dialog.dismiss()
                navigateToMainTab(context, "자산현황")
            }
            assetContainer.addView(assetItem)

            itemsLayout.addView(coinContainer)
            itemsLayout.addView(cashContainer)
            itemsLayout.addView(assetContainer)

        } catch (e: Exception) {
            // 오류 시 기본 항목들 표시
            createDefaultAssetItems(context, itemsLayout, dialog)
            Log.e("DeleteAccount", "자산 상태 확인 실패", e)
        }
    }
}

/**
 * 기본 자산 항목 생성 (오류 시)
 */
private fun createDefaultAssetItems(context: Context, itemsLayout: LinearLayout, dialog: android.app.Dialog) {
    val items = listOf(
        Triple("✗ 모든 코인을 매도해주세요", "거래소", false),
        Triple("✗ 현금 잔고를 출금해주세요", "입출금", false),
        Triple("✗ 모든 자산을 정리해주세요", "자산현황", false)
    )

    for ((text, tab, completed) in items) {
        val container = LinearLayout(context)
        container.orientation = LinearLayout.VERTICAL
        val containerParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        )
        containerParams.bottomMargin = 18
        container.layoutParams = containerParams

        val item = createAssetActionItem(context, text, tab, completed) {
            dialog.dismiss()
            navigateToMainTab(context, tab)
        }
        container.addView(item)
        itemsLayout.addView(container)
    }
}

/**
 * 자산 상태 확인 함수 - AuthTokenManager 사용
 */
private suspend fun checkAssetStatus(context: Context): Pair<Boolean, Boolean> {
    return try {
        val authTokenManager = AuthTokenManager(context)
        var hasCoinAssets = false
        var hasCashBalance = false

        // 1. 코인 자산 확인
        val walletResult = authTokenManager.makeAuthenticatedRequest(
            url = "${AuthTokenManager.BASE_URL}/mywallet",
            method = "GET"
        )

        walletResult.fold(
            onSuccess = { walletData ->
                try {
                    val walletJson = JSONObject(walletData)
                    val myWalletArray = walletJson.getJSONArray("myWallet")

                    for (i in 0 until myWalletArray.length()) {
                        val assetJson = myWalletArray.getJSONObject(i)
                        val amount = assetJson.getDouble("amount")
                        if (amount > 0.001) {
                            hasCoinAssets = true
                            break
                        }
                    }
                } catch (e: Exception) {
                    Log.e("DeleteAccount", "지갑 데이터 파싱 오류", e)
                }
            },
            onFailure = {
                Log.e("DeleteAccount", "지갑 확인 실패")
            }
        )

        // 2. 현금 잔고 확인
        val userResult = authTokenManager.makeAuthenticatedRequest(
            url = "${AuthTokenManager.BASE_URL}/user/info",
            method = "GET"
        )

        userResult.fold(
            onSuccess = { userData ->
                try {
                    val userJson = JSONObject(userData)
                    val cashAmount = userJson.optDouble("cash", 0.0)

                    if (cashAmount > 1000) {
                        hasCashBalance = true
                    }
                } catch (e: Exception) {
                    Log.e("DeleteAccount", "사용자 데이터 파싱 오류", e)
                }
            },
            onFailure = {
                Log.e("DeleteAccount", "사용자 정보 확인 실패")
            }
        )

        val coinCleared = !hasCoinAssets
        val cashCleared = !hasCashBalance

        Log.d("DeleteAccount", "자산 확인 결과 - 코인 정리: $coinCleared, 현금 정리: $cashCleared")
        Pair(coinCleared, cashCleared)

    } catch (e: Exception) {
        Log.e("DeleteAccount", "자산 확인 API 실패", e)
        Pair(false, false)
    }
}

/**
 * 자산 액션 항목 생성 함수
 */
private fun createAssetActionItem(
    context: Context,
    itemText: String,
    targetPage: String,
    isCompleted: Boolean,
    onClick: () -> Unit
): LinearLayout {
    val itemLayout = LinearLayout(context)
    itemLayout.orientation = LinearLayout.HORIZONTAL
    itemLayout.gravity = Gravity.CENTER_VERTICAL
    itemLayout.setPadding(16, 16, 16, 16)
    itemLayout.setBackgroundColor(Color.WHITE)
    itemLayout.isClickable = true
    itemLayout.isFocusable = true

    val attrs = intArrayOf(android.R.attr.selectableItemBackground)
    val typedArray = context.obtainStyledAttributes(attrs)
    itemLayout.background = typedArray.getDrawable(0)
    typedArray.recycle()

    val itemParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.MATCH_PARENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    itemLayout.layoutParams = itemParams

    itemLayout.setOnClickListener { onClick() }

    val textView = TextView(context)
    textView.text = itemText
    textView.textSize = 15f
    textView.setTextColor(if (isCompleted) Color.parseColor("#4CAF50") else Color.parseColor("#D32F2F"))
    if (isCompleted) {
        textView.setTypeface(null, Typeface.BOLD)
    }
    val textParams = LinearLayout.LayoutParams(
        0,
        LinearLayout.LayoutParams.WRAP_CONTENT,
        1.0f
    )
    textView.layoutParams = textParams

    val rightSection = LinearLayout(context)
    rightSection.orientation = LinearLayout.HORIZONTAL
    rightSection.gravity = Gravity.CENTER_VERTICAL

    val pageLabel = TextView(context)
    pageLabel.text = targetPage
    pageLabel.textSize = 13f
    pageLabel.setTextColor(if (isCompleted) Color.parseColor("#4CAF50") else Color.parseColor("#D32F2F"))
    pageLabel.setTypeface(null, Typeface.BOLD)
    val labelParams = LinearLayout.LayoutParams(
        LinearLayout.LayoutParams.WRAP_CONTENT,
        LinearLayout.LayoutParams.WRAP_CONTENT
    )
    labelParams.rightMargin = 8
    pageLabel.layoutParams = labelParams

    val arrowIcon = TextView(context)
    arrowIcon.text = "→"
    arrowIcon.textSize = 16f
    arrowIcon.setTextColor(if (isCompleted) Color.parseColor("#4CAF50") else Color.parseColor("#D32F2F"))
    arrowIcon.setTypeface(null, Typeface.BOLD)

    rightSection.addView(pageLabel)
    rightSection.addView(arrowIcon)

    itemLayout.addView(textView)
    itemLayout.addView(rightSection)

    return itemLayout
}

/**
 * MainActivity의 특정 탭으로 이동하는 함수
 */
private fun navigateToMainTab(context: Context, tabName: String) {
    try {
        val mainIntent = Intent(context, MainActivity::class.java)
        mainIntent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        mainIntent.putExtra("navigate_to_tab", tabName)
        context.startActivity(mainIntent)

        val message = when (tabName) {
            "거래소" -> "거래소로 이동합니다."
            "입출금" -> "입출금 페이지로 이동합니다."
            "자산현황" -> "자산현황으로 이동합니다."
            else -> "페이지로 이동합니다."
        }

        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        Log.d("DeleteAccount", "$tabName 페이지로 이동 성공")

    } catch (e: Exception) {
        Log.e("DeleteAccount", "$tabName 페이지 이동 실패: ${e.message}", e)
        Toast.makeText(context, "페이지 이동에 실패했습니다. 메뉴에서 직접 이동해주세요.", Toast.LENGTH_SHORT).show()
    }
}

/**
 * 첫 번째 삭제 확인 다이얼로그
 */
private fun showFirstDeleteDialog(context: Context) {
    val dialog = android.app.Dialog(context)

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(80, 60, 80, 60)
        setBackgroundColor(Color.WHITE)
    }

    val titleView = TextView(context).apply {
        text = "회원탈퇴"
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#D32F2F"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 55 }
    }

    val messageView = TextView(context).apply {
        val fullText = "정말로 탈퇴하시겠습니까?\n탈퇴 시 모든 데이터가\n영구 삭제되며,\n복구할 수 없습니다."
        val spannableString = SpannableString(fullText)

        val deleteStart = fullText.indexOf("영구 삭제되며,")
        if (deleteStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                deleteStart, deleteStart + 7,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                deleteStart, deleteStart + 7,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val recoverStart = fullText.indexOf("복구할 수 없습니다.")
        if (recoverStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                recoverStart, recoverStart + 11,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                recoverStart, recoverStart + 11,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        text = spannableString
        textSize = 16f
        setTextColor(Color.parseColor("#424242"))
        setLineSpacing(1.5f * textSize, 1.0f)
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 }
    }

    val buttonLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    val cancelButton = Button(context).apply {
        text = "취소"
        textSize = 16f
        setTextColor(Color.parseColor("#666666"))
        setBackgroundColor(Color.parseColor("#F0F0F0"))
        setTypeface(null, Typeface.BOLD)
        setPadding(20, 15, 20, 15)
        layoutParams = LinearLayout.LayoutParams(130, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = 60
        }
    }

    val confirmButton = Button(context).apply {
        text = "확인"
        textSize = 18f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#D32F2F"))
        setTypeface(null, Typeface.BOLD)
        setPadding(20, 15, 20, 15)
        layoutParams = LinearLayout.LayoutParams(150, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    buttonLayout.addView(cancelButton)
    buttonLayout.addView(confirmButton)

    layout.addView(titleView)
    layout.addView(messageView)
    layout.addView(buttonLayout)

    dialog.setContentView(layout)
    dialog.window?.apply {
        setLayout(800, ViewGroup.LayoutParams.WRAP_CONTENT)
        setBackgroundDrawableResource(android.R.drawable.dialog_frame)
    }

    cancelButton.setOnClickListener { dialog.dismiss() }
    confirmButton.setOnClickListener {
        dialog.dismiss()
        showSecondDeleteDialog(context)
    }

    dialog.show()
}

/**
 * 두 번째 다이얼로그 - 30일 보관 정책 안내
 */
private fun showSecondDeleteDialog(context: Context) {
    val dialog = android.app.Dialog(context)

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(60, 50, 60, 50)
        setBackgroundColor(Color.WHITE)
    }

    val titleView = TextView(context).apply {
        text = "탈퇴 정책 안내"
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#D32F2F"))
        gravity = Gravity.CENTER
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 55 }
    }

    val spacerView = android.view.View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 10)
    }

    val policySection = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(0, 0, 0, 0)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 }
    }

    val policyTitle = TextView(context).apply {
        text = "데이터 보관 정책"
        textSize = 16f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#333333"))
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 16 }
    }

    val policyContent = TextView(context).apply {
        text = "회원 탈퇴 시, 계정 및 관련 정보는 30일간 보관되며 이 기간 동안 복구가 가능합니다. 보관 기간 경과 후 모든 정보는 영구 삭제됩니다.\n복구 관련 상담은 관리자에게 문의하세요."
        textSize = 13f
        setTextColor(Color.parseColor("#333333"))
        setLineSpacing(1.6f * textSize, 1.0f)
    }

    policySection.addView(policyTitle)
    policySection.addView(policyContent)

    val buttonLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    val backButton = Button(context).apply {
        text = "이전"
        textSize = 16f
        setTextColor(Color.parseColor("#666666"))
        setBackgroundColor(Color.parseColor("#F0F0F0"))
        setTypeface(null, Typeface.BOLD)
        setPadding(20, 15, 20, 15)
        layoutParams = LinearLayout.LayoutParams(130, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = 60
        }
    }

    val continueButton = Button(context).apply {
        text = "동의 후 \n계속"
        textSize = 15f
        setTextColor(Color.WHITE)
        setBackgroundColor(Color.parseColor("#D32F2F"))
        setTypeface(null, Typeface.BOLD)
        setPadding(20, 15, 20, 15)
        layoutParams = LinearLayout.LayoutParams(160, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    buttonLayout.addView(backButton)
    buttonLayout.addView(continueButton)

    layout.addView(titleView)
    layout.addView(spacerView)
    layout.addView(policySection)
    layout.addView(buttonLayout)

    dialog.setContentView(layout)
    dialog.window?.apply {
        setLayout(850, ViewGroup.LayoutParams.WRAP_CONTENT)
        setBackgroundDrawableResource(android.R.drawable.dialog_frame)
    }

    backButton.setOnClickListener {
        dialog.dismiss()
        showFirstDeleteDialog(context)
    }

    continueButton.setOnClickListener {
        dialog.dismiss()
        showThirdDeleteDialog(context)
    }

    dialog.show()
}

/**
 * 세 번째 다이얼로그 - 최종 확인
 */
private fun showThirdDeleteDialog(context: Context) {
    val dialog = android.app.Dialog(context)

    val layout = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(60, 50, 60, 50)
        setBackgroundColor(Color.WHITE)
    }

    val titleView = TextView(context).apply {
        text = "회원탈퇴 재확인\n"
        textSize = 22f
        setTypeface(null, Typeface.BOLD)
        setTextColor(Color.parseColor("#D32F2F"))
        gravity = Gravity.CENTER
    }

    val spacerView = android.view.View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 60)
    }

    val messageView = TextView(context).apply {
        val fullText = "다음 사항을 다시 한번 확인해주세요.\n\n• 모든 개인정보가 완전히 삭제됩니다.\n• 30일 후에는 절대 복구할 수 없습니다.\n• 같은 이메일로 재가입이 불가능합니다.\n\n"
        val spannableString = SpannableString(fullText)

        val deleteStart = fullText.indexOf("완전히 삭제됩니다.")
        if (deleteStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                deleteStart, deleteStart + 9,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                deleteStart, deleteStart + 9,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val undoStart = fullText.indexOf("30일 후에는 절대 복구할 수 없습니다.")
        if (undoStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                undoStart, undoStart + 22,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                undoStart, undoStart + 22,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val rejoinStart = fullText.indexOf("재가입이 불가능합니다.")
        if (rejoinStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                rejoinStart, rejoinStart + 11,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                rejoinStart, rejoinStart + 11,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        text = spannableString
        textSize = 15f
        setTextColor(Color.parseColor("#333333"))
        setLineSpacing(1.6f * textSize, 1.0f)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 40 }
    }

    val checkboxCard = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setBackgroundColor(Color.parseColor("#F8F9FA"))
        setPadding(20, 20, 20, 20)
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { bottomMargin = 30 }
    }

    val checkbox = CheckBox(context).apply {
        layoutParams = LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { rightMargin = 16 }
    }

    val checkboxText = TextView(context).apply {
        val fullText = "위 내용과 30일 보관 정책을 모두 확인했으며,\n회원탈퇴에 동의합니다."
        val spannableString = SpannableString(fullText)

        val policyStart = fullText.indexOf("30일 보관 정책을 모두 확인했으며,")
        if (policyStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                policyStart, policyStart + 19,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                policyStart, policyStart + 19,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        val agreeStart = fullText.indexOf("회원탈퇴에 동의합니다.")
        if (agreeStart != -1) {
            spannableString.setSpan(
                ForegroundColorSpan(Color.parseColor("#D32F2F")),
                agreeStart, agreeStart + 11,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
            spannableString.setSpan(
                StyleSpan(Typeface.BOLD),
                agreeStart, agreeStart + 11,
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        text = spannableString
        textSize = 13f
        setTextColor(Color.parseColor("#333333"))
        setTypeface(null, Typeface.BOLD)
        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1.0f)
    }

    checkboxCard.addView(checkbox)
    checkboxCard.addView(checkboxText)

    val buttonSpacerView = android.view.View(context).apply {
        layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 40)
    }

    val buttonLayout = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL
        gravity = Gravity.CENTER
    }

    val backButton = Button(context).apply {
        text = "이전"
        textSize = 16f
        setTextColor(Color.parseColor("#666666"))
        setBackgroundColor(Color.parseColor("#F0F0F0"))
        setTypeface(null, Typeface.BOLD)
        setPadding(20, 15, 20, 15)
        layoutParams = LinearLayout.LayoutParams(130, LinearLayout.LayoutParams.WRAP_CONTENT).apply {
            rightMargin = 60
        }
    }

    val deleteButton = Button(context).apply {
        text = "탈퇴"
        textSize = 16f
        setTextColor(Color.parseColor("#AAAAAA"))
        setBackgroundColor(Color.parseColor("#E8E8E8"))
        setTypeface(null, Typeface.BOLD)
        setPadding(20, 15, 20, 15)
        isEnabled = false
        layoutParams = LinearLayout.LayoutParams(160, LinearLayout.LayoutParams.WRAP_CONTENT)
    }

    checkbox.setOnCheckedChangeListener { _, isChecked ->
        if (isChecked) {
            deleteButton.apply {
                isEnabled = true
                setBackgroundColor(Color.parseColor("#D32F2F"))
                setTextColor(Color.WHITE)
            }
        } else {
            deleteButton.apply {
                isEnabled = false
                setBackgroundColor(Color.parseColor("#E8E8E8"))
                setTextColor(Color.parseColor("#AAAAAA"))
            }
        }
    }

    buttonLayout.addView(backButton)
    buttonLayout.addView(deleteButton)

    layout.addView(titleView)
    layout.addView(spacerView)
    layout.addView(messageView)
    layout.addView(checkboxCard)
    layout.addView(buttonSpacerView)
    layout.addView(buttonLayout)

    dialog.setContentView(layout)
    dialog.window?.setLayout(850, ViewGroup.LayoutParams.WRAP_CONTENT)

    backButton.setOnClickListener {
        dialog.dismiss()
        showSecondDeleteDialog(context)
    }

    deleteButton.setOnClickListener {
        if (checkbox.isChecked) {
            dialog.dismiss()
            performDeleteAccount(context)
        }
    }

    dialog.show()
}

/**
 * 실제 회원탈퇴 처리 - AuthTokenManager 사용
 */
private fun performDeleteAccount(context: Context) {
    CoroutineScope(Dispatchers.Main).launch {
        try {
            val authTokenManager = AuthTokenManager(context)

            if (!authTokenManager.isLoggedIn()) {
                Toast.makeText(context, "로그인이 필요합니다. 다시 로그인해주세요.", Toast.LENGTH_SHORT).show()
                return@launch
            }

            Log.d("DeleteAccount", "회원탈퇴 요청 시작")

            // AuthTokenManager를 사용하여 DELETE 요청 (토큰은 자동으로 추가됨)
            val result = authTokenManager.makeAuthenticatedRequest(
                url = "${AuthTokenManager.BASE_URL}/auth/delete",
                method = "DELETE",
                requestBody = "{}" // 빈 JSON 객체 (토큰은 AuthTokenManager가 추가함)
            )

            result.fold(
                onSuccess = { responseData ->
                    Log.d("DeleteAccount", "회원탈퇴 성공: $responseData")

                    // 로그아웃 처리 (토큰 삭제)
                    authTokenManager.logout()

                    // 추가 SharedPreferences 정리
                    val sharedPref = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
                    sharedPref.edit().clear().apply()

                    // 로그인 페이지로 이동
                    val intent = Intent(context, LoginPage::class.java)
                    intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                    context.startActivity(intent)

                    Toast.makeText(context, "회원탈퇴가 완료되었습니다.", Toast.LENGTH_SHORT).show()
                },
                onFailure = { exception ->
                    Log.e("DeleteAccount", "회원탈퇴 실패: ${exception.message}")

                    val errorMessage = when {
                        exception.message?.contains("401") == true ||
                                exception.message?.contains("세션") == true -> {
                            // 세션 만료 시 처리
                            authTokenManager.logout()
                            val intent = Intent(context, LoginPage::class.java)
                            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                            context.startActivity(intent)
                            "인증이 만료되었습니다. 다시 로그인해주세요."
                        }
                        exception.message?.contains("404") == true -> {
                            "사용자 정보를 찾을 수 없습니다."
                        }
                        exception.message?.contains("네트워크") == true -> {
                            "네트워크 연결을 확인해주세요."
                        }
                        else -> {
                            "회원탈퇴 중 오류가 발생했습니다."
                        }
                    }

                    Toast.makeText(context, errorMessage, Toast.LENGTH_SHORT).show()
                }
            )
        } catch (e: Exception) {
            Log.e("DeleteAccount", "예상치 못한 오류 발생", e)
            Toast.makeText(context, "회원탈퇴 중 오류가 발생했습니다.", Toast.LENGTH_SHORT).show()
        }
    }
}