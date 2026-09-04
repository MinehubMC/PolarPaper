# Polar world format for Paper

Polar is a world format very similar to Slime, with the same advantages:
 - Small file sizes
 - Single file world
 - Immutable (worlds do not save until explicitly requested)
 - Store worlds wherever (whether as a file or in a database)
 - Great for lobbies and game arenas

Polar is also a single plugin without requiring classloaders or a Paper fork

Polar currently supports versions 26.2, 26.1.2, and 1.21.11, and requires minimum Java 25

### [Download the latest jar](https://github.com/MinehubMC/PolarPaper/releases/latest)

Polar was originally developed for [Minestom](https://github.com/Minestom/Minestom), see the Minestom library [here](https://github.com/hollow-cube/polar)

[Support Discord](https://discord.gg/n7fp52auB7)

## Large worlds
Polar is not designed for large worlds! Polar is most useful when the whole world is mostly within render distance. The entire world is held in memory as a trade-off to allow for faster loading. This means the size/number of worlds you can load depends on how much RAM you have available. If you need large worlds, it's likely Anvil (the default world format) will be more useful.

## Permissions
Permission nodes are simply `polarpaper.<subcommand>`, for example: `polarpaper.info` for `/polar info`

`polarpaper.use` for the root command (/polar)

## Custom gamerules
Polar provides a few custom gamerules that can be defined in the config:
| Name          | Type    | Description                                                                                                |
| ------------- | ------- | ---------------------------------------------------------------------------------------------------------- |
| blockPhysics  | Boolean | Controls block placement/interaction rules                                                                 |
| blockGravity  | Boolean | Allow gravity blocks to fall (sand, gravel)                                                                |
| liquidPhysics | Boolean | Allow lava/water to flow                                                                                   |
| blockFade     | Boolean | Controls block fading or disappearing (e.g. snow/ice melting, fire burning out, coral death, turtle eggs)  |

# API
Remember to add `polarpaper` to your depend list in plugin.yml if using as a plugin/compileOnly
```yml
depend:
  - polarpaper
```

Add to Gradle:
```kts
repositories {
    maven("https://repo.minehub.live/releases")
}
dependencies {
    compileOnly("live.minehub:polarpaper:<latest version>")
}
```

Load a polar world
```java
// Manually
byte[] bytes = ...
Polar.createWorld(bytes, worldName);
// or
byte[] bytes = ...
PolarWorld polarWorld = PolarReader.read(bytes); // (PolarWorld can be reused, see below)
Polar.createWorld(polarWorld, worldName);

// Using PolarSource
Path savePath = Path.of("./epic/world.polar");
// feel free to use your own PolarSource implementation
PolarSource source = new FilePolarSource(savePath);
Polar.loadWorld(source, worldName);

// Load world like /polar load - must be in PolarPaper worlds folder
Polar.loadWorldFromFile("gamingworld");
```

Save a polar world
```java
// Manually
PolarWorld polarWorld = ...
polarWorld.updateChunks(bukkitWorld); // update chunks in the polar world
byte[] bytes = PolarWriter.write(polarWorld);

// Using PolarSource
World bukkitWorld = player.getWorld();
Path savePath = Path.of("./epic/world.polar");
// feel free to use your own PolarSource implementation
PolarSource source = new FilePolarSource(savePath);
Polar.saveWorld(bukkitWorld, source);

// Save world like /polar save
World bukkitWorld = player.getWorld();
Polar.saveWorldToFile(bukkitWorld);
```

Reusing the PolarWorld object
```java
// We can save memory and load time by reusing one PolarWorld
byte[] bytes = ...
PolarWorld polarWorld = PolarReader.read(bytes);
Polar.createWorld(polarWorld, "worldName1");
Polar.createWorld(polarWorld, "worldName2");
Polar.createWorld(polarWorld, "worldName3");
```

Get the `PolarGenerator` of a world
```java
World world = ... // e.g. player.getWorld()
PolarGenerator polarGenerator = PolarGenerator.fromWorld(world);
// (returns null if the world is not from PolarPaper)
```

Paste a polar world into a world (like a .schematic)
```java
Path worldPath = Path.of("./epic/world.polar");
byte[] polarBytes = Files.readAllBytes(worldPath);
PolarWorld polarWorld = PolarReader.read(polarBytes);
Vector3i offset = new Vector3i(x, y, z);
Setter setter = new Setter.World(player.getWorld());
Schematic.paste(polarWorld, setter, offset, Rotation.CLOCKWISE_90, Schematic.IgnoreAir.EMPTY_SECTION);
```

Register events
```java
// If you're not using PolarPaper as a plugin and instead using it exclusively
// as a dependency (e.g. implementation instead of compileOnly), you do not need to
// add it to the depend list in your plugin.yml. However, you must manually register the plugin listeners:
PolarPaper.registerEvents();
```
