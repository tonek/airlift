package com.facebook.presto.benchmark;

import io.airlift.units.DataSize;
import io.airlift.units.Duration;

import javax.annotation.Nullable;
import java.util.Map;

import static com.facebook.presto.cli.FormatUtils.formatCount;
import static com.facebook.presto.cli.FormatUtils.formatCountRate;
import static com.facebook.presto.cli.FormatUtils.formatDataRate;
import static com.facebook.presto.cli.FormatUtils.formatDataSize;
import static com.google.common.base.Preconditions.checkArgument;
import static com.google.common.base.Preconditions.checkNotNull;
import static io.airlift.units.DataSize.Unit.BYTE;
import static java.util.concurrent.TimeUnit.NANOSECONDS;

public abstract class AbstractBenchmark
{
    private final String benchmarkName;
    private final int warmupIterations;
    private final int measuredIterations;

    protected AbstractBenchmark(String benchmarkName, int warmupIterations, int measuredIterations)
    {
        checkNotNull(benchmarkName, "benchmarkName is null");
        checkArgument(warmupIterations >= 0, "warmupIterations must not be negative");
        checkArgument(measuredIterations >= 0, "measuredIterations must not be negative");

        this.benchmarkName = benchmarkName;
        this.warmupIterations = warmupIterations;
        this.measuredIterations = measuredIterations;
    }

    public String getBenchmarkName()
    {
        return benchmarkName;
    }

    /**
     * Some monitoring tools only accept one result. Return the name of the result that
     * should be tracked.
     */
    protected abstract String getDefaultResult();

    /**
     * Initialize any state necessary to run benchmark. This is run once at start up.
     */
    protected void setUp()
    {
        // Default: no-op
    }

    /**
     * Runs the benchmark and returns the result metrics
     */
    protected abstract Map<String, Long> runOnce();

    /**
     * Clean up any state from the benchmark. This is run once after all the iterations are complete.
     */
    protected void tearDown()
    {
        // Default: no-op
    }

    public void runBenchmark()
    {
        runBenchmark(null);
    }

    public void runBenchmark(@Nullable BenchmarkResultHook benchmarkResultHook)
    {
        AverageBenchmarkResults averageBenchmarkResults = new AverageBenchmarkResults();
        setUp();
        try {
            for (int i = 0; i < warmupIterations; i++) {
                runOnce();
            }
            for (int i = 0; i < measuredIterations; i++) {
                Map<String, Long> results = runOnce();
                if (benchmarkResultHook != null) {
                    benchmarkResultHook.addResults(results);
                }
                averageBenchmarkResults.addResults(results);
            }
        } finally {
            tearDown();
        }
        if (benchmarkResultHook != null) {
            benchmarkResultHook.finished();
        }

        Map<String, Double> resultsAvg = averageBenchmarkResults.getAverageResultsValues();
        Duration cpuNanos = new Duration(resultsAvg.get("cpu_nanos"), NANOSECONDS);

        long inputRows = resultsAvg.get("input_rows").longValue();
        DataSize inputBytes = new DataSize(resultsAvg.get("input_bytes"), BYTE);

        long outputRows = resultsAvg.get("output_rows").longValue();
        DataSize outputBytes = new DataSize(resultsAvg.get("output_bytes"), BYTE);

        System.out.printf("%35s :: %8.3f cpu ms :: in %5s,  %6s,  %8s,  %8s :: out %5s,  %6s,  %8s,  %8s%n",
                getBenchmarkName(),
                cpuNanos.toMillis(),

                formatCount(inputRows),
                formatDataSize(inputBytes, true),
                formatCountRate(inputRows, cpuNanos, true),
                formatDataRate(inputBytes, cpuNanos, true),

                formatCount(outputRows),
                formatDataSize(outputBytes, true),
                formatCountRate(outputRows, cpuNanos, true),
                formatDataRate(outputBytes, cpuNanos, true));
    }
}