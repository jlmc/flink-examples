# How to run 

## About it

This is a almost production ready flink job configuration

### What is dependency-reduced-pom.xml?

When you use the Maven Shade Plugin, it creates a “fat” or “uber” JAR that contains all your project’s dependencies.
To prevent double-declaring dependencies in other builds, the Shade plugin generates a dependency-reduced POM.
  - This POM is a copy of your original POM, but it removes the dependencies that are now included inside the shaded JAR.
  - Its purpose: if someone else depends on your shaded artifact, they won’t re-import dependencies already bundled inside the uber JAR.


#### Should it be versioned?

- dependency-reduced-pom.xml is auto-generated every time you run mvn package.
- It is not meant to be committed to your VCS as a main POM.
- You should not manually version it. It’s only used internally by Maven during the shading process.

##### Key points
- Do not edit it manually.
- Do not commit it to Git, unless your team has a very specific workflow. Usually, it’s ignored via .gitignore.
- Its only purpose is for reduced dependency resolution if the shaded artifact is published to a Maven repo.

---

## Locally

Assuming your main class is WorksCounterStreamSocketProcessing:

```bash
# Compile & shade
mvn clean package

# Run locally with Flink (requires flink installed)
FLINK_HOME=/path/to/flink
$FLINK_HOME/bin/flink run target/flink-works-counter-stream-socket-processing-1.0-SNAPSHOT-shaded.jar

```

Or, for testing in standalone JVM mode:
```bash
java -jar target/flink-works-counter-stream-socket-processing-1.0-SNAPSHOT-shaded.jar
```
Make sure log4j2.xml is in src/main/resources so it’s included in the JAR.

## How to deploy to a Flink cluster

1. Standalone Flink cluster
    ```bash
    FLINK_HOME=/path/to/flink
    $FLINK_HOME/bin/flink run -c io.github.jlmc.flink.j4.WorksCounterStreamSocketProcessing \
        target/flink-works-counter-stream-socket-processing-1.0-SNAPSHOT-shaded.jar
    ```

   - `-c` specifies the fully-qualified main class.
   - The shaded JAR contains all runtime dependencies except Flink core (provided scope).

2. YARN cluster
    ```bash
   $FLINK_HOME/bin/flink run -m yarn-cluster \
    -c io.github.jlmc.flink.j4.WorksCounterStreamSocketProcessing \
    target/flink-works-counter-stream-socket-processing-1.0-SNAPSHOT-shaded.jar

   ```
   
3. Kubernetes cluster
```bash
kubectl apply -f your-flink-job.yaml
```
