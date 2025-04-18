# PolarPaper
#### Polar world format for Paper

> [!WARNING]  
> Not widely tested, possibly unstable. Backup your worlds before if you don't want to lose them!

Polar is a world format very similar to Slime, with the same advantages:
 - Small file sizes
 - Single file world
 - Immutable (Worlds do not save until explicitly requested)
 - Store worlds wherever (Whether as a file or in a database)

Polar is also a single plugin without requiring classloaders or a Paper fork

### [Download the latest jar](https://github.com/MinehubMC/PolarPaper/releases/latest)

Polar was originally developed for [Minestom](https://github.com/Minestom/Minestom), see the Minestom loader [here](https://github.com/hollow-cube/polar)

[Support Discord](https://discord.gg/5MrPmKqS7p)

## Permissions
Permission nodes are simply `polarpaper.<subcommand>`, for example: `polarpaper.info` for `/polar info`

## API
Remember to add `polarpaper` to your depend list in plugin.yml.
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

Get the `PolarWorld` that a player is in
```java
PolarWorld polarWorld = PolarWorld.fromWorld(player.getWorld());
// (returns null if the world is not from PolarPaper)
```

Load a polar world
```java
// Manually
Path path = Path.of("path/to/world.polar");
String worldName = path.getFileName().toString().split("\\.polar")[0];
byte[] bytes;
try {
    // This example shows reading a world from a file, however
    // as long as you can read and write a byte array, you can read it
    // from wherever you want - including databases like mysql and redis!
    bytes = Files.readAllBytes(path);
} catch (IOException e) {
    throw new RuntimeException(e);
}
PolarWorld polarWorld = PolarReader.read(bytes);
Polar.loadWorld(polarWorld, worldName);

// or by using config
Polar.loadWorldConfigSource("gamingworld", null, null);
```

Save a polar world
```java
// Manually
World bukkitWorld = player.getWorld();
PolarWorld polarWorld = PolarWorld.fromWorld(bukkitWorld);
PolarGenerator polarGenerator = PolarGenerator.fromWorld(bukkitWorld);
if (polarWorld == null) return;

// save to file
Path savePath = Path.of("./epic/world.polar")
Polar.saveWorld(bukkitWorld, polarWorld, polarGenerator, savePath) // update the chunks and save to file

// or custom location
Polar.updateWorld(bukkitWorld, polarWorld, polarGenerator, ChunkSelector.all(), 0, 0) // update the chunks
byte[] bytes = PolarWriter.write(polarWorld); // save the bytes somewhere


// or automatically using config (same as /polar save)
Polar.saveWorldConfigSource("gamingworld", null, null);
```

### Versioning
To be used starting from this point, does not include older releases.

`<mc version>.<our version>`

for example `1.21.4.1`
