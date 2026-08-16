#!/bin/bash
# FIREWALL CONFIGURATION FOR TDD GUARD DEVCONTAINER
# =================================================
#
# This script implements network restrictions for the development container
# to ensure a controlled environment for TDD Guard development.
#
# ALLOWED CONNECTIONS:
# - DNS resolution (port 53)
# - SSH connections (port 22)
# - Localhost/loopback
# - GitHub API and Git operations (api.github.com, github.com)
# - Package registries:
#   - NPM: registry.npmjs.org
#   - PyPI: pypi.org, files.pythonhosted.org
#   - Packagist: packagist.org, repo.packagist.org, getcomposer.org
#   - Go: proxy.golang.org, sum.golang.org, go.dev, storage.googleapis.com, honnef.co
#   - Ruby: rubygems.org
#   - JVM: services.gradle.org, plugins.gradle.org, repo.maven.apache.org
#   - Rust: crates.io, static.crates.io, index.crates.io
#   - .NET: api.nuget.org, builds.dotnet.microsoft.com
# - Claude/Anthropic services: api.anthropic.com, sentry.io, statsig.com
# - JetBrains plugin marketplace: plugins.jetbrains.com
# - VS Code extension galleries: marketplace.visualstudio.com, github.gallerycdn.vsassets.io,
#   ms-python.gallerycdn.vsassets.io, anthropic.gallerycdn.vsassets.io,
#   ms-dotnettools.gallerycdn.vsassets.io, mobile.events.data.microsoft.com
# - Playwright browser downloads: cdn.playwright.dev, playwright.download.prss.microsoft.com
# - Host network (for Docker operations)
#
# BLOCKED CONNECTIONS:
# - All other outbound connections
# - All inbound connections except established/related
#
# RATIONALE:
# - Prevents accidental data exfiltration during development
# - Ensures all dependencies are explicitly declared
# - Maintains security boundaries for sensitive development
#
# To disable: Comment out the firewall initialization in postCreateCommand
#
# REFERENCES:
# - Claude Code DevContainer docs: https://docs.anthropic.com/en/docs/claude-code/devcontainer
# - Example configurations: https://github.com/anthropics/claude-code/tree/main/.devcontainer

set -euo pipefail  # Exit on error, undefined vars, and pipeline failures
IFS=$'\n\t'       # Stricter word splitting

# 1. Extract Docker DNS info BEFORE any flushing
DOCKER_DNS_RULES=$(iptables-save -t nat | grep "127\.0\.0\.11" || true)

# Flush existing rules and delete existing ipsets
iptables -F
iptables -X
iptables -t nat -F
iptables -t nat -X
iptables -t mangle -F
iptables -t mangle -X
ipset destroy allowed-domains 2>/dev/null || true

# 2. Selectively restore ONLY internal Docker DNS resolution
if [ -n "$DOCKER_DNS_RULES" ]; then
    echo "Restoring Docker DNS rules..."
    iptables -t nat -N DOCKER_OUTPUT 2>/dev/null || true
    iptables -t nat -N DOCKER_POSTROUTING 2>/dev/null || true
    echo "$DOCKER_DNS_RULES" | xargs -L 1 iptables -t nat
else
    echo "No Docker DNS rules to restore"
fi

# First allow DNS and localhost before any restrictions
# Allow outbound DNS
iptables -A OUTPUT -p udp --dport 53 -j ACCEPT
# Allow inbound DNS responses
iptables -A INPUT -p udp --sport 53 -j ACCEPT
# Allow outbound SSH
iptables -A OUTPUT -p tcp --dport 22 -j ACCEPT
# Allow inbound SSH responses
iptables -A INPUT -p tcp --sport 22 -m state --state ESTABLISHED -j ACCEPT
# Allow localhost
iptables -A INPUT -i lo -j ACCEPT
iptables -A OUTPUT -o lo -j ACCEPT

# Create ipset with CIDR support
ipset create allowed-domains hash:net

# Fetch GitHub meta information and aggregate + add their IP ranges
echo "Fetching GitHub IP ranges..."
gh_ranges=$(curl -s https://api.github.com/meta)
if [ -z "$gh_ranges" ]; then
    echo "ERROR: Failed to fetch GitHub IP ranges"
    exit 1
fi

if ! echo "$gh_ranges" | jq -e '.web and .api and .git' >/dev/null; then
    echo "ERROR: GitHub API response missing required fields"
    exit 1
fi

echo "Processing GitHub IPs..."
while read -r cidr; do
    if [[ ! "$cidr" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}/[0-9]{1,2}$ ]]; then
        echo "ERROR: Invalid CIDR range from GitHub meta: $cidr"
        exit 1
    fi
    echo "Adding GitHub range $cidr"
    ipset add allowed-domains "$cidr"
