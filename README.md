# Developer Mode

A beta 1.7.3 mod built on [StationAPI](https://github.com/ModificationStation/StationAPI) with BIN mappings, for server + client.

Based on [calmilamsy/stationapi-example-mod](https://github.com/calmilamsy/stationapi-example-mod). The template's `_setup/setupMod` task has already been run, so the mod id, package and maven group are set in `gradle.properties`.

## Setup

[See the StationAPI wiki.](https://github.com/ModificationStation/StationAPI/wiki)

## Common Issues

**My project isn't building after updating babric loom/stationapi!**  
Run a gradle task with `--refresh-dependencies` as an argument, and this should be fixed. If not, try deleting your project's `.gradle` folder, and try again.

**Gradle fails with "Dependency requires at least JVM runtime version 21"!**  
Babric loom 1.17 needs a Java 21+ Gradle JVM. Go into `File > Settings > Build, Execution, Deployment > Build Tools > Gradle` and set "Gradle JVM" to a Java 21 (or newer) SDK. The mod itself is still compiled against Java 17.

**I get "Invalid source release: 17" as an error!**  
Open up `File > Project Structure` and make sure the project SDK is Java 17 or newer.

**How do I stop server.properties from constantly changing?**  
Remove the last line in the `gitignore` file.

**My client hangs on a blank screen on trying to my test server!**  
Open your `server.properties` and set `online-mode` to `false`.

[Here for more issues.](https://github.com/calmilamsy/BIN-fabric-example-mod#common-issues)

## License

This mod is available under the CC0 license.
