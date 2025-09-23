// E2EEncryptionUtils.kt (범용 기본 함수만 제공)
package com.example.myapplication.security

import android.util.Base64
import org.json.JSONObject
import javax.crypto.Cipher
import javax.crypto.spec.SecretKeySpec

/**
 * E2E 암호화 유틸리티 클래스 (AES-256-ECB, 기본 함수만 제공)
 */
object E2EEncryptionUtils {

    // Base64로 인코딩된 AES-256 키 (32바이트 = 256비트)
    private const val BASE64_SECRET_KEY = "2sJ6wg48eSafT26AmC9uR/Be+KF/8BzrLbmu2aoqCyg="

    // Base64 키를 바이트 배열로 변환
    private val SECRET_KEY_BYTES = Base64.decode(BASE64_SECRET_KEY, Base64.NO_WRAP)

    /**
     * Map을 JSON으로 변환 후 AES-256-ECB로 암호화
     * @param data 암호화할 데이터 (Key-Value 형태)
     * @return 암호화된 Base64 문자열
     */
    fun encryptData(data: Map<String, Any>): String {
        try {
            // 1. Map을 JSON 문자열로 변환
            val jsonObject = JSONObject()
            for ((key, value) in data) {
                jsonObject.put(key, value)
            }
            val jsonString = jsonObject.toString()

            // 2. AES-256-ECB 암호화
            return encryptString(jsonString)
        } catch (e: Exception) {
            throw Exception("데이터 암호화 실패: ${e.message}", e)
        }
    }

    /**
     * 가변 인자로 편리하게 사용할 수 있는 암호화 함수
     * @param pairs 키-값 쌍들 (Pair<String, Any> 형태)
     * @return 암호화된 Base64 문자열
     */
    fun encryptData(vararg pairs: Pair<String, Any>): String {
        return encryptData(mapOf(*pairs))
    }

    /**
     * 문자열을 AES-256-ECB로 암호화
     */
    private fun encryptString(plainText: String): String {
        try {
            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(SECRET_KEY_BYTES, "AES")

            cipher.init(Cipher.ENCRYPT_MODE, keySpec)
            val encrypted = cipher.doFinal(plainText.toByteArray())

            return Base64.encodeToString(encrypted, Base64.NO_WRAP)
        } catch (e: Exception) {
            throw Exception("문자열 암호화 실패: ${e.message}", e)
        }
    }

    /**
     * AES-256-ECB로 암호화된 문자열을 복호화
     */
    fun decryptString(encryptedText: String): String {
        try {
            val encrypted = Base64.decode(encryptedText, Base64.NO_WRAP)

            val cipher = Cipher.getInstance("AES/ECB/PKCS5Padding")
            val keySpec = SecretKeySpec(SECRET_KEY_BYTES, "AES")

            cipher.init(Cipher.DECRYPT_MODE, keySpec)
            val decrypted = cipher.doFinal(encrypted)

            return String(decrypted)
        } catch (e: Exception) {
            throw Exception("문자열 복호화 실패: ${e.message}", e)
        }
    }

    /**
     * 복호화된 JSON을 Map으로 파싱
     */
    fun decryptToMap(encryptedText: String): Map<String, Any> {
        try {
            val jsonString = decryptString(encryptedText)
            val jsonObject = JSONObject(jsonString)
            val map = mutableMapOf<String, Any>()

            val keys = jsonObject.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                map[key] = jsonObject.get(key)
            }

            return map
        } catch (e: Exception) {
            throw Exception("복호화 및 파싱 실패: ${e.message}", e)
        }
    }

    /**
     * 키 유효성 검증
     */
    fun isKeyValid(): Boolean {
        return try {
            SECRET_KEY_BYTES.size == 32
        } catch (e: Exception) {
            false
        }
    }

    /**
     * 테스트용 암호화/복호화 검증
     */
    fun testEncryption(): Boolean {
        return try {
            val testData = "test message"
            val encrypted = encryptString(testData)
            val decrypted = decryptString(encrypted)
            testData == decrypted
        } catch (e: Exception) {
            false
        }
    }
}