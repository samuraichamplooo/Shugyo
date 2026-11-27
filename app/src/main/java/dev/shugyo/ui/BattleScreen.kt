package dev.shugyo.ui

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.data.Prefs
import dev.shugyo.data.Question
import dev.shugyo.data.Stats
import dev.shugyo.data.Store
import dev.shugyo.game.CreatureView
import dev.shugyo.game.Creatures
import dev.shugyo.game.Species

/**
 * Battle Dojo: a practice quiz as a turn-based battle. A right answer is your partner's attack (harder
 * questions hit harder, streaks build combos); a wrong one lets the wild creature strike back. Bring a
 * creature to 0 HP and you befriend it.
 */
@Composable
fun BattleScreen(deckId: String, objectiveId: String = "") {
    val decks by Store.decks.collectAsState()
    val settings by Prefs.settings.collectAsState()
    val deck = decks.firstOrNull { it.id == deckId } ?: return
    val p = Store.progress(deck.id)

    var started by remember { mutableStateOf(false) }
    var count by remember { mutableIntStateOf(20) }
    var onlyMissed by remember { mutableStateOf(false) }
    var round by remember { mutableIntStateOf(0) }
    var questions by remember { mutableStateOf<List<Question>>(emptyList()) }

    val pool = deck.questions.filter { objectiveId.isBlank() || it.objective == objectiveId }
    val missedPool = pool.filter { (p.qWrong[it.id] ?: 0) > (p.qRight[it.id] ?: 0) }

    if (!Creatures.hasPartner()) {
        Screen("Battle Dojo", subtitle = deck.title) {
            StarterPicker(Modifier.weight(1f))
        }
        return
    }
    val (partner, stage) = Creatures.partner()
    @Suppress("UNUSED_VARIABLE") val xpNow = settings.xp

    if (!started) {
        Screen("Battle Dojo", subtitle = deck.title) {
            LazyColumn(
                Modifier.weight(1f),
                contentPadding = PaddingValues(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    InkCard(Modifier.fillMaxWidth(), border = BattleColor.copy(alpha = 0.4f)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            CreatureView(partner, Modifier.size(110.dp), stage = stage)
                            Spacer(Modifier.width(8.dp))
                            Column(Modifier.weight(1f)) {
                                Text("${partner.name} is ready!", color = Paper, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                                Text(
                                    "Each question is a turn. Right answers attack (harder questions land critical hits, " +
                                        "streaks build combos). Wrong answers let the wild creature strike back. " +
                                        "Bring one to 0 HP to befriend it.",
                                    color = Faint, fontSize = 13.sp,
                                )
                            }
                        }
                    }
                }
                item { SectionLabel("How many questions?") }
                item {
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(10, 20, 40, 0).forEach { n ->
                            val on = count == n
                            Box(
                                Modifier
                                    .clip(RoundedCornerShape(50))
                                    .background(if (on) BattleColor else CardHigh)
                                    .clickable { count = n }
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                            ) { Text(if (n == 0) "All ${pool.size}" else "$n", color = if (on) Ink else Paper, fontWeight = FontWeight.SemiBold) }
                        }
                    }
                }
                item {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Rematch: only questions I've missed", color = Paper)
                            Text("${missedPool.size} to fix", color = Faint, fontSize = 13.sp)
                        }
                        Switch(
                            checked = onlyMissed, onCheckedChange = { onlyMissed = it }, enabled = missedPool.isNotEmpty(),
                            colors = SwitchDefaults.colors(checkedTrackColor = BattleColor),
                        )
                    }
                }
                item {
                    TextButton(onClick = { Nav.go(Route.Quiz(deck.id, objectiveId)) }) {
                        Text("Prefer a plain quiz without the battle?", color = Faint)
                    }
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
                    "⚔️ Battle!", {
                        val shuffled = from.shuffled()
                        questions = if (count == 0) shuffled else shuffled.take(count)
                        round++
                        started = true
                    },
                    Modifier.fillMaxWidth(), color = BattleColor, textColor = Ink, enabled = from.isNotEmpty(),
                )
            }
        }
        return
    }

    Battle(deck.id, questions, partner, stage, round, onAgain = { list ->
        if (list.isNotEmpty()) {
            questions = list
            round++
        } else started = false
    })
}

