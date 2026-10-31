## Aurelia Transit Railway

_Aurelia Transit Railway_ (ATR) is a maintained fork of [Minecraft Transit Railway](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) (MTR) for **Minecraft 1.20.1 on Fabric**.

- **Based on MTR by jonafanho (Jonathan Ho).** MTR and its simulation core are MIT licensed; the copyright and license notice are kept in [LICENSE](LICENSE).
- Some vehicle and model features come from Nemo's Transit Expansion (NTE), also MIT licensed, as they were ported in MTR 4.0.
- **Drop-in replacement:** the mod id stays `mtr`, so MTR add-ons and existing MTR worlds keep working. ATR reports version `4.0.5+atr.1.0.1`, so add-ons that require `mtr >=4.0.5 <4.1.0` still load.
- Install by removing the MTR jar and putting the ATR jar in its place. Do not run both.
- Build: `./gradlew build -x test` gives `build/release/ATR-fabric-<version>-mc1.20.1.jar`.

### Features

- Faster rendering with a switch for every fix, the Aurelia panel for all tools and settings, the Rail Builder, built-in Aurelia Transit Architecture blocks and Station Studio, and AureliaMap, a 3D web map with a live layer of lines, stations and trains.
- See [CHANGELOG.md](CHANGELOG.md) and the release page text in [docs/release](docs/release).

### Install

1. Minecraft **1.20.1** with **Fabric Loader** and **Fabric API**.
2. Remove any `mtr` jar (Minecraft Transit Railway or an older ATR build).
3. Put `ATR-fabric-4.0.5+atr.1.0.1-mc1.20.1.jar` in the `mods` folder.

Releases are published on [this repository](https://github.com/aurelia-works/Aurelia-Transit-Railway/releases).

<!-- TODO: add the ATR Modrinth page URL once the project exists. Do not link the upstream MTR Modrinth page as if it were ATR. -->

### What it is

ATR keeps the MTR 4.0.5 transport simulation on 1.20.1: trains, boats, cable cars, and planes, with the 4.0 vehicle model, signalling, and station tools. Upstream MTR has moved on to newer Minecraft versions. This fork stays on 1.20.1 and adds Aurelia's rendering fixes, building tools, station blocks, and the web map.

### Credits

- Minecraft Transit Railway by jonafanho (Jonathan Ho), MIT. Upstream source: https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway
- Nemo's Transit Expansion (NTE), MIT, for the OBJ and model work carried into MTR 4.0.
- [Noto fonts](https://fonts.google.com/noto), bundled with the mod, are under the [Open Font License](https://scripts.sil.org/OFL).

This project is MIT licensed. See [LICENSE](LICENSE).

### Issues

Report bugs and ideas on [GitHub Issues](https://github.com/aurelia-works/Aurelia-Transit-Railway/issues).

<!-- TODO: ATR does not have its own Discord, Patreon, or Crowdin project yet. Do not send players to the upstream MTR Discord, Patreon, or Crowdin. -->

### Building

From the repository root, with JDK 21:

```
./gradlew setupFiles
./gradlew build -x test
```

The release jar is `build/release/ATR-fabric-<version>-mc1.20.1.jar`. The Gradle project name is `Aurelia-Transit-Railway`; the jar name does not use it.
