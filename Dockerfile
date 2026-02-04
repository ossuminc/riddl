# Multi-stage Dockerfile for riddlc
# Target image size: ~80-100MB using Alpine + jlink custom JRE

# Stage 1: Build the application with sbt
FROM eclipse-temurin:25-jdk-alpine AS sbt-builder

# Install sbt and build dependencies
RUN apk add --no-cache bash curl git

# Install sbt
RUN curl -fsSL https://github.com/sbt/sbt/releases/download/v1.10.7/sbt-1.10.7.tgz | \
    tar xz -C /opt && \
    ln -s /opt/sbt/bin/sbt /usr/local/bin/sbt

# Set working directory
WORKDIR /app

# Copy project files for sbt dependency resolution (cached layer)
COPY project/build.properties project/plugins.sbt project/Dep.scala project/
COPY build.sbt ./

# Pre-fetch dependencies (this layer is cached if build files don't change)
RUN sbt --batch update

# Copy source code
COPY . .

# Build the universal package
RUN sbt --batch riddlc/Universal/stage

# Stage 2: Create custom JRE with jlink
FROM eclipse-temurin:25-jdk-alpine AS jre-builder

# Create minimal JRE with only required modules
# Modules determined by running: jdeps --print-module-deps on the app JARs
RUN $JAVA_HOME/bin/jlink \
    --add-modules java.base,java.logging,java.desktop,java.management,java.naming,java.xml,java.net.http \
    --strip-debug \
    --no-man-pages \
    --no-header-files \
    --compress=zip-9 \
    --output /custom-jre

# Stage 3: Minimal runtime image
FROM alpine:3.21

# Add labels
LABEL org.opencontainers.image.title="riddlc"
LABEL org.opencontainers.image.description="RIDDL Language Compiler"
LABEL org.opencontainers.image.source="https://github.com/ossuminc/riddl"
LABEL org.opencontainers.image.vendor="Ossum Inc."
LABEL org.opencontainers.image.licenses="Apache-2.0"

# Copy custom JRE from builder
COPY --from=jre-builder /custom-jre /opt/java

# Copy staged application from sbt builder
COPY --from=sbt-builder /app/riddlc/jvm/target/universal/stage /opt/riddlc

# Set up environment
ENV JAVA_HOME=/opt/java
ENV PATH="/opt/java/bin:/opt/riddlc/bin:${PATH}"

# Create non-root user for security
RUN adduser -D -h /work riddl && \
    chown -R riddl:riddl /opt/riddlc

# Switch to non-root user
USER riddl

# Working directory for user files (mount point)
WORKDIR /work

# Default entrypoint
ENTRYPOINT ["riddlc"]

# Default command shows help
CMD ["--help"]
