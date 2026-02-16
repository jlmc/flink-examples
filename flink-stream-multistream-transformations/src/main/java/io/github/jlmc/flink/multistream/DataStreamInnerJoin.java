package io.github.jlmc.flink.multistream;

import org.apache.flink.api.common.functions.OpenContext;
import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.common.typeinfo.Types;
import org.apache.flink.api.java.typeutils.PojoTypeInfo;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.datastream.SingleOutputStreamOperator;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.co.KeyedCoProcessFunction;
import org.apache.flink.util.Collector;

import java.io.Serializable;

public class DataStreamInnerJoin {

    public static void main(String[] args) throws Exception {
        StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<User> usersDs = usersDs(env);
        DataStream<Address> addressesDs = addressDs(env);


        defineWorkflow(usersDs, addressesDs)
                .map(u -> String.format("Enriched User: userId=%d, name=%s came from: city=%s", u.userId, u.name, u.city))
                .print();


        env.execute("DataStream Inner Join Example");
    }

    private static DataStream<Address> addressDs(StreamExecutionEnvironment env) {
        SingleOutputStreamOperator<Address> addressesDs = env.fromData(
                "1,New York",
                "2,Los Angeles",
                "3,Chicago",
                "4,Houston",
                "5,Phoenix",
                "6,Philadelphia",
                "7,Houston",
                "8,Phoenix",
                "9,Philadelphia",
                "10,San Antonio"
        ).map(line -> {
            String[] parts = line.split(",");
            return new Address(Integer.parseInt(parts[0].trim()), parts[1].trim());
        }, PojoTypeInfo.of(Address.class));
        //}, Types.POJO(Address.class));
        return addressesDs;
    }

    private static DataStream<User> usersDs(StreamExecutionEnvironment env) {
        SingleOutputStreamOperator<User> usersDs = env.fromData(
                "1,Alice",
                "2,Bob",
                "3,Carlos",
                "4,Elvis",
                "5,Felix",
                "6,George",
                "10,Martin"
        ).map(line -> {
            String[] parts = line.split(",");
            return new User(Integer.parseInt(parts[0].trim()), parts[1].trim());
        }, Types.POJO(User.class));
        //}, PojoTypeInfo.of(User.class));
        return usersDs;
    }

    public static DataStream<EnrichedUser> defineWorkflow(DataStream<User> usersDs, DataStream<Address> addressesDs) {
        SingleOutputStreamOperator<EnrichedUser> joinedStream =
                usersDs.connect(addressesDs)
                        .keyBy(User::userId, Address::userId)
                        .process(
                                new KeyedCoProcessFunction<Integer, User, Address, EnrichedUser>() {

                                    MapState<Integer, User> userMapState;
                                    MapState<Integer, Address> addressMapState;

                                    @Override
                                    public void open(OpenContext openContext) throws Exception {
                                        super.open(openContext);
                                        this.userMapState = getRuntimeContext().getMapState(
                                                new MapStateDescriptor<Integer, User>(
                                                        "user-state",
                                                        Types.INT,
                                                        Types.POJO(User.class)
                                                )
                                        );
                                        this.addressMapState = getRuntimeContext().getMapState(
                                                new MapStateDescriptor<Integer, Address>(
                                                        "address-state",
                                                        Types.INT,
                                                        Types.POJO(Address.class)
                                                )
                                        );
                                    }

                                    @Override
                                    public void processElement1(User user, Context ctx, Collector<EnrichedUser> out) throws Exception {
                                        userMapState.put(user.userId, user);
                                        emitEnrichedUserIfPossible(ctx, out);
                                    }

                                    @Override
                                    public void processElement2(Address address, Context ctx, Collector<EnrichedUser> out) throws Exception {
                                        addressMapState.put(address.userId, address);
                                        emitEnrichedUserIfPossible(ctx, out);
                                    }

                                    private void emitEnrichedUserIfPossible(Context ctx, Collector<EnrichedUser> out) throws Exception {
                                        Integer userId = ctx.getCurrentKey();
                                        User user = userMapState.get(userId);
                                        Address address = addressMapState.get(userId);

                                        if (user != null && address != null) {
                                            out.collect(new EnrichedUser(user.userId, user.name, address.city));
                                        }
                                    }
                                }
                        );


        return joinedStream;
    }

    public record User(Integer userId, String name) implements Serializable {
    }

    public record Address(Integer userId, String city) implements Serializable  {
    }

    public record EnrichedUser(Integer userId, String name, String city)  implements Serializable {
    }
}
