#requires -Version 7.0
param(
    [Parameter(Mandatory=$true)][string]$Package,
    [Parameter(Mandatory=$true)][string]$Output,
    [Parameter(Mandatory=$true)][string]$NotesFile,
    [string]$Key = (Join-Path ([Environment]::GetFolderPath('UserProfile')) '.codex/private/prates-updates/signing.pem')
)
$ErrorActionPreference='Stop'
$projectRoot=Split-Path -Parent $PSScriptRoot
$release=Get-Content -LiteralPath (Join-Path $projectRoot 'release.json') -Raw | ConvertFrom-Json
if($release.repository -ne 'Pr4Tes/cam-lite-android-releases' -or $release.channel -ne 'stable') { throw 'Distribuição incompatível com a fonte incorporada ao aplicativo.' }
if($release.version -notmatch '^\d+\.\d+\.\d+$' -or $release.version_code -lt 1 -or $release.min_sdk -lt 26) { throw 'Versão/requisitos inválidos.' }
$packageFile=Get-Item -LiteralPath $Package
if($packageFile.Name -ne $release.apk -or $packageFile.Length -gt 64MB) { throw 'Arquivo de pacote incompatível.' }
Add-Type -AssemblyName System.IO.Compression.FileSystem
$archive=[IO.Compression.ZipFile]::OpenRead($packageFile.FullName)
try { if($archive.Entries.FullName | Where-Object { $_ -match '^lib/.+\.so$' }) { throw 'Revise as ABIs: a declaração universal deste publicador exige APK sem bibliotecas nativas.' } } finally { $archive.Dispose() }
$rsa=[Security.Cryptography.RSA]::Create()
$expected=[Security.Cryptography.RSA]::Create()
try {
    $rsa.ImportFromPem([IO.File]::ReadAllText($Key))
    $expected.ImportFromPem([IO.File]::ReadAllText((Join-Path $projectRoot 'app/src/main/res/raw/update_public_key.pem')))
    if($rsa.KeySize -lt 3072 -or [Convert]::ToBase64String($rsa.ExportSubjectPublicKeyInfo()) -ne [Convert]::ToBase64String($expected.ExportSubjectPublicKeyInfo())) { throw 'A chave privada não corresponde à chave pública confiável.' }
    $now=[DateTimeOffset]::UtcNow
    $manifest=[ordered]@{
        schema=1; app_id='com.camlite.android'; version=$release.version; version_code=[long]$release.version_code
        channel='stable'; os='android'; arch='universal'; architectures=@('armeabi-v7a','arm64-v8a','x86','x86_64')
        package_type='android-apk'; min_os_build=0; min_sdk=[int]$release.min_sdk
        url="https://github.com/$($release.repository)/releases/download/v$($release.version)/$($release.apk)"
        size=$packageFile.Length; sha256=(Get-FileHash -LiteralPath $packageFile.FullName -Algorithm SHA256).Hash.ToLowerInvariant()
        published_at=$now.ToString('o'); expires_at=$now.AddDays(90).ToString('o')
        notes=[IO.File]::ReadAllText((Resolve-Path -LiteralPath $NotesFile)); mandatory=$false; key_id=$release.key_id
    }
    $bytes=[Text.UTF8Encoding]::new($false).GetBytes(($manifest | ConvertTo-Json -Depth 5 -Compress))
    $signature=$rsa.SignData($bytes,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)
    if(-not $expected.VerifyData($bytes,$signature,[Security.Cryptography.HashAlgorithmName]::SHA256,[Security.Cryptography.RSASignaturePadding]::Pkcs1)) { throw 'Assinatura produzida inválida.' }
    New-Item -ItemType Directory -Force -Path $Output | Out-Null
    [IO.File]::WriteAllBytes((Join-Path $Output 'version.json'),$bytes)
    [IO.File]::WriteAllText((Join-Path $Output 'version.json.sig'),[Convert]::ToBase64String($signature),[Text.Encoding]::ASCII)
    Write-Output 'Metadados assinados. Isso não publica a release; publicação estável exige aprovação.'
} finally { $rsa.Dispose(); $expected.Dispose() }
