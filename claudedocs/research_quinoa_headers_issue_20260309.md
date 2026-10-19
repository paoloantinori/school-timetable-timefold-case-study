# Quarkus Quinoa Headers Issue Research Report

**Date:** 2026-03-09
**Status:** Comprehensive Analysis Complete
**Confidence:** High (multiple sources, official documentation, GitHub issues)

## Executive Summary

Your issue with **headers being sent out of order, causing curl to fail with HTTP/0.9 errors** is caused by **RESTEasy Reactive's chunked transfer encoding behavior** when serving large HTML files through Quinoa. This is a known issue with multiple related bugs in Quarkus.

---

## Root Cause Analysis

### The Problem

When Quinoa serves your built React app (likely a large `index.html` with embedded JS):

1. **File exceeds buffer size** (~8191 bytes default)
2. **RESTEasy Reactive switches to chunked encoding**
3. **Bugs in header handling** cause:
   - Both `Content-Length` AND `Transfer-Encoding: chunked` headers (RFC 7230 violation)
   - Duplicate `Transfer-Encoding: chunked` headers
   - Headers sent in wrong order

### Why HTTP/0.9 Error?

Curl (since version 7.66.0) rejects responses without proper HTTP headers. When Quarkus sends:
- Malformed headers due to chunking bugs
- Both Content-Length and Transfer-Encoding (HTTP spec violation)

Curl interprets this as HTTP/0.9 (the original 1991 protocol with no headers) and fails with:
```
curl: (1) Received HTTP/0.9 when not allowed
```

---

## Key Findings from Research

### 1. RESTEasy Reactive Buffer Issue

