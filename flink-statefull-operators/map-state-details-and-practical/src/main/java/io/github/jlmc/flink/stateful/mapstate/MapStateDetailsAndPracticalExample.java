package io.github.jlmc.flink.stateful.mapstate;

import org.apache.flink.api.common.state.MapState;
import org.apache.flink.api.common.state.MapStateDescriptor;
import org.apache.flink.api.java.tuple.Tuple3;
import org.apache.flink.configuration.Configuration;
import org.apache.flink.streaming.api.datastream.DataStream;
import org.apache.flink.streaming.api.environment.StreamExecutionEnvironment;
import org.apache.flink.streaming.api.functions.KeyedProcessFunction;
import org.apache.flink.util.Collector;

/**
 * Example focused on Keyed State MapState details and practical usage.
 */
public class MapStateDetailsAndPracticalExample {

    public static void main(String[] args) throws Exception {
        final StreamExecutionEnvironment env = StreamExecutionEnvironment.getExecutionEnvironment();

        DataStream<Score> scoreStream = env
                .socketTextStream("localhost", 9999)
                .map(MapStateDetailsAndPracticalExample::parseScoreLine);

        scoreStream
                .keyBy(Score::getClassId)
                .process(new CourseAverageByClass())
                .print();

        env.execute("MapState Details and Practical - Course Avg by Class");
    }

    static class CourseAverageByClass extends KeyedProcessFunction<String, Score, Tuple3<String, String, Float>> {

        private transient MapState<String, Float> mapState;

        @Override
        public void open(Configuration parameters) {
            MapStateDescriptor<String, Float> mapStateDescriptor =
                    new MapStateDescriptor<>("mapState", String.class, Float.class);
            mapState = getRuntimeContext().getMapState(mapStateDescriptor);
        }

        @Override
        public void processElement(Score score,
                                   KeyedProcessFunction<String, Score, Tuple3<String, String, Float>>.Context ctx,
                                   Collector<Tuple3<String, String, Float>> out) throws Exception {
            Float avgCourseScore = mapState.get(score.getCourseName());

            if (avgCourseScore == null) {
                mapState.put(score.getCourseName(), score.getScore());
            } else {
                mapState.put(score.getCourseName(), (score.getScore() + avgCourseScore) / 2F);
            }

            out.collect(Tuple3.of(
                    ctx.getCurrentKey(),
                    score.getCourseName(),
                    mapState.get(score.getCourseName())
            ));
        }
    }

    static Score parseScoreLine(String line) {
        String[] parts = line.split(",");
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected input format: classId,studentId,courseName,score");
        }
        return new Score(parts[0].trim(), parts[1].trim(), parts[2].trim(), Float.parseFloat(parts[3].trim()));
    }

    public static class Score {
        private final String classId;
        private final String studentId;
        private final String courseName;
        private final float score;

        public Score(String classId, String studentId, String courseName, float score) {
            this.classId = classId;
            this.studentId = studentId;
            this.courseName = courseName;
            this.score = score;
        }

        public String getClassId() {
            return classId;
        }

        public String getStudentId() {
            return studentId;
        }

        public String getCourseName() {
            return courseName;
        }

        public float getScore() {
            return score;
        }
    }
}
