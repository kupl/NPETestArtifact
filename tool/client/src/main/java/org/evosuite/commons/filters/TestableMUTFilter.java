package org.evosuite.commons.filters;

import org.evosuite.commons.spoon.TypeAccessibilityChecker;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;

public class TestableMUTFilter extends BaseTypeFilter<CtMethod<?>> {
  public static final TestableMUTFilter INSTANCE = new TestableMUTFilter();

  private TestableMUTFilter() {
    super(CtMethod.class);
  }

  @Override
  public boolean matches(CtMethod<?> method) {
    if (!super.matches(method)) {
      return false;
    }

    CtType<?> declaringType = method.getDeclaringType();

    if (declaringType.isAnonymous()) {
      // check if it is instantiable later
      return TypeAccessibilityChecker.isGloballyAssignableAnonymousClass(declaringType);
    }

    CtType<?> parent = declaringType;
    while (parent != null) {
      // check accessibility in package-private later
      if (!parent.isPublic()) {
        if (!parent.isAbstract()) {
          return false;
        }
      } else {
        if (parent.isTopLevel()) {
          return true;
        }
      }
      parent = parent.getDeclaringType();
    }
    return true;
  }
}
