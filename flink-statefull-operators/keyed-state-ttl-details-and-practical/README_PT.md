# Flink Keyed State - Ativar Time-To-Live (TTL): Detalhes e Prática

Este módulo apresenta um exemplo prático e teórico para ativar **Keyed State TTL** em Apache Flink `1.20.3`, alinhado com o fluxo explicado na transcrição da aula.

## Objetivo

- Criar um módulo dedicado (descendente de `flink-statefull-operators`) focado em `Keyed State TTL`.
- Demonstrar como ativar TTL num `MapStateDescriptor`.
- Implementar semântica de `left join` com `KeyedCoProcessFunction`, onde o estado do lado direito expira automaticamente.

## Keyed State e TTL

### Keyed State (definição geral)

O keyed state é mantido como um repositório embebido de chave/valor.
O estado é particionado e distribuído em conjunto com os streams com chave.
Por isso, o keyed state só pode ser acedido após `keyBy(...)` e apenas para a chave atual em processamento.

Este alinhamento de chaves garante atualizações locais de estado (sem transações distribuídas por atualização) e permite ao Flink redistribuir estado de forma transparente durante reescala.

### Tipos de interface de estado

- `ValueState<T>`: guarda um valor por chave.
- `ReducingState<T>`: guarda uma agregação reduzida por chave.
- `AggregatingState<IN, OUT>`: guarda agregação onde tipo de entrada e saída podem ser diferentes.
- `ListState<T>`: guarda uma lista de elementos por chave.
- `MapState<UK, UV>`: guarda pares chave-valor por chave.

### O que o TTL faz

Quando o TTL é configurado para keyed state:

- cada valor de estado recebe metadados com timestamp do último acesso relevante;
- entradas expiradas não são devolvidas (quando configurado com `NeverReturnExpired`);
- a limpeza é feita pelos mecanismos do Flink (lazy/no acesso + cleanup do backend);
- para estados de coleção (`ListState`, `MapState`), o TTL funciona **por entrada**.

## Exemplo incluído

Classe principal:

- `src/main/java/io/github/jlmc/flink/stateful/ttl/KeyedStateTtlDetailsAndPracticalExample.java`

Comportamento implementado:

1. Lê eventos `User` no socket `localhost:9998` (`id,name`).
2. Lê eventos `Address` no socket `localhost:9999` (`id,country`).
3. Faz `connect` dos streams com `keyBy(id)`.
4. Em `open(...)`, cria `MapStateDescriptor<Integer, Address>`.
5. Constrói `StateTtlConfig` com:
   - `Time.minutes(1)`
   - `UpdateType.OnCreateAndWrite`
   - `StateVisibility.NeverReturnExpired`
6. Ativa TTL via `descriptor.enableTimeToLive(ttlConfig)`.
7. Em `processElement2`, guarda endereço no map state.
8. Em `processElement1`, emite `UserWithAddress(user, addressOuNull)` (semântica de left join).

## Porque este exemplo demonstra left join + TTL

- Left join: quando chega utilizador e ainda não existe endereço, o output é emitido na mesma com endereço `null`.
- Efeito do TTL: quando a entrada de endereço expira no estado, eventos futuros do utilizador voltam a produzir endereço `null`.

## Detalhes técnicos e considerações de TTL

- O TTL é avaliado nos caminhos de acesso/atualização do estado e também por rotinas de cleanup do backend.
- `NeverReturnExpired` garante que dados expirados são filtrados em leitura, mesmo antes de remoção física.
- `OnCreateAndWrite` renova TTL apenas em criação/escrita; leituras não estendem a validade.
- Metadados de TTL introduzem overhead adicional por entrada de estado.
- Com backend RocksDB, a remoção física pode ocorrer durante compaction, enquanto a expiração lógica é aplicada na camada de estado do Flink.

## `open(...)` instrução a instrução (com justificação)

Bloco de referência de `LeftJoinFunction.open(...)`:

