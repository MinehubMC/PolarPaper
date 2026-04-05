# Polar format
## Version 7
### Header
| Name           | Type            | Notes                                                                                                         |
|----------------|-----------------|---------------------------------------------------------------------------------------------------------------|
| Magic Number   | int             | `Polr` (0x506F6C72)                                                                                           |
| Version        | short           | Latest version = 7                                                                                            |
| Data Version   | int             | The Minecraft data version, e.g. 4671 for 1.21.11 (see [Data version](https://minecraft.wiki/w/Data_version)) |
| Compression    | byte            | 0 = None, 1 = Zstd                                                                                            |
| Length of data | varint          | Uncompressed length of data (or just length of data if `Compression=0`)                                       |
| World          | [world](#world) |                                                                                                               |

<a name="world"></a>
### World
| Name             | Type                   | Notes                                    |
|------------------|------------------------|------------------------------------------|
| Min Section      | byte                   | -4 in a vanilla world                    |
| Max Section      | byte                   | 19 in a vanilla world                    |
| User data        | array[byte]            | Arbitrary user data segment              |
| Number of Chunks | varint                 | Number of entries in the following array |
| Chunks           | array[[chunk](#chunk)] | Chunk data                               |

<a name="chunk"></a>
### Chunk
Entities or some other extra data field needs to be added to chunks in the future.

| Name                     | Type                                 | Notes                                                |
|--------------------------|--------------------------------------|------------------------------------------------------|
| Chunk X                  | varint                               |                                                      |
| Chunk Z                  | varint                               |                                                      |
| Sections                 | array[[section](#section)]           | `maxSection-minSection+1` entries                    |
| Number of Block Entities | varint                               | Number of entries in the following array             |
| Block Entities           | array[[block entity](#block-entity)] |                                                      |
| Heightmap Mask           | int                                  | A mask indicating which heightmaps are present       |
| Heightmaps               | array[bytes]                         | One heightmap for each bit present in Heightmap Mask |
| Length of user data      | varint                               | Number of entries in the following array             |
| User data                | array[byte]                          | Arbitrary user data segment                          |

<a name="section"></a>
### Section
| Name                      | Type          | Notes                                                                        |
|---------------------------|---------------|------------------------------------------------------------------------------|
| Is Empty                  | bool          | If set, nothing follows                                                      |
| Block Palette Size        | varint        |                                                                              |
| Block Palette             | array[string] | Entries are in the form `minecraft:block[key1=value1,key2=value2]`           |
| Block Palette Data Length | varint        | Only present if `Block Palette Size > 1`                                     |
| Block Palette Data        | array[long]   | Packed long array, see [Chunk format](https://minecraft.wiki/w/Chunk_format) |
| Biome Palette Size        | varint        |                                                                              |
| Biome Palette             | array[string] |                                                                              |
| Biome Palette Data Length | varint        | Only present if `Biome Palette Size > 1`                                     |
| Biome Palette Data        | array[long]   | Packed long array, see [Chunk format](https://minecraft.wiki/w/Chunk_format) |
| Block Light Data Content  | byte          | 0 = no lighting, 1 = all zero, 2 = all max, 3 = present after                |
| Block Light               | array[byte]   | A 2048 byte long nibble array, only present if above = 3                     |
| Sky Light Data Content    | byte          | 0 = no lighting, 1 = all zero, 2 = all max, 3 = present after                |
| Sky Light                 | array[byte]   | A 2048 byte long nibble array, only present if above = 3                     |

<a name="block-entity"></a>
### Block Entity
| Name            | Type   | Notes                                |
|-----------------|--------|--------------------------------------|
| Chunk Pos       | int    |                                      |
| Has ID          | bool   | If unset, Block Entity ID is omitted |
| Block Entity ID | string |                                      |
| Has NBT Data    | bool   | If unset, NBT Data is omitted        |
| NBT Data        | nbt    |                                      |

## Polar Paper specific
Polar Paper stores entities in the userdata section of each chunk.
Minestom Polar does not read these back by default
### Polar Paper userdata
| Name                      | Type          | Notes                                                    |
|---------------------------|---------------|----------------------------------------------------------|
| Version                   | byte          | Latest version = 2                                       |
| Number of Entities        | varint        |                                                          |
| Entities                  | array[entity] |                                                          |
| Persistent Data Container | array[byte]   | The Bukkit persistent data container serialized to bytes |

### Entity
| Name       | Type        | Notes |
|------------|-------------|-------|
| X Position | double      |       |
| Y Position | double      |       |
| Z Position | double      |       |
| Yaw        | float       |       |
| Pitch      | float       |       |
| NBT        | array[byte] |       |