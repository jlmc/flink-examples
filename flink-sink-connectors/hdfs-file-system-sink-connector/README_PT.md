# Flink HDFS Sink Connector Example

Este exemplo demonstra como usar o `FileSink` do Apache Flink para escrever dados no **HDFS (Hadoop Distributed File System)**.

## Estrutura do Projeto

- `HdfsFileSystemSinkConnectorExample.java`: Define o Job Flink que gera mensagens e as escreve no HDFS.
- `docker-compose.yaml`: Provisiona um cluster HDFS (NameNode, DataNode) e um cluster Flink.
- `hadoop-data/`: Diretório local onde os dados do NameNode e DataNode são persistidos via volumes Docker.
- `hadoop.env`: Variáveis de ambiente para configurar o cluster Hadoop.

## Como Executar

### 1. Build do Projeto

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Iniciar o Ambiente

Isso iniciará o HDFS e o Flink:

```bash
docker-compose up -d
```

Certifique-se de que o NameNode está fora do modo de segurança (safe mode) antes de enviar o job, ou aguarde alguns segundos.

### 3. Implantar o Job

```bash
chmod +x upload-job.sh
./upload-job.sh
```

**Nota:** O Job gera dados indefinidamente para garantir que os arquivos sejam finalizados através dos checkpoints.

## Verificar os Resultados

Você pode verificar os arquivos gerados no HDFS usando o comando `hdfs dfs` dentro do container do NameNode:

```bash
docker exec -it namenode hdfs dfs -ls -R /flink/output/hdfs-sink
```

Para visualizar o conteúdo de um arquivo:

```bash
docker exec -it namenode hdfs dfs -cat /flink/output/hdfs-sink/<bucket>/<file>
```

Você também pode acessar a interface web do NameNode em [http://localhost:9870](http://localhost:9870) e navegar pelo sistema de arquivos (Utilities -> Browse the file system).

## Configuração do Hadoop no Flink

Para que o Flink consiga escrever no HDFS, ele precisa das bibliotecas do Hadoop no classpath. No `pom.xml` deste projeto, incluímos `hadoop-client` para facilitar o exemplo autocontido. Em ambientes de produção, geralmente o Flink utiliza a variável de ambiente `HADOOP_CONF_DIR` para localizar as configurações do cluster.
