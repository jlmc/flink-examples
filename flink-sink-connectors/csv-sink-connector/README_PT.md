# Flink CSV Sink Connector Example

Este exemplo demonstra como usar o `FileSink` do Apache Flink para escrever dados no formato CSV em um sistema de arquivos local.

## Estrutura do Projeto

- `CsvSinkConnectorExample.java`: Define o Job Flink que gera dados fictícios de pacientes e os escreve em arquivos CSV.
- `PatientCsvEncoder`: Uma implementação personalizada de `Encoder` para formatar os objetos `Patient` como linhas CSV.
- `docker-compose.yaml`: Provisiona um cluster Flink local (JobManager e TaskManager).

## Como Executar

### 1. Build do Projeto

Use o script fornecido para realizar o build do JAR sombreado (shaded jar) usando uma imagem Docker do Maven:

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Iniciar o Ambiente

Suba o cluster Flink:

```bash
docker-compose up -d
```

### 3. Implantar o Job

Envie o JAR para o cluster Flink:

```bash
chmod +x upload-job.sh
./upload-job.sh
```

**Nota:** O Job está configurado para gerar dados indefinidamente. Para interrompê-lo, você pode cancelar o Job via Flink UI (http://localhost:8081) ou parar o container.

## Verificar os Resultados

O Job está configurado para escrever os arquivos em `/tmp/flink-output/csv-sink` dentro do container do TaskManager. Este diretório está mapeado para o diretório local `./flink-output/csv-sink` no seu host através de um volume do Docker.

Para listar os arquivos gerados no host:

```bash
ls -R ./flink-output/csv-sink
```

Ou diretamente no container:

```bash
docker-compose exec taskmanager ls -R /tmp/flink-output/csv-sink
```

Para ler o conteúdo de um dos arquivos gerados:

```bash
docker-compose exec taskmanager cat /tmp/flink-output/csv-sink/<bucket-path>/<file-name>
```

## Semântica de Entrega e Checkpointing

Este exemplo habilita o **Checkpointing** (`EXACTLY_ONCE`) a cada 5 segundos. O `FileSink` depende dos checkpoints para finalizar os arquivos.

### Quando o arquivo é considerado completo ("Finished")?

No Apache Flink, os arquivos gerados pelo `FileSink` passam por três estados:

1.  **In-progress**: O arquivo está sendo escrito no momento. Geralmente possui um prefixo ou sufixo oculto (ex: `.part-uuid-0.inprogress`).
2.  **Pending**: O arquivo foi fechado (atingiu o limite de tamanho, tempo ou inatividade), mas ainda não foi confirmado por um checkpoint.
3.  **Finished**: O checkpoint foi concluído com sucesso após o arquivo entrar em estado `pending`. O Flink renomeia o arquivo para o seu nome final (ex: `part-uuid-0`).

**Portanto, um arquivo só é considerado completo e seguro para leitura externa quando:**
- A política de rolagem (`RollingPolicy`) decide fechar o arquivo atual (neste exemplo, a cada 10 segundos de duração, 5 segundos de inatividade ou 10KB de dados).
- Um checkpoint do Flink é finalizado com sucesso após esse fechamento.

Com a configuração atual (checkpoint de 5s e rolagem agressiva), você verá novos arquivos finalizados surgindo no diretório a cada poucos segundos.
