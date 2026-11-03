# Verification — 2026-11-03

## Worker milestone (0.2.0)

`build runGameTest` passed with the toolchain below: 77 planner/HTTP checks and all 8 required Minecraft server tests (7 worker tests plus Fabric's registration test). Worker tests exercise real navigation, mining two ore blocks, loot pickup, tool wear and chest delivery; full-storage retention; cancellation; unsuitable tools and area limits; inaccessible ore; entity/binding serialization; and item-transfer conservation. The command parser is checked with a namespaced item ID.

The world-start ordering and exact standing-cell navigation are exercised by these tests. Minecraft server tests do not verify the client renderer visually or mouse targeting in a live client. Those remain manual in-game checks. This milestone does not change the provider HTTP client or require a new live NVIDIA request.

## Initial command milestone (0.1.0)

Target: Minecraft 26.3, Fabric Loader 0.19.5, Fabric API 0.163.0+26.3, Loom 1.17.21, Gradle 9.6.0, JDK 25.

- `build`: passed, producing the mod JAR and source JAR.
- `integrationTest`: passed 77 checks, including strict task fields, registered-item predicate, quantity boundaries, actual local HTTP serialization/authentication, 401/403/429/500, refused redirects, malformed envelopes, incomplete output, oversized bodies, missing credentials, input limits and timeout behavior.
- `livePlannerTest`: passed against `https://integrate.api.nvidia.com/v1/chat/completions` with `nvidia/nemotron-3.5-super-vl-120b-a12b`. A fixed request for 16 iron ingots returned a valid acquisition plan for `minecraft:iron_ingot`, quantity 16, destination `base_storage`. The key was resolved from the server environment and was not printed or committed.

Local fixtures exercise the production HTTP client, not a stub of its methods. They represent chosen provider success/failure responses, not a guarantee of all NVIDIA behavior. Postman CLI/simulation and Orbit lookup were not used successfully in this environment; provider details were checked against NVIDIA's documentation and the live request.

Not verified in a running Minecraft world: command registration, permission checks, chat delivery, cooldown behavior and late-response suppression after cancellation. Those paths compile against the exact Minecraft/Fabric dependencies. No NPC execution exists in this milestone.
