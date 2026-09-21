param([switch]$UseDownloadedFiles)
$ErrorActionPreference = 'Stop'
$root = Split-Path $PSScriptRoot -Parent
$stockPath = Join-Path $root '.tools/egx-catalog.json'
$fundPath = Join-Path $root '.tools/current-snduk.html'
if (!$UseDownloadedFiles) {
    New-Item -ItemType Directory -Force (Join-Path $root '.tools') | Out-Null
    $query = '{"filter":[{"left":"exchange","operation":"equal","right":"EGX"}],"columns":["name","description","currency","type","exchange"],"range":[0,1000]}'
    Invoke-WebRequest 'https://scanner.tradingview.com/egypt/scan' -Method Post -Body $query -ContentType 'application/json' -OutFile $stockPath -UseBasicParsing
    Invoke-WebRequest 'https://snduk.com/eg/page/mutual-funds-prices-today?lang=en' -OutFile $fundPath -UseBasicParsing
}
$date = Get-Date -Format 'yyyy-MM-dd'
$stocks = Get-Content $stockPath -Raw -Encoding UTF8 | ConvertFrom-Json
if ($stocks.totalCount -ne $stocks.data.Count) { throw 'Incomplete stock catalogue' }
$entries = @($stocks.data | ForEach-Object {
    $d = $_.d
    if ($d[4] -eq 'EGX' -and $d[3] -eq 'stock') {
        @{id="EGX:$($d[0])"; ticker=$d[0]; name=$d[1]; type='STOCK'; currency=$d[2]; source='TradingView EGX identity directory'; verifiedAt=$date; validated=$true}
    }
})
$aliases = @{'thndr-t70-fund'='T70'; 'ci-the-quant-fund'='CTQ'; 'beltone-fadda-silver-fund'='BFA'; 'az-gold-fund'='AZG'}
$html = Get-Content $fundPath -Raw -Encoding UTF8
foreach ($row in [regex]::Matches($html, '(?s)<tr\b[^>]*>(.*?)</tr>')) {
    $cells = [regex]::Matches($row.Groups[1].Value, '(?s)<td\b[^>]*>(.*?)</td>')
    if ($cells.Count -ne 4) { continue }
    $link = [regex]::Match($cells[0].Value, '(?s)<a\b[^>]*href="[^"]*/eg/funds/([a-zA-Z0-9-]+)[^"]*"[^>]*>(.*?)</a>')
    $currency = [regex]::Match($cells[3].Value, '\b(EGP|USD|EUR|GBP)\b').Value
    if (!$link.Success -or !$currency) { continue }
    $slug = $link.Groups[1].Value
    $name = [System.Net.WebUtility]::HtmlDecode([regex]::Replace($link.Groups[2].Value, '<[^>]+>', '')).Trim()
    $ticker = if ($aliases.ContainsKey($slug)) { $aliases[$slug] } else { $slug }
    $id = if ($aliases.ContainsKey($slug)) { "EG:FUND:$ticker" } else { "SNDUK:$slug" }
    $entries += @{id=$id; ticker=$ticker; name=$name; type='FUND'; currency=$currency; source="https://snduk.com/eg/funds/$slug`?lang=en"; verifiedAt=$date; validated=$true}
}
$entries = @($entries | Sort-Object -Property { $_.id } -Unique)
if (($entries | Where-Object { $_.type -eq 'FUND' }).Count -lt 4) { throw 'Fund table format changed' }
$destination = Join-Path $root 'app/src/main/resources'
New-Item -ItemType Directory -Force $destination | Out-Null
$json = @{instruments=$entries} | ConvertTo-Json -Depth 5
[IO.File]::WriteAllText((Join-Path $destination 'instrument-catalog.json'), $json, [Text.UTF8Encoding]::new($false))
$entries | Group-Object -Property { $_.type } | Select-Object Name,Count
# Identity metadata only. Never bundle downloaded price/NAV snapshots in the application.
