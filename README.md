# PolarPaper
#### Polar world format for Paper

> [!WARNING]  
> Not widely tested, possibly unstable. Backup your worlds before if you don't want to lose them!

Polar is a world format very similar to Slime, with the same advantages:
 - Small file sizes
 - Single file world
 - Immutable (worlds do not save until explicitly requested)
 - Store worlds wherever (whether as a file or in a database)

Polar is also a single plugin without requiring classloaders or a Paper fork

### [Download the latest jar](https://github.com/MinehubMC/PolarPaper/releases/latest)

Polar was originally developed for [Minestom](https://github.com/Minestom/Minestom), see the Minestom loader [here](https://github.com/hollow-cube/polar)

[Support Discord](https://discord.gg/5MrPmKqS7p)

## Permissions
Permission nodes are simply `polarpaper.<subcommand>`, for example: `polarpaper.info` for `/polar info`

## API
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
Path path = Path.of("path/to/world.polar");
String worldName = path.getFileName().toString().split("\\.polar")[0];
byte[] bytes;
try {
    // This example shows reading a world from a file, however
    // as long as you can read and write an array of bytes, you can read it
    // from wherever you want - including mysql and redis!
    bytes = Files.readAllBytes(path);
} catch (IOException e) {
    throw new RuntimeException(e);
}
PolarWorld polarWorld = PolarReader.read(bytes);
Polar.loadWorld(polarWorld, worldName);

// or by using config
Polar.loadWorldConfigSource("gamingworld");
```

Save a polar world
```java
// Using config (same as /polar save)
World bukkitWorld = player.getWorld();
Polar.saveWorldConfigSource(bukkitWorld);

// Custom source
World bukkitWorld = player.getWorld();
Path savePath = Path.of("./epic/world.polar");
// feel free to use your own PolarSource class
PolarSource source = new FilePolarSource(savePath);
Polar.saveWorld(bukkitWorld, source);
```

Get the `PolarWorld` that a player is in
```java
PolarWorld polarWorld = PolarWorld.fromWorld(player.getWorld());
// (returns null if the world is not from PolarPaper)
```

Register events
```java
// If you're not running the PolarPaper plugin and instead using it exclusively
// as a dependency (e.g. implementation instead of compileOnly), you do not need to
// add it to the depend list in your plugin.yml. However, in order to allow entities
// to be read and spawned automatically, you must manually register the plugin listeners:
PolarPaper.registerEvents();
```

### Versioning
`<mc version>.<our version>`

for example `1.21.4.1`