```java
StateTtlConfig ttlConfig = StateTtlConfig
        .newBuilder(Duration.ofMinutes(1))
        .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
        .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
        .build();

MapStateDescriptor<Integer, Address> descriptor =
        new MapStateDescriptor<>("mapState", Types.INT, Types.POJO(Address.class));
descriptor.enableTimeToLive(ttlConfig);

addressState = getRuntimeContext().getMapState(descriptor);
```

Decomposição detalhada:

1. `StateTtlConfig ttlConfig = StateTtlConfig ...`
   - Cria o objeto de política TTL que o runtime de estado do Flink vai aplicar.
   - Justificação: centraliza o comportamento de expiração e torna explícito o ciclo de vida do estado.

2. `.newBuilder(Duration.ofMinutes(1))`
   - Define o tempo de retenção (`1 minuto`) para cada entrada do `MapState`.
   - Justificação: neste exemplo facilita a observação da expiração durante a demo; em termos operacionais evita crescimento ilimitado do estado.

3. `.setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)`
   - Reinicia o TTL apenas em criação/escrita (`put`, `putAll`, etc.).
   - Justificação: leituras não devem “reviver” estado antigo. Num cache para left join, isto evita que endereços obsoletos se mantenham indefinidamente só porque há leituras frequentes.

4. `.setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)`
   - Entradas expiradas são tratadas como inexistentes em leitura, mesmo antes de remoção física.
   - Justificação: garante comportamento determinístico no join: após expiração, `addressState.get(id)` devolve `null`, alinhado com a semântica esperada de left join.

5. `.build()`
   - Fecha a construção da configuração TTL (imutável).
   - Justificação: evita mutações acidentais durante execução e mantém política estável para o descritor.

6. `new MapStateDescriptor<>("mapState", Types.INT, Types.POJO(Address.class))`
   - Declara os metadados do estado: nome lógico + tipos/serialização da chave e do valor.
   - Justificação:
     - `"mapState"` identifica este estado no operador.
     - `Types.INT` corresponde à chave do join (`id`).
     - `Types.POJO(Address.class)` serializa o payload do lado direito guardado no estado.

7. `descriptor.enableTimeToLive(ttlConfig)`
   - Ativa TTL neste descritor específico de estado.
   - Justificação: sem esta chamada, o estado não expira automaticamente, anulando o objetivo de cleanup automático.

8. `addressState = getRuntimeContext().getMapState(descriptor)`
   - Obtém a instância de `MapState` gerida pelo runtime do Flink.
   - Justificação: liga o descritor (já com TTL) ao estado gerido, com partição por chave, integração com checkpoints e restauração após falha.

## Como executar

Na raiz do repositório:

```bash
mvn -pl flink-statefull-operators/keyed-state-ttl-details-and-practical -am clean package
```

Arrancar produtor de utilizadores (terminal A):

```bash
nc -lk 9998
```

Arrancar produtor de endereços (terminal B):

```bash
nc -lk 9999
```

Executar `KeyedStateTtlDetailsAndPracticalExample.main` no IDE.

Enviar eventos de exemplo:

Terminal A (`9998`):

```text
1,Alex
```

Terminal B (`9999`):

```text
1,CN
```

Terminal A (`9998`) novamente:

```text
1,Alex
```

Esperar cerca de um minuto (TTL), e enviar novamente:

```text
1,Alex
```

Formato esperado:

```text
UserWithAddress{userId=1, userName='Alex', country='null'}
UserWithAddress{userId=1, userName='Alex', country='CN'}
UserWithAddress{userId=1, userName='Alex', country='null'}
```

## Referências oficiais (Flink 1.20.x)

- Working with State (Keyed State, TTL):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
- Secção de conceito State TTL:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/#state-time-to-live-ttl
- Checkpointing e tolerância a falhas:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/checkpointing/
- State backends (`HashMapStateBackend`, `EmbeddedRocksDBStateBackend`):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/
- API `StateTtlConfig`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/StateTtlConfig.html
- API `MapState`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/MapState.html
- API `MapStateDescriptor`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/MapStateDescriptor.html
- API `KeyedCoProcessFunction`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/streaming/api/functions/co/KeyedCoProcessFunction.html
- DataStream `ConnectedStreams` / `connect`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/overview/
