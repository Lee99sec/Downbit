package com.example.myapplication

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

// 읽은 공지사항을 관리하는 오브젝트
object NoticeReadManager {
    private const val PREF_NAME = "notice_read_prefs"
    private var sharedPreferences: android.content.SharedPreferences? = null

    fun init(context: Context) {
        if (sharedPreferences == null) {
            sharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        }
    }

    fun markAsRead(noticeId: Int) {
        sharedPreferences?.edit()?.putBoolean("notice_$noticeId", true)?.apply()
    }

    fun isRead(noticeId: Int): Boolean {
        return sharedPreferences?.getBoolean("notice_$noticeId", false) ?: false
    }

    // 3일 지난 공지사항의 읽음 상태를 정리 (선택사항)
    fun cleanupOldNotices(oldNoticeIds: List<Int>) {
        val editor = sharedPreferences?.edit()
        oldNoticeIds.forEach { noticeId ->
            editor?.remove("notice_$noticeId")
        }
        editor?.apply()
    }
}

// 3일 이내인지 확인하는 함수
object NoticeUtils {
    fun isWithin3Days(createdAt: String): Boolean {
        return try {
            val dateTimeFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.getDefault())
            val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_YEAR, -3)
            val threeDaysAgo = calendar.time

            val noticeDate = if (createdAt.contains("T")) {
                dateTimeFormat.parse(createdAt.substring(0, 19))
            } else {
                dateFormat.parse(createdAt.substring(0, 10))
            }

            noticeDate != null && noticeDate.after(threeDaysAgo)
        } catch (e: Exception) {
            false
        }
    }
}