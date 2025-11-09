## 🐳 Apache Flink (Java 21) – Docker Setup

This project provides a simple, minimal setup for running an **Apache Flink** cluster (JobManager + TaskManager) using a **Java 21 base image** — no Scala required.

---

### 📁 **Project Structure**

```
.
├── Dockerfile
├── start-flink.sh
├── stop-flink.sh
└── README.md
```

---

## 🚀 **1. Build the Docker Image**

Make sure you have **Docker** installed and running.

Build the image:

```bash
docker build -t myflink:java21 .
```

This creates a custom image based on `eclipse-temurin:21-jdk-jammy` and downloads Apache Flink (v1.20.0 by default).

---

## 🧠 **2. Start the Flink Cluster**

Use the provided startup script:

```bash
chmod +x start-flink.sh
./start-flink.sh
```

What it does:

* Builds the image (if not already built).
* Creates a Docker network named `flink-net`.
* Starts two containers:

    * `jobmanager` (Flink master)
    * `taskmanager` (Flink worker)

---

## 🔍 **3. Verify That It’s Running**

### ✅ Check container status

```bash
docker ps
```

You should see something like:

```
CONTAINER ID   NAME          STATUS          PORTS
abcd1234efgh   jobmanager    Up 15 seconds   0.0.0.0:8081->8081/tcp
ijkl5678mnop   taskmanager   Up 10 seconds
```

### 🌐 Check the Flink Web UI

Open your browser to:

👉 [http://localhost:8081](http://localhost:8081)

You should see the **Flink Dashboard** showing:

* JobManager overview
* Registered TaskManagers
* Cluster metrics and logs

### 🧾 View logs

```bash
docker logs jobmanager
docker logs taskmanager
```

---

## 🧹 **4. Stop and Clean Up the Cluster**

When you’re done, shut everything down:

```bash
chmod +x stop-flink.sh
./stop-flink.sh
```

This script:

* Stops and removes the JobManager and TaskManager containers.
* Removes the `flink-net` Docker network.
* Cleans up gracefully.

---

## ⚙️ **5. Optional Steps**

### 🔄 Restart quickly

You can restart without rebuilding the image:

```bash
./stop-flink.sh
./start-flink.sh
```

### 🧩 Add your own job

Copy your JAR into the image by editing the `Dockerfile`:

```dockerfile
COPY my-flink-job.jar /opt/flink/usrlib/
```

Then, in the JobManager container:

```bash
docker exec -it jobmanager flink run /opt/flink/usrlib/my-flink-job.jar
```

### 🧠 Run interactively (for debugging)

```bash
docker exec -it jobmanager /bin/bash
```

---

## 🧾 **Summary**

| Task           | Command                                               |
| -------------- | ----------------------------------------------------- |
| Build image    | `docker build -t myflink:java21 .`                    |
| Start cluster  | `./start-flink.sh`                                    |
| Verify cluster | `docker ps` / [localhost:8081](http://localhost:8081) |
| Stop cluster   | `./stop-flink.sh`                                     |

---

## 🐳 Docker Compose Setup

You can start your Flink cluster using **Docker Compose** instead of the Bash scripts. This simplifies management and avoids manual container removal.

### `docker-compose.yml`

```yaml
version: "3.9"

services:
  jobmanager:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: jobmanager
    ports:
      - "8081:8081"   # Flink Web UI
    environment:
      - JOB_MANAGER_RPC_ADDRESS=jobmanager
    command: bash -c "$FLINK_HOME/bin/jobmanager.sh start-foreground"
    networks:
      - flink-net

  taskmanager:
    build:
      context: .
      dockerfile: Dockerfile
    container_name: taskmanager
    depends_on:
      - jobmanager
    environment:
      - JOB_MANAGER_RPC_ADDRESS=jobmanager
    command: bash -c "$FLINK_HOME/bin/taskmanager.sh start-foreground"
    networks:
      - flink-net

networks:
  flink-net:
    driver: bridge
```

---

### Start the Cluster

```bash
docker-compose up -d
```

* Automatically builds the image from the Dockerfile if needed.
* Creates the `flink-net` network.
* Starts JobManager and TaskManager containers.

---

### Verify the Cluster

Check container status:

```bash
docker-compose ps
```

View logs:

```bash
docker-compose logs -f jobmanager
docker-compose logs -f taskmanager
```

Open the Flink Web UI:

👉 [http://localhost:8081](http://localhost:8081)

---

### Stop the Cluster

```bash
docker-compose down
```

This stops and removes the containers and the network.

---

