package dev.shugyo.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Store

/** The set as a study guide: overview, glossary, tables and diagrams. */
@Composable
fun NotesScreen(deckId: String, startTab: Int) {
    val decks by Store.decks.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    var tab by remember { mutableIntStateOf(startTab) }
    var search by remember { mutableStateOf("") }
    val tabs = listOf("Overview", "Terms ${deck.terms.size}", "Tables ${deck.tables.size}", "Diagrams ${deck.figures.size}")

    Screen("Notes", subtitle = deck.title) {
        Row(
            Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            tabs.forEachIndexed { i, t ->
                val on = tab == i
                Box(
                    Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (on) NotesColor else CardHigh)
                        .clickable { tab = i }
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                ) { Text(t, color = if (on) Ink else Paper, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1) }
            }
        }
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            when (tab) {
                0 -> {
                    if (deck.overview.isNotBlank()) {
                        item {
                            InkCard {
                                Text("Overview", color = NotesColor, fontWeight = FontWeight.SemiBold)
                                Spacer(Modifier.height(6.dp))
                                RichText(deck.overview)
                            }
                        }
                    }
                    items(deck.sections) { s ->
                        InkCard(Modifier.fillMaxWidth()) {
                            Text(s.title, color = Paper, fontFamily = Mincho, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            if (s.summary.isNotBlank()) {
                                Spacer(Modifier.height(6.dp))
                                RichText(s.summary, style = MaterialTheme.typography.bodyMedium)
                            }
                            s.points.forEach { pt ->
                                Spacer(Modifier.height(6.dp))
                                RichText("• $pt", color = Soft, style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }
                    if (deck.objectives.isNotEmpty()) {
                        item { SectionLabel("Learning objectives") }
                        items(deck.objectives) { o ->
                            Text(
                                "• ${o.text}", color = Soft, fontSize = 15.sp,
                                modifier = Modifier
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable { Nav.go(Route.Lesson(deck.id, deck.objectives.indexOf(o))) }
                                    .padding(4.dp),
                            )
                        }
                    }
                }
                1 -> {
                    item {
                        OutlinedTextField(
                            search, { search = it }, singleLine = true,
                            placeholder = { Text("Search terms") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }
                    val q = search.trim().lowercase()
                    val list = deck.terms
                        .filter { q.isEmpty() || it.term.lowercase().contains(q) || it.definition.lowercase().contains(q) }
                        .sortedBy { it.term.lowercase() }
                    items(list, key = { it.id }) { t -> TermCard(deck.id, t) }
                    if (list.isEmpty()) item { Text("No terms match.", color = Faint) }
                }
                2 -> {
                    items(deck.tables, key = { it.id }) { t -> TableView(t) }
                    if (deck.tables.isEmpty()) item { EmptyNote("📊", "No tables", "This reading didn't have any tables.") }
                }
                else -> {
                    items(deck.figures, key = { it.id }) { f -> FigureCard(deck.id, f) }
                    if (deck.figures.isEmpty()) item { EmptyNote("🖼", "No diagrams", "Snap pages with diagrams to add them.") }
                }
            }
        }
    }
}
