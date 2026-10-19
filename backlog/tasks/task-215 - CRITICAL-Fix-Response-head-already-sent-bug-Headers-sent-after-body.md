---
id: TASK-215
title: 'CRITICAL: Fix "Response head already sent" bug - Headers sent after body'
status: Done
assignee: []
created_date: '2026-03-09 17:06'
updated_date: '2026-03-09 19:50'
labels:
  - quinoa
  - critical-bug
  - http-headers
  - vertx
dependencies: []
priority: high
ordinal: 81000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
**CRITICAL BUG:** Vert.x `IllegalStateException: Response head already sent` - HTTP response body is being written BEFORE headers, causing protocol violations.

## Root Cause
From Quarkus Issue #13797:

```
java.lang.IllegalStateException: Response head already sent
at io.vertx.core.http.impl.HttpServerResponseImpl.checkHeadWritten
```

**Symptoms:**
- Browser sometimes fails to render page
- curl requires --http0.9 flag
- Headers sent AFTER body content (HTTP protocol violation)

## Test to Verify Issue
```bash
# Check if headers come before body
wget -S -O /dev/null http://localhost:8080/

# Should NOT need --http0.9
curl http://localhost:8080/
```

## Potential Causes
1. HTTP compression enabled (causes delayed header calculation)
2. Buffer size too small (already fixed with 65536)
3. Conflicting route handlers/filters
4. Quinoa static resource handler order issue
<!-- SECTION:DESCRIPTION:END -->

## Acceptance Criteria
<!-- AC:BEGIN -->
- [x] #1 Headers sent BEFORE body content
- [x] #2 No HTTP/0.9 errors in curl
- [x] #3 Browser renders page correctly
- [x] #4 Response ordering verified with wget -S
<!-- AC:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
✅ **FIXED:** Disabled HTTP compression that was causing headers to be sent AFTER body content

✅ **Verified:** Headers now come BEFORE body (wget shows '# got header 252 bytes:' first)

✅ **Tested:** Large JS bundle (527KB) loads correctly without HTTP/0.9 errors

✅ **Root cause:** quarkus.http.enable-compression=true was calculating Content-Encoding after body started writing
<!-- SECTION:FINAL_SUMMARY:END -->