done < <(echo "$gh_ranges" | jq -r '(.web + .api + .git)[]' | aggregate -q)

# Resolve and add other allowed domains
for domain in \
    "registry.npmjs.org" \
    "api.anthropic.com" \
    "sentry.io" \
    "statsig.com" \
    "pypi.org" \
    "files.pythonhosted.org" \
    "packagist.org" \
    "repo.packagist.org" \
    "getcomposer.org" \
    "proxy.golang.org" \
    "sum.golang.org" \
    "go.dev" \
    "storage.googleapis.com" \
    "honnef.co" \
    "rubygems.org" \
    "services.gradle.org" \
    "plugins.gradle.org" \
    "repo.maven.apache.org" \
    "crates.io" \
    "static.crates.io" \
    "index.crates.io" \
    "api.nuget.org" \
    "builds.dotnet.microsoft.com" \
    "plugins.jetbrains.com" \
    "marketplace.visualstudio.com" \
    "github.gallerycdn.vsassets.io" \
    "ms-python.gallerycdn.vsassets.io" \
    "anthropic.gallerycdn.vsassets.io" \
    "ms-dotnettools.gallerycdn.vsassets.io" \
    "mobile.events.data.microsoft.com" \
    "cdn.playwright.dev" \
    "playwright.download.prss.microsoft.com"; do
    echo "Resolving $domain..."
    # Retry DNS resolution with exponential backoff
    for attempt in 1 2 3 4 5; do
        ips=$(dig +short A "$domain" 2>/dev/null)
        if [ -n "$ips" ]; then
            break
        fi
        if [ $attempt -lt 5 ]; then
            delay=$((attempt * attempt))  # 1, 4, 9, 16 seconds
            echo "  DNS resolution attempt $attempt failed, retrying in ${delay}s..."
            sleep $delay
        fi
    done

    if [ -z "$ips" ]; then
        echo "ERROR: Failed to resolve $domain after 5 attempts"
        exit 1
    fi

    while read -r ip; do
        # Skip CNAME records and other non-IP entries
        if [[ ! "$ip" =~ ^[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}$ ]]; then
            echo "Skipping non-IP entry for $domain: $ip"
            continue
        fi
        echo "Adding $ip for $domain"
        ipset add allowed-domains "$ip" 2>/dev/null || echo "  (already in set)"
    done < <(echo "$ips")
done

# Get host IP from default route
HOST_IP=$(ip route | grep default | cut -d" " -f3)
if [ -z "$HOST_IP" ]; then
    echo "ERROR: Failed to detect host IP"
    exit 1
fi

HOST_NETWORK=$(echo "$HOST_IP" | sed "s/\.[0-9]*$/.0\/24/")
echo "Host network detected as: $HOST_NETWORK"

# Set up remaining iptables rules
iptables -A INPUT -s "$HOST_NETWORK" -j ACCEPT
iptables -A OUTPUT -d "$HOST_NETWORK" -j ACCEPT

# Set default policies to DROP first
iptables -P INPUT DROP
iptables -P FORWARD DROP
iptables -P OUTPUT DROP

# First allow established connections for already approved traffic
iptables -A INPUT -m state --state ESTABLISHED,RELATED -j ACCEPT
iptables -A OUTPUT -m state --state ESTABLISHED,RELATED -j ACCEPT

# Then allow only specific outbound traffic to allowed domains
iptables -A OUTPUT -m set --match-set allowed-domains dst -j ACCEPT

echo "Firewall configuration complete"
echo "Verifying firewall rules..."
if curl --connect-timeout 5 https://example.com >/dev/null 2>&1; then
    echo "ERROR: Firewall verification failed - was able to reach https://example.com"
    exit 1
else
    echo "Firewall verification passed - unable to reach https://example.com as expected"
fi

# Verify GitHub API access
if ! curl --connect-timeout 5 https://api.github.com/zen >/dev/null 2>&1; then
    echo "ERROR: Firewall verification failed - unable to reach https://api.github.com"
    exit 1
else
    echo "Firewall verification passed - able to reach https://api.github.com as expected"
fi
