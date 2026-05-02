#!/usr/bin/env bash

set -euo pipefail

if [ "$#" -ne 1 ]; then
  echo "Usage: $0 <csv-file-path>"
  exit 1
fi

CSV_FILE="$1"

if [ ! -f "$CSV_FILE" ]; then
  echo "File not found: $CSV_FILE"
  exit 1
fi

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
MODULE_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
FILE_NAME="$(basename "$CSV_FILE")"

cd "$MODULE_DIR"

docker compose cp "$CSV_FILE" localstack:/tmp/"$FILE_NAME"
docker compose exec -T localstack awslocal --endpoint-url=http://localhost:4566 s3 cp /tmp/"$FILE_NAME" s3://locations-csv-input/"$FILE_NAME"

echo "Uploaded $FILE_NAME to s3://locations-csv-input/$FILE_NAME"
