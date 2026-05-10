package io.github.jlmc.flink.stateful.ttl;

import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.state.StateTtlConfig;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;
import java.time.Duration;

/**
 * Example focused on Keyed State TTL with a left join semantic.
 */
public class KeyedStateTtlDetailsAndPracticalExample {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<User> userStream = env
                .socketTextStream("localhost", 9998)
                .map(KeyedStateTtlDetailsAndPracticalExample::parseUserLine, Types.POJO(User.class));

        DataStream<Address> addressStream = env
                .socketTextStream("localhost", 9999)
                .map(KeyedStateTtlDetailsAndPracticalExample::parseAddressLine, Types.POJO(Address.class));

        userStream
                .keyBy(User::getId)
                .connect(addressStream.keyBy(Address::getId))
                .process(new LeftJoinFunction())
                .print();

        env.execute("Keyed State TTL Details and Practical - Left Join");
    }

    static User parseUserLine(String line) {
        String[] parts = line.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected user input format: id,name");
        }
        return new User(Integer.parseInt(parts[0].trim()), parts[1].trim());
    }

    static Address parseAddressLine(String line) {
        String[] parts = line.split(",");
        if (parts.length != 2) {
            throw new IllegalArgumentException("Expected address input format: id,country");
        }
        return new Address(Integer.parseInt(parts[0].trim()), parts[1].trim());
    }

    static class LeftJoinFunction extends KeyedCoProcessFunction<Integer, User, Address, UserWithAddress> {

        private transient MapState<Integer, Address> addressState;

        @Override
        public void open(Configuration parameters) {
            // Build TTL policy once during operator initialization.
            // This keeps state bounded in long-running jobs and avoids unbounded growth of address entries.
            StateTtlConfig ttlConfig = StateTtlConfig
                    // Retention window: each address entry lives for 1 minute since last create/write.
                    // Chosen to make expiration behavior easy to observe in this practical example.
                    .newBuilder(Duration.ofMinutes(1))
                    // Refresh TTL only on create/write.
                    // Justification: reads should not prolong entry lifetime, otherwise stale addresses could survive forever under frequent reads.
                    .setUpdateType(StateTtlConfig.UpdateType.OnCreateAndWrite)
                    // Never expose expired values to user code.
                    // Justification: guarantees deterministic left-join semantics after expiration (expired address is treated as absent/null).
                    .setStateVisibility(StateTtlConfig.StateVisibility.NeverReturnExpired)
                    // Finalize immutable TTL configuration object.
                    .build();

            // Declare managed keyed state metadata: name + key/value serializers.
            // Key=Integer (user id), Value=Address (right side of the join).
            MapStateDescriptor<Integer, Address> descriptor =
                    new MapStateDescriptor<>("mapState", Types.INT, Types.POJO(Address.class));
            // Attach TTL policy to this descriptor so every map entry follows the same expiration rules.
            descriptor.enableTimeToLive(ttlConfig);

            // Obtain the runtime-managed MapState instance (checkpointed/restored by Flink).
            addressState = getRuntimeContext().getMapState(descriptor);
        }

        @Override
        public void processElement1(User user,
                                    KeyedCoProcessFunction<Integer, User, Address, UserWithAddress>.Context ctx,
                                    Collector<UserWithAddress> out) throws Exception {
            Address address = addressState.get(user.getId());
            out.collect(new UserWithAddress(user, address));
        }

        @Override
        public void processElement2(Address address,
                                    KeyedCoProcessFunction<Integer, User, Address, UserWithAddress>.Context ctx,
                                    Collector<UserWithAddress> out) throws Exception {
            addressState.put(address.getId(), address);
        }
    }

    public static class User implements Serializable {
        private int id;
        private String name;

        public User() {
        }

        public User(int id, String name) {
            this.id = id;
            this.name = name;
        }

        public int getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class Address implements Serializable {
        private int id;
        private String country;

        public Address() {
        }

        public Address(int id, String country) {
            this.id = id;
            this.country = country;
        }

        public int getId() {
            return id;
        }

        public String getCountry() {
            return country;
        }
    }

    public static class UserWithAddress implements Serializable {
        private User user;
        private Address address;

        public UserWithAddress() {
        }

        public UserWithAddress(User user, Address address) {
            this.user = user;
            this.address = address;
        }

        @Override
        public String toString() {
            return "UserWithAddress{" +
                    "userId=" + (user == null ? null : user.getId()) +
                    ", userName='" + (user == null ? null : user.getName()) + '\'' +
                    ", country='" + (address == null ? null : address.getCountry()) + '\'' +
                    '}';
        }
    }
}