**Source:** [Stack Overflow #70498150](https://stackoverflow.com/questions/70498150/resteasy-reactive-and-content-length-in-response-headers)

- **Behavior:**
  - Responses < buffer size: `Content-Length` header set
  - Responses >= buffer size: `Transfer-Encoding: chunked` used
  - **Buffer size increased from 256 to 8191 bytes in Quarkus 2.7.0.CR1**

- **Configuration:**
  ```properties
  quarkus.resteasy-reactive.output-buffer-size=65536  # Default: 8191
  ```

### 2. HTTP Spec Violation Bug

**Source:** [Quarkus Issue #26464](https://github.com/quarkusio/quarkus/issues/26464)

- **Issue:** RESTEasy Reactive adds `Transfer-Encoding: chunked` even when `Content-Length` is manually set
- **RFC 7230 states:** "Messages MUST NOT include both a Content-Length header field and a non-identity transfer-coding"
- **Status:** Reported, unclear if fully resolved

### 3. Duplicate Transfer-Encoding Headers

**Source:** [Quarkus Issue #49875](https://github.com/quarkusio/quarkus/issues/49875)

- **Issue:** Responses contain duplicate `Transfer-Encoding: chunked` headers
- **Impact:** Breaks strict HTTP clients (proxies, security scanners)
- **Affected versions:** Quarkus 3.23.4, 3.26.2 (possibly more)

### 4. REST Client Header Propagation Bug

**Source:** [Quarkus Issue #29059](https://github.com/quarkusio/quarkus/issues/29059)

- **Issue:** When REST Client Reactive propagates headers from backend, it adds `Content-Length` even when `Transfer-Encoding: chunked` exists
- **Result:** Both headers present (RFC violation)

### 5. Quinoa Version-Specific Issues

**Source:** [Quinoa Issue #960](https://github.com/quarkiverse/quarkus-quinoa/issues/960)

- **Issue:** index.html serving broken between Quarkus 3.27.2 and 3.28.0
- **Related:** SPA routing issues since Quarkus 3.9 (Issue #666)

### 6. Quinoa Requires RESTEasy Reactive

**Source:** [Quinoa Discussion #791](https://github.com/quarkiverse/quarkus-quinoa/discussions/791)

- **Critical finding:** Quinoa is **NOT compatible** with `quarkus-resteasy` (Classic)
- **You MUST use:** `quarkus-resteasy-reactive` (or `quarkus-rest` in Quarkus 3.15+)
- **Migration:** From `quarkus-resteasy` → `quarkus-resteasy-reactive` or `quarkus-rest`

---

## Solutions and Workarounds

### ✅ Solution 1: Increase Output Buffer Size (RECOMMENDED)

**Most effective immediate fix.** Increase the buffer so your index.html fits entirely:

```properties
# In src/main/resources/application.properties
# Note: For Quarkus 3.15+ with quarkus-rest extension, use:
quarkus.rest.output-buffer-size=65536
# For older versions with quarkus-resteasy-reactive, use:
# quarkus.resteasy-reactive.output-buffer-size=65536
```

**Why this works:**
- Forces `Content-Length` instead of chunked encoding
- Avoids the buggy chunking code path entirely
- No HTTP spec violations

**Test your index.html size:**
```bash
ls -lh target/quinoa-app/quarkus-run.jar  # Or check extracted index.html
# Or check after build:
du -h target/quinoa-build/index.html
```

Set buffer to **2x your largest HTML file** for safety.

---

### ✅ Solution 2: Optimize Frontend Build

Reduce your `index.html` size to fit within default buffer:

**A. Code splitting:**
```typescript
// vite.config.ts or webpack config
export default {
  build: {
    rollupOptions: {
      output: {
        manualChunks: {
          vendor: ['react', 'react-dom'],
          // Split large libraries into separate chunks
        }
      }
    }
  }
}
```

**B. Disable source maps in production:**
```typescript
export default {
  build: {
    sourcemap: false  // Reduces bundle size
  }
}
```

**C. Use compression (server-side):**
```properties
quarkus.http.enable-compression=true
```

---

### ✅ Solution 3: Quarkus Version Upgrade

**Current versions may have fixes:**

Your project uses **Quarkus 3.31** (from pom.xml). Check if issues are resolved in:

- **Quarkus 3.15+** (renamed RESTEasy Reactive → Quarkus REST)
- **Latest Quarkus 3.x** (check changelog for RESTEasy fixes)

**Migration guide:** [Quarkus REST Migration](https://quarkus.io/guides/rest-migration)

```xml
<!-- OLD (Quarkus < 3.15) -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive-jackson</artifactId>
</dependency>

<!-- NEW (Quarkus 3.15+) -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-rest-jackson</artifactId>
</dependency>
```

---

### ⚠️ Solution 4: Disable Chunking (Advanced)

**NOT RECOMMENDED** - May break streaming responses, but for completeness:

```java
// Custom filter to force Content-Length
@Provider
@Priority(Priorities.ENTITY_CODING)
public class ForceContentLengthFilter implements ContainerResponseFilter {

    @Override
    public void filter(ContainerRequestContext requestContext,
                     ContainerResponseContext responseContext) {
        // Remove Transfer-Encoding if Content-Length is set
        if (responseContext.getHeaderString("Content-Length") != null) {
            responseContext.getHeaders().remove("Transfer-Encoding");
        }
    }
}
```

---

### ✅ Solution 5: Nginx Reverse Proxy (Production)

In production, use Nginx to handle headers properly:

```nginx
location / {
    proxy_pass http://localhost:8080;
    proxy_http_version 1.1;
    proxy_set_header Connection "";
    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;

    # Let Nginx handle chunking properly
    chunked_transfer_encoding on;
}
```

---

## Best Practices for Quinoa

### 1. Always Use RESTEasy Reactive (or Quarkus REST)

```xml
<!-- CORRECT -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy-reactive</artifactId>
</dependency>

<!-- WRONG - Not compatible with Quinoa -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-resteasy</artifactId>
</dependency>
```

### 2. Configure Buffer Size Appropriately

```properties
# For production apps with large bundles (Quarkus 3.15+)
quarkus.rest.output-buffer-size=65536

# For development with smaller bundles (Quarkus 3.15+)
%dev.quarkus.rest.output-buffer-size=16384

# For older Quarkus versions with quarkus-resteasy-reactive:
# quarkus.resteasy-reactive.output-buffer-size=65536
```

### 3. Monitor Response Sizes

```bash
# Check your production bundle sizes
./scripts/api/api.sh raw / | grep -i "content-length"

# Or use verbose curl
curl -I http://localhost:8080/
```

### 4. Enable Compression

```properties
quarkus.http.enable-compression=true
quarkus.http.compress-media-types=text/html,text/css,application/javascript,application/json
```

### 5. Use Latest Quarkus and Quinoa

```xml
<!-- Check for latest versions -->
<quarkus.version>3.31.2</quarkus.version>
<quinoa.version>2.6.1</quinoa.version>
```

---

## Action Items for Your Project

### Immediate (High Priority)

1. ✅ **Add buffer size configuration:**
   ```properties
   # In src/main/resources/application.properties
   # For Quarkus 3.15+ with quarkus-rest:
   quarkus.rest.output-buffer-size=65536
   # For older versions with quarkus-resteasy-reactive:
   # quarkus.resteasy-reactive.output-buffer-size=65536
   ```

2. ✅ **Verify pom.xml dependencies:**
   ```bash
   grep -E "resteasy|quarkus-rest" pom.xml
   ```
   Ensure you're using `quarkus-resteasy-reactive` or `quarkus-rest`, NOT `quarkus-resteasy` (Classic)

3. ✅ **Test with curl after fix:**
   ```bash
   curl -I http://localhost:8080/
   # Should NOT see HTTP/0.9 error
   ```

### Short-term (Medium Priority)

4. ⚠️ **Check index.html size:**
   ```bash
   # After build
   du -h target/quinoa-build/index.html
   ```

5. ⚠️ **If > 32KB, optimize bundle:**
   - Code splitting
   - Remove unused dependencies
   - Disable production source maps

### Long-term (Low Priority)

6. 📋 **Consider Quarkus 3.15+ migration:**
   - Migrate from `quarkus-resteasy-reactive` to `quarkus-rest`
   - May include header handling fixes

7. 📋 **Monitor Quarkus issue trackers:**
   - [#26464](https://github.com/quarkusio/quarkus/issues/26464) - Transfer-Encoding when Content-Length set
   - [#49875](https://github.com/quarkusio/quarkus/issues/49875) - Duplicate Transfer-Encoding headers

---

## Related Resources

### Documentation
- [Quarkus HTTP Reference](https://quarkus.io/guides/http-reference)
- [Quinoa Advanced Guides](https://docs.quarkiverse.io/quarkus-quinoa/dev/advanced-guides.html)
- [Quarkus REST Migration Guide](https://quarkus.io/guides/rest-migration)

### GitHub Issues
- [RESTEasy Reactive Content-Length Issue](https://github.com/quarkusio/quarkus/issues/26464)
- [Duplicate Transfer-Encoding Headers](https://github.com/quarkusio/quarkus/issues/49875)
- [Quinoa index.html serving broken #960](https://github.com/quarkiverse/quarkus-quinoa/issues/960)
- [Quinoa SPA routing issues #666](https://github.com/quarkiverse/quarkus-quinoa/issues/666)

### Stack Overflow
- [RESTEasy Reactive and Content-Length](https://stackoverflow.com/questions/70498150/resteasy-reactive-and-content-length-in-response-headers)

### RFC References
- [RFC 7230 - HTTP/1.1 Message Syntax](https://httpwg.org/specs/rfc7230.html)
- [RFC 2616 - HTTP/1.1 (obsoleted by 7230-7235)](https://www.w3.org/Protocols/rfc2616/rfc2616.html)

---

## Conclusion

The HTTP/0.9 error you're experiencing is a **known issue with RESTEasy Reactive's chunked transfer encoding implementation** when serving files that exceed the output buffer size. The most effective immediate solution is to **increase `quarkus.resteasy-reactive.output-buffer-size`** to accommodate your largest HTML files.

**Recommended immediate action:**
```properties
# For Quarkus 3.15+ with quarkus-rest:
quarkus.rest.output-buffer-size=65536

# For older versions with quarkus-resteasy-reactive:
# quarkus.resteasy-reactive.output-buffer-size=65536
```

This will force Content-Length headers instead of chunked encoding, avoiding the buggy code path entirely and eliminating the HTTP/0.9 error.
