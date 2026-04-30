# Apache Flink 1.20.3 - Guia e Exemplos de Watermarks

Este módulo consolida orientação prática sobre processamento em tempo de evento com **Watermarks** no Apache Flink **1.20.3**.

## 1. Semântica de Tempo

O Flink suporta diferentes semânticas de tempo. Escolher a correta é essencial para janelas e joins.

### Processing Time
- Usa o relógio local da máquina que executa o operador.
- Menor latência e configuração mais simples.
- Não é determinístico em replay, backfill ou ingestão com atraso.

### Event Time
- Usa timestamps carregados pelos próprios eventos.
- É determinístico e robusto para streams fora de ordem.
- Exige extração de timestamp e geração de watermark.

### Processing Time vs Event Time
- **Determinismo:** Event Time vence.
- **Latência:** Processing Time normalmente vence.
- **Resiliência a atraso/desordem:** Event Time vence.
- Regra prática: use Event Time quando a correção de negócio depende de quando o evento realmente aconteceu.

## 2. Introdução a Watermarks

### O que é um Watermark?
Watermark é o sinal de progresso de tempo de evento no Flink. Um watermark em `T` significa que o Flink assume que não devem chegar mais eventos com timestamp `<= T` (exceto atrasados, se tolerados).

### Elementos fora de ordem (Out-of-orderness)
Streams reais não chegam perfeitamente ordenadas. O Flink modela a desordem esperada ao atrasar o avanço de watermark (por exemplo, desordem limitada).

### Dados atrasados (Late Data)
Eventos que chegam depois de o watermark ultrapassar a fronteira da janela são eventos atrasados.
- Parte deles ainda pode ser aceita via allowed lateness.
- Eventos muito atrasados podem ser enviados para side output.

## 3. Watermark API (Flink 1.20.3)

### WatermarkStrategy (padrão recomendado)
No Flink 1.20.3, `WatermarkStrategy` é a API padrão e recomendada para extração de timestamp + geração de watermark.

```java
WatermarkStrategy<MyEvent> strategy = WatermarkStrategy
    .<MyEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withTimestampAssigner((event, ts) -> event.getEventTime());

DataStream<MyEvent> stream = source.assignTimestampsAndWatermarks(strategy);
```

### Propagação de Watermark
- Watermarks propagam através dos operadores.
- Em streams paralelos, o avanço downstream segue o **mínimo** watermark entre partições/subtarefas upstream.
- Uma partição lenta pode travar o progresso global de event time.

### Fontes ociosas (Idle Sources)
Use detecção de ociosidade para que partições silenciosas temporariamente não bloqueiem o watermark global:

```java
WatermarkStrategy<MyEvent> strategy = WatermarkStrategy
    .<MyEvent>forBoundedOutOfOrderness(Duration.ofSeconds(5))
    .withTimestampAssigner((event, ts) -> event.getEventTime())
    .withIdleness(Duration.ofSeconds(30));
```

### Allowed Lateness
Operadores de janela podem aceitar eventos atrasados por um período de tolerância configurado:

```java
.window(TumblingEventTimeWindows.of(Time.minutes(1)))
.allowedLateness(Time.seconds(30))
```

### Late Data Side Output
Eventos muito atrasados podem ser desviados para auditoria, alertas ou compensação:

```java
final OutputTag<MyEvent> lateTag = new OutputTag<>("late-events", Types.POJO(MyEvent.class));

SingleOutputStreamOperator<Result> main = stream
    .keyBy(MyEvent::getKey)
    .window(TumblingEventTimeWindows.of(Time.minutes(1)))
    .sideOutputLateData(lateTag)
    .process(new MyProcessWindowFunction());

DataStream<MyEvent> late = main.getSideOutput(lateTag);
```

## 4. DataStream Join com Semântica Temporal

### Window Join
Une dois streams quando os eventos de ambos caem na mesma janela.

Use quando:
- ambos os streams representam fatos correlacionados em baldes de tempo alinhados,
- e a granularidade por janela é aceitável.

### Interval Join
Une stream `A` com stream `B` quando `B.timestamp` está dentro de um intervalo relativo ao timestamp de `A`.

Use quando:
- você precisa de relações temporais assimétricas,
- e de restrições explícitas de `before/after` (por exemplo, `A.ts - 2s <= B.ts <= A.ts + 5s`).

## Notas do Flink 1.20.3

- `WatermarkStrategy` continua sendo a API moderna recomendada (em vez de assigners legados).
- Melhorias de checkpointing e state management na linha 1.20.x ajudam a estabilizar workloads com uso intensivo de event time e janelas com estado grande.

## Progressão prática sugerida

1. Comece com Event Time + bounded out-of-orderness.
2. Adicione idleness para partições esparsas.
3. Configure allowed lateness para correções controladas.
4. Direcione eventos muito atrasados para side output.
5. Adicione um cenário de Window Join e um de Interval Join.
