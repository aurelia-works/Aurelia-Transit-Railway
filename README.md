## Aurelia Transit Railway

_Aurelia Transit Railway_ (ATR) is a maintained fork of [Minecraft Transit Railway](https://github.com/Minecraft-Transit-Railway/Minecraft-Transit-Railway) (MTR) for **Minecraft 1.20.1 on Fabric**.

- **Based on MTR by jonafanho (Jonathan Ho).** MTR and its simulation core are MIT licensed; the copyright and license notice are kept in [LICENSE](LICENSE).
- **Drop-in replacement:** the mod id stays `mtr`, so MTR add-ons and existing MTR worlds keep working. ATR reports version `4.0.5+atr.1.0.0`, so add-ons that require `mtr >=4.0.5 <4.1.0` still load.
- Install by removing the MTR jar and putting the ATR jar in its place. Do not run both.
- Build: `./gradlew build -x test` gives `build/release/ATR-fabric-<version>-mc1.20.1.jar`.

### Features
- Faster rendering with a switch for every fix, the Aurelia panel for all tools and settings, the Rail Builder, built-in Aurelia Transit Architecture blocks and Station Studio, and AureliaMap, a 3D web map with a live layer of lines, stations and trains.
- See [CHANGELOG.md](CHANGELOG.md) and the release page text in [docs/release](docs/release).

The rest of this file is MTR's original README.

---

## Minecraft Transit Railway 4.0.0

_Minecraft Transit Railway_ is a [Minecraft mod](https://minecraft.wiki/w/Mods) that allows you to build your own transport network with automated trains, boats, cable cars, and planes!

Version 4.0.0 is a complete rewrite from scratch. The vehicle simulation code has been separated from the Minecraft instance, greatly reducing lag on Minecraft servers.

## Major Features in 4.0.0

- Custom train configuration
- Realistic bogies and train movement
- Improved signalling
- Sideways elevators
- OBJ features ported over from Nemo's Transit Expansion
- Vehicle swaying effect
- New online system map and OneBusAway integration
- Client-side FPS improvements and server-side TPS improvements

[See these features in detail.](https://www.youtube.com/playlist?list=PLp0jZgheSlXuQCXu9UynrKuBnq2Ef_TSI)

[![Video Trailer](https://github.com/jonafanho/Minecraft-Transit-Railway/blob/master/images/footer/video-preview.png)](https://www.youtube.com/watch?v=1cZfU7t4cAk)

Please report any issues or bugs you find; that would be greatly appreciated! Refer to the [todo list](https://github.com/jonafanho/Minecraft-Transit-Railway/projects/2) to see currently known issues.

## Downloads and Installation

Head over to the [Modrinth page](https://modrinth.com/mod/minecraft-transit-railway) to download the mod or to see the project information.

## Guide

There is a [new wiki](https://wiki.minecrafttransitrailway.com/start) for the mod. Take a look.

## Recommended Addons

- [MTR London Underground Addon](https://modrinth.com/mod/the-tube)
- [Joban Client Mod](https://modrinth.com/mod/jcm)
- [Station Decoration](https://modrinth.com/mod/station-decoration)
- [Russian Metro Addon](https://modrinth.com/mod/russian-metro-addon)

## Contributing

### Help Translate the Mod!

The [Crowdin site for the Minecraft Transit Railway mod](https://crwd.in/minecraft-transit-railway) is available!

Crowdin is a cloud-based platform for translators to contribute to a project. With your help, we can translate the mod into many different languages. You may create a free account to start translating.

[![Crowdin](https://badges.crowdin.net/minecraft-transit-railway/localized.svg)](https://crowdin.com/project/minecraft-transit-railway)

### Adding Features

1. Fork this project
2. On your fork, create a new branch based on the development version branch
3. Commit your changes to the new branch
4. Make a Pull Request to merge your branch into the development version of this repository

### Building

To build the mod, run the following commands in the root directory of the project:

```gradle
gradlew setupFiles -PminecraftVersion="<minecraft version>"
gradlew build -PminecraftVersion="<minecraft version>"
```

The mod jar file should be generated in the following directory:

```
<root>/build/release/MTR-<fabric|forge>-<mod version>+<minecraft version>.jar
```

## License

This project is licensed under the [MIT License](https://opensource.org/licenses/MIT). All [Noto fonts](http://www.google.com/get/noto/), bundled with this mod, are licensed under the [Open Font License](http://scripts.sil.org/OFL).

## Questions? Comments? Complaints?

Let's connect.

<a href="https://discord.gg/PVZ2nfUaTW" target="_blank"><img src="https://github.com/jonafanho/Minecraft-Transit-Railway/blob/master/images/footer/discord.png" alt="Discord" width=64></a>
&nbsp;
<a href="https://www.linkedin.com/in/jonathanho33" target="_blank"><img src="https://github.com/jonafanho/Minecraft-Transit-Railway/blob/master/images/footer/linked_in.png" alt="LinkedIn" width=64></a>
&nbsp;
<a href="mailto:jonho.minecraft@gmail.com" target="_blank"><img src="https://github.com/jonafanho/Minecraft-Transit-Railway/blob/master/images/footer/email.png" alt="Email" width=64></a>
&nbsp;
<a href="https://www.patreon.com/minecraft_transit_railway" target="_blank"><img src="https://github.com/jonafanho/Minecraft-Transit-Railway/blob/master/images/footer/patreon.png" alt="Patreon" width=64></a>