@Composable
private fun Battle(
    deckId: String,
    questions: List<Question>,
    partner: Species,
    stage: Int,
    round: Int,
    onAgain: (List<Question>) -> Unit,
) {
    val deck = Store.deck(deckId) ?: return
    val p = Store.progress(deckId)
    val enemies = remember(round) {
        val fromTerms = deck.terms.shuffled().map { it.id to Creatures.forTerm(deck, it) }
        fromTerms.ifEmpty { listOf("" to Creatures.forKey(deck, "wild", "")) }
    }
    val maxHp = 100 + (stage - 1) * 20
    var index by remember(round) { mutableIntStateOf(0) }
    var enemyNo by remember(round) { mutableIntStateOf(0) }
    var enemyHp by remember(round) { mutableIntStateOf(100) }
    var hp by remember(round) { mutableIntStateOf(maxHp) }
    var combo by remember(round) { mutableIntStateOf(0) }
    var befriended by remember(round) { mutableIntStateOf(0) }
    var xp by remember(round) { mutableIntStateOf(0) }
    var log by remember(round) { mutableStateOf("") }
    var popup by remember(round) { mutableStateOf("") }
    var popupKey by remember(round) { mutableIntStateOf(0) }
    var partnerHit by remember(round) { mutableIntStateOf(0) }
    var partnerAttack by remember(round) { mutableIntStateOf(0) }
    var enemyHit by remember(round) { mutableIntStateOf(0) }
    var enemyAttack by remember(round) { mutableIntStateOf(0) }
    var enemyDown by remember(round) { mutableStateOf(false) }
    var partnerDown by remember(round) { mutableStateOf(false) }
    var finished by remember(round) { mutableStateOf(false) }
    val results = remember(round) { mutableStateMapOf<String, Boolean>() }
    val states = remember(round) { HashMap<String, QState>() }

    val (enemyTermId, enemy) = enemies[enemyNo % enemies.size]
    LaunchedEffect(enemyNo, round) {
        if (enemyTermId.isNotEmpty() && p.seen.add(enemyTermId)) Store.progressChanged(deckId)
        log = "A wild ${enemy.name} appeared! ${enemy.element.emoji}"
    }

    if (finished) {
        BattleResults(questions, results, xp, befriended, partner, stage) { onAgain(it) }
        return
    }
    val q = questions.getOrNull(index) ?: run {
        finished = true
        return
    }
    val st = states.getOrPut(q.id) { QState() }
    val listState = rememberLazyListState()
    LaunchedEffect(index) { listState.scrollToItem(0) }

    Screen("Battle Dojo", subtitle = "Turn ${index + 1} of ${questions.size} · $befriended befriended · +$xp XP") {
        // The arena.
        Box(
            Modifier
                .fillMaxWidth()
                .height(236.dp)
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Brush.verticalGradient(listOf(enemy.element.dark.copy(alpha = 0.45f), Card, partner.element.dark.copy(alpha = 0.35f)))),
        ) {
            // Ground spots.
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .offset(x = (-14).dp, y = 96.dp)
                    .size(width = 120.dp, height = 22.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f)),
            )
            Box(
                Modifier
                    .align(Alignment.BottomStart)
                    .offset(x = 14.dp, y = (-8).dp)
                    .size(width = 132.dp, height = 24.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.06f)),
            )
            HpPanel(enemy.name, "${enemy.element.emoji} wild", enemyHp, 100, Modifier.align(Alignment.TopStart).padding(12.dp))
            CreatureView(
                enemy,
                Modifier
                    .align(Alignment.TopEnd)
                    .padding(end = 18.dp, top = 8.dp)
                    .size(112.dp),
                faceLeft = true, hitKey = enemyHit, attackKey = enemyAttack, fainted = enemyDown,
            )
            CreatureView(
                partner,
                Modifier
                    .align(Alignment.BottomStart)
                    .padding(start = 16.dp, bottom = 6.dp)
                    .size(128.dp),
                stage = stage, hitKey = partnerHit, attackKey = partnerAttack, fainted = partnerDown,
            )
            HpPanel(
                partner.name, "Lv ${Stats.level(Prefs.current.xp)}" + if (combo >= 2) " · 🔥 combo ×$combo" else "",
                hp, maxHp, Modifier.align(Alignment.BottomEnd).padding(12.dp),
            )
            DamagePopup(popup, popupKey, Modifier.align(Alignment.Center))
        }
        Text(
            log, color = Paper, fontSize = 14.sp, fontWeight = FontWeight.Medium,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 16.dp),
        ) {
            item(key = q.id + round) {
                QuestionCard(q, st) { ok ->
                    results[q.id] = ok
                    if (ok) p.qRight[q.id] = (p.qRight[q.id] ?: 0) + 1 else p.qWrong[q.id] = (p.qWrong[q.id] ?: 0) + 1
                    if (ok) {
                        combo++
                        val base = when (q.level) {
                            3 -> 50
                            2 -> 36
                            else -> 28
                        }
                        var dmg = base
                        if (combo >= 3) dmg = (dmg * 1.5f).toInt()
                        if (st.hint) dmg = (dmg * 0.6f).toInt()
                        enemyHp = (enemyHp - dmg).coerceAtLeast(0)
                        partnerAttack++
                        enemyHit++
                        popup = "−$dmg"
                        popupKey++
                        val extra = when {
                            q.level >= 3 -> " Critical hit!"
                            combo >= 3 -> " Combo ×$combo!"
                            else -> ""
                        }
                        log = "${partner.name} used ${partner.element.move}!$extra"
                        var gained = 10
                        if (enemyHp == 0) {
                            enemyDown = true
                            befriended++
                            gained += 15
                            if (enemyTermId.isNotEmpty()) p.friends.add(enemyTermId)
                            log += " ${enemy.name} was befriended! 🤝"
                        }
                        Stats.award(gained)
                        xp += gained
                    } else {
                        combo = 0
                        hp = (hp - 25).coerceAtLeast(0)
                        enemyAttack++
                        partnerHit++
                        popup = "−25"
                        popupKey++
                        log = "${enemy.name} struck back! Check the explanation, then try the next one."
                        if (hp == 0) {
                            partnerDown = true
                            log = "${partner.name} is worn out!"
                        }
                        Stats.award(3)
                        xp += 3
                    }
                    Store.progressChanged(deckId)
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
            GhostButton("Run 🏃", { finished = true }, Modifier.weight(1f))
            when {
                !st.checked -> BigButton("Answer to attack", {}, Modifier.weight(2f), color = BattleColor, textColor = Ink, enabled = false)
                partnerDown -> BigButton("🍙 Share an onigiri", {
                    hp = maxHp
                    partnerDown = false
                    log = "${partner.name} feels better! Back to it."
                    if (index < questions.size - 1) index++ else finished = true
                }, Modifier.weight(2f), color = Gold, textColor = Ink)
                index >= questions.size - 1 -> BigButton("Finish battle", { finished = true }, Modifier.weight(2f), color = Good, textColor = Ink)
                else -> BigButton(if (enemyDown) "Next creature →" else "Next turn →", {
                    if (enemyDown) {
                        enemyDown = false
                        enemyHp = 100
                        enemyNo++
                    }
                    index++
                }, Modifier.weight(2f), color = BattleColor, textColor = Ink)
            }
        }
    }
}

