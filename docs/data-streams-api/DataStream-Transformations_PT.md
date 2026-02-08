# Stream Data Transformations (Apache Flink – DataStream API)

Uma **stream transformation** recebe um ou mais *streams* de entrada e produz um ou mais *streams* de saída.
Escrever um programa usando a **DataStream API** basicamente consiste em encadear transformações para construir um **dataflow graph** que implementa a lógica da aplicação.

A maioria das transformações é baseada em **funções definidas pelo utilizador (UDFs)**.
Essas funções definem como cada elemento do *“input” stream* será convertido em elementos do *_output_ stream*.

As “interfaces” de função são, em geral, **SAM (Single Abstract Method)**, portanto podem ser implementadas com **lambdas Java 8+** (e também com lambdas em Scala).

A DataStream API organiza as transformações em quatro categorias principais:

1. **Basic transformations** – operam evento a evento (stateless)
2. **KeyedStream transformations** – operam por chave (stateful)
3. **Multistream transformations** – combinam ou separam streams
4. **Distribution transformations** – alteram a distribuição física dos dados

---

## 1. Basic Transformations (Stateless)

Operam independentemente sobre cada evento.
Não guardam memória do passado.

Operadores mais comuns:

* `map`
* `filter`
* `flatMap`

### Exemplo – Map

```java
DataStream<String> input = env.fromElements("flink", "stream", "data");

DataStream<String> upperCase = input.map(s -> s.toUpperCase());
```

**Ideia importante:**
Cada elemento entra → é transformado → sai imediatamente.
Nenhuma informação entre eventos é mantida.

---

## 2. KeyedStream Transformations (Stateful)

Estas transformações exigem primeiro um:

```
keyBy(...)
```

O `keyBy` **não agrupa dados como no SQL**.
Ele faz algo diferente:

> Ele particiona logicamente o stream, garantindo que todos os eventos com a mesma chave sejam processados pela mesma instância do operador.

Isso permite manter **estado por chave**.

### Exemplo – Word Count

```java
DataStream<WordEntry> stream = env.fromElements(
    new WordEntry("apple", 1), 
    new WordEntry("apple", 1), 
    new WordEntry("banana", 1)
);

DataStream<WordEntry> counts = stream
    .keyBy(entry -> entry.word)
    .reduce((v1, v2) -> new WordEntry(v1.word, v1.count + v2.count));
```

Aqui, Flink cria **um contador independente para cada palavra**.

---

### Exemplo – Soma por sensor

```java
DataStream<SensorReading> totals = readings
    .keyBy(sensor -> sensor.id)
    .sum("value");
```

Cada `sensor.id` mantém o seu próprio estado interno.

👉 Isto só funciona porque o operador agora é **stateful**.

---

## 3. Multistream Transformations

Permitem combinar múltiplos streams.

### `union()`

* Junta streams **do mesmo tipo**
* Não partilha estado

```java
DataStream<String> merged = stream1.union(stream2);
```

---

### `connect()`

* Permite streams **de tipos diferentes**
* Podem **partilhar lógica e estado**

Este é um operador muito importante em sistemas reais (regras dinâmicas, feature flags, configurações em tempo real).

```java
DataStream<String> data = env.fromElements("user_1", "user_2");
DataStream<Boolean> control = env.fromElements(true);

DataStream<String> result = data
    .connect(control.broadcast())
    .flatMap(new CoFlatMapFunction<String, Boolean, String>() {

        private boolean shouldProcess = true;

        @Override
        public void flatMap1(String value, Collector<String> out) {
            if (shouldProcess)
                out.collect("Processing: " + value);
        }

        @Override
        public void flatMap2(Boolean value, Collector<String> out) {
            shouldProcess = value;
        }
    });
```

Aqui:

* `data` = eventos
* `control` = stream de controlo (configuração)

O segundo stream altera o comportamento do primeiro **em tempo real**.

---

## 4. Distribution Transformations

Estas transformações **não mudam os dados**.
Elas mudam **como os dados são distribuídos entre tarefas paralelas**.

São fundamentais para desempenho e para evitar **data skew** (quando um worker recebe quase tudo).

### `rebalance()`

Distribuição Round-Robin uniforme:

```java
DataStream<Long> output = heavyData
    .rebalance()
    .map(val -> performHeavyComputation(val));
```

Sem `rebalance`, um único core pode ficar sobrecarregado.

---

## Notas Técnicas (Flink 1.20)

* Lambdas Java 8+ totalmente suportadas
* Para tipos complexos pode ser necessário:

```
.returns(Types.POJO(...))
```

(devido ao sistema de extração de tipos do Flink)

* O **SinkV2 API** (1.15+ e refinado em 1.20) é o método recomendado para outputs (batch + streaming consistente).

---

# KeyedProcessFunction

O `KeyedProcessFunction` é considerado o **operador mais poderoso da DataStream API**.

Ele dá controlo direto sobre:

* **State (estado persistente por chave)**
* **Timers (ações baseadas no tempo)**

Depois de um `keyBy`, cada chave tem:

* o seu próprio estado
* os seus próprios timers

---

## O que o Flink fornece

Para cada evento:

1. **Context** → chave atual e timestamp
2. **State** → armazenamento persistente (checkpointed)
3. **TimerService** → callbacks futuros

---

## Exemplo: Alerta de Inatividade

Se um sensor parar de enviar dados por 10 segundos → gerar alerta.

```java
public class InactivityAlertFunction
    extends KeyedProcessFunction<String, SensorReading, String> {

    private ValueState<Long> timerState;

    @Override
    public void open(OpenContext ctx) {
        timerState = getRuntimeContext()
            .getState(new ValueStateDescriptor<>("timer-state", Long.class));
    }

    @Override
    public void processElement(SensorReading value, Context ctx, Collector<String> out) throws Exception {

        long now = ctx.timerService().currentProcessingTime();
        long timeout = now + 10_000;

        Long lastTimer = timerState.value();
        if (lastTimer != null) {
            ctx.timerService().deleteProcessingTimeTimer(lastTimer);
        }

        ctx.timerService().registerProcessingTimeTimer(timeout);
        timerState.update(timeout);
    }

    @Override
    public void onTimer(long timestamp, OnTimerContext ctx, Collector<String> out) {
        out.collect("Alert: Sensor " + ctx.getCurrentKey() + " inactive for 10s!");
        timerState.clear();
    }
}
```

---

## Componentes Importantes

| Método             | Função                           |
|--------------------|----------------------------------|
| `processElement()` | Executado em cada evento         |
| `onTimer()`        | Executado quando o timer dispara |
| `ValueState`       | Memória persistente por chave    |
| `TimerService`     | Agenda ações futuras             |

---

## Por que não usar apenas `map`?

`map` é **stateless** → não lembra nada.

`KeyedProcessFunction` é **stateful** → lembra eventos anteriores.

Permite construir:

* Sessões de utilizador
* Deduplicação
* Rate limiting
* Detecção de fraude
* Timeouts
* Regras dinâmicas

---
