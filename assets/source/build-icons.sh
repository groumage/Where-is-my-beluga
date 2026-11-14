#!/usr/bin/env bash
# Regenerates the Google Play listing images from beluga.svg. Needs rsvg-convert.
# The launcher icon is a vector drawable with the same paths:
# app/src/main/res/drawable/ic_launcher_foreground.xml
set -euo pipefail
cd "$(dirname "$0")"

BLUE='#1d4e89'
tmp=$(mktemp -d)
trap 'rm -rf "$tmp"' EXIT

# variant <name> <scale> <plane color>
variant() {
  sed -e "s/SCALE/$2/" -e "s/PLANE/$3/" beluga.svg > "$tmp/$1.svg"
}

variant full 0.95 white

# Google Play store listing: 512px icon and 1024x500 feature graphic.
store=../../docs/google-play
mkdir -p "$store"
rsvg-convert -w 512 -h 512 -b "$BLUE" "$tmp/full.svg" -o "$store/icon-512.png"
cat > "$tmp/feature.svg" <<SVG
<svg xmlns='http://www.w3.org/2000/svg' xmlns:xlink='http://www.w3.org/1999/xlink' width='1024' height='500'>
  <rect width='1024' height='500' fill='$BLUE'/>
  <image href='file://$tmp/full-plane.png' x='40' y='70' width='400' height='360'/>
  <text x='470' y='235' font-family='sans-serif' font-weight='700' font-size='64' fill='white'>Where is my</text>
  <text x='470' y='315' font-family='sans-serif' font-weight='700' font-size='84' fill='white'>Beluga</text>
</svg>
SVG
rsvg-convert -w 1024 -h 1024 "$tmp/full.svg" -o "$tmp/full-plane.png"
rsvg-convert "$tmp/feature.svg" -o "$store/feature-graphic.png"

echo "Store assets written to $(cd "$store" && pwd)"
