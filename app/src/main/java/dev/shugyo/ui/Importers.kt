package dev.shugyo.ui

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import dev.shugyo.scan.Basket
import dev.shugyo.scan.Images
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Ways to add pages besides the browser: photos and screenshots, a PDF, or pasted text. */
class Importers(val photos: () -> Unit, val pdf: () -> Unit, val paste: () -> Unit)

@Composable
fun rememberImporters(onAdded: () -> Unit): Importers {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var working by remember { mutableStateOf("") }
    var pdfUri by remember { mutableStateOf<Uri?>(null) }
    var pdfPages by remember { mutableIntStateOf(0) }
    var pasting by remember { mutableStateOf(false) }

    val photoPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(40)) { uris ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        scope.launch {
            working = "Adding ${uris.size} pictures…"
            withContext(Dispatchers.IO) {
                uris.forEach { u -> Images.fromUri(context, u)?.let { Basket.addSnap(it, "Photo") } }
            }
            working = ""
            onAdded()
        }
    }
    val pdfPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) Incoming.pdf.value = uri
    }

    // A PDF picked here or shared from another app.
    val incomingPdf by Incoming.pdf
    LaunchedEffect(incomingPdf) {
        val uri = incomingPdf ?: return@LaunchedEffect
        Incoming.pdf.value = null
        val n = withContext(Dispatchers.IO) { Images.pdfPageCount(context, uri) }
        if (n > 0) {
            pdfPages = n
            pdfUri = uri
        }
    }

    pdfUri?.let { uri ->
        PdfRangeDialog(
            pages = pdfPages,
            onDismiss = { pdfUri = null },
            onPick = { from, to ->
                pdfUri = null
                scope.launch {
                    working = "Reading PDF pages…"
                    withContext(Dispatchers.IO) {
                        Images.renderPdf(context, uri, from, to) { n, bmp ->
                            Basket.addSnap(bmp, "PDF page $n")
                            working = "Reading PDF page $n of $to…"
                        }
                    }
                    working = ""
                    onAdded()
                }
            },
        )
    }

    if (pasting) {
        PasteDialog(onDismiss = { pasting = false }) { title, text ->
            pasting = false
            Basket.addText(text, title)
            onAdded()
        }
    }

    if (working.isNotEmpty()) {
        Dialog(onDismissRequest = {}) {
            InkCard(color = CardHigh) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = Gold, strokeWidth = 3.dp)
                    Text(working, color = Paper)
                }
            }
        }
    }

    return remember {
        Importers(
            photos = { photoPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
            pdf = { pdfPicker.launch(arrayOf("application/pdf")) },
            paste = { pasting = true },
        )
    }
}

@Composable
private fun PdfRangeDialog(pages: Int, onDismiss: () -> Unit, onPick: (Int, Int) -> Unit) {
    var from by remember { mutableStateOf("1") }
    var to by remember { mutableStateOf(minOf(pages, 20).toString()) }
    val a = from.toIntOrNull()?.coerceIn(1, pages) ?: 1
    val b = to.toIntOrNull()?.coerceIn(a, pages) ?: a
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        title = { Text("Which pages?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("This PDF has $pages pages. Pick the chapter's pages. Long ranges are read in batches of 8.", color = Soft)
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(
                        from, { from = it.filter(Char::isDigit) }, label = { Text("From") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                    OutlinedTextField(
                        to, { to = it.filter(Char::isDigit) }, label = { Text("To") }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier.weight(1f),
                    )
                }
                Text("${b - a + 1} pages", color = Faint, fontSize = 13.sp)
            }
        },
        confirmButton = { TextButton(onClick = { onPick(a, b) }) { Text("Add pages", color = Gold) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Faint) } },
    )
}

@Composable
private fun PasteDialog(onDismiss: () -> Unit, onAdd: (String, String) -> Unit) {
    var title by remember { mutableStateOf("") }
    var text by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = CardHigh,
        title = { Text("Paste text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(title, { title = it }, label = { Text("Title (optional)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    text, { text = it }, label = { Text("Text from your book or notes") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 160.dp, max = 320.dp),
                )
            }
        },
        confirmButton = {
            TextButton(enabled = text.isNotBlank(), onClick = { onAdd(title, text) }) { Text("Add", color = Gold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = Faint) } },
    )
}
