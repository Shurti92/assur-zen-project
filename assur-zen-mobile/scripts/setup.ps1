<#
.SYNOPSIS
  AssurZen Mobile - préparation du projet Android (Windows).

.DESCRIPTION
  Idempotent : peut être relancé à volonté. Ne supprime rien ; tout fichier
  modifié est d'abord sauvegardé (une seule fois) dans .assurzen-setup-backup\.

    1. détecte Flutter / Dart / Java / Android SDK
    2. génère android\ avec `flutter create` uniquement s'il manque
    3. adapte le manifeste : permissions INTERNET + USE_BIOMETRIC, nom AssurZen,
       <queries> pour url_launcher (sans écraser vos réglages)
    4. MainActivity -> FlutterFragmentActivity, LaunchTheme -> AppCompat (local_auth)
    5. minSdk 24, icônes AssurZen (android_config\res)
    6. flutter pub get, analyze, test, build apk --debug (si l'environnement le permet)

.PARAMETER SkipVerify
  Prépare seulement les fichiers ; pas de pub get / analyze / test / build.

.PARAMETER SkipBuild
  Exécute pub get, analyze et test, mais pas la compilation APK.

.EXAMPLE
  powershell -ExecutionPolicy Bypass -File scripts\setup.ps1
#>
[CmdletBinding()]
param(
  [switch]$SkipVerify,
  [switch]$SkipBuild
)

$ErrorActionPreference = 'Stop'
[Console]::OutputEncoding = [Text.Encoding]::UTF8
$Root = (Resolve-Path (Join-Path $PSScriptRoot '..')).Path
Set-Location $Root

$Utf8NoBom = New-Object Text.UTF8Encoding($false)
$BackupDir = Join-Path $Root '.assurzen-setup-backup'
$Summary = New-Object 'System.Collections.Generic.List[object]'
$MinSdk = 24
$OrgId = 'ci.assurzen'
$ProjectName = 'assurzen_mobile'

# ---------------------------------------------------------------- utilitaires

function Add-Result([string]$Status, [string]$Label, [string]$Detail = '') {
  $Summary.Add([pscustomobject]@{ Status = $Status; Label = $Label; Detail = $Detail })
  $color = switch ($Status) { 'OK' { 'Green' } 'MODIFIE' { 'Cyan' } 'IGNORE' { 'DarkGray' } 'ATTENTION' { 'Yellow' } 'ECHEC' { 'Red' } default { 'White' } }
  $line = '  [{0}] {1}' -f $Status, $Label
  if ($Detail) { $line += ' - ' + $Detail }
  Write-Host $line -ForegroundColor $color
}

function Write-Step([string]$Text) { Write-Host ''; Write-Host "== $Text" -ForegroundColor White }

function Read-Text([string]$Path) { [IO.File]::ReadAllText($Path) }
function Write-Text([string]$Path, [string]$Text) { [IO.File]::WriteAllText($Path, $Text, $Utf8NoBom) }

function Get-RelativePath([string]$Path) {
  $full = (Resolve-Path $Path).Path
  if ($full.StartsWith($Root)) { return $full.Substring($Root.Length).TrimStart('\') }
  return (Split-Path $full -Leaf)
}

# Sauvegarde un fichier une seule fois (jamais écrasée par les relances suivantes).
function Backup-Once([string]$Path) {
  if (-not (Test-Path $Path -PathType Leaf)) { return }
  $dest = Join-Path $BackupDir (Get-RelativePath $Path)
  if (Test-Path $dest) { return }
  New-Item -ItemType Directory -Force -Path (Split-Path $dest) | Out-Null
  Copy-Item $Path $dest
}

function Get-Newline([string]$Text) { if ($Text.Contains("`r`n")) { "`r`n" } else { "`n" } }

function Get-FileHashSafe([string]$Path) { (Get-FileHash -Algorithm SHA256 -Path $Path).Hash }

# Écrit $New dans $Path seulement si le contenu change (après sauvegarde).
function Save-IfChanged([string]$Path, [string]$New, [string]$Label, [string]$Detail) {
  $old = Read-Text $Path
  if ($old -ceq $New) { Add-Result 'OK' $Label 'déjà conforme'; return }
  Backup-Once $Path
  Write-Text $Path $New
  Add-Result 'MODIFIE' $Label $Detail
}

function Find-Flutter {
  $cmd = Get-Command flutter -ErrorAction SilentlyContinue
  if ($cmd) { return $cmd.Source }
  $candidates = @()
  if ($env:FLUTTER_ROOT) { $candidates += $env:FLUTTER_ROOT }
  $candidates += 'C:\flutter', 'C:\src\flutter', 'C:\dev\flutter'
  if ($env:USERPROFILE) { $candidates += (Join-Path $env:USERPROFILE 'flutter'), (Join-Path $env:USERPROFILE 'develop\flutter') }
  if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'flutter') }
  foreach ($c in $candidates) {
    $bat = Join-Path $c 'bin\flutter.bat'
    if (Test-Path $bat) { $env:Path = (Split-Path $bat) + ';' + $env:Path; return $bat }
  }
  return $null
}

function Find-AndroidSdk {
  $candidates = @($env:ANDROID_HOME, $env:ANDROID_SDK_ROOT)
  if ($env:LOCALAPPDATA) { $candidates += (Join-Path $env:LOCALAPPDATA 'Android\Sdk') }
  foreach ($c in $candidates) { if ($c -and (Test-Path (Join-Path $c 'platform-tools'))) { return $c } }
  return $null
}

# Lance une commande Flutter en affichant sa sortie ; renvoie le code de sortie.
function Invoke-Flutter([string[]]$FlutterArgs) {
  Write-Host ("  > flutter " + ($FlutterArgs -join ' ')) -ForegroundColor DarkGray
  & flutter @FlutterArgs | Out-Host    # Out-Host : la sortie ne doit pas polluer la valeur de retour
  return $LASTEXITCODE
}

# ------------------------------------------------- transformations de contenu

function Update-Manifest([string]$Text) {
  $nl = Get-Newline $Text
  $changes = New-Object 'System.Collections.Generic.List[string]'

  $permissions = @(
    @{ Name = 'INTERNET'; Comment = 'Réseau : API AssurZen' },
    @{ Name = 'USE_BIOMETRIC'; Comment = 'Connexion par empreinte (local_auth)' }
  )
  foreach ($p in $permissions) {
    if ($Text -notmatch [regex]::Escape("android.permission.$($p.Name)")) {
      $m = [regex]::Match($Text, '<manifest\b[^>]*>')
      if (-not $m.Success) { throw 'Balise <manifest> introuvable.' }
      $ins = "$nl    <!-- $($p.Comment) -->$nl    <uses-permission android:name=`"android.permission.$($p.Name)`" />"
      $Text = $Text.Insert($m.Index + $m.Length, $ins)
      $changes.Add("permission $($p.Name)")
    }
  }

  if ($Text -match 'android:label="assurzen_mobile"') {
    $Text = $Text -replace 'android:label="assurzen_mobile"', 'android:label="AssurZen"'
    $changes.Add('nom AssurZen')
  }

  if ($Text -notmatch 'android:allowBackup') {
    $Text = [regex]::Replace($Text, '<application\b', "<application$nl        android:allowBackup=`"false`"", 1)
    $changes.Add('allowBackup=false')
  }
  if ($Text -notmatch 'android:screenOrientation') {
    $Text = [regex]::Replace($Text, '<activity\b', "<activity$nl            android:screenOrientation=`"portrait`"", 1)
    $changes.Add('orientation portrait')
  }

  # <queries> : requis sur Android 11+ pour url_launcher (appel, e-mail, web).
  $needed = @(
    @{ Scheme = 'tel'; Action = 'android.intent.action.DIAL' },
    @{ Scheme = 'mailto'; Action = 'android.intent.action.SENDTO' },
    @{ Scheme = 'https'; Action = 'android.intent.action.VIEW' }
  )
  if ($Text -notmatch '<queries>') {
    $block = "$nl    <queries>$nl"
    foreach ($q in $needed) { $block += "        <intent>$nl            <action android:name=`"$($q.Action)`" />$nl            <data android:scheme=`"$($q.Scheme)`" />$nl        </intent>$nl" }
    $block += "    </queries>$nl"
    $idx = $Text.LastIndexOf('</manifest>')
    if ($idx -lt 0) { throw 'Balise </manifest> introuvable.' }
    $Text = $Text.Insert($idx, $block)
    $changes.Add('bloc <queries>')
  } else {
    foreach ($q in $needed) {
      if ($Text -notmatch ('android:scheme="' + $q.Scheme + '"')) {
        $intent = "        <intent>$nl            <action android:name=`"$($q.Action)`" />$nl            <data android:scheme=`"$($q.Scheme)`" />$nl        </intent>$nl    "
        $idx = $Text.LastIndexOf('</queries>')
        $Text = $Text.Insert($idx, $intent)
        $changes.Add("queries $($q.Scheme)")
      }
    }
  }
  return @{ Text = $Text; Changes = $changes }
}

# ============================================================================
Write-Host ''
Write-Host 'AssurZen Mobile - préparation Android' -ForegroundColor White
Write-Host "Projet : $Root"

# ---------------------------------------------------------- 1. Prérequis
Write-Step '1/6  Prérequis'

if (-not (Test-Path (Join-Path $Root 'pubspec.yaml'))) {
  Add-Result 'ECHEC' 'pubspec.yaml' 'introuvable : lancez le script depuis le projet AssurZen'
  exit 1
}

$flutterPath = Find-Flutter
if (-not $flutterPath) {
  Add-Result 'ECHEC' 'Flutter' 'introuvable. Installez-le (https://docs.flutter.dev/get-started/install) ou ajoutez flutter\bin au PATH'
  exit 1
}

$flutterVersion = $null; $dartVersion = $null
try {
  $raw = (& flutter --version --machine | Out-String)
  $json = $raw.Substring($raw.IndexOf('{')) | ConvertFrom-Json
  $flutterVersion = [string]$json.frameworkVersion
  $dartVersion = [string]$json.dartSdkVersion
} catch { }
if (-not $flutterVersion) {
  Add-Result 'ECHEC' 'Flutter' "version illisible (exécutable : $flutterPath). Lancez 'flutter doctor'"
  exit 1
}
Add-Result 'OK' 'Flutter' "$flutterVersion (Dart $dartVersion) - $flutterPath"

# Contrainte SDK de pubspec.yaml (>=X.Y.Z)
$pubspec = Read-Text (Join-Path $Root 'pubspec.yaml')
$mSdk = [regex]::Match($pubspec, "sdk:\s*['""]?>=\s*(\d+\.\d+\.\d+)")
if ($mSdk.Success -and $dartVersion) {
  try {
    if ([version]($dartVersion -replace '[-+].*$', '') -lt [version]$mSdk.Groups[1].Value) {
      Add-Result 'ECHEC' 'Version de Dart' "pubspec.yaml exige Dart >= $($mSdk.Groups[1].Value), vous avez $dartVersion. Lancez 'flutter upgrade'"
      exit 1
    }
  } catch { }
}

function Find-JavaHome {
  $candidates = @()
  foreach ($base in $env:ProgramFiles, $env:LOCALAPPDATA) {
    if ($base) { $candidates += (Join-Path $base 'Android\Android Studio\jbr'), (Join-Path $base 'Programs\Android Studio\jbr') }
  }
  # JDK installés, du plus récent au plus ancien
  foreach ($vendor in 'Java', 'Eclipse Adoptium', 'Microsoft') {
    $dir = Join-Path $env:ProgramFiles $vendor
    if (Test-Path $dir) { $candidates += (Get-ChildItem $dir -Directory -ErrorAction SilentlyContinue | Sort-Object Name -Descending | ForEach-Object FullName) }
  }
  foreach ($c in $candidates) { if (Test-Path (Join-Path $c 'bin\java.exe')) { return $c } }
  return $null
}

$javaOk = $false
$javaHome = $env:JAVA_HOME
if ($javaHome -and (Test-Path (Join-Path $javaHome 'bin\java.exe'))) {
  $javaOk = $true; Add-Result 'OK' 'Java' "JAVA_HOME=$javaHome"
} else {
  # Gradle exige un JAVA_HOME valide s'il est défini : on le corrige pour cette session seulement.
  $found = Find-JavaHome
  if ($javaHome -and $found) {
    $env:JAVA_HOME = $found; $javaOk = $true
    Add-Result 'ATTENTION' 'JAVA_HOME' "invalide ($javaHome) : $found utilisé pour cette session. Corrigez la variable d'environnement."
  } elseif ($found) {
    $env:JAVA_HOME = $found; $javaOk = $true
    Add-Result 'OK' 'Java' "JDK détecté : $found"
  } elseif ($javaHome) {
    Add-Result 'ATTENTION' 'JAVA_HOME' "invalide ($javaHome) et aucun JDK trouvé : la compilation Android sera ignorée"
  } elseif (Get-Command java -ErrorAction SilentlyContinue) {
    $javaOk = $true; Add-Result 'OK' 'Java' 'trouvé dans le PATH'
  } else {
    Add-Result 'ATTENTION' 'Java (JDK)' 'introuvable : la compilation Android sera ignorée (Android Studio embarque un JDK)'
  }
}

# `flutter config --jdk-dir` prime sur JAVA_HOME : s'il est invalide, Gradle échoue.
$jdkBlocked = $false
$jdkLine = (& flutter config --list | Out-String) -split "`r?`n" | Where-Object { $_ -match '^\s*jdk-dir:\s*(.+?)\s*$' } | Select-Object -First 1
if ($jdkLine -and $jdkLine -match '^\s*jdk-dir:\s*(.+?)\s*$') {
  $cfgJdk = $Matches[1]
  if (Test-Path (Join-Path $cfgJdk 'bin\java.exe')) { Add-Result 'OK' 'flutter config jdk-dir' $cfgJdk }
  else {
    $jdkBlocked = $true
    $fix = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME 'bin\java.exe'))) { $env:JAVA_HOME } else { '<chemin du JDK>' }
    Add-Result 'ATTENTION' 'flutter config jdk-dir' "invalide ($cfgJdk) : la compilation échouerait. Corrigez avec : flutter config --jdk-dir `"$fix`""
  }
}

$sdk = Find-AndroidSdk
if ($sdk) { Add-Result 'OK' 'Android SDK' $sdk }
else { Add-Result 'ATTENTION' 'Android SDK' 'introuvable (ANDROID_HOME) : la compilation Android sera ignorée' }

# ------------------------------------------------- 2. Génération de android\
Write-Step '2/6  Dossier android\'

$android = Join-Path $Root 'android'
function Test-AndroidComplete {
  $hasSettings = (Test-Path (Join-Path $android 'settings.gradle')) -or (Test-Path (Join-Path $android 'settings.gradle.kts'))
  $hasAppGradle = (Test-Path (Join-Path $android 'app\build.gradle')) -or (Test-Path (Join-Path $android 'app\build.gradle.kts'))
  return $hasSettings -and $hasAppGradle -and
    (Test-Path (Join-Path $android 'gradle\wrapper\gradle-wrapper.properties')) -and
    (Test-Path (Join-Path $android 'app\src\main\AndroidManifest.xml'))
}

if (Test-AndroidComplete) {
  Add-Result 'OK' 'android\' 'déjà présent : conservé tel quel (aucune régénération)'
} else {
  if (Test-Path $android) {
    # android\ incomplet : copie de sécurité avant que flutter create ne complète.
    $stamp = Get-Date -Format 'yyyyMMdd-HHmmss'
    $dest = Join-Path $BackupDir "android-incomplet-$stamp"
    New-Item -ItemType Directory -Force -Path $dest | Out-Null
    Copy-Item (Join-Path $android '*') $dest -Recurse -Force
    Add-Result 'ATTENTION' 'android\' "incomplet : copie de sécurité dans .assurzen-setup-backup\android-incomplet-$stamp"
  }

  # Garde-fou : flutter create ne doit toucher ni à vos sources ni à votre config.
  $guarded = 'pubspec.yaml', 'analysis_options.yaml', 'lib\main.dart', 'test\widget_test.dart', 'README.md', '.gitignore'
  $snapshot = @{}
  foreach ($g in $guarded) { $p = Join-Path $Root $g; if (Test-Path $p) { $snapshot[$g] = [IO.File]::ReadAllBytes($p) } }

  # `flutter create .` sur un projet existant génère uniquement android\ (--platforms android),
  # avec le modèle correspondant à la version de Flutter détectée (Groovy ou Kotlin DSL).
  $code = Invoke-Flutter @('create', '.', '--org', $OrgId, '--project-name', $ProjectName, '--platforms', 'android')
  if ($code -ne 0) {
    Add-Result 'ECHEC' 'flutter create' "code $code. Lancez 'flutter doctor' puis relancez le script"
    exit 1
  }

  foreach ($g in $snapshot.Keys) {
    $p = Join-Path $Root $g
    $now = if (Test-Path $p) { [IO.File]::ReadAllBytes($p) } else { $null }
    $same = $now -and ($now.Length -eq $snapshot[$g].Length) -and (-not (Compare-Object $now $snapshot[$g] -SyncWindow 0))
    if (-not $same) {
      [IO.File]::WriteAllBytes($p, $snapshot[$g])
      Add-Result 'ATTENTION' $g 'modifié par flutter create : version d''origine restaurée'
    }
  }
  if (-not (Test-AndroidComplete)) { Add-Result 'ECHEC' 'android\' 'génération incomplète'; exit 1 }
  Add-Result 'MODIFIE' 'android\' "généré pour Flutter $flutterVersion"
}

# ------------------------------------------------------- 3. Manifeste
Write-Step '3/6  AndroidManifest.xml'

$manifestPath = Join-Path $android 'app\src\main\AndroidManifest.xml'
$res = Update-Manifest (Read-Text $manifestPath)
Save-IfChanged $manifestPath $res.Text 'AndroidManifest.xml' ($res.Changes -join ', ')

# ------------------------------------------- 4. Activité + thème (local_auth)
Write-Step '4/6  Compatibilité biométrique (local_auth)'

$mainDir = Join-Path $android 'app\src\main'
$activity = Get-ChildItem -Path $mainDir -Recurse -Include 'MainActivity.kt', 'MainActivity.java' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($activity) {
  $code = Read-Text $activity.FullName
  if ($code -match 'FlutterFragmentActivity') {
    Add-Result 'OK' $activity.Name 'FlutterFragmentActivity déjà utilisé'
  } elseif ($code -match 'FlutterActivity') {
    Save-IfChanged $activity.FullName ($code -replace 'FlutterActivity', 'FlutterFragmentActivity') $activity.Name 'FlutterActivity -> FlutterFragmentActivity (requis par local_auth)'
  } else {
    Add-Result 'ATTENTION' $activity.Name 'classe de base non reconnue : utilisez FlutterFragmentActivity manuellement'
  }
} else {
  $pkg = "$OrgId.$ProjectName"
  $gradleFile = @('app\build.gradle.kts', 'app\build.gradle') | ForEach-Object { Join-Path $android $_ } | Where-Object { Test-Path $_ } | Select-Object -First 1
  if ($gradleFile) {
    $mNs = [regex]::Match((Read-Text $gradleFile), 'namespace\s*=?\s*["'']([\w.]+)["'']')
    if ($mNs.Success) { $pkg = $mNs.Groups[1].Value }
  }
  $dir = Join-Path $mainDir ('kotlin\' + ($pkg -replace '\.', '\'))
  New-Item -ItemType Directory -Force -Path $dir | Out-Null
  $src = Join-Path $Root 'android_config\MainActivity.kt'
  $body = if (Test-Path $src) { (Read-Text $src) -replace '(?m)^package\s+[\w.]+', "package $pkg" }
          else { "package $pkg`n`nimport io.flutter.embedding.android.FlutterFragmentActivity`n`nclass MainActivity : FlutterFragmentActivity()`n" }
  Write-Text (Join-Path $dir 'MainActivity.kt') $body
  Add-Result 'MODIFIE' 'MainActivity.kt' "créé (package $pkg, FlutterFragmentActivity)"
}

