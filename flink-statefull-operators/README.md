# Flink Stateful Operators (ADT Events)

Novo agregador para estudar operadores stateful no Apache Flink com domínio Healthcare FHIR ADT.

## Módulos

- `stateful-and-state`
  - Tema: What is stateful application + State in Apache Flink.
  - Exemplo: `StatefulAndStateAdtExample`.
- `keyed-state`
  - Tema: `ValueState`, `ListState`, `MapState`, `ReducingState`, `AggregatingState`, `State TTL`.
  - Exemplo: `KeyedStateAdtExample`.
- `operator-and-broadcast-state`
  - Tema: `Operator State`, `Broadcast State`, `Stateful Source Function`.
  - Exemplo: `OperatorAndBroadcastStateAdtExample`.
- `state-backend`
  - Tema: `HashMapStateBackend` e `EmbeddedRocksDBStateBackend`.
  - Exemplo: `StateBackendAdtExample`.

## Documentação

A pasta `documentations/` contém a explicação por tópico:

- `1.stateful-and-state.md`
- `2.keyed-state.md`
- `3.operator-and-broadcast-state.md`
- `4.state-backend.md`

## Referências oficiais (Flink 1.20.3)

- Stateful stream processing (conceitos): https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/concepts/stateful-stream-processing/
- Working with State (Keyed/Operator State, TTL): https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
- Broadcast State: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/broadcast_state/
- State Backends: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/
- Checkpointing: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/checkpointing/
