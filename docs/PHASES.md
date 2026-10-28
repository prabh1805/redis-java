# PHASES.md

Build order. Each phase ends in a server that runs and does something visible.

**Status: nothing started.** Update the checkboxes as phases complete.

---

## Before phase 1 — the groundwork

- [x] Understand what Redis is, why it exists, how it differs from a relational
      database, and where using it is a mistake
- [] Read the RESP2 spec (redis.io/docs/reference/protocol-spec) — short, and
  the whole protocol
- [x] Install real Redis locally. It's the reference implementation and the
      benchmark control; every behavioural question gets answered by trying it
      against the real one first
- [x] Project scaffolding: plain Java 21, Maven or Gradle, **no Spring**

No Spring anywhere in this project. It brings a DI container, an HTTP stack and
an opinionated lifecycle, none of which a Redis server wants. The admin API in
phase 4 uses the JDK's built-in HTTP server. Writing plain Java here is part of
the point.

---

## Phase 1 — RESP and a hash map

**Goal:** `redis-cli` connects and basic commands work.

**Concepts to cover first**

- TCP sockets in Java — `ServerSocket`, accept loops, what a connection is
- Blocking I/O, and what blocks where
- Virtual threads — what they are, why one per connection is viable now
- RESP2 — why a binary-ish protocol beats JSON here, how length prefixes avoid
  parsing ambiguity

**Build**

- Accept loop, one virtual thread per connection
- RESP parser: simple strings, errors, integers, bulk strings, arrays
- RESP serializer
- Command dispatch
- `PING`, `ECHO`, `SET`, `GET`, `DEL`, `EXISTS`
- Keyspace as `HashMap<byte[]-wrapper, Value>`

**Decide early:** keys as `byte[]`, not `String`. Redis keys are binary-safe and
retrofitting this later means touching everything. A wrapper class with proper
`equals`/`hashCode` is needed since arrays don't hash by content.

**Done when:** `redis-cli -p 6379` connects, `SET foo bar` returns `OK`,
`GET foo` returns `bar`, and `redis-cli` doesn't complain about malformed
replies.

---

## Phase 2 — Data types

**Goal:** all five Redis types, driven from `redis-cli`.

**Concepts to cover first**

- Why Redis is a "data structure server" rather than a key-value store
- Skip lists — structure, probabilistic balancing, why not a red-black tree
- The two-structure problem for sorted sets: ordering by score _and_ O(1)
  lookup by member

**Build**

- Lists: `LPUSH` `RPUSH` `LPOP` `RPOP` `LRANGE` `LLEN`
- Hashes: `HSET` `HGET` `HGETALL` `HDEL`
- Sets: `SADD` `SREM` `SMEMBERS` `SISMEMBER`
- Sorted sets: `ZADD` `ZSCORE` `ZRANGE` `ZRANGEBYSCORE` `ZREM` — skip list plus
  hash map
- `TYPE`, and a wrong-type error for every command

**Done when:** every type works from `redis-cli`, `ZRANGEBYSCORE` returns
correct ranges, and `LPUSH` on a string key returns a `WRONGTYPE` error.

---

## Phase 3 — Expiry

**Goal:** keys expire without a background scan eating the CPU.

**Concepts to cover first**

- Why scanning every key doesn't scale
- Lazy deletion — cheap, but expired keys hold memory indefinitely
- Active sampling — Redis's adaptive probabilistic sweep, and why the
  25%-retry loop is the clever part

**Build**

- `EXPIRE` `TTL` `PERSIST` `SETEX`
- Separate expiry table: key → expiry timestamp
- Naive full scan **first**, deliberately
- Measure it with a million keys, watch it fall over
- Replace with lazy deletion plus active sampling at 10Hz

**Done when:** a million keys with TTLs are collected steadily, with no CPU
spike and no unbounded memory growth. The before/after numbers from the naive
version are worth keeping.

---

## Phase 4 — The dashboard

**Goal:** see what the server is doing.

