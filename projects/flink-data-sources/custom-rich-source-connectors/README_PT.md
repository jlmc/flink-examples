## RichSourceFunction vs RichParallelSourceFunction (Flink)

Apesar do nome parecido, **o comportamento é muito diferente** e isso tem impacto directo na execução do job.

---

## 🔹 `RichSourceFunction<T>`

👉 **Fonte não paralela**

```java
public abstract class RichSourceFunction<T>
    implements SourceFunction<T>
```

### Características principais:

* Executa **sempre com paralelismo = 1**
* Mesmo que configures:

  ```java
  .setParallelism(4)
  ```

  👉 o Flink **ignora**
* Existe **apenas uma instância** da source
* Boa opção quando:

    * a fonte **não pode ser paralelizada**
    * existe um **recurso único** (ex.: ligação exclusiva)
    * queres algo simples ou didáctico

### Exemplo:

```java
env.addSource(new SimpleRichSourceFunction())
   .print();
```

---

## 🔹 `RichParallelSourceFunction<T>`

👉 **Fonte paralela**

```java
public abstract class RichParallelSourceFunction<T>
    implements ParallelSourceFunction<T>
```

### Características principais:

* Suporta **paralelismo > 1**
* Cada subtask é uma **instância independente**
* `.setParallelism(n)` **funciona**
* Tens acesso a:

  ```java
  getRuntimeContext().getIndexOfThisSubtask();
  getRuntimeContext().getNumberOfParallelSubtasks();
  ```
* Ideal quando:

    * os dados podem ser **divididos (shards/partições)**
    * queres **escala e throughput**
    * cada subtask pode trabalhar de forma autónoma

### Exemplo:

```java
env.addSource(new SimpleRichParallelSourceFunction())
   .setParallelism(4)
   .print();
```

---

## ⚠️ Armadilha comum

```java
env.addSource(new RichSourceFunction<>())
   .setParallelism(4); // ❌ não tem efeito
```

O job **corre na mesma com apenas 1 subtask**, sem qualquer aviso.

---

## 🧠 Diferença prática

| Aspecto          | RichSourceFunction | RichParallelSourceFunction |
|------------------|--------------------|----------------------------|
| Paralelismo      | Sempre 1           | Configurável               |
| Nº de instâncias | 1                  | N                          |
| Subtasks         | Não faz sentido    | Essencial                  |
| Escalabilidade   | ❌                  | ✅                          |

---

## 🧪 Quando usar cada um

### Usa `RichSourceFunction` quando:

* a fonte **não é paralelizável**
* só pode existir **um produtor**
* o estado é **global**
* simplicidade > performance

### Usa `RichParallelSourceFunction` quando:

* a leitura pode ser **dividida**
* precisas de **escalar**
* queres melhor desempenho
* cada subtask é independente

---

## 🔔 Nota importante (Flink moderno)

Desde o Flink **1.12+**, a API `SourceFunction` está **deprecated**.

O caminho recomendado é a **nova API**:

```java
Source<T>
```

(com `SourceReader`, `SplitEnumerator`, checkpoints mais robustos, etc.)

Mesmo assim:

* `RichSourceFunction` → excelente para aprender
* `RichParallelSourceFunction` → ainda muito comum em código legado

---

## Resumo rápido 🧩

* `RichSourceFunction` → **não paralela**
* `RichParallelSourceFunction` → **paralela**
* `.setParallelism()` **só funciona** na paralela
* Para produção moderna → `Source<T>`
