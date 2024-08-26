package org.evosuite.commons.filters;

import java.util.function.Predicate;

import spoon.reflect.declaration.CtElement;
import spoon.reflect.visitor.filter.TypeFilter;

public abstract class BaseTypeFilter<T extends CtElement> extends TypeFilter<T> implements Predicate<T> {

  protected BaseTypeFilter(Class<? super T> type) {
    super(type);
  }

  @Override
  public boolean test(T t) {
    return matches(t);
  }

  @Override
  public Predicate<T> and(Predicate<? super T> other) {
    return Predicate.super.and(other);
  }

  @Override
  public Predicate<T> negate() {
    return Predicate.super.negate();
  }

  @Override
  public Predicate<T> or(Predicate<? super T> other) {
    return Predicate.super.or(other);
  }
}
