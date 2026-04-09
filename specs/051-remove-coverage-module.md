# Remove mocapi-coverage aggregate module

## What to build

Remove the `mocapi-coverage` module and switch to per-module JaCoCo coverage,
matching how RipCurl and Methodical handle it.

### Remove the module

- Delete the `mocapi-coverage` directory
- Remove `<module>mocapi-coverage</module>` from parent pom
- Remove or update the `sonar.coverage.jacoco.xmlReportPaths` property in parent pom
  to point at per-module report paths instead of the aggregate

### Update Sonar configuration

Configure Sonar to read per-module JaCoCo reports. Use a comma-separated list
or wildcard pattern for `sonar.coverage.jacoco.xmlReportPaths`.

## Acceptance criteria

- [ ] `mocapi-coverage` directory deleted
- [ ] Module removed from parent pom `<modules>`
- [ ] Sonar coverage property updated for per-module reports
- [ ] `mvn verify` passes
- [ ] No references to `mocapi-coverage` remain in the project
