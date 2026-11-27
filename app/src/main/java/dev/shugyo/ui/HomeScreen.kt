package dev.shugyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Deck
import dev.shugyo.data.Prefs
import dev.shugyo.data.Srs
import dev.shugyo.data.Stats
import dev.shugyo.data.Store

@Composable
fun HomeScreen(loaded: Boolean) {
    val decks by Store.decks.collectAsState()
    val tick = Store.version.collectAsState().value
    val settings by Prefs.settings.collectAsState()
    val run by BuildRunner.state.collectAsState()
    var adding by remember { mutableStateOf(false) }
    val importers = rememberImporters(onAdded = { Nav.go(Route.Build) })

    Box(
        Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        LazyColumn(
            Modifier
                .fillMaxSize()
                .statusBarsPadding(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 120.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("修行", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 34.sp, color = Seal)
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Shugyō", fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 22.sp, color = Paper)
                        Text("your training journey", color = Faint, fontSize = 13.sp)
                    }
                    IconButton(onClick = { Nav.go(Route.Settings) }) { Icon(Icons.Filled.Settings, "Settings", tint = Soft) }
                }
            }
            item { TrainerCard(settings.xp, settings.dailyGoal) }

            if (run.running) {
                item {
                    InkCard(onClick = { if (run.upgrade) Nav.go(Route.DeckHome(run.upgradeDeck)) else Nav.go(Route.Build) }, border = Gold.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.padding(end = 14.dp), color = Gold)
                            Column {
                                Text(
                                    (if (run.upgrade) "Upgrading a study set… " else "Building your study set… ") + "${(run.fraction * 100).toInt()}%",
                                    color = Paper, fontWeight = FontWeight.SemiBold,
                                )
                                Text(run.status, color = Faint, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }
                }
            }
            if (run.doneDeck.isNotEmpty()) {
                item {
                    InkCard(
                        onClick = {
                            val id = run.doneDeck
                            val upgraded = run.upgrade
                            BuildRunner.clear()
                            if (!upgraded) dev.shugyo.scan.Basket.clear()
                            Nav.go(Route.DeckHome(id))
                        },
                        color = GoodDeep, border = Good.copy(alpha = 0.4f),
                    ) {
                        Text(
                            if (run.upgrade) "✨ Your study set has its lessons and new cards" else "✨ Your new study set is ready",
                            color = Good, fontWeight = FontWeight.Bold,
                        )
                        Text("Tap to open it.", color = Soft, fontSize = 14.sp)
                    }
                }
            }
            if (run.error.isNotEmpty()) {
                item {
                    InkCard(onClick = { if (!run.upgrade) Nav.go(Route.Build) else BuildRunner.clear() }, color = BadDeep, border = Bad.copy(alpha = 0.4f)) {
                        Text(if (run.upgrade) "The upgrade didn't finish" else "The last build didn't finish", color = Bad, fontWeight = FontWeight.Bold)
                        Text(run.error, color = Soft, fontSize = 14.sp, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    }
                }
            }

            if (loaded && decks.isEmpty()) {
                item {
                    EmptyNote(
                        "🏯", "Begin your training",
                        "Scan a chapter of your textbook and Shugyō turns it into lessons, flashcards and practice quizzes. " +
                            "Tap ＋ Scan a chapter below to start.",
                    )
                }
            }

            val groups = decks.groupBy { it.subject.ifBlank { "Other" } }
            groups.forEach { (subject, list) ->
                item(key = "h:$subject") { SectionLabel(subject) }
                items(list, key = { it.id }) { d -> DeckRow(d, tick) }
            }
        }

        Box(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(16.dp),
        ) {
            BigButton("＋ Scan a chapter", { adding = true }, Modifier.fillMaxWidth())
        }
    }

    if (adding) {
        ModalBottomSheet(onDismissRequest = { adding = false }, containerColor = Card) {
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("Add pages", style = androidx.compose.material3.MaterialTheme.typography.titleLarge, color = Paper)
                AddOption("🌐", "Open my textbook", "Your class site (like Moodle) or McGraw Hill: sign in, then scan each page") {
                    adding = false
                    Nav.go(Route.Browser)
                }
                AddOption("🖼", "Screenshots or photos", "Pictures of pages, slides or whiteboards") {
                    adding = false
                    importers.photos()
                }
                AddOption("📄", "A PDF", "Pick the chapter's pages") {
                    adding = false
                    importers.pdf()
                }
                AddOption("📋", "Paste text", "From your notes or a document") {
                    adding = false
                    importers.paste()
                }
                Spacer(Modifier.height(8.dp))
            }
        }
    }
}

