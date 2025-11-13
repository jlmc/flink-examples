

```java
DataStreamSource<String> sourceStream = env.fromSource(
        source,
        WatermarkStrategy.noWatermarks(), // Watermarks are not strictly needed in BATCH mode
        "File input source"
);
```

Vamos desmistificar essa linha do Flink:

```java
DataStreamSource<String> sourceStream = env.fromSource(
        source,
        WatermarkStrategy.noWatermarks(), // Watermarks are not strictly needed in BATCH mode
        "File input source"
);
```

---

## 🧩 **O que está a acontecer aqui**

Essa linha **cria um DataStream** (um fluxo de dados) a partir de uma **fonte (`source`)** que você definiu — pode ser um arquivo, Kafka, socket etc.
O método `fromSource()` pertence ao **`StreamExecutionEnvironment`** do Flink.

Vamos ver cada parte 👇

---

### 1️⃣ `env.fromSource(...)`

* `env` → é o ambiente principal de execução do Flink (`StreamExecutionEnvironment`).
* `fromSource` → cria um *stream* a partir de uma *source moderna* (a nova API unificada introduzida no Flink 1.12+).

  > Essa é a forma recomendada de usar *sources* hoje em vez do antigo `env.addSource()`.

O retorno é um **`DataStreamSource<T>`**, que é o ponto de partida para aplicar transformações (`map`, `filter`, `flatMap`, etc).

---

### 2️⃣ `source`

É o objeto `Source` que tu criou antes, por exemplo:

```java
KafkaSource<String> source = KafkaSource.<String>builder()
    .setBootstrapServers("kafka:9092")
    .setTopics("patient-adt-topic")
    .setGroupId("patient-adt-processing-job")
    .setStartingOffsets(OffsetsInitializer.latest())
    .setValueOnlyDeserializer(new SimpleStringSchema())
    .build();
```

👉 Esse objeto sabe **como ler** os dados (de Kafka, arquivo, etc).

---

### 3️⃣ `WatermarkStrategy.noWatermarks()`

**Watermarks** são marcas de tempo que o Flink usa para lidar com **event time** — ou seja, quando o tempo dos eventos importa (por exemplo, janelas baseadas em tempo real dos eventos, não no tempo de processamento).

* `noWatermarks()` indica que **não vamos usar event-time**.
* Isso é comum em:

    * Jobs em **modo batch**, onde a ordem dos eventos não importa.
    * Pipelines que apenas leem e processam dados sem janelas baseadas em tempo.

💬 Em resumo:

> “Não quero controlar tempo de evento — só processar os dados na ordem que chegam.”

---

### 4️⃣ `"File input source"`

É apenas o **nome da source** no pipeline.
Ele aparecerá no **Flink Web UI** e nos logs do job, ajudando a identificar o operador.

---

## 🧠 **Resumo final**

| Parte                              | O que faz                                          | Exemplo                                   |
| ---------------------------------- | -------------------------------------------------- | ----------------------------------------- |
| `env.fromSource(...)`              | Cria o fluxo a partir de uma source moderna        | Flink lê os dados de Kafka, arquivos, etc |
| `source`                           | A fonte configurada (KafkaSource, FileSource, etc) | Define de onde vem os dados               |
| `WatermarkStrategy.noWatermarks()` | Desativa uso de event time / marca d’água          | Ideal em jobs simples ou batch            |
| `"File input source"`              | Nome do operador (para logs/UI)                    | Mostrado no Flink dashboard               |

---

✅ **Em outras palavras:**

> Essa linha cria um *DataStream* chamado `sourceStream` a partir de uma fonte configurada (`source`), dizendo ao Flink que não precisa de marcações de tempo (`noWatermarks`), e dando um nome descritivo ao operador (“File input source”).

---


### 🔹 O que é `WatermarkStrategy.noWatermarks()`

No Apache Flink, as ***watermarks*** (ou “marcas de água”) são utilizadas para indicar o **progresso do tempo dos eventos** dentro de um fluxo de dados.

Quando usas:

```java
WatermarkStrategy.noWatermarks()
```

estás basicamente a dizer ao Flink:

> “Não quero usar tempo de evento (event time). Processa os registos apenas pela ordem em que chegam (processing time).”

Isto é suficiente se estiveres apenas a ler e transformar dados sem dependência de tempo — por exemplo, apenas a consumir mensagens e chamar um serviço HTTP.

---

### 🔹 Se quiseres usar a **linha do tempo (event time)**

Então deves **definir uma estratégia de *watermark*** adequada ao tipo de dados que estás a processar.
Ou seja, dizes ao Flink **onde está o carimbo temporal (timestamp)** em cada evento e **como gerar as marcas de água**.

Por exemplo:

```java
WatermarkStrategy
    .<PatientEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
    .withTimestampAssigner((event, timestamp) -> event.getEventTime())
```

#### O que isto faz:

* `forBoundedOutOfOrderness(Duration.ofSeconds(10))`
  👉 permite que os eventos cheguem **atrasados até 10 segundos**.

* `.withTimestampAssigner(...)`
  👉 indica ao Flink **qual o campo do evento contém o timestamp real** (por exemplo, `eventTime`).

---

### 🔹 Exemplo completo

```java
DataStreamSource<PatientEvent> stream = env.fromSource(
    source,
    WatermarkStrategy
        .<PatientEvent>forBoundedOutOfOrderness(Duration.ofSeconds(10))
        .withTimestampAssigner((event, ts) -> event.getTimestamp()),
    "Kafka Source com Watermarks"
);
```

Assim, o Flink:

* entende o tempo de cada evento (não apenas a ordem de chegada);
* consegue criar **janelas temporais** corretas (por ex., de 1 minuto);
* trata corretamente **eventos fora de ordem**.

---

### 🧠 Em resumo

| Estratégia                           | Significado                                               | Quando usar                       |
| ------------------------------------ | --------------------------------------------------------- | --------------------------------- |
| `WatermarkStrategy.noWatermarks()`   | Ignora tempo de evento, usa apenas tempo de processamento | Jobs simples ou batch             |
| `forBoundedOutOfOrderness(Duration)` | Usa tempo de evento, tolera pequenos atrasos              | Streams em tempo real             |
| `forMonotonousTimestamps()`          | Usa tempo de evento mas assume que nunca há atrasos       | Dados sempre em ordem cronológica |

---

Se quiseres, posso mostrar-te o **exemplo completo em Kotlin** com o `WatermarkStrategy` configurado para usar o tempo de evento do campo `timestamp` do teu `PatientEvent`.
Queres que te mostre isso?


