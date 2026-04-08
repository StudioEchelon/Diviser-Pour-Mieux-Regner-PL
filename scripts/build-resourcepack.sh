#!/usr/bin/env bash
set -euo pipefail
# Les PNG du pack sont TOUJOURS recopies depuis assets/ vers resourcepack/ ci-dessous.
# Si tu modifies seulement resourcepack/assets/dpmr/textures/item/*.png, le prochain
# lancement de ce script ecrase tes changements. Edite les fichiers dans assets/ (meme noms).

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
PACK_DIR="$ROOT_DIR/resourcepack"
DIST_DIR="$ROOT_DIR/dist"
ASSETS_DIR="$ROOT_DIR/assets"

# Textures dans assets/ (voir README resourcepack). MK18 : carabine_mk18.png ou repli gun_2-*.png
if [[ -f "$ASSETS_DIR/carabine_mk18.png" ]]; then
  CARABINE_SRC="$ASSETS_DIR/carabine_mk18.png"
else
  CARABINE_SRC="$ASSETS_DIR/gun_2-3e661ceb-9a27-4209-bcbc-fd5452d82cf9.png"
fi
CARABINE_RARE_SRC="$ASSETS_DIR/carabine_rare.png"
JERRYCAN_SRC="$ASSETS_DIR/jerrycan.png"
BANDAGE_SRC="$ASSETS_DIR/bandage.png"
REVOLVER_SRC="$ASSETS_DIR/revolver.png"
EPIC_SHOTGUN_SRC="$ASSETS_DIR/epic_shotgun.png"
SHOTGUN_COMMON_SRC="$ASSETS_DIR/shotgun_common.png"
REVOLVER_FLAMME_SRC="$ASSETS_DIR/revolver_flamme.png"
REVOLVER_CYBER_SRC="$ASSETS_DIR/revolver_cyber.png"
REVOLVER_CHATAIGNE_SRC="$ASSETS_DIR/revolver_chataigne.png"
POMPE_ACIDE_SRC="$ASSETS_DIR/pompe_acide.png"
POMPE_DOUBLE_SRC="$ASSETS_DIR/pompe_double.png"
POMPE_DOUBLE_CYBER_SRC="$ASSETS_DIR/pompe_double_cyber.png"
GHOST_POMPE_SRC="$ASSETS_DIR/ghost_pompe.png"
CLIO3_SRC="$ASSETS_DIR/clio3.png"
# Optionnel : sinon on reutilise revolver.png pour eviter un pack casse
if [[ -f "$ASSETS_DIR/radar.png" ]]; then
  RADAR_SRC="$ASSETS_DIR/radar.png"
else
  RADAR_SRC="$ASSETS_DIR/revolver.png"
fi

CARABINE_DST="$PACK_DIR/assets/dpmr/textures/item/carabine_mk18.png"
CARABINE_RARE_DST="$PACK_DIR/assets/dpmr/textures/item/carabine_rare.png"
JERRYCAN_DST="$PACK_DIR/assets/dpmr/textures/item/jerrycan.png"
BANDAGE_DST="$PACK_DIR/assets/dpmr/textures/item/bandage.png"
REVOLVER_DST="$PACK_DIR/assets/dpmr/textures/item/revolver.png"
EPIC_SHOTGUN_DST="$PACK_DIR/assets/dpmr/textures/item/epic_shotgun.png"
SHOTGUN_COMMON_DST="$PACK_DIR/assets/dpmr/textures/item/shotgun_common.png"
REVOLVER_FLAMME_DST="$PACK_DIR/assets/dpmr/textures/item/revolver_flamme.png"
REVOLVER_CYBER_DST="$PACK_DIR/assets/dpmr/textures/item/revolver_cyber.png"
REVOLVER_CHATAIGNE_DST="$PACK_DIR/assets/dpmr/textures/item/revolver_chataigne.png"
POMPE_ACIDE_DST="$PACK_DIR/assets/dpmr/textures/item/pompe_acide.png"
POMPE_DOUBLE_DST="$PACK_DIR/assets/dpmr/textures/item/pompe_double.png"
POMPE_DOUBLE_CYBER_DST="$PACK_DIR/assets/dpmr/textures/item/pompe_double_cyber.png"
GHOST_POMPE_DST="$PACK_DIR/assets/dpmr/textures/item/ghost_pompe.png"
CLIO3_DST="$PACK_DIR/assets/dpmr/textures/item/clio3.png"
RADAR_DST="$PACK_DIR/assets/dpmr/textures/item/radar.png"

mkdir -p "$DIST_DIR"
mkdir -p "$(dirname "$CARABINE_DST")"
mkdir -p "$(dirname "$CARABINE_RARE_DST")"
mkdir -p "$(dirname "$JERRYCAN_DST")"
mkdir -p "$(dirname "$BANDAGE_DST")"
mkdir -p "$(dirname "$REVOLVER_DST")"
mkdir -p "$(dirname "$EPIC_SHOTGUN_DST")"
mkdir -p "$(dirname "$SHOTGUN_COMMON_DST")"
mkdir -p "$(dirname "$RADAR_DST")"

