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

Polar was originally developed for [Minestom](https://github.com/Minestom/Minestom), see the Minestom loader [here](https://github.com/hollow-cube/polar)

## Permissions
Permission nodes are simply `polarpaper.<subcommand>`, for example: `polarpaper.info` for `/polar info`

## API
Get the `PolarWorld` that a player is in
```java
PolarWorld world = PolarWorld.fromWorld(player.getWorld());
// (returns null if the world is not from PolarPaper)
```

Load a polar world manually
```java
Path path = Path.of("path/to/world.polar");
String worldName = path.getFileName().toString().split(".polar")[0];
byte[] bytes;
try {
    bytes = Files.readAllBytes(path);
} catch (IOException e) {
    throw new RuntimeException(e);
}
// This example shows reading a world from a .polar file, however
// as long as you can read and write a byte array, you can read it
// from wherever you want - including databases like mysql and redis!

PolarWorld polarWorld = PolarReader.read(bytes);
Polar.loadWorld(polarWorld, worldName);
```