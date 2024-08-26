package org.evosuite.commons.filters;

import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;

public class MethodAccessibilityFilter extends BaseTypeFilter<CtMethod<?>> {
  public static final MethodAccessibilityFilter INSTANCE = new MethodAccessibilityFilter();

  private CtPackage ctPackage;

  public MethodAccessibilityFilter setPackage(CtPackage ctPackage) {
    this.ctPackage = ctPackage;
    return this;
  }

  private MethodAccessibilityFilter() {
    super(CtMethod.class);
  }

  @Override
  public boolean matches(CtMethod<?> ctMethod) {
    if (ctMethod.getDeclaringType().isInterface() || ctMethod.isPublic()) {
      return true;
    } else if (!ctMethod.isPrivate()) {
      return ctMethod.getParent(CtPackage.class) != null &&
              ctMethod.getParent(CtPackage.class).equals(ctPackage);
    } else {
      return false;
    }

  }
}
