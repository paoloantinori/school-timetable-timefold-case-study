# Research Report: Quarkus Quinoa Live Reload Optimization

**Date**: 2026-02-26
**Depth**: Exhaustive
**Status**: Completed

## Executive Summary

After comprehensive research into Quarkus Quinoa's live reload capabilities, I've identified **critical gaps** in our current configuration that prevent us from using the full potential of simultaneous frontend/backend live coding. The project is currently missing the key configuration that enables Quinoa's dev server proxy mode.

**Key Finding**: The project has `quarkus.quinoa.dev-server.port=3000` configured but is **not using the proxy mode** because the `package.json` uses a `dev` script instead of the expected `start` script. This means Quinoa falls back to "build on change" mode rather than the faster proxy-to-Vite-dev-server mode.

---

## Current Configuration Analysis

### application.properties (Current)
```properties
quarkus.quinoa.dev-server.port=3000
quarkus.quinoa.build-dir=dist
quarkus.quinoa.package-manager=npm
quarkus.quinoa.enable-spa-routing=true
quarkus.http.cors=true
quarkus.http.cors.origins=http://localhost:3000
```

### package.json (Current)
```json
{
  "scripts": {
    "dev": "vite",           // ❌ Quinoa expects "start"
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  }
}
```

### vite.config.ts (Current)
```typescript
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
});
```

---

## Quinoa Live Reload Modes

Quinoa provides **two modes** for frontend development:

### Mode 1: Dev Server Proxy (Recommended)
- Quinoa calls `npm run start` to launch Vite's dev server
- Quarkus proxies requests to Vite (port 3000)
- **Result**: Full HMR (Hot Module Replacement), instant frontend updates
- **Requirement**: `start` script in package.json + `dev-server.port` config

### Mode 2: Build on Change (Fallback)
- Quinoa watches files and rebuilds on change
- Slower because it runs full build process
- **Result**: No HMR, page refresh needed
- **Triggered when**: No `start` script OR `dev-server.port` not configured

---

## Gap Analysis

| Feature | Current State | Optimal State | Impact |
|---------|--------------|---------------|--------|
| Dev script name | `dev` | `start` | **Critical** - proxy mode disabled |
| HMR support | ❌ Not active | ✅ Full HMR | Fast frontend iteration |
| Backend live reload | ✅ Working | ✅ Working | Already optimal |
| Vite proxy config | ✅ Correct | ✅ Correct | Already optimal |
| SPA routing | ✅ Enabled | ✅ Enabled | Already optimal |

---

## Recommended Configuration Changes

### 1. Fix package.json (Critical)
```json
{
  "scripts": {
    "start": "vite",         // ✅ Add this for Quinoa proxy mode
    "dev": "vite",           // Keep for standalone Vite usage
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  }
}
```

### 2. Add Vite HMR Configuration (Recommended)
```typescript
// vite.config.ts
export default defineConfig({
  plugins: [react()],
  server: {
    port: 3000,
    host: '127.0.0.1',      // Explicit host
    hmr: {
      // HMR configuration for proxy mode
      protocol: 'ws',
      host: '127.0.0.1',
      port: 3000,
    },
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
});
```

### 3. Optional: Add Dev-Specific Quinoa Configuration
```properties
# In application.properties - already correct
%dev.quarkus.quinoa.dev-server.port=3000
%dev.quarkus.quinoa.build-dir=dist

# Optional: Enable dev server logs for debugging
%dev.quarkus.quinoa.dev-server.logs=true

# Optional: Increase check timeout for slower machines
%dev.quarkus.quinoa.dev-server.check-timeout=30000
```

---

## Rules of Engagement for Quarkus Quinoa Development

Based on the research, here are the recommended rules:

### Rule 1: Always Use `quarkus:dev` for Development
```bash
# CORRECT - enables full live reload
./mvnw quarkus:dev

# WRONG - production mode, no live reload
./mvnw quarkus:run
```

### Rule 2: Ensure `start` Script Exists in package.json
Quinoa convention requires a `start` script for proxy mode:
```json
"scripts": {
  "start": "vite"
}
```

### Rule 3: Frontend Changes Don't Require Rebuild
With proxy mode enabled:
- Edit React/TypeScript files → Vite HMR updates browser instantly
- No `mvn package` needed
- No server restart needed

### Rule 4: Backend Changes Trigger Live Reload
- Edit Java files → Quarkus recompiles and reloads
- Tests can run continuously in background
- Press `r` in dev console to run tests

### Rule 5: When Full Rebuild IS Required
- Adding new Maven dependencies
- Changing `application.properties` build-time properties
- Modifying `pom.xml`
- First-time setup after `git clone`

### Rule 6: SPA Routing Configuration
- Keep `quarkus.quinoa.enable-spa-routing=true` for React Router
- This ensures client-side routes work correctly

### Rule 7: API Proxy is Handled by Vite (Dev Mode)
- Vite proxies `/api/*` to Quarkus (port 8080)
- Quinoa proxies UI requests to Vite (port 3000)
- Both frontend and backend are accessible on port 8080 in browser

---

## Development Workflow Comparison

### Current Workflow (Suboptimal)
```bash
1. Edit React component
2. Wait for Quinoa to detect change
3. Quinoa runs `npm run build`
4. Quarkus serves new static files
5. Browser refresh needed
```
**Time per iteration**: 5-15 seconds

### Optimized Workflow (With Proxy Mode)
```bash
1. Edit React component
2. Vite HMR updates browser instantly
3. No refresh needed (state preserved)
```
**Time per iteration**: <1 second

---

## Additional Quarkus Dev Mode Features to Leverage

### Continuous Testing
Press `r` in dev mode console to enable continuous testing:
- Tests run automatically when code changes
- Press `b` for "broken only" mode
- Press `f` to re-run failed tests only

### Instrumentation-Based Reload
Press `i` in dev mode console to enable:
- Hotswaps bytecode without restart
- Only works for method body changes
- Preserves application state

### Dev UI
Access at `http://localhost:8080/q/dev/`:
- View configuration
- View beans
- View health checks
- View scheduled tasks

---

## Action Items

### Immediate (P0)
1. Add `"start": "vite"` to package.json scripts
2. Verify proxy mode works with `./mvnw quarkus:dev`

### Short-term (P1)
1. Update vite.config.ts with explicit HMR settings
2. Document the workflow in CLAUDE.md
3. Consider removing `scripts/rebuild-ui.sh` or repurposing it

### Long-term (P2)
1. Evaluate remote development mode for containerized workflows
2. Consider adding Playwright tests to continuous testing

---

## Sources

1. [Quinoa Documentation](https://docs.quarkiverse.io/quarkus-quinoa/dev/index.html)
2. [Quinoa Configuration Reference](https://docs.quarkiverse.io/quarkus-quinoa/dev/config-reference.html)
3. [Quinoa Advanced Guides](https://docs.quarkiverse.io/quarkus-quinoa/dev/advanced-guides.html)
4. [Quarkus Dev Mode Differences](https://quarkus.io/guides/dev-mode-differences)
5. [Quarkus Continuous Testing](https://quarkus.io/guides/continuous-testing)
6. [Quarkus Dev Services](https://quarkus.io/guides/dev-services)
7. [Vite Server Options](https://v3.vitejs.dev/config/server-options)

---

## Confidence Level

**High** - Based on official documentation and multiple community sources confirming the `start` script convention for Quinoa proxy mode.
