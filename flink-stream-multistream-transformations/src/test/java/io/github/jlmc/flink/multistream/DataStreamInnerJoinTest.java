package io.github.jlmc.flink.multistream;

import io.github.jlmc.flink.testutils.CollectSink;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.test.util.AbstractTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DataStreamInnerJoinTest extends AbstractTestBase {

    @BeforeEach
    void setUp() {
        CollectSink.clear();
    }

    @Test
    @DisplayName("Should join user and address when both are present for the same user ID")
    void shouldJoinUserAndAddressWhenBothArePresent() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var users = List.of(
                new DataStreamInnerJoin.User(1, "Alice"),
                new DataStreamInnerJoin.User(2, "Bob")
        );
        var addresses = List.of(
                new DataStreamInnerJoin.Address(1, "New York"),
                new DataStreamInnerJoin.Address(3, "Chicago")
        );

        DataStreamInnerJoin.defineWorkflow(env.fromCollection(users), env.fromCollection(addresses))
                .addSink(new CollectSink<>());

        env.execute();

        List<DataStreamInnerJoin.EnrichedUser> results = getResults();

        assertThat(results)
                .hasSize(1)
                .extracting(DataStreamInnerJoin.EnrichedUser::userId)
                .containsExactly(1);
        
        assertThat(results.get(0).name()).isEqualTo("Alice");
        assertThat(results.get(0).city()).isEqualTo("New York");
    }

    @Test
    @DisplayName("Should produce multiple joined records when data arrives in interleaved order")
    void shouldProduceMultipleJoinedRecords() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var users = List.of(
                new DataStreamInnerJoin.User(1, "Alice"),
                new DataStreamInnerJoin.User(2, "Bob")
        );
        var addresses = List.of(
                new DataStreamInnerJoin.Address(2, "Los Angeles"),
                new DataStreamInnerJoin.Address(1, "New York")
        );

        DataStreamInnerJoin.defineWorkflow(env.fromCollection(users), env.fromCollection(addresses))
                .addSink(new CollectSink<>());

        env.execute();

        List<DataStreamInnerJoin.EnrichedUser> results = getResults();

        assertThat(results)
                .hasSize(2)
                .extracting(DataStreamInnerJoin.EnrichedUser::userId)
                .containsExactlyInAnyOrder(1, 2);
    }

    @Test
    @DisplayName("Should not produce any output when there are no matching user IDs")
    void shouldNotProduceOutputWhenNoMatches() throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();
        env.setParallelism(1);

        var users = List.of(new DataStreamInnerJoin.User(1, "Alice"));
        var addresses = List.of(new DataStreamInnerJoin.Address(2, "Los Angeles"));

        DataStreamInnerJoin.defineWorkflow(env.fromCollection(users), env.fromCollection(addresses))
                .addSink(new CollectSink<>());

        env.execute();

        List<DataStreamInnerJoin.EnrichedUser> results = getResults();

        assertThat(results).isEmpty();
    }

    private List<DataStreamInnerJoin.EnrichedUser> getResults() {
        return CollectSink.<DataStreamInnerJoin.EnrichedUser>values().stream()
                .toList();
    }
}
