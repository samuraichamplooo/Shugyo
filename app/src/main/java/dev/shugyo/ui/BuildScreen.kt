package dev.shugyo.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.ai.Ai
import dev.shugyo.data.Prefs
import dev.shugyo.data.Store
import dev.shugyo.scan.Basket
import dev.shugyo.scan.BasketItem

/** Everything you've scanned, ready to become a study set. */
@Composable
fun BuildScreen() {
    val items by Basket.items.collectAsState()
    val run by BuildRunner.state.collectAsState()
    val settings by Prefs.settings.collectAsState()
    var title by remember { mutableStateOf("") }
    var subject by remember { mutableStateOf(Store.subjects().firstOrNull().orEmpty()) }
    val decks by Store.decks.collectAsState()
    var target by remember { mutableStateOf<String?>(null) }   // null: a new study set
    val targetDeck = decks.firstOrNull { it.id == target }
    val importers = rememberImporters(onAdded = {})
    val context = androidx.compose.ui.platform.LocalContext.current
    val askNotifications = androidx.activity.compose.rememberLauncherForActivityResult(
        androidx.activity.result.contract.ActivityResultContracts.RequestPermission(),
    ) { }
    fun startBuild() {
        if (android.os.Build.VERSION.SDK_INT >= 33 &&
            androidx.core.content.ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            try {
                askNotifications.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            } catch (e: Exception) {
                // no permission screen: build anyway
            }
        }
        BuildRunner.start(context, items, title, subject, target)
    }

    LaunchedEffect(run.doneDeck) {
        if (run.doneDeck.isNotEmpty() && !run.upgrade) {
            val id = run.doneDeck
            BuildRunner.clear()
            Basket.clear()
            Nav.replace(Route.DeckHome(id))
        }
    }

    if (run.running) {
        Building(run)
        return
    }

    Screen("Build a study set", subtitle = "Scan as many pages of the chapter as you like, then build") {
        LazyColumn(
            Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (run.error.isNotEmpty() && !run.upgrade) {
                item {
                    InkCard(color = BadDeep, border = Bad.copy(alpha = 0.4f)) {
                        Text("Couldn't build it", color = Bad, fontWeight = FontWeight.Bold)
                        Text(run.error, color = Soft, fontSize = 14.sp)
                    }
                }
            }
            if (items.isEmpty()) {
                item {
                    EmptyNote(
                        "🧺", "Nothing scanned yet",
                        "Open your textbook in the browser and tap Scan page, or add screenshots, photos, a PDF or text.",
                    )
                }
            }
            val pages = items.filterIsInstance<BasketItem.Page>()
            val snaps = items.filterIsInstance<BasketItem.Snap>()
            items(pages, key = { it.id }) { p ->
                InkCard {
                    Row(verticalAlignment = Alignment.Top) {
                        Column(Modifier.weight(1f)) {
                            Text(p.title, color = Paper, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (p.url.isNotBlank()) {
                                Text(p.url, color = Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Pill("${"%,d".format(p.words)} words", LearnColor)
                                if (p.images.isNotEmpty()) Pill("${p.images.size} pictures", NotesColor)
                                if (p.tables > 0) Pill("${p.tables} tables", Gold)
                                if (p.definitions > 0) Pill("${p.definitions} definitions", QuizColor)
                            }
                        }
                        IconButton(onClick = { Basket.remove(p.id) }) { Icon(Icons.Filled.Close, "Remove", tint = Faint) }
                    }
                }
            }
            if (snaps.isNotEmpty()) {
                item {
                    InkCard {
                        Text("${snaps.size} page pictures", color = Paper, fontWeight = FontWeight.SemiBold)
                        Text("The AI reads the text, tables and diagrams in these.", color = Faint, fontSize = 13.sp)
                        Spacer(Modifier.height(10.dp))
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(snaps, key = { it.id }) { s ->
                                Box {
                                    LocalImage(
                                        s.file,
                                        Modifier
                                            .size(width = 84.dp, height = 140.dp)
                                            .clip(RoundedCornerShape(10.dp)),
                                        contentScale = ContentScale.Crop,
                                        maxSide = 400,
                                    )
                                    Box(
                                        Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(4.dp)
                                            .size(24.dp)
                                            .clip(CircleShape)
                                            .background(Ink.copy(alpha = 0.8f))
                                            .clickable { Basket.remove(s.id) },
                                        contentAlignment = Alignment.Center,
                                    ) { Text("✕", color = Paper, fontSize = 12.sp) }
                                }
                            }
                        }
                    }
                }
            }
            item {
                SectionLabel("Add more")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    GhostButton("🌐 Textbook", { Nav.go(Route.Browser) })
                    GhostButton("🖼 Photos", importers.photos)
                    GhostButton("📄 PDF", importers.pdf)
                    GhostButton("📋 Paste", importers.paste)
                }
            }
            if (decks.isNotEmpty()) {
                item {
                    SectionLabel("Save to")
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Choice("＋ New study set", target == null) { target = null }
                        decks.take(20).forEach { d ->
                            Choice("Add to: ${d.title}", target == d.id) { target = d.id }
                        }
                    }
                    if (targetDeck != null) {
                        Spacer(Modifier.height(8.dp))
                        Text(
                            "These pages join \"${targetDeck.title}\" as more objectives, terms and questions. Your progress there is kept.",
                            color = Faint, fontSize = 13.sp,
                        )
                    }
                }
            }
            if (targetDeck == null) item {
                SectionLabel("About this set")
                OutlinedTextField(
                    title, { title = it }, singleLine = true,
                    label = { Text("Title (optional, the AI finds it)") },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    subject, { subject = it }, singleLine = true,
                    label = { Text("Class, e.g. Microbiology") },
                    modifier = Modifier.fillMaxWidth(),
                )
                val known = Store.subjects().filter { it != subject }
                if (known.isNotEmpty()) {
                    Spacer(Modifier.height(8.dp))
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        known.forEach { k -> Box(Modifier.clickable { subject = k }) { Pill(k, Faint) } }
                    }
                }
            }
            item {
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Questions per objective", color = Paper)
                        Text("More questions take a little longer to build", color = Faint, fontSize = 12.sp)
                    }
                    Stepper(settings.questionsPerObjective, 2, 10) { v -> Prefs.update { it.copy(questionsPerObjective = v) } }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            if (!Ai.ready()) {
                Text("Add your free Gemini key first (it takes a minute).", color = Gold, fontSize = 14.sp)
                Spacer(Modifier.height(8.dp))
                BigButton("Open Settings", { Nav.go(Route.Settings) }, Modifier.fillMaxWidth(), color = Gold, textColor = Ink)
            } else {
                BigButton(
                    if (targetDeck != null) "⚔️ Add to my study set" else "⚔️ Build my study set",
                    { startBuild() },
                    Modifier.fillMaxWidth(),
                    enabled = items.isNotEmpty(),
                )
            }
        }
    }
}

