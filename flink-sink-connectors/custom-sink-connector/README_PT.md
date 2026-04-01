# Exemplo de Conector de Sink HTTP Personalizado no Flink

Este exemplo demonstra como implementar um **Sink personalizado do mundo real** no Apache Flink usando a API moderna `Sink` (SinkV2).
Ele implementa um `HttpSink` que envia dados JSON para uma API REST.

## Estrutura do Projeto

- `CustomSinkConnectorExample.java`: Define o Job Flink e a implementação do `HttpSink`.
- `HttpSink`: Implementação da interface `Sink<Patient>` que cria um escritor.
- `HttpSinkWriter`: Implementação da interface `SinkWriter<Patient>` que envia cada elemento como uma requisição POST para um endpoint REST.
- `Patient`: Um POJO simples representando os dados sendo processados.
- `docker-compose.yaml`: Provisiona um cluster Flink e um **MockServer** para atuar como o destino HTTP.

## Como Executar

### 1. Build do Projeto

```bash
chmod +x build-jdk11.sh
./build-jdk11.sh
```

### 2. Iniciar o Ambiente

```bash
docker-compose up -d
```

### 3. Configuração do Mock Server

O MockServer é configurado automaticamente via `mockserver-initializer.json` para responder com `200 OK` às requisições POST em `/api/patients`.

- **UI do MockServer (Dashboard)**: Acessível em http://localhost:1080/mockserver/dashboard
- **Logs do MockServer**: 
  ```bash
  docker-compose logs -f mockserver
  ```

### 4. Implantar o Job

```bash
chmod +x upload-job.sh
./upload-job.sh
```

**Nota:** O Job gera mensagens indefinidamente a uma taxa de 2 por segundo.

## Verificar os Resultados

### Verificar Logs do TaskManager
Você pode ver o Sink enviando mensagens inspecionando os logs do TaskManager:

```bash
docker-compose logs -f taskmanager
```

Você deverá ver logs como:
`INFO  ... CustomSinkConnectorExample$HttpSinkWriter  - Sending patient to HTTP Sink: Patient{id=0, name='Patient 0'}`

### Verificar MockServer (UI e Logs)
Você pode verificar as requisições POST recebidas:
1. Abrindo a **UI do MockServer** em http://localhost:1080/mockserver/dashboard
2. Verificando os **logs do MockServer**:
   ```bash
   docker-compose logs -f mockserver
   ```

Você deverá ver entradas indicando que requisições POST foram recebidas em `/api/patients`.

## Notas sobre a Implementação

- **API SinkV2**: A maneira recomendada de criar conectores de saída no Flink desde a versão 1.14+.
- **HTTP Client**: Usa o `HttpClient` nativo do Java (introduzido no Java 11).
- **Serialização JSON**: Usa **Flink JSON** (`JsonSerializationSchema`) para converter POJOs em strings JSON.
- **Tratamento de Erros**: O `HttpSinkWriter` verifica o código de status HTTP e lança uma `IOException` se a requisição falhar (ex: erros 4xx ou 5xx), o que aciona os mecanismos de tolerância a falhas do Flink (reinicialização baseada em checkpoints).
- **Checkpointing**: Habilitado para garantir que, se o job falhar, ele possa retomar do último estado bem-sucedido.
