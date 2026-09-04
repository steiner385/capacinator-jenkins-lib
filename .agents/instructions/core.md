# steiner385__capacinator-jenkins-lib agent guidance

## Repository workflow

- Read `README.md` before changing unfamiliar areas.
- Derive build, test, lint, and formatting commands from checked-in manifests and CI; do not invent commands.
- Search for the existing source of truth before adding a new helper, constant, schema, registry, or configuration copy.
- Preserve unrelated user changes and keep edits scoped to the requested task.
- Run checks appropriate to the changed area and report any check that could not be run.
- Treat provider-local memory as temporary. Use the `capture-learning` skill for verified knowledge that should persist.
