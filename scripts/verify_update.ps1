#requires -Version 7.0
param([Parameter(Mandatory=$true)][string]$Directory)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$raw=[IO.File]::ReadAllBytes((Join-Path $Directory 'version.json'))
$sig=[Convert]::FromBase64String([IO.File]::ReadAllText((Join-Path $Directory 'version.json.sig')))
$rsa=[Security.Cryptography.RSA]::Create()
try {
    $rsa.ImportFromPem([IO.File]::ReadAllText((Join-Path $projectRoot 'app/src/main/res/raw/update_public_key.pem')))
    if(-not $rsa.VerifyData($raw,$sig,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)) { throw 'Assinatura inválida: metadados não serão interpretados.' }
    $tampered=[byte[]]$raw.Clone(); $tampered[0]=$tampered[0] -bxor 1
    if($rsa.VerifyData($tampered,$sig,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)) { throw 'Teste de adulteração falhou.' }
    $m=[Text.Encoding]::UTF8.GetString($raw) | ConvertFrom-Json
    if($m.schema -ne 1 -or $m.app_id -ne 'com.camlite.android' -or $m.os -ne 'android' -or $m.package_type -ne 'android-apk' -or $m.key_id -ne 'prates-updates-2026') { throw 'Identidade incompatível.' }
    if([DateTimeOffset]$m.expires_at -le [DateTimeOffset]::UtcNow) { throw 'Metadados vencidos.' }
    $file=Get-Item -LiteralPath (Join-Path $Directory ([Uri]$m.url).Segments[-1])
    if($file.Length -ne $m.size -or (Get-FileHash -LiteralPath $file.FullName -Algorithm SHA256).Hash.ToLowerInvariant() -ne $m.sha256) { throw 'Tamanho/hash não correspondem aos metadados autenticados.' }
    Write-Output "Assinatura RSA, teste de adulteração, tamanho e SHA-256 válidos: $($file.Name)"
} finally { $rsa.Dispose() }
