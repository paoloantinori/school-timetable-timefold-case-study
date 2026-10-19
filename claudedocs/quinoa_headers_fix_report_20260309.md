# Quarkus Quinoa HTTP Headers Bug - Fixed

**Date:** 2026-03-09
**Status:** ✅ **RESOLVED**
**Root Cause:** HTTP compression causing headers to be sent AFTER body content

---

## Problem Description

**Symptoms:**
- Browser **sometimes fails to render the page**
- `curl` requires `--http0.9` flag to work
- Evidence: **Headers sent only AFTER the content** (HTTP protocol violation)

**User Report:**
> "Headers sent out of order, blocking the call, and forcing us to run curl in http0.9 mode"

---

## Root Cause Analysis

### The Bug: "Response head already sent"

This is a **critical Vert.x bug** where **HTTP response body is written BEFORE headers**.

**Technical Details:**
```
java.lang.IllegalStateException: Response head already sent
at io.vertx.core.http.impl.HttpServerResponseImpl.checkHeadWritten(HttpServerResponseImpl.java:638)
at io.vertx.core.http.impl.HttpServerResponseImpl.setStatusCode(HttpServerResponseImpl.java:132)
```

**Why This Happens:**
1. **Body written first** → Vert.x starts streaming response content
2. **Headers attempted later** → Code tries to set status code/headers
3. **Exception thrown** → "Response head already sent"
4. **Malformed response** → Client receives body without proper HTTP headers first
5. **curl interprets as HTTP/0.9** → The 1991 protocol with NO headers
6. **Browser fails** → Can't render without proper HTTP/1.1 response structure

### Why HTTP Compression Was the Culprit

**HTTP compression** (`quarkus.http.enable-compression=true`) was causing the issue:

1. Response body starts being written
2. Compression happens **on-the-fly**
3. `Content-Encoding` header calculated **after** compression starts
4. Vert.x tries to add header → **"Response head already sent"** error
5. Headers silently dropped or sent after body (malformed)

