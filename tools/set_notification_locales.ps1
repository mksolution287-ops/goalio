param()

$ErrorActionPreference = "Stop"
$resRoot = Join-Path $PSScriptRoot "..\app\src\main\res"
$keys = @(
    "notification_channel_name",
    "notification_channel_description",
    "notification_next_match",
    "notification_kickoff",
    "notification_view_match",
    "notification_match_reminder",
    "notification_upcoming_match",
    "notification_match_in_24_hours",
    "notification_match_in_1_hour",
    "notification_kickoff_now"
)

$translations = [ordered]@{
    "values-ar-rSA" = @("تحديثات المباريات", "تذكيرات انطلاق المباريات والنتائج المباشرة وتنبيهات الأهداف.", "المباراة القادمة", "موعد الانطلاق", "عرض المباراة", "تذكير بالمباراة", "مباراة قادمة", "المباراة بعد 24 ساعة", "المباراة بعد ساعة", "انطلاق المباراة الآن")
    "values-b+es+419" = @("Actualizaciones de partidos", "Recordatorios de inicio, marcadores en vivo y alertas de gol.", "Próximo partido", "Inicio", "Ver partido", "Recordatorio del partido", "Partido próximo", "Partido en 24 horas", "Partido en 1 hora", "El partido comienza ahora")
    "values-de-rDE" = @("Spiel-Updates", "Anstoß-Erinnerungen, Live-Ergebnisse und Torbenachrichtigungen.", "Nächstes Spiel", "Anstoß", "Spiel ansehen", "Spielerinnerung", "Bevorstehendes Spiel", "Spiel in 24 Stunden", "Spiel in 1 Stunde", "Anstoß jetzt")
    "values-fr-rFR" = @("Actualités des matchs", "Rappels du coup d’envoi, scores en direct et alertes de but.", "Prochain match", "Coup d’envoi", "Voir le match", "Rappel de match", "Match à venir", "Match dans 24 heures", "Match dans 1 heure", "Coup d’envoi maintenant")
    "values-hi-rIN" = @("मैच अपडेट", "किकऑफ रिमाइंडर, लाइव स्कोर और गोल अलर्ट।", "अगला मैच", "किकऑफ", "मैच देखें", "मैच रिमाइंडर", "आगामी मैच", "24 घंटे में मैच", "1 घंटे में मैच", "अभी किकऑफ")
    "values-it-rIT" = @("Aggiornamenti partite", "Promemoria del calcio d’inizio, risultati in diretta e avvisi gol.", "Prossima partita", "Calcio d’inizio", "Vedi partita", "Promemoria partita", "Partita in arrivo", "Partita tra 24 ore", "Partita tra 1 ora", "Calcio d’inizio ora")
    "values-ja-rJP" = @("試合速報", "キックオフのリマインダー、ライブスコア、ゴール通知。", "次の試合", "キックオフ", "試合を見る", "試合リマインダー", "今後の試合", "試合開始まで24時間", "試合開始まで1時間", "まもなくキックオフ")
    "values-ko-rKR" = @("경기 업데이트", "킥오프 알림, 실시간 스코어 및 골 알림입니다.", "다음 경기", "킥오프", "경기 보기", "경기 알림", "예정된 경기", "24시간 후 경기", "1시간 후 경기", "지금 킥오프")
    "values-pt-rBR" = @("Atualizações de partidas", "Lembretes de início, placares ao vivo e alertas de gol.", "Próxima partida", "Início", "Ver partida", "Lembrete da partida", "Próxima partida", "Partida em 24 horas", "Partida em 1 hora", "A partida começa agora")
    "values-ru-rRU" = @("Обновления матчей", "Напоминания о начале, счёт в реальном времени и уведомления о голах.", "Следующий матч", "Начало матча", "Смотреть матч", "Напоминание о матче", "Предстоящий матч", "Матч через 24 часа", "Матч через 1 час", "Матч начинается")
    "values-zh-rCN" = @("比赛动态", "开赛提醒、实时比分和进球通知。", "下一场比赛", "开赛", "查看比赛", "比赛提醒", "即将开始的比赛", "比赛将在24小时后开始", "比赛将在1小时后开始", "比赛现在开始")
    "values-zh-rTW" = @("賽事動態", "開賽提醒、即時比分和進球通知。", "下一場比賽", "開賽", "查看比賽", "比賽提醒", "即將開始的比賽", "比賽將在24小時後開始", "比賽將在1小時後開始", "比賽現在開始")
}

foreach ($entry in $translations.GetEnumerator()) {
    $path = Join-Path (Join-Path $resRoot $entry.Key) "strings.xml"
    $content = (Get-Content -Raw -Encoding UTF8 $path).Trim()
    $content = $content -replace '^```xml\s*', '' -replace '\s*`+$', ''
    $document = [xml]$content
    for ($index = 0; $index -lt $keys.Count; $index++) {
        $key = $keys[$index]
        $node = $document.resources.string | Where-Object { $_.name -eq $key } | Select-Object -First 1
        if (-not $node) {
            $node = $document.CreateElement("string")
            $node.SetAttribute("name", $key)
            [void]$document.resources.AppendChild($node)
        }
        $node.InnerText = $entry.Value[$index]
    }

    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Indent = $true
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
    $writer = [System.Xml.XmlWriter]::Create($path, $settings)
    try { $document.Save($writer) } finally { $writer.Dispose() }
}
