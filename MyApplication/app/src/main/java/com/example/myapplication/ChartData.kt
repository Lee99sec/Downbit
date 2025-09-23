// ChartData.kt - 차트 데이터 관련 함수들
package com.example.myapplication.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

// 캔들 데이터 클래스
data class CandleData(
    val market: String,
    val candleDateTimeUtc: String,
    val candleDateTimeKst: String,
    val openingPrice: Double,
    val highPrice: Double,
    val lowPrice: Double,
    val tradePrice: Double, // 종가
    val timestamp: Long,
    val candleAccTradePrice: Double,
    val candleAccTradeVolume: Double,
    val unit: Int
)

// 차트 타입 열거형
enum class ChartType(val displayName: String, val unit: String) {
    MINUTE_1("1분", "1"),
    MINUTE_3("3분", "3"),
    MINUTE_5("5분", "5"),
    MINUTE_15("15분", "15"),
    MINUTE_30("30분", "30"),
    MINUTE_60("60분", "60"),
    DAY("일", "day"),
    WEEK("주", "week"),
    MONTH("월", "month")
}

// 기본 데이터 클래스들
data class PricePoint(val time: String, val price: Float)

data class TradingInfo(
    val volume24h: String = "0", // 24시간 거래량
    val volumeAmount24h: String = "0", // 24시간 거래액
    val high52w: String = "0", // 52주 최고가
    val low52w: String = "0", // 52주 최저가
    val previousClose: String = "0", // 전일 종가
    val dayHigh: String = "0", // 당일 고가
    val dayLow: String = "0" // 당일 저가
)

// 분 캔들 데이터 가져오기
suspend fun fetchMinuteCandleData(
    symbol: String,
    unit: Int = 1,
    count: Int = 100
): List<CandleData> {
    return try {
        val market = "KRW-$symbol"
        val url = "https://api.bithumb.com/v1/candles/minutes/$unit?market=$market&count=$count"

        val response = withContext(Dispatchers.IO) {
            URL(url).readText()
        }

        val jsonArray = JSONArray(response)
        val candleList = mutableListOf<CandleData>()

        for (i in 0 until jsonArray.length()) {
            val candleJson = jsonArray.getJSONObject(i)
            val candle = CandleData(
                market = candleJson.getString("market"),
                candleDateTimeUtc = candleJson.getString("candle_date_time_utc"),
                candleDateTimeKst = candleJson.getString("candle_date_time_kst"),
                openingPrice = candleJson.getDouble("opening_price"),
                highPrice = candleJson.getDouble("high_price"),
                lowPrice = candleJson.getDouble("low_price"),
                tradePrice = candleJson.getDouble("trade_price"),
                timestamp = candleJson.getLong("timestamp"),
                candleAccTradePrice = candleJson.getDouble("candle_acc_trade_price"),
                candleAccTradeVolume = candleJson.getDouble("candle_acc_trade_volume"),
                unit = unit
            )
            candleList.add(candle)
        }

        // 시간순으로 정렬 (오래된 것부터)
        candleList.sortedBy { it.timestamp }
    } catch (e: Exception) {
        emptyList()
    }
}

// 일 캔들 데이터 가져오기
suspend fun fetchDayCandleData(
    symbol: String,
    count: Int = 100
): List<CandleData> {
    return try {
        val market = "KRW-$symbol"
        val url = "https://api.bithumb.com/v1/candles/days?market=$market&count=$count"

        val response = withContext(Dispatchers.IO) {
            URL(url).readText()
        }

        val jsonArray = JSONArray(response)
        val candleList = mutableListOf<CandleData>()

        for (i in 0 until jsonArray.length()) {
            val candleJson = jsonArray.getJSONObject(i)
            val candle = CandleData(
                market = candleJson.getString("market"),
                candleDateTimeUtc = candleJson.getString("candle_date_time_utc"),
                candleDateTimeKst = candleJson.getString("candle_date_time_kst"),
                openingPrice = candleJson.getDouble("opening_price"),
                highPrice = candleJson.getDouble("high_price"),
                lowPrice = candleJson.getDouble("low_price"),
                tradePrice = candleJson.getDouble("trade_price"),
                timestamp = candleJson.getLong("timestamp"),
                candleAccTradePrice = candleJson.getDouble("candle_acc_trade_price"),
                candleAccTradeVolume = candleJson.getDouble("candle_acc_trade_volume"),
                unit = 1440 // 일 단위는 1440분
            )
            candleList.add(candle)
        }

        candleList.sortedBy { it.timestamp }
    } catch (e: Exception) {
        emptyList()
    }
}

// 주 캔들 데이터 가져오기
suspend fun fetchWeekCandleData(
    symbol: String,
    count: Int = 100
): List<CandleData> {
    return try {
        val market = "KRW-$symbol"
        val url = "https://api.bithumb.com/v1/candles/weeks?market=$market&count=$count"

        val response = withContext(Dispatchers.IO) {
            URL(url).readText()
        }

        val jsonArray = JSONArray(response)
        val candleList = mutableListOf<CandleData>()

        for (i in 0 until jsonArray.length()) {
            val candleJson = jsonArray.getJSONObject(i)
            val candle = CandleData(
                market = candleJson.getString("market"),
                candleDateTimeUtc = candleJson.getString("candle_date_time_utc"),
                candleDateTimeKst = candleJson.getString("candle_date_time_kst"),
                openingPrice = candleJson.getDouble("opening_price"),
                highPrice = candleJson.getDouble("high_price"),
                lowPrice = candleJson.getDouble("low_price"),
                tradePrice = candleJson.getDouble("trade_price"),
                timestamp = candleJson.getLong("timestamp"),
                candleAccTradePrice = candleJson.getDouble("candle_acc_trade_price"),
                candleAccTradeVolume = candleJson.getDouble("candle_acc_trade_volume"),
                unit = 10080 // 주 단위는 10080분
            )
            candleList.add(candle)
        }

        candleList.sortedBy { it.timestamp }
    } catch (e: Exception) {
        emptyList()
    }
}

