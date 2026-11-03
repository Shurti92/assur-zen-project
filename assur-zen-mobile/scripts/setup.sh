#!/usr/bin/env sh
# AssurZen Mobile — préparation du projet Android (macOS / Linux).
#
# Idempotent : peut être relancé à volonté. Ne supprime rien ; tout fichier
# modifié est d'abord sauvegardé (une seule fois) dans .assurzen-setup-backup/.
#
#   1. détecte Flutter / Dart / Java / Android SDK
#   2. génère android/ avec `flutter create` uniquement s'il manque
#   3. adapte le manifeste : permissions INTERNET + USE_BIOMETRIC, nom AssurZen,
#      <queries> pour url_launcher (sans écraser vos réglages)
#   4. MainActivity -> FlutterFragmentActivity, LaunchTheme -> AppCompat (local_auth)
#   5. minSdk 24, icônes AssurZen (android_config/res)
#   6. flutter pub get, analyze, test, build apk --debug (si l'environnement le permet)
#
# Usage :  sh scripts/setup.sh [--skip-verify] [--skip-build]

set -u

SKIP_VERIFY=0
SKIP_BUILD=0
for arg in "$@"; do
  case "$arg" in
    --skip-verify) SKIP_VERIFY=1 ;;
    --skip-build) SKIP_BUILD=1 ;;
    -h|--help) sed -n '2,15p' "$0"; exit 0 ;;
    *) echo "Option inconnue : $arg (voir --help)"; exit 2 ;;
  esac
done

ROOT=$(cd "$(dirname "$0")/.." && pwd)
cd "$ROOT" || exit 1
BACKUP_DIR="$ROOT/.assurzen-setup-backup"
ANDROID="$ROOT/android"
MIN_SDK=24
ORG_ID="ci.assurzen"
PROJECT_NAME="assurzen_mobile"
TMP=$(mktemp -d 2>/dev/null || mktemp -d -t assurzen)
trap 'rm -rf "$TMP"' EXIT INT TERM

# ------------------------------------------------------------------ résumé
RESULTS="$TMP/results"
: > "$RESULTS"
if [ -t 1 ]; then
  C_OK=$(printf '\033[32m'); C_MOD=$(printf '\033[36m'); C_SKIP=$(printf '\033[90m')
  C_WARN=$(printf '\033[33m'); C_FAIL=$(printf '\033[31m'); C_OFF=$(printf '\033[0m')
else
  C_OK=''; C_MOD=''; C_SKIP=''; C_WARN=''; C_FAIL=''; C_OFF=''
fi

# add_result STATUS LABEL [DETAIL]
add_result() {
  status=$1; label=$2; detail=${3:-}
  printf '%s\n' "$status" >> "$RESULTS"
  case "$status" in
    OK) color=$C_OK ;; MODIFIE) color=$C_MOD ;; IGNORE) color=$C_SKIP ;;
    ATTENTION) color=$C_WARN ;; ECHEC) color=$C_FAIL ;; *) color='' ;;
  esac
  if [ -n "$detail" ]; then
    printf '%s  [%s] %s - %s%s\n' "$color" "$status" "$label" "$detail" "$C_OFF"
  else
    printf '%s  [%s] %s%s\n' "$color" "$status" "$label" "$C_OFF"
  fi
}

step() { printf '\n== %s\n' "$1"; }

# ---------------------------------------------------------------- utilitaires

