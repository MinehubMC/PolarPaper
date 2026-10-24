# Polar format
## Version 7

### Types
All numbers are big-endian.

| Type     | Notes                                                                                 |
|----------|---------------------------------------------------------------------------------------|
| varint   | Same as the Minecraft protocol VarInt                                                 |
| bool     | A byte, 1 = true, 0 = false                                                           |
| string   | Varint length followed by that many bytes of UTF-8                                    |
| array[T] | Varint length followed by that many `T` entries, unless the notes give a fixed length |
| nbt      | A tag type byte followed by the tag payload, with no tag name (network NBT)           |

### Header
| Name           | Type            | Notes                                                                                                         |
|----------------|-----------------|---------------------------------------------------------------------------------------------------------------|
| Magic Number   | int             | `Polr` (0x506F6C72)                                                                                           |
| Version        | short           | Latest version = 7                                                                                            |
| Data Version   | varint          | The Minecraft data version, e.g. 4671 for 1.21.11 (see [Data version](https://minecraft.wiki/w/Data_version)) |
| Compression    | byte            | 0 = None, 1 = Zstd                                                                                            |
| Length of data | varint          | Uncompressed length of the [world](#world) data that follows                                                  |
| World          | [world](#world) | Zstd compressed if `Compression=1`                                                                            |

<a name="world"></a>
### World
| Name        | Type                   | Notes                             |
|-------------|------------------------|-----------------------------------|
| Min Section | byte                   | -4 in a vanilla world             |
| Max Section | byte                   | 19 in a vanilla world (inclusive) |
| User data   | array[byte]            | Arbitrary user data segment       |
| Chunks      | array[[chunk](#chunk)] | Chunk data                        |

<a name="chunk"></a>
### Chunk
| Name           | Type                                 | Notes                                                                |
|----------------|--------------------------------------|----------------------------------------------------------------------|
| Chunk X        | varint                               |                                                                      |
| Chunk Z        | varint                               |                                                                      |
| Sections       | [section](#section)                  | Exactly `maxSection-minSection+1` entries, no length prefix          |
| Block Entities | array[[block entity](#block-entity)] |                                                                      |
| Heightmap Mask | int                                  | Bit `i` set means heightmap `i` is present                           |
| Heightmaps     | array[long]                          | One entry for each bit set in Heightmap Mask, in ascending bit order |
| User data      | array[byte]                          | Arbitrary user data segment                                          |

<a name="section"></a>
### Section
| Name               | Type          | Notes                                                                                                                             |
|--------------------|---------------|-----------------------------------------------------------------------------------------------------------------------------------|
| Is Empty           | bool          | If set, nothing follows                                                                                                           |
| Block Palette      | array[string] | Entries are in the form `minecraft:block[key1=value1,key2=value2]`                                                                |
| Block Palette Data | array[long]   | Only present if Block Palette has more than 1 entry. Packed long array, see [Chunk format](https://minecraft.wiki/w/Chunk_format) |
| Biome Palette      | array[string] |                                                                                                                                   |
| Biome Palette Data | array[long]   | Only present if Biome Palette has more than 1 entry. Packed long array, see [Chunk format](https://minecraft.wiki/w/Chunk_format) |
| Block Light Type   | byte          | 0 = no lighting, 1 = all zero, 2 = all max, 3 = present after                                                                     |
| Block Light        | byte[2048]    | Nibble array with no length prefix, only present if above = 3                                                                     |
| Sky Light Type     | byte          | 0 = no lighting, 1 = all zero, 2 = all max, 3 = present after                                                                     |
| Sky Light          | byte[2048]    | Nibble array with no length prefix, only present if above = 3                                                                     |

<a name="block-entity"></a>
### Block Entity
| Name            | Type   | Notes                               |
|-----------------|--------|-------------------------------------|
| Chunk Pos       | int    |                                     |
| Has ID          | bool   |                                     |
| Block Entity ID | string | Only present if Has ID is set       |
| Has NBT Data    | bool   |                                     |
| NBT Data        | nbt    | Only present if Has NBT Data is set |

## PolarPaper specific
PolarPaper stores entities and the chunk's persistent data container in the user data of each chunk.
Minestom Polar does not read these back by default.

### PolarPaper chunk user data
| Name                      | Type                     | Notes                                                                       |
|---------------------------|--------------------------|-----------------------------------------------------------------------------|
| Version                   | byte                     | Latest version = 2                                                          |
| Entities                  | array[[entity](#entity)] |                                                                             |
| Persistent Data Container | array[byte]              | The Bukkit persistent data container serialized to bytes, version >= 2 only |

<a name="entity"></a>
### Entity
| Name       | Type        | Notes                                                                                      |
|------------|-------------|--------------------------------------------------------------------------------------------|
| X Position | double      | Relative to the chunk (0-16)                                                               |
| Y Position | double      |                                                                                            |
| Z Position | double      | Relative to the chunk (0-16)                                                               |
| Yaw        | float       |                                                                                            |
| Pitch      | float       |                                                                                            |
| NBT        | array[byte] | Entity NBT as a root compound tag (type byte, empty name, payload), includes `DataVersion` |

### PolarPaper world user data
Only written by `/polar createfromregion` and `/polar setcenter`, used as the schematic offset when pasting the world.

| Name             | Type | Notes                           |
|------------------|------|---------------------------------|
| Version          | byte | Latest version = 1              |
| Schematic Offset | int  | X, followed by Y and Z (3 ints) |