# LaunchTheme : parent AppCompat obligatoire (sinon plantage Android 8 et moins).
$themeCount = 0
foreach ($rel in 'values\styles.xml', 'values-night\styles.xml') {
  $p = Join-Path $android "app\src\main\res\$rel"
  if (-not (Test-Path $p)) { continue }
  $themeCount++
  $t = Read-Text $p
  $n = [regex]::Replace($t, '(<style\s+name="LaunchTheme"\s+parent=")@android:style/Theme\.[\w.]*(")', '${1}Theme.AppCompat.DayNight${2}')
  Save-IfChanged $p $n "res\$rel" 'LaunchTheme -> Theme.AppCompat.DayNight'
}
if ($themeCount -eq 0) { Add-Result 'ATTENTION' 'styles.xml' 'introuvable : vérifiez que LaunchTheme hérite de Theme.AppCompat' }

# ------------------------------------------------- 5. minSdk + icônes
Write-Step '5/6  minSdk et icônes'

$gradleDone = $false
foreach ($rel in 'app\build.gradle.kts', 'app\build.gradle') {
  $p = Join-Path $android $rel
  if (-not (Test-Path $p)) { continue }
  $t = Read-Text $p
  $n = $t -replace 'minSdk\s*=\s*flutter\.minSdkVersion', "minSdk = $MinSdk"
  $n = $n -replace 'minSdkVersion\s+flutter\.minSdkVersion', "minSdkVersion $MinSdk"
  if ($n -cne $t) { Save-IfChanged $p $n $rel "minSdk = $MinSdk (Android 7.0)" }
  elseif ($t -match "minSdk(Version)?\s*=?\s*$MinSdk\b") { Add-Result 'OK' $rel "minSdk déjà à $MinSdk" }
  else { Add-Result 'OK' $rel 'minSdk personnalisé : conservé' }
  $gradleDone = $true
}
if (-not $gradleDone) { Add-Result 'ATTENTION' 'build.gradle' 'introuvable : minSdk non vérifié' }

