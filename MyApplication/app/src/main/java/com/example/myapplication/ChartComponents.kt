// ChartComponents.kt - 차트 관련 컴포넌트들
package com.example.myapplication.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.Card
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.DropdownMenu
import androidx.compose.material.DropdownMenuItem
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.myapplication.data.CandleData
import com.example.myapplication.data.ChartType
import com.example.myapplication.data.fetchCandleData
import com.example.myapplication.data.formatCandleTime

// 색상 정의
val redMain = Color(0xFFE53E3E)

// 가격 포맷팅 함수 - 소수점까지 자세하게 표시
private fun formatPriceDetailed(price: Double): String {
    return when {
        price >= 1000000 -> String.format("%.0f", price) // 100만원 이상: 정수로
        price >= 10000 -> String.format("%.0f", price)   // 1만원 이상: 정수로
        price >= 1000 -> String.format("%.1f", price)    // 1천원 이상: 소수점 1자리
        price >= 100 -> String.format("%.2f", price)     // 100원 이상: 소수점 2자리
        price >= 10 -> String.format("%.3f", price)      // 10원 이상: 소수점 3자리
        price >= 1 -> String.format("%.4f", price)       // 1원 이상: 소수점 4자리
        price >= 0.1 -> String.format("%.5f", price)     // 0.1원 이상: 소수점 5자리
        price >= 0.01 -> String.format("%.6f", price)    // 0.01원 이상: 소수점 6자리
        price >= 0.001 -> String.format("%.7f", price)   // 0.001원 이상: 소수점 7자리
        else -> String.format("%.8f", price)             // 그 외: 소수점 8자리
    }
}

