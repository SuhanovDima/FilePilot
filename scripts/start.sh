#!/bin/sh
set -eu
project_dir=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd -P)
exec java -jar "$project_dir/target/filepilot-0.1.0.jar" "$@"
