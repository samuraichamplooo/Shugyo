package dev.shugyo.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Deck
import dev.shugyo.data.Srs
import dev.shugyo.data.Stats
import dev.shugyo.data.Store
import dev.shugyo.game.CreatureView
import dev.shugyo.game.Creatures
import dev.shugyo.game.Species

/** Spaced-repetition flashcards: due cards first, then today's new ones. */
@Composable
fun FlashcardsScreen(deckId: String, all: Boolean, starredOnly: Boolean) {
    val decks by Store.decks.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val p = Store.progress(deck.id)
    var everything by remember { mutableStateOf(all) }
    var definitionFirst by remember { mutableStateOf(false) }
    val queue = remember(everything) {
        mutableStateListOf<String>().apply {
            val ids = when {
                starredOnly -> Srs.cardIds(deck).filter { it in p.starred }
                everything -> Srs.cardIds(deck).shuffled()
                else -> Srs.dueCards(deck, p).shuffled() + Srs.newCards(deck, p).take(Srs.newLeftToday(p))
            }
            addAll(ids)
        }
    }
    var done by remember(everything) { mutableIntStateOf(0) }
    var known by remember(everything) { mutableIntStateOf(0) }
    var xp by remember(everything) { mutableIntStateOf(0) }
    var flipped by remember { mutableStateOf(false) }
    var cheer by remember { mutableStateOf("") }
    var happy by remember { mutableIntStateOf(0) }
    var startle by remember { mutableIntStateOf(0) }
    androidx.compose.runtime.LaunchedEffect(cheer) {
        if (cheer.isNotEmpty()) {
            kotlinx.coroutines.delay(2600)
            cheer = ""
        }
    }
    val total = done + queue.size

    Screen(
        "Wild Encounters", subtitle = deck.title,
        actions = {
            Text(
                if (definitionFirst) "Def → Term" else "Term → Def",
                color = CardsColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .clickable {
                        definitionFirst = !definitionFirst
                        flipped = false
                    }
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            )
        },
    ) {
        if (queue.isEmpty()) {
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                if (done > 0) {
                    Text("🎉", fontSize = 56.sp)
                    Text("Session complete!", style = MaterialTheme.typography.headlineSmall, color = Paper)
                    Spacer(Modifier.height(8.dp))
                    Text("$done cards · $known known · +$xp XP", color = Soft)
                } else {
                    Text("🌸", fontSize = 56.sp)
                    Text("All caught up", style = MaterialTheme.typography.headlineSmall, color = Paper)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        if (deck.terms.isEmpty()) "This set has no key terms yet." else "No cards are due right now. Come back tomorrow, or review everything now.",
                        color = Faint, textAlign = TextAlign.Center,
                    )
                }
                Spacer(Modifier.height(24.dp))
                if (deck.terms.isNotEmpty()) {
                    BigButton("Review all ${Srs.cardIds(deck).size} cards", {
                        everything = true
                        flipped = false
                    }, Modifier.fillMaxWidth(), color = CardsColor, textColor = Ink)
                    Spacer(Modifier.height(10.dp))
                }
                GhostButton("Done", { Nav.back() }, Modifier.fillMaxWidth())
            }
            return@Screen
        }

        val cardId = queue.first()
        Column(Modifier.padding(horizontal = 16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("${done + 1} / $total", color = Faint, fontSize = 13.sp, modifier = Modifier.weight(1f))
                Text("+$xp XP", color = Gold, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(6.dp))
            Bar(done.toFloat() / total.coerceAtLeast(1), CardsColor, height = 6.dp)
        }
        // The creature this card belongs to, and how close you are to befriending it.
        val creature = remember(deck.id, cardId) { creatureFor(deck, cardId) }
        val box = p.cards[cardId]?.box ?: 0
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            CreatureView(creature, Modifier.size(70.dp), faceLeft = true, hitKey = startle, attackKey = happy)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    cheer.ifEmpty { if (box == 0) "A wild ${creature.name} appeared! ${creature.element.emoji}" else "${creature.name} is back! ${creature.element.emoji}" },
                    color = if (cheer.isNotEmpty()) Good else Paper, fontWeight = FontWeight.SemiBold, fontSize = 14.sp,
                )
                Text(
                    "Bond " + "♥".repeat(minOf(box, Srs.MASTERED_BOX)) + "♡".repeat((Srs.MASTERED_BOX - box).coerceAtLeast(0)),
                    color = CardsColor, fontSize = 14.sp,
                )
            }
        }
        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(16.dp),
        ) {
            FlipCard(deck, cardId, flipped, definitionFirst) { flipped = !flipped }
        }
        Column(
            Modifier
                .fillMaxWidth()
                .background(Card)
                .navigationBarsPadding()
                .padding(12.dp),
        ) {
            if (!flipped) {
                BigButton("Show answer", { flipped = true }, Modifier.fillMaxWidth(), color = CardsColor, textColor = Ink)
            } else {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(
                        Triple(Srs.Grade.AGAIN, "Again", Bad),
                        Triple(Srs.Grade.HARD, "Hard", Gold),
                        Triple(Srs.Grade.GOOD, "Good", Good),
                        Triple(Srs.Grade.EASY, "Easy", LearnColor),
                    ).forEach { (g, label, color) ->
                        val next = Srs.grade(p.cards[cardId], g)
                        val days = next.due - Srs.today()
                        GradeButton(label, if (days <= 0) "soon" else "${days}d", color, Modifier.weight(1f)) {
                            val wasNew = cardId !in p.cards
                            val before = p.cards[cardId]?.box ?: 0
                            p.cards[cardId] = next
                            if (cardId.startsWith("t:")) p.seen.add(cardId.removePrefix("t:"))
                            if (g == Srs.Grade.AGAIN) startle++ else happy++
                            if (before < Srs.MASTERED_BOX && next.box >= Srs.MASTERED_BOX) {
                                if (cardId.startsWith("t:")) p.friends.add(cardId.removePrefix("t:"))
                                cheer = "🤝 You befriended ${creature.name}!"
                                Stats.award(10)
                                xp += 10
                            }
                            if (wasNew) Srs.noteNewSeen(p)
                            Store.progressChanged(deck.id)
                            val gained = if (g == Srs.Grade.AGAIN) 1 else if (g == Srs.Grade.HARD) 2 else 3
                            Stats.award(gained)
                            xp += gained
                            queue.removeAt(0)
                            if (g == Srs.Grade.AGAIN) {
                                // See it again in a few cards.
                                queue.add(minOf(3, queue.size), cardId)
                            } else {
                                done++
                                if (g != Srs.Grade.HARD) known++
                            }
                            flipped = false
                        }
                    }
                }
            }
        }
    }
}

