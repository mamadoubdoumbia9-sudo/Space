package com.signalpro.app.ui

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.MultiFormatWriter

/**
 * Génère un QR code localement (ZXing) à partir de la charge de couplage
 * fournie par la passerelle installée chez l'utilisateur.
 *
 * Aucune image n'est téléchargée : le contenu du QR ne transite que du
 * serveur vers l'application, puis vers WhatsApp par le scanner de
 * l'utilisateur.
 */
@Composable
fun rememberQrBitmap(content: String, sizePx: Int = 768): ImageBitmap? = remember(content, sizePx) {
    qrBitmap(content, sizePx)
}

fun qrBitmap(content: String, sizePx: Int = 768): ImageBitmap? = runCatching {
    if (content.isBlank()) return null
    val hints = mapOf(
        EncodeHintType.MARGIN to 1,
        EncodeHintType.CHARACTER_SET to "UTF-8",
    )
    val matrix = MultiFormatWriter().encode(content, BarcodeFormat.QR_CODE, sizePx, sizePx, hints)
    val bitmap = Bitmap.createBitmap(matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
    for (x in 0 until matrix.width) {
        for (y in 0 until matrix.height) {
            bitmap.setPixel(x, y, if (matrix.get(x, y)) AndroidColor.BLACK else AndroidColor.WHITE)
        }
    }
    bitmap.asImageBitmap()
}.getOrNull()
