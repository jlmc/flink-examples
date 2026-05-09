# Flink Keyed State - Detalhes e Prática de MapState

Este módulo apresenta um exemplo prático e detalhado de `MapState` em Apache Flink `1.20.3`, alinhado com o fluxo explicado na transcrição da aula.

## Objetivo

- Criar um módulo dedicado (descendente de `flink-statefull-operators`) para estudar `MapState`.
- Demonstrar o uso de `MapState` dentro de `KeyedProcessFunction`.
- Calcular média por `courseName` dentro de cada `classId`.

## Keyed State

### Definição geral

O keyed state é mantido como um repositório embebido de chave/valor.
O estado é particionado e distribuído estritamente em conjunto com os streams lidos por operadores stateful.
Por isso, o acesso ao estado chave/valor só é possível em streams com `keyBy(...)` e fica limitado aos valores associados à chave do evento atual.

Ao alinhar as chaves do stream com o estado, todas as atualizações tornam-se operações locais, garantindo consistência sem overhead transacional.
Este alinhamento também permite ao Flink redistribuir estado e ajustar o particionamento dos streams de forma transparente quando há reescala.

### Tipos de interface de estado

- `ValueState<T>`: mantém um único valor, com leitura via `value()` e atualização via `update(T)`.
- `ReducingState<T>`: mantém um único valor que representa a redução/combinação de todos os valores adicionados ao estado.
- `AggregatingState<IN, OUT>`: mantém um valor agregado; ao contrário de `ReducingState`, o tipo de saída pode ser diferente do tipo de entrada.
- `ListState<T>`: mantém uma lista de elementos; suporta `add(T)`, `addAll(List<T>)`, `get()` e `update(List<T>)`.
- `MapState<UK, UV>`: mantém mapeamentos chave-valor; suporta `put(UK, UV)`, `putAll(Map<UK, UV>)`, `get(UK)`, `entries()`, `keys()`, `values()` e `isEmpty()`.

## Exemplo incluído

Classe principal:

- `src/main/java/io/github/jlmc/flink/stateful/mapstate/MapStateDetailsAndPracticalExample.java`

Comportamento implementado:

1. Lê eventos de pontuação via socket (`localhost:9999`).
2. Faz parse do formato `classId,studentId,courseName,score`.
3. Aplica `keyBy(classId)` para isolar estado por turma.
4. Usa `MapState<String, Float>` onde:
   - chave = `courseName`
   - valor = média atual dessa disciplina nessa turma
5. Para cada nova pontuação:
   - se não existir histórico para a disciplina, guarda a pontuação atual;
   - caso contrário, atualiza com `(newScore + previousAvg) / 2` (mesma lógica da transcrição).
6. Emite `(classId, courseName, avgScore)` em `Tuple3<String, String, Float>`.

## Porque usar `MapState`

`MapState` é indicado quando, para cada chave principal (neste caso `classId`), precisamos de um mapa dinâmico secundário (neste caso `courseName -> avgScore`).

Face a `ValueState`, permite acesso direto por disciplina sem serializar manualmente uma estrutura única com todos os cursos.

### Vantagens práticas do `MapState`

- **Atualizações parciais eficientes**: permite atualizar apenas uma entrada interna (`courseName`) sem reescrever um objeto agregado completo.
- **Modelação natural para dimensões dinâmicas/esparsas**: disciplinas podem surgir/desaparecer por turma sem alterações de esquema.
- **Menor complexidade na aplicação**: `get/put` por subchave evita lógica manual de serialização/deserialização de mapas.
- **Separação clara de responsabilidades**: `keyBy(classId)` define a partição principal e o `MapState` gere os valores por disciplina dentro dessa partição.

### Porque `MapState` é vantajoso com checkpoints

- O `MapState` faz parte do estado gerido (`managed keyed state`) do Flink, logo o seu conteúdo é incluído automaticamente em checkpoints.
- Em caso de falha/restart, o Flink restaura as entradas do mapa por chave a partir do último checkpoint bem-sucedido, preservando continuidade de cálculo.
- Isto suporta **consistência de estado exactly-once**: o estado recuperado reflete um snapshot consistente, e não um momento arbitrário em memória.
- Com estado grande e `EmbeddedRocksDBStateBackend`, os checkpoints podem ser incrementais ao nível do backend, reduzindo I/O de checkpoint para mudanças de estado.
- Na prática, obténs ergonomia de atualização simples no código e tolerância a falhas de nível de produção.

## Como executar

Na raiz do repositório:

```bash
mvn -pl flink-statefull-operators/map-state-details-and-practical -am clean package
```

Arrancar produtor socket local (terminal A):

```bash
nc -lk 9999
```

Executar o job a partir do IDE (`MapStateDetailsAndPracticalExample.main`) ou empacotar/executar como habitual.

Enviar eventos de exemplo no terminal A:

```text
class-1,student-1,Math,18
class-1,student-2,Math,14
class-1,student-3,Physics,16
class-2,student-1,Math,11
class-1,student-4,Math,20
```

Formato esperado de saída:

```text
(class-1,Math,18.0)
(class-1,Math,16.0)
(class-1,Physics,16.0)
(class-2,Math,11.0)
(class-1,Math,18.0)
```

## Detalhes importantes da implementação

- O `MapState` é inicializado em `open(...)` via `MapStateDescriptor`.
- `ctx.getCurrentKey()` devolve o `classId` corrente definido em `keyBy(...)`.
- O estado é mantido por chave e gerido pelo runtime do Flink (incluindo integração com checkpoints).
- O exemplo mantém intencionalmente a regra simples de média usada na aula transcrita.

## Referências oficiais

- Working with State (Keyed/Operator State):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/state/
- Checkpointing e tolerância a falhas:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/fault-tolerance/checkpointing/
- State Backends (inclui contexto de checkpoints incrementais):
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/ops/state/state_backends/
- API `MapState`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/api/common/state/MapState.html
- API `KeyedProcessFunction`:
  - https://nightlies.apache.org/flink/flink-docs-release-1.20/api/java/org/apache/flink/streaming/api/functions/KeyedProcessFunction.html
