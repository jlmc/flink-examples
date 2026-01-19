#!/bin/bash
# Script to upload a file to LocalStack S3 bucket, triggering SQS notification

set -e

if [ "$#" -ne 3 ]; then
  echo "Usage: $0 <file_path> <account_id> <type>"
  exit 1
fi

FILE_PATH="$1"
ACCOUNT_ID="$2"
TYPE="$3"

LOCALSTACK_HOST=localhost
EDGE_PORT=4566
S3_BUCKET_NAME=locations-directory

FILENAME=$(basename "$FILE_PATH")
S3_KEY="${ACCOUNT_ID}/${TYPE}/${FILENAME}"

aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} s3 cp "$FILE_PATH" "s3://${S3_BUCKET_NAME}/${S3_KEY}"

echo "Uploaded $FILE_PATH to s3://${S3_BUCKET_NAME}/${S3_KEY} via LocalStack."
