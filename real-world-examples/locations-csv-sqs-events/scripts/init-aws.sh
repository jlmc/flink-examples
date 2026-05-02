#!/usr/bin/env bash

set -euo pipefail

awslocal --endpoint-url=http://localstack:4566 s3 mb s3://locations-csv-input || true
awslocal --endpoint-url=http://localstack:4566 sqs create-queue --queue-name locations-csv-events

QUEUE_ARN=$(awslocal --endpoint-url=http://localstack:4566 sqs get-queue-attributes \
  --queue-url http://localstack:4566/000000000000/locations-csv-events \
  --attribute-names QueueArn \
  --query 'Attributes.QueueArn' --output text)

awslocal --endpoint-url=http://localstack:4566 sqs set-queue-attributes \
  --queue-url http://localstack:4566/000000000000/locations-csv-events \
  --attributes "{\"Policy\":\"{\\\"Version\\\":\\\"2012-10-17\\\",\\\"Statement\\\":[{\\\"Effect\\\":\\\"Allow\\\",\\\"Principal\\\":\\\"*\\\",\\\"Action\\\":\\\"SQS:SendMessage\\\",\\\"Resource\\\":\\\"${QUEUE_ARN}\\\"}]}\"}"

awslocal --endpoint-url=http://localstack:4566 s3api put-bucket-notification-configuration \
  --bucket locations-csv-input \
  --notification-configuration '{
    "QueueConfigurations": [
      {
        "QueueArn": "arn:aws:sqs:us-east-1:000000000000:locations-csv-events",
        "Events": ["s3:ObjectCreated:*"],
        "Filter": {
          "Key": {
            "FilterRules": [
              {"Name": "suffix", "Value": ".csv"}
            ]
          }
        }
      }
    ]
  }'
