# World size

Polar by default uses ZSTD with compression level 7 and includes light data by default

Including light data makes world files larger, however decreases CPU usage when loading the world as it avoids the light engine

Using compression level 22 is usually not practical as it takes much longer and much more memory to save, but it's a fun test regardless

Anvil is the default world format used by Minecraft (.mca in region folder)

## Pacer Impossible 12
Very small map (4 chunks), mostly air, no entities

| Format | Size (kB) | Compared to Slime |
| - | - | - |
| Slime | 2.3 | 1.00x |
| Polar | 1.3 | 0.57x |
| Polar (Compression Level 22) | 1.1 | 0.48x |
| Anvil | 185.9 | 80.83x |

## Survival Games Marsh University
Medium sized map (24x24 chunks), some entities

| Format | Size (kB) | Compared to Slime |
| - | - | - |
| Slime | 1800 | 1.00x |
| Polar | 1400 | 0.78x |
| Polar No Light | 945 | 0.53x |
| Polar Compression Level 22 | 1100 | 0.62x |
| Polar Compression Level 22 No Light | 781 | 0.43x |
| Anvil | 7892 | 4.38x |

## Overworld
18x18 chunks of vanilla overworld, no entities

| Format | Size (kB) | Compared to Slime |
| - | - | - |
| Slime | 936 | 1.00x |
| Polar | 798 | 0.85x |
| Polar No Light | 661 | 0.71x |
| Polar Compression Level 22 | 656 | 0.70x |
| Polar Compression Level 22 No Light | 548 | 0.59x |
| Anvil | 3280 | 3.50x |
