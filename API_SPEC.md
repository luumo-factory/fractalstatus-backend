# Fractal Status - Backend API Specification

Authoritative contract for the SPA. Reflects the backend on `main` as of this
commit. Base URL (dev): `http://localhost:8080`. All payloads are JSON unless
noted. CORS is enabled for `/api/**` (any origin by default, configurable via
`fractalstatus.cors.allowed-origins`), including the SSE stream.

> Status note: the "defaults materialisation" layer is still pending, so
> `/api/tree/config-defaults` currently returns the authored config normalised
> through the object model plus the computed `path` and the few primitive/enum
> defaults that are always present (see notes below). It will gain more
> explicit defaults later without changing the response shape.

---

## 1. Endpoints

| Method | Path | Query params | Body | Success | Errors |
|--------|------|--------------|------|---------|--------|
| GET | `/api/tree/config` | - | - | 200 (raw JSON), 204 if tree not loaded | - |
| GET | `/api/tree/config-defaults` | - | - | 200, 204 if not loaded | - |
| GET | `/api/tree/full` | - | - | 200, 204 if not loaded | - |
| GET | `/api/tree/status` | - | - | 200, 204 if not loaded | - |
| GET | `/api/logs` | `path` (optional, string), `limit` (optional, int, default `200`) | - | 200 (array) | 400 if `limit` not an int |
| GET | `/api/logs/stream` | `path` (optional, string) | - | 200 (`text/event-stream`) | - |
| GET | `/api/theme` | - | - | 200 (name -> CSS colour map; `{}` if none) | - |
| GET | `/api/scheduler` | - | - | 200 | - |
| POST | `/api/scheduler/start` | - | - | 200 | - |
| POST | `/api/scheduler/stop` | - | - | 200 | - |
| POST | `/api/reload` | - | - | 200 | 500 if config cannot be read |

Common error statuses (all use the error envelope in section 5):
`400` bad parameter, `404` unknown path, `405` wrong method, `500` server error.

### Query param semantics
- `path` (logs + stream): filters by **path prefix**. A node path matches if it
  equals the prefix or is nested beneath it (prefix followed by `.`). Omit /
  blank = everything. Example: `path=root.servers` matches
  `root.servers.pveHosts.pve01` and `root.servers`.
- `limit` (logs): max entries returned, newest last. `<= 0` means unlimited.

---

## 2. JSON response shapes

### 2.1 The node tree (recursive)

Every node has a `type` of `"group"` or `"entity"`. Field presence depends on
the endpoint/view:

| Field | config (raw) | config-defaults | full | status |
|-------|:---:|:---:|:---:|:---:|
| `id`, `name`, `type` | yes (as authored) | yes | yes | yes |
| `path` | no | yes | yes | yes |
| `display` | as authored | yes | yes | **yes** |
| `config` (entities only) | n/a (it *is* the config) | yes | yes | no |
| `runtime` | no | no | yes | yes |
| `children` (group only) | yes | yes | yes | yes |

Notes:
- `/api/tree/config` returns the config file **exactly as authored**. Its
  top-level shape is the wrapper `{ schemes, defaultSchemes, tree }` (see section
  2.3.1); the node tree lives under `tree`, with no `path`, no `runtime`,
  `${this.*}` templates intact, and `display` colours/defaults not materialised.
  Endpoints #2-#4 return the node tree directly (the root node), not the wrapper.
- `path` is the dotted id chain, e.g. `root.servers.pveHosts.pve01`.
- `display` is **layout metadata shared by group and entity** and is a top-level
  sibling of `config`/`runtime` (not nested under `config`) specifically so it is
  available in the lightweight `status` view. See section 2.3.
- Leaf containers: a `group` always has a `children` array (possibly empty); an
  `entity` never has `children`.

#### Group node (full view)
```json
{
  "id": "pveHosts",
  "name": "Proxmox Hosts",
  "type": "group",
  "path": "root.servers.pveHosts",
  "runtime": { "state": "online" },
  "children": [ /* nodes */ ]
}
```
Groups have **no `config`**. Group `runtime` contains only `state`, which is
**derived automatically** as the worst state of the group's children (applied
bottom-up, so a failure anywhere in the subtree propagates to every ancestor,
including the root). There are no group-level rules.

