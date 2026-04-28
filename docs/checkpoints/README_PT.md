# Checkpoints no Apache Flink

Os checkpoints são o mecanismo fundamental do Apache Flink para garantir a **tolerância a falhas** e a **consistência de estado**. Eles permitem que o Flink recupere o estado de um Job após uma falha, garantindo que o processamento continue de onde parou.

---

## 1. O que são Checkpoints?

Um checkpoint é um instantâneo (snapshot) consistente e distribuído do estado de todos os operadores em um Job Flink em um determinado momento. 

*   **Como funcionam:** O Flink insere "barreiras" (Checkpoint Barriers) no fluxo de dados. Quando um operador recebe uma barreira, ele grava seu estado atual no armazenamento persistente (geralmente um State Backend como o RocksDB ou o FileSystem).
*   **Algoritmo Chandy-Lamport:** O Flink utiliza uma variante deste algoritmo para realizar checkpoints sem interromper o processamento (checkpoints assíncronos).

---

## 2. EXACTLY_ONCE vs. AT_LEAST_ONCE

A escolha entre estes dois modos resume-se ao equilíbrio entre **integridade absoluta dos dados** e **velocidade de resposta (latência)**.

### 2.1. Justificação para `AT_LEAST_ONCE`
**"Prioridade: Velocidade e Simplicidade"**

Escolha esta opção se o seu sistema precisa de reagir o mais depressa possível e se o sistema que consome os resultados (downstream) consegue lidar com repetições ocasionais.

*   **Baixa Latência:** Os dados são enviados para o Sink assim que processados, sem esperar pelos ciclos de checkpoint.
*   **Performance:** Menor overhead no cluster Flink e no Broker externo (ex: Kafka), já que não há gestão de transações complexas.
*   **Resiliência a falhas de Checkpoint:** Mesmo que o sistema de ficheiros dos checkpoints esteja lento, os dados continuam a fluir.
*   **Cenário Ideal:** Dashboards em tempo real, sistemas de segurança/deteção de bots onde "bloquear duas vezes" o mesmo IP não é um problema grave.

### 2.2. Justificação para `EXACTLY_ONCE`
**"Prioridade: Precisão e Consistência"**

Escolha esta opção se cada evento gera uma ação crítica que não pode, de forma alguma, ser duplicada.

*   **Integridade Total:** Garante que, mesmo que o Job falhe, o resultado não será enviado/processado duas vezes após a recuperação.
*   **Coordenação Transacional:** O Flink usa o protocolo *Two-Phase Commit* (2PC) para garantir que o estado interno do Job e o estado do sistema externo (ex: Kafka) estão em perfeita sintonia.
*   **Custo de Latência:** O "preço" é que os resultados podem ficar retidos no Sink até que o próximo checkpoint termine com sucesso (alinhamento de barreiras).
*   **Cenário Ideal:** Sistemas financeiros, faturação ou pipelines onde o processamento posterior não é idempotente.

---

## 3. Tabela Comparativa de Decisão

| Critério | `AT_LEAST_ONCE` | `EXACTLY_ONCE` |
| :--- | :--- | :--- |
| **Latência** | Mínima (Imediata) | Alta (Depende do Checkpoint Interval) |
| **Duplicados** | Possíveis em caso de falha | Impossíveis |
| **Configuração** | Simples | Exigente (Requer Kafka Transactions, etc.) |
| **Impacto no Kafka** | Ligeiro | Elevado (Cria muitos marcadores de transação) |

---

## 4. Veredito e Recomendações

### Para Deteção de Bots / Segurança
A latência é geralmente o fator mais crítico. Se um bot está a atacar, queres o alerta em 1-5 segundos, não em 30-60 segundos. Por isso, **`AT_LEAST_ONCE` com um intervalo de checkpoint curto** costuma ser a solução pragática mais eficaz.

### Para Sistemas Financeiros
A precisão é mandatória. Use **`EXACTLY_ONCE`**, mas certifique-se de configurar o `checkpoint.interval` para um valor que equilibre a latência aceitável com o overhead do cluster (ex: 5-10 segundos).

---

## 5. Configuração Exemplo (Java - Flink 1.20)

No Flink 1.20, as configurações foram reorganizadas com novos prefixos (`execution.checkpointing.*`, `state.*`, etc.). Embora o API programático ainda suporte os métodos antigos, a recomendação é seguir a nova estrutura de configuração.

```java
StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

// Ativar checkpoints a cada 10 segundos
env.enableCheckpointing(10000);

// Configurar o modo
env.getCheckpointConfig().setCheckpointingMode(CheckpointingMode.EXACTLY_ONCE);

// --- Novidades e Configurações Recomendadas ---

// 1. Unaligned Checkpoints (Essencial para lidar com Backpressure)
// Permite que as barreiras de checkpoint ultrapassem os buffers de dados.
env.getCheckpointConfig().enableUnalignedCheckpoints();

// 2. Alinhamento de Barreiras com Timeout (Misto)
// Tenta alinhado, mas muda para desalinhado se demorar mais de 5s
env.getCheckpointConfig().setAlignedCheckpointTimeout(Duration.ofSeconds(5));

// 3. Retenção de Checkpoints (Externalized Checkpoints)
// Mantém o checkpoint mesmo após o cancelamento do Job
env.getCheckpointConfig().setExternalizedCheckpointRetention(
    ExternalizedCheckpointRetention.RETAIN_ON_CANCELLATION);

// 4. Tempo mínimo entre checkpoints (Evita "checkpoint flooding")
env.getCheckpointConfig().setMinPauseBetweenCheckpoints(5000);

// 5. Número máximo de checkpoints simultâneos
env.getCheckpointConfig().setMaxConcurrentCheckpoints(1);
```

