# Operadores de Janela e Baseados em Tempo no Flink

Este módulo explora a **API de Janelas (Window API) do Flink** e vários operadores baseados em tempo. Fornece exemplos e explicações sobre como processar fluxos de dados infinitos, dividindo-os em "baldes" (buckets) ou janelas finitas.

## Estrutura do Projeto

Este projeto está dividido em vários sub-módulos, cada um focado num tipo específico de Atribuidor de Janela (Window Assigner) ou Função de Janela (Window Function):

*   **[tumbling-windows](./tumbling-windows)**: Janelas de tamanho fixo que não se sobrepõem.
*   **[sliding-windows](./sliding-windows)**: Janelas de tamanho fixo que "deslizam" com base num intervalo definido, permitindo sobreposições.
*   **[session-windows](./session-windows)**: Janelas que fecham após um período de inatividade (gap).
*   **[global-windows](./global-windows)**: Uma única janela gigante por chave, exigindo um Trigger personalizado para disparar.
*   **[count-windows](./count-windows)**: Janelas agrupadas por um número fixo de eventos em vez de tempo.
*   **[window-functions](./window-functions)**: Exemplos de `ReduceFunction`, `AggregateFunction` e `ProcessWindowFunction`.

---

## Conceitos Fundamentais

### O que é o Windowing?
Windowing (Janelamento) é uma técnica utilizada no processamento de fluxos para agrupar dados em pedaços finitos com base no tempo ou noutros critérios, permitindo a aplicação de operações sobre esses pedaços. No Flink, as janelas podem ser:
*   **Baseadas em tempo** (`Time Window`)
*   **Baseadas em dados** (`Count Window`)

### Janelas com Chave (Keyed) vs. Sem Chave (Non-Keyed)

Antes de realizar uma operação de janela, deve especificar se o fluxo deve ter uma chave (keyed):

*   **Janelas com Chave** (`.keyBy(...).window(...)`):
    *   O fluxo é particionado por uma chave.
    *   As computações são executadas em paralelo por múltiplas tarefas.
    *   Cada fluxo lógico com chave é processado de forma independente.
*   **Janelas sem Chave** (`.windowAll(...)`):
    *   O fluxo não é particionado.
    *   Toda a lógica de janelas é executada por uma **única tarefa** (paralelismo de 1).
    *   **Aviso**: Isto pode causar gargalos (bottlenecks) de desempenho para grandes volumes de dados.

---

## Atribuidores de Janela (Window Assigners)

1.  **Janelas Fixas (Tumbling Windows)**: Tamanho fixo, sem sobreposição. Ideais para relatórios horários ou diários.
2.  **Janelas Deslizantes (Sliding Windows)**: Tamanho fixo, mas deslizantes. Úteis para análise de tendências em tempo real (ex: "últimos 5 minutos de dados, atualizados a cada 1 minuto").
3.  **Janelas de Sessão (Session Windows)**: Baseadas em intervalos de inatividade. Perfeitas para analisar sessões de comportamento do utilizador.
4.  **Janelas Globais (Global Windows)**: Agrupa tudo numa única janela. Requer um `Trigger` personalizado para produzir resultados.
5.  **Janelas de Contagem (Count Windows)**: Disparadas após um número específico de elementos (ex: a cada 100 eventos).

---

## Funções de Processamento

Uma vez que os dados estejam agrupados, devem ser processados utilizando uma destas funções:

*   **Agregações Incrementais** (`ReduceFunction`, `AggregateFunction`): Mais eficientes, pois o Flink apenas armazena o resultado parcial (ex: uma soma acumulada).
*   **Funções de Janela Completa** (`ProcessWindowFunction`): O Flink armazena todos os elementos da janela em memória e entrega-os de uma só vez. Mais pesado, mas permite aceder a metadados da janela (como timestamps).

---

## Lógica de Tempo e Atrasos

Para um processamento de fluxo robusto, o Flink fornece:

*   **Watermarks**: Sinalizam até que ponto o "tempo do evento" progrediu.
*   **Atraso Permitido (Allowed Lateness)**: Permite que dados que cheguem atrasados sejam incluídos numa janela antes de esta ser destruída.
*   **Saídas Laterais (Side Outputs)**: Um mecanismo para capturar dados extremamente atrasados que, de outra forma, seriam perdidos.

---

## Como Executar

Cada sub-módulo contém os seus próprios exemplos. Consulte o README específico em cada diretório para detalhes de execução.

---

## Documentação
Para mais detalhes, consulte:
- [Documentação Oficial de Windowing do Apache Flink (Estável)](https://nightlies.apache.org/flink/flink-docs-stable/docs/dev/datastream/operators/windows/)
- [Documentação Oficial de Windowing do Apache Flink (v1.20.x)](https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/dev/datastream/operators/windows/)