/** The creature a flashcard belongs to: a term's own creature, or one from its dojo. */
private fun creatureFor(deck: Deck, cardId: String): Species {
    val id = cardId.substringAfter(':')
    return when {
        cardId.startsWith("t:") -> deck.term(id)?.let { Creatures.forTerm(deck, it) }
        cardId.startsWith("c:") -> deck.card(id)?.let { Creatures.forKey(deck, cardId, it.objective) }
        cardId.startsWith("f:") -> deck.figure(id)?.let { Creatures.forKey(deck, cardId, it.objective) }
        else -> null
    } ?: Creatures.forKey(deck, cardId, "")
}

@Composable
private fun GradeButton(label: String, sub: String, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        modifier = modifier.height(58.dp),
        shape = RoundedCornerShape(14.dp),
        colors = ButtonDefaults.buttonColors(containerColor = color.copy(alpha = 0.18f), contentColor = color),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(4.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(label, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Text(sub, fontSize = 11.sp, color = color.copy(alpha = 0.8f))
        }
    }
}

@Composable
private fun FlipCard(deck: Deck, cardId: String, flipped: Boolean, definitionFirst: Boolean, onTap: () -> Unit) {
    val rotation by animateFloatAsState(if (flipped) 180f else 0f, tween(380), label = "flip")
    val showBack = rotation > 90f
    val shape = RoundedCornerShape(24.dp)
    val p = Store.progress(deck.id)
    var starred by remember(cardId) { mutableStateOf(cardId in p.starred) }
    Box(
        Modifier
            .fillMaxSize()
            .graphicsLayer {
                rotationY = rotation
                cameraDistance = 14f * density
            }
            .clip(shape)
            .background(if (showBack) CardHigh else Card)
            .border(1.5.dp, if (showBack) CardsColor.copy(alpha = 0.5f) else Hairline, shape)
            .clickable(onClick = onTap),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .graphicsLayer { rotationY = if (showBack) 180f else 0f }
                .verticalScroll(rememberScrollState())
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            val term = if (cardId.startsWith("t:")) deck.term(cardId.removePrefix("t:")) else null
            val fig = if (cardId.startsWith("f:")) deck.figures.firstOrNull { it.id == cardId.removePrefix("f:") } else null
            val fact = if (cardId.startsWith("c:")) deck.card(cardId.removePrefix("c:")) else null
            when {
                term != null && !showBack && !definitionFirst -> {
                    Text("TERM", style = Label)
                    Spacer(Modifier.height(12.dp))
                    Text(term.term, color = Paper, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 30.sp, textAlign = TextAlign.Center, lineHeight = 38.sp)
                }
                term != null && !showBack -> {
                    Text("WHAT TERM IS THIS?", style = Label)
                    Spacer(Modifier.height(12.dp))
                    RichText(term.definition.ifBlank { term.simple }, color = Paper, style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center))
                }
                term != null -> {
                    if (definitionFirst) {
                        Text(term.term, color = CardsColor, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 26.sp, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(12.dp))
                    }
                    if (!definitionFirst && term.definition.isNotBlank()) {
                        RichText(term.definition, color = Paper, style = MaterialTheme.typography.bodyLarge.copy(textAlign = TextAlign.Center))
                    }
                    if (term.simple.isNotBlank()) Back("🗣 In plain words", term.simple, LearnColor)
                    if (term.mnemonic.isNotBlank()) Back("🧠 Memory trick", term.mnemonic, Gold)
                    if (term.example.isNotBlank()) Back("💡 Example", term.example, NotesColor)
                }
                fact != null && !showBack -> {
                    Text("QUESTION", style = Label)
                    Spacer(Modifier.height(12.dp))
                    RichText(
                        fact.front, color = Paper,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 22.sp, lineHeight = 31.sp, textAlign = TextAlign.Center),
                    )
                }
                fact != null -> {
                    RichText(fact.front, color = Faint, style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center))
                    Spacer(Modifier.height(14.dp))
                    RichText(
                        fact.back, color = Paper,
                        style = MaterialTheme.typography.bodyLarge.copy(fontSize = 20.sp, lineHeight = 29.sp, textAlign = TextAlign.Center),
                    )
                }
                fig != null && !showBack -> {
                    Text("WHAT DOES THIS SHOW?", style = Label)
                    Spacer(Modifier.height(12.dp))
                    LocalImage(
                        Store.figureFile(deck.id, fig),
                        Modifier
                            .fillMaxWidth()
                            .heightIn(max = 380.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color.White),
                        contentScale = ContentScale.Fit,
                    )
                }
                fig != null -> {
                    if (fig.caption.isNotBlank()) {
                        Text(fig.caption, color = CardsColor, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.Center)
                        Spacer(Modifier.height(10.dp))
                    }
                    RichText(fig.explain, color = Paper, style = MaterialTheme.typography.bodyLarge)
                    if (fig.labels.isNotEmpty()) Back("🏷 Labels", fig.labels.joinToString(" · "), NotesColor)
                }
                else -> Text("This card is missing.", color = Faint)
            }
        }
        Text(
            if (starred) "★" else "☆", color = if (starred) Gold else Faint, fontSize = 24.sp,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .graphicsLayer { rotationY = if (showBack) 180f else 0f }
                .clip(RoundedCornerShape(50))
                .clickable {
                    starred = !starred
                    if (starred) p.starred.add(cardId) else p.starred.remove(cardId)
                    Store.progressChanged(deck.id)
                }
                .padding(14.dp),
        )
    }
}

@Composable
private fun Back(label: String, text: String, color: Color) {
    Spacer(Modifier.height(16.dp))
    Text(label, color = color, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
    Spacer(Modifier.height(4.dp))
    RichText(text, color = Soft, style = MaterialTheme.typography.bodyMedium.copy(textAlign = TextAlign.Center))
}
