package dev.shugyo.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import dev.shugyo.ai.Ai
import dev.shugyo.ai.Claude
import dev.shugyo.data.Prefs
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen() {
    val s by Prefs.settings.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showKeys by remember { mutableStateOf(false) }
    var testing by remember { mutableStateOf(false) }
    var results by remember { mutableStateOf<List<String>>(emptyList()) }

    fun open(url: String) {
        try {
            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
        } catch (e: Exception) {
            // no browser
        }
    }

    Screen("Settings") {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp)
                .navigationBarsPadding(),
        ) {
            SectionLabel("AI that builds your study sets")
            InkCard(Modifier.fillMaxWidth()) {
                Text("Gemini (free)", color = Paper, fontWeight = FontWeight.SemiBold)
                Text(
                    "Get a free key at Google AI Studio, then paste it here. You can use the same key as Yomiage.",
                    color = Faint, fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    s.geminiKey, { v -> Prefs.update { it.copy(geminiKey = v.trim()) } },
                    label = { Text("Gemini API key") }, singleLine = true,
                    visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                TextButton(onClick = { open("https://aistudio.google.com/apikey") }) { Text("Get a free Gemini key →", color = Gold) }

                Spacer(Modifier.height(10.dp))
                Text("Claude (optional)", color = Paper, fontWeight = FontWeight.SemiBold)
                Text("A key from the Claude Console. Used as a backup, or first if you switch it on below.", color = Faint, fontSize = 13.sp)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    s.claudeKey, { v -> Prefs.update { it.copy(claudeKey = v.trim()) } },
                    label = { Text("Claude API key") }, singleLine = true,
                    visualTransformation = if (showKeys) VisualTransformation.None else PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (s.claudeKey.isNotBlank()) {
                    Spacer(Modifier.height(6.dp))
                    Claude.models.forEach { (id, label) ->
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = s.claudeModel == id,
                                onClick = { Prefs.update { it.copy(claudeModel = id) } },
                                colors = RadioButtonDefaults.colors(selectedColor = Seal),
                            )
                            Text(label, color = Soft, fontSize = 14.sp)
                        }
                    }
                    ToggleRow("Use Claude first", "Otherwise Gemini goes first and Claude is the backup", s.preferClaude) { v ->
                        Prefs.update { it.copy(preferClaude = v) }
                    }
                }
                ToggleRow("Show keys", "", showKeys) { showKeys = it }
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    GhostButton("Test my keys", {
                        testing = true
                        results = emptyList()
                        scope.launch {
                            results = Ai.testAll()
                            testing = false
                        }
                    }, enabled = !testing)
                    if (testing) {
                        Spacer(Modifier.width(12.dp))
                        CircularProgressIndicator(Modifier.size(22.dp), color = Gold, strokeWidth = 2.dp)
                    }
                }
                results.forEach { Text(it, color = if ("✓" in it) Good else Soft, fontSize = 13.sp, modifier = Modifier.padding(top = 6.dp)) }
            }

            SectionLabel("How things are explained")
            InkCard(Modifier.fillMaxWidth()) {
                Text("Explain things for…", color = Paper, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    s.audience, { v -> Prefs.update { it.copy(audience = v) } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }

            SectionLabel("Study")
            InkCard(Modifier.fillMaxWidth()) {
                SettingStepper("Questions per objective", "When building a set", s.questionsPerObjective, 2, 10, 1) { v ->
                    Prefs.update { it.copy(questionsPerObjective = v) }
                }
                SettingStepper("New flashcards a day", "Per study set", s.newCardsPerDay, 5, 100, 5) { v ->
                    Prefs.update { it.copy(newCardsPerDay = v) }
                }
                SettingStepper("Daily XP goal", "Fills the ring on the home screen", s.dailyGoal, 50, 1000, 50) { v ->
                    Prefs.update { it.copy(dailyGoal = v) }
                }
            }

            SectionLabel("Coming next")
            InkCard(Modifier.fillMaxWidth(), border = BattleColor.copy(alpha = 0.35f)) {
                Text("⚔️ Battle Dojo and 🏯 Exams", color = Paper, fontWeight = FontWeight.SemiBold)
                Text(
                    "Turn-based battles with study creatures that level up as you master terms, and timed mock exams across chapters.",
                    color = Faint, fontSize = 13.sp,
                )
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ToggleRow(title: String, body: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper)
            if (body.isNotBlank()) Text(body, color = Faint, fontSize = 12.sp)
        }
        Switch(checked, onChange, colors = SwitchDefaults.colors(checkedTrackColor = Seal))
    }
}

@Composable
private fun SettingStepper(title: String, body: String, value: Int, min: Int, max: Int, step: Int, onChange: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, color = Paper)
            Text(body, color = Faint, fontSize = 12.sp)
        }
        Stepper(value, min, max, step, onChange)
    }
}
