# CardboardMC

CardboardMC is a Paper fork aimed at making day-to-day server operation smoother: better performance headroom where it matters, clearer visibility when something is going wrong, and small quality-of-life features that reduce guesswork.

This repository contains the full source for `paper-api` and `paper-server`.

## Notable changes

### Expanded `/tps` monitoring

`/tps` still shows TPS for `1m, 5m, 15m`. On CardboardMC it also includes a few extra, lightweight subcommands that are meant to be safe to run in production and easy to paste into a support thread:

- `/tps mem`
  - Shows JVM memory usage.
  - Permission: `bukkit.command.tpsmemory`
- `/tps entities`
  - Shows total entities, block entities, tickable block entities, and players.
  - Includes a per-world breakdown.
  - Permission: `bukkit.command.tpsentities`
- `/tps chunks`
  - Shows total loaded chunks and a per-world breakdown.
  - Permission: `bukkit.command.tpschunks`
- `/tps gc`
  - Shows total GC collection count/time, plus deltas since the last `/tps gc`.
  - Permission: `bukkit.command.tpsgc`

## Building from source

To compile CardboardMC, you need JDK 21 and an internet connection.

From the repository root:

```bash
./gradlew applyPatches
./gradlew createMojmapBundlerJar
```

You can find the compiled output in `paper-server/build/libs`.

To see available tasks:

```bash
./gradlew tasks
```

## Contributing

See `CONTRIBUTING.md`.

## Credits

CardboardMC is based on Paper and inherits upstream licensing and attribution. See `LICENSE.md` and `paper-server/LICENCE.txt`.
