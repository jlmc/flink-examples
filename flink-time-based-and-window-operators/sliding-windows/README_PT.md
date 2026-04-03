# Exemplo de Janelas Deslizantes (Sliding Windows)

Este exemplo demonstra como usar **Janelas Deslizantes (Sliding Windows)** no Flink para processar dados de sensores do Kafka.

## Cenário
Calculamos a temperatura média dos sensores nos últimos 20 segundos, atualizada a cada 10 segundos.

## Como Executar

1.  **Iniciar a Infraestrutura**:
    ```bash
    docker-compose up -d
    ```

2.  **Construir o Projeto**:
    Pode construir o projeto diretamente se tiver o Maven e JDK 17+ instalados:
    ```bash
    mvn clean package
    ```
    Ou use o script fornecido para construir usando um contentor Docker com JDK 11 (garante compatibilidade com a imagem do Flink):
    ```bash
    chmod +x build-jdk11.sh
    ./build-jdk11.sh
    ```

3.  **Submeter o Job**:
    ```bash
    ./upload-job.sh
    ```

4.  **Enviar Eventos de Exemplo**:
    ```bash
    ./submit-events.sh
    ```

5.  **Verificar a Saída**:
    Consulte os logs do TaskManager para ver os resultados das janelas deslizantes.
