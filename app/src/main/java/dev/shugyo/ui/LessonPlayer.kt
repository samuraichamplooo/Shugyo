package dev.shugyo.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.ai.Coach
import dev.shugyo.data.Beat
import dev.shugyo.data.Deck
import dev.shugyo.data.Objective
import dev.shugyo.data.Stats
import dev.shugyo.data.Store
import dev.shugyo.data.Term

/** What one screen of a lesson shows. */
private sealed interface Page {
    data object Intro : Page
    data class Step(val i: Int) : Page
    data object Words : Page
    data class Check(val i: Int) : Page
    data object Done : Page
}

/**
 * One objective taught as a short, tap-through lesson: what it's about, a few small steps that build the
 * idea, the key words, a quick check, and a recap. Then on to the next objective.
 */
@Composable
fun LessonPlayer(deckId: String, index: Int) {
    val decks by Store.decks.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val o = deck.objectives.getOrNull(index) ?: return
    val p = Store.progress(deck.id)
    val beats = remember(deck, o) {
        o.lesson.ifEmpty {
            // Sets built before lessons existed: teach from the notes they have.
            listOfNotNull(
                o.explain.takeIf { it.isNotBlank() }?.let { Beat("Let's break it down", it, o.terms) },
            ) + deck.termsFor(o).take(6).map { t ->
                Beat(t.term, listOf(t.simple, t.example.takeIf { it.isNotBlank() }?.let { "For example: $it" })
                    .filterNotNull().joinToString(" ").ifBlank { t.definition }, listOf(t.id))
            }
        }
    }
    val terms = remember(deck, o) { deck.termsFor(o) }
    val questions = remember(deck, o) { deck.questionsFor(o).sortedBy { it.level }.take(3) }
    val pages = remember(deck, o) {
        buildList<Page> {
            add(Page.Intro)
            beats.indices.forEach { add(Page.Step(it)) }
            if (terms.isNotEmpty()) add(Page.Words)
            questions.indices.forEach { add(Page.Check(it)) }
            add(Page.Done)
        }
    }
    val states = remember(o.id) { questions.associate { it.id to QState() } }
    var page by remember(o.id) { mutableIntStateOf(0) }
    var right by remember(o.id) { mutableIntStateOf(0) }
    var answered by remember(o.id) { mutableIntStateOf(0) }
    var explaining by remember(o.id) { mutableStateOf<Beat?>(null) }
    val current = pages[page.coerceIn(0, pages.size - 1)]
    val passed = questions.isEmpty() || right * 3 >= questions.size * 2

    fun finishObjective() {
        if (passed && o.id !in p.learned) {
            p.learned.add(o.id)
            Store.progressChanged(deck.id)
            Stats.award(25)
        }
    }

    Screen(
        "Objective ${index + 1} of ${deck.objectives.size}",
        subtitle = deck.title,
        actions = {
            TextButton(onClick = { Nav.go(Route.Lesson(deck.id, index)) }) { Text("Notes", color = LearnColor) }
        },
    ) {
        // One segment per screen of the lesson.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            pages.indices.forEach { i ->
                Box(
                    Modifier
                        .weight(1f)
                        .height(5.dp)
                        .clip(RoundedCornerShape(50))
                        .background(if (i <= page) LearnColor else CardHigh),
                )
            }
        }
        AnimatedContent(
            targetState = page,
            transitionSpec = {
                if (targetState > initialState) {
                    slideInHorizontally { it / 3 } togetherWith slideOutHorizontally { -it / 3 }
                } else {
                    slideInHorizontally { -it / 3 } togetherWith slideOutHorizontally { it / 3 }
                }
            },
            modifier = Modifier.weight(1f),
            label = "lesson",
        ) { shown ->
            Column(
                Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                when (val pg = pages[shown.coerceIn(0, pages.size - 1)]) {
                    Page.Intro -> Intro(o, beats.size, questions.size)
                    is Page.Step -> StepCard(deck, beats[pg.i], pg.i, beats.size) { explaining = beats[pg.i] }
                    Page.Words -> {
                        Text("Key words for this one", style = MaterialTheme.typography.titleLarge, color = Paper)
                        Text("Tap a word for its definition and memory trick.", color = Faint, fontSize = 14.sp)
                        terms.forEach { TermCard(deck.id, it) }
                    }
                    is Page.Check -> {
                        Text("Quick check ${pg.i + 1} of ${questions.size}", style = Label)
                        val q = questions[pg.i]
                        QuestionCard(q, states.getValue(q.id)) { ok ->
                            answered++
                            if (ok) right++
                            if (ok) p.qRight[q.id] = (p.qRight[q.id] ?: 0) + 1 else p.qWrong[q.id] = (p.qWrong[q.id] ?: 0) + 1
                            Store.progressChanged(deck.id)
                            Stats.award(if (ok) 10 else 3)
                        }
                    }
                    Page.Done -> DoneCard(o, passed, right, questions.size)
                }
            }
        }

        // Back and next.
        val isCheck = current is Page.Check
        val checked = (current as? Page.Check)?.let { states.getValue(questions[it.i].id).checked } ?: true
        Row(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .navigationBarsPadding()
                .padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            GhostButton(
                "←", {
                    if (page > 0) page-- else if (index > 0) Nav.replace(Route.Player(deck.id, index - 1))
                },
                Modifier.weight(1f), enabled = page > 0 || index > 0,
            )
            when {
                current == Page.Done && index < deck.objectives.size - 1 -> BigButton(
                    "Next objective →", {
                        finishObjective()
                        Nav.replace(Route.Player(deck.id, index + 1))
                    },
                    Modifier.weight(3f), color = LearnColor, textColor = Ink,
                )
                current == Page.Done -> BigButton(
                    "Finish 🎉", {
                        finishObjective()
                        Nav.back()
                    },
                    Modifier.weight(3f), color = Good, textColor = Ink,
                )
                else -> BigButton(
                    when {
                        isCheck && !checked -> "Answer to continue"
                        current == Page.Intro -> "Let's go →"
                        else -> "Next →"
                    },
                    { page++ },
                    Modifier.weight(3f), color = LearnColor, textColor = Ink, enabled = checked,
                )
            }
        }
    }

    explaining?.let { beat ->
        ModalBottomSheet(onDismissRequest = { explaining = null }, containerColor = Card) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .heightIn(max = 640.dp)
                    .verticalScroll(rememberScrollState())
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Text(beat.title, style = MaterialTheme.typography.titleLarge, color = Paper)
                Spacer(Modifier.height(10.dp))
                ExplainPanel(
                    "${deck.id}:${o.id}:${beat.title}",
                    beat.title,
                    "Objective: ${o.text}\n\n${beat.text}\n\n" + Coach.objectiveContext(deck, o),
                )
            }
        }
    }
}

