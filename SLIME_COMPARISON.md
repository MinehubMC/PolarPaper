# World size

Polar by default uses ZSTD with compression level 7 and includes light data by default

Including light data makes world files larger, however decreases CPU usage when loading the world as it avoids the light engine

Using compression level 22 is usually not practical as it takes much longer and much more memory to save, but it's a fun test regardless

## Pacer Impossible 12
Very small map (4 chunks), mostly air, no entities

| Format | Size (kB) | Compared to Slime |
| - | - | - |
| Slime | 2.3 | 1.00x |
| Polar | 1.3 | 0.57x |
| Polar (Compression Level 22) | 1.1 | 0.48x |

## Survival Games Marsh University
Medium sized map (24x24 chunks), some entities

| Format | Size (kB) | Compared to Slime |
| - | - | - |
| Slime | 1800 | 1.00x |
| Polar | 1400 | 0.78x |
| Polar No Light | 945 | 0.53x |
| Polar Compression Level 22 | 1100 | 0.62x |
| Polar Compression Level 22 No Light | 781 | 0.43x |

## Overworld
8x8 chunks of vanilla overworld, no entities

| Format | Size (kB) | Compared to Slime |
| - | - | - |
| Slime | 936 | 1.00x |
| Polar | 798 | 0.85x |
| Polar No Light | 661 | 0.71x |
| Polar Compression Level 22 | 656 | 0.70x |
| Polar Compression Level 22 No Light | 548 | 0.59x |
