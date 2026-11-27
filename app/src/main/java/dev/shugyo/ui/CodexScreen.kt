package dev.shugyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Store
import dev.shugyo.data.Term
import dev.shugyo.game.CreatureView
import dev.shugyo.game.Creatures

/** Every key term in the set as a creature to collect. Befriend them all and you know every term. */
@Composable
fun CodexScreen(deckId: String) {
    val decks by Store.decks.collectAsState()
    Store.version.collectAsState().value
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val total = deck.terms.size
    val friends = deck.terms.count { Creatures.befriended(deck, it) }
    var open by remember { mutableStateOf<Pair<Int, Term>?>(null) }

    Screen("Codex", subtitle = deck.title) {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(104.dp),
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(span = { GridItemSpan(maxLineSpan) }) {
                InkCard(Modifier.fillMaxWidth(), border = NotesColor.copy(alpha = 0.4f)) {
                    Text("$friends of $total befriended", color = Paper, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                    Spacer(Modifier.height(8.dp))
                    Bar(if (total == 0) 0f else friends.toFloat() / total, NotesColor)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Every key term is a creature. Meet them in Wild Encounters and Battle Dojo; master a card or win a " +
                            "battle to befriend it. A full Codex means you know every term in this reading.",
                        color = Faint, fontSize = 13.sp,
                    )
                }
            }
            itemsIndexed(deck.terms, key = { _, t -> t.id }) { i, t ->
                val sp = remember(deck.id, t.id) { Creatures.forTerm(deck, t) }
                val seen = Creatures.seen(deck, t)
                val friend = Creatures.befriended(deck, t)
                Column(
                    Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(if (friend) CardHigh else Card)
                        .clickable { open = i to t }
                        .padding(8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("#%03d".format(i + 1), color = Faint, fontSize = 11.sp)
                    CreatureView(sp, Modifier.size(78.dp), silhouette = !seen, bob = friend)
                    Text(
                        if (seen) sp.name else "???", color = if (friend) Paper else Soft, fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        if (friend) "${sp.element.emoji} friend" else if (seen) "met" else "not met",
                        color = if (friend) Good else Faint, fontSize = 11.sp,
                    )
                }
            }
        }
    }

    open?.let { (i, t) ->
        val sp = Creatures.forTerm(deck, t)
        val seen = Creatures.seen(deck, t)
        val friend = Creatures.befriended(deck, t)
        AlertDialog(
            onDismissRequest = { open = null },
            containerColor = CardHigh,
            confirmButton = { TextButton(onClick = { open = null }) { Text("Close", color = Gold) } },
            text = {
                Column(
                    Modifier
                        .heightIn(max = 520.dp)
                        .verticalScroll(rememberScrollState()),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box(contentAlignment = Alignment.Center) { CreatureView(sp, Modifier.size(140.dp), silhouette = !seen) }
                    Text(
                        "#%03d ".format(i + 1) + (if (seen) sp.name else "???"),
                        color = Paper, fontWeight = FontWeight.Bold, fontSize = 20.sp,
                    )
                    Text("${sp.element.emoji} ${sp.element.label} type", color = sp.element.main, fontSize = 13.sp)
                    Spacer(Modifier.height(12.dp))
                    if (!seen) {
                        Text(
                            "You haven't met this one yet. It's waiting in Wild Encounters and Battle Dojo.",
                            color = Soft, textAlign = TextAlign.Center,
                        )
                    } else {
                        Column(Modifier.fillMaxWidth()) {
                            Text(t.term, color = Gold, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            if (t.definition.isNotBlank()) Detail("📖 Book definition", t.definition, Soft)
                            if (t.simple.isNotBlank()) Detail("🗣 In plain words", t.simple, LearnColor)
                            if (t.mnemonic.isNotBlank()) Detail("🧠 Memory trick", t.mnemonic, Gold)
                            if (t.example.isNotBlank()) Detail("💡 Example", t.example, NotesColor)
                            Spacer(Modifier.height(10.dp))
                            Text(
                                if (friend) "🤝 Befriended" else "Not befriended yet: master its flashcard or win a battle against it.",
                                color = if (friend) Good else Faint, fontSize = 13.sp,
                            )
                        }
                    }
                }
            },
        )
    }
}
