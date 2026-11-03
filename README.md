# Ore & Order

A Minecraft **26.3 / Fabric** strategy-mod prototype. Natural-language orders become validated resource-acquisition plans using a server-side model endpoint.

## Current milestone

This build implements **command → NVIDIA → validated task → in-game feedback**. It does not spawn workers, mine blocks, access chests, or execute plans yet. Plans are held in memory and cleared when the server stops. `base_storage` is a symbolic future destination, not a bound chest.

## Build

Use **JDK 25** and the included Gradle 9.6.0 wrapper:

```powershell
.\gradlew.bat build
```

The mod JAR is `build/libs/ore-and-order-0.1.0+mc26.3.jar`. Install Fabric Loader 0.19.5+ and Fabric API for Minecraft 26.3. Put the JAR in the server's `mods` directory, or your local instance's `mods` directory for singleplayer. The mod uses server-side commands and needs no custom client protocol.

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
/oreorder ask Get 16 iron ingots
/oreorder status
/oreorder cancel
```

The response shows a **preview plan**, not an executing worker job. Only acquisition of a registered non-air item is accepted, in quantities 1–4096. Default quantity is 16. Unsupported tasks fail validation.

Requests run off the server thread. Limits: 500-character orders, a 10-second per-player cooldown, two concurrent provider requests, configurable 45-second timeout, 64 KiB response cap, and 1,024 output tokens. Errors report sanitized messages; provider bodies, keys, and player orders are not logged. Requests are not automatically retried. Cancellation invalidates the result but an already-started request may finish and consume provider quota.

Player order text is sent to the configured provider. This milestone sends no world snapshot, player name, or inventory. NVIDIA's catalog access is for trial/development use; review hosting terms before releasing a production service.

## Verification

`build` runs task validation tests and actual HTTP requests against a local fixture covering success, rejected credentials, rate limits, provider errors, redirects, malformed/truncated/oversized responses, missing configuration, and timeout behavior. No external API calls occur during normal builds.

An explicit smoke test sends one fixed order to NVIDIA using the environment key:

```powershell
.\gradlew.bat livePlannerTest
```

Development client: `.\gradlew.bat runClient`. Development server: `.\gradlew.bat runServer` (review and accept Minecraft's EULA in the generated run directory if required).

## Next milestone

Add one persistent worker with an inventory and a designated chest; implement deterministic navigation, mining and delivery before connecting validated plans to execution. Then add tool handling, recovery and smelting.

## References

- [Fabric 26.3 tooling](https://fabricmc.net/2026/09/15/263.html)
- [NVIDIA model card](https://build.nvidia.com/nvidia/nemotron-3.5-super-vl-120b-a12b/modelcard)
- [NVIDIA API reference](https://docs.api.nvidia.com/nim/reference/nvidia-nemotron-3_5-super-vl-120b-a12b)