# Projet et cache pub sur deux lecteurs (ex. D: et C:) : le cache incrémental de Kotlin échoue.
$gp = Join-Path $android 'gradle.properties'
if (Test-Path $gp) {
  $gpText = Read-Text $gp
  if ($gpText -match '(?m)^\s*kotlin\.incremental\s*=') { Add-Result 'OK' 'gradle.properties' 'kotlin.incremental déjà défini : conservé' }
  else {
    Backup-Once $gp
    $nl = Get-Newline $gpText
    if ($gpText.Length -gt 0 -and -not $gpText.EndsWith("`n")) { $gpText += $nl }
    Write-Text $gp ($gpText + "# Projet et cache pub sur deux lecteurs : le cache incremental de Kotlin echoue${nl}kotlin.incremental=false${nl}")
    Add-Result 'MODIFIE' 'gradle.properties' 'kotlin.incremental=false'
  }
} else { Add-Result 'ATTENTION' 'gradle.properties' 'introuvable : kotlin.incremental non configuré' }

$iconSrcRoot = Join-Path $Root 'android_config\res'
$iconsChanged = 0; $iconsOk = 0; $iconsMissing = 0
foreach ($d in 'mdpi', 'hdpi', 'xhdpi', 'xxhdpi', 'xxxhdpi') {
  $src = Join-Path $iconSrcRoot "mipmap-$d\ic_launcher.png"
  if (-not (Test-Path $src)) { $iconsMissing++; continue }
  $destDir = Join-Path $android "app\src\main\res\mipmap-$d"
  New-Item -ItemType Directory -Force -Path $destDir | Out-Null
  $dest = Join-Path $destDir 'ic_launcher.png'
  if ((Test-Path $dest) -and ((Get-FileHashSafe $src) -eq (Get-FileHashSafe $dest))) { $iconsOk++; continue }
  Backup-Once $dest
  Copy-Item $src $dest -Force
  $iconsChanged++
}
if ($iconsMissing -eq 5) { Add-Result 'IGNORE' 'Icône AssurZen' 'android_config\res introuvable : icône Flutter par défaut conservée' }
elseif ($iconsChanged -gt 0) { Add-Result 'MODIFIE' 'Icône AssurZen' "$iconsChanged densité(s) mise(s) à jour" }
else { Add-Result 'OK' 'Icône AssurZen' 'déjà en place' }
if ($iconsMissing -gt 0 -and $iconsMissing -lt 5) { Add-Result 'ATTENTION' 'Icône AssurZen' "$iconsMissing densité(s) manquante(s) dans android_config\res" }

