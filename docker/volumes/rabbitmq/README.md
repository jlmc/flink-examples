# RabbitMQ Messaging Scenarios (RabbitMQ 4.x)

This document explains the messaging scenarios defined in `rabbitmq-definitions.json` and how messages flow between **producers**, **exchanges**, **queues**, and **consumers**.

The examples cover most real‑world RabbitMQ patterns used in production systems.

---

## 1. Topic Exchange – Event Routing (Audit Logs)

### Use case

Route events based on hierarchical routing keys (e.g. `auditor.audits`, `user.created`).

### Components

* **Exchange**: `tdx.events` (type: `topic`)
* **Queue**: `audit.logging`
* **Binding key**: `auditor.audits`

### Flow diagram

```
Producer
   |
   |  routing_key = auditor.audits
   v
+--------------------+
| tdx.events    |  (topic)
+--------------------+
           |
           | auditor.audits
           v
     +----------------+
     | audit.logging  |
     +----------------+
              |
              v
          Audit Service
```

### When to use

* Event-driven architectures
* Audit trails
* Domain events

---

## 2. Direct Exchange – Work Queue (Load Balancing)

### Use case

Distribute jobs among multiple workers (round‑robin).

### Components

* **Exchange**: `jobs.exchange` (type: `direct`)
* **Queue**: `jobs.work` (type: `quorum`)
* **Routing key**: `jobs.process`

### Flow diagram

```
Producer
   |
   | routing_key = jobs.process
   v
+-----------------+
| jobs.exchange   | (direct)
+-----------------+
          |
          v
     +------------+
     | jobs.work  |
     +------------+
       /     |     \
      v      v      v
   Worker1 Worker2 Worker3
```

### When to use

* Background jobs
* Task processing
* Horizontal scaling

---

## 3. Retry with Delay (TTL + Dead Letter Exchange)

### Use case

Retry failed messages after a delay (e.g. 5 seconds).

### Components

* **Retry queue**: `jobs.retry.5s`
* **TTL**: 5000 ms
* **DLX**: `jobs.exchange`

### Flow diagram

```
Consumer (fails)
      |
      | reject / nack
      v
+-------------------+
| jobs.retry.5s     |
| (TTL = 5s)        |
+-------------------+
        |
        | TTL expired
        v
+-------------------+
| jobs.exchange     |
+-------------------+
        |
        v
+-------------------+
| jobs.work         |
+-------------------+
```

### When to use

* Temporary failures
* External API retries
* Backoff strategies

---

## 4. Dead Letter Queue (DLQ)

### Use case

Capture permanently failed messages for inspection.

### Components

* **Dead letter exchange**: `jobs.dlx`
* **Queue**: `jobs.dlq`

### Flow diagram

```
Failed Message
      |
      v
+-------------+
| jobs.dlx    |
+-------------+
      |
      v
+-------------+
| jobs.dlq    |
+-------------+
      |
      v
 Ops / Support Review
```

### When to use

* Error analysis
* Manual reprocessing
* Compliance requirements

---

## 5. Fanout Exchange – Broadcast Notifications

### Use case

Send the same message to multiple consumers.

### Components

* **Exchange**: `notifications.exchange` (type: `fanout`)
* **Queues**: `notifications.email`, `notifications.sms`

### Flow diagram

```
Producer
   |
   v
+------------------------+
| notifications.exchange | (fanout)
+------------------------+
      |            |
      v            v
+-----------+  +-----------+
| email Q   |  | sms Q     |
+-----------+  +-----------+
      |            |
      v            v
 Email Svc     SMS Svc
```

### When to use

* Notifications
* Cache invalidation
* Real‑time updates

---

## 6. Headers Exchange – Attribute-Based Routing

### Use case

Route messages based on headers instead of routing keys.

### Components

* **Exchange**: `headers.exchange`
* **Queue**: `audit.logging`
* **Headers**:

    * `service = billing`
    * `severity = high`

### Flow diagram

```
Producer
Headers:
 service=billing
 severity=high
      |
      v
+-------------------+
| headers.exchange  |
+-------------------+
          |
          v
+-------------------+
| audit.logging     |
+-------------------+
```

### When to use

* Metadata-based routing
* Multi-tenant systems
* Advanced filtering

---

## 7. Queue Types Summary (RabbitMQ 4.x)

| Queue Type | Use                           |
| ---------- | ----------------------------- |
| quorum     | Recommended default, HA, safe |
| classic    | Legacy compatibility          |

> ⚠️ Mirrored classic queues are **removed** in RabbitMQ 4.x.

---

## 8. Recommended Architecture

```
            Producers
                 |
                 v
           +--------------+
           | Exchanges    |
           +--------------+
          /       |        \
         v        v         v
     Work Q    Retry Q    Fanout Qs
        |          |           |
        v          v           v
     Workers    DLX/DLQ   Notification Svcs
```

---

## 9. Key Takeaways

* Use **topic exchanges** for events
* Use **direct exchanges** for jobs
* Use **fanout exchanges** for broadcast
* Always configure **DLQs**
* Prefer **quorum queues** in RabbitMQ 4.x

---

If you need:

* Architecture diagrams (PNG / SVG)
* Per-service examples
* Kubernetes / Docker deployment mapping

Just say the word.
