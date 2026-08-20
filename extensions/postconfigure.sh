#!/usr/bin/env bash
set -euo pipefail

: "${DB_URL:?DB_URL is required}"
: "${DB_USERNAME:?DB_USERNAME is required}"
: "${DB_PASSWORD:?DB_PASSWORD is required}"

echo "Configuring the AonFine CUBRID datasource"
"${JBOSS_HOME}/bin/jboss-cli.sh" \
  --properties=/dev/null \
  --file="${JBOSS_HOME}/extensions/datasource.cli"
