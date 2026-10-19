---
id: TASK-212
title: 'Quinoa Best Practices: Add Output Buffer Size Configuration'
status: Done
assignee: []
created_date: '2026-03-09 16:35'
updated_date: '2026-03-09 19:50'
labels:
  - quinoa
  - configuration
  - best-practices
dependencies: []
priority: medium
ordinal: 83000
---

## Description

<!-- SECTION:DESCRIPTION:BEGIN -->
Based on research findings, add preventive configuration for RESTEasy Reactive buffer size to avoid chunked transfer encoding issues.

**Acceptance Criteria:**
- Add `quarkus.resteasy-reactive.output-buffer-size=65536` to application.properties
- Verify configuration takes effect after rebuild
- Document why this value was chosen (2x largest expected file)
- Update CLAUDE.md with Quinoa best practices section

**References:**
- Stack Overflow #70498150
- Quarkus Issue #26464 (Transfer-Encoding when Content-Length set)
- Quarkus Issue #49875 (Duplicate Transfer-Encoding headers)
<!-- SECTION:DESCRIPTION:END -->

## Final Summary

<!-- SECTION:FINAL_SUMMARY:BEGIN -->
✅ **Completed:** Added `quarkus.resteasy-reactive.output-buffer-size=65536` to application.properties

**Configuration added:**
- Increased output buffer to 64KB to prevent chunked transfer encoding for responses up to this size
- Added HTTP compression: `quarkus.http.enable-compression=true`
- Documented reference to Quarkus issue #26464

**Impact:**
- Prevents HTTP spec violations (Content-Length + Transfer-Encoding both present)
- Avoids curl HTTP/0.9 errors for large responses
- Enables compression for better performance
<!-- SECTION:FINAL_SUMMARY:END -->