---

## 6. State Backends (Onde o estado é guardado)

O Flink oferece duas implementações principais de State Backend:

### 6.1. HashMapStateBackend
*   **Onde guarda:** Na memória (Heap) da JVM.
*   **Performance:** Extremamente rápido (acesso direto a objetos Java).
*   **Limitação:** Limitado pelo tamanho da RAM disponível. Snapshots podem ser pesados para a Garbage Collection.
*   **Ideal para:** Estados pequenos, janelas simples, baixa latência.

**Como implementar (Java):**
```java
import org.apache.flink.runtime.state.hashmap.HashMapStateBackend;

// No StreamExecutionEnvironment:
env.setStateBackend(new HashMapStateBackend());
```

### 6.2. EmbeddedRocksDBStateBackend
*   **Onde guarda:** No RocksDB (base de dados chave-valor embarcada) que escreve no disco local.
*   **Performance:** Ligeiramente mais lento que o Heap (precisa de serialização/deserialização), mas muito eficiente.
*   **Escalabilidade:** Praticamente ilimitado (limitado apenas pelo disco). Suporta estados de Terabytes.
*   **Checkpoints Incrementais:** Fundamental para grandes estados; guarda apenas o que mudou desde o último checkpoint.
*   **Ideal para:** Estados grandes, janelas longas, alta disponibilidade.

**Como implementar (Java):**
```java
import org.apache.flink.state.rocksdb.EmbeddedRocksDBStateBackend;

// No StreamExecutionEnvironment (Checkpoints Incrementais ativados por padrão):
env.setStateBackend(new EmbeddedRocksDBStateBackend(true));
```

### 6.3. Persistência em S3 (Storage)

Independentemente do State Backend escolhido (HashMap ou RocksDB), os checkpoints devem ser persistidos num sistema de ficheiros distribuído para garantir a recuperação em caso de falha do cluster.

**Dependências Necessárias (Maven):**
Para Flink 1.20, recomenda-se o uso do plugin `flink-s3-fs-presto` para checkpointing.

```xml
<dependency>
    <groupId>org.apache.flink</groupId>
    <artifactId>flink-s3-fs-presto</artifactId>
    <version>1.20.0</version>
</dependency>
```

**Configuração Programática (Java):**
```java
import org.apache.flink.runtime.state.storage.FileSystemCheckpointStorage;
import org.apache.flink.configuration.Configuration;

// 1. Definir o local de armazenamento (S3)
env.getCheckpointConfig().setCheckpointStorage("s3://meu-bucket/flink/checkpoints");

// 2. Se estiver a usar MinIO ou um endpoint S3 customizado, configure via Configuration
Configuration config = new Configuration();
config.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
config.set(CheckpointingOptions.CHECKPOINTS_DIRECTORY, "s3://flink/checkpoints");
config.set(CheckpointingOptions.SAVEPOINTS_DIRECTORY, "s3://flink/savepoints");

// Opções específicas para S3 (MinIO exemplo)
config.setString("s3.endpoint", "http://localhost:9000");
config.setString("s3.access-key", "minioadmin");
config.setString("s3.secret-key", "minioadmin");
config.setBoolean("s3.path.style.access", true); // Necessário para MinIO

StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
```

**Dica:** Em produção, prefira colocar o JAR do `flink-s3-fs-presto` (ou `hadoop`) na pasta `/plugins/s3-fs-presto/` da sua distribuição Flink em vez de incluí-lo no Fat JAR da aplicação.

---

## 7. Novidades do Flink 1.20 (Destaques)

### 7.1. Unified File Merging (MVP)
No Flink 1.20, foi introduzido um mecanismo para fundir pequenos ficheiros de checkpoint em ficheiros maiores. Isto reduz a pressão sobre o sistema de ficheiros (ex: S3, HDFS) ao criar menos ficheiros.

**Como configurar:**
Pode ser configurado no `flink-conf.yaml` ou programaticamente no código do Job:

```java
import org.apache.flink.configuration.Configuration;
import org.apache.flink.configuration.CheckpointingOptions;

Configuration config = new Configuration();
// Ativar Unified File Merging
config.set(CheckpointingOptions.FILE_MERGING_ENABLED, true);

StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment(config);
```

### 7.2. Reorganização das Configurações
As opções de configuração foram categorizadas para maior clareza. Aqui estão as classes de opções mais comuns para usar com o novo sistema de configuração do Flink 1.20:

*   `CheckpointingOptions`: Opções para `execution.checkpointing.*`
*   `StateBackendOptions`: Opções para `state.backend.*`
*   `StateRecoveryOptions`: Opções para `execution.state-recovery.*`

**Exemplo de uso programático:**
```java
config.set(CheckpointingOptions.CHECKPOINTING_MODE, CheckpointingMode.EXACTLY_ONCE);
config.set(CheckpointingOptions.CHECKPOINTING_INTERVAL, Duration.ofSeconds(10));
config.set(StateBackendOptions.STATE_BACKEND, "rocksdb");
```

---

## 8. Diferença entre Checkpoints e Savepoints

| Característica | Checkpoint | Savepoint |
| :--- | :--- | :--- |
| **Propósito** | Recuperação automática de falhas. | Manutenção, atualizações de código, migrações. |
| **Trigger** | Gerido automaticamente pelo Flink. | Disparado manualmente pelo utilizador. |
| **Lifecycle** | Removido quando o Job é cancelado (por padrão). | Persistido até ser removido manualmente. |
