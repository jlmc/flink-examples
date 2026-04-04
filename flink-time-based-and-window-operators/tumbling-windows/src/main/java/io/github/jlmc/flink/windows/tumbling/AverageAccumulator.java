package io.github.jlmc.flink.windows.tumbling;

public final class AverageAccumulator implements java.io.Serializable {
    private final long count;
    private final double sum;

    public AverageAccumulator() {
        this.count = 0L;
        this.sum = 0.0;
    }

    private AverageAccumulator(long count, double sum) {
        this.count = count;
        this.sum = sum;
    }

    public static AverageAccumulator merge(AverageAccumulator a, AverageAccumulator b) {
        return new AverageAccumulator(a.count + b.count, a.sum + b.sum);
    }

    public AverageAccumulator add(double val) {
        return new AverageAccumulator(this.count + 1, this.sum + val);
    }

    public long getCount() {
        return count;
    }

    public double getSum() {
        return sum;
    }

    public double getAverage() {
        return count == 0 ? 0.0 : sum / count;
    }
}
