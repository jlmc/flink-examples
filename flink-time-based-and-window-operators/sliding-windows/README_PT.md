# Exemplo de Janelas Deslizantes (Sliding Windows) - Segurança de Acessos

Este exemplo demonstra como usar **Janelas Deslizantes (Sliding Windows)** no Flink para resolver um problema do mundo real: **Segurança de Acessos e Deteção de Bots**.

## O Problema: Deteção de Brute Force
Se um utilizador errar a password 5 vezes num dia, pode ser apenas alguém esquecido. Mas se alguém errar a password 5 vezes em apenas **30 segundos**, é muito provável que seja um bot a tentar adivinhar a senha (ataque de força bruta).

Com **Sliding Window (Janela Deslizante)**, definimos uma janela de 30 segundos que desliza a cada 5 segundos. Esta janela "persegue" o rasto do atacante. Assim que 5 tentativas falhadas caiam dentro de qualquer intervalo de 30 segundos, o alerta dispara.

---

## Diferenças entre Sliding e Tumbling Windows

Ambos os tipos de janelas são de tamanho fixo, mas diferem no comportamento de "movimento" e na sobreposição de dados:

| Característica | Janela Fixa (Tumbling) | Janela Deslizante (Sliding) |
| :--- | :--- | :--- |
| **Definição** | Apenas o tamanho (ex: 10s). | Tamanho e Intervalo de Deslize (ex: 10s a cada 5s). |
| **Sobreposição** | **Não há sobreposição.** Cada evento pertence a exatamente uma janela. | **Pode haver sobreposição.** Se o deslize for menor que o tamanho, um evento pode pertencer a múltiplas janelas. |
| **Espaços (Gaps)** | Não há espaços. O fim de uma janela é o início da próxima. | Se o deslize for maior que o tamanho, alguns dados podem ser ignorados (não recomendado para a maioria dos casos). |
| **Uso Ideal** | Relatórios discretos (ex: total de vendas por hora). | Médias móveis e detecção de tendências (ex: temperatura média nos últimos 5 min, atualizada a cada minuto). |

**Exemplo Visual:**
- **Tumbling (10s):** [0-10], [10-20], [20-30]
- **Sliding (30s, deslize 5s):** [0-30], [5-35], [10-40], [15-45]

Neste exemplo, cada tentativa de login falhada será processada em **seis janelas diferentes** (30 / 5 = 6), permitindo que detetemos o padrão de 5 falhas no momento em que ocorrerem, independentemente de quando o ataque começou.

| Relação | Nome | Comportamento |
| :--- | :--- | :--- |
| **Slide = Size** | Tumbling Window | Janelas fixas, sem sobreposição. Cada evento é processado 1 vez. |
| **Slide < Size** | Sliding Window | Sobreposição. Cada evento é processado `Size / Slide` vezes. (Ex: 30s/5s = 6 vezes). |
| **Slide > Size** | Sampling Window | Gaps. Alguns dados nunca serão processados (não recomendado para segurança). |

---

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
