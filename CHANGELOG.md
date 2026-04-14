# Changelog

All notable changes to this project are documented in this file. The format is based on
[Keep a Changelog](https://keepachangelog.com/en/1.1.0/) and this project adheres to
[Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Annotation-driven prompt registration via `@PromptService` + `@PromptMethod`. Method
  parameters bind from the incoming argument map via Spring's `ConversionService`,
  supporting strings, primitives, enums, `java.time` types, and any custom
  `Converter<String, T>`. A `Map<String, String>` parameter receives the whole
  argument map.
- Annotation-driven resource registration via `@ResourceService` with two method-level
  annotations: `@ResourceMethod` for fixed URIs and `@ResourceTemplateMethod` for
  RFC 6570 URI templates. Path variables bind to method parameters by name using the
  same `StringMapArgResolver` as prompt arguments.
- `docs/prompts-guide.md` and `docs/resources-guide.md` documenting the new
  annotations with argument-binding, URI-template, and return-type coverage.
- README Quick Start now includes prompt and resource examples alongside tools.

### Changed

- Tool annotation scanning now builds a per-invoker Methodical resolver chain
  instead of relying on globally-registered `ParameterResolver` beans. Dropped the
  `@Bean`-exposed `McpToolContextResolver` and `McpToolParamsResolver` — the
  `ToolServiceMcpToolProvider` constructs them once and hands them to each
  `AnnotationMcpTool` invoker. No user-facing API change unless you were relying
  on those beans directly.
- Return-type and parameter-shape validation failures in the annotation scanners
  now throw `IllegalArgumentException` (previously `IllegalStateException`), since
  the violation is in the declared method signature.

### Requirements

- Methodical bumped from 0.3.0 to 0.4.0 for the per-invoker resolver API.

## [0.1.0] - 2026-04-14

Initial public release on Maven Central.

[Unreleased]: https://github.com/callibrity/mocapi/compare/0.1.0...HEAD
[0.1.0]: https://github.com/callibrity/mocapi/releases/tag/0.1.0
