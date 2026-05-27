#!/usr/bin/env bash
# Generates a self-signed TLS certificate for local development.
# In production, replace with a certificate from Let's Encrypt or your CA.
set -e

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"

openssl req -x509 -nodes -days 365 -newkey rsa:2048 \
  -keyout "$SCRIPT_DIR/key.pem" \
  -out    "$SCRIPT_DIR/cert.pem" \
  -subj   "/C=US/ST=Demo/L=Demo/O=SecurityDemo/CN=localhost" \
  -addext "subjectAltName=DNS:localhost,IP:127.0.0.1"

chmod 600 "$SCRIPT_DIR/key.pem"
echo "Self-signed certificate generated at $SCRIPT_DIR"
