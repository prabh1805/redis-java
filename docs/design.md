# Redis in Java — Design

A working Redis server written in Java. Real `redis-cli` connects to it. Real
`redis-benchmark` measures it. Plus a web dashboard for looking at what's
inside, which `redis-cli` can't give you.

---

## Why this project

Three properties the last project lacked:

**It's real from hour four.** Once RESP parsing and `SET`/`GET` work, you point
actual `redis-cli` at it and type commands. Not a mock, not a test harness — the
real client, speaking the real protocol. Every feature after that is usable the
moment it's written.

**The numbers are comparable.** `redis-benchmark` runs against your server and
against real Redis. You get ops/sec and p99 side by side with the thing you're
imitating. That beats self-reported numbers from a benchmark you also wrote.

**It layers.** Each stage is a working server. There is no 60% point where it's
useless — stopping after persistence still leaves something that runs.

---

## Scope

### In

| Area | What |
|---|---|
| Protocol | RESP2 — the wire format `redis-cli` speaks |
| Types | Strings, lists, hashes, sets, sorted sets |
| Expiry | `EXPIRE`, `TTL`, lazy deletion + active sampling |
| Persistence | AOF with fsync policies, RDB-style snapshots |
| Replication | Master → replica, full resync (partial if time allows) |
| Pub/sub | `SUBSCRIBE`, `PUBLISH` |
| Transactions | `MULTI`, `EXEC`, `DISCARD` |
| Admin API | HTTP, read-only, feeds the dashboard |
| Dashboard | Browse keys, inspect values, watch live stats |

### Out

- **Cluster mode** — sharding, hash slots, redirects. A separate project.
- **Lua scripting** — embedding an interpreter teaches you about Lua, not Redis.
- **Modules, streams, geo, bitmaps, HyperLogLog** — breadth, not depth.
- **ACLs and TLS** — plumbing.
- **RESP3** — RESP2 is what matters; RESP3 is incremental.

Breadth is the trap here. Thirty commands implemented well beats two hundred
implemented shallowly, and nobody is grading command coverage.

---

## Architecture

```
                    ┌──────────────────────────────┐
  redis-cli ───────▶│  RESP server      :6379      │
  redis-benchmark   │  ├── connection accept loop  │
                    │  ├── RESP parser             │
                    │  └── command dispatch        │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  Keyspace                    │
                    │  ├── HashMap<String, Value>  │
                    │  ├── expiry table            │
                    │  └── type implementations    │
                    └──────────────┬───────────────┘
                                   │
                    ┌──────────────▼───────────────┐
                    │  Persistence                 │
                    │  ├── AOF writer              │
                    │  └── snapshot writer         │
                    └──────────────────────────────┘

                    ┌──────────────────────────────┐
  browser ─────────▶│  Admin HTTP API   :8080      │──▶ reads keyspace
                    │  + static dashboard          │    (carefully — see below)
                    └──────────────────────────────┘
```

Two ports, two entry points, one keyspace. That separation matters: the RESP
server is the product, the admin API is an observation window that must not
disturb it.

---

## The central design decision: concurrency

This is the thing to get right, and it's the best interview material in the
project.

**Real Redis executes commands on a single thread.** One event loop, epoll,
commands run one at a time to completion. This sounds like a limitation and is
mostly a feature:

- Every command is atomic by construction. No locks anywhere.
- `INCR` is trivially correct — no compare-and-set, no race.
- No lock contention, no context switching between command executions.
- Cache locality is excellent.

The cost: one CPU core for command execution, and one slow command blocks
everything behind it.

### Three options in Java

**A. Single-threaded event loop** — `java.nio.Selector`, one thread, non-blocking
sockets. Closest to real Redis. Teaches you the most. Roughly 200 lines of
fiddly but well-understood code.

**B. Thread-per-connection with virtual threads** — Java 21. Blocking reads,
simple linear code, one virtual thread per client. But shared state now needs
locking, and command atomicity stops being free.

**C. Netty** — production-grade, handles the hard parts. Which is also the
problem: it hides the event loop you're trying to learn.

### The plan: build B, then A, and measure both