@Composable
fun ChartTabContent(
    symbol: String,
    currentPrice: String = "0"
) {
    var selectedChartType by remember { mutableStateOf(ChartType.MONTH) } // 기본값을 월봉으로 변경
    var candleData by remember { mutableStateOf<List<CandleData>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // 차트 데이터 로딩
    LaunchedEffect(symbol, selectedChartType) {
        isLoading = true
        errorMessage = null
        try {
            candleData = fetchCandleData(symbol, selectedChartType, 100)
            if (candleData.isEmpty()) {
                errorMessage = "데이터를 불러올 수 없습니다"
            }
        } catch (e: Exception) {
            errorMessage = "네트워크 오류: ${e.message}"
        } finally {
            isLoading = false
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        // 차트 타입 선택 버튼들
        ChartTypeSelector(
            selectedType = selectedChartType,
            onTypeSelected = { selectedChartType = it }
        )

        when {
            isLoading -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        CircularProgressIndicator(color = redMain)
                        Spacer(modifier = Modifier.height(8.dp))
                        Text("차트 로딩중...", color = Color.Gray)
                    }
                }
            }
            errorMessage != null -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = errorMessage ?: "오류가 발생했습니다",
                            color = Color.Red,
                            fontSize = 16.sp
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "현재가: $currentPrice KRW",
                            fontSize = 14.sp,
                            color = Color.Gray
                        )
                    }
                }
            }
            candleData.isNotEmpty() -> {
                // 캔들차트 표시
                CandleChart(
                    candleData = candleData,
                    chartType = selectedChartType,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
fun ChartTypeSelector(
    selectedType: ChartType,
    onTypeSelected: (ChartType) -> Unit
) {
    var showMinuteDropdown by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 분 캔들 드롭다운
            Box(modifier = Modifier.weight(1f)) {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showMinuteDropdown = !showMinuteDropdown },
                    backgroundColor = if (selectedType in listOf(
                            ChartType.MINUTE_1, ChartType.MINUTE_3, ChartType.MINUTE_5,
                            ChartType.MINUTE_15, ChartType.MINUTE_30, ChartType.MINUTE_60
                        )) redMain else Color(0xFFF5F5F5),
                    elevation = 2.dp,
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = if (selectedType in listOf(
                                    ChartType.MINUTE_1, ChartType.MINUTE_3, ChartType.MINUTE_5,
                                    ChartType.MINUTE_15, ChartType.MINUTE_30, ChartType.MINUTE_60
                                )) selectedType.displayName else "분",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Medium,
                            color = if (selectedType in listOf(
                                    ChartType.MINUTE_1, ChartType.MINUTE_3, ChartType.MINUTE_5,
                                    ChartType.MINUTE_15, ChartType.MINUTE_30, ChartType.MINUTE_60
                                )) Color.White else Color.Black
                        )
                        Text(
                            text = if (showMinuteDropdown) "▲" else "▼",
                            fontSize = 12.sp,
                            color = if (selectedType in listOf(
                                    ChartType.MINUTE_1, ChartType.MINUTE_3, ChartType.MINUTE_5,
                                    ChartType.MINUTE_15, ChartType.MINUTE_30, ChartType.MINUTE_60
                                )) Color.White else Color.Gray
                        )
                    }
                }

                // 드롭다운 메뉴
                DropdownMenu(
                    expanded = showMinuteDropdown,
                    onDismissRequest = { showMinuteDropdown = false }
                ) {
                    val minuteTypes = listOf(
                        ChartType.MINUTE_1, ChartType.MINUTE_3, ChartType.MINUTE_5,
                        ChartType.MINUTE_15, ChartType.MINUTE_30, ChartType.MINUTE_60
                    )

                    minuteTypes.forEach { type ->
                        DropdownMenuItem(
                            onClick = {
                                onTypeSelected(type)
                                showMinuteDropdown = false
                            }
                        ) {
                            Text(
                                text = type.displayName,
                                fontSize = 14.sp,
                                color = if (selectedType == type) redMain else Color.Black,
                                fontWeight = if (selectedType == type) FontWeight.Bold else FontWeight.Normal
                            )
                        }
                    }
                }
            }

            // 기간별 버튼들
            Row(
                modifier = Modifier.weight(2f),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                val periodTypes = listOf(ChartType.DAY, ChartType.WEEK, ChartType.MONTH)

                periodTypes.forEach { type ->
                    ChartTypeButton(
                        type = type,
                        isSelected = selectedType == type,
                        onClick = { onTypeSelected(type) },
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
fun ChartTypeButton(
    type: ChartType,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.clickable { onClick() },
        backgroundColor = if (isSelected) redMain else Color(0xFFF5F5F5),
        elevation = if (isSelected) 4.dp else 1.dp,
        shape = RoundedCornerShape(8.dp)
    ) {
        Box(
            modifier = Modifier.padding(vertical = 8.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = type.displayName,
                fontSize = 12.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                color = if (isSelected) Color.White else Color.Gray,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
fun CandleChart(
    candleData: List<CandleData>,
    chartType: ChartType,
    modifier: Modifier = Modifier
) {
    if (candleData.isEmpty()) return

    // 줌 및 팬 상태 관리
    var zoomLevel by remember { mutableStateOf(1f) }
    var offsetX by remember { mutableStateOf(0f) }
    var offsetY by remember { mutableStateOf(0f) }

    // 최소/최대 줌 레벨 설정
    val minZoom = 1.0f
    val maxZoom = 5f

    // 전체 데이터 가격 범위 계산 (줌과 무관한 기준)
    val prices = candleData.flatMap { listOf(it.highPrice, it.lowPrice) }
    val maxPrice = prices.maxOrNull() ?: 0.0
    val minPrice = prices.minOrNull() ?: 0.0
    val priceRange = (maxPrice - minPrice).coerceAtLeast(1.0)

    Box(modifier = modifier) {
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(500.dp) // 400dp에서 500dp로 증가
                .padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 80.dp)
                .pointerInput(Unit) {
                    detectDragGestures { change, dragAmount ->
                        // 드래그로 차트 이동 (기존 감도 유지)
                        offsetX -= dragAmount.x * 0.1f // 기존 감도 그대로
                        offsetY += dragAmount.y * 0.3f // 기존 감도 그대로

                        // X축 이동 범위 제한 - Y축과 동일한 공식 적용
                        val baseMaxOffsetX = size.width * (zoomLevel - 1) / 2
                        val baseXExtension = 0.5f // Y축과 동일한 기본 50%
                        val zoomXExtension = (zoomLevel - 1f) * 0.3f // Y축과 동일: 줌 레벨당 30% 추가
                        val totalXExtension = baseXExtension + zoomXExtension
                        val maxOffsetX = baseMaxOffsetX * totalXExtension
                        offsetX = offsetX.coerceIn(-maxOffsetX, maxOffsetX)

                        // Y축 이동 범위 제한 - 줌 레벨에 따라 범위 확장
                        val chartHeightPx = size.height - 60.dp.toPx()
                        val totalPriceRangeInPixels = chartHeightPx // 전체 가격 범위가 차트 높이와 매핑

                        // 줌 레벨에 비례해서 위아래 이동 범위 확장
                        val baseExtension = 0.5f // 기본 50%
                        val zoomExtension = (zoomLevel - 1f) * 0.3f // 줌 레벨당 30% 추가
                        val priceRangeExtension = baseExtension + zoomExtension
                        val maxOffsetYBasedOnPrice = totalPriceRangeInPixels * priceRangeExtension

                        offsetY = offsetY.coerceIn(-maxOffsetYBasedOnPrice, maxOffsetYBasedOnPrice)
                    }
                }
        ) {
            val canvasWidth = size.width
            val canvasHeight = size.height

            // 차트 영역 설정
            val chartLeft = 60.dp.toPx()
            val chartRight = canvasWidth - 20.dp.toPx()
            val chartTop = 20.dp.toPx()
            val chartBottom = canvasHeight - 40.dp.toPx()

            // Y축 격자선과 가격 라벨 (전체 데이터 범위 기반)
            val gridCount = (5 * zoomLevel).toInt().coerceIn(3, 15)

            // 줌에 따른 표시 가격 범위 (확대 시 줄어듬)
            val adjustedPriceRange = priceRange / zoomLevel

            // 전체 데이터 기준으로 Y 오프셋 영향 계산
            val chartHeightForPrice = chartBottom - chartTop
            val priceOffsetRatio = offsetY / chartHeightForPrice
            val priceOffsetAmount = priceOffsetRatio * priceRange * 0.5 // 전체 가격 범위의 50% 영향

            // 중심 가격 계산 (전체 데이터 범위의 중앙 + 오프셋)
            val centerPrice = minPrice + priceRange / 2 + priceOffsetAmount

            // 표시할 가격 범위 계산 (줌 레벨 반영하되 전체 데이터 범위 기준)
            val adjustedMinPrice = (centerPrice - adjustedPriceRange / 2)
                .coerceAtLeast(minPrice - priceRange * 0.25) // 전체 범위 아래 25%까지
            val adjustedMaxPrice = (centerPrice + adjustedPriceRange / 2)
                .coerceAtMost(maxPrice + priceRange * 0.25) // 전체 범위 위 25%까지

            val actualPriceRange = adjustedMaxPrice - adjustedMinPrice
            val priceStep = actualPriceRange / gridCount

            // Y축 격자선과 가격 라벨 그리기
            for (i in 0..gridCount) {
                val price = adjustedMinPrice + i * priceStep
                if (price >= adjustedMinPrice && price <= adjustedMaxPrice) {
                    val y = chartBottom - ((price - adjustedMinPrice) / actualPriceRange) * (chartBottom - chartTop)

                    if (y >= chartTop && y <= chartBottom) {
                        // 격자선
                        drawLine(
                            color = Color.LightGray.copy(alpha = 0.5f),
                            start = Offset(chartLeft, y.toFloat()),
                            end = Offset(chartRight, y.toFloat()),
                            strokeWidth = 1.dp.toPx()
                        )

                        // 가격 라벨
                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                formatPriceDetailed(price), // 더 자세한 가격 포맷팅
                                chartLeft - 10.dp.toPx(),
                                y.toFloat() + 5.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 24f
                                    textAlign = android.graphics.Paint.Align.RIGHT
                                    isAntiAlias = true
                                }
                            )
                        }
                    }
                }
            }

            // 캔들 그리기
            val visibleCandleCount = (candleData.size / zoomLevel).toInt().coerceAtLeast(1)

            // 중앙 기준으로 X 오프셋 적용 (기존 민감도 유지)
            val centerIndex = candleData.size / 2
            val offsetIndex = (offsetX / (chartRight - chartLeft) * visibleCandleCount * 10).toInt() // 기존 10배 유지
            val startIndex = (centerIndex - visibleCandleCount / 2 + offsetIndex)
                .coerceIn(0, (candleData.size - visibleCandleCount).coerceAtLeast(0))
            val endIndex = (startIndex + visibleCandleCount).coerceAtMost(candleData.size)

            val visibleCandles = if (startIndex < candleData.size && endIndex > startIndex) {
                candleData.subList(startIndex, endIndex)
            } else {
                emptyList()
            }

            val candleWidth = if (visibleCandles.isNotEmpty()) {
                (chartRight - chartLeft) / visibleCandles.size.toFloat()
            } else {
                0f
            }
            val candleBodyWidth = (candleWidth * 0.7f).coerceAtMost(12.dp.toPx())

            // 캔들 그리기
            visibleCandles.forEachIndexed { index, candle ->
                val x = chartLeft + (index + 0.5f) * candleWidth

                // 가격을 Y 좌표로 변환
                val openY = chartBottom - ((candle.openingPrice - adjustedMinPrice) / actualPriceRange * (chartBottom - chartTop)).toFloat()
                val closeY = chartBottom - ((candle.tradePrice - adjustedMinPrice) / actualPriceRange * (chartBottom - chartTop)).toFloat()
                val highY = chartBottom - ((candle.highPrice - adjustedMinPrice) / actualPriceRange * (chartBottom - chartTop)).toFloat()
                val lowY = chartBottom - ((candle.lowPrice - adjustedMinPrice) / actualPriceRange * (chartBottom - chartTop)).toFloat()

                // 캔들이 일부라도 화면에 보이면 그리기 (경계 체크 완화)
                if (x >= chartLeft - candleBodyWidth && x <= chartRight + candleBodyWidth) {

                    // 캔들 색상 결정
                    val isRising = candle.tradePrice >= candle.openingPrice
                    val candleColor = if (isRising) redMain else Color.Blue

                    // 고가-저가 세로선 (심지) - 화면 밖으로 나가는 부분도 잘라서 표시
                    val clippedHighY = highY.coerceIn(chartTop, chartBottom)
                    val clippedLowY = lowY.coerceIn(chartTop, chartBottom)

                    if (clippedHighY != clippedLowY) {
                        drawLine(
                            color = candleColor,
                            start = Offset(x, clippedHighY),
                            end = Offset(x, clippedLowY),
                            strokeWidth = 1.dp.toPx()
                        )
                    }

                    // 캔들 몸통 - 화면 밖으로 나가는 부분도 잘라서 표시
                    val bodyTop = if (isRising) closeY else openY
                    val bodyBottom = if (isRising) openY else closeY
                    val clippedBodyTop = bodyTop.coerceIn(chartTop, chartBottom)
                    val clippedBodyBottom = bodyBottom.coerceIn(chartTop, chartBottom)
                    val bodyHeight = (clippedBodyBottom - clippedBodyTop).coerceAtLeast(1.dp.toPx())

                    if (bodyHeight > 0) {
                        drawRect(
                            color = candleColor,
                            topLeft = Offset(
                                x - candleBodyWidth / 2,
                                clippedBodyTop
                            ),
                            size = androidx.compose.ui.geometry.Size(
                                candleBodyWidth,
                                bodyHeight
                            )
                        )
                    }
                }
            }

            // X축 시간 라벨 (고정 5개)
            val labelCount = 5
            if (visibleCandles.isNotEmpty() && visibleCandles.size >= labelCount) {
                val labelStep = (visibleCandles.size - 1).toFloat() / (labelCount - 1)

                for (i in 0 until labelCount) {
                    val dataIndex = (i * labelStep).toInt().coerceAtMost(visibleCandles.size - 1)
                    if (dataIndex < visibleCandles.size) {
                        val candle = visibleCandles[dataIndex]
                        val x = chartLeft + (dataIndex + 0.5f) * candleWidth

                        drawIntoCanvas { canvas ->
                            canvas.nativeCanvas.drawText(
                                formatCandleTime(candle.timestamp, chartType),
                                x,
                                chartBottom + 25.dp.toPx(),
                                android.graphics.Paint().apply {
                                    color = android.graphics.Color.GRAY
                                    textSize = 22f
                                    textAlign = android.graphics.Paint.Align.CENTER
                                    isAntiAlias = true
                                }
                            )
                        }
                    }
                }
            }
        }

        // 줌 컨트롤 버튼들 (오른쪽 상단)
        Column(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(16.dp)
        ) {
            // 확대 버튼
            Card(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(enabled = zoomLevel < maxZoom) {
                        if (zoomLevel < maxZoom) {
                            zoomLevel = (zoomLevel * 1.5f).coerceAtMost(maxZoom)
                        }
                    },
                backgroundColor = if (zoomLevel < maxZoom)
                    Color.White.copy(alpha = 0.9f)
                else
                    Color.Gray.copy(alpha = 0.3f),
                elevation = if (zoomLevel < maxZoom) 4.dp else 1.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "+",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (zoomLevel < maxZoom) redMain else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 축소 버튼
            Card(
                modifier = Modifier
                    .size(36.dp)
                    .clickable(enabled = zoomLevel > minZoom) {
                        if (zoomLevel > minZoom) {
                            zoomLevel = (zoomLevel / 1.5f).coerceAtLeast(minZoom)
                            // 줌 아웃 시 오프셋 조정
                            offsetX *= 0.8f
                            offsetY *= 0.8f
                        }
                    },
                backgroundColor = if (zoomLevel > minZoom)
                    Color.White.copy(alpha = 0.9f)
                else
                    Color.Gray.copy(alpha = 0.3f),
                elevation = if (zoomLevel > minZoom) 4.dp else 1.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "−",
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (zoomLevel > minZoom) redMain else Color.Gray
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // 리셋 버튼
            Card(
                modifier = Modifier
                    .size(36.dp)
                    .clickable {
                        zoomLevel = 1f
                        offsetX = 0f
                        offsetY = 0f
                    },
                backgroundColor = Color.White.copy(alpha = 0.9f),
                elevation = 4.dp,
                shape = RoundedCornerShape(8.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "⌂",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.Gray
                    )
                }
            }
        }

        // 줌 레벨 표시
        if (zoomLevel != 1f) {
            Card(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(16.dp),
                backgroundColor = Color.Black.copy(alpha = 0.7f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "×${String.format("%.1f", zoomLevel)}",
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    fontSize = 12.sp,
                    color = Color.White
                )
            }
        }
    }
}
