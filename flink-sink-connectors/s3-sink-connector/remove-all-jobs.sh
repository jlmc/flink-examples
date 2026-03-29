#!/bin/bash

# Get the directory of the script
DIR="$( cd "$( dirname "${BASH_SOURCE[0]}" )" >/dev/null 2>&1 && pwd )"

# Flink JobManager endpoint
FLINK_URL="http://localhost:8081"

echo "Fetching all jobs from Flink JobManager at $FLINK_URL..."

# Get all jobs
JOBS_RESPONSE=$(curl -s "$FLINK_URL/jobs")

if [ -z "$JOBS_RESPONSE" ]; then
    echo "Error: Could not connect to Flink JobManager at $FLINK_URL."
    exit 1
fi

# Parse job IDs that are not in terminal states (FINISHED, FAILED, CANCELED)
# Using python if available for easy JSON parsing, or just simple grep/sed
JOB_IDS=$(echo "$JOBS_RESPONSE" | python3 -c "import sys, json; data = json.load(sys.stdin); print('\n'.join([job['id'] for job in data['jobs'] if job['status'] not in ['FINISHED', 'FAILED', 'CANCELED', 'CANCELLING']]))" 2>/dev/null)

# Fallback if python3 is not available
if [ $? -ne 0 ]; then
    JOB_IDS=$(echo "$JOBS_RESPONSE" | grep -o '"id":"[a-f0-9]*"' | cut -d'"' -f4)
    # Note: this fallback might include jobs that are already finished, 
    # but the cancel request will just fail gracefully on those.
fi

if [ -z "$JOB_IDS" ]; then
    echo "No running or active jobs found to remove."
    exit 0
fi

echo "Found the following active jobs to cancel:"
echo "$JOB_IDS"

for JOB_ID in $JOB_IDS; do
    echo "Cancelling job $JOB_ID..."
    # PATCH /jobs/:jobid?mode=cancel is the standard way to cancel a job in recent Flink versions
    CANCEL_RESPONSE=$(curl -s -X PATCH "$FLINK_URL/jobs/$JOB_ID?mode=cancel")
    echo "Response: $CANCEL_RESPONSE"
done

echo "All active jobs have been sent a cancellation request."
