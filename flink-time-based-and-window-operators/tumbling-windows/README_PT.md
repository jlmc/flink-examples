# Exemplo de Janelas Fixas (Tumbling Windows)

Este exemplo demonstra como usar **Janelas Fixas (Tumbling Windows)** no Flink para processar dados de sensores em tempo real a partir do Kafka.

## Cenário
Estamos a monitorizar sensores de temperatura. Cada sensor envia a sua leitura como um evento JSON. Queremos calcular a temperatura média por sensor a cada 10 segundos.

## Como Executar

1.  **Iniciar a Infraestrutura**:
    ```bash
    docker-compose up -d
    ```
    Isto inicia o Flink (JobManager e TaskManager), Kafka e um Kafka-UI (em [http://localhost:8085](http://localhost:8085)).

2.  **Construir o Projeto**:
    Pode construir o projeto diretamente se tiver o Maven e JDK 17+ instalados:
    ```bash
    mvn clean package
    ```
    Ou use o script fornecido para construir usando um contentor Docker com JDK 11 (garante compatibilidade com a imagem do Flink):
    ```bash
    chmod +x build-jdk17.sh
    ./build-jdk17.sh
    ```

3.  **Submeter o Job Flink**:
    ```bash
    ./upload-job.sh
    ```

4.  **Enviar Eventos de Exemplo**:
    ```bash
    ./submit-events.sh
    ```

5.  **Verificar a Saída**:
    Monitorize os logs do TaskManager ou a Web UI do Flink (em [http://localhost:8081](http://localhost:8081)) para ver as médias computadas.

## Formato do Evento JSON
```json
{
  "id": "sensor-1",
  "timestamp": "2026-04-03T20:12:00.000Z",
  "temperature": 22.5
}
```
