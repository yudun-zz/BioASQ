package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer;

import static java.util.stream.Collectors.toList;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.BinaryOperator;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collector;
import java.util.stream.IntStream;

import com.google.common.collect.ImmutableSet;

public class FeaturesSequenceCollector implements
        Collector<List<Map<String, Double>>, List<Map<String, Double>>, List<Map<String, Double>>> {

  private int sequenceLength;

  public FeaturesSequenceCollector(int sequenceLength) {
    this.sequenceLength = sequenceLength;
  }

  @Override
  public Supplier<List<Map<String, Double>>> supplier() {
    return () -> IntStream.range(0, sequenceLength).mapToObj(i -> new HashMap<String, Double>())
            .collect(toList());
  }

  @Override
  public BiConsumer<List<Map<String, Double>>, List<Map<String, Double>>> accumulator() {
    return (x, y) -> IntStream.range(0, x.size()).forEach(i -> x.get(i).putAll(y.get(i)));
  }

  @Override
  public BinaryOperator<List<Map<String, Double>>> combiner() {
    return (x, y) -> {
      accumulator().accept(x, y);
      return x;
    };
  }

  @Override
  public Function<List<Map<String, Double>>, List<Map<String, Double>>> finisher() {
    return Function.identity();
  }

  @Override
  public Set<java.util.stream.Collector.Characteristics> characteristics() {
    return ImmutableSet.of();
  }

}