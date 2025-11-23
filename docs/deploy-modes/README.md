# Flink Deployment Modes

Apache Flink supports multiple deployment modes suited for different levels of isolation, scalability, and operational needs.
This document explains each mode using simple diagrams.

---

## 🟦 Session Mode

A long-running cluster where multiple jobs **share the same JobManager and TaskManagers**.

```
             +----------------------+
             |   Session Cluster    |
             |  (JobManager + TMs)  |
             +----------+-----------+
                        |
        -------------------------------------
        |                 |                |
   +----v----+       +----v----+      +----v----+
   |  Job 1  |       |  Job 2  |      |  Job 3  |
   +---------+       +---------+      +---------+
(shared cluster & resources)
```

**Pros**

* Very fast job submission
* Efficient for many small jobs

**Cons**

* Low isolation
* Jobs may affect each other

---

## 🟩 Application Mode

Each application submission creates its own **dedicated, isolated cluster**.

```
          Application Submission
                    |
        +-----------v----------------+
        |  Flink Application Cluster |
        | (JobManager + TaskManagers)|
        +-----------+----------------+
                    |
                +---v---+
                |  Job  |
                +-------+
(one application = one cluster)
```

**Pros**

* Strong isolation
* Great for production
* Clean microservice-like deployment

**Cons**

* More clusters to manage
* Slightly slower startup

---

## 🟧 Per-Job Mode

Each job runs in a **short-lived, job-specific cluster**.
The cluster shuts down automatically after the job finishes.

```
         Client submits Job
                    |
         +----------v----------+
         |    Per-Job Cluster  |
         | (Job-specific only) |
         +----------+----------+
                    |
                +---v---+
                |  Job  |
                +-------+
(cluster ends once the job completes)
```

**Pros**

* Maximum isolation
* Ideal for batch jobs

**Cons**

* More overhead than Session Mode

---

## 🟨 Native Deployments (Kubernetes, YARN, Standalone)

### Kubernetes Native (example with Application Mode)

```
               +----------------+
               |   Kubernetes   |
               +--------+-------+
                        |
         ---------------------------------
         |               |               |
 +-------v-------+ +-----v------+ +------v-------+
 | Flink Cluster | | Flink Cl.  | | Flink Cl.    |
 | (App 1)       | | (App 2)    | | (App 3)      |
 +---------------+ +------------+ +--------------+
```

Flink can run natively using:

* **Kubernetes (Operator or Helm)**
* **YARN**
* **Standalone clusters**

---

## 🧱 Comparison Table

| Mode              | Isolation | Startup Speed | Lifecycle           | Best For                 |
|-------------------|-----------|---------------|---------------------|--------------------------|
| Session           | Low       | Very Fast     | Shared cluster      | Many small jobs          |
| Application       | High      | Medium        | One cluster per app | Production streaming     |
| Per-Job           | High      | Medium–High   | One cluster per job | Batch / isolated jobs    |
| Kubernetes Native | High      | Variable      | Per app/job         | Cloud-native deployments |

