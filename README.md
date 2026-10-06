<div align="center">
<img width="150" height="150" alt="polar-logo" src="https://github.com/user-attachments/assets/84a8df1c-52c9-42e9-8446-fab7192e5aac" />
 
# Polar world format for Paper
</div>

[![](https://dcbadge.limes.pink/api/server/n7fp52auB7)](https://discord.gg/n7fp52auB7)


Polar is a world format very similar to Slime, with the same advantages:
 - Small file sizes (see [SLIME_COMPARISON](SLIME_COMPARISON.md))
 - Single file world
 - Immutable (worlds do not save until explicitly requested)
 - Store worlds wherever (whether as a file or in a database)
 - Great for lobbies and game arenas
 - Fast loading and saving

Polar is also a single plugin and so doesn't require a Paper fork or classloaders 

Polar currently supports versions 26.3 - 1.21.11, and requires minimum Java 25

### [Download the latest jar](https://github.com/MinehubMC/PolarPaper/releases/latest)

Polar was originally developed for [Minestom](https://github.com/Minestom/Minestom)! See the Minestom library [here](https://github.com/hollow-cube/polar)

## Large worlds
Polar is not designed for large worlds! 

Polar is most useful when the whole world is mostly within render distance. The entire world is held in memory as a trade-off to allow for faster loading. This means the size/number of worlds you can load depends on how much RAM you have available. 

If you need large worlds, it's likely Anvil (the default world format) will be more useful.

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
[See the wiki](https://github.com/MinehubMC/PolarPaper/wiki)
