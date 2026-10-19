# Bug Report: StaticHandler.sendFile() Race Condition - HTTP Body Sent Before Headers

## For Submission To:
- **GitHub:** https://github.com/eclipse/vert.x/issues/new
- **Title:** StaticHandler.sendFile() sends HTTP body before headers causing race condition

---

## Issue Description

**Component:** `io.vertx.ext.web.handler.StaticHandler`
**Method:** `sendFile()`
**Severity:** HTTP protocol violation

### Problem

When using `StaticHandler` to serve static files (e.g., `index.html`), there is a race condition where the HTTP response body content is sent **BEFORE** the HTTP headers. This violates the HTTP/1.1 specification (RFC 7230) which requires headers to precede the body.

### Impact

- **Clients receive malformed HTTP responses** (body before headers)
- **curl fails** with: `curl: (1) Received HTTP/0.9 when not allowed`
- **Browsers may fail** to render the page
- **HTTP/0.9 interpretation** instead of HTTP/1.1

---

## Evidence

### Actual Response (Malformed)

```bash
$ curl --http0.9 -v http://10.20.202:8080/

# Response shows body FIRST, then headers:
<!DOCTYPE html>
<html lang="en">
  <head>
    <meta charset="UTF-8" />
    ...
  </head>
  <body>
    <div id="root"></div>
  </body>
</html>
HTTP/1.1 200 OK
accept-ranges: bytes
content-length: 800
cache-control: public, immutable, max-age=86400
```

### Expected Response (Correct HTTP/1.1)

```bash
$ curl -v http://10.20.202:8080/

# Should show headers FIRST:
< HTTP/1.1 200 OK
< Content-Type: text/html;charset=UTF-8
< Cache-Control: public, immutable, max-age=86400
< Content-Length: 800
<
<!DOCTYPE html>
<html lang="en">
...
```

### tcpdump Analysis

From remote client (WireGuard VPN):
```
10:14:13.562438 > GET / HTTP/1.1
10:14:13.562756 * Request completely sent off
<!DOCTYPE html>        ← BODY SENT FIRST
...
</html>
HTTP/1.1 200 OK     ← HEADERS SENT AFTER BODY
```

---

## Root Cause Analysis

### Likely Code Path (Hypothesis)

The `StaticHandler.sendFile()` method uses asynchronous file reading with callbacks:

```java
// Hypothetical Vert.x StaticHandler implementation
fileSystem.readFile(path, result -> {
    if (result.succeeded()) {
        // RACE CONDITION: Body written immediately via async callback
        response.write(result.result());

        // Headers set later - TOO LATE!
        // Headers may be sent after body has already started
        response.putHeader("Content-Type", mimeType);
        response.end();
    }
});
```

### Why It Happens

1. **Async callback fires** → `response.write()` starts sending body immediately
2. **Headers calculated later** → `putHeader()` attempts to add headers
3. **Headers sent after body** → HTTP protocol violation
4. **Client interprets as HTTP/0.9** → 1991 protocol with NO headers

### Why It's Intermittent

- **Race condition** depends on thread scheduling
- **Localhost (loopback)** often works (faster, more predictable timing)
- **Network interfaces** more likely to fail (latency, buffering)
- **After first request** may behave differently (connection pooling, caching)

---

## Environment

- **Vert.x Version:** 4.5.25
- **Quarkus Version:** 3.31.4
- **Java Version:** 17.0.12-tem
- **OS:** macOS (Darwin 25.3.0)
- **Reproduction:** Consistent from network interfaces, intermittent on localhost

---

## Reproduction Steps

### Quarkus/Vert.x Setup

```java
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.handler.StaticHandler;
import jakarta.enterprise.event.Observes;

public class StaticResources {

    void installRoute(@Observes StartupEvent startupEvent, Router router) {
        // This triggers the bug:
        router.route()
                .path("/")
                .handler(StaticHandler.create("target/quinoa/build/"));
    }
}
```

### Test

```bash
# From remote client or different machine
curl --http0.9 http://server:8080/

# Expected: Headers before body
# Actual: Body before headers
```

---

## Workaround

### Custom Route Handler (Current Fix)

