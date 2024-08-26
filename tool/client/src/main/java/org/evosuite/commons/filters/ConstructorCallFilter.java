package org.evosuite.commons.filters;

import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.declaration.CtType;

public class ConstructorCallFilter extends BaseTypeFilter<CtConstructorCall<?>> {
  private final CtType<?> type;

  public ConstructorCallFilter(CtType<?> type) {
    super(CtConstructorCall.class);
    this.type = type;
  }

  @Override
  public boolean matches(CtConstructorCall<?> element) {
    return super.matches(element) && element.getType().getQualifiedName().equals(type.getQualifiedName());
  }
}
