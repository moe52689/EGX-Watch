param([Parameter(Mandatory=$true)][string]$GatewayUrl)
$ErrorActionPreference='Stop'
$uri=[Uri]$GatewayUrl
if ($uri.Scheme -ne 'https' -or $uri.UserInfo -or $uri.Query -or $uri.Fragment) { throw 'Use an authorized HTTPS gateway base URL without credentials' }
# Contract: a complete, licensed identity directory, never prices. Refuse redirects.
$response=Invoke-WebRequest ($GatewayUrl.TrimEnd('/')+'/instruments?q=') -MaximumRedirection 0 -UseBasicParsing
$data=$response.Content | ConvertFrom-Json
if (!$data.instruments -or $data.instruments.Count -gt 500) { throw 'Invalid identity directory' }
foreach($row in $data.instruments) {
    if (!$row.validated -or !$row.id -or !$row.ticker -or !$row.name -or $row.type -notin @('STOCK','ETF','FUND')) { throw 'Invalid instrument' }
}
$records=@($data.instruments | Select-Object id,ticker,name,type,currency,source,verifiedAt,validated)
$path=Join-Path (Split-Path $PSScriptRoot -Parent) 'app/src/main/resources/instrument-catalog.json'
[IO.File]::WriteAllText($path,(@{instruments=$records}|ConvertTo-Json -Depth 6),[Text.UTF8Encoding]::new($false))
