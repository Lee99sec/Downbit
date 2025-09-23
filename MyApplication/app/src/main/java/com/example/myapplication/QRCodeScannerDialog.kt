// QRCodeScannerDialog.kt - 수정된 버전
package com.example.myapplication

import android.Manifest
import android.util.Log
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

@OptIn(ExperimentalPermissionsApi::class)
@Composable
fun QRCodeScannerDialog(
    onDismiss: () -> Unit,
    onQrCodeScanned: (String) -> Unit
) {
    val cameraPermissionState = rememberPermissionState(Manifest.permission.CAMERA)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            when {
                cameraPermissionState.status.isGranted -> {
                    // 권한이 허용된 경우 QR 스캐너 표시
                    QRCodeScanner(
                        onQrCodeScanned = onQrCodeScanned,
                        onClose = onDismiss
                    )
                }
                cameraPermissionState.status.shouldShowRationale -> {
                    // 권한 설명이 필요한 경우
                    PermissionRationaleContent(
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                        onDismiss = onDismiss
                    )
                }
                else -> {
                    // 처음 권한 요청하는 경우
                    PermissionRequestContent(
                        onRequestPermission = { cameraPermissionState.launchPermissionRequest() },
                        onDismiss = onDismiss
                    )
                }
            }
        }
    }
}

@Composable
private fun QRCodeScanner(
    onQrCodeScanned: (String) -> Unit,
    onClose: () -> Unit
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasScanned by remember { mutableStateOf(false) }

    Box(modifier = Modifier.fillMaxSize()) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val executor: ExecutorService = Executors.newSingleThreadExecutor()

                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener({
                    val cameraProvider = cameraProviderFuture.get()

                    val preview = Preview.Builder().build().also {
                        it.setSurfaceProvider(previewView.surfaceProvider)
                    }

                    val imageAnalysis = ImageAnalysis.Builder()
                        .setTargetResolution(Size(1280, 720))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build()

                    imageAnalysis.setAnalyzer(executor) { imageProxy ->
                        if (!hasScanned) {
                            processImageProxy(imageProxy) { qrCode ->
                                if (!hasScanned) {
                                    hasScanned = true
                                    onQrCodeScanned(qrCode)
                                }
                            }
                        } else {
                            imageProxy.close()
                        }
                    }

                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

                    try {
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                            lifecycleOwner,
                            cameraSelector,
                            preview,
                            imageAnalysis
                        )
                    } catch (exc: Exception) {
                        Log.e("QRScanner", "카메라 바인딩 실패", exc)
                    }

                }, ContextCompat.getMainExecutor(ctx))

                previewView
            },
            modifier = Modifier.fillMaxSize()
        )

        // QR 스캔 가이드 오버레이
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            // 스캔 영역 표시
            Box(
                modifier = Modifier
                    .size(250.dp)
                    .background(
                        Color.Transparent,
                        RoundedCornerShape(16.dp)
                    )
            ) {
                // 모서리 가이드 라인들
                val cornerSize = 40.dp
                val cornerThickness = 4.dp

                // 왼쪽 위
                Box(
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .size(cornerSize)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cornerThickness)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(cornerThickness)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }

                // 오른쪽 위
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(cornerSize)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cornerThickness)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(cornerThickness)
                            .fillMaxHeight()
                            .align(Alignment.TopEnd)
                            .background(Color.White)
                    )
                }

                // 왼쪽 아래
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .size(cornerSize)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cornerThickness)
                            .align(Alignment.BottomStart)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(cornerThickness)
                            .fillMaxHeight()
                            .background(Color.White)
                    )
                }

                // 오른쪽 아래
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .size(cornerSize)
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(cornerThickness)
                            .align(Alignment.BottomEnd)
                            .background(Color.White)
                    )
                    Box(
                        modifier = Modifier
                            .width(cornerThickness)
                            .fillMaxHeight()
                            .align(Alignment.BottomEnd)
                            .background(Color.White)
                    )
                }
            }
        }

        // 상단 제목과 닫기 버튼
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "QR 코드 스캔",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            IconButton(
                onClick = onClose,
                modifier = Modifier
                    .background(
                        Color.Black.copy(alpha = 0.5f),
                        RoundedCornerShape(20.dp)
                    )
            ) {
                Icon(
                    Icons.Default.Close,
                    contentDescription = "닫기",
                    tint = Color.White
                )
            }
        }

        // 하단 안내 메시지
        Text(
            text = "지갑 주소 QR 코드를\n화면 중앙에 맞춰주세요",
            color = Color.White,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 100.dp)
                .background(
                    Color.Black.copy(alpha = 0.7f),
                    RoundedCornerShape(8.dp)
                )
                .padding(16.dp)
        )
    }
}

@Composable
private fun PermissionRequestContent(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.White,
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "카메라 권한 필요",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "QR 코드를 스캔하려면\n카메라 권한이 필요합니다.",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = Color.Gray)
                    }

                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD32F2F)
                        )
                    ) {
                        Text("권한 허용", color = Color.White)
                    }
                }
            }
        }
    }
}

@Composable
private fun PermissionRationaleContent(
    onRequestPermission: () -> Unit,
    onDismiss: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(32.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            backgroundColor = Color.White,
            elevation = 8.dp,
            shape = RoundedCornerShape(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    "카메라 권한이 필요합니다",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(bottom = 16.dp)
                )

                Text(
                    "QR 코드 스캔 기능을 사용하려면\n카메라에 접근할 수 있는 권한이 필요합니다.\n\n설정에서 직접 권한을 허용하거나\n다시 시도해 주세요.",
                    fontSize = 16.sp,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.padding(bottom = 24.dp)
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    TextButton(onClick = onDismiss) {
                        Text("취소", color = Color.Gray)
                    }

                    Button(
                        onClick = onRequestPermission,
                        colors = ButtonDefaults.buttonColors(
                            backgroundColor = Color(0xFFD32F2F)
                        )
                    ) {
                        Text("다시 시도", color = Color.White)
                    }
                }
            }
        }
    }
}

private fun processImageProxy(
    imageProxy: ImageProxy,
    onQrCodeDetected: (String) -> Unit
) {
    val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()

    val scanner = BarcodeScanning.getClient(options)
    val mediaImage = imageProxy.image

    if (mediaImage != null) {
        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)

        scanner.process(image)
            .addOnSuccessListener { barcodes ->
                // break 문제를 해결하기 위해 forEach 대신 일반 for 문을 사용
                var found = false
                for (barcode in barcodes) {
                    if (!found) {
                        barcode.rawValue?.let { qrCode ->
                            onQrCodeDetected(qrCode)
                            found = true
                        }
                    }
                }
            }
            .addOnFailureListener { exception ->
                Log.e("QRScanner", "바코드 스캔 실패", exception)
            }
            .addOnCompleteListener {
                imageProxy.close()
            }
    } else {
        imageProxy.close()
    }
}