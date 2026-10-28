# Design: redis-java

## Purpose

`redis-java` is a Redis-compatible server written from scratch in Java 21. Its initial goal is to accept TCP clients, speak enough RESP2 to interoperate with Redis tools, and provide a small foundation for implementing Redis commands.

This document describes the current design and separates existing behavior from planned work. Redis compatibility is incremental; the project does not currently aim to implement the full Redis command set or persistence.

## Goals and non-goals

### Goals

- Accept multiple TCP clients on the Redis default port, 6379.
- Handle each client connection independently using Java virtual threads.
- Read and parse RESP command requests while preserving argument bytes.
- Keep protocol parsing separate from command execution and network lifecycle.
- Add command behavior incrementally with focused tests.

### Non-goals for the initial version

- Full Redis or RESP2 feature parity.
- Persistence, replication, clustering, authentication, or transactions.
- Production-grade resource limits, observability, or high availability.

## Architecture

The server is organized around a small request path:

1. `Server` binds a `ServerSocket` to port 6379 and accepts client sockets.
2. A virtual thread handles each accepted connection, allowing clients to be served independently.
3. `ByteReader` buffers the socket input and exposes byte-oriented reads, including fixed-length reads.
4. `RespParser` reads a RESP command array and returns its arguments as `List<byte[]>`.
5. The connection handler writes a RESP reply to the socket.

The parser returns bytes rather than decoding every argument as text so that binary-safe command values remain possible. Command dispatch and data storage are not yet separate components; they should be introduced when commands move beyond the current placeholder response.

## Current behavior and limitations

- The listener uses a fixed port and has no configurable bind address or port.
- Each connection is read in a loop on a virtual thread.
- The parser currently supports command arrays containing bulk-string arguments. It is not a general RESP2 value parser.
- The server currently prints parsed arguments and responds with `+PONG\r\n` for every parsed command. It does not validate command names or execute Redis operations.
- There is no key-value store, expiration handling, persistence, or graceful server shutdown.
- Malformed and incomplete protocol input is only partially validated. The parser should reject invalid lengths, unexpected line endings, and truncated input before it is treated as a valid command.
- The repository does not yet contain tracked automated tests for the networking or protocol path.

These constraints are intentional for an early implementation, but they must be addressed before claiming broad Redis compatibility or production readiness.

## Error and connection handling

An invalid or incomplete request must not be dispatched as a command. Protocol errors should produce a well-formed RESP error reply when the connection can still be used; otherwise the server should close that client connection. A client disconnect should end only that client's handler, not the accept loop or other client handlers.

As command handling is added, command-level errors should be distinct from parser and socket errors. This keeps malformed wire input, valid but unsupported commands, and I/O failures observable as different cases.

## Planned evolution

1. Add parser and byte-reader tests for valid commands, empty arguments, fragmented input, malformed framing, and disconnects.
2. Introduce a command dispatcher that validates command names and argument counts and emits RESP replies.
3. Implement a minimal in-memory key-value store and a small set of commands, starting with common connectivity and string operations.
4. Add protocol-aware error replies, connection cleanup, and a configurable server lifecycle.
5. Expand compatibility only alongside tests using `redis-cli` and documented supported commands.

Each step should preserve a clear boundary between socket I/O, RESP parsing/encoding, command execution, and storage.

## Verification

Unit tests should cover byte buffering, parser framing, command dispatch, and RESP encoding without requiring a running server. Integration tests should start the server on an available port, exchange RESP requests over a socket, and verify responses and connection behavior. Manual interoperability checks with `redis-cli` are useful during development, but do not replace automated tests.