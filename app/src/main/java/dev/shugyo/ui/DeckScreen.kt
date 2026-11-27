package dev.shugyo.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Srs
import dev.shugyo.data.Store

/** One study set: how you're doing and the ways to study it. */
@Composable
fun DeckScreen(deckId: String) {
    val decks by Store.decks.collectAsState()
    val tick = Store.version.collectAsState().value
    val deck = decks.firstOrNull { it.id == deckId }
    if (deck == null) {
        Screen("Study set") { EmptyNote("🍃", "This set is gone", "It may have been deleted.") }
        return
    }
    val p = Store.progress(deck.id)
    val mastery = remember(tick, deck) { Srs.mastery(deck, p) }
    val learned = deck.objectives.count { it.id in p.learned }
    val due = remember(tick, deck) { Srs.waitingToday(deck, p) }
    val mastered = remember(tick, deck) { Srs.cardIds(deck).count { (p.cards[it]?.box ?: 0) >= Srs.MASTERED_BOX } }
    val answered = deck.questions.count { (p.qRight[it.id] ?: 0) + (p.qWrong[it.id] ?: 0) > 0 }
    val missed = deck.questions.count { (p.qWrong[it.id] ?: 0) > (p.qRight[it.id] ?: 0) }
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    val run by BuildRunner.state.collectAsState()
    val context = androidx.compose.ui.platform.LocalContext.current
    val cardCount = Srs.cardIds(deck).size
    androidx.compose.runtime.LaunchedEffect(run.doneDeck) {
        if (run.upgrade && run.doneDeck == deck.id) BuildRunner.clear()
    }
    var confirm by remember { mutableStateOf("") }

    Screen(
        deck.title,
        subtitle = deck.subject,
        actions = {
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More", tint = Paper) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Rename") }, onClick = {
                        menu = false
                        renaming = true
                    })
                    DropdownMenuItem(text = { Text("✨ Add lessons & more cards") }, onClick = {
                        menu = false
                        BuildRunner.upgrade(context, deck.id)
                    })
                    DropdownMenuItem(text = { Text("Reset progress") }, onClick = {
                        menu = false
                        confirm = "reset"
                    })
                    DropdownMenuItem(text = { Text("Delete set", color = Bad) }, onClick = {
                        menu = false
                        confirm = "delete"
                    })
                }
            }
        },
    ) {
        LazyColumn(
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InkCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Ring(mastery, if (mastery >= 0.8f) Good else Seal, size = 96.dp, stroke = 9.dp) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("${(mastery * 100).toInt()}%", color = Paper, fontWeight = FontWeight.Bold, fontSize = 24.sp)
                                Text("mastery", color = Faint, fontSize = 11.sp)
                            }
                        }
                        Spacer(Modifier.width(18.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            StatLine("📘", "$learned / ${deck.objectives.size} objectives learned")
                            StatLine("🃏", "$mastered / ${Srs.cardIds(deck).size} cards mastered")
                            StatLine("✍️", "$answered / ${deck.questions.size} questions tried")
                        }
                    }
                    if (deck.overview.isNotBlank()) {
                        Spacer(Modifier.height(14.dp))
                        RichText(deck.overview, style = androidx.compose.material3.MaterialTheme.typography.bodyMedium)
                    }
                }
            }
            if (run.running && run.upgrade) {
                item {
                    InkCard(border = Gold.copy(alpha = 0.4f)) {
                        Text("✨ Adding lessons and cards… ${(run.fraction * 100).toInt()}%", color = Paper, fontWeight = FontWeight.SemiBold)
                        Text(run.status, color = Faint, fontSize = 13.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                        Spacer(Modifier.height(8.dp))
                        Bar(run.fraction, Gold)
                        Spacer(Modifier.height(6.dp))
                        Text("You can leave the app; it keeps going.", color = Faint, fontSize = 12.sp)
                    }
                }
            } else if (run.upgrade && run.error.isNotEmpty()) {
                item {
                    InkCard(color = BadDeep, border = Bad.copy(alpha = 0.4f)) {
                        Text("The upgrade didn't finish", color = Bad, fontWeight = FontWeight.Bold)
                        Text(run.error, color = Soft, fontSize = 14.sp)
                        Spacer(Modifier.height(8.dp))
                        GhostButton("Try again", { BuildRunner.clear(); BuildRunner.upgrade(context, deck.id) })
                    }
                }
            } else if (!deck.hasLessons || deck.cards.isEmpty()) {
                item {
                    InkCard(border = Gold.copy(alpha = 0.45f)) {
                        Text("✨ Make this set complete", color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Adds a step-by-step lesson for every objective, 5–10 extra flashcards each, and more practice " +
                                "questions where there are few. Takes a few minutes and keeps going in the background.",
                            color = Soft, fontSize = 14.sp,
                        )
                        Spacer(Modifier.height(10.dp))
                        BigButton("Upgrade this set", { BuildRunner.upgrade(context, deck.id) }, Modifier.fillMaxWidth(), color = Gold, textColor = Ink)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeTile(
                        "📘", "Learn", "${deck.objectives.size} step-by-step lessons", LearnColor,
                        badge = if (learned < deck.objectives.size) "${deck.objectives.size - learned} to go" else "done ✓",
                        modifier = Modifier.weight(1f),
                    ) { Nav.go(Route.Learn(deck.id)) }
                    ModeTile(
                        "🃏", "Flashcards", "$cardCount cards: terms, facts & diagrams", CardsColor,
                        badge = if (due > 0) "$due due" else "all caught up",
                        modifier = Modifier.weight(1f),
                    ) { Nav.go(Route.Cards(deck.id)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeTile(
                        "✍️", "Practice quiz", "${deck.questions.size} questions with hints", QuizColor,
                        badge = if (missed > 0) "$missed to fix" else "",
                        modifier = Modifier.weight(1f),
                    ) { Nav.go(Route.Quiz(deck.id)) }
                    ModeTile(
                        "📚", "Notes", "Terms, tables & diagrams", NotesColor,
                        badge = "${deck.figures.size} diagrams",
                        modifier = Modifier.weight(1f),
                    ) { Nav.go(Route.Notes(deck.id)) }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    ModeTile(
                        "⚔️", "Battle Dojo", "Turn-based battles with your study creatures", BattleColor,
                        badge = "coming next", locked = true, modifier = Modifier.weight(1f),
                    ) {}
                    ModeTile(
                        "🏯", "Exam", "Timed mock exams across chapters", Color(0xFF9AA4B2),
                        badge = "coming next", locked = true, modifier = Modifier.weight(1f),
                    ) {}
                }
            }
            if (deck.source.startsWith("http")) {
                item {
                    Text(
                        "From ${deck.source}", color = Faint, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.navigationBarsPadding(),
                    )
                }
            }
        }
    }

    if (renaming) {
        var t by remember { mutableStateOf(deck.title) }
        var s by remember { mutableStateOf(deck.subject) }
        AlertDialog(
            onDismissRequest = { renaming = false },
            containerColor = CardHigh,
            title = { Text("Rename") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(t, { t = it }, label = { Text("Title") }, singleLine = true)
                    OutlinedTextField(s, { s = it }, label = { Text("Class") }, singleLine = true)
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    Store.rename(deck.id, t, s)
                    renaming = false
                }) { Text("Save", color = Gold) }
            },
            dismissButton = { TextButton(onClick = { renaming = false }) { Text("Cancel", color = Faint) } },
        )
    }
    if (confirm.isNotEmpty()) {
        val deleting = confirm == "delete"
        AlertDialog(
            onDismissRequest = { confirm = "" },
            containerColor = CardHigh,
            title = { Text(if (deleting) "Delete this set?" else "Reset progress?") },
            text = {
                Text(
                    if (deleting) "\"${deck.title}\" and its diagrams will be removed from your phone."
                    else "Cards, objectives and quiz scores for this set start over. Your XP stays.",
                    color = Soft,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirm = ""
                    if (deleting) {
                        Nav.back()
                        Store.delete(deck.id)
                    } else Store.resetProgress(deck.id)
                }) { Text(if (deleting) "Delete" else "Reset", color = Bad) }
            },
            dismissButton = { TextButton(onClick = { confirm = "" }) { Text("Cancel", color = Faint) } },
        )
    }
}

@Composable
private fun StatLine(emoji: String, text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(emoji, fontSize = 14.sp)
        Spacer(Modifier.width(8.dp))
        Text(text, color = Soft, fontSize = 14.sp)
    }
}

@Composable
private fun ModeTile(
    emoji: String,
    title: String,
    body: String,
    color: Color,
    badge: String,
    modifier: Modifier = Modifier,
    locked: Boolean = false,
    onClick: () -> Unit,
) {
    InkCard(
        modifier = modifier.heightIn(min = 150.dp),
        color = if (locked) Card.copy(alpha = 0.6f) else Card,
        border = color.copy(alpha = if (locked) 0.15f else 0.35f),
        onClick = if (locked) null else onClick,
    ) {
        Text(emoji, fontSize = 28.sp)
        Spacer(Modifier.height(8.dp))
        Text(title, color = if (locked) Faint else Paper, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Text(body, color = Faint, fontSize = 13.sp, lineHeight = 17.sp, modifier = Modifier.padding(top = 2.dp))
        if (badge.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Pill(badge, color)
        }
    }
}
