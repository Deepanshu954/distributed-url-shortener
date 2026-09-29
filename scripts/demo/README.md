# TinyScale System Demo & Smoke Test

This directory contains `demo.sh`, which exercises the core distributed functionality of the URL Shortener end-to-end via `curl`.

## Prerequisites

Start the application with zero Docker setup:
```bash
./scripts/run-local.sh
```
Or run the backend JAR directly:
```bash
java -jar -Dapp.sharding.enabled=false backend/target/url-shortener-2.0.0.jar
```

## Running the Demo

```bash
./scripts/demo/demo.sh
```

## Tested System Flows

1. **Health Check**: Validates `/actuator/health` probe status (`UP`).
2. **Distributed ID Generation**: Creates a short link with 64-bit Snowflake ID encoded into Base62 (`POST /api/links`).
3. **Custom Alias Creation**: Shortens link with customized marketing handle.
4. **302 Redirection**: Queries short code and validates `Location:` HTTP header.
5. **Asynchronous Click Telemetry**: Validates non-blocking analytical event consumption.
6. **Cache Invalidation on Update**: Updates destination URL and confirms synchronous cache eviction across all cache layers.
7. **Deletion & Eviction**: Deletes link and verifies immediate `404 Not Found`.
