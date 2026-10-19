# solverConfig.xml Synchronization Rule

<!--
ALWAYS run XSD validation after modifying solverConfig.xml
Use: `./scripts/validate-solver-xml.sh`
-->

## Dependent Artifacts

When modifying **solverConfig.xml**, you following artifacts may also require updates:

to maintain consistency across documentation, frontend, and backend services:

### Backend (Java)

| File | Update Required |
|-----|----------------------------------------------------------------|
| `src/main/java/com/school/planner/service/SolverConfigParser.java` | Phase names, timeout configs, count |
| `src/main/java/com/school/planner/solver/SolverProgressionListener.java` | Phase detection logic |
| `src/main/java/com/school/planner/service/PhaseProgressionService.java` | Phase tracking |

### Frontend (TypeScript/React)
| File | Update Required |
|-----|----------------------------------------------------------------|
| `src/main/webui/src/data/solverPhases.ts` | Phase definitions array |
| `src/main/webui/src/components/SolverPhasesPage.tsx` | Phases documentation page |
| `src/main/webui/src/components/UnifiedSolverControls.tsx` | Solver controls (phase display) |

### Documentation (Markdown)
| File | Update Required |
|-----|----------------------------------------------------------------|
| `claudedocs/solver-phases.md` | Main phases documentation |
| `claudedocs/solver-configurations.md` | Configuration analysis (if architectural changes) |

### i18n (Resource Bundles)
| File | Update Required |
|-----|----------------------------------------------------------------|
| `src/main/resources/messages_en.properties` | English translations |
| `src/main/resources/messages_it.properties` | Italian translations |
| `src/main/webui/src/i18n/locales/en` | Frontend translations (if new phase-related keys) |

### Memory
| File | Update Required |
|-----|----------------------------------------------------------------|
| `MEMORY.md` | Project memory (note solver phase changes)

## Checklist

After modifying solverConfig.xml, verify:

- [ ] Run `./scripts/validate-solver-xml.sh`
- [ ] Rebuild: `./scripts/rebuild.sh` or `./scripts/rebuild-quick.sh`
- [ ] Update affected frontend files (if UI changes)
- [ ] Update `solverPhases.ts` with matching:
  - Phase count matches XML
  - Phase names match `SolverConfigParser.PHASE_NAMES`
  - Termination configs match XML values
  - Moves and metaheuristics match XML
- [ ] Update documentation files
  - [ ] Update `claudedocs/solver-phases.md`
  - [ ] Update `claudedocs/solver-configurations.md` (if architectural)
- [ ] Update MEMORY.md with note about changes

- [ ] Commit changes with descriptive message

