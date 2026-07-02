param(
    [string]$BackendUrl = "https://goalio-backend-sd2p.onrender.com",
    [string]$RefreshPrefix = ""
)

$ErrorActionPreference = "Stop"
$resRoot = Join-Path $PSScriptRoot "..\app\src\main\res"
$basePath = Join-Path $resRoot "values\strings.xml"
$base = [xml](Get-Content -Raw -Encoding UTF8 $basePath)

$locales = [ordered]@{
    "values-ar-rSA"   = "ar-SA"
    "values-b+es+419" = "es-419"
    "values-de-rDE"   = "de-DE"
    "values-fr-rFR"   = "fr-FR"
    "values-hi-rIN"   = "hi-IN"
    "values-it-rIT"   = "it-IT"
    "values-ja-rJP"   = "ja-JP"
    "values-ko-rKR"   = "ko-KR"
    "values-pt-rBR"   = "pt-BR"
    "values-ru-rRU"   = "ru-RU"
    "values-zh-rCN"   = "zh-CN"
    "values-zh-rTW"   = "zh-TW"
}

foreach ($entry in $locales.GetEnumerator()) {
    $directory = Join-Path $resRoot $entry.Key
    $path = Join-Path $directory "strings.xml"
    $existing = if (Test-Path $path) { [xml](Get-Content -Raw -Encoding UTF8 $path) } else { $null }
    $existingByName = @{}
    if ($existing) {
        foreach ($node in $existing.resources.string) { $existingByName[$node.name] = $node.InnerText }
    }

    $missingNodes = @($base.resources.string | Where-Object {
        $refresh = $RefreshPrefix -and ([string]$_.name).StartsWith($RefreshPrefix)
        $_.name -ne "onesignal_app_id" -and ($refresh -or -not $existingByName.ContainsKey($_.name))
    })
    $translatedByText = @{}
    if ($missingNodes.Count -gt 0) {
        $body = @{
            texts = @($missingNodes | ForEach-Object { $_.InnerText })
            target_language = $entry.Value
        } | ConvertTo-Json -Depth 4
        $webResponse = Invoke-WebRequest -UseBasicParsing -Uri "$BackendUrl/api/v1/translate/batch" -Method Post -ContentType "application/json; charset=utf-8" -Body $body
        $stream = $webResponse.RawContentStream
        $stream.Position = 0
        $reader = New-Object System.IO.StreamReader($stream, [System.Text.Encoding]::UTF8, $true)
        try { $response = ($reader.ReadToEnd() | ConvertFrom-Json) } finally { $reader.Dispose() }
        foreach ($property in $response.translations.PSObject.Properties) {
            $translatedByText[$property.Name] = [string]$property.Value
        }
    }

    $document = New-Object System.Xml.XmlDocument
    $resources = $document.CreateElement("resources")
    [void]$document.AppendChild($resources)
    foreach ($source in $base.resources.string) {
        $node = $document.CreateElement("string")
        $node.SetAttribute("name", [string]$source.name)
        if ($source.translatable -eq "false") { $node.SetAttribute("translatable", "false") }
        $refresh = $RefreshPrefix -and ([string]$source.name).StartsWith($RefreshPrefix)
        $node.InnerText = if ($refresh -and $translatedByText.ContainsKey($source.InnerText)) {
            $translatedByText[$source.InnerText]
        } elseif ($existingByName.ContainsKey($source.name)) {
            $existingByName[$source.name]
        } elseif ($source.name -eq "onesignal_app_id") {
            $source.InnerText
        } elseif ($translatedByText.ContainsKey($source.InnerText)) {
            $translatedByText[$source.InnerText]
        } else {
            $source.InnerText
        }
        [void]$resources.AppendChild($node)
    }

    New-Item -ItemType Directory -Force -Path $directory | Out-Null
    $settings = New-Object System.Xml.XmlWriterSettings
    $settings.Indent = $true
    $settings.Encoding = New-Object System.Text.UTF8Encoding($false)
    $writer = [System.Xml.XmlWriter]::Create($path, $settings)
    try { $document.Save($writer) } finally { $writer.Dispose() }
}