@Composable
private fun Choice(text: String, on: Boolean, onClick: () -> Unit) {
    Box(
        Modifier
            .clip(RoundedCornerShape(50))
            .background(if (on) Seal else CardHigh)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 9.dp),
    ) {
        Text(text, color = Paper, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.widthIn(max = 280.dp))
    }
}

@Composable
fun Stepper(value: Int, min: Int, max: Int, step: Int = 1, onChange: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = { onChange((value - step).coerceAtLeast(min)) }, enabled = value > min) {
            Text("−", color = if (value > min) Paper else Faint, fontSize = 22.sp)
        }
        Text("$value", color = Paper, fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(horizontal = 4.dp))
        IconButton(onClick = { onChange((value + step).coerceAtMost(max)) }, enabled = value < max) {
            Text("+", color = if (value < max) Paper else Faint, fontSize = 22.sp)
        }
    }
}

/** The waiting screen while the AI builds the set: a brush circle that keeps turning. */
@Composable
private fun Building(run: BuildRunner.State) {
    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(0f, 360f, infiniteRepeatable(tween(2400, easing = LinearEasing)), label = "angle")
    val pulse by spin.animateFloat(0.85f, 1f, infiniteRepeatable(tween(900), RepeatMode.Reverse), label = "pulse")
    Column(
        Modifier
            .fillMaxSize()
            .background(Ink)
            .padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(170.dp), contentAlignment = Alignment.Center) {
            Canvas(Modifier.fillMaxSize()) {
                val w = 14.dp.toPx()
                rotate(angle) {
                    drawArc(
                        Seal, 0f, 300f, false, Offset(w, w), Size(size.width - 2 * w, size.height - 2 * w),
                        style = Stroke(w, cap = StrokeCap.Round),
                    )
                }
            }
            Text("修", fontSize = (64 * pulse).sp, color = Paper, fontFamily = Mincho, fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(28.dp))
        Text("Training in progress", style = MaterialTheme.typography.headlineSmall, color = Paper)
        Spacer(Modifier.height(8.dp))
        Text(run.status, color = Soft, textAlign = TextAlign.Center, fontSize = 15.sp)
        Spacer(Modifier.height(20.dp))
        Bar(run.fraction, Gold, height = 10.dp)
        Spacer(Modifier.height(8.dp))
        Text("${(run.fraction * 100).toInt()}%", color = Faint, fontSize = 13.sp)
        Spacer(Modifier.height(24.dp))
        var seconds by remember { androidx.compose.runtime.mutableIntStateOf(0) }
        LaunchedEffect(Unit) {
            while (true) {
                kotlinx.coroutines.delay(1000)
                seconds++
            }
        }
        Text(
            "%d:%02d".format(seconds / 60, seconds % 60) + " · a full chapter takes a few minutes",
            color = Faint, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You can switch apps or lock your phone. Shugyō keeps going and shows its progress in your notifications. " +
                "If it stops, finished steps are saved and Build picks up from there.",
            color = Faint, fontSize = 13.sp, textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(20.dp))
        GhostButton("Cancel", { BuildRunner.cancel() })
    }
}