// 월 캔들 데이터 가져오기
suspend fun fetchMonthCandleData(
    symbol: String,
    count: Int = 100
): List<CandleData> {
    return try {
        val market = "KRW-$symbol"
        val url = "https://api.bithumb.com/v1/candles/months?market=$market&count=$count"

        val response = withContext(Dispatchers.IO) {
            URL(url).readText()
        }

        val jsonArray = JSONArray(response)
        val candleList = mutableListOf<CandleData>()

        for (i in 0 until jsonArray.length()) {
            val candleJson = jsonArray.getJSONObject(i)
            val candle = CandleData(
                market = candleJson.getString("market"),
                candleDateTimeUtc = candleJson.getString("candle_date_time_utc"),
                candleDateTimeKst = candleJson.getString("candle_date_time_kst"),
                openingPrice = candleJson.getDouble("opening_price"),
                highPrice = candleJson.getDouble("high_price"),
                lowPrice = candleJson.getDouble("low_price"),
                tradePrice = candleJson.getDouble("trade_price"),
                timestamp = candleJson.getLong("timestamp"),
                candleAccTradePrice = candleJson.getDouble("candle_acc_trade_price"),
                candleAccTradeVolume = candleJson.getDouble("candle_acc_trade_volume"),
                unit = 43200 // 월 단위는 대략 43200분
            )
            candleList.add(candle)
        }

        candleList.sortedBy { it.timestamp }
    } catch (e: Exception) {
        emptyList()
    }
}

// 차트 타입에 따라 데이터 가져오기
suspend fun fetchCandleData(
    symbol: String,
    chartType: ChartType,
    count: Int = 100
): List<CandleData> {
    return when (chartType) {
        ChartType.MINUTE_1 -> fetchMinuteCandleData(symbol, 1, count)
        ChartType.MINUTE_3 -> fetchMinuteCandleData(symbol, 3, count)
        ChartType.MINUTE_5 -> fetchMinuteCandleData(symbol, 5, count)
        ChartType.MINUTE_15 -> fetchMinuteCandleData(symbol, 15, count)
        ChartType.MINUTE_30 -> fetchMinuteCandleData(symbol, 30, count)
        ChartType.MINUTE_60 -> fetchMinuteCandleData(symbol, 60, count)
        ChartType.DAY -> fetchDayCandleData(symbol, count)
        ChartType.WEEK -> fetchWeekCandleData(symbol, count)
        ChartType.MONTH -> fetchMonthCandleData(symbol, count)
    }
}

// 시간 포맷팅 함수
fun formatCandleTime(timestamp: Long, chartType: ChartType): String {
    val date = Date(timestamp)
    val sdf = when (chartType) {
        ChartType.MINUTE_1, ChartType.MINUTE_3, ChartType.MINUTE_5 ->
            SimpleDateFormat("HH:mm", Locale.KOREA)
        ChartType.MINUTE_15, ChartType.MINUTE_30, ChartType.MINUTE_60 ->
            SimpleDateFormat("MM/dd HH:mm", Locale.KOREA)
        ChartType.DAY -> SimpleDateFormat("MM/dd", Locale.KOREA)
        ChartType.WEEK -> SimpleDateFormat("MM/dd", Locale.KOREA)
        ChartType.MONTH -> SimpleDateFormat("yyyy/MM", Locale.KOREA)
    }
    return sdf.format(date)
}

// 간단한 현재가 조회 함수
suspend fun fetchCurrentPrice(symbol: String): String {
    return try {
        val tickerResponse = withContext(Dispatchers.IO) {
            URL("https://api.bithumb.com/public/ticker/${symbol}_KRW").readText()
        }
        val tickerJson = JSONObject(tickerResponse)
        val tickerData = tickerJson.getJSONObject("data")
        tickerData.getString("closing_price")
    } catch (e: Exception) {
        "0"
    }
}

// 기본적인 거래 정보 조회 함수 (간소화)
suspend fun fetchBasicTradingInfo(symbol: String): TradingInfo {
    return try {
        val tickerResponse = withContext(Dispatchers.IO) {
            URL("https://api.bithumb.com/public/ticker/${symbol}_KRW").readText()
        }
        val tickerJson = JSONObject(tickerResponse)
        val tickerData = tickerJson.getJSONObject("data")

        TradingInfo(
            volume24h = tickerData.optString("units_traded_24H", "0"),
            volumeAmount24h = tickerData.optString("acc_trade_value_24H", "0"),
            dayHigh = tickerData.optString("max_price", "0"),
            dayLow = tickerData.optString("min_price", "0"),
            previousClose = tickerData.optString("prev_closing_price", "0")
        )
    } catch (e: Exception) {
        TradingInfo()
    }
}

// 간단한 포맷팅 함수들
fun formatVolume(volume: String): String {
    return try {
        val number = volume.toDouble()
        when {
            number >= 1_000_000 -> String.format("%.2fM", number / 1_000_000)
            number >= 1_000 -> String.format("%.2fK", number / 1_000)
            else -> String.format("%.2f", number)
        }
    } catch (e: Exception) {
        volume
    }
}

fun formatPrice(price: String): String {
    return try {
        val number = price.toDouble()
        String.format("%,.0f", number)
    } catch (e: Exception) {
        price
    }
}

fun formatPrice(price: Double): String {
    return String.format("%,.0f", price)
}