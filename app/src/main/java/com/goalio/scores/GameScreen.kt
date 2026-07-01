package com.goalio.scores

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.goalio.scores.ui.theme.GoalioColors
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun GameScreen(onBack: () -> Unit, onOpenHome: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val metrics = rememberGoalioMetrics()
    val scope = rememberCoroutineScope()
    var session by remember { mutableStateOf<QuizSessionInfo?>(null) }
    var index by remember { mutableStateOf(0) }
    var remaining by remember { mutableStateOf(15) }
    var answer by remember { mutableStateOf<QuizAnswerInfo?>(null) }
    var submitting by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var leaderboard by remember { mutableStateOf(QuizRepository.cachedLeaderboard(context)) }

    suspend fun start() {
        loading = true; error = null
        runCatching { QuizRepository.start() }.onSuccess { session = it; index = 0; remaining = 15; answer = null }.onFailure { error = it.message }
        loading = false
    }
    suspend fun submit(selected: Int) {
        val current = session?.questions?.getOrNull(index) ?: return
        if (submitting || answer != null) return
        submitting = true
        runCatching { QuizRepository.answer(session!!.sessionId, current.id, selected) }
            .onSuccess { answer = it; leaderboard = runCatching { QuizRepository.leaderboard(context) }.getOrDefault(leaderboard) }
            .onFailure { error = it.message }
        submitting = false
    }

    LaunchedEffect(Unit) { leaderboard = runCatching { QuizRepository.leaderboard(context) }.getOrDefault(leaderboard) }
    LaunchedEffect(session?.sessionId, index, answer) {
        if (session == null || answer != null) return@LaunchedEffect
        remaining = session!!.questions[index].timeLimitSeconds
        while (remaining > 0 && answer == null) { delay(1_000); remaining-- }
        if (remaining == 0 && answer == null) submit(-1)
    }

    GoalioBackground {
        LazyColumn(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding(), contentPadding = PaddingValues(metrics.horizontalPadding, 18.dp, metrics.horizontalPadding, 40.dp), verticalArrangement = Arrangement.spacedBy(18.dp)) {
            item { Row(verticalAlignment = Alignment.CenterVertically) { Text("‹", color = GoalioColors.Accent, fontSize = metrics.sp(34), modifier = Modifier.clickable(onClick = onBack)); Spacer(Modifier.width(12.dp)); Text("GOALIO QUIZ", color = GoalioColors.TextPrimary, fontSize = metrics.sp(24), fontWeight = FontWeight.Black); Spacer(Modifier.weight(1f)); Text("${leaderboard?.me?.xp ?: 0} XP", color = GoalioColors.Accent, fontWeight = FontWeight.Black) } }
            if (session == null) {
                item { QuizWelcome(loading, error) { loading = true } }
                item { Button(onClick = { scope.launch { start() } }, enabled = !loading, colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Accent), modifier = Modifier.fillMaxWidth().height(54.dp)) { Text(if (loading) "LOADING…" else "PLAY 5 QUESTIONS", color = Color.Black, fontWeight = FontWeight.Black) } }
            } else {
                val question = session!!.questions[index]
                item { Row { Text("QUESTION ${index + 1}/5", color = GoalioColors.TextSecondary, fontWeight = FontWeight.Bold); Spacer(Modifier.weight(1f)); Text("00:%02d".format(remaining), color = if (remaining <= 5) GoalioColors.Live else GoalioColors.Accent, fontWeight = FontWeight.Black) } }
                item { Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder)) { Column(Modifier.padding(22.dp)) { Text(question.category.uppercase(), color = GoalioColors.Accent, fontSize = metrics.sp(12), fontWeight = FontWeight.Black); Spacer(Modifier.height(12.dp)); Text(question.prompt, color = GoalioColors.TextPrimary, fontSize = metrics.sp(22), fontWeight = FontWeight.Black) } } }
                items(question.options.indices.toList()) { optionIndex ->
                    val result = answer
                    val selectedColor = when { result == null -> GoalioColors.Surface1; optionIndex == result.correctAnswerIndex -> Color(0xFF194D35); else -> GoalioColors.Surface1 }
                    Surface(color = selectedColor, shape = RoundedCornerShape(16.dp), border = BorderStroke(1.dp, if (result != null && optionIndex == result.correctAnswerIndex) Color(0xFF38D985) else GoalioColors.CardBorder), modifier = Modifier.fillMaxWidth().clickable(enabled = result == null && !submitting) { scope.launch { submit(optionIndex) } }) {
                        Text("${('A'.code + optionIndex).toChar()}.  ${question.options[optionIndex]}", Modifier.padding(18.dp), color = GoalioColors.TextPrimary, fontWeight = FontWeight.Bold)
                    }
                }
                answer?.let { result -> item { Surface(color = if (result.correct) Color(0xFF173E2D) else Color(0xFF4A2020), shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(18.dp)) { Text(if (result.correct) "+${result.xpDelta} XP • CORRECT" else "${result.xpDelta} XP • ${if (result.timedOut) "TIME UP" else "WRONG"}", color = if (result.correct) Color(0xFF52E49A) else Color(0xFFFF7C72), fontWeight = FontWeight.Black); Spacer(Modifier.height(7.dp)); Text(result.explanation, color = GoalioColors.TextSecondary) } } } }
                answer?.let { result -> item { Button(onClick = { if (result.completed) { session = null } else { index++; answer = null; remaining = 15 } }, colors = ButtonDefaults.buttonColors(containerColor = GoalioColors.Accent), modifier = Modifier.fillMaxWidth()) { Text(if (result.completed) "FINISH" else "NEXT QUESTION", color = Color.Black, fontWeight = FontWeight.Black) } } }
            }
            item { Text("LEADERBOARD", color = GoalioColors.TextPrimary, fontSize = metrics.sp(18), fontWeight = FontWeight.Black) }
            items(leaderboard?.entries.orEmpty().take(10)) { player -> Surface(color = if (player.isMe) Color(0xFF302A1E) else GoalioColors.Surface1, shape = RoundedCornerShape(14.dp)) { Row(Modifier.fillMaxWidth().padding(15.dp)) { Text("#${player.rank}", color = GoalioColors.Accent, fontWeight = FontWeight.Black, modifier = Modifier.width(48.dp)); Text("@${player.username}", color = GoalioColors.TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f)); Text("${player.xp} XP", color = GoalioColors.TextSecondary, fontWeight = FontWeight.Black) } } }
        }
    }
}

@Composable private fun QuizWelcome(loading: Boolean, error: String?, onStart: () -> Unit) {
    Surface(color = GoalioColors.Surface1, shape = RoundedCornerShape(22.dp), border = BorderStroke(1.dp, GoalioColors.CardBorder)) { Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) { Text("THE FOOTBALL FIVE", color = GoalioColors.TextPrimary, fontSize = 26.sp, fontWeight = FontWeight.Black); Spacer(Modifier.height(10.dp)); Text("Five random questions. 15 seconds each. +10 XP correct, −5 XP wrong.", color = GoalioColors.TextSecondary, textAlign = TextAlign.Center); error?.let { Spacer(Modifier.height(8.dp)); Text(it, color = GoalioColors.Live) } } }
}
