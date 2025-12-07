#!/bin/bash
# Wait for MongoDB to start
sleep 5

# Run replica set initiation
mongosh -u "$MONGO_INITDB_ROOT_USERNAME" -p "$MONGO_INITDB_ROOT_PASSWORD" --eval "rs.initiate()"