# backup_once FILE : copie FILE dans .assurzen-setup-backup/ une seule fois.
backup_once() {
  # Variables préfixées bk_ : le shell POSIX n'a pas de variables locales.
  [ -f "$1" ] || return 0
  bk_rel=${1#"$ROOT"/}
  bk_dest="$BACKUP_DIR/$bk_rel"
  [ -e "$bk_dest" ] && return 0
  mkdir -p "$(dirname "$bk_dest")"
  cp "$1" "$bk_dest"
}

# save_if_changed FILE NEWFILE LABEL DETAIL : remplace FILE par NEWFILE s'ils diffèrent.
save_if_changed() {
  if cmp -s "$1" "$2"; then
    add_result OK "$3" "déjà conforme"
  else
    backup_once "$1"
    cat "$2" > "$1"      # conserve les permissions du fichier d'origine
    add_result MODIFIE "$3" "$4"
  fi
}

# ver_ge A B : vrai si version A >= version B (x.y.z).
ver_ge() {
  awk -v a="$1" -v b="$2" 'BEGIN {
    na = split(a, x, "."); nb = split(b, y, ".");
    for (i = 1; i <= 3; i++) { xi = x[i] + 0; yi = y[i] + 0;
      if (xi > yi) exit 0; if (xi < yi) exit 1 }
    exit 0 }'
}

find_flutter() {
  if command -v flutter >/dev/null 2>&1; then command -v flutter; return 0; fi
  for d in "${FLUTTER_ROOT:-}" "$HOME/flutter" "$HOME/development/flutter" "$HOME/develop/flutter" \
           "$HOME/fvm/default" /opt/flutter /usr/local/flutter /usr/local/share/flutter "$HOME/snap/flutter/common/flutter"; do
    if [ -n "$d" ] && [ -x "$d/bin/flutter" ]; then
      PATH="$d/bin:$PATH"; export PATH
      echo "$d/bin/flutter"; return 0
    fi
  done
  return 1
}

find_android_sdk() {
  for d in "${ANDROID_HOME:-}" "${ANDROID_SDK_ROOT:-}" "$HOME/Library/Android/sdk" "$HOME/Android/Sdk"; do
    if [ -n "$d" ] && [ -d "$d/platform-tools" ]; then echo "$d"; return 0; fi
  done
  return 1
}

# ---------------------------------------------------- transformations de texte

