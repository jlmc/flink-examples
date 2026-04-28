#!/bin/bash

# Este script cancela todos os jobs em execução no Flink e remove todos os JARs carregados via API REST
# Assume que o container do jobmanager chama-se 'jobmanager'

echo "Listando jobs em execução..."
JOBS=$(docker exec jobmanager flink list | grep 'RUNNING' | awk '{print $4}')

if [ -z "$JOBS" ]; then
    echo "Nenhum job em execução encontrado."
else
    for JOB_ID in $JOBS; do
        echo "Cancelando job: $JOB_ID"
        docker exec jobmanager flink cancel "$JOB_ID"
    done
    echo "Todos os jobs ativos foram cancelados."
fi

echo "Removendo todos os JARs carregados via API REST..."
JAR_IDS=$(docker exec jobmanager curl -s localhost:8081/jars | sed -n 's/.*"id":"\([^"]*\)".*/\1/p' | tr ',' '\n')

if [ -z "$JAR_IDS" ]; then
    echo "Nenhum JAR carregado encontrado."
else
    for JAR_ID in $JAR_IDS; do
        echo "Removendo JAR: $JAR_ID"
        docker exec jobmanager curl -s -X DELETE "localhost:8081/jars/$JAR_ID" > /dev/null
    done
    echo "Todos os JARs foram removidos."
fi
