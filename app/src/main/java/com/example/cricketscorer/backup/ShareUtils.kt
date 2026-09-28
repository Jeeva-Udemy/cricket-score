package com.example.cricketscorer.backup

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.content.FileProvider
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Hands files to WhatsApp (or any other app) through the app's FileProvider — used by the
 * Match Dashboard image and Share Data. Files go to cache/shared/ (see res/xml/file_paths.xml)
 * and are replaced on every share, so they never pile up.
 */
object ShareUtils {

    private const val WHATSAPP = "com.whatsapp"
    private const val WHATSAPP_BUSINESS = "com.whatsapp.w4b"

    private fun sharedDir(context: Context): File =
        File(context.cacheDir, "shared").apply { mkdirs() }

    private fun uriFor(context: Context, file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)

    fun timestamp(): String = SimpleDateFormat("yyyyMMdd-HHmm", Locale.US).format(Date())

    /** Writes [bitmap] as PNG and returns a shareable content:// Uri. Blocking — call off the main thread. */
    fun saveBitmap(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val dir = sharedDir(context)
        dir.listFiles { f -> f.name.endsWith(".png") }?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return uriFor(context, file)
    }

    /** Writes [text] to a file and returns a shareable content:// Uri. Blocking — call off the main thread. */
    fun saveText(context: Context, text: String, fileName: String): Uri {
        val dir = sharedDir(context)
        dir.listFiles { f -> f.name.endsWith(".json") }?.forEach { it.delete() }
        val file = File(dir, fileName)
        file.writeText(text)
        return uriFor(context, file)
    }

    /**
     * Opens WhatsApp directly with [uri] attached when [preferWhatsApp] is true (falls back to
     * WhatsApp Business, then to the normal share sheet if neither is installed).
     */
    fun shareFile(
        context: Context,
        uri: Uri,
        mimeType: String,
        message: String,
        chooserTitle: String,
        preferWhatsApp: Boolean
    ) {
        fun baseIntent() = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, message)
            clipData = android.content.ClipData.newRawUri("", uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }

        if (preferWhatsApp) {
            for (pkg in listOf(WHATSAPP, WHATSAPP_BUSINESS)) {
                try {
                    context.startActivity(baseIntent().setPackage(pkg))
                    return
                } catch (e: ActivityNotFoundException) {
                    // try the next option
                }
            }
        }
        val chooser = Intent.createChooser(baseIntent(), chooserTitle).apply {
            if (context !is android.app.Activity) addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
