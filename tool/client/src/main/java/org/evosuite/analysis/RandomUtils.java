package org.evosuite.analysis;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Random;

public class RandomUtils {
  private RandomUtils() {}

  public static final Random random = new Random();

  public static <T> T select(Collection<T> collection) {
    if (collection.isEmpty()) {
      return null;
    }

    int index = random.nextInt(collection.size());
    Iterator<T> iterator = collection.iterator();
    for (int i = 0; i < index; i++) {
      iterator.next();
    }
    return iterator.next();
  }

  public static <T> List<T> sublist(Collection<T> typeReferences, int length) {
    if (length == 0) {
      return new ArrayList<>();
    }

    if (typeReferences.size() <= length) {
      return new ArrayList<>(typeReferences);
    }
    List<T> copy = new ArrayList<>(typeReferences);
    Collections.shuffle(copy);
    return copy.subList(0, length);
  }

  public static float p() {
    return random.nextFloat();
  }
}
