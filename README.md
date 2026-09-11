# Datastar IntelliJ Plugin

[![Version](https://img.shields.io/jetbrains/plugin/v/26072.svg)](https://plugins.jetbrains.com/plugin/26072)
[![Downloads](https://img.shields.io/jetbrains/plugin/d/26072.svg)](https://plugins.jetbrains.com/plugin/26072)

<!-- Plugin description -->
Native IntelliJ support for [Datastar](https://data-star.dev/).

Features include:

- Datastar attribute, modifier, and native event completion
- Datastar action completion and parameter information
- Attribute key and value validation
- Documentation for attributes, actions, and signals
- Document-local signal and nested-property completion
- Go to declaration, find usages, and rename for signals
- Datastar attribute, action, and signal highlighting
<!-- Plugin description end -->

## Installation

Install **Datastar** from the JetBrains Marketplace, or install a ZIP produced by
`./gradlew buildPlugin` using **Settings | Plugins | Install Plugin from Disk**.

## Configuration

Use **Settings | Languages & Frameworks | Datastar** to configure enabled file
extensions and custom Datastar attributes. Custom attributes are entered without
the `data-` prefix.

## Development

```shell
./gradlew test
./gradlew buildPlugin
./gradlew runIde
./gradlew verifyPlugin
```

## Compatibility

The plugin targets IntelliJ Platform 2023.3 and later. Native features are
registered for HTML and XML PSI, including template languages that expose embedded
HTML PSI.

## License

This plugin is licensed under the MIT License.