# .gitignore : ne pas versionner les sauvegardes
$gi = Join-Path $Root '.gitignore'
if (Test-Path $gi) {
  $giText = Read-Text $gi
  if ($giText -notmatch '(?m)^\.assurzen-setup-backup/?\s*$') {
    $nl = Get-Newline $giText
    if (-not $giText.EndsWith("`n")) { $giText += $nl }
    Write-Text $gi ($giText + "$nl# Sauvegardes du script scripts/setup$nl.assurzen-setup-backup/$nl")
    Add-Result 'MODIFIE' '.gitignore' 'sauvegardes du script exclues'
  }
}

# ---------------------------------------------------------- 6. Vérifications
Write-Step '6/6  Vérifications'

if ($SkipVerify) {
  Add-Result 'IGNORE' 'Vérifications' 'désactivées (-SkipVerify)'
} else {
  $code = Invoke-Flutter @('pub', 'get')
  if ($code -eq 0) { Add-Result 'OK' 'flutter pub get' } else { Add-Result 'ECHEC' 'flutter pub get' "code $code" }

  if ($code -eq 0) {
    $a = Invoke-Flutter @('analyze')
    if ($a -eq 0) { Add-Result 'OK' 'flutter analyze' 'aucun problème' }
    else { Add-Result 'ATTENTION' 'flutter analyze' "des problèmes sont signalés (code $a) : voir la sortie ci-dessus" }

    if (Test-Path (Join-Path $Root 'test')) {
      $t = Invoke-Flutter @('test')
      if ($t -eq 0) { Add-Result 'OK' 'flutter test' 'tests réussis' } else { Add-Result 'ECHEC' 'flutter test' "code $t" }
    } else { Add-Result 'IGNORE' 'flutter test' 'dossier test\ absent' }

    if ($SkipBuild) { Add-Result 'IGNORE' 'Compilation APK' 'désactivée (-SkipBuild)' }
    elseif (-not ($javaOk -and $sdk)) { Add-Result 'IGNORE' 'Compilation APK' 'JDK ou Android SDK manquant' }
    elseif ($jdkBlocked) { Add-Result 'IGNORE' 'Compilation APK' 'ignorée : jdk-dir de Flutter invalide (voir plus haut)' }
    else {
      $b = Invoke-Flutter @('build', 'apk', '--debug')
      if ($b -eq 0) { Add-Result 'OK' 'flutter build apk --debug' 'build\app\outputs\flutter-apk\app-debug.apk' }
      else { Add-Result 'ECHEC' 'flutter build apk --debug' "code $b (essayez 'flutter doctor --android-licenses')" }
    }
  }
}

# ------------------------------------------------------------------ Résumé
Write-Host ''
Write-Host '================ Résumé ================' -ForegroundColor White
foreach ($g in $Summary | Group-Object Status) { Write-Host ('  {0,-10} {1}' -f $g.Name, $g.Count) }
$failed = @($Summary | Where-Object { $_.Status -eq 'ECHEC' })
Write-Host ''
if ($failed.Count -gt 0) {
  Write-Host 'Terminé avec des échecs :' -ForegroundColor Red
  $failed | ForEach-Object { Write-Host ("  - {0} : {1}" -f $_.Label, $_.Detail) -ForegroundColor Red }
  exit 1
}
if (Test-Path $BackupDir) { Write-Host "Sauvegardes des fichiers modifiés : $BackupDir" }
Write-Host 'Terminé. Lancez l''application avec :  flutter run' -ForegroundColor Green
