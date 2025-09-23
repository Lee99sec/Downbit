package com.example.myapplication

import android.app.Application

class MyApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        // 앱이 시작될 때 한 번만 실행
        // 모든 Activity에서 자동으로 사용 가능
        RetrofitClient.init(this)
    }
}