@Composable
private fun HpPanel(name: String, sub: String, hp: Int, max: Int, modifier: Modifier) {
    val f by animateFloatAsState(hp.toFloat() / max.coerceAtLeast(1), tween(450), label = "hp")
    val color = when {
        f > 0.5f -> Good
        f > 0.2f -> Gold
        else -> Bad
    }
    Column(
        modifier
            .widthIn(max = 170.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(Ink.copy(alpha = 0.72f))
            .padding(horizontal = 10.dp, vertical = 7.dp),
    ) {
        Text(name, color = Paper, fontWeight = FontWeight.Bold, fontSize = 14.sp, maxLines = 1)
        Text(sub, color = Faint, fontSize = 11.sp, maxLines = 1)
        Spacer(Modifier.height(4.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("HP", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Bar(f, color, Modifier.width(96.dp), height = 7.dp)
        }
        Text("$hp/$max", color = Faint, fontSize = 10.sp)
    }
}

@Composable
private fun DamagePopup(text: String, key: Int, modifier: Modifier) {
    val rise = remember { Animatable(0f) }
    LaunchedEffect(key) {
        if (key == 0) return@LaunchedEffect
        rise.snapTo(0f)
        rise.animateTo(1f, tween(900))
    }
    if (key == 0 || rise.value >= 1f) return
    Text(
        text, color = Color.White, fontSize = 30.sp, fontWeight = FontWeight.Black,
        modifier = modifier.graphicsLayer {
            translationY = -rise.value * 60f
            alpha = 1f - rise.value
        },
    )
}

@Composable
private fun BattleResults(
    questions: List<Question>,
    results: Map<String, Boolean>,
    xp: Int,
    befriended: Int,
    partner: Species,
    stage: Int,
    onAgain: (List<Question>) -> Unit,
) {
    val right = results.values.count { it }
    val missed = questions.filter { results[it.id] == false }
    val pct = if (results.isEmpty()) 0 else right * 100 / results.size
    Screen("Battle results") {
        LazyColumn(
            Modifier.weight(1f),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                InkCard(Modifier.fillMaxWidth(), border = BattleColor.copy(alpha = 0.4f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CreatureView(partner, Modifier.size(100.dp), stage = stage)
                        Column(Modifier.padding(start = 12.dp)) {
                            Text(
                                when {
                                    pct >= 90 -> "Legendary! 🏆"
                                    pct >= 80 -> "Victory! 🔥"
                                    pct >= 60 -> "Good fight 💪"
                                    else -> "Keep training 🥋"
                                },
                                style = MaterialTheme.typography.titleLarge, color = Paper,
                            )
                            Text("$right of ${results.size} right · $befriended befriended · +$xp XP", color = Soft, fontSize = 14.sp)
                        }
                    }
                }
            }
            if (missed.isNotEmpty()) {
                item { SectionLabel("Learn from these") }
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
            if (missed.isNotEmpty()) {
                BigButton("⚔️ Rematch the ${missed.size} I missed", { onAgain(missed.shuffled()) }, Modifier.fillMaxWidth(), color = BattleColor, textColor = Ink)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                GhostButton("New battle", { onAgain(emptyList()) }, Modifier.weight(1f))
                GhostButton("Done", { Nav.back() }, Modifier.weight(1f))
            }
        }
    }
}

/** Pick your partner: shown the first time you need one. */
@Composable
fun StarterPicker(modifier: Modifier = Modifier, onPicked: () -> Unit = {}) {
    var pick by remember { mutableIntStateOf(-1) }
    var nick by remember { mutableStateOf("") }
    LazyColumn(
        modifier,
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("Choose your partner", style = MaterialTheme.typography.headlineSmall, color = Paper)
            Text(
                "Your partner trains with you, battles for you and evolves as your level grows (at levels 10 and 25).",
                color = Faint, fontSize = 14.sp,
            )
        }
        Creatures.starters.forEachIndexed { i, s ->
            item(key = "s$i") {
                val on = pick == i
                InkCard(
                    Modifier.fillMaxWidth(),
                    color = if (on) CardHigh else Card,
                    border = if (on) s.species.element.main else Hairline,
                    onClick = { pick = i },
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        CreatureView(s.species, Modifier.size(96.dp))
                        Spacer(Modifier.width(10.dp))
                        Column(Modifier.weight(1f)) {
                            Text("${s.species.name} ${s.species.element.emoji}", color = Paper, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                            Text(s.blurb, color = Soft, fontSize = 13.sp)
                            Text("Evolves into ${s.stages[1]}, then ${s.stages[2]}", color = Faint, fontSize = 12.sp)
                        }
                    }
                    if (on) {
                        Spacer(Modifier.height(10.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            (1..3).forEach { stg ->
                                CreatureView(s.species, Modifier.size(64.dp), stage = stg, bob = false)
                            }
                        }
                    }
                }
            }
        }
        item {
            androidx.compose.material3.OutlinedTextField(
                nick, { nick = it.take(16) }, singleLine = true,
                label = { Text("Nickname (optional)") },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            BigButton(
                if (pick >= 0) "Train with ${Creatures.starters[pick].species.name}!" else "Pick one above",
                {
                    Prefs.update { it.copy(partner = pick, partnerName = nick.trim()) }
                    onPicked()
                },
                Modifier.fillMaxWidth(), color = BattleColor, textColor = Ink, enabled = pick >= 0,
            )
        }
    }
}
