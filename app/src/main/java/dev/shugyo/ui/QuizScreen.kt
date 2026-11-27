package dev.shugyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Question
import dev.shugyo.data.Stats
import dev.shugyo.data.Store

/** A practice quiz: pick how many questions, answer with hints, go back, and review what you missed. */
@Composable
fun QuizScreen(deckId: String, objectiveId: String, missedOnly: Boolean) {
    val decks by Store.decks.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val p = Store.progress(deck.id)

    var started by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(20) }
    var onlyMissed by remember { mutableStateOf(missedOnly) }
    var round by remember { mutableIntStateOf(0) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }
    val states = remember(round) { HashMap<String, QState>() }
    val results = remember(round) { mutableStateMapOf<String, Boolean>() }
    var index by remember(round) { mutableIntStateOf(0) }
    var finished by remember(round) { mutableStateOf(false) }
    var xp by remember(round) { mutableIntStateOf(0) }

    val pool = deck.questions.filter { objectiveId.isBlank() || it.objective == objectiveId }
    val missedPool = pool.filter { (p.qWrong[it.id] ?: 0) > (p.qRight[it.id] ?: 0) }

    fun start(list: List<Question>) {
        questions = list
        round++
        started = true
    }

    if (!started) {
        Screen("Practice quiz", subtitle = deck.title) {
            Column(
                Modifier
                    .weight(1f)
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                InkCard {
                    Text("✍️", fontSize = 36.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("${pool.size} questions ready", color = Paper, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text(
                        "Multiple choice, select-all-that-apply and true/false, from recall up to real-life scenarios. " +
                            "Use 💡 hints when you're stuck. +10 XP for each right answer.",
                        color = Faint, fontSize = 14.sp,
                    )
                }
                SectionLabel("How many?")
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(10, 20, 40, 0).forEach { n ->
                        val on = count == n
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(50))
                                .background(if (on) QuizColor else CardHigh)
                                .clickable { count = n }
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        ) { Text(if (n == 0) "All" else "$n", color = Paper, fontWeight = FontWeight.SemiBold) }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Only questions I've missed", color = Paper)
                        Text("${missedPool.size} to fix", color = Faint, fontSize = 13.sp)
                    }
                    Switch(
                        checked = onlyMissed, onCheckedChange = { onlyMissed = it }, enabled = missedPool.isNotEmpty(),
                        colors = SwitchDefaults.colors(checkedTrackColor = QuizColor),
                    )
                }
            }
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Card)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                val from = if (onlyMissed && missedPool.isNotEmpty()) missedPool else pool
                BigButton(
                    "Start", {
                        val shuffled = from.shuffled()
                        start(if (count == 0) shuffled else shuffled.take(count))
                    },
                    Modifier.fillMaxWidth(), enabled = from.isNotEmpty(),
                )
            }
        }
        return
    }

    if (finished) {
        QuizResults(questions, results, xp, onRetryMissed = {
            start(questions.filter { results[it.id] == false })
        }, onNew = { started = false })
        return
    }

    val q = questions.getOrNull(index) ?: run {
        finished = true
        return
    }
    val st = states.getOrPut(q.id) { QState() }
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }
    val right = results.values.count { it }

    Screen("Question ${index + 1} of ${questions.size}", subtitle = "$right right · +$xp XP") {
        Bar((index + 1f) / questions.size, QuizColor, height = 4.dp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
        ) {
            item(key = q.id) {
                QuestionCard(q, st) { ok ->
                    results[q.id] = ok
                    if (ok) p.qRight[q.id] = (p.qRight[q.id] ?: 0) + 1 else p.qWrong[q.id] = (p.qWrong[q.id] ?: 0) + 1
                    Store.progressChanged(deck.id)
                    val gained = if (ok) 10 else 3
                    Stats.award(gained)
                    xp += gained
                }
            }
            val o = deck.objective(q.objective)
            if (st.checked && o != null) {
                item {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "From objective: ${o.text}", color = Faint, fontSize = 13.sp,
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { Nav.go(Route.Lesson(deck.id, deck.objectives.indexOf(o))) }
                            .padding(4.dp),
                    )
                }
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GhostButton("← Back", { index-- }, Modifier.weight(1f), enabled = index > 0)
            if (index < questions.size - 1) {
                BigButton(if (st.checked) "Next →" else "Skip →", { index++ }, Modifier.weight(2f))
            } else {
                BigButton("Finish", { finished = true }, Modifier.weight(2f), color = Good, textColor = Ink)
            }
        }
    }
}

@Composable
private fun QuizResults(
    questions: List<Question>,
    results: Map<String, Boolean>,
    xp: Int,
    onRetryMissed: () -> Unit,
    onNew: () -> Unit,
) {
    val right = results.values.count { it }
    val missed = questions.filter { results[it.id] == false }
    val skipped = questions.count { it.id !in results }
    val pct = if (questions.isEmpty()) 0 else right * 100 / questions.size
    Screen("Results") {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(pct / 100f, if (pct >= 80) Good else if (pct >= 60) Gold else Bad, size = 96.dp, stroke = 9.dp) {
                            Text("$pct%", color = Paper, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                        }
                        Column(Modifier.padding(start = 18.dp)) {
                            Text(
                                when {
                                    pct >= 90 -> "Outstanding! 🏆"
                                    pct >= 80 -> "Great work! 🔥"
                                    pct >= 60 -> "Getting there 💪"
                                    else -> "Keep training 🥋"
                                },
                                style = MaterialTheme.typography.titleLarge, color = Paper,
                            )
                            Text("$right of ${questions.size} right · +$xp XP", color = Soft)
                            if (skipped > 0) Text("$skipped skipped", color = Faint, fontSize = 13.sp)
                        }
                    }
                }
            }
            if (missed.isNotEmpty()) {
                item { SectionLabel("Review what you missed") }
                missed.forEach { q ->
                    item(key = "m" + q.id) {
                        InkCard(Modifier.fillMaxWidth()) {
                            RichText(q.prompt, color = Paper, style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.Medium))
                            Spacer(Modifier.height(8.dp))
                            q.answer.forEach { a ->
                                RichText("✓ ${q.choices.getOrElse(a) { "" }}", color = Good, style = MaterialTheme.typography.bodyMedium)
                            }
                            if (q.explain.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                RichText(q.explain, color = Soft, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                }
            }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .navigationBarsPadding()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (missed.isNotEmpty()) BigButton("Retry the ${missed.size} I missed", onRetryMissed, Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("New quiz", onNew, Modifier.weight(1f))
                GhostButton("Done", { Nav.back() }, Modifier.weight(1f))
            }
        }
    }
}
