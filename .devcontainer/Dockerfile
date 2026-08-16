ARG RUBY_VERSION=3.3.6
ARG RUST_VERSION=1.94.1

FROM ruby:${RUBY_VERSION}-slim-bookworm AS ruby-source

FROM rust:${RUST_VERSION}-bookworm AS rust-source

FROM node:22

ARG TZ
ENV TZ="$TZ"

ARG CLAUDE_CODE_VERSION=latest
ARG COMPOSER_VERSION=2.8.10
ARG GIT_DELTA_VERSION=0.18.2
ARG ZSH_IN_DOCKER_VERSION=1.2.1
ARG GO_VERSION=1.24.0
ARG GOLANGCI_LINT_VERSION=v2.3.1
ARG DOTNET_SDK_VERSION=10.0.400

# Install basic development tools and iptables/ipset
RUN apt-get update && apt-get install -y --no-install-recommends \
  locales \
  less \
  git \
  procps \
  sudo \
  fzf \
  zsh \
  man-db \
  unzip \
  gnupg2 \
  gh \
  iptables \
  ipset \
  iproute2 \
  dnsutils \
  aggregate \
  jq \
  nano \
  vim \
  python3 \
  python3-pip \
  python3-venv \
  pipx \
  php \
  php-cli \
  php-mbstring \
  php-xml \
  libyaml-0-2 \
  libreadline8 \
  build-essential \
  curl \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# Install Playwright browser dependencies for Storybook test-runner
