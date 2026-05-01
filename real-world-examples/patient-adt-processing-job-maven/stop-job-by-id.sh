#!/bin/bash

JOB_ID=$1

if [ -z "$JOB_ID" ]; then
    echo "Erro: Forneça o ID do job."
    echo "Uso: $0 <JOB_ID>"
    exit 1
fi

echo "Cancelando job: $JOB_ID"
docker exec jobmanager flink cancel "$JOB_ID"

if [ $? -eq 0 ]; then
    echo "Job $JOB_ID cancelado com sucesso."
else
    echo "Erro ao cancelar o job $JOB_ID ou job já inexistente."
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
