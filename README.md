# CClaims: A container claim mod for fabric
Claims is a Fabric server-side mod to claim and protect container blocks (e.g. Chest, Barrels and Hoppers).

This mod provides the `/cclaim` command to work the claims.
Permissions can be managed by [LuckPerms](https://github.com/LuckPerms/LuckPerms) or any other mod implementing the [fabric-permissions-api](https://github.com/lucko/fabric-permissions-api).

**Features:**
- Supported Container Blocks: Chests, Copper Chests, Barrels, Hoppers, Shelfs, Brewing Stands and Beacons
- Prevents usage and destruction of claimed blocks by other players (except admin mode players)
  - Prevents destruction by explosions, headless pistons (aka. the bedrock breaking method) or the Ender dragon
- Players can add ("trust") players to their claims.

**Note:**
Claimed hoppers will push items into unclaimed containers. Therefore, you should not have claimed hoppers facing an unclaimed block (including normal non-container blocks that cannot be claimed).

## Commands
- `/cclaim claim`: Claims the container the player is facing
- `/cclaim unclaim`: Unclaims the container the player is facing
- `/cclaim trust <player>`: Trusts a player to use the claimed container. Trusted players can only use the container, but neither destroy the block nor manage the claim.
- `/cclaim untrust <player>`: Untrusts a player from a claimed container
- `/cclaim info`: Shows the current claim status of the container the player is facing
- `/cclaim adminmode`: Toggles if the player is in admin mode. The mod treats players in admin mode like they own every claim.
- `/cclaim list [<dimension>] [all]`: Lists the coordinates of all container claims in a dimension (defaults to the overworld). By default, the list is paginated, but this can be turned off by adding "all" as an argument.
## Permissions
- `cclaim.adminmode`: Required to use `/cclaim adminmode`. By default, it requires OP Level 3
- `cclaim.list`: Required to use `/cclaim list`. By default, it requires OP Level 2
- `cclaim.info.admin`: Allows to get all data using `/cclaim info` on claims owned by other players and additionally shows the timestamp of claim creation. By default, it requires OP Level 2
## Storage
The claim data is stored in the NBT data of the block entities.
Additional metadata is stored in `world/data/cclaims.dat`.
## License
This project is licensed under the GNU Lesser General Public License version 3 only.