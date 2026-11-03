# Ore & Order

A Minecraft **26.3 / Fabric** strategy-mod prototype. Give a worker natural-language mining orders; it walks to exposed ore, mines it, picks up real drops, and delivers them to storage.

## Current milestone

Version **0.2.0** connects validated model plans to one owned worker per player. Supports raw iron, raw copper, raw gold, coal, diamond, emerald, redstone and lapis lazuli. Mining uses normal ore loot and tool durability. No smelting, crafting, tunnelling or combat yet: ask for **raw iron**, not iron ingots.

## First in-game test

Use a flat, dry test area with a few exposed iron ore blocks, a chest, and a stone pickaxe or better. Leave clear walking routes around the ore and chest. Enable cheats or use operator level 2.

1. `/oreorder worker spawn` creates your worker nearby.
2. Hold the pickaxe and run `/oreorder worker equip` within 8 blocks. This transfers the actual tool from your hand.
3. Look at the chest (within 6 blocks) and run `/oreorder worker storage`. Barrels also work; a double chest binds the half you point at.
4. Look at one corner of the mining area and run `/oreorder worker area pos1`, then look at the opposite corner and run `/oreorder worker area pos2`. Corners include their blocks. The area is limited to 16 × 16 × 16 blocks. You can also enter coordinates: `/oreorder worker area pos1 <x> <y> <z>`.
5. `/oreorder ask Get 2 raw iron` starts planning and then execution.
6. `/oreorder status` shows the planner result and worker progress. `/oreorder cancel` stops the job and ignores late planner results. `/oreorder worker unequip` returns the tool when idle.

For a reproducible test without an API call: `/oreorder worker mine minecraft:raw_iron 2`. This runs the same worker controller directly.

Workers stop with a message when the tool is unsuitable, storage is full/missing, paths are blocked, ore runs out, chunks unload, or they take damage. Cancelled/failed jobs retain carried items; uncollected drops remain in the world. Clearing a problem and submitting a new order uses any matching items already carried. Existing items in the chest do not count toward a new order. Fortune may produce more than the requested amount.

Worker entities, inventory, tools, ownership, storage, and area bindings persist with the world. Jobs do not resume automatically after world reload; submit a new order. Workers do not force-load chunks. Keep the area and storage loaded while testing. Ordinary survival damage applies; a dead worker drops its inventory/tool, and a replacement can be spawned.

## Build

Use **JDK 25** and the included Gradle 9.6.0 wrapper:

```powershell
.\gradlew.bat build
```

The mod JAR is `build/libs/ore-and-order-0.2.0+mc26.3.jar`. Install Fabric Loader 0.19.5+ and Fabric API for Minecraft 26.3. Replace the previous Ore & Order JAR; do not leave both versions installed. **Install on both client and server** for multiplayer, because this version adds a registered worker entity and its client renderer. In singleplayer, install in your local instance's `mods` directory.

## NVIDIA configuration

The server process must inherit `NVIDIA_API_KEY`. Do not put the key in the repository, chat, a client packet, or the properties file. If you set it after starting a launcher or server, restart that process so it inherits the variable.

On server startup the mod creates `config/ore-and-order.properties`:

```properties
endpoint=https://integrate.api.nvidia.com/v1/chat/completions
model=nvidia/nemotron-3.5-super-vl-120b-a12b
apiKeyEnv=NVIDIA_API_KEY
timeoutSeconds=45
```

Restart the server after editing configuration. Alternative compatible endpoints can be configured; HTTPS is required except for loopback HTTP. NVIDIA's `enable_thinking=false` option is sent only to its hostname. For local endpoints requiring no authentication, set the configured environment variable to a non-secret placeholder. Model compatibility must be tested separately.

## In-game use

Commands require game-master permissions (operator level 2 / singleplayer cheats enabled) so other players cannot freely consume the server's API quota.

```text
/oreorder
/oreorder ask Get 16 raw iron
/oreorder status
/oreorder cancel
```

The response starts the worker after plan validation and setup checks. Acquisition plans require a registered non-air item in quantities 1–4096 (default 16). The executor accepts the supported ore drops listed above and rejects unsupported tasks with an explanation. It never runs model-generated Minecraft commands.

Requests run off the server thread. Limits: 500-character orders, a 10-second per-player cooldown, two concurrent provider requests, configurable 45-second timeout, 64 KiB response cap, and 1,024 output tokens. Errors report sanitized messages; provider bodies, keys, and player orders are not logged. Requests are not automatically retried. Cancellation invalidates the result but an already-started request may finish and consume provider quota.

Player order text is sent to the configured provider. This milestone sends no world snapshot, player name, or inventory. NVIDIA's catalog access is for trial/development use; review hosting terms before releasing a production service.

## Verification

`build` runs task validation tests, local HTTP integration checks and Minecraft server game tests. The HTTP fixture covers success, rejected credentials, rate limits, provider errors, redirects, malformed/truncated/oversized responses, missing configuration, and timeout behavior. No external API calls occur during normal builds. Run the Minecraft tests separately with `.\gradlew.bat runGameTest`.

An explicit smoke test sends one fixed order to NVIDIA using the environment key:

```powershell
.\gradlew.bat livePlannerTest
```

Development client: `.\gradlew.bat runClient`. Development server: `.\gradlew.bat runServer` (review and accept Minecraft's EULA in the generated run directory if required).

## Next milestone

Add smelting, improved job recovery and richer worker interaction. Then expand to more workers, shared task queues and crafting dependencies.

## References

- [Fabric 26.3 tooling](https://fabricmc.net/2026/09/15/263.html)
- [NVIDIA model card](https://build.nvidia.com/nvidia/nemotron-3.5-super-vl-120b-a12b/modelcard)
- [NVIDIA API reference](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3_5-super-vl-120b-a12b)
