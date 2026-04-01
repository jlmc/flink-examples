#!/bin/bash

# Get the directory of the script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Find the shaded JAR
JAR_FILE=$(find "$DIR/target" -name "jdbc-sink-connector-*-shaded.jar" | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: Shaded JAR not found in $DIR/target. Please run build-jdk11.sh first."
    exit 1
fi

echo "Uploading job $JAR_FILE to Flink JobManager..."

# Upload the JAR to the JobManager
# Assuming the JobManager is accessible on localhost:8081
RESPONSE=$(curl -s -X POST -H "Expect:" -F "jarfile=@$JAR_FILE" http://localhost:8081/jars/upload)

if echo "$RESPONSE" | grep -q '"status":"success"'; then
    JAR_ID=$(echo "$RESPONSE" | sed -n 's/.*"filename":"\([^"]*\)".*/\1/p' | awk -F'/' '{print $NF}')
    echo "Successfully uploaded JAR. JAR ID: $JAR_ID"
    
    echo "Running the job..."
    RUN_RESPONSE=$(curl -s -X POST "http://localhost:8081/jars/$JAR_ID/run")
    
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