#### Entity node (full view)
```json
{
  "id": "pve01",
  "name": "pve-01",
  "type": "entity",
  "path": "root.servers.pveHosts.pve01",
  "config": {
    "values": { "ipAddress": "10.0.1.41", "hostname": "pve-01" },
    "checks": [
      {
        "name": "ping",
        "module": "ping",
        "config": { "host": "${this.ipAddress}", "interval": 30 }
      }
    ],
    "state": {
      "offlineWhen": "ping.reachable == false",
      "warningWhen": "ping.rttMs > 100",
      "fallback": "online"
    }
  },
  "runtime": {
    "state": "online",
    "checks": [
      {
        "name": "ping",
        "lastUpdate": "2026-10-03T15:26:48.680036511Z",
        "online": true,
        "output": { "reachable": true, "rttMs": 0.128, "packetLoss": 0.0 },
        "message": "192.168.88.1 reachable, rtt 0.128 ms"
      }
    ]
  },
  "type": "entity"
}
```

#### Entity node (status view) - config omitted
```json
{
  "id": "pve01",
  "name": "pve-01",
  "type": "entity",
  "path": "root.servers.pveHosts.pve01",
  "runtime": {
    "state": "online",
    "checks": [
      {
        "name": "ping",
        "lastUpdate": "2026-10-03T15:26:48.680036511Z",
        "online": true,
        "output": { "reachable": true, "rttMs": 0.128, "packetLoss": 0.0 },
        "message": "192.168.88.1 reachable, rtt 0.128 ms"
      }
    ]
  }
}
```

### 2.2 Field reference

**Groups have no `config`.** A group's state is always derived (worst of its
children); there are no group-level rules.

**`config` (entity)** = `EntityConfig`:
- `values` (object, always present; arbitrary key -> scalar/string)
- `checks` (array of `CheckConfig`, always present, may be empty)
  - `CheckConfig`: `name` (string), `module` (string), `config` (object;
    module-specific, may contain `${this.*}` templates)
- `state` (`StateRules`, optional)
  - `StateRules`: `offlineWhen` (string, optional), `warningWhen` (string,
    optional), `fallback` (state enum, always present, defaults to `"online"`)

**`runtime` (group)** = `{ "state": <State> }` - `state` is derived (worst of
children), not configured.

