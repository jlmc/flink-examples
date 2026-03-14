package io.github.jlmc.flink.exchange;


import org.apache.flink.api.common.functions.Partitioner;

public class MyCustomPartitioner<K> implements Partitioner<K> {
    @Override
    public int partition(K key, int numPartitions) {

        int i = key.hashCode();

        return i % numPartitions;
    }
}
