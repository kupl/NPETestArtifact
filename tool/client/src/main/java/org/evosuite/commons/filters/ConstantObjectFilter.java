package org.evosuite.commons.filters;

import org.evosuite.commons.spoon.TypeAccessibilityChecker;
import spoon.reflect.declaration.CtField;
import spoon.reflect.reference.CtTypeReference;

public class ConstantObjectFilter extends BaseTypeFilter<CtField<?>> {
  private CtTypeReference<?> type;

  public static final ConstantObjectFilter INSTANCE = new ConstantObjectFilter();

  private ConstantObjectFilter() {
    super(CtField.class);
  }

  public ConstantObjectFilter setType(CtTypeReference<?> type) {
    this.type = type;
    return this;
  }

  @Override
  public boolean matches(CtField<?> field) {
    if (!super.matches(field)) {
      return false;
    }

    if (!field.getType().getQualifiedName().equals(type.getQualifiedName())) {
      return false;
    }

    return field.isFinal() && field.isStatic() && field.isPublic()
            && TypeAccessibilityChecker.isGloballyAccessible(field.getDeclaringType());
  }
}
