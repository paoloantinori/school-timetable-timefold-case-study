# Benchmark Config XSD Validation Rule

**CRITICAL**: After modifying any solver benchmark XML configuration, you MUST validate against the Timefold XSD before considering the change successful.

## When to Apply
- Any change to `src/main/resources/solverBenchmarkConfig.xml`

## Required Validation Step
After editing benchmark XML files, run:
```bash
./scripts/validate/validate-solver-xml.sh
```

## What Happens if You Skip This
- Invalid XML structure causes runtime `SAXParseException`
- Invalid elements/attributes cause `IllegalArgumentException`
- Score format strings cause `NumberFormatException`
- Wasted time debugging instead of catching issues early

## Common Invalid Patterns
- `<environmentMode>` in `<inheritedSolverBenchmark>` (must be in individual `<solverBenchmark>`)
- Wrong score format: `5000hard/0random` → use `5000hard/0medium/0soft`
- Decimal scores: `1.0hard` → use `1hard`
- `<compositeTabuSize>` → use `<entityTabuSize>`
- Wrong element placement: `<unionMoveSelector>` in inherited solver

## Examples of Good Changes
```xml
<!-- ✅ Good: environmentMode in individual solverBenchmark -->
<solverBenchmark>
  <solver>
    <environmentMode>NON_REPRODUCIBLE</environmentMode>
    <localSearch>...</localSearch>
  </solver>
</solverBenchmark>

<!-- ✅ Good: Integer scores -->
<simulatedAnnealingStartingTemperature>5000hard/0medium/0soft</simulatedAnnealingStartingTemperature>
```
