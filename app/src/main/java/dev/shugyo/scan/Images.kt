package dev.shugyo.scan

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.os.ParcelFileDescriptor
import android.util.Base64
import android.webkit.CookieManager
import java.io.ByteArrayOutputStream
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.max
import kotlin.math.roundToInt

/** Decoding, shrinking and saving pictures, and turning PDF pages into pictures. */
object Images {
    const val MAX_SIDE = 1600

    fun decode(bytes: ByteArray, maxSide: Int = MAX_SIDE): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
        var sample = 1
        while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts) ?: return null
        return shrink(bmp, maxSide)
    }

    fun decodeFile(file: File, maxSide: Int = MAX_SIDE): Bitmap? =
        if (file.exists()) decode(file.readBytes(), maxSide) else null

    fun shrink(bmp: Bitmap, maxSide: Int): Bitmap {
        val m = max(bmp.width, bmp.height)
        if (m <= maxSide) return bmp
        val s = maxSide.toFloat() / m
        return Bitmap.createScaledBitmap(bmp, (bmp.width * s).roundToInt().coerceAtLeast(1), (bmp.height * s).roundToInt().coerceAtLeast(1), true)
    }

    fun jpeg(bmp: Bitmap, quality: Int = 85): ByteArray {
        // Pictures with see-through parts get a white page behind them.
        val src = if (bmp.hasAlpha()) {
            val flat = Bitmap.createBitmap(bmp.width, bmp.height, Bitmap.Config.ARGB_8888)
            Canvas(flat).apply {
                drawColor(Color.WHITE)
                drawBitmap(bmp, 0f, 0f, null)
            }
            flat
        } else bmp
        val out = ByteArrayOutputStream()
        src.compress(Bitmap.CompressFormat.JPEG, quality, out)
        return out.toByteArray()
    }

    fun save(bmp: Bitmap, file: File, maxSide: Int = MAX_SIDE) {
        file.parentFile?.mkdirs()
        file.writeBytes(jpeg(shrink(bmp, maxSide)))
    }

    /** A smaller JPEG for sending to the AI. */
    fun forAi(file: File, maxSide: Int = 1280): ByteArray? {
        val b = decodeFile(file, maxSide) ?: return null
        return jpeg(b, 80)
    }

    fun fromDataUrl(url: String): ByteArray? {
        val comma = url.indexOf(',')
        if (!url.startsWith("data:") || comma < 0) return null
        return try {
            Base64.decode(url.substring(comma + 1), Base64.DEFAULT)
        } catch (e: Exception) {
            null
        }
    }

    /** Downloads a picture with the browser's sign-in cookies (textbook images are often behind a login). */
    fun download(url: String, referer: String, userAgent: String): ByteArray? {
        if (!url.startsWith("http")) return null
        return try {
            val c = URL(url).openConnection() as HttpURLConnection
            c.connectTimeout = 15_000
            c.readTimeout = 20_000
            CookieManager.getInstance().getCookie(url)?.let { c.setRequestProperty("Cookie", it) }
            if (referer.isNotBlank()) c.setRequestProperty("Referer", referer)
            if (userAgent.isNotBlank()) c.setRequestProperty("User-Agent", userAgent)
            if (c.responseCode != 200) return null
            c.inputStream.use { it.readBytes() }
        } catch (e: Exception) {
            null
        }
    }

    fun fromUri(context: Context, uri: Uri): Bitmap? = try {
        context.contentResolver.openInputStream(uri)?.use { decode(it.readBytes()) }
    } catch (e: Exception) {
        null
    }

    fun pdfPageCount(context: Context, uri: Uri): Int = try {
        context.contentResolver.openFileDescriptor(uri, "r")?.use { fd ->
            PdfRenderer(fd).use { it.pageCount }
        } ?: 0
    } catch (e: Exception) {
        0
    }

    /** Renders PDF pages [from]..[to] (1-based) as pictures and hands each one to [onPage]. */
    fun renderPdf(context: Context, uri: Uri, from: Int, to: Int, onPage: (Int, Bitmap) -> Unit) {
        val fd: ParcelFileDescriptor = context.contentResolver.openFileDescriptor(uri, "r") ?: return
        fd.use {
            PdfRenderer(it).use { r ->
                val a = (from - 1).coerceIn(0, r.pageCount - 1)
                val b = (to - 1).coerceIn(a, r.pageCount - 1)
                for (i in a..b) {
                    r.openPage(i).use { page ->
                        val w = 1400
                        val h = (w.toFloat() * page.height / page.width).roundToInt().coerceAtLeast(1)
                        val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                        bmp.eraseColor(Color.WHITE)
                        page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                        onPage(i + 1, bmp)
                    }
                }
            }
        }
    }

    /** A crop of a page picture, with the box on a 0–1000 scale as [ymin, xmin, ymax, xmax]. */
    fun crop(bmp: Bitmap, box: List<Int>): Bitmap? {
        if (box.size != 4) return null
        val (y0, x0, y1, x1) = box.map { it.coerceIn(0, 1000) }
        if (y1 - y0 < 40 || x1 - x0 < 40) return null
        // A little room around the figure so labels at the edge aren't cut off.
        val pad = 12
        val left = ((x0 - pad).coerceAtLeast(0) / 1000f * bmp.width).roundToInt()
        val top = ((y0 - pad).coerceAtLeast(0) / 1000f * bmp.height).roundToInt()
        val right = ((x1 + pad).coerceAtMost(1000) / 1000f * bmp.width).roundToInt()
        val bottom = ((y1 + pad).coerceAtMost(1000) / 1000f * bmp.height).roundToInt()
        val w = (right - left).coerceAtMost(bmp.width - left)
        val h = (bottom - top).coerceAtMost(bmp.height - top)
        if (w < 40 || h < 40) return null
        return Bitmap.createBitmap(bmp, left, top, w, h)
    }
}
