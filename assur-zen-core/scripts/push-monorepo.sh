#!/usr/bin/env bash
# Pousse les commits de ce projet (branche courante) vers le dépôt principal AssurZen, dans le
# sous-dossier assur-zen-core/ de la branche main, en conservant l'historique.
#
# Ce dépôt n'est pas le dépôt principal : il y a été importé par « git subtree add ». Le script clone
# donc le dépôt principal dans un dossier temporaire, y rapatrie les nouveaux commits avec
# « git subtree pull », pousse main, puis supprime le dossier temporaire.
#
# Usage : bash scripts/push-monorepo.sh   (depuis n'importe où dans le projet, arbre de travail propre)
set -euo pipefail

PREFIX="assur-zen-core"
REMOTE_BRANCH="main"
SRC="$(git rev-parse --show-toplevel)"
BRANCH="$(git rev-parse --abbrev-ref HEAD)"
REMOTE_URL="$(git -C "$SRC" remote get-url origin)"

if [ -n "$(git -C "$SRC" status --porcelain)" ]; then
  echo "Arbre de travail non propre : commitez ou remisez vos modifications avant de pousser." >&2
  exit 1
fi

TMP="$(mktemp -d)"
trap 'rm -rf "$TMP"' EXIT

echo "Clone du dépôt principal ($REMOTE_URL, $REMOTE_BRANCH)…"
git clone --quiet --branch "$REMOTE_BRANCH" "$REMOTE_URL" "$TMP/monorepo"
cd "$TMP/monorepo"
git config core.longpaths true

echo "Rapatriement de la branche $BRANCH dans $PREFIX/…"
git subtree pull --prefix="$PREFIX" "$SRC" "$BRANCH" \
  -m "chore: synchronisation de $PREFIX ($BRANCH)"

echo "Envoi vers $REMOTE_BRANCH…"
git push origin "$REMOTE_BRANCH"
echo "Terminé : $(git log --oneline -1)"
