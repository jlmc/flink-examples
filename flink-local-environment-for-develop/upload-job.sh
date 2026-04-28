#!/bin/bash

# Get the directory of the script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"
MODULE_NAME=$(basename "$DIR")

# Find the shaded JAR
JAR_FILE=$(find "$DIR/target" -name "$MODULE_NAME-*-shaded.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: Shaded JAR not found in $DIR/target. Please run 'mvn clean package' first."
    exit 1
fi

echo "Uploading job $JAR_FILE to Flink JobManager..."

# Upload the JAR to the JobManager
RESPONSE=$(curl -s -X POST -H "Expect:" -F "jarfile=@$JAR_FILE" http://localhost:8081/jars/upload)

if echo "$RESPONSE" | grep -q '"status":"success"'; then
    JAR_ID=$(echo "$RESPONSE" | sed -n 's/.*"filename":"\([^"]*\)".*/\1/p' | awk -F'/' '{print $NF}')
    echo "Successfully uploaded JAR. JAR ID: $JAR_ID"
    
    echo "Running the job..."
    # Prepare program arguments (example: --bootstrap.servers kafka:19092 --input.topic sensors-data --output.topic sensors-avg-data)
    ARGS="--bootstrap.servers kafka:19092 --input.topic sensors-data --output.topic sensors-avg-data"
    
    RUN_RESPONSE=$(curl -s -X POST "http://localhost:8081/jars/$JAR_ID/run" \
        -H "Content-Type: application/json" \
        -d "{\"programArgs\": \"$ARGS\"}")
    
    if echo "$RUN_RESPONSE" | grep -q '"jobid"'; then
        JOB_ID=$(echo "$RUN_RESPONSE" | sed -n 's/.*"jobid":"\([^"]*\)".*/\1/p')
        echo "Successfully started job. Job ID: $JOB_ID"
    else
        echo "Failed to start job. Response: $RUN_RESPONSE"
        exit 1
    fi
else
    echo "Failed to upload JAR. Response: $RESPONSE"
    exit 1
fi