**Concepts to cover first**

- Why `KEYS *` is dangerous and `SCAN` exists — cursor-based iteration
- The constraint the admin API creates: it must not block command execution
- Server-sent events vs polling

**Build**

- JDK `HttpServer` on port 8080
- Atomic counters for stats, updated during command execution — no keyspace
  access needed
- Cursor-based key scan that yields between batches
- `GET /api/stats`, `/api/keys`, `/api/keys/{key}`, `/api/stream`
- Dashboard: plain HTML and JavaScript, no framework. Live ops/sec chart, key
  browser, value inspector

**Done when:** you can watch active expiry collecting keys in waves, live, in a
browser.

---

## Phase 5 — Persistence

**Goal:** survive a restart. Survive `kill -9` with a known, bounded loss.

**Concepts to cover first**

- Write-ahead logging and why appending is fast
- `fsync`, the page cache, and what durability actually guarantees
- The durability/throughput tradeoff, which the fsync policies make concrete
- `fork()` and copy-on-write — what Redis does, and why Java can't

**Build**

- AOF: append write commands in RESP format
- fsync policies: `always`, `everysec`, `no` — all three, benchmarked
- Replay on startup
- AOF rewrite: compact to the minimal command set, buffering writes during the
  rewrite
- Snapshots: stop-the-world first, measure the pause, then decide whether a
  copy-on-write approach is worth building

**Done when:** `kill -9` during sustained writes loses no more than each policy
permits, and there's a table of ops/sec per fsync policy.

---

## Phase 6 — The concurrency experiment

**Goal:** two concurrency models, measured against each other.

**Concepts to cover first**

- Why real Redis runs commands on one thread, and what that buys — atomicity
  without locks
- `select` / `epoll` / `kqueue`, and what `java.nio.Selector` wraps
- Non-blocking I/O, partial reads, and buffering across reads
- Where lock contention actually costs under the virtual-thread model

**Build**

- Single-threaded NIO event loop as an alternative to virtual threads
- A switch to run either
- Same `redis-benchmark` against both, at 1 / 10 / 50 / 200 / 1000 connections

**Done when:** there's a table of ops/sec and p99 for both models at several
connection counts, and an explanation of where the crossover is and why.

This is the strongest interview material in the project — an experiment with a
result, not a preference.

---

## Phase 7 — Replication

**Goal:** a replica that stays consistent and recovers from being killed.

**Concepts to cover first**

- Asynchronous replication and the consistency it gives up
- What is lost when a master dies before replicating an acknowledged write
- Replication backlog as a ring buffer, and what happens when it's exhausted

**Build**

- `REPLICAOF` — replica connects to master
- Full resync: snapshot transfer, then the backlog accumulated during transfer
- Command stream to replicas
- Partial resync via offsets, if time allows
- Replica serves reads, refuses writes

**Done when:** a replica stays consistent with a master under sustained writes,
and recovers correctly after being killed and restarted.

---

## Phase 8 — Measure and write it up

**Goal:** numbers, next to real Redis.

**Build**

- `redis-benchmark` runs against both your server and real Redis
- Record: ops/sec (GET and SET), p50/p99/p99.9, memory per key, ops/sec per
  fsync policy, ops/sec per concurrency model, snapshot pause duration, replica
  lag
- Profile where the gap against real Redis comes from — expect GC, object
  headers, lack of zero-copy
- README with the design decisions, the results, and reproduction steps

**Done when:** someone else can clone the repo and reproduce the results table.

---

## Stopping points

Every phase leaves a working server. If interest or time runs out:

- **After phase 2** — a working in-memory Redis with all five types. Thin but real.
- **After phase 4** — the above plus expiry and a dashboard. A complete small project.
- **After phase 6** — the strongest stopping point. Persistence and a measured
  concurrency experiment is a genuinely good portfolio piece.
- **After phase 8** — the full thing.

Phases 1–6 are the project. Phases 7–8 are the extension.