RUN apt-get update && apt-get install -y --no-install-recommends \
  libnss3 \
  libnspr4 \
  libatk1.0-0 \
  libatk-bridge2.0-0 \
  libcups2 \
  libdrm2 \
  libdbus-1-3 \
  libxkbcommon0 \
  libxcomposite1 \
  libxdamage1 \
  libxfixes3 \
  libxrandr2 \
  libgbm1 \
  libpango-1.0-0 \
  libcairo2 \
  libasound2 \
  libatspi2.0-0 \
  && apt-get clean && rm -rf /var/lib/apt/lists/*

# Configure locale
RUN sed -i '/en_US.UTF-8/s/^# //g' /etc/locale.gen && \
    locale-gen
ENV LANG=en_US.UTF-8
ENV LANGUAGE=en_US:en
ENV LC_ALL=en_US.UTF-8

# Ensure default node user has access to /usr/local/share
RUN mkdir -p /usr/local/share/npm-global && \
  chown -R node:node /usr/local/share

ARG USERNAME=node

# Persist bash history.
RUN SNIPPET="export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
  && mkdir /commandhistory \
  && touch /commandhistory/.bash_history \
  && chown -R $USERNAME /commandhistory

# Set `DEVCONTAINER` environment variable to help with orientation
ENV DEVCONTAINER=true

# Create workspace and config directories and set permissions
RUN mkdir -p /workspace /home/node/.claude && \
  chown -R node:node /workspace /home/node/.claude

WORKDIR /workspace

# Install Composer with signature verification
RUN curl -sS https://getcomposer.org/installer -o composer-setup.php && \
    curl -sS https://composer.github.io/installer.sig -o composer-setup.sig && \
    php -r "if (hash_file('SHA384', 'composer-setup.php') === trim(file_get_contents('composer-setup.sig'))) { echo 'Installer verified'; } else { echo 'Installer corrupt'; unlink('composer-setup.php'); exit(1); } echo PHP_EOL;" && \
    php composer-setup.php --install-dir=/usr/local/bin --filename=composer --version=${COMPOSER_VERSION} && \
    rm composer-setup.php composer-setup.sig && \
    chmod +x /usr/local/bin/composer

# Install Ruby from the official image (bundler ships with the runtime)
COPY --from=ruby-source /usr/local/bin/ruby /usr/local/bin/ruby
COPY --from=ruby-source /usr/local/bin/gem /usr/local/bin/gem
COPY --from=ruby-source /usr/local/bin/bundle /usr/local/bin/bundle
COPY --from=ruby-source /usr/local/bin/bundler /usr/local/bin/bundler
COPY --from=ruby-source /usr/local/bin/irb /usr/local/bin/irb
COPY --from=ruby-source /usr/local/bin/rake /usr/local/bin/rake
COPY --from=ruby-source /usr/local/bin/erb /usr/local/bin/erb
COPY --from=ruby-source /usr/local/bin/rdoc /usr/local/bin/rdoc
COPY --from=ruby-source /usr/local/bin/ri /usr/local/bin/ri
COPY --from=ruby-source /usr/local/bin/racc /usr/local/bin/racc
COPY --from=ruby-source /usr/local/bin/typeprof /usr/local/bin/typeprof
COPY --from=ruby-source /usr/local/lib/ruby /usr/local/lib/ruby
COPY --from=ruby-source /usr/local/lib/libruby.so* /usr/local/lib/
COPY --from=ruby-source /usr/local/include /usr/local/include
COPY --from=ruby-source /usr/local/etc/gemrc /usr/local/etc/gemrc
RUN ldconfig

# Install RuboCop
RUN gem install rubocop -v 1.86.0

# Install JDK 17 for the JUnit5 reporter (Gradle toolchain target)
RUN apt-get update && apt-get install -y --no-install-recommends \
  openjdk-17-jdk-headless \
  && apt-get clean && rm -rf /var/lib/apt/lists/* \
  && ln -s /usr/lib/jvm/java-17-openjdk-$(dpkg --print-architecture) /opt/java-17

ENV JAVA_HOME=/opt/java-17
ENV PATH="${JAVA_HOME}/bin:${PATH}"

# Install Go
RUN ARCH=$(dpkg --print-architecture) && \
    case ${ARCH} in \
        amd64) GOARCH=amd64 ;; \
        arm64) GOARCH=arm64 ;; \
        *) echo "Unsupported architecture: ${ARCH}" && exit 1 ;; \
    esac && \
    wget -q "https://go.dev/dl/go${GO_VERSION}.linux-${GOARCH}.tar.gz" && \
    tar -C /usr/local -xzf "go${GO_VERSION}.linux-${GOARCH}.tar.gz" && \
    rm "go${GO_VERSION}.linux-${GOARCH}.tar.gz"

# Add Go to PATH
ENV PATH="/usr/local/go/bin:${PATH}"
ENV GOPATH="/home/node/go"
ENV PATH="${GOPATH}/bin:${PATH}"

# Install golangci-lint
RUN curl -sSfL https://raw.githubusercontent.com/golangci/golangci-lint/${GOLANGCI_LINT_VERSION}/install.sh | sh -s -- -b /usr/local/go/bin ${GOLANGCI_LINT_VERSION}

# Install Rust
ENV RUSTUP_HOME=/opt/rust/rustup
ENV CARGO_HOME=/opt/rust/cargo
ENV PATH="${CARGO_HOME}/bin:${PATH}"
COPY --from=rust-source /usr/local/cargo /opt/rust/cargo
COPY --from=rust-source /usr/local/rustup /opt/rust/rustup
RUN chown -R node:node /opt/rust && \
    ARCH=$(dpkg --print-architecture) && \
    case ${ARCH} in \
        amd64) NEXTEST_URL=https://get.nexte.st/latest/linux ;; \
        arm64) NEXTEST_URL=https://get.nexte.st/latest/linux-arm ;; \
        *) echo "Unsupported architecture: ${ARCH}" && exit 1 ;; \
    esac && \
    curl -LsSf "${NEXTEST_URL}" | tar zxf - -C /opt/rust/cargo/bin && \
    chown node:node /opt/rust/cargo/bin/cargo-nextest

# Install .NET SDK for the dotnet reporter
ENV DOTNET_ROOT=/usr/local/dotnet
ENV DOTNET_CLI_TELEMETRY_OPTOUT=1
ENV DOTNET_NOLOGO=1
ENV PATH="${DOTNET_ROOT}:${PATH}"
RUN curl -sSL https://builds.dotnet.microsoft.com/dotnet/scripts/v1/dotnet-install.sh -o /tmp/dotnet-install.sh && \
    bash /tmp/dotnet-install.sh --version ${DOTNET_SDK_VERSION} --install-dir ${DOTNET_ROOT} && \
    rm /tmp/dotnet-install.sh

RUN ARCH=$(dpkg --print-architecture) && \
  wget "https://github.com/dandavison/delta/releases/download/${GIT_DELTA_VERSION}/git-delta_${GIT_DELTA_VERSION}_${ARCH}.deb" && \
  sudo dpkg -i "git-delta_${GIT_DELTA_VERSION}_${ARCH}.deb" && \
  rm "git-delta_${GIT_DELTA_VERSION}_${ARCH}.deb"

# Set up non-root user
USER node

# Configure pipx
ENV PIPX_HOME=/home/node/.local/pipx
ENV PIPX_BIN_DIR=/home/node/.local/bin
RUN pipx ensurepath

# Install global packages
ENV NPM_CONFIG_PREFIX=/usr/local/share/npm-global
ENV PATH=$PATH:/usr/local/share/npm-global/bin:/home/node/.local/bin

# Set the default shell to zsh rather than sh
ENV SHELL=/bin/zsh

# Set the default editor and visual
ENV EDITOR=nano
ENV VISUAL=nano

# Default powerline10k theme
RUN sh -c "$(wget -O- https://github.com/deluan/zsh-in-docker/releases/download/v${ZSH_IN_DOCKER_VERSION}/zsh-in-docker.sh)" -- \
  -p git \
  -p fzf \
  -a "source /usr/share/doc/fzf/examples/key-bindings.zsh" \
  -a "source /usr/share/doc/fzf/examples/completion.zsh" \
  -a "export PROMPT_COMMAND='history -a' && export HISTFILE=/commandhistory/.bash_history" \
  -a "alias python=python3" \
  -a "alias pip=pip3" \
  -x

# Install Claude
RUN npm install -g @anthropic-ai/claude-code@${CLAUDE_CODE_VERSION}

# Copy and set up scripts
COPY scripts/ /usr/local/bin/
USER root
RUN chmod +x /usr/local/bin/*.sh && \
  echo "node ALL=(root) NOPASSWD: /usr/local/bin/init-firewall.sh" > /etc/sudoers.d/node-firewall && \
  chmod 0440 /etc/sudoers.d/node-firewall
USER node