Start with virtual threads. The code is simple, commands work quickly, and you
reach a usable server fast. Then build the single-threaded event loop and run
the same benchmark against both.

That gives a genuine experiment rather than an assertion:

- Where does each model win?
- How much does lock contention cost under concurrent writes?
- What happens to p99 at 50, 200, 1000 connections?

"I implemented both concurrency models and measured the crossover" is a far
better answer than "I used an event loop because Redis does."

---

## Data structures

| Redis type | Java implementation | Why |
|---|---|---|
| String | `byte[]` | Redis strings are binary-safe; `String` would corrupt binary data |
| List | Doubly-linked list, or `ArrayDeque` | O(1) push/pop at both ends |
| Hash | `HashMap<byte[], byte[]>` wrapper | Direct mapping |
| Set | `HashSet` | Direct mapping |
| Sorted set | **Skip list + HashMap** | The interesting one |

The sorted set is worth the effort. It needs score→member ordering *and*
member→score lookup, so it's two structures kept in sync: a skip list ordered by
score for range queries, and a hash map for O(1) score lookup by member.

A skip list is a probabilistic balanced structure — simpler than a red-black
tree, nearly as fast, and you almost certainly haven't built one. Real Redis
uses exactly this, for exactly this reason.

---

## Key expiry

Naive approach: a background thread scanning every key for expiry. With ten
million keys that's unusable.

Redis uses two mechanisms together, and it's worth implementing both:

**Lazy deletion.** On every key access, check the TTL. Expired → delete, return
nil. Costs nothing when keys aren't accessed. Problem: a key nobody touches
never gets collected, so expired data occupies memory indefinitely.

**Active sampling.** Periodically (10Hz):

1. Take 20 random keys from the expiry table
2. Delete the expired ones
3. If more than 25% were expired, go to 1 immediately

The loop is the clever part. A high expired ratio implies many more are waiting,
so it keeps going; a low ratio means it stops and gives the time back. It's
probabilistic garbage collection with an adaptive rate.

Implement the naive version first and watch it fall over. That's the lesson.

---

## Persistence

### AOF — append-only file

Every write command is appended to a log in RESP format. Restart replays the
log. Simple, durable, and the file is human-readable.

The design question is **fsync policy**, and all three are worth supporting:

| Policy | Durability | Throughput | Notes |
|---|---|---|---|
| `always` | Lose nothing | Terrible | fsync per write, disk-bound |
| `everysec` | Lose ≤1s | Good | Redis's default |
| `no` | OS decides | Best | Lose whatever's in the page cache |

Measuring the throughput difference between these three is one of the better
numbers this project produces.

AOF grows forever, so it needs **rewriting**: periodically dump current state as
the minimal set of commands that reproduce it. Writes arriving during the
rewrite have to go somewhere — a buffer that's appended when the rewrite
finishes.

### Snapshots — the fork problem

Real Redis calls `fork()`. The child gets a copy-on-write view of memory and
writes it to disk while the parent keeps serving. The OS does the hard part.

**Java cannot fork.** This is a genuine constraint with no clean workaround, and
it forces a real decision:

- **Stop-the-world snapshot** — block writes, serialize, resume. Simple; a pause
  proportional to dataset size.
- **Copy-on-write by hand** — mark the keyspace immutable, write new values to an
  overlay, merge after. Complex, but no pause.
- **Iterate with versioning** — snapshot reads a consistent version while writes
  create new ones. MVCC, essentially.

Implementing stop-the-world first and *measuring the pause* makes the case for
the alternatives concrete. "Snapshotting 1M keys blocks writes for 340ms" is a
finding.

---

## Replication

Master accepts writes. Replicas connect, get a full snapshot, then receive a
stream of subsequent write commands.

- **Full resync** — replica connects, master snapshots and sends it, then streams
  the backlog accumulated during transfer
- **Partial resync** — replica reconnects with an offset; master replays from a
  ring buffer if the offset is still in range

Failure cases worth handling: replica falls behind and the backlog is exhausted
(→ full resync), master restarts mid-stream, network partition heals.