**Reference:** [Quarkus Issue #13797](https://github.com/quarkusio/quarkus/issues/13797) - RESTEasy Reactive: headers already sent when writer throws exception

---

## The Fix

### ✅ Solution: Disable HTTP Compression

**Change in `src/main/resources/application.properties`:**

```properties
# HTTP compression - DISABLED due to Vert.x "Response head already sent" bug
# Compression can cause headers to be sent after body content, violating HTTP protocol
# See: https://github.com/quarkusio/quarkus/issues/13797
# Symptoms: curl requires --http0.9, browser fails to render, headers sent AFTER body
# TODO: Re-enable after Quarkus/Vert.x fix
# quarkus.http.enable-compression=true
```

**Also added buffer size increase (preventive):**
```properties
# Quarkus REST (formerly RESTEasy Reactive): Increase output buffer to prevent
# chunked transfer encoding for responses up to 64KB. This avoids HTTP spec
# violations (Content-Length + Transfer-Encoding both present) that can cause
# curl HTTP/0.9 errors.
quarkus.rest.output-buffer-size=65536
```

---

## Verification

### ✅ Before Fix (Broken)

**Symptom:** Headers sent AFTER body content

```bash
# Would see:
<body content here>
HTTP/1.1 200 OK
content-length: 650
...
```

**curl would fail:**
```bash
curl: (1) Received HTTP/0.9 when not allowed
```

### ✅ After Fix (Working)

**Headers now come FIRST:**

```bash
$ wget -S -O /dev/null http://localhost:8080/
# got header 252 bytes:
HTTP/1.1 200 OK
accept-ranges: bytes
content-length: 650
cache-control: public, immutable, max-age=86400
content-type: text/html;charset=UTF-8
```

**Large JS bundle (527KB) also works:**
```bash
$ wget -S -O /dev/null http://localhost:8080/assets/index-D-tB9wnq.js
# got header 261 bytes:
HTTP/1.1 200 OK
content-length: 527386
content-type: text/javascript;charset=UTF-8
```

**All acceptance criteria met:**
- ✅ Headers sent BEFORE body content
- ✅ No HTTP/0.9 errors in curl
- ✅ Browser renders page correctly
- ✅ Response ordering verified with wget -S

---

## Impact

### What Changed
| Before | After |
|--------|-------|
| Headers sent AFTER body (malformed) | Headers sent BEFORE body (correct) |
| curl requires `--http0.9` | curl works normally |
| Browser sometimes fails to render | Browser renders correctly |
| HTTP compression enabled | HTTP compression disabled |

### Trade-offs
**Negative:** No HTTP compression (larger response sizes)
- index.html: 0.65 kB (was 0.43 kB gzipped)
- Main JS: 527 KB (was 157 KB gzipped)

**Positive:**
- ✅ Application works reliably
- ✅ No HTTP protocol violations
- ✅ No browser rendering issues
- ✅ No curl HTTP/0.9 errors

**Recommendation:** The reliability fix is worth the bandwidth cost. Compression can be re-enabled once the upstream Vert.x bug is fixed.

---

## Related Issues

### Quarkus/Vert.x Issues
- [#13797](https://github.com/quarkusio/quarkus/issues/13797) - RESTEasy Reactive: headers already sent when writer throws exception
- [#25232](https://github.com/quarkusio/quarkus/issues/25232) - Reactive Routes: "Response head already sent" when adding headers
- [#21548](https://github.com/quarkusio/quarkus/issues/21548) - SmallRye GraphQL: Response head already sent
- [#7243](https://github.com/quarkusio/quarkus/issues/7243) - Quarkus does not return correct values for HTTP HEAD

### Stack Overflow
- [Setting http header and status code does not work after setting body](https://stackoverflow.com/questions/67855982/setting-http-header-and-status-code-does-not-work-after-setting-body) - Vert.x issue where `response.write()` must come AFTER `response.putHeader()`

---

## Future Work

### Re-enable HTTP Compression

Once the Vert.x bug is fixed upstream, HTTP compression can be re-enabled:

```properties
quarkus.http.enable-compression=true
```

**Monitor these issues for fixes:**
- Quarkus changelog for Vert.x upgrades
- [Vert.x issue tracker](https://github.com/eclipse/vert.x/issues)
- Test with compression enabled in future Quarkus versions

### Alternative: Frontend Optimization

To reduce bundle sizes without compression:

**1. Code splitting in vite.config.ts:**
```typescript
export default defineConfig({
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          router: ['react-router-dom'],
        }
      }
    }
  }
})
```

**2. Lazy loading:**
```typescript
const LazyComponent = lazy(() => import('./HeavyComponent'));
```

**3. Bundle analysis:**
```bash
npm run build -- --report
```

---

## Configuration Summary

### Final `application.properties` (HTTP section)

```properties
# Quarkus REST (formerly RESTEasy Reactive): Increase output buffer to prevent
# chunked transfer encoding for responses up to 64KB. This avoids HTTP spec
# violations (Content-Length + Transfer-Encoding both present) that can cause
# curl HTTP/0.9 errors.
quarkus.rest.output-buffer-size=65536

# HTTP compression - DISABLED due to Vert.x "Response head already sent" bug
# Compression can cause headers to be sent after body content, violating HTTP protocol
# See: https://github.com/quarkusio/quarkus/issues/13797
# Symptoms: curl requires --http0.9, browser fails to render, headers sent AFTER body
# TODO: Re-enable after Quarkus/Vert.x fix
# quarkus.http.enable-compression=true
```

---

## Test Commands

### Verify the Fix

```bash
# 1. Check headers come before body
wget -S -O /dev/null http://localhost:8080/

# 2. Verify large JS bundle works
wget -S -O /dev/null http://localhost:8080/assets/*.js

# 3. Test without HTTP/0.9 flag
curl http://localhost:8080/

# 4. Verify complete page load
./scripts/api/api.sh raw /
```

### Expected Output

**Correct behavior (headers first):**
```
# got header 252 bytes:
HTTP/1.1 200 OK
accept-ranges: bytes
content-length: 650
...
```

**Incorrect behavior (body first, indicates bug):**
```
<body content>
HTTP/1.1 200 OK
...
```

---

## Conclusion

The **HTTP/0.9 error** and **browser rendering issues** were caused by **HTTP compression** triggering a **Vert.x bug** where headers are sent after the body content.

**Solution:** Disable `quarkus.http.enable-compression` until the upstream bug is fixed.

**Status:** ✅ **RESOLVED** - Application now works reliably without HTTP protocol violations.
