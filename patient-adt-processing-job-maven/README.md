# patient-adt-processing-job-maven

Novo módulo em `Java + Maven` com implementação equivalente ao processamento ADT:

- consumo de eventos ADT em JSON a partir de Kafka;
- parsing para uma classe simplificada `AdtEvent`;
- `keyBy(accountId_patientId)`;
- resolução de último estado por paciente com `MapState` + `TTL`;
- saída no `console sink` (ponto simples para trocar por Mongo/Kafka sink no teu ambiente).

## Build

```bash
mvn -pl patient-adt-processing-job-maven -am test
mvn -pl patient-adt-processing-job-maven -am package
```

## Run

```bash
java -jar patient-adt-processing-job-maven/target/patient-adt-processing-job-maven-1.0-SNAPSHOT.jar \
  --kafkaBootstrapServers localhost:9092 \
  --kafkaTopic hls-providers.hl7.adt \
  --kafkaGroupId patient-adt-processing-job-java \
  --flinkParallelism 1 \
  --eventTtlInDays 5 \
  --dischargedTtlInDays 2
```
