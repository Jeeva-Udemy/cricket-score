package com.example.cricketscorer.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.codescanner.GmsBarcodeScannerOptions
import com.google.mlkit.vision.codescanner.GmsBarcodeScanning
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter

/**
 * Renders [text] (the 6-character Match Code) as a black-on-white QR bitmap using zxing.
 * Pure on-device rendering — no permissions or network needed. Returns null if [text] is
 * blank or somehow fails to encode.
 */
fun generateQrCodeBitmap(text: String, sizePx: Int = 512): ImageBitmap? {
    if (text.isBlank()) return null
    return runCatching {
        val bitMatrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, sizePx, sizePx)
        val bitmap = Bitmap.createBitmap(sizePx, sizePx, Bitmap.Config.RGB_565)
        val black = androidx.compose.ui.graphics.Color.Black.toArgb()
        val white = androidx.compose.ui.graphics.Color.White.toArgb()
        for (x in 0 until sizePx) {
            for (y in 0 until sizePx) {
                bitmap.setPixel(x, y, if (bitMatrix[x, y]) black else white)
            }
        }
        bitmap.asImageBitmap()
    }.getOrNull()
}

/**
 * Launches Google's built-in code-scanner UI (a full-screen Activity supplied by Play
 * services) to scan a QR code, then hands the decoded text back via [onResult]. No CAMERA
 * permission is requested by this app — Play services owns that permission itself, which is
 * also why [context] should be an Activity context (it starts an Activity for the result
 * under the hood).
 */
fun scanQrCodeForMatch(
    context: Context,
    onResult: (String) -> Unit,
    onFailure: (String) -> Unit
) {
    val options = GmsBarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    GmsBarcodeScanning.getClient(context, options)
        .startScan()
        .addOnSuccessListener { barcode ->
            val value = barcode.rawValue?.trim()
            if (value.isNullOrBlank()) {
                onFailure("That QR code didn't contain a match code.")
            } else {
                onResult(value)
            }
        }
        .addOnFailureListener { e ->
            onFailure(e.message ?: "Couldn't scan the code. Try entering it manually.")
        }
        .addOnCanceledListener { /* user backed out of the scanner — no error to show */ }
}
