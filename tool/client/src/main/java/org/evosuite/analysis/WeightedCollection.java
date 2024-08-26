package org.evosuite.analysis;

import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.NavigableMap;
import java.util.Random;
import java.util.TreeMap;
import java.util.function.Function;

public class WeightedCollection<E> {
  private final NavigableMap<Float, E> map = new TreeMap<>();
  private final Random random;
  private float total = 0;
  private static final float EPSILON = 1e-3f;

  public WeightedCollection() {
    this(new Random());
  }

  public WeightedCollection(Random random) {
    this.random = random;
  }

  public <N extends Number> WeightedCollection(Map<E, N> map) {
    this.random = new Random();
    for (Entry<E, N> entry : map.entrySet()) {
      E element = entry.getKey();
      N weight = entry.getValue();
      total += weight.floatValue() + EPSILON;
      this.map.put(total, element);
    }
  }

  public <N extends Number> WeightedCollection<E> add(N weight, E element) {
    float weightFloat = weight.floatValue();
    if (weightFloat < 0f)
      return this;
    total += weightFloat + EPSILON;
    map.put(total, element);
    return this;
  }

  public E next() {
    float value = random.nextFloat() * total;
    return map.ceilingEntry(value) != null ? map.ceilingEntry(value).getValue() : null;
  }
  

  public boolean isEmpty() {
    return map.isEmpty();
  }

  public <N extends Number> void setup(List<E> seedPool, Function<E, N> scoringFunction) {
    for (E e : seedPool) {
      N score = scoringFunction.apply(e);
      add(score.floatValue(), e);
    }
  }
}
