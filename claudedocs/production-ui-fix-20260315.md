# Production UI Fix (2026-03-15)

## Problem

The production JAR (`java -jar target/quarkus-app/quarkus-run.jar`) was not serving the UI. Accessing `http://localhost:8080/` returned a 500 error.

## Root Cause

The `IndexResource` class was using hardcoded file system paths:
- `target/quinoa/build/index.html`
- `target/quinoa/build/assets/{path}`

This works in dev mode (`mvn quarkus:run`) because:
1. You run from the project root
2. The paths resolve relative to the working directory

But fails in production JAR mode because:
1. You run from `target/quarkus-app/` directory
2. The relative paths don't resolve correctly
3. UI resources weren't bundled into the JAR

## Solution

### 1. Updated IndexResource.java

Modified `IndexResource` to support both dev and production modes:
- **Classpath resources first** (production JAR): Loads from `META-INF/resources/`
- **File system fallback** (dev mode): Falls back to `target/quinoa/build/`

```java
// Try classpath first (production JAR mode)
InputStream classPathStream = Thread.currentThread()
        .getContextClassLoader()
        .getResourceAsStream("META-INF/resources/index.html");

if (classPathStream != null) {
    // Serve from classpath
} else {
    // Fall back to file system (dev mode)
}
```

### 2. Created copy-ui-to-resources.sh Script

Added `scripts/build/copy-ui-to-resources.sh` to copy Quinoa build output to classpath resources:
- Copies `target/quinoa/build/index.html` → `src/main/resources/META-INF/resources/`
- Copies `target/quinoa/build/assets/` → `src/main/resources/META-INF/resources/assets/`

### 3. Build Process

The build process now includes:
1. Quinoa builds the frontend to `target/quinoa/build/`
2. `copy-ui-to-resources.sh` copies to `src/main/resources/META-INF/resources/`
3. Maven bundles resources into the JAR during `mvn package`

## Usage

### Development (Dev Mode)

```bash
./planner.sh run dev
```
UI served from file system paths (`target/quinoa/build/`).

### Production (JAR Mode)

```bash
# First build (includes UI resources)
./scripts/build/copy-ui-to-resources.sh  # Copy UI to resources
mvn clean package                       # Bundle into JAR

# Then run
./planner-prod.sh run dev               # Runs from classpath resources
```

UI served from classpath resources (`META-INF/resources/`).

## Files Changed

1. `src/main/java/com/school/planner/rest/IndexResource.java` - Dual-mode serving
2. `scripts/build/copy-ui-to-resources.sh` - New script
3. `planner-prod.sh` - Updated production launcher
4. `claudedocs/rest-api.md` - Documentation (pending update)

## Why Quinoa HTTP Handler is Disabled

The `application.properties` has:
```
quarkus.quinoa.enabled=false
```

This was done to fix an HTTP/0.9 bug in Vert.x FileServer where body content could be sent before headers. The workaround is to serve static files through JAX-RS with `@Blocking` instead of using Quinoa's HTTP handler.

## Verification

After the fix:
- ✅ Dev mode UI works (file system resources)
- ✅ Production JAR UI works (classpath resources)
- ✅ Assets (JS, CSS, fonts) load correctly in both modes
- ✅ Hot reload still works in dev mode