@Composable
private fun AddOption(emoji: String, title: String, body: String, onClick: () -> Unit) {
    InkCard(onClick = onClick, color = CardHigh) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(emoji, fontSize = 26.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(title, color = Paper, fontWeight = FontWeight.SemiBold)
                Text(body, color = Faint, fontSize = 13.sp)
            }
        }
    }
}

@Composable
private fun TrainerCard(xp: Int, goal: Int) {
    val level = Stats.level(xp)
    val today = Stats.xpToday()
    val streak = Stats.streakNow()
    if (!dev.shugyo.game.Creatures.hasPartner()) {
        InkCard(onClick = { Nav.go(Route.Partner) }, border = BattleColor.copy(alpha = 0.5f)) {
            Text("Choose your partner", color = Paper, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            Text("A study creature that trains, battles and evolves with you.", color = Faint, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(
                Modifier.horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                dev.shugyo.game.Creatures.starters.forEach {
                    dev.shugyo.game.CreatureView(it.species, Modifier.size(72.dp))
                }
            }
        }
        return
    }
    val (partner, stage) = dev.shugyo.game.Creatures.partner()
    val evolveAt = dev.shugyo.game.Creatures.nextEvolution(level)
    InkCard {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(contentAlignment = Alignment.BottomEnd) {
                dev.shugyo.game.CreatureView(partner, Modifier.size(96.dp), stage = stage)
                Ring(today.toFloat() / goal.coerceAtLeast(1), Gold, size = 34.dp, stroke = 4.dp) {
                    Text("$today", color = Paper, fontWeight = FontWeight.Bold, fontSize = 9.sp)
                }
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(partner.name, color = Paper, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Lv $level · ${partner.element.emoji} ${dev.shugyo.game.Creatures.partnerStageName()}", color = Soft, fontSize = 13.sp)
                    Spacer(Modifier.width(6.dp))
                    Pill(Stats.rank(level), Gold)
                }
                Spacer(Modifier.height(8.dp))
                Bar(Stats.levelProgress(xp), Gold)
                Spacer(Modifier.height(6.dp))
                Text(
                    "${Stats.xpFor(level + 1) - xp} XP to Lv ${level + 1}" +
                        (if (evolveAt > 0) " · evolves at Lv $evolveAt" else " · fully evolved"),
                    color = Faint, fontSize = 12.sp,
                )
                Text(
                    (if (streak > 0) "🔥 $streak-day streak" else "Start a streak today") + " · today $today/$goal XP",
                    color = if (streak > 0) Gold else Faint, fontSize = 12.sp,
                )
            }
        }
    }
}

@Composable
private fun DeckRow(d: Deck, @Suppress("UNUSED_PARAMETER") tick: Int) {
    val p = Store.progress(d.id)
    val mastery = Srs.mastery(d, p)
    val waiting = Srs.waitingToday(d, p)
    InkCard(onClick = { Nav.go(Route.DeckHome(d.id)) }) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Ring(mastery, if (mastery >= 0.8f) Good else Seal, size = 54.dp, stroke = 5.dp) {
                Text("${(mastery * 100).toInt()}%", color = Paper, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(d.title, color = Paper, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(
                    "${d.objectives.size} objectives · ${d.terms.size} terms · ${d.questions.size} questions",
                    color = Faint, fontSize = 12.sp,
                )
            }
            if (waiting > 0) Pill("$waiting due", Gold)
        }
    }
}
