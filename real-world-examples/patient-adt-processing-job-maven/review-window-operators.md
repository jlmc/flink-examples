# Review: `patient-adt-processing-job-maven` vs `flink-time-based-and-window-operators`

## Objetivo
Avaliar se existem features do módulo `flink-time-based-and-window-operators` que podem ser reaproveitadas no job `patient-adt-processing-job-maven`.

## Estado atual do job ADT

### O que já está bem implementado
- Processamento por paciente com `keyBy(AdtEvent::patientKey)`.
- Processamento baseado em tempo de evento com `WatermarkStrategy.forBoundedOutOfOrderness(...)`.
- Resolução de estado por paciente com `KeyedProcessFunction` + `MapState`.
- Gestão de retenção com `StateTtlConfig` no estado do histórico.

### Limitação atual
- Não existem operadores de janela explícitos (`.window(...)`) no fluxo principal.
- A pipeline está focada em materialização do último estado no MongoDB, mas sem ramo dedicado para métricas temporais agregadas.

## Features do módulo de janelas com potencial de adoção

### 1) Session Windows (Event Time)
Referência no módulo: `session-windows/.../SessionWindowKafkaExample.java` com `EventTimeSessionWindows.withGap(...)`.

Aplicabilidade:
- Modelar episódios/sessões clínicas por paciente com fecho por inatividade.

Justificação:
- A semântica de sessão encaixa naturalmente em sequências ADT com períodos de atividade e inatividade.
- Permite produzir visão de “episódio” além do “último estado”.

Prioridade: **Média**

---

### 2) Tumbling Windows
Referência no módulo: `window-functions/.../WindowFunctionsKafkaExample.java` com `TumblingEventTimeWindows.of(...)`.

Aplicabilidade:
- Métricas operacionais em intervalos fixos (admissões, altas, transferências por janela temporal).

Justificação:
- Simples de operar e interpretar.
- Bom custo/benefício para observabilidade e relatórios near-real-time.

Prioridade: **Alta**

---

### 3) Sliding Windows
Referência no módulo: `sliding-windows/.../SlidingWindowKafkaExample.java` com `SlidingEventTimeWindows.of(...)`.

Aplicabilidade:
- KPIs contínuos (ex.: últimos 30 minutos atualizados a cada 5 minutos).

Justificação:
- Melhora percepção de tendência sem esperar fecho de janelas grandes.
- Útil para monitorização operacional em tempo quase contínuo.

Prioridade: **Alta**

---

### 4) Window Functions (`AggregateFunction` + `ProcessWindowFunction`)
Referência no módulo: `window-functions/.../WindowFunctionsKafkaExample.java`.

Aplicabilidade:
- Agregações incrementais eficientes e enriquecimento com metadados de janela (start/end).

Justificação:
- `AggregateFunction` reduz custo de memória para cálculos contínuos.
- `ProcessWindowFunction` adiciona contexto temporal útil para auditoria e debugging.

Prioridade: **Média**

## O que não priorizar agora
- `GlobalWindows` e `CountWindows` no core clínico de localização:
  - Semântica menos natural para o domínio temporal ADT.
  - Potencial de maior complexidade operacional sem ganho proporcional no objetivo principal (last location).

## Recomendação arquitetural
- Manter o fluxo atual (`KeyedProcessFunction`) para materialização de `last location` no MongoDB.
- Adicionar um **ramo paralelo de analytics** com janelas (`tumbling` + `sliding`) para métricas operacionais.
- Avaliar `session windows` para um segundo produto de dados (episódios por paciente), sem substituir de imediato o fluxo principal.

## Conclusão
Existe oportunidade clara para reutilizar features de `flink-time-based-and-window-operators` como complemento do pipeline atual, especialmente para métricas e visão de episódios. Para `last location` em MongoDB, a abordagem atual permanece adequada e estável.

---

## Prompt para futuras melhorias (com justificativa)

Use o prompt abaixo para conduzir próximas iterações de melhoria do módulo `patient-adt-processing-job-maven`:

```text
Contexto:
Temos um job Flink de ADT que já resolve `last location` por paciente e persiste no MongoDB.
Queremos evoluir o pipeline com operadores de janela sem quebrar a semântica atual.

Objetivo:
Propor e implementar melhorias incrementais, com foco em valor operacional e baixo risco.

Melhorias solicitadas (com “porquê” obrigatório em cada item):
1) Criar ramo paralelo com `TumblingEventTimeWindows` para métricas por intervalo fixo.
   - Porquê: fornece KPIs simples, estáveis e fáceis de consumir por dashboards.

2) Criar ramo paralelo com `SlidingEventTimeWindows` para métricas de tendência.
   - Porquê: oferece leitura contínua da operação (near-real-time) para deteção precoce de anomalias.

3) Avaliar `EventTimeSessionWindows` por paciente para gerar “episódios ADT”.
   - Porquê: melhora a visão clínica/operacional ao agrupar eventos relacionados por atividade/inatividade.

4) Usar `AggregateFunction` (incremental) e complementar com `ProcessWindowFunction` onde precisar de metadados de janela.
   - Porquê: otimiza memória e latência, mantendo contexto temporal para auditoria.

5) Definir política explícita para late events (allowed lateness + side output).
   - Porquê: evita perda silenciosa de dados e aumenta previsibilidade em cenários de atraso.

6) Garantir estratégia de rollout segura (feature flags/params por ramo de janela).
   - Porquê: permite ativação gradual em produção e rollback rápido sem impacto no fluxo principal.

Critérios de aceitação:
- Não alterar a semântica do fluxo principal de `last location`.
- Documentar trade-offs (custo, latência, precisão, complexidade).
- Incluir testes unitários/integrados para lógica de janela e late events.
- Entregar plano de observabilidade (métricas e logs) para os novos ramos.
```
