# Based on work licensed under the Apache License, Version 2.0
# https://www.apache.org/licenses/LICENSE-2.0
#
# Modifications:
# - Simplified package list
# - Removed GPG verification
# - Changed checksum verification method
# - Updated Alpine base image
#
# Copyright © original authors

# Reference at https://github.com/adoptium/containers/blob/main/21/jdk/alpine/3.23/Dockerfile
FROM alpine:3.23.2 AS jdk-21-alpine-build

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV JAVA_VERSION=jdk-21.0.9+10

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'

LABEL maintainer="Ngoc Khanh"

LABEL title="Gradle 9.2 JDK 21 Alpine Base Image"
LABEL description="Alpine Linux image with Gradle 9.2 and Eclipse Temurin OpenJDK 21 providing a Java Development Kit, checksum verified"

RUN set -eux; \
    apk add --no-cache \
        # utilities for keeping Alpine and OpenJDK CA certificates in sync \
        ca-certificates \
        # locales ensures proper character encoding and locale-specific behaviors using en_US.UTF-8 \
        musl-locales musl-locales-lang \
        # jlink --strip-debug needs objcopy \
        binutils \
        tzdata \
        # wget for downloading JDK \
        wget \
    ; \
    rm -rf /var/cache/apk/*

RUN set -eux; \
    JDK_VERSION="${JAVA_VERSION}"; \
    JDK_VERSION_ENCODED=$(echo "$JDK_VERSION" | sed 's/+/%2B/g'); \
    JDK_VERSION_FORMATTED=$(echo "$JDK_VERSION" | sed -e 's/jdk-//' -e 's/+/_/g'); \
    ARCH="$(apk --print-arch)"; \
    case "${ARCH}" in \
       aarch64) \
         BINARY_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jdk_aarch64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz"; \
         CHECKSUM_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jdk_aarch64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz.sha256.txt"; \
         ;; \
       x86_64) \
         BINARY_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jdk_x64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz"; \
         CHECKSUM_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jdk_x64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz.sha256.txt"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    echo "Downloading JDK from Eclipse Temurin (official open-source JDK)..."; \
    wget -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "Downloading checksum file..."; \
    wget -O /tmp/openjdk.tar.gz.sha256.txt ${CHECKSUM_URL}; \
    echo "Verifying checksum..."; \
    EXPECTED_HASH=$(cut -d' ' -f1 /tmp/openjdk.tar.gz.sha256.txt); \
    ACTUAL_HASH=$(sha256sum /tmp/openjdk.tar.gz | cut -d' ' -f1); \
    if [ "${EXPECTED_HASH}" != "${ACTUAL_HASH}" ]; then \
        echo "ERROR: Checksum verification failed!"; \
        echo "Expected: ${EXPECTED_HASH}"; \
        echo "Actual:   ${ACTUAL_HASH}"; \
        exit 1; \
    fi; \
    echo "Checksum verification passed."; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract \
        --file /tmp/openjdk.tar.gz \
        --directory "$JAVA_HOME" \
        --strip-components 1 \
        --no-same-owner \
    ; \
    rm -f /tmp/openjdk.tar.gz /tmp/openjdk.tar.gz.sha256.txt ${JAVA_HOME}/lib/src.zip;

RUN set -eux; \
    echo "Verifying install ..."; \
    java --version; \
    echo "Complete."


# Referene at https://github.com/gradle/docker-gradle/blob/fba2d36b492eab91f3eb95610354df7b8d12d46f/jdk21-alpine/Dockerfile
FROM jdk-21-alpine-build AS gradle-build

CMD ["gradle"]

ENV GRADLE_HOME=/opt/gradle

RUN set -o errexit -o nounset \
    && echo "Adding gradle user and group" \
    && addgroup --system --gid 1000 gradle \
    && adduser --system --ingroup gradle --uid 1000 --shell /bin/ash gradle \
    && mkdir /home/gradle/.gradle \
    && chown -R gradle:gradle /home/gradle \
    && chmod -R 755 /home/gradle

VOLUME /home/gradle/.gradle

WORKDIR /home/gradle

RUN set -o errexit -o nounset \
    && apk add --no-cache \
      # common utilities \
      curl \
      make \
      \
      # VCSes \
      breezy \
      py3-tzlocal \
      git \
      git-lfs \
      mercurial \
      subversion \
    \
    && echo "Testing common utilities" \
    && which awk \
    && which curl \
    && which cut \
    && which grep \
    && which gunzip \
    && which sha256sum \
    && which sed \
    && which tar \
    && which tr \
    && which unzip \
    && which wget \
    \
    && echo "Testing VCSes" \
    && which git \
    && which git-lfs \
    && which hg \
    && which svn

ENV GRADLE_VERSION=9.2.1
# https://gradle.org/release-checksums/#${GRADLE_VERSION}
ARG GRADLE_DOWNLOAD_SHA256=72f44c9f8ebcb1af43838f45ee5c4aa9c5444898b3468ab3f4af7b6076c5bc3f
RUN set -o errexit -o nounset \
    && echo "Downloading Gradle" \
    && wget --no-verbose --output-document=gradle.zip "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip" \
    \
    && echo "Checking Gradle download hash" \
    && echo "${GRADLE_DOWNLOAD_SHA256} *gradle.zip" | sha256sum -c - \
    \
    && echo "Installing Gradle" \
    && unzip gradle.zip \
    && rm gradle.zip \
    && mv "gradle-${GRADLE_VERSION}" "${GRADLE_HOME}/" \
    && ln -s "${GRADLE_HOME}/bin/gradle" /usr/bin/gradle

RUN apk del git git-lfs curl make py3-tzlocal breezy mercurial subversion wget

USER gradle

RUN set -o errexit -o nounset \
    && echo "Testing Gradle installation" \
    && gradle --version

# Based on work licensed under the Apache License, Version 2.0
# https://www.apache.org/licenses/LICENSE-2.0
#
# Modifications:
# - Simplified package list
# - Removed GPG verification
# - Changed checksum verification method
# - Updated Alpine base image
#
# Copyright © original authors

# Reference at https://github.com/adoptium/containers/blob/main/21/jre/alpine/3.23/Dockerfile
FROM alpine:3.23.2 AS jdk-21-alpine-run

ENV JAVA_HOME=/opt/java/openjdk
ENV PATH=$JAVA_HOME/bin:$PATH
ENV JAVA_VERSION=jdk-21.0.9+10

LABEL maintainer="Ngoc Khanh"

ENV LANG='en_US.UTF-8' LANGUAGE='en_US:en' LC_ALL='en_US.UTF-8'


RUN set -eux; \
    apk add --no-cache \
        # utilities for keeping Alpine and OpenJDK CA certificates in sync \
        ca-certificates \
        # locales ensures proper character encoding and locale-specific behaviors using en_US.UTF-8 \
        musl-locales musl-locales-lang \
        tzdata \
        # wget for downloading JRE \
        wget \
    ; \
    rm -rf /var/cache/apk/*


RUN set -eux; \
    JDK_VERSION="${JAVA_VERSION}"; \
    JDK_VERSION_ENCODED=$(echo "$JDK_VERSION" | sed 's/+/%2B/g'); \
    JDK_VERSION_FORMATTED=$(echo "$JDK_VERSION" | sed -e 's/jdk-//' -e 's/+/_/g'); \
    ARCH="$(apk --print-arch)"; \
    case "${ARCH}" in \
       aarch64) \
         BINARY_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jre_aarch64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz"; \
         CHECKSUM_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jre_aarch64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz.sha256.txt"; \
         ;; \
       x86_64) \
         BINARY_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jre_x64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz"; \
         CHECKSUM_URL="https://github.com/adoptium/temurin21-binaries/releases/download/${JDK_VERSION_ENCODED}/OpenJDK21U-jre_x64_alpine-linux_hotspot_${JDK_VERSION_FORMATTED}.tar.gz.sha256.txt"; \
         ;; \
       *) \
         echo "Unsupported arch: ${ARCH}"; \
         exit 1; \
         ;; \
    esac; \
    echo "Downloading JRE from Eclipse Temurin (official open-source JDK)..."; \
    wget -O /tmp/openjdk.tar.gz ${BINARY_URL}; \
    echo "Downloading checksum file..."; \
    wget -O /tmp/openjdk.tar.gz.sha256.txt ${CHECKSUM_URL}; \
    echo "Verifying checksum..."; \
    EXPECTED_HASH=$(cut -d' ' -f1 /tmp/openjdk.tar.gz.sha256.txt); \
    ACTUAL_HASH=$(sha256sum /tmp/openjdk.tar.gz | cut -d' ' -f1); \
    if [ "${EXPECTED_HASH}" != "${ACTUAL_HASH}" ]; then \
        echo "ERROR: Checksum verification failed!"; \
        echo "Expected: ${EXPECTED_HASH}"; \
        echo "Actual:   ${ACTUAL_HASH}"; \
        exit 1; \
    fi; \
    echo "Checksum verification passed."; \
    mkdir -p "$JAVA_HOME"; \
    tar --extract \
        --file /tmp/openjdk.tar.gz \
        --directory "$JAVA_HOME" \
        --strip-components 1 \
        --no-same-owner \
    ; \
    rm -f /tmp/openjdk.tar.gz /tmp/openjdk.tar.gz.sha256.txt;

RUN set -eux; \
    echo "Verifying install ..."; \
    java --version; \
    echo "Complete."; \
    # Remove wget after download (not needed at runtime) \
    apk del wget; \
    # Remove unnecessary JRE components to reduce attack surface \
    rm -rf "$JAVA_HOME/lib/src.zip" "$JAVA_HOME/man" "$JAVA_HOME/include" || true

FROM gradle-build AS build

WORKDIR /app

COPY . .

RUN gradle --info clean build --build-cache -x test
RUN mkdir -p build/dependency && (cd build/dependency; jar -xf ../libs/*.jar)

FROM jdk-21-alpine-run AS final

# Create app user
RUN addgroup -S app && adduser -S app -G app

# Create app directory
WORKDIR /app

# Copy application from build stage
COPY --from=build /app/build/libs/*.jar /app/app.jar
COPY --from=build /app/build/dependency /app/lib

# Fix ownership AFTER files exist
RUN chown -R app:app /app

USER app

ENTRYPOINT ["java", "-jar", "/app/app.jar"]