@Composable
private fun Intro(o: Objective, steps: Int, checks: Int) {
    InkCard(Modifier.fillMaxWidth(), border = LearnColor.copy(alpha = 0.45f)) {
        Text("OBJECTIVE", style = Label)
        Spacer(Modifier.height(6.dp))
        Text(o.text, color = Paper, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 21.sp, lineHeight = 29.sp)
    }
    if (o.simple.isNotBlank()) {
        InkCard(Modifier.fillMaxWidth()) {
            Text("🗣 What it's really asking", color = LearnColor, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            RichText(o.simple)
        }
    }
    Text(
        "$steps short steps" + (if (checks > 0) ", then a $checks-question check" else "") + ". Take your time with each one.",
        color = Faint, fontSize = 14.sp,
    )
}

@Composable
private fun StepCard(deck: Deck, beat: Beat, i: Int, of: Int, onExplain: () -> Unit) {
    var zoom by remember(beat) { mutableStateOf(false) }
    var openTerm by remember(beat) { mutableStateOf<Term?>(null) }
    Text("STEP ${i + 1} OF $of", style = Label)
    Text(beat.title, color = Paper, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 24.sp, lineHeight = 31.sp)
    RichText(beat.text, color = Paper, style = MaterialTheme.typography.bodyLarge.copy(fontSize = 19.sp, lineHeight = 29.sp))
    val fig = deck.figure(beat.figure)
    if (fig != null) {
        val file = Store.figureFile(deck.id, fig)
        LocalImage(
            file,
            Modifier
                .fillMaxWidth()
                .heightIn(max = 340.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color.White)
                .clickable { zoom = true },
            contentScale = ContentScale.Fit,
        )
        if (fig.caption.isNotBlank()) Text(fig.caption, color = Faint, fontSize = 13.sp)
        if (zoom) ZoomImage(file) { zoom = false }
    }
    val terms = beat.terms.mapNotNull { deck.term(it) }
    if (terms.isNotEmpty()) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            terms.forEach { t ->
                val on = openTerm?.id == t.id
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) Gold else Gold.copy(alpha = 0.15f))
                        .clickable { openTerm = if (on) null else t }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                ) { Text(t.term, color = if (on) Ink else Gold, fontSize = 14.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        openTerm?.let { t ->
            InkCard(Modifier.fillMaxWidth(), color = CardHigh) {
                Text(t.term, color = Paper, fontWeight = FontWeight.Bold)
                if (t.definition.isNotBlank()) Detail("📖 Book definition", t.definition, Soft)
                if (t.simple.isNotBlank()) Detail("🗣 In plain words", t.simple, LearnColor)
                if (t.mnemonic.isNotBlank()) Detail("🧠 Memory trick", t.mnemonic, Gold)
            }
        }
    }
    Spacer(Modifier.height(4.dp))
    GhostButton("🤔 Still fuzzy? Explain it another way", onExplain, Modifier.fillMaxWidth(), color = LearnColor)
}

@Composable
private fun DoneCard(o: Objective, passed: Boolean, right: Int, total: Int) {
    Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Spacer(Modifier.height(12.dp))
        Text(if (passed) "🎉" else "💪", fontSize = 56.sp)
        Text(
            if (passed) "Objective learned!" else "Almost there",
            style = MaterialTheme.typography.headlineSmall, color = Paper,
        )
        if (total > 0) {
            Text("$right of $total right" + if (passed) " · +25 XP" else "", color = if (passed) Good else Soft)
        }
    }
    if (o.recap.isNotBlank() || o.simple.isNotBlank()) {
        InkCard(Modifier.fillMaxWidth(), color = if (passed) GoodDeep else CardHigh) {
            Text("🎯 Remember this", color = Gold, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            RichText(o.recap.ifBlank { o.simple }, color = Paper)
        }
    }
    if (!passed) {
        Text(
            "Go back through the steps (←) or open Notes, then try the check again later. It comes back in your practice quiz too.",
            color = Faint, fontSize = 14.sp, textAlign = TextAlign.Center,
        )
    }
    Spacer(Modifier.width(1.dp))
}
