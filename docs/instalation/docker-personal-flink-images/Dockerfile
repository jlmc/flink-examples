# ---------------------------------------------
# 🏗️ Base image: OpenJDK 21 (Temurin)
# ---------------------------------------------
FROM eclipse-temurin:21-jdk-jammy

# Set environment variables
ENV FLINK_VERSION=2.1.0
ENV SCALA_VERSION=2.12
ENV FLINK_HOME=/opt/flink
ENV PATH=$PATH:$FLINK_HOME/bin

# Install system dependencies
RUN apt-get update && \
    apt-get install -y curl tar bash && \
    rm -rf /var/lib/apt/lists/*

# Download and extract the Flink binary (Java-only)
# Apache now provides flink-*-bin-java.tgz builds since 1.16+
RUN curl -fsSL https://dlcdn.apache.org/flink/flink-${FLINK_VERSION}/flink-${FLINK_VERSION}-bin-scala_${SCALA_VERSION}.tgz \
    | tar -xz -C /opt && \
    mv /opt/flink-${FLINK_VERSION} ${FLINK_HOME}


# Optional: add your custom Flink job JAR
# COPY my-flink-job.jar $FLINK_HOME/usrlib/
# # Example of adding a JAR from an external source
#RUN curl -o $FLINK_HOME/usrlib/my-job.jar -L https://example.com/path/to/my-job.jar
# Then, in CMD, automatically start the job:
# CMD ["bash", "-c", "$FLINK_HOME/bin/flink run -d $FLINK_HOME/usrlib/my-job.jar && tail -f /dev/null"]


# Expose Flink Web UI port
EXPOSE 8081

# Default command (JobManager)
CMD ["bash", "-c", "$FLINK_HOME/bin/jobmanager.sh start-foreground"]