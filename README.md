# Fractal Status - Backend

Spring Boot 4 monitoring backend. Infrastructure is modelled as a single
recursive tree; that tree *is* the layout rendered by the SPA. Each entity runs
pluggable checks (ping, http, ...) on their own virtual threads, and node state
(online / warning / offline) is derived from check output via SpEL and rolled up
through groups.

- Java 25, Spring Boot 4.1.0, Maven
- Base package: `ai.luumo.fractalstatus`

## Core concepts

- **Tree.** Every node shares `{ id, name, type }` plus optional `display`
  (layout metadata: icon, width/height grid units, a colour `scheme` name, and
  per-state colour overrides). Colour schemes are declared once at the top of the
  config and resolved into each node's effective `display.colors`.
  `group` nodes have `children` (and no config of their own); `entity` nodes have
  `config` (values + checks + state rules) and ephemeral `runtime`. `display` is a
  top-level sibling of `config`/`runtime` so it is also present in the
  lightweight status view.
- **Identifiers.** All ids / value keys / check names / output fields must match
  `[A-Za-z_][A-Za-z0-9_]*` (camelCase convention) so they are valid SpEL
  property names. The dotted **path** (`root.servers.pveHosts.pve01`) built from
  ids is therefore also a valid SpEL chain and is the key used for log
  filtering.
- **Config vs runtime.** The config file never contains runtime state. Runtime
  is ephemeral and held in memory; the API serves the two merged.
- **Interpolation.** Check config may contain `${this.<key>}` placeholders,
  resolved against the entity's `values` when the live check instance is built
  (plain substitution, not SpEL).
- **State rules (SpEL).** An entity's `config.state` maps check output to a
  state. Expressions read like `ping.reachable == false` or
  `http.statusCode != 200`, evaluated against a context exposing each check by
  name -> its structured output. Order: `offlineWhen`, then `warningWhen`, else
  `fallback`.
- **Group roll-up.** Groups have no rules: a group's state is always derived as
  the worst state of its children, applied bottom-up, so a failure anywhere in a
  subtree propagates up to every ancestor (including the root). Group
  `runtime.state` is still emitted (auto-derived) for tile colouring.

## API

Four tree views (see `TreeController`):

| Endpoint                      | Contents                                   |
|-------------------------------|--------------------------------------------|
| `GET /api/tree/config`        | #1 config exactly as supplied (raw JSON)   |
| `GET /api/tree/config-defaults` | #2 resolved config incl. defaults        |
| `GET /api/tree/full`          | #3 config + defaults + runtime             |
| `GET /api/tree/status`        | #4 tree + runtime only                     |

Central log (see `LogController`):

- `GET /api/logs?path=<prefix>&limit=<n>` - snapshot, filterable by path prefix
- `GET /api/logs/stream?path=<prefix>` - live SSE stream

Control (see `ControlController`):

- `GET /api/scheduler`, `POST /api/scheduler/start`, `POST /api/scheduler/stop`
- `POST /api/reload` - reload config from disk and restart checks

## Configuration

There are **two** JSON files:

1. **Application config** - `fractalstatus.json` (the app's own settings, incl.
   the listen port).
2. **Monitoring config** - the tree of groups/entities/checks (default
   `demo-config.json`, referenced by `fractalstatus.config-path`).

### Application config (`fractalstatus.json`)

Loaded early by `JsonConfigEnvironmentPostProcessor` and flattened into the
Spring environment. Resolution order:

1. the path in `fractalstatus.config-file` / env `FRACTALSTATUS_CONFIG_FILE`
   (a plain path is treated as a filesystem path; `classpath:`/`file:`/URL also
   accepted),
2. otherwise `./fractalstatus.json` next to the running app,
3. otherwise the bundled `classpath:fractalstatus.json` defaults.

Keys use the normal Spring property namespace, so command-line args, system
properties and env vars still override the file. Example:

