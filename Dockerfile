# Stage 1: Build
FROM rust:1.92-alpine AS builder

WORKDIR /usr/src/app

# Install build dependencies
RUN apk add --no-cache musl-dev pkgconfig openssl-dev

# Copy project files
COPY migrations migrations
COPY src src
COPY static static
COPY templates templates
COPY Cargo.toml Cargo.toml
COPY Cargo.lock Cargo.lock

# Build the application (Rust on Alpine defaults to musl)
RUN cargo build --release

# Stage 2: Run
FROM alpine:3.20 AS final

WORKDIR /app

# Install runtime dependencies for Alpine
RUN apk add --no-cache ca-certificates libgcc

# Copy the binary from the builder stage
COPY --from=builder /usr/src/app/target/release/read-story /app/read-story

# Copy required runtime data and resources
COPY --from=builder /usr/src/app/static /app/static
COPY --from=builder /usr/src/app/templates /app/templates

# Expose the API port
EXPOSE 8080

# Run the binary
CMD ["./read-story"]
