package zero.ramjikvarosai.hirebazzar

private fun seededPregameHomeProbability(league: String, matchId: String, home: MatchTeamInfo?, away: MatchTeamInfo?): Float {
    val seed = "$league:$matchId:${home?.id.orEmpty()}:${away?.id.orEmpty()}".hashCode().toUInt()
    return (46 + (seed % 9u).toInt()).toFloat()
}

fun ScheduleMatch.sharedHomeWinProbability(): Float {
    if (state == "pre") return seededPregameHomeProbability(league, matchId, homeTeam, awayTeam)
    val home = homeTeam?.score ?: return seededPregameHomeProbability(league, matchId, homeTeam, awayTeam)
    val away = awayTeam?.score ?: return seededPregameHomeProbability(league, matchId, homeTeam, awayTeam)
    return (50f + (home - away) * 12f).coerceIn(30f, 78f)
}

fun MatchDetail.sharedHomeWinProbability(): Float {
    val state = detailState()
    if (state != "pre") {
        winProbability?.homeWinPercentage?.let { return it.toFloat().coerceIn(1f, 99f) }
    }
    if (state == "pre") return seededPregameHomeProbability(league, matchId, homeTeam, awayTeam)
    val home = homeTeam?.score ?: return seededPregameHomeProbability(league, matchId, homeTeam, awayTeam)
    val away = awayTeam?.score ?: return seededPregameHomeProbability(league, matchId, homeTeam, awayTeam)
    return (50f + (home - away) * 12f).coerceIn(30f, 78f)
}

private fun MatchDetail.detailState(): String? {
    val raw = "${status.orEmpty()} ${statusDescription.orEmpty()}".lowercase()
    return when {
        raw.contains("final") || raw.contains("full") || raw.contains("ft") || raw.contains("post") -> "post"
        raw.contains("live") || raw.contains("half") || Regex("\\b\\d{1,3}'").containsMatchIn(raw) -> "in"
        raw.contains("pre") || raw.contains("scheduled") || raw.contains("upcoming") || raw.isBlank() -> "pre"
        else -> null
    }
}
