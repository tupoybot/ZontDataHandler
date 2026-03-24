#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
OUT_DIR="$ROOT_DIR/.release-local"
KEYSTORE_PATH="$OUT_DIR/zont-release.keystore"
KEY_ALIAS="zont-release"
KEYSTORE_PROPERTIES_PATH="$ROOT_DIR/keystore.properties"
SECRETS_ENV_PATH="$OUT_DIR/github-actions-secrets.env"
SECRETS_MD_PATH="$OUT_DIR/github-actions-secrets.md"

if [[ -e "$KEYSTORE_PATH" || -e "$KEYSTORE_PROPERTIES_PATH" || -e "$SECRETS_ENV_PATH" ]]; then
    cat >&2 <<EOF
Refusing to overwrite existing release signing materials.
Remove these files first if you really want to regenerate them:
  $KEYSTORE_PATH
  $KEYSTORE_PROPERTIES_PATH
  $SECRETS_ENV_PATH
EOF
    exit 1
fi

mkdir -p "$OUT_DIR"

random_secret() {
    openssl rand -base64 24 | tr -d '\n=+/' | cut -c1-24
}

STORE_PASSWORD="$(random_secret)"
KEY_PASSWORD="$STORE_PASSWORD"

keytool -genkeypair \
    -alias "$KEY_ALIAS" \
    -keyalg RSA \
    -keysize 4096 \
    -validity 3650 \
    -storetype PKCS12 \
    -keystore "$KEYSTORE_PATH" \
    -storepass "$STORE_PASSWORD" \
    -keypass "$KEY_PASSWORD" \
    -dname "CN=ZONT Data Handler, OU=Release, O=ZONT Data Handler, L=Samara, ST=Samara, C=RU" \
    >/dev/null

cat >"$KEYSTORE_PROPERTIES_PATH" <<EOF
storeFile=.release-local/zont-release.keystore
storePassword=$STORE_PASSWORD
keyAlias=$KEY_ALIAS
keyPassword=$KEY_PASSWORD
EOF

KEYSTORE_BASE64="$(base64 "$KEYSTORE_PATH" | tr -d '\n')"

cat >"$SECRETS_ENV_PATH" <<EOF
ZONT_RELEASE_KEYSTORE_BASE64=$KEYSTORE_BASE64
ZONT_RELEASE_STORE_PASSWORD=$STORE_PASSWORD
ZONT_RELEASE_KEY_ALIAS=$KEY_ALIAS
ZONT_RELEASE_KEY_PASSWORD=$KEY_PASSWORD
EOF

cat >"$SECRETS_MD_PATH" <<EOF
# GitHub Actions release signing secrets

Paste these values into:
GitHub repository -> Settings -> Secrets and variables -> Actions -> Repository secrets

- \`ZONT_RELEASE_KEYSTORE_BASE64\`
- \`ZONT_RELEASE_STORE_PASSWORD\`
- \`ZONT_RELEASE_KEY_ALIAS\`
- \`ZONT_RELEASE_KEY_PASSWORD\`

Source file with exact values:
\`$SECRETS_ENV_PATH\`
EOF

cat <<EOF
Generated local release signing materials:
  Keystore: $KEYSTORE_PATH
  Local Gradle config: $KEYSTORE_PROPERTIES_PATH
  GitHub Actions secrets: $SECRETS_ENV_PATH
  Human-readable notes: $SECRETS_MD_PATH
EOF
