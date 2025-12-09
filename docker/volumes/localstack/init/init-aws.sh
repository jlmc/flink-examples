#!/bin/bash
# Script to initialize LocalStack with required S3 bucket and SQS queue for Flink integration

set -e

# LocalStack endpoints
LOCALSTACK_HOST=localhost
EDGE_PORT=4566

# Resource names
S3_BUCKET_NAME=locations-directory
SQS_QUEUE_NAME=locations-directory-upload-event-queue

# LocalStack credentials
aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} configure set aws_access_key_id test
aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} configure set aws_secret_access_key test
aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} configure set region us-east-1

# Create S3 bucket
aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} s3 mb s3://${S3_BUCKET_NAME}

echo "Created S3 bucket: ${S3_BUCKET_NAME}"

# Create SQS queue and get its ARN
QUEUE_URL=$(aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} sqs create-queue --queue-name ${SQS_QUEUE_NAME} --query 'QueueUrl' --output text)
QUEUE_ARN=$(aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} sqs get-queue-attributes --queue-url ${QUEUE_URL} --attribute-names QueueArn --query 'Attributes.QueueArn' --output text)

echo "Created SQS queue: ${SQS_QUEUE_NAME}"

echo "SQS Queue ARN: ${QUEUE_ARN}"

# Create notification configuration JSON
cat > /tmp/notification.json <<EOF
{
  "QueueConfigurations": [
    {
      "QueueArn": "${QUEUE_ARN}",
      "Events": ["s3:ObjectCreated:*"]
    }
  ]
}
EOF

# Set bucket notification configuration
aws --endpoint-url=http://${LOCALSTACK_HOST}:${EDGE_PORT} s3api put-bucket-notification-configuration \
  --bucket ${S3_BUCKET_NAME} \
  --notification-configuration file:///tmp/notification.json

echo "Configured S3 bucket notifications for ObjectCreated events to SQS queue."

echo "LocalStack AWS resources initialized."
