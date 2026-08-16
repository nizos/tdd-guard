#!/bin/bash
# DEVELOPMENT ENVIRONMENT SETUP
# =============================
#
# This script sets up the complete development environment for TDD Guard,
# including all dependencies for the main package and language-specific reporters.
#
# SETUP INCLUDES:
# - Network firewall configuration
# - Node.js dependencies and build
# - PHPUnit reporter dependencies
# - pytest reporter dependencies
# - Go reporter dependencies
# - Rust reporter dependencies
# - RSpec reporter dependencies
# - Minitest reporter dependencies
# - .NET reporter build
#
# REQUIREMENTS:
# - Must be run from the workspace root
# - Requires sudo access for firewall setup
# - Node.js, PHP, Python, Go, Rust, Ruby, and .NET must be pre-installed
#
# EXIT CODES:
# - 0: Success
# - 1: General failure
# - 2: Missing prerequisites

set -euo pipefail  # Exit on error, undefined vars, and pipeline failures
IFS=$'\n\t'       # Stricter word splitting

echo "Starting TDD Guard development environment setup..."

# 1. Initialize firewall
echo ""
echo "📡 Initializing network firewall..."
sudo /usr/local/bin/init-firewall.sh

# 2. Install Node.js dependencies
echo ""
echo "📦 Installing Node.js dependencies..."
npm ci

# 3. Build TypeScript packages
echo ""
echo "🔨 Building TypeScript packages..."
npm run build

# 4. Install PHPUnit reporter dependencies
echo ""
echo "🐘 Installing PHPUnit reporter dependencies..."
composer install -d reporters/phpunit

# 5. Set up Python environment for pytest reporter
echo ""
echo "🐍 Setting up Python environment for pytest reporter..."
python3 -m venv reporters/pytest/.venv
reporters/pytest/.venv/bin/pip install -e reporters/pytest pytest

# 6. Download Go dependencies for Go reporter
echo ""
echo "🐹 Setting up Go reporter dependencies..."
go mod download -C reporters/go

# 7. Install Ruby reporter dependencies (RSpec, Minitest)
echo ""
echo "💎 Installing Ruby reporter dependencies..."
for dir in reporters/rspec reporters/minitest; do
  (cd "$dir" && bundle config set --local path vendor/bundle && bundle install)
done

# 8. Build Rust reporter
echo ""
echo "🦀 Building Rust reporter..."
cargo build --release --manifest-path reporters/rust/Cargo.toml || echo "⚠️  Rust reporter build failed (non-fatal)"

# 9. Build JUnit5 reporter
echo ""
echo "☕ Building JUnit5 reporter..."
reporters/junit5/gradlew -p reporters/junit5 jar --no-daemon || echo "⚠️  JUnit5 reporter build failed (non-fatal)"

# 10. Build .NET reporter
echo ""
echo "🟣 Building .NET reporter..."
dotnet build reporters/dotnet/TddGuard.Dotnet.Tests/TddGuard.Dotnet.Tests.csproj || echo "⚠️  .NET reporter build failed (non-fatal)"

# 11. Install Playwright browsers for Storybook test-runner
echo ""
echo "🎭 Installing Playwright browsers for Storybook test-runner..."
NODE_OPTIONS="--dns-result-order=ipv4first" npx playwright install chromium --only-shell

echo ""
echo "✅ Development environment setup complete!"
echo ""
echo "You can now run:"
echo "  • npm test               - Run all tests"
echo "  • npm run test:unit      - Run unit tests only"
echo "  • npm run test:reporters - Run reporter tests"
echo "  • npm run checks         - Run all quality checks"
