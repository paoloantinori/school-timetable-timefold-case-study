# Research: Timefold XML Value Sorting Configuration

**Date:** 2026-03-11
**Subject:** CH Phase 2 Compaction via Value Sorting (TASK-226)
**Depth:** Exhaustive
**Confidence:** High (verified against official Timefold XSD schema)

## Executive Summary

**Finding:** The user discovered the correct XML configuration for value sorting in Timefold 1.31.0. The solution uses `selectionOrder` (as a child element, not attribute) with `sorterManner=ASCENDING`.

**Solution:**
```xml
<valueSelector variableName="timeslot">
  <cacheType>PHASE</cacheType>
  <selectionOrder>SORTED</selectionOrder>
  <sorterManner>ASCENDING</sorterManner>
</valueSelector>
```

## Key Findings

### 1. `selectionOrder` is a Child Element, Not Attribute

My earlier attempts failed because I tried `selectionOrder` as an attribute on `<valueSelector>`:
```xml
<!-- INVALID - attribute syntax -->
<valueSelector variableName="timeslot" selectionOrder="SORTED">
```

**Correct syntax:** `selectionOrder` is a child element:
```xml
<!-- VALID - child element syntax -->
<valueSelector variableName="timeslot">
  <selectionOrder>SORTED</selectionOrder>
</valueSelector>
```

### 2. `sorterManner=ASCENDING` Uses Natural Ordering

The `ASCENDING` sorterManner uses the natural ordering of the planning variable:
- For `Timeslot`: natural ordering is by `startDateTime` (ascending)
- This achieves the same compaction goal as a custom comparator
- No custom comparator class needed

### 3. Complete valueSelectorConfig Schema (from Timefold 1.31.0 XSD)

```xml
<xs:complexType name="valueSelectorConfig">
  <xs:sequence>
    <xs:element minOccurs="0" name="downcastEntityClass" type="xs:string"/>
    <xs:element minOccurs="0" name="cacheType" type="tns:selectionCacheType"/>
    <xs:element minOccurs="0" name="selectionOrder" type="tns:selectionOrder"/>
    <xs:element minOccurs="0" name="nearbySelection" type="tns:nearbySelectionConfig"/>
    <xs:element minOccurs="0" name="filterClass" type="xs:string"/>
    <xs:element minOccurs="0" name="sorterManner" type="tns:valueSorterManner"/>
    <xs:element minOccurs="0" name="sorterComparatorClass" type="xs:string"/>
    <xs:element minOccurs="0" name="comparatorClass" type="xs:string"/>
    <xs:element minOccurs="0" name="sorterWeightFactoryClass" type="xs:string"/>
    <xs:element minOccurs="0" name="comparatorFactoryClass" type="xs:string"/>
    <xs:element minOccurs="0" name="sorterOrder" type="tns:selectionSorterOrder"/>
    <xs:element minOccurs="0" name="sorterClass" type="xs:string"/>
    <xs:element minOccurs="0" name="probabilityWeightFactoryClass" type="xs:string"/>
    <xs:element minOccurs="0" name="selectedCountLimit" type="xs:long"/>
  </xs:sequence>
  <xs:attribute name="id" type="xs:string"/>
  <xs:attribute name="mimicSelectorRef" type="xs:string"/>
  <xs:attribute name="variableName" type="xs:string"/>
</xs:complexType>
```

### 4. selectionOrder Enum Values

| Value | Description |
|-------|-------------|
| `INHERIT` | Inherit from parent selector |
| `ORIGINAL` | Default order (no sorting) |
| `SORTED` | Use sorter to sort values |
| `RANDOM` | Random order |

### 5. valueSorterManner Enum Values

| Value | Description |
|-------|-------------|
| `NONE` | No sorting |
| `INCREASING_STRENGTH` | Weakest values first (requires comparator) |
| `INCREASING_STRENGTH_IF_AVAILABLE` | Conditional increasing strength |
| `DECREASING_STRENGTH` | Strongest values first (requires comparator) |
| `DECREASING_STRENGTH_IF_AVAILABLE` | Conditional decreasing strength |
| `ASCENDING` | Natural ascending order |
| `ASCENDING_IF_AVAILABLE` | Conditional ascending |
| `DESCENDING` | Natural descending order |
| `DESCENDING_IF_AVAILABLE` | Conditional descending |

### 6. selectionSorterOrder Enum Values

| Value | Description |
|-------|-------------|
| `ASCENDING` | Sort in ascending order |
| `DESCENDING` | Sort in descending order |

## Why My Attempts Failed

### Attempt 1: `sorterManner=INCREASING_STRENGTH` + `sorterComparatorClass`

```xml
<sorterManner>INCREASING_STRENGTH</sorterManner>
<sorterComparatorClass>com.school.planner.domain.TimeslotStrengthComparator</sorterComparatorClass>
```

**Error:** `resolvedSelectionOrder (ORIGINAL) that is not SORTED`

**Reason:** `INCREASING_STRENGTH` with custom comparator requires `selectionOrder=SORTED`, but I didn't include the `<selectionOrder>` child element.

### Attempt 2: `comparatorClass` alone

```xml
<comparatorClass>com.school.planner.domain.TimeslotStrengthComparator</comparatorClass>
```

**Error:** Same as above

**Reason:** `comparatorClass` alone doesn't set `sorterManner`, so `selectionOrder` remains `ORIGINAL`.

### Attempt 3: `selectionOrder` as attribute

```xml
<valueSelector variableName="timeslot" selectionOrder="SORTED">
```

**Error:** `attribute 'selectionOrder' is not allowed`

**Reason:** `selectionOrder` is a child element, not an attribute.

## Correct Solution (User's Discovery)

```xml
<valueSelector variableName="timeslot">
  <cacheType>PHASE</cacheType>
  <selectionOrder>SORTED</selectionOrder>
  <sorterManner>ASCENDING</sorterManner>
</valueSelector>
```

**Why this works:**
1. `<selectionOrder>SORTED</selectionOrder>` explicitly sets sorted order
2. `<sorterManner>ASCENDING</sorterManner>` uses Timeslot's natural ordering (`compareTo` by `startDateTime`)
3. No custom comparator needed - simpler than `INCREASING_STRENGTH`

## Verification

- **XSD Validation:** ✅ Passes
- **Build:** ✅ Success
- **Runtime:** ✅ App starts without errors
- **Configuration:** Works in CH Phase 2

## Source

**XSD Location:** `solver.xsd` (extracted from `timefold-solver-core-1.31.0.jar`)

**Confidence:** High - verified against official Timefold 1.31.0 XSD schema.
