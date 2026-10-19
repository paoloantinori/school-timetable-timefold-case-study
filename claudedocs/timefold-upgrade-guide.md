# Timefold Upgrade Guide

**Purpose**: Instructions for upgrading Timefold Solver while maintaining the IdentityHashSet patch.

**Current Version**: 1.31.0 (patched)
**Patch Location**: `ValueRangeCache.Builder.FOR_USER_VALUES` (IdentityHashSet → HashSet)

---

## Quick Reference

| Step | Command | Purpose |
|------|---------|---------|
| 1 | Update `pom.xml` | Change Timefold version |
| 2 | `mvn clean package` | Test if vanilla version works |
| 3 | If bug: patch + build | Apply patch to new version |
| 4 | `cd core && mvn install` | Install patched JAR to local repo |
| 5 | `mvn clean package` | Rebuild project with patched Timefold |

---

## Full Upgrade Procedure

### Step 1: Update pom.xml

```bash
# Edit pom.xml
vi pom.xml

# Find the Timefold version property:
#   <timefold-solver.version>1.31.0</timefold-solver.version>

# Change to new version:
#   <timefold-solver.version>1.XX.X</timefold-solver.version>
```

### Step 2: Test Vanilla Version

**IMPORTANT**: Try the unpatched version first. The bug may be fixed upstream!

```bash
mvn clean package -DskipTests
./planner.sh start dev
./planner.sh wait

# Test the solver:
curl -X POST http://localhost:8080/api/solver/start

# If solver starts successfully without IllegalStateException, the bug is fixed!
# Skip to Step 5 (update documentation).

# If you see the IllegalStateException, continue to Step 3.
```

### Step 3: Clone Timefold Source

```bash
cd /tmp

# If you already have a clone:
cd timefold-solver
git fetch --all
git checkout v<NEW_VERSION>

# Or clone fresh:
git clone https://github.com/TimefoldAI/timefold-solver.git
cd timefold-solver
git checkout v<NEW_VERSION>
```

### Step 4: Verify Bug Still Exists

```bash
# Check if FOR_USER_VALUES still uses IdentityHashSet
grep -A5 "FOR_USER_VALUES" core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java

# If you see:
#   CollectionUtils.newIdentityHashSet(size)
# Then the bug still exists. Continue to Step 5.
#
# If you see:
#   CollectionUtils.newHashSet(size)
# Then the bug is fixed upstream! Skip to Step 7.
```

### Step 5: Apply Patch

```bash
# Edit the file
vi core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java

# Find the FOR_USER_VALUES enum constant (around line 109)
# Change:
#   return new ValueRangeCache<>(size, CollectionUtils.newIdentityHashSet(size), false);
# To:
#   return new ValueRangeCache<>(size, CollectionUtils.newHashSet(size), false);

# Or use sed:
sed -i 's/newIdentityHashSet(size)/newHashSet(size)/g' \
  core/src/main/java/ai/timefold/solver/core/impl/domain/valuerange/ValueRangeCache.java
```

### Step 6: Build and Install Patched Timefold

```bash
# Build the core module (faster than full build)
cd core
mvn clean install -DskipTests

# Verify installation
ls -lh ~/.m2/repository/ai/timefold/solver/timefold-solver-core/<NEW_VERSION>/
```

### Step 7: Rebuild Project

```bash
cd /home/user/data/repo/personal/planner
mvn clean package -DskipTests -Dnet.bytebuddy.experimental=true
```

### Step 8: Test

```bash
./planner.sh start dev
./planner.sh wait

# Start the solver and verify it runs without crashing
./scripts/api/api.sh pre-assign  # If using pre-assignment
./scripts/api/api.sh start       # Start the solver

# Monitor logs
tail -f /tmp/planner.log | grep -i "illegalstate\|exception\|error"
```

### Step 9: Update Documentation

1. **Update `claudedocs/timefold-vrp-identity-bug-fix-20260306.md`**:
   - Change "Timefold Version" at top to new version
   - Add note to "Maintenance" section about new version

2. **Update `claudedocs/solver-configurations.md`**:
   - Update "Timefold Version" note (if present)

3. **Update `MEMORY.md`**:
   - Update Timefold patch version note

4. **Update `pom.xml` comment**:
   - Add comment explaining why we're using this version
   ```xml
   <!-- Timefold 1.XX.X (patched) - See claudedocs/timefold-vrp-identity-bug-fix-20260306.md -->
   ```

---

## Verification Checklist

After upgrade, verify:

- [ ] Maven dependency tree shows correct Timefold version
- [ ] Solver starts without IllegalStateException
- [ ] Pre-assignment works (if applicable)
- [ ] CH → LS phase transition succeeds
- [ ] Documentation updated with new version
- [ ] Git commit includes upgrade notes

---

## Rollback Procedure

If the upgrade causes issues:

```bash
# Revert pom.xml
git checkout pom.xml
# OR manually change version back to previous

# Rebuild
mvn clean package

# Restart app
./planner.sh kill-app
./planner.sh run > /tmp/planner.log 2>&1 &
```

---

## Troubleshooting

### Issue: Maven can't find patched Timefold

**Symptom**: `Could not find artifact ai.timefold:solver:timefold-solver-core:jar:<VERSION>`

**Solution**: Did you run `mvn install` in the Timefold `core/` directory? The JAR must be in `~/.m2/repository/`.

### Issue: Solver still crashes after patch

**Possible causes**:
1. Patch wasn't applied correctly - verify with `grep IdentityHashSet`
2. Maven is using cached version - run `mvn clean`
3. Local repo has multiple versions - check `~/.m2/repository/ai/timefold/solver/timefold-solver-core/`

### Issue: Can't build Timefold source

**Workaround**: Use Docker for consistent build environment:

```bash
docker run -it --rm -v /tmp/timefold-solver:/work maven:3.9-eclipse-temurin-17 \
  bash -c "cd /work/core && mvn clean install -DskipTests"
```

---

## Related Documents

- **Bug Fix Details**: `claudedocs/timefold-vrp-identity-bug-fix-20260306.md`
- **Bug Report**: `claudedocs/timefold-bug-report-value-range-identity-bug.md`
- **Solver Config**: `claudedocs/solver-configurations.md`

---

## Change Log

| Date | Version | Patched? | Notes |
|------|---------|----------|-------|
| 2026-03-06 | 1.31.0 | Yes | Initial patch applied |
| ? | 1.XX.X | TBD | Future versions |

---

## Questions?

Refer to:
- **Full bug analysis**: `claudedocs/timefold-bug-report-value-range-identity-bug.md`
- **Fix documentation**: `claudedocs/timefold-vrp-identity-bug-fix-20260306.md`
- **Timefold docs**: https://timefold.ai/docs
