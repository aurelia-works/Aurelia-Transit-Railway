#!/usr/bin/env bash
# AureliaMap runner: renders a world into static tiles at the lowest priority, or serves them.
#   aureliamap.sh render <world-dir>   render (incremental; AURELIAMAP_FORCE=1 re-renders everything)
#   aureliamap.sh serve                serve run/web on http://127.0.0.1:$AURELIAMAP_PORT
# Env: AURELIAMAP_RUN (output dir), AURELIAMAP_MODS (mod jars dir), AURELIAMAP_BOX (minX,minZ,maxX,maxZ)
# The world is only read. Needs a Java 25 runtime (JAVA_HOME or java on PATH).
set -euo pipefail

here="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
root="$(dirname "$here")"
run="${AURELIAMAP_RUN:-$root/run}"
port="${AURELIAMAP_PORT:-8100}"
mc="${AURELIAMAP_MC:-1.20.1}"
jar="${AURELIAMAP_JAR:-$(ls -t "$root"/implementations/cli/build/libs/*-shadow.jar 2>/dev/null | head -1)}"
java="${JAVA_HOME:+$JAVA_HOME/bin/}java"

[ -n "$jar" ] && [ -f "$jar" ] || { echo "no jar: run ./gradlew :cli:shadowJar in $root with JDK 25" >&2; exit 1; }
mode="${1:-}"

prepare_config() { # $1 = world dir or empty
  rm -rf "$run/config"; mkdir -p "$run/config/maps" "$run/config/storages" "$run/config/packs"
  cp "$here"/config-template/*.conf "$run/config/"
  cp "$here"/config-template/storages/*.conf "$run/config/storages/"
  sed "s|@PORT@|$port|" "$here/config-template/webserver.conf" > "$run/config/webserver.conf"
  if [ -n "$1" ]; then sed "s|@WORLD@|$1|" "$here/config-template/maps/overworld.conf" > "$run/config/maps/overworld.conf"; fi
  # AURELIAMAP_BOX="minX,minZ,maxX,maxZ" renders only that box (and starts the viewer at its centre)
  if [ -n "$1" ] && [ -n "${AURELIAMAP_BOX:-}" ]; then
    [[ "$AURELIAMAP_BOX" =~ ^-?[0-9]+,-?[0-9]+,-?[0-9]+,-?[0-9]+$ ]] || { echo "AURELIAMAP_BOX must be minX,minZ,maxX,maxZ in whole blocks, got: $AURELIAMAP_BOX" >&2; exit 1; }
    IFS=, read -r x1 z1 x2 z2 <<< "$AURELIAMAP_BOX"
    sed -i.bak -e "s|^render-mask: .*|render-mask: [ { min-x: $x1, min-z: $z1, max-x: $x2, max-z: $z2 } ]|" \
      -e "s|^start-pos: .*|start-pos: { x: $(( (x1 + x2) / 2 )), z: $(( (z1 + z2) / 2 )) }|" "$run/config/maps/overworld.conf"
    rm -f "$run/config/maps/overworld.conf.bak"
  fi
}

case "$mode" in
  render)
    world="${2:?usage: aureliamap.sh render <world-dir>}"
    [ -f "$world/level.dat" ] || { echo "not a world (no level.dat): $world" >&2; exit 1; }
    world="$(cd "$world" && pwd)"
    mkdir -p "$run"; prepare_config "$world"
    cd "$run"
    args=(-c config -v "$mc" -g -s -r)
    [ "${AURELIAMAP_FORCE:-0}" = 1 ] && args+=(-f)
    [ -n "${AURELIAMAP_MODS:-}" ] && args+=(-n "$AURELIAMAP_MODS")
    # lowest priority, one render thread (core.conf), small heap
    exec nice -n 19 "$java" -Xmx1g -jar "$jar" "${args[@]}"
    ;;
  serve)
    [ -f "$run/config/maps/overworld.conf" ] && [ -d "$run/web/maps" ] || { echo "nothing rendered yet: run render first" >&2; exit 1; }
    # keep the config from the last render: its map configs are what register the map routes
    sed "s|@PORT@|$port|" "$here/config-template/webserver.conf" > "$run/config/webserver.conf"
    cd "$run"
    exec nice -n 19 "$java" -Xmx256m -jar "$jar" -c config -v "$mc" -w
    ;;
  *) echo "usage: aureliamap.sh render <world-dir> | serve" >&2; exit 2 ;;
esac