if [[ ! -f "$PACK_DIR/pack.mcmeta" ]]; then
  echo "Erreur: pack.mcmeta introuvable dans $PACK_DIR"
  exit 1
fi

if [[ ! -f "$CARABINE_SRC" ]]; then
  echo "Erreur: texture carabine introuvable: $CARABINE_SRC"
  exit 1
fi

if [[ ! -f "$CARABINE_RARE_SRC" ]]; then
  echo "Erreur: texture carabine rare introuvable: $CARABINE_RARE_SRC"
  exit 1
fi

if [[ ! -f "$JERRYCAN_SRC" ]]; then
  echo "Erreur: texture jerrican introuvable: $JERRYCAN_SRC"
  exit 1
fi

if [[ ! -f "$BANDAGE_SRC" ]]; then
  echo "Erreur: texture bandage introuvable: $BANDAGE_SRC"
  exit 1
fi

if [[ ! -f "$REVOLVER_SRC" ]]; then
  echo "Erreur: texture revolver introuvable: $REVOLVER_SRC"
  exit 1
fi

if [[ ! -f "$EPIC_SHOTGUN_SRC" ]]; then
  echo "Erreur: texture fusil a pompe epique introuvable: $EPIC_SHOTGUN_SRC"
  exit 1
fi

if [[ ! -f "$SHOTGUN_COMMON_SRC" ]]; then
  echo "Erreur: texture pompe commune introuvable: $SHOTGUN_COMMON_SRC"
  exit 1
fi

for pair in \
  "$REVOLVER_FLAMME_SRC|$REVOLVER_FLAMME_DST|revolver flamme" \
  "$REVOLVER_CYBER_SRC|$REVOLVER_CYBER_DST|revolver cyber" \
  "$REVOLVER_CHATAIGNE_SRC|$REVOLVER_CHATAIGNE_DST|revolver chataigne" \
  "$POMPE_ACIDE_SRC|$POMPE_ACIDE_DST|pompe acide" \
  "$POMPE_DOUBLE_SRC|$POMPE_DOUBLE_DST|pompe double" \
  "$POMPE_DOUBLE_CYBER_SRC|$POMPE_DOUBLE_CYBER_DST|pompe double cyber" \
  "$GHOST_POMPE_SRC|$GHOST_POMPE_DST|ghost pompe" \
  "$CLIO3_SRC|$CLIO3_DST|clio3"; do
  IFS='|' read -r _src _dst _label <<< "$pair"
  if [[ ! -f "$_src" ]]; then
    echo "Erreur: texture $_label introuvable: $_src"
    exit 1
  fi
done

cp -f "$CARABINE_SRC" "$CARABINE_DST"
cp -f "$CARABINE_RARE_SRC" "$CARABINE_RARE_DST"
cp -f "$JERRYCAN_SRC" "$JERRYCAN_DST"
cp -f "$BANDAGE_SRC" "$BANDAGE_DST"
cp -f "$REVOLVER_SRC" "$REVOLVER_DST"
cp -f "$EPIC_SHOTGUN_SRC" "$EPIC_SHOTGUN_DST"
cp -f "$SHOTGUN_COMMON_SRC" "$SHOTGUN_COMMON_DST"
cp -f "$REVOLVER_FLAMME_SRC" "$REVOLVER_FLAMME_DST"
cp -f "$REVOLVER_CYBER_SRC" "$REVOLVER_CYBER_DST"
cp -f "$REVOLVER_CHATAIGNE_SRC" "$REVOLVER_CHATAIGNE_DST"
cp -f "$POMPE_ACIDE_SRC" "$POMPE_ACIDE_DST"
cp -f "$POMPE_DOUBLE_SRC" "$POMPE_DOUBLE_DST"
cp -f "$POMPE_DOUBLE_CYBER_SRC" "$POMPE_DOUBLE_CYBER_DST"
cp -f "$GHOST_POMPE_SRC" "$GHOST_POMPE_DST"
cp -f "$CLIO3_SRC" "$CLIO3_DST"
cp -f "$RADAR_SRC" "$RADAR_DST"
[[ "$RADAR_SRC" != "$ASSETS_DIR/radar.png" ]] && echo "Note: radar.png absent -> texture radar = revolver (ajoute assets/radar.png pour l'outil radar)."

ZIP_PATH="$DIST_DIR/dpmr-pack.zip"
rm -f "$ZIP_PATH"

(
  cd "$PACK_DIR"
  zip -r "$ZIP_PATH" . >/dev/null
)

echo "OK: pack créé -> $ZIP_PATH"
echo "SHA1:"
shasum -a 1 "$ZIP_PATH" | awk '{print $1}'

