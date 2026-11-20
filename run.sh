#!/bin/sh
set -eu
cd "$(dirname "$0")"
if ! command -v java >/dev/null 2>&1; then
  echo "Install Java 17 or newer on this computer, then run this launcher again." >&2
  exit 1
fi
exec java -jar campus-register.jar
