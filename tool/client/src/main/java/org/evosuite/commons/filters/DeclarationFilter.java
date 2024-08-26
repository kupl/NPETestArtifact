package org.evosuite.commons.filters;

import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtVariable;

public class DeclarationFilter extends BaseTypeFilter<CtVariable<?>> {
  private final CtType<?> type;

  public DeclarationFilter(CtType<?> type) {
    super(CtVariable.class);
    this.type = type;
  }

  @Override
  public boolean matches(CtVariable<?> variable) {
    if (!super.matches(variable)) {
      return false;
    }

    return variable.getType().getQualifiedName().equals(type.getQualifiedName());
  }
}
