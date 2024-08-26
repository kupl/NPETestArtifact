package org.evosuite.commons.filters;

import org.evosuite.commons.spoon.TypeAccessibilityChecker;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;

import java.util.List;

public class GeneratorMethodFilter extends BaseTypeFilter<CtMethod<?>> {
  public static final GeneratorMethodFilter INSTANCE = new GeneratorMethodFilter();

  private CtTypeReference<?> concreteReturnType;

  private GeneratorMethodFilter() {
    super(CtMethod.class);
  }

  public GeneratorMethodFilter setReturnType(CtTypeReference<?> returnType) {
    this.concreteReturnType = returnType;
    return this; 
  }

  @Override
  public boolean matches(CtMethod<?> method) {
    if (!super.matches(method)) {
      return false;
    }

    if (!method.isStatic() || !method.isPublic()
            || method.getDeclaringType() == null && !TypeAccessibilityChecker.isGloballyAccessible(method.getDeclaringType())) {
      return false;
    }

    CtTypeReference<?> returnType = method.getType();
    if (!returnType.getQualifiedName().equals(concreteReturnType.getQualifiedName())) {
      return false;
    }

    if (!concreteReturnType.getTypeDeclaration().isGenerics()) {
      return true;
    }

    List<CtTypeReference<?>> actualTypeArguments = concreteReturnType.getActualTypeArguments();
    if (returnType.getActualTypeArguments().equals(actualTypeArguments)) {
      return true;
    }

    boolean unadaptable = false;
    for (int i = 0; i < returnType.getActualTypeArguments().size(); i++) {
      CtTypeReference<?> typeArgument1 = returnType.getActualTypeArguments().get(i);
      CtTypeReference<?> typeArgument2 = concreteReturnType.getActualTypeArguments().get(i);
      unadaptable |= !(typeArgument1 instanceof CtTypeParameterReference) && !(typeArgument1.equals(typeArgument2));
    }

    return !unadaptable;
  }
}