# manifest_insert_after_open FILE LINE : insère LINE après la balise <manifest ...>.
manifest_insert_after_open() {
  awk -v ins="$2" '
    { print }
    !done && /<manifest/ { open = 1 }
    open && !done && />/ { print ins; done = 1 }
  ' "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

# insert_before_last FILE MARKER TEXT : insère TEXT avant la dernière occurrence de MARKER.
insert_before_last() {
  awk -v marker="$2" -v ins="$3" '
    { lines[NR] = $0; if (index($0, marker)) last = NR }
    END { for (i = 1; i <= NR; i++) { if (i == last) print ins; print lines[i] } }
  ' "$1" > "$1.tmp" && mv "$1.tmp" "$1"
}

# patch_manifest FILE : applique les modifications AssurZen, liste les changements dans $CHANGES.
patch_manifest() {
  f=$1; CHANGES=''
  add_change() { CHANGES="${CHANGES:+$CHANGES, }$1"; }

  if ! grep -q 'android.permission.INTERNET' "$f"; then
    manifest_insert_after_open "$f" '    <!-- Réseau : API AssurZen -->\n    <uses-permission android:name="android.permission.INTERNET" />'
    add_change 'permission INTERNET'
  fi
  if ! grep -q 'android.permission.USE_BIOMETRIC' "$f"; then
    manifest_insert_after_open "$f" '    <!-- Connexion par empreinte (local_auth) -->\n    <uses-permission android:name="android.permission.USE_BIOMETRIC" />'
    add_change 'permission USE_BIOMETRIC'
  fi

  if grep -q 'android:label="assurzen_mobile"' "$f"; then
    sed 's/android:label="assurzen_mobile"/android:label="AssurZen"/' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
    add_change 'nom AssurZen'
  fi
  if ! grep -q 'android:allowBackup' "$f"; then
    awk '!d && /<application/ { sub(/<application/, "<application\n        android:allowBackup=\"false\""); d = 1 } { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
    add_change 'allowBackup=false'
  fi
  if ! grep -q 'android:screenOrientation' "$f"; then
    awk '!d && /<activity/ { sub(/<activity/, "<activity\n            android:screenOrientation=\"portrait\""); d = 1 } { print }' "$f" > "$f.tmp" && mv "$f.tmp" "$f"
    add_change 'orientation portrait'
  fi

  # <queries> : requis sur Android 11+ pour url_launcher (appel, e-mail, web).
  if ! grep -q '<queries>' "$f"; then
    insert_before_last "$f" '</manifest>' '    <queries>\n        <intent>\n            <action android:name="android.intent.action.DIAL" />\n            <data android:scheme="tel" />\n        </intent>\n        <intent>\n            <action android:name="android.intent.action.SENDTO" />\n            <data android:scheme="mailto" />\n        </intent>\n        <intent>\n            <action android:name="android.intent.action.VIEW" />\n            <data android:scheme="https" />\n        </intent>\n    </queries>\n'
    add_change 'bloc <queries>'
  else
    for pair in 'tel:android.intent.action.DIAL' 'mailto:android.intent.action.SENDTO' 'https:android.intent.action.VIEW'; do
      scheme=${pair%%:*}; action=${pair#*:}
      if ! grep -q "android:scheme=\"$scheme\"" "$f"; then
        insert_before_last "$f" '</queries>' "        <intent>\n            <action android:name=\"$action\" />\n            <data android:scheme=\"$scheme\" />\n        </intent>"
        add_change "queries $scheme"
      fi
    done
  fi
}

android_complete() {
  { [ -f "$ANDROID/settings.gradle" ] || [ -f "$ANDROID/settings.gradle.kts" ]; } &&
  { [ -f "$ANDROID/app/build.gradle" ] || [ -f "$ANDROID/app/build.gradle.kts" ]; } &&
  [ -f "$ANDROID/gradle/wrapper/gradle-wrapper.properties" ] &&
  [ -f "$ANDROID/app/src/main/AndroidManifest.xml" ]
}

# ============================================================================
printf '\nAssurZen Mobile - préparation Android\nProjet : %s\n' "$ROOT"

# ---------------------------------------------------------- 1. Prérequis
step "1/6  Prérequis"

if [ ! -f pubspec.yaml ]; then
  add_result ECHEC pubspec.yaml "introuvable : lancez le script depuis le projet AssurZen"
  exit 1
fi

FLUTTER_BIN=$(find_flutter) || {
  add_result ECHEC Flutter "introuvable. Installez-le (https://docs.flutter.dev/get-started/install) ou ajoutez flutter/bin au PATH"
  exit 1
}

MACHINE=$(flutter --version --machine 2>/dev/null)
FLUTTER_VERSION=$(printf '%s\n' "$MACHINE" | sed -n 's/.*"frameworkVersion"[^"]*"\([^"]*\)".*/\1/p' | head -n 1)
DART_VERSION=$(printf '%s\n' "$MACHINE" | sed -n 's/.*"dartSdkVersion"[^"]*"\([^"]*\)".*/\1/p' | head -n 1)
if [ -z "$FLUTTER_VERSION" ]; then
  add_result ECHEC Flutter "version illisible ($FLUTTER_BIN). Lancez 'flutter doctor'"
  exit 1
fi
add_result OK Flutter "$FLUTTER_VERSION (Dart ${DART_VERSION:-?}) - $FLUTTER_BIN"

# Contrainte SDK de pubspec.yaml (>=X.Y.Z)
MIN_DART=$(sed -n "s/^[[:space:]]*sdk:[[:space:]]*['\"]*>=[[:space:]]*\([0-9][0-9.]*\).*/\1/p" pubspec.yaml | head -n 1)
DART_BASE=$(printf '%s' "$DART_VERSION" | sed 's/[-+].*$//')
if [ -n "$MIN_DART" ] && [ -n "$DART_BASE" ] && ! ver_ge "$DART_BASE" "$MIN_DART"; then
  add_result ECHEC "Version de Dart" "pubspec.yaml exige Dart >= $MIN_DART, vous avez $DART_VERSION. Lancez 'flutter upgrade'"
  exit 1
fi

find_java_home() {
  for d in "/Applications/Android Studio.app/Contents/jbr/Contents/Home" "$HOME/android-studio/jbr" \
           /opt/android-studio/jbr /usr/local/android-studio/jbr; do
    [ -x "$d/bin/java" ] && { echo "$d"; return 0; }
  done
  if [ -x /usr/libexec/java_home ]; then
    d=$(/usr/libexec/java_home 2>/dev/null) && [ -x "$d/bin/java" ] && { echo "$d"; return 0; }
  fi
  return 1
}

JAVA_OK=0
if [ -n "${JAVA_HOME:-}" ] && [ -x "$JAVA_HOME/bin/java" ]; then
  JAVA_OK=1; add_result OK Java "JAVA_HOME=$JAVA_HOME"
else
  # Gradle exige un JAVA_HOME valide s'il est défini : on le corrige pour cette session seulement.
  BAD_JAVA_HOME=${JAVA_HOME:-}
  FOUND_JAVA=$(find_java_home) || FOUND_JAVA=''
  if [ -n "$FOUND_JAVA" ]; then
    JAVA_HOME=$FOUND_JAVA; export JAVA_HOME; JAVA_OK=1
    if [ -n "$BAD_JAVA_HOME" ]; then
      add_result ATTENTION JAVA_HOME "invalide ($BAD_JAVA_HOME) : $FOUND_JAVA utilisé pour cette session. Corrigez la variable d'environnement."
    else
      add_result OK Java "JDK détecté : $FOUND_JAVA"
    fi
  elif [ -n "$BAD_JAVA_HOME" ]; then
    unset JAVA_HOME
    if command -v java >/dev/null 2>&1; then
      JAVA_OK=1; add_result ATTENTION JAVA_HOME "invalide ($BAD_JAVA_HOME) : ignoré, java du PATH utilisé"
    else
      add_result ATTENTION JAVA_HOME "invalide ($BAD_JAVA_HOME) et aucun JDK trouvé : la compilation Android sera ignorée"
    fi
  elif command -v java >/dev/null 2>&1; then
    JAVA_OK=1; add_result OK Java "trouvé dans le PATH"
  else
    add_result ATTENTION "Java (JDK)" "introuvable : la compilation Android sera ignorée (Android Studio embarque un JDK)"
  fi
fi

# `flutter config --jdk-dir` prime sur JAVA_HOME : s'il est invalide, Gradle échoue.
JDK_BLOCKED=0
CFG_JDK=$(flutter config --list 2>/dev/null | sed -n 's/^[[:space:]]*jdk-dir:[[:space:]]*\(.*[^[:space:]]\)[[:space:]]*$/\1/p' | head -n 1)
if [ -n "$CFG_JDK" ]; then
  if [ -x "$CFG_JDK/bin/java" ]; then
    add_result OK "flutter config jdk-dir" "$CFG_JDK"
  else
    JDK_BLOCKED=1
    fix=${JAVA_HOME:-<chemin du JDK>}
    add_result ATTENTION "flutter config jdk-dir" "invalide ($CFG_JDK) : la compilation échouerait. Corrigez avec : flutter config --jdk-dir \"$fix\""
  fi
fi

SDK=$(find_android_sdk) || SDK=''
if [ -n "$SDK" ]; then add_result OK "Android SDK" "$SDK"
else add_result ATTENTION "Android SDK" "introuvable (ANDROID_HOME) : la compilation Android sera ignorée"; fi

# ------------------------------------------------- 2. Génération de android/
step "2/6  Dossier android/"

if android_complete; then
  add_result OK "android/" "déjà présent : conservé tel quel (aucune régénération)"
else
  if [ -d "$ANDROID" ]; then
    # android/ incomplet : copie de sécurité avant que flutter create ne complète.
    stamp=$(date +%Y%m%d-%H%M%S)
    mkdir -p "$BACKUP_DIR/android-incomplet-$stamp"
    cp -R "$ANDROID/." "$BACKUP_DIR/android-incomplet-$stamp/"
    add_result ATTENTION "android/" "incomplet : copie de sécurité dans .assurzen-setup-backup/android-incomplet-$stamp"
  fi

  # Garde-fou : flutter create ne doit toucher ni à vos sources ni à votre config.
  GUARDED='pubspec.yaml analysis_options.yaml lib/main.dart test/widget_test.dart README.md .gitignore'
  mkdir -p "$TMP/guard"
  for g in $GUARDED; do
    if [ -f "$g" ]; then mkdir -p "$TMP/guard/$(dirname "$g")"; cp -p "$g" "$TMP/guard/$g"; fi
  done

  # `flutter create .` sur un projet existant génère uniquement android/ (--platforms android),
  # avec le modèle correspondant à la version de Flutter détectée (Groovy ou Kotlin DSL).
  printf '  > flutter create . --org %s --project-name %s --platforms android\n' "$ORG_ID" "$PROJECT_NAME"
  if ! flutter create . --org "$ORG_ID" --project-name "$PROJECT_NAME" --platforms android; then
    add_result ECHEC "flutter create" "échec. Lancez 'flutter doctor' puis relancez le script"
    exit 1
  fi

  for g in $GUARDED; do
    if [ -f "$TMP/guard/$g" ] && ! cmp -s "$TMP/guard/$g" "$g"; then
      cat "$TMP/guard/$g" > "$g"
      add_result ATTENTION "$g" "modifié par flutter create : version d'origine restaurée"
    fi
  done
  if ! android_complete; then add_result ECHEC "android/" "génération incomplète"; exit 1; fi
  add_result MODIFIE "android/" "généré pour Flutter $FLUTTER_VERSION"
fi

# ------------------------------------------------------- 3. Manifeste
step "3/6  AndroidManifest.xml"

MANIFEST="$ANDROID/app/src/main/AndroidManifest.xml"
cp "$MANIFEST" "$TMP/manifest.new"
patch_manifest "$TMP/manifest.new"
save_if_changed "$MANIFEST" "$TMP/manifest.new" "AndroidManifest.xml" "$CHANGES"

# ------------------------------------------- 4. Activité + thème (local_auth)
step "4/6  Compatibilité biométrique (local_auth)"

MAIN_DIR="$ANDROID/app/src/main"
ACTIVITY=$(find "$MAIN_DIR" \( -name MainActivity.kt -o -name MainActivity.java \) 2>/dev/null | head -n 1)
if [ -n "$ACTIVITY" ]; then
  name=$(basename "$ACTIVITY")
  if grep -q 'FlutterFragmentActivity' "$ACTIVITY"; then
    add_result OK "$name" "FlutterFragmentActivity déjà utilisé"
  elif grep -q 'FlutterActivity' "$ACTIVITY"; then
    sed 's/FlutterActivity/FlutterFragmentActivity/g' "$ACTIVITY" > "$TMP/activity.new"
    save_if_changed "$ACTIVITY" "$TMP/activity.new" "$name" "FlutterActivity -> FlutterFragmentActivity (requis par local_auth)"
  else
    add_result ATTENTION "$name" "classe de base non reconnue : utilisez FlutterFragmentActivity manuellement"
  fi
else
  PKG="$ORG_ID.$PROJECT_NAME"
  GRADLE_APP=''
  for g in "$ANDROID/app/build.gradle.kts" "$ANDROID/app/build.gradle"; do [ -f "$g" ] && { GRADLE_APP=$g; break; }; done
  if [ -n "$GRADLE_APP" ]; then
    ns=$(sed -n "s/.*namespace[[:space:]]*=\{0,1\}[[:space:]]*[\"']\([A-Za-z0-9_.]*\)[\"'].*/\1/p" "$GRADLE_APP" | head -n 1)
    [ -n "$ns" ] && PKG=$ns
  fi
  dir="$MAIN_DIR/kotlin/$(printf '%s' "$PKG" | tr . /)"
  mkdir -p "$dir"
  {
    printf 'package %s\n\n' "$PKG"
    printf 'import io.flutter.embedding.android.FlutterFragmentActivity\n\n'
    printf 'class MainActivity : FlutterFragmentActivity()\n'
  } > "$dir/MainActivity.kt"
  add_result MODIFIE "MainActivity.kt" "créé (package $PKG, FlutterFragmentActivity)"
fi

# LaunchTheme : parent AppCompat obligatoire (sinon plantage Android 8 et moins).
theme_count=0
for rel in values/styles.xml values-night/styles.xml; do
  p="$MAIN_DIR/res/$rel"
  [ -f "$p" ] || continue
  theme_count=$((theme_count + 1))
  sed 's#\(<style[[:space:]]*name="LaunchTheme"[[:space:]]*parent="\)@android:style/Theme\.[A-Za-z0-9_.]*"#\1Theme.AppCompat.DayNight"#' "$p" > "$TMP/styles.new"
  save_if_changed "$p" "$TMP/styles.new" "res/$rel" "LaunchTheme -> Theme.AppCompat.DayNight"
done
[ "$theme_count" -eq 0 ] && add_result ATTENTION "styles.xml" "introuvable : vérifiez que LaunchTheme hérite de Theme.AppCompat"

# ------------------------------------------------- 5. minSdk + icônes
step "5/6  minSdk et icônes"

gradle_done=0
for rel in app/build.gradle.kts app/build.gradle; do
  p="$ANDROID/$rel"
  [ -f "$p" ] || continue
  sed -e "s/minSdk[[:space:]]*=[[:space:]]*flutter\.minSdkVersion/minSdk = $MIN_SDK/" \
      -e "s/minSdkVersion[[:space:]][[:space:]]*flutter\.minSdkVersion/minSdkVersion $MIN_SDK/" "$p" > "$TMP/gradle.new"
  if ! cmp -s "$p" "$TMP/gradle.new"; then
    save_if_changed "$p" "$TMP/gradle.new" "$rel" "minSdk = $MIN_SDK (Android 7.0)"
  elif grep -Eq "minSdk(Version)?[[:space:]]*=?[[:space:]]*$MIN_SDK([^0-9]|$)" "$p"; then
    add_result OK "$rel" "minSdk déjà à $MIN_SDK"
  else
    add_result OK "$rel" "minSdk personnalisé : conservé"
  fi
  gradle_done=1
done
[ "$gradle_done" -eq 0 ] && add_result ATTENTION "build.gradle" "introuvable : minSdk non vérifié"

# Projet et cache pub sur deux lecteurs (ex. D: et C:) : le cache incrémental de Kotlin échoue.
GP="$ANDROID/gradle.properties"
if [ -f "$GP" ]; then
  if grep -Eq '^[[:space:]]*kotlin\.incremental[[:space:]]*=' "$GP"; then
    add_result OK "gradle.properties" "kotlin.incremental déjà défini : conservé"
  else
    backup_once "$GP"
    [ -n "$(tail -c 1 "$GP")" ] && printf '\n' >> "$GP"
    printf '# Projet et cache pub sur deux lecteurs : le cache incremental de Kotlin echoue\nkotlin.incremental=false\n' >> "$GP"
    add_result MODIFIE "gradle.properties" "kotlin.incremental=false"
  fi
else
  add_result ATTENTION "gradle.properties" "introuvable : kotlin.incremental non configuré"
fi

icons_changed=0; icons_missing=0
for d in mdpi hdpi xhdpi xxhdpi xxxhdpi; do
  src="$ROOT/android_config/res/mipmap-$d/ic_launcher.png"
  if [ ! -f "$src" ]; then icons_missing=$((icons_missing + 1)); continue; fi
  destdir="$MAIN_DIR/res/mipmap-$d"
  mkdir -p "$destdir"
  dest="$destdir/ic_launcher.png"
  if [ -f "$dest" ] && cmp -s "$src" "$dest"; then continue; fi
  backup_once "$dest"
  cp "$src" "$dest"
  icons_changed=$((icons_changed + 1))
done
if [ "$icons_missing" -eq 5 ]; then
  add_result IGNORE "Icône AssurZen" "android_config/res introuvable : icône Flutter par défaut conservée"
elif [ "$icons_changed" -gt 0 ]; then
  add_result MODIFIE "Icône AssurZen" "$icons_changed densité(s) mise(s) à jour"
else
  add_result OK "Icône AssurZen" "déjà en place"
fi
[ "$icons_missing" -gt 0 ] && [ "$icons_missing" -lt 5 ] && add_result ATTENTION "Icône AssurZen" "$icons_missing densité(s) manquante(s) dans android_config/res"

# .gitignore : ne pas versionner les sauvegardes
if [ -f .gitignore ] && ! grep -Eq '^\.assurzen-setup-backup/?[[:space:]]*$' .gitignore; then
  [ -n "$(tail -c 1 .gitignore)" ] && printf '\n' >> .gitignore
  printf '\n# Sauvegardes du script scripts/setup\n.assurzen-setup-backup/\n' >> .gitignore
  add_result MODIFIE ".gitignore" "sauvegardes du script exclues"
fi

# ---------------------------------------------------------- 6. Vérifications
step "6/6  Vérifications"

run_flutter() { printf '  > flutter %s\n' "$*"; flutter "$@"; }

if [ "$SKIP_VERIFY" -eq 1 ]; then
  add_result IGNORE "Vérifications" "désactivées (--skip-verify)"
else
  if run_flutter pub get; then
    add_result OK "flutter pub get"

    if run_flutter analyze; then add_result OK "flutter analyze" "aucun problème"
    else add_result ATTENTION "flutter analyze" "des problèmes sont signalés : voir la sortie ci-dessus"; fi

    if [ -d test ]; then
      if run_flutter test; then add_result OK "flutter test" "tests réussis"
      else add_result ECHEC "flutter test" "des tests échouent"; fi
    else
      add_result IGNORE "flutter test" "dossier test/ absent"
    fi

    if [ "$SKIP_BUILD" -eq 1 ]; then
      add_result IGNORE "Compilation APK" "désactivée (--skip-build)"
    elif [ "$JAVA_OK" -ne 1 ] || [ -z "$SDK" ]; then
      add_result IGNORE "Compilation APK" "JDK ou Android SDK manquant"
    elif [ "$JDK_BLOCKED" -eq 1 ]; then
      add_result IGNORE "Compilation APK" "ignorée : jdk-dir de Flutter invalide (voir plus haut)"
    elif run_flutter build apk --debug; then
      add_result OK "flutter build apk --debug" "build/app/outputs/flutter-apk/app-debug.apk"
    else
      add_result ECHEC "flutter build apk --debug" "échec (essayez 'flutter doctor --android-licenses')"
    fi
  else
    add_result ECHEC "flutter pub get" "échec"
  fi
fi

# ------------------------------------------------------------------ Résumé
printf '\n================ Résumé ================\n'
for s in OK MODIFIE IGNORE ATTENTION ECHEC; do
  n=$(grep -c "^$s\$" "$RESULTS")
  [ "$n" -gt 0 ] && printf '  %-10s %s\n' "$s" "$n"
done
printf '\n'
if grep -q '^ECHEC$' "$RESULTS"; then
  printf '%sTerminé avec des échecs (voir les lignes [ECHEC] ci-dessus).%s\n' "$C_FAIL" "$C_OFF"
  exit 1
fi
[ -d "$BACKUP_DIR" ] && printf 'Sauvegardes des fichiers modifiés : %s\n' "$BACKUP_DIR"
printf '%sTerminé. Lancez l'"'"'application avec :  flutter run%s\n' "$C_OK" "$C_OFF"