```json
{
  "server": { "port": 8080 },
  "fractalstatus": {
    "config-path": "classpath:demo-config.json",
    "auto-start": true,
    "log": { "capacity": 5000 },
    "cors": { "allowed-origins": ["*"] }
  }
}
```

- `server.port` - HTTP listen port.
- `fractalstatus.config-path` - Spring resource for the monitoring config JSON
  (e.g. `file:/etc/fractalstatus/config.json`).
- `fractalstatus.auto-start` - start the scheduler on boot.
- `fractalstatus.log.capacity` - central log ring-buffer size.
- `fractalstatus.cors.allowed-origins` - CORS origins for the SPA.

To run on a different port, drop a `fractalstatus.json` next to the app (or point
`FRACTALSTATUS_CONFIG_FILE` at one) and set `server.port`.

A `demo-config.json` (the monitoring tree) is bundled for local development.

If you want a machine-local monitoring tree, create `./fractalstatus.json`
(ignored by Git) and point `fractalstatus.config-path` at a local file such as
`file:sample-config.local.json`.

### Monitoring config & colour schemes

The monitoring config file is wrapped as `{ schemes, defaultSchemes, tree }`:

```json
{
  "schemes": {
    "node": {
      "online": "oklch(0.56 0.13 152)", "warning": "oklch(0.63 0.14 68)",
      "offline": "oklch(0.55 0.17 25)", "unknown": "oklch(0.58 0.02 250)"
    }
  },
  "defaultSchemes": { "group": "node", "entity": "node" },
  "theme": { "explode": "oklch(0.49 0.06 245)", "collapse": "oklch(0.47 0.06 305)" },
  "tree": { "id": "root", "type": "group", "...": "..." }
}
```

- Define named palettes under `schemes` (state -> CSS colour string; hex,
  `oklch(...)`, `rgb(...)` etc. all pass through verbatim).
- `defaultSchemes` picks a scheme per node type, so most nodes need no annotation.
- A node/group can set `display.scheme: "<name>"` to pick a scheme, and
  `display.colors` to override individual states.
- The backend resolves scheme + overrides into each node's `display.colors`, so
  resolved API views carry the final palette (the SPA just reads `display.colors`).
- `theme` holds named UI-chrome colours that are not per-node statuses (e.g.
  control-tile colours); served via `GET /api/theme` as a flat name -> colour map.
- A bare-node config (top-level node, no wrapper) is still accepted; it just has
  no schemes/theme.

## Writing a check module

Implement `CheckModule` and annotate with `@Component`; it is auto-registered by
`id()`:

```java
@Component
public class PingCheckModule implements CheckModule {
    public String id() { return "ping"; }
    public CheckOutcome check(CheckContext ctx) {
        // ctx.config() is interpolated; ctx.log() is path-scoped
        return CheckOutcome.builder(true).put("reachable", true).put("rttMs", 12).build();
    }
}
```

Output field names must be camelCase so SpEL state rules can address them
(`ping.rttMs`). The scheduler runs one virtual thread per check instance.

> Bundled modules (all implemented):
> - **ping** - invokes the OS `ping`; config `host`, `count` [1], `timeoutMs` (ms)
>   or `timeout`s [2, fractional ok];
>   output `reachable`, `rttMs`, `packetLoss`.
> - **http** - status-code check with SSL validation; config `url`, `method`
>   [GET], `timeout`s [10], `followRedirects` [true], `verifySsl` [true],
>   `expectStatus` [200]; output `statusCode`, `responseTimeMs`, `sslValid`.
> - **http-body** - as `http` plus body matching; config adds `expectBody`,
>   `matchMode` (contains/equals/regex), `ignoreCase`; output adds `bodyMatched`.
>
> The HTTP modules set both connect and request timeouts so an endpoint that
> hangs indefinitely is abandoned rather than blocking the monitor.

## Build & run

```bash
./mvnw test        # run tests
./mvnw spring-boot:run
```
