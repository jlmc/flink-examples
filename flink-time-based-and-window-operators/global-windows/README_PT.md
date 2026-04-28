# Exemplo de Janelas Globais (Global Windows)

Este exemplo demonstra como usar **Janelas Globais (Global Windows)** com um `Trigger` personalizado.

## Cenário
Os elementos são agrupados numa única janela global. O processamento é acionado após 5 elementos por chave.

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
    chmod +x build-jdk17.sh
    ./build-jdk17.sh
    ```

3.  **Submeter o Job**:
    ```bash
    ./upload-job.sh
    ```

4.  **Enviar Eventos de Exemplo**:
    ```bash
    ./submit-events.sh
    ```
