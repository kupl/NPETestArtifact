package org.evosuite.commons.spoon;

import java.util.List;

import spoon.reflect.reference.CtTypeParameterReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;

public class TypeUsabilityChecker {
  private TypeUsabilityChecker() {
  }

  /**
   * Check if the type is used in a variable declaration.
   * Note that it doesn't consider raw type as a declarable type.
   *
   * @param typeReference  the type to check.
   * @param isArrayElement whether the type is used for an array element.
   * @return true if the type is used in a variable declaration, except for raw type.
   * Reference to all primitive types and non-generic types returns true. For generic type reference, if it contains
   * unbounded wildcard, it returns false.
   */
  public static boolean isDeclarable(CtTypeReference<?> typeReference, boolean isArrayElement) {
    if (typeReference instanceof CtTypeParameterReference) {
      return false;
    }

    if (typeReference.getTypeDeclaration().isGenerics() && TypeUtils.isRawType(typeReference)) {
      return false;
    }

    if (!TypeUtils.isRawType(typeReference) && isArrayElement) {
      return false;
    }

    if (TypeUtils.isPrimitive(typeReference)) {
      return true;
    }

    List<CtTypeReference<?>> actualTypeArguments = typeReference.getActualTypeArguments();
    boolean allTypeArgumentsAreDeclarable = true;
    for (CtTypeReference<?> actualTypeArgument : actualTypeArguments) {
      if (!allTypeArgumentsAreDeclarable) {
        break;
      }

      if (actualTypeArgument instanceof CtWildcardReference &&
              ((CtWildcardReference) actualTypeArgument).getBoundingType() != null &&
              !((CtWildcardReference) actualTypeArgument).getBoundingType().isImplicit()) {
        allTypeArgumentsAreDeclarable = TypeAccessibilityChecker.isGloballyAccessible(
                ((CtWildcardReference) actualTypeArgument).getBoundingType().getTypeDeclaration());
        continue;
      }

      if (!(actualTypeArgument instanceof CtWildcardReference) &&
              actualTypeArgument instanceof CtTypeParameterReference) {
        allTypeArgumentsAreDeclarable = false;
      } else {
        allTypeArgumentsAreDeclarable = TypeAccessibilityChecker.isGloballyAccessible(actualTypeArgument.getTypeDeclaration());
      }
    }

    return allTypeArgumentsAreDeclarable;
  }
}
