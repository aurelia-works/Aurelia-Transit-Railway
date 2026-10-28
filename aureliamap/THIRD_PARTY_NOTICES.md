# Third-party notices

AureliaMap is a fork of BlueMap and BlueMapAPI by Blue (Lukas Rieger) and contributors, MIT licence (`LICENSE`, `api/LICENSE`).

Audited 2026-10-28 from the CLI jar (`./gradlew :cli:dependencies --configuration runtimeClasspath` and the classes inside `cli-*-shadow.jar`) and the webapp's production dependencies (`npm ls --omit=dev --all`).

## Bundled in the CLI jar

| Library | Licence |
|---|---|
| BlueNBT, BlueCommands (de.bluecolored) | MIT |
| Caffeine 3.3.1 (Ben Manes) | Apache-2.0 |
| Configurate core, gson, hocon 4.2.0 (SpongePowered) | Apache-2.0 |
| Adventure API and key 4.26.1, Examination 1.3.0, Option 1.1.0 (KyoriPowered) | MIT |
| Apache Commons CLI 1.11.0, DBCP2 2.14.0, Pool2 2.13.0, Logging 1.3.5 | Apache-2.0 |
| Gson 2.8.9 (Google) | Apache-2.0 |
| Error Prone annotations 2.50.0 (Google) | Apache-2.0 |
| aircompressor 2.0.3 (Airlift) | Apache-2.0 |
| lz4-java 1.10.1 | Apache-2.0 |
| geantyref 1.3.16 (Leangen) | Apache-2.0 |
| flow-math 1.0.3 (Flow Powered) | MIT |

The Apache-2.0 libraries' own `LICENSE` and `NOTICE` files are kept in the jar's `META-INF/`.

## Bundled in the webapp

| Library | Licence |
|---|---|
| three.js 0.186.1 | MIT |
| Vue 3.5.44 and its runtime packages, vue-i18n 9.14.5 and @intlify packages | MIT |
| Hammer.js 2.0.8 | MIT |
| hocon-parser 1.0.1 | ISC |
| Quicksand font (`src/fonts/Quicksand.ttf`) | SIL Open Font License 1.1 (`src/fonts/OFL.txt`) |

Build-only tools (Vite, Sass, the Vue template compiler with entities (BSD-2-Clause), Babel parser, PostCSS) are not shipped.

## Not bundled

Minecraft's `client.jar` is downloaded by the user's own copy at first run (`accept-download: true`) and is never committed or redistributed. Mod jars passed with `AURELIAMAP_MODS` are read from the user's mods folder and are not copied into the output, apart from the rendered map tiles.