**`runtime` (entity)** = `{ "state": <State>, "metric": {value,unit}?, "checks": [CheckResult...] }`
- `metric` (object, optional - **entities only**; omitted when no healthy value) -
  the primary display metric for the tile: `{ "value": string (pre-formatted),
  "unit": string (may be "") }`. Defaults per module: **ping** ->
  `rttMs` to 2dp + `"ms"`; **http**/**http-body** -> `statusCode` + `"HTTP"`.
  Override per entity via `display.metric` (see 2.3). Groups have no metric.
- `CheckResult`:
  - `name` (string)
  - `lastUpdate` (ISO-8601 instant)
  - `online` (boolean) - the module's primary health signal
  - `output` (object, always present, may be `{}`) - module-specific fields,
    camelCase keys. All current modules always include their fields (with
    sentinels on error, e.g. `statusCode: 0`) so SpEL state rules never hit a
    missing key:
    - **ping** -> `{ "reachable": bool, "rttMs": number (ms, average; present
      only when reachable), "packetLoss": number (percent) }`
    - **http** -> `{ "statusCode": int (0 on connect/timeout/SSL error),
      "responseTimeMs": number, "sslValid": bool }`
    - **http-body** -> `{ "statusCode": int, "responseTimeMs": number,
      "sslValid": bool, "bodyMatched": bool }`
  - `message` (string, optional - omitted when null)

> Implemented modules: `ping`, `http`, `http-body`. Config keys:
> - **ping**: `host` (required, supports `${this.*}`), `count` (default 1),
>   `timeoutMs` milliseconds (per-reply wait; takes precedence) or `timeout`
>   seconds (may be fractional; default 2).
> - **http** / **http-body** (shared): `url` (required), `method` (default GET),
>   `timeout` seconds (default 10; bounds connect AND request so nothing hangs
>   indefinitely), `followRedirects` (default true), `verifySsl` (default true -
>   validates the certificate chain and hostname). **http** adds `expectStatus`
>   (default 200). **http-body** adds `expectStatus`, `expectBody`, `matchMode`
>   (`contains` [default] / `equals` / `regex`), `ignoreCase` (default false).
> Field names are stable; values are not yet real.

### 2.3 Display (layout metadata) - on every node

Shared shape on both group and entity nodes. Present in `config-defaults`,
`full`, and `status` (and in raw `config` where authored).

```json
"display": {
  "icon": "hard-drive",
  "width": 1,
  "height": 1,
  "scheme": "node",
  "colors": {
    "online": "#2E7D32", "warning": "#F9A825",
    "offline": "#C62828", "unknown": "#9E9E9E"
  }
}
```

- `icon` (string, optional) - **Lucide** icon name, kebab-case
  (https://lucide.dev). Treated as an opaque string by the backend; omitted when
  not set.
- `width` (int, grid units) - **always present in resolved views; defaults to 1.**
- `height` (int, grid units) - **always present in resolved views; defaults to 1.**
- `scheme` (string, optional) - name of a colour scheme declared at the top of
  the config (see section 2.3.1). Present where authored.
- `metric` (object, optional, entities only) - override for the primary display
  metric (`runtime.metric`): `{ "check": string?, "field": string?, "unit":
  string?, "decimals": int? }`. If `field` is set its value is shown (formatted to
  `decimals` when numeric); otherwise the check module's default metric is used,
  with `unit` still able to override the unit. `check` selects which check to read
  (default: the entity's first check).
- `colors` (object, optional) - per-state palette with keys `online` /
  `warning` / `offline` / `unknown`. **In the resolved views (`config-defaults`,
  `full`, `status`) this is the fully materialised effective palette** (scheme +
  per-state overrides). In raw `config` it shows only authored overrides. Colour
  values are **opaque CSS colour strings** passed through verbatim - hex,
  `oklch(...)`, `rgb(...)`, named colours, etc. are all fine.

### 2.3.1 Colour schemes (top of config)

The monitoring config is wrapped as `{ schemes, defaultSchemes, tree }`. Schemes
are named palettes; nodes pick one via `display.scheme` (or by the per-type
default), and any `display.colors` entries override individual states. The
backend resolves this into each node's `display.colors` for the resolved views,
so **the SPA can simply read `display.colors`** and does not need to understand
schemes.

```json
{
  "schemes": {
    "node": {
      "online": "oklch(0.56 0.13 152)", "warning": "oklch(0.63 0.14 68)",
      "offline": "oklch(0.55 0.17 25)", "unknown": "oklch(0.58 0.02 250)"
    }
  },
  "defaultSchemes": { "group": "node", "entity": "node" },
  "theme": {
    "explode": "oklch(0.49 0.06 245)", "collapse": "oklch(0.47 0.06 305)"
  },
  "tree": { "...root node...": "..." }
}
```

(Groups and entities both use the single status palette - `defaultSchemes.group`
points at the same `node` scheme.)

`schemes` is exposed verbatim in raw `config` (#1) but resolved away in #2-#4.
A bare-node config (no wrapper) is still accepted and simply carries no schemes.

### 2.3.2 Theme colours (`GET /api/theme`)

UI chrome colours that are **not** per-node statuses (e.g. control-tile colours)
live in a top-level `theme` block in the config wrapper and are served as a flat
name -> CSS-colour map:

```
GET /api/theme  ->  200
{
  "explode":  "oklch(0.49 0.06 245)",
  "collapse": "oklch(0.47 0.06 305)"
}
```

Static for the life of a loaded config, so fetch it once. Returns `{}` if no
`theme` block is configured. The keys are whatever the config author defines
(arbitrary named colours), not a fixed set.

### 2.4 Logs query - `GET /api/logs`

Returns an **array** of log entries (newest last):
```json
[
  {
    "seq": 24,
    "ts": "2026-10-03T15:26:55.664962488Z",
    "level": "info",
    "path": "root.domains",
    "message": "state unknown -> online"
  }
]
```
- `seq` (number) - monotonic sequence id (also used as the SSE event id)
- `ts` (ISO-8601 instant)
- `level` (log-level enum)
- `path` (string) - node path the entry relates to, or `"system"` for
  application-wide entries
- `message` (string)

### 2.5 Scheduler - `GET /api/scheduler`, `POST /api/scheduler/{start,stop}`
```json
{ "running": true }
```

### 2.6 Reload - `POST /api/reload`
```json
{ "reloaded": true, "running": true }
```
Stops the scheduler, reloads config from disk, and restarts it if it was
running. Returns `500` (error envelope) if the config resource cannot be read.

---

## 3. SSE - `GET /api/logs/stream`

- `Content-Type: text/event-stream`. No server-side timeout (stream stays open).
- Optional `path` query param filters by prefix (same semantics as `/api/logs`).
- Each entry is emitted as one SSE event:

```
id:24
event:log
data:{"seq":24,"ts":"2026-10-03T15:26:55.664962488Z","level":"info","path":"root.domains","message":"state unknown -> online"}
```

- **Event name:** `log` (subscribe with `source.addEventListener('log', ...)`,
  not the default `message` handler).
- **id:** the entry `seq`.
- **data:** a single `LogEntry` JSON object (same shape as section 2.4).

**Backlog / reconnection behaviour (important):**
- The stream delivers only entries produced **after** the subscription. There is
  no initial snapshot pushed on connect.
- `Last-Event-ID` is **not** currently honoured for replay; a reconnecting
  `EventSource` resumes with live entries only (gap during disconnect is not
  backfilled).
- Recommended client pattern: on connect/reconnect, call `GET /api/logs?path=...`
  for the recent backlog, then attach the stream for live updates. De-duplicate
  by `seq` if needed.

---

## 4. Enums & formats

- **state** (`runtime.state`, `StateRules.fallback`): `"unknown"`, `"online"`,
  `"warning"`, `"offline"`. `unknown` is the initial state before a check has
  produced a result.
- **log level**: `"debug"`, `"info"`, `"warn"`, `"error"`.
- **node type**: `"group"`, `"entity"`.
- **display icons**: agreed set is **Lucide** (kebab-case names); opaque strings
  to the backend.
- **date/time**: ISO-8601 UTC instants with nanosecond precision and trailing
  `Z`, e.g. `2026-10-03T15:26:48.680036511Z`. Treat as opaque ISO strings;
  `new Date(...)` parses them fine (sub-millisecond precision is truncated).

---

## 5. Error envelope

Errors use the standard Spring Boot JSON shape (no `message` field by default):
```json
{
  "timestamp": "2026-10-03T15:26:55.683Z",
  "status": 404,
  "error": "Not Found",
  "path": "/api/tree/nope"
}
```
Examples observed: `400 Bad Request` (bad `limit`), `404 Not Found` (unknown
route), `405 Method Not Allowed` (wrong verb; includes an `Allow` header),
`500 Internal Server Error` (e.g. reload failure).

---

## 6. Background: the fractal/recursive model & UX

**The tree is the layout.** The entire system is one recursive tree of nodes.
The nesting *is* how the status page should be drawn: nested blocks, grouped to
arbitrary depth. Rearranging nodes in the config rearranges the UI.

- **Groups** are containers (`children`) with no config of their own. A group's
  state is **always** the worst state of its children, applied bottom-up, so a
  failure anywhere in the subtree (however deeply nested) propagates up to every
  ancestor including the root.
- **Entities** are the monitored things. Each runs one or more **checks**
  (ping/http/...), each producing structured `output`. The entity's `state` is
  derived from that output via SpEL rules (`offlineWhen`, then `warningWhen`,
  else `fallback`).
- **State rolls up** from entities through ancestor groups to the root.

**Identifier rule:** every `id` / value key / check name / output field matches
`[A-Za-z_][A-Za-z0-9_]*` (camelCase), so they are valid SpEL names and the
dotted `path` is a valid key.

**Suggested UX**
- Render nested blocks; colour by `state` (e.g. unknown=grey, online=green,
  warning=amber, offline=red). `unknown` = not yet checked.
- Drill into groups; show entity checks and their `output`/`message` on expand.
- A log panel driven by `/api/logs` + `/api/logs/stream`. Because every log
  entry carries a node `path`, selecting any node (or group) can filter the log
  to that subtree via the `path` prefix - giving global log at the root down to
  a single entity.
- Use `/api/tree/status` for the live status view (smallest payload, has
  structure + state + check results), `/api/tree/full` when you also need
  config, and `/api/tree/config` to show/edit the raw authored config.
- `/api/scheduler` + start/stop and `/api/reload` back an admin/control panel.
