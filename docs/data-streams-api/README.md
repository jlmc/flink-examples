# Data streams api

- A few basic data sources and sink are built into flink and are always available. The predefined data-sources include reading from files, directory, and sockets and ingested data from collections and iterators. The predefined data sinks support writing to files, to stdout and stderr, and sockets. 

  - https://nightlies.apache.org/flink/flink-docs-release-1.20/docs/connectors/datastream/overview/
  - https://bahir.apache.org/docs/flink/
  - https://bahir.apache.org/docs/flink/current/documentation/
  - https://bahir.apache.org/docs/flink/1.0/documentation/

- Java Collection source
- DataGeneratorSource
- Socket Source
- LocalFile Source
- HdfsFile System Source
- Mongodb Source
- Apache Kafka topic Source
- Customize Source


## Java Collection Source Connector

- `fromCollection`: creates a data stream from the given non-empty collection. The type of the data stream is that of the elements in the collection.
- `fromElements`: Creates a new data stream that contains the given elements. The elements must all be of the same type, for example, all elements of the type String or Integer.
- `fromSequence`: Creates a new data stream that contains a sequence of numbers (longs) and is useful for testing and for cases that just need a stream of N events of any kind. The generated source splits the sequence into many parallel sub-sequences and there are parallel source readers. Each sub-sequence will be produced in order, if the parallelism is limited to one, the source will produce one sequence in order.