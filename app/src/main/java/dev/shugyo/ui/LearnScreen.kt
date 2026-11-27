package dev.shugyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Stats
import dev.shugyo.data.Store

/** The objectives of a set, in order, with what you've finished. */
@Composable
fun LearnScreen(deckId: String) {
    val decks by Store.decks.collectAsState()
    val tick = Store.version.collectAsState().value
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val p = Store.progress(deck.id)
    val learned = deck.objectives.count { it.id in p.learned }
    val next = deck.objectives.indexOfFirst { it.id !in p.learned }

    Screen("Learn", subtitle = deck.title) {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                InkCard(border = LearnColor.copy(alpha = 0.35f)) {
                    Text("$learned of ${deck.objectives.size} objectives learned", color = Paper, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(8.dp))
                    Bar(if (deck.objectives.isEmpty()) 0f else learned.toFloat() / deck.objectives.size, LearnColor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Each objective is a short lesson you tap through, step by step, with its key words and diagrams, then a quick check. Pass it to learn the objective (+25 XP).",
                        color = Faint, fontSize = 13.sp,
                    )
                }
            }
            itemsIndexed(deck.objectives, key = { _, o -> o.id }) { i, o ->
                val done = o.id in p.learned
                InkCard(onClick = { Nav.go(Route.Player(deck.id, i)) }) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (done) Good else CardHigh),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(if (done) "✓" else "${i + 1}", color = if (done) Ink else Paper, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(o.text, color = Paper, maxLines = 3, overflow = TextOverflow.Ellipsis, fontSize = 15.sp)
                            Text(
                                "${deck.termsFor(o).size} terms · ${deck.questionsFor(o).size} questions",
                                color = Faint, fontSize = 12.sp,
                            )
                        }
                    }
                }
            }
        }
        if (next >= 0) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Card)
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                BigButton(
                    if (learned == 0) "Start with objective 1" else "Continue: objective ${next + 1}",
                    { Nav.go(Route.Player(deck.id, next)) },
                    Modifier.fillMaxWidth(), color = LearnColor, textColor = Ink,
                )
            }
        }
    }
}

/** One objective, taught step by step, ending with a quick check. */
@Composable
fun LessonScreen(deckId: String, index: Int) {
    val decks by Store.decks.collectAsState()
    Store.version.collectAsState().value
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val o = deck.objectives.getOrNull(index) ?: return
    val p = Store.progress(deck.id)
    val terms = remember(deck, o) { deck.termsFor(o) }
    val tables = remember(deck, o) { deck.tablesFor(o) }
    val figures = remember(deck, o) { deck.figuresFor(o) }
    val questions = remember(deck, o) { deck.questionsFor(o).sortedBy { it.level }.take(3) }
    val states = remember(o.id) { questions.associate { it.id to QState() } }
    var right by remember(o.id) { mutableIntStateOf(0) }
    var answered by remember(o.id) { mutableIntStateOf(0) }
    val learned = o.id in p.learned
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }

    fun markLearned() {
        if (o.id !in p.learned) {
            p.learned.add(o.id)
            Store.progressChanged(deck.id)
            Stats.award(25)
        }
    }

    Screen("Objective ${index + 1} of ${deck.objectives.size}", subtitle = deck.title) {
        Bar((index + 1f) / deck.objectives.size, LearnColor, height = 4.dp, modifier = Modifier.padding(horizontal = 16.dp))
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InkCard(border = LearnColor.copy(alpha = 0.45f)) {
                    Text("OBJECTIVE", style = Label)
                    Spacer(Modifier.height(6.dp))
                    Text(o.text, color = Paper, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 19.sp, lineHeight = 26.sp)
                    if (learned) {
                        Spacer(Modifier.height(10.dp))
                        Pill("✓ Learned", Good)
                    }
                }
            }
            if (o.simple.isNotBlank()) {
                item {
                    InkCard {
                        Text("🗣 In plain words", color = LearnColor, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        RichText(o.simple)
                    }
                }
            }
            if (o.explain.isNotBlank()) {
                item {
                    InkCard {
                        Text("🧩 Let's break it down", color = LearnColor, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        RichText(o.explain)
                    }
                }
            }
            item {
                ExplainPanel("${deck.id}:${o.id}", o.text, dev.shugyo.ai.Coach.objectiveContext(deck, o))
            }
            if (terms.isNotEmpty()) {
                item { SectionLabel("Key terms (${terms.size})") }
                items(terms.size) { i -> TermCard(deck.id, terms[i]) }
            }
            if (tables.isNotEmpty()) {
                item { SectionLabel("Tables") }
                items(tables.size) { i -> TableView(tables[i]) }
            }
            if (figures.isNotEmpty()) {
                item { SectionLabel("Diagrams") }
                items(figures.size) { i -> FigureCard(deck.id, figures[i]) }
            }
            if (questions.isNotEmpty()) {
                item { SectionLabel("Quick check") }
                items(questions.size) { i ->
                    val q = questions[i]
                    val st = states[q.id] ?: return@items
                    QuestionCard(q, st) { ok ->
                        answered++
                        if (ok) right++
                        if (ok) p.qRight[q.id] = (p.qRight[q.id] ?: 0) + 1 else p.qWrong[q.id] = (p.qWrong[q.id] ?: 0) + 1
                        Stats.award(if (ok) 10 else 3)
                        // Learned when most of the check is right.
                        if (answered == questions.size && right * 3 >= questions.size * 2) markLearned()
                        Store.progressChanged(deck.id)
                    }
                }
                if (answered == questions.size) {
                    item {
                        val passed = right * 3 >= questions.size * 2
                        InkCard(color = if (passed) GoodDeep else CardHigh) {
                            Text(
                                if (passed) "🎉 Objective learned! +25 XP" else "$right of ${questions.size} right. Review the notes above and try again later.",
                                color = if (passed) Good else Soft, fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            } else if (!learned) {
                item {
                    GhostButton("✓ I've got this one", { markLearned() }, Modifier.fillMaxWidth(), color = Good)
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
            GhostButton("← Back", { Nav.replace(Route.Lesson(deck.id, index - 1)) }, Modifier.weight(1f), enabled = index > 0)
            if (index < deck.objectives.size - 1) {
                BigButton("Next objective →", { Nav.replace(Route.Lesson(deck.id, index + 1)) }, Modifier.weight(2f), color = LearnColor, textColor = Ink)
            } else {
                BigButton("Finish", { Nav.back() }, Modifier.weight(2f), color = Good, textColor = Ink)
            }
        }
    }
}
