# TDD Guard Development Container

A consistent, isolated environment for developing TDD Guard.

## Features

- Network isolation for security
- Automated development environment setup
- Pre-configured development tools
- Persistent command history

## What's Inside

| Component          | Purpose                                         |
| ------------------ | ----------------------------------------------- |
| Node.js 22         | Main CLI & Vitest/Jest/Storybook reporters      |
| Python 3.11 + pipx | Pytest reporter                                 |
| PHP 8.2 + Composer | PHPUnit reporter                                |
| Go 1.24            | Go reporter                                     |
| Rust 1.89          | Rust reporter with cargo-nextest                |
| Ruby 3.x + Bundler | RSpec reporter                                  |
| .NET SDK 10        | Dotnet reporter (Microsoft Testing Platform)    |
| Playwright deps    | Browser automation for Storybook test-runner    |
| Claude Code        | AI assistance                                   |
| Dev tools          | Git, zsh, fzf, Docker, gh, vim, nano, git-delta |

**Key files:**

- `Dockerfile` - Container definition
- `devcontainer.json` - IDE configuration
- `scripts/` - Setup and configuration scripts
  - `init-firewall.sh` - Network security
  - `setup-dev-environment.sh` - Main setup orchestrator

## Prerequisites

Choose one of:

- **[Docker Desktop](https://www.docker.com/products/docker-desktop/)** (macOS/Windows/Linux)
- **[Colima](https://github.com/abiosoft/colima)** (macOS)

## Quick Start

### VS Code

1. Install [Dev Containers extension](https://marketplace.visualstudio.com/items?itemName=ms-vscode-remote.remote-containers)
2. Open project → Click green button (bottom-left) → "Reopen in Container"
3. Wait for build → Start coding

[Learn more about VS Code Dev Containers →](https://code.visualstudio.com/docs/devcontainers/containers)

### IntelliJ IDEA

1. File → Remote Development → Dev Containers → New Dev Container → From Local Project
2. Select Docker/Colima connection
3. Browse to `<project>/.devcontainer/devcontainer.json`
4. Build Container and Continue

**Resources:**

- [Official documentation →](https://www.jetbrains.com/help/idea/start-dev-container-from-welcome-screen.html)
- [Dev Containers tutorial →](https://blog.jetbrains.com/idea/2024/07/using-dev-containers-in-jetbrains-ides-part-1/)

## Tips & Troubleshooting

### Colima Setup

IntelliJ requires more resources than the default:

```bash
# Basic setup
colima start --cpu 2 --memory 4

# With performance optimization
colima start --cpu 4 --memory 8 --vm-type vz --mount-type virtiofs
```

### macOS Performance

For better file performance:

- **Docker Desktop**: Enable VirtioFS in Settings → General → Choose file sharing implementation
- **Colima**: Use ` --vm-type vz --mount-type virtiofs` flag when starting (see above)

### Common Commands

```bash
npm test               # Run all tests
npm run test:unit      # Run unit tests only
npm run test:reporters # Run reporter tests
npm run checks         # Run all quality checks (typecheck, lint, format, test)
```

### Additional Notes

- **Command history** is preserved between container sessions in a persistent volume
- **Network isolation** restricts outbound connections for security (see `scripts/init-firewall.sh` for allowed services)
- **Environment variables** like `NODE_OPTIONS` and `USE_SYSTEM_CLAUDE` are pre-configured
- **Timezone** defaults to Europe/Stockholm but can be changed via `TZ` build arg
