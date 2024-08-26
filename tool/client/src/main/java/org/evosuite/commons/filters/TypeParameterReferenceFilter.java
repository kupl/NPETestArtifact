package org.evosuite.commons.filters;

import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtWildcardReference;

public class TypeParameterReferenceFilter extends BaseTypeFilter<CtTypeParameterReference> {
  public static final TypeParameterReferenceFilter INSTANCE = new TypeParameterReferenceFilter();

  private TypeParameterReferenceFilter() {
    super(CtTypeParameterReference.class);
  }

  @Override
  public boolean matches(CtTypeParameterReference element) {
    return super.matches(element) && !(element instanceof CtWildcardReference);
  }
}
