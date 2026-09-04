package com.example.cricketscorer.ui

import android.content.Context
import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.common.moduleinstall.ModuleInstall
import com.google.android.gms.common.moduleinstall.ModuleInstallRequest
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

/**
 * req #5: "sometime it shows just packages or module is downloading msg, instead show some
 * loading icon like how much percentage is downloaded." The QR scanner needs Google Play
 * services' on-device barcode-scanning module — if it isn't installed yet, launching
 * [scanQrCodeForMatch] directly shows Play Services' own generic "Getting things ready" sheet
 * with no real progress. Pre-installing the module ourselves via [ModuleInstall] first lets the
 * caller show its own percentage-based progress UI instead (see RoomsScreen's scan flow) —
 * launching the scanner afterwards is then instant since the module is already there.
 */
fun ensureBarcodeScannerModuleInstalled(
    context: Context,
    onProgress: (percent: Int) -> Unit,
    onReady: () -> Unit,
    onFailure: (String) -> Unit
) {
    val scannerClient = GmsBarcodeScanning.getClient(context)
    val moduleInstallClient = ModuleInstall.getClient(context)

    moduleInstallClient.areModulesAvailable(scannerClient)
        .addOnSuccessListener { response ->
            if (response.areModulesAvailable()) {
                onReady()
            } else {
                val request = ModuleInstallRequest.newBuilder()
                    .addApi(scannerClient)
                    .setListener { update ->
                        val progress = update.progressInfo
                        if (progress != null && progress.totalBytesToDownload > 0) {
                            val percent = (progress.bytesDownloaded * 100 / progress.totalBytesToDownload)
                                .toInt()
                                .coerceIn(0, 100)
                            onProgress(percent)
                        }
                    }
                    .build()
                moduleInstallClient.installModules(request)
                    .addOnSuccessListener { onReady() }
                    .addOnFailureListener { e ->
                        onFailure(
                            e.message ?: "Couldn't download the scanner. Check your connection and try again."
                        )
                    }
            }
        }
        .addOnFailureListener { e ->
            onFailure(e.message ?: "Couldn't check the scanner's download status.")
        }
}
