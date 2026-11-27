package dev.shugyo.ui

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.ai.Coach
import dev.shugyo.build.Prompts
import dev.shugyo.data.Figure
import dev.shugyo.data.Question
import dev.shugyo.data.QType
import dev.shugyo.data.Store
import dev.shugyo.data.TableBlock
import dev.shugyo.data.Term
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

// ---------------------------------------------------------------- questions

/** Where you are with one question. */
class QState {
    var picked by mutableStateOf(setOf<Int>())
    var checked by mutableStateOf(false)
    var hint by mutableStateOf(false)
}

fun levelLabel(level: Int) = when (level) {
    3 -> "Apply"
    2 -> "Understand"
    else -> "Recall"
}

@Composable
fun QuestionCard(q: Question, st: QState, onChecked: (Boolean) -> Unit) {
    InkCard(Modifier.fillMaxWidth().animateContentSize()) {
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Pill(q.typeLabel, QuizColor)
            Pill(levelLabel(q.level), if (q.level >= 3) Gold else Faint)
        }
        Spacer(Modifier.height(12.dp))
        RichText(q.prompt, color = Paper, style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.Medium))
        Spacer(Modifier.height(12.dp))
        q.choices.forEachIndexed { i, c ->
            ChoiceRow(
                text = c,
                multi = q.isMulti,
                picked = i in st.picked,
                checked = st.checked,
                right = i in q.answer,
            ) {
                if (st.checked) return@ChoiceRow
                st.picked = if (q.isMulti) {
                    if (i in st.picked) st.picked - i else st.picked + i
                } else setOf(i)
            }
            Spacer(Modifier.height(8.dp))
        }
        if (st.hint && q.hint.isNotBlank() && !st.checked) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Gold.copy(alpha = 0.12f))
                    .padding(12.dp),
            ) { RichText("💡 ${q.hint}", color = Gold, style = MaterialTheme.typography.bodyMedium) }
            Spacer(Modifier.height(8.dp))
        }
        if (!st.checked) {
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                if (q.hint.isNotBlank()) {
                    GhostButton("💡 Hint", { st.hint = true }, Modifier.weight(1f), enabled = !st.hint)
                }
                BigButton(
                    "Check", {
                        st.checked = true
                        onChecked(q.isRight(st.picked))
                    },
                    Modifier.weight(1f), enabled = st.picked.isNotEmpty(),
                )
            }
        } else {
            val ok = q.isRight(st.picked)
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(if (ok) GoodDeep else BadDeep)
                    .padding(14.dp),
            ) {
                Text(
                    if (ok) listOf("✅ Nailed it!", "✅ Correct!", "✅ That's it!").random() else "❌ Not quite",
                    color = if (ok) Good else Bad, fontWeight = FontWeight.Bold,
                )
                if (!ok && q.type == QType.SATA) {
                    Text("Right answers: ${q.answer.joinToString { ('A' + it).toString() }}", color = Soft, fontSize = 14.sp)
                }
                if (q.explain.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    RichText(q.explain, color = Soft, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun ChoiceRow(text: String, multi: Boolean, picked: Boolean, checked: Boolean, right: Boolean, onClick: () -> Unit) {
    val (bg, border) = when {
        checked && right -> GoodDeep to Good
        checked && picked -> BadDeep to Bad
        picked -> SealDeep to Seal
        else -> CardHigh to Line
    }
    val shape = RoundedCornerShape(14.dp)
    Row(
        Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(bg)
            .border(1.dp, border, shape)
            .clickable(enabled = !checked, onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val mark = when {
            checked && right -> "✓"
            checked && picked -> "✕"
            picked -> if (multi) "✓" else "●"
            else -> ""
        }
        Box(
            Modifier
                .size(24.dp)
                .clip(if (multi) RoundedCornerShape(6.dp) else CircleShape)
                .border(2.dp, if (picked || (checked && right)) border else Faint, if (multi) RoundedCornerShape(6.dp) else CircleShape),
            contentAlignment = Alignment.Center,
        ) { Text(mark, color = border, fontSize = 13.sp, fontWeight = FontWeight.Bold) }
        Spacer(Modifier.width(12.dp))
        RichText(text, color = Paper, style = MaterialTheme.typography.bodyMedium)
    }
}

// ---------------------------------------------------------------- terms, tables, figures

@Composable
fun TermCard(deckId: String, t: Term, startOpen: Boolean = false) {
    var open by remember(t.id) { mutableStateOf(startOpen) }
    val p = Store.progress(deckId)
    var starred by remember(t.id) { mutableStateOf("t:${t.id}" in p.starred) }
    InkCard(Modifier.fillMaxWidth().animateContentSize(), onClick = { open = !open }) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(t.term, color = Paper, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                if (t.simple.isNotBlank()) {
                    Spacer(Modifier.height(4.dp))
                    RichText(t.simple, style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text(
                if (starred) "★" else "☆", color = if (starred) Gold else Faint, fontSize = 22.sp,
                modifier = Modifier
                    .clip(CircleShape)
                    .clickable {
                        starred = !starred
                        val id = "t:${t.id}"
                        if (starred) p.starred.add(id) else p.starred.remove(id)
                        Store.progressChanged(deckId)
                    }
                    .padding(start = 10.dp, end = 4.dp),
            )
        }
        if (open) {
            if (t.definition.isNotBlank()) Detail("📖 Book definition", t.definition, Soft)
            if (t.mnemonic.isNotBlank()) Detail("🧠 Memory trick", t.mnemonic, Gold)
            if (t.example.isNotBlank()) Detail("💡 Example", t.example, NotesColor)
        } else if (t.definition.isNotBlank() || t.mnemonic.isNotBlank()) {
            Text("Tap for the definition and memory trick", color = Faint, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        }
    }
}

@Composable
fun Detail(label: String, text: String, color: Color) {
    Spacer(Modifier.height(10.dp))
    Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    RichText(text, color = Soft, style = MaterialTheme.typography.bodyMedium)
}

@Composable
fun TableView(t: TableBlock) {
    InkCard(Modifier.fillMaxWidth()) {
        if (t.title.isNotBlank()) {
            Text(t.title, color = Paper, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(10.dp))
        }
        Column(
            Modifier
                .horizontalScroll(rememberScrollState())
                .clip(RoundedCornerShape(10.dp))
                .border(1.dp, Line, RoundedCornerShape(10.dp)),
        ) {
            val cols = maxOf(t.headers.size, t.rows.maxOfOrNull { it.size } ?: 0)
            val width = if (cols <= 2) 170.dp else 150.dp
            if (t.headers.isNotEmpty()) {
                Row(Modifier.background(SealDeep)) {
                    for (c in 0 until cols) Cell(t.headers.getOrElse(c) { "" }, width, header = true)
                }
            }
            t.rows.forEachIndexed { r, row ->
                Row(Modifier.background(if (r % 2 == 0) Card else CardHigh)) {
                    for (c in 0 until cols) Cell(row.getOrElse(c) { "" }, width, header = false)
                }
            }
        }
        if (t.takeaway.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            RichText("🎯 ${t.takeaway}", color = Gold, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun Cell(text: String, width: androidx.compose.ui.unit.Dp, header: Boolean) {
    Box(Modifier.width(width).padding(10.dp)) {
        Text(
            rich(text), color = if (header) Paper else Soft, fontSize = 13.sp, lineHeight = 18.sp,
            fontWeight = if (header) FontWeight.Bold else FontWeight.Normal,
        )
    }
}

@Composable
fun FigureCard(deckId: String, f: Figure) {
    var zoom by remember { mutableStateOf(false) }
    var extra by remember(f.id) { mutableStateOf(Coach.cached("fig:$deckId:${f.file}").orEmpty()) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    val scope = rememberCoroutineScope()
    val file = Store.figureFile(deckId, f)
    InkCard(Modifier.fillMaxWidth(), padding = androidx.compose.foundation.layout.PaddingValues(0.dp)) {
        LocalImage(
            file,
            Modifier
                .fillMaxWidth()
                .heightIn(min = 120.dp, max = 420.dp)
                .background(Color.White)
                .clickable { zoom = true },
            contentScale = ContentScale.Fit,
        )
        Column(Modifier.padding(16.dp)) {
            if (f.caption.isNotBlank()) Text(f.caption, color = Faint, fontSize = 13.sp, lineHeight = 18.sp)
            if (f.explain.isNotBlank()) {
                Spacer(Modifier.height(8.dp))
                RichText(f.explain, style = MaterialTheme.typography.bodyMedium)
            }
            if (f.labels.isNotEmpty()) {
                Spacer(Modifier.height(10.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    f.labels.forEach { Pill(it, NotesColor) }
                }
            }
            when {
                extra.isNotBlank() -> {
                    Spacer(Modifier.height(10.dp))
                    RichText(extra, style = MaterialTheme.typography.bodyMedium)
                }
                loading -> {
                    Spacer(Modifier.height(10.dp))
                    CircularProgressIndicator(Modifier.size(22.dp), color = NotesColor, strokeWidth = 2.dp)
                }
                f.explain.isBlank() -> {
                    Spacer(Modifier.height(10.dp))
                    GhostButton("🔍 Explain this diagram", {
                        loading = true
                        error = ""
                        scope.launch {
                            try {
                                extra = Coach.figure(deckId, f.file, f.caption)
                            } catch (e: CancellationException) {
                                throw e
                            } catch (e: Exception) {
                                error = e.message ?: "Couldn't explain it right now."
                            }
                            loading = false
                        }
                    }, color = NotesColor)
                }
            }
            if (error.isNotBlank()) Text(error, color = Bad, fontSize = 13.sp)
        }
    }
    if (zoom) ZoomImage(file) { zoom = false }
}

// ---------------------------------------------------------------- explain it differently

/** Buttons that re-explain a topic in another way, plus a box to ask your own question. */
@Composable
fun ExplainPanel(key: String, topic: String, context: String) {
    val scope = rememberCoroutineScope()
    var style by remember(key) { mutableStateOf<Prompts.Style?>(null) }
    var text by remember(key) { mutableStateOf("") }
    var loading by remember(key) { mutableStateOf(false) }
    var question by remember(key) { mutableStateOf("") }

    fun launchAsk(block: suspend () -> String) {
        loading = true
        text = ""
        scope.launch {
            text = try {
                block()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                "⚠️ ${e.message ?: "The AI didn't answer. Try again."}"
            }
            loading = false
        }
    }

    InkCard(Modifier.fillMaxWidth().animateContentSize(), border = LearnColor.copy(alpha = 0.35f)) {
        Text("Still fuzzy? Explain it another way", color = LearnColor, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(10.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Prompts.Style.values().forEach { s ->
                val on = style == s
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) LearnColor else CardHigh)
                        .clickable(enabled = !loading) {
                            style = s
                            launchAsk { Coach.explain("$key:${s.name}", topic, context, s) }
                        }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text("${s.emoji} ${s.label}", color = if (on) Ink else Paper, fontSize = 14.sp) }
            }
        }
        if (loading) {
            Spacer(Modifier.height(14.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                CircularProgressIndicator(Modifier.size(20.dp), color = LearnColor, strokeWidth = 2.dp)
                Spacer(Modifier.width(10.dp))
                Text("Thinking…", color = Faint)
            }
        }
        if (text.isNotBlank()) {
            Spacer(Modifier.height(14.dp))
            RichText(text, color = Paper, style = MaterialTheme.typography.bodyMedium)
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = question,
            onValueChange = { question = it },
            placeholder = { Text("Ask anything about this…") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = {
                val q = question.trim()
                if (q.isNotEmpty() && !loading) {
                    style = null
                    launchAsk { Coach.answer(topic, context, q) }
                }
            }),
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
