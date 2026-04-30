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
  - Exemplos: `HashMapStateBackendAdtExample` e `EmbeddedRocksDbStateBackendAdtExample`.

## Documentação

A pasta `documentations/` contém a explicação por tópico:

- `1.stateful-and-state.md`
- `2.keyed-state.md`
- `3.operator-and-broadcast-state.md`
- `4.state-backend.md`
- `5.checkpointing-and-fault-tolerance.md`

---

# Apache Flink 1.20.3: Stateful Stream Processing

## 1. Stateful and State (Conceitos Fundamentais)

No Flink, o **Estado (State)** é o que permite que uma aplicação de streaming seja mais do que um simples filtro de eventos. Ele é a "memória" do sistema.

*   **Por que Estado?** Para realizar operações como janelas (Windows), detecção de padrões (CEP) ou junções (Joins), o sistema precisa "lembrar" do que aconteceu anteriormente.
*   **Localidade dos Dados:** O estado é armazenado localmente no **TaskManager** (na Heap ou no disco local). Isso elimina a latência de rede que ocorreria se o sistema dependesse de um banco de dados externo.
*   **Snapshotting e Fault Tolerance:** Através do algoritmo de *Asynchronous Barrier Snapshotting*, o Flink tira fotos consistentes do estado sem interromper o processamento. Se houver uma falha, o estado é restaurado a partir de um checkpoint salvo no armazenamento durável (S3, HDFS, Azure Blob).

---

## 2. Keyed State

O **Keyed State** é vinculado a uma chave específica e só pode ser utilizado após uma operação de `.keyBy()`. O Flink mantém um repositório de estados para cada chave única no fluxo.

### Tipos de Keyed State
*   **ValueState<T>:** Armazena um único valor. Métodos: `value()` e `update(T)`.
*   **ListState<T>:** Armazena uma lista de elementos. Métodos: `add(T)`, `addAll(List<T>)` e `get()`.
*   **MapState<UK, UV>:** Armazena pares chave-valor. Métodos: `put(UK, UV)`, `get(UK)` e `entries()`.
*   **ReducingState<T>:** Retorna um único valor que é o resultado da redução de todos os elementos adicionados ao estado.
*   **AggregatingState<IN, OUT>:** Similar ao ReducingState, mas o tipo do resultado pode ser diferente do tipo dos elementos adicionados.

### State TTL (Time-to-Live)
Permite que o estado expire automaticamente após um período definido, liberando recursos.
*   **Configuração:** Pode ser configurado para limpar no acesso de leitura ou escrita.
*   **Utilidade:** Fundamental para manter a saúde do cluster em fluxos com chaves infinitas (ex: IDs de sessão que nunca se repetem).

---

## 3. Operator and Broadcast State

Diferente do Keyed State, estes tipos de estado pertencem a uma instância paralela do operador e não a chaves específicas.

### Operator State
Frequentemente utilizado para gerenciar o estado de fontes (Sources) e sumidouros (Sinks).
*   **Escalabilidade:** Quando o paralelismo do job aumenta ou diminui, o Operator State é redistribuído.
*   **ListState:** O tipo mais comum. Na redistribuição, as listas são concatenadas e depois divididas entre as novas instâncias.

### Broadcast State
Utilizado quando você precisa que uma regra ou configuração seja aplicada a todos os eventos de um fluxo, independentemente da chave.
*   **Mecanismo:** Um fluxo (geralmente de controle/regras) é transmitido para todas as instâncias de um operador.
*   **Exemplo:** Um fluxo de "Regras de Fraude" que deve ser consultado por todos os processadores de "Transações".

---

## 4. State Backends

O **State Backend** define a estrutura física onde o estado de trabalho é mantido durante a execução.

| Característica | HashMapStateBackend | EmbeddedRocksDBStateBackend |
| :--- | :--- | :--- |
| **Armazenamento** | Heap da JVM (Objetos Java) | Disco Local (RocksDB embarcado) |
| **Tamanho do Estado** | Limitado pela RAM da TaskManager | Limitado pelo disco local |
| **Velocidade** | Acesso mais rápido (Memória) | Latência de serialização/deserialização |
| **Checkpoints** | Sempre Full Checkpoints | Suporta **Checkpoints Incrementais** |
| **Uso Ideal** | Estados pequenos e baixa latência | Estados gigantes (TB) e alta escalabilidade |

---

## 5. Checkpointing (Tolerância a Falhas)

O Checkpointing transforma um fluxo de dados instável em um sistema confiável.

*   **Exatamente-Uma-Vez (Exactly-Once):** O Flink garante que, mesmo após falhas, os resultados finais sejam como se cada evento tivesse sido processado exatamente uma vez.
*   **Checkpoints Incrementais:** (Exclusivo RocksDB) Apenas as mudanças desde o último checkpoint são enviadas para o armazenamento persistente, economizando largura de banda e tempo.
*   **Unaligned Checkpoints:** Recurso para situações de **Backpressure**. Permite que o checkpoint finalize mesmo se o fluxo estiver congestionado, pois as barreiras podem "pular a fila" de buffers de rede.

---

## Exemplo Prático de Conceito (ADT)

> **Cenário:** Monitorar a duração de uma estadia.
> 1. Recebe evento `ADT_A01` (Admissão): Salva o timestamp no `ValueState`.
> 2. Recebe evento `ADT_A03` (Alta): Recupera o timestamp do `ValueState`, calcula a diferença e limpa o estado (`state.clear()`).
> 3. **TTL:** Se a alta não chegar em 30 dias, o estado expira automaticamente.

---

## Referências oficiais (Flink 1.20.3)

- Stateful stream processing (conceitos): https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/concepts/stateful-stream-processing/
- Working with State (Keyed/Operator State, TTL): https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
- Broadcast State: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/broadcast_state/
- State Backends: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/
- Checkpointing: https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/checkpointing/