This is the most "distributed systems" part of the project and the best place
for an interview conversation about consistency — Redis replication is
asynchronous, so a replica can serve stale reads, and a master failing before
replication can lose acknowledged writes.

---

## Admin API and dashboard

The thing `redis-cli` can't do: see the whole keyspace at once, visually.

### Endpoints (HTTP, port 8080, read-only)

```
GET  /api/stats            ops/sec, memory, connected clients, uptime
GET  /api/keys?pattern=*   paginated key list with type and TTL
GET  /api/keys/{key}       the value, rendered by type
GET  /api/slowlog          slowest recent commands
GET  /api/stream           server-sent events: live ops/sec and key count
```

Read-only by design. Writes go through RESP, where they belong.

### The design constraint worth noting

If command execution is single-threaded, **the admin API must not touch the
keyspace directly**. A dashboard request scanning a million keys would block
every client.

Two correct approaches:

1. **Queue admin requests as commands** on the event loop, like any other
   command. Bounded, fair, and they take their turn.
2. **Maintain atomic counters** updated as commands execute, so stats need no
   keyspace access at all.

Use (2) for stats and (1) for key browsing, with a cursor-based scan that yields
between batches — the same reason real Redis has `SCAN` rather than `KEYS`.

This is a real design problem your dashboard creates, and solving it correctly is
worth more than the dashboard itself.

### The dashboard

Plain HTML plus a small amount of JavaScript. No framework. A live ops/sec
chart, a searchable key list, a value inspector that renders by type, and a
memory breakdown.

Its real job is making the server's behaviour visible — watching active expiry
collect keys in waves, watching ops/sec drop when AOF fsync is set to `always`,
watching a replica catch up.

---

## Build order

Each stage ends in a server that runs and does something you can see.

**1. RESP and a hash map**
Parse RESP, implement `PING`, `SET`, `GET`, `DEL`, `EXISTS`. Virtual thread per
connection.
*Done when:* `redis-cli -p 6379` connects and `SET foo bar` / `GET foo` work.

**2. Types**
Lists, hashes, sets, sorted sets with the skip list. Maybe 25 more commands.
*Done when:* `redis-cli` can drive every type, and `ZRANGEBYSCORE` returns
correct ranges.

**3. Expiry**
`EXPIRE`, `TTL`, `PERSIST`. Naive scan first, then lazy + active sampling.
*Done when:* a million keys with TTLs are collected without a CPU spike.

**4. The dashboard**
Admin API, stats counters, cursor-based key scan, the web UI.
*Done when:* you can watch active expiry collecting keys in real time.

**5. Persistence**
AOF with all three fsync policies, replay on startup, rewrite. Then snapshots.
*Done when:* `kill -9` mid-write loses no more than the policy allows.

**6. The concurrency experiment**
Build the single-threaded NIO event loop alongside virtual threads. Benchmark
both.
*Done when:* there's a table of ops/sec and p99 for both models at several
connection counts.

**7. Replication**
Master/replica, full resync, then partial.
*Done when:* a replica stays consistent with a master under sustained writes,
and recovers after being killed.

---

## What gets measured

All of it via `redis-benchmark`, with real Redis as the control:

| Metric | Why it matters |
|---|---|
| ops/sec, GET and SET | The headline comparison |
| p50 / p99 / p99.9 latency | Where GC pauses show up |
| ops/sec by fsync policy | The durability/throughput tradeoff, quantified |
| ops/sec by concurrency model | The experiment from stage 6 |
| Memory per key | Java object overhead vs C structs |
| Snapshot pause duration | The cost of not having fork() |
| Replica lag under load | Replication throughput |

Expect to land meaningfully below real Redis. That gap is the interesting part —
GC pauses, object headers, no zero-copy. Finding out exactly where it goes, with
a profiler, is better material than matching the number would have been.

---

## Open questions

- Single JVM only, or support running several as a cluster later?
- Keys as `byte[]` throughout (correct, binary-safe) or `String` (easier,
  subtly wrong for binary data)? Decide early — retrofitting is painful.
- How far to take RESP3? Probably not at all.
- Dashboard: poll on an interval, or server-sent events? SSE is nicer and
  costs little.
