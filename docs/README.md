# Neon Song documentation

Start here for project and contributor information:

- [Project overview, features, and build instructions](../README.md)
- [Upstream origin, modification, and license notice](../NOTICE.md)
- [APK build and download notes](../builds/README.md)
- [GitHub release workflow](../.github/workflows/release.yml)
- [Changelog](../CHANGELOG.md)
- [Contributing guide](../CONTRIBUTING.md)
- [Security policy](../SECURITY.md)
- [Liquid Glass migration notes](liquid-glass-migration.md)
- [Logging and diagnostics design](superpowers/specs/2026-10-02-logging-diagnostics-design.md)

## Publishing policy

Only reviewed, sanitized engineering documentation belongs in the public GitHub repository. Generated audit reports, machine-local paths, diagnostic dumps, test credentials, databases, APKs, and backup snapshots stay local and are covered by the root `.gitignore`. Do not add real credentials to documentation; use placeholders and GitHub Actions Secrets for CI values.