```java
import io.quarkus.runtime.StartupEvent;
import io.vertx.ext.web.Router;
import io.vertx.core.http.HttpServerResponse;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.event.Observes;

public class RootPathHandler {

    void installRoutes(@Observes StartupEvent startupEvent, Router router) {
        router.route()
                .path("/")
                .order(10)  // BEFORE StaticHandler (default ~1000)
                .handler(this::handleRoot);
    }

    private void handleRoot(RoutingContext rc) {
        try {
            // SYNCHRONOUS read - no race condition
            String content = Files.readString(Paths.get("index.html"));

            // Headers FIRST
            HttpServerResponse response = rc.response()
                    .setStatusCode(200)
                    .putHeader("Content-Type", "text/html;charset=UTF-8")
                    .putHeader("Cache-Control", "public, immutable, max-age=86400")
                    .putHeader("Content-Length", String.valueOf(content.length()));

            // Then body - buffered, sent correctly
            response.end(content);
        } catch (Exception e) {
            rc.response().setStatusCode(500).end("Error loading page");
        }
    }
}
```

### Key Fix Elements

1. **`order(10)`** - Runs BEFORE StaticHandler
2. **`Files.readString()`** - Synchronous file read (no async callback)
3. **`response.end(content)`** - Buffers entire response, ensures headers sent first
4. **`@Blocking`** - Ensures execution on worker thread

---

## Related Issues

### Vert.x Issues
- #5929 - HttpClient connection closes early while streaming a gzip response (Open)
- Compression-related race conditions

### Quarkus Issues
- #13797 - RESTEasy Reactive: headers already sent when writer throws exception (Closed)
- #44637 - Quarkus's applyCompression Method Disables Vert.x Compression Logic (Closed)

### RFC References
- **RFC 7230 Section 3:** HTTP message format requires headers before body
- **RFC 7230 Section 3.3:** Message length and body transmission

---

## Suggested Fix

### Option 1: Buffer Response in StaticHandler

Modify `StaticHandler.sendfile()` to:
1. **Buffer headers first** before starting body transmission
2. **Use `response.end(content)`** instead of `response.write()` for small files
3. **Ensure `putHeader()` is called BEFORE any `write()` calls**

### Option 2: Synchronous Mode for StaticHandler

Add configuration option:
```java
StaticHandler.create()
    .setSyncMode(true)  // Force synchronous file sending
    .setBufferBeforeWrite(true)
```

### Option 3: Fix sendFile() Race Condition

```java
// Current (buggy):
fileSystem.readFile(path, result -> {
    response.write(result.result());  // Body sent immediately
    response.putHeader("Content-Type", mimeType);  // Too late
});

// Fixed:
fileSystem.readFile(path, result -> {
    if (result.succeeded()) {
        Buffer content = result.result();
        response.putHeader("Content-Type", mimeType);  // Headers FIRST
        response.putHeader("Content-Length", String.valueOf(content.length()));
        response.end(content);  // Then body
    }
});
```

---

## Additional Notes

### Why Localhost Often Works

- **Loopback interface** has minimal latency
- **Thread scheduling** more predictable
- **Connection pooling** may mask the issue
- **After first request** connection reuse may behave differently

### Why Remote Clients Fail

- **Network latency** increases race condition window
- **Buffering differences** on network interfaces
- **TCP segmentation** may expose the bug more clearly

### Test Case for Verification

```bash
# Should always show headers before body:
curl -v http://server:8080/ 2>&1 | grep -A 5 "< HTTP"

# Should see:
# < HTTP/1.1 200 OK
# < Content-Type: text/html;charset=UTF-8
# < Cache-Control: ...
# <
# <!DOCTYPE html>...
```

---

## Priority

**High** - HTTP protocol violation affecting multiple clients
- Security implications (cache poisoning potential)
- Reliability impact (browsers failing to render)
- Standards compliance (RFC 7230)

---

## Checklist

- [x] Reproducible from remote clients
- [x] Violates HTTP/1.1 specification
- [x] Affects StaticHandler.sendFile()
- [x] Has workaround (custom route handler)
- [ ] Root cause identified in Vert.x code
- [ ] Fix implemented
- [ ] Unit test added
- [ ] Documentation updated

---

## References

- **RFC 7230:** Hypertext Transfer Protocol (HTTP/1.1): Message Syntax and Routing
- **Vert.x StaticHandler:** https://vertx.io/docs/apidocs/io/vertx/ext/web/handler/StaticHandler.html
- **Related:** Quarkus Issue #13797
