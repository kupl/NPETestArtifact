package org.evosuite.commons.spoon;

import spoon.reflect.code.CtNewClass;
import spoon.reflect.code.CtReturn;
import spoon.reflect.declaration.*;

import java.util.ArrayList;
import java.util.List;

public class TypeAccessibilityChecker {

  public static boolean isGloballyAccessible(CtType<?> ctType) {
    if (TypeUtils.isPrimitive(ctType)) {
      return true;
    }

    if (ctType.isAnonymous()) {
      return isGloballyAssignableAnonymousClass(ctType);
    }

    if (inaccessible(AccessLevel.GLOBAL, ctType, null)) {
      return false;
    }

    CtType<?> parent = ctType.getDeclaringType();
    while (parent != null) {
      // check accessibility in package-private later

      if (inaccessible(AccessLevel.GLOBAL, ctType, null)) {
        return false;
      }
      parent = parent.getDeclaringType();
    }
    return true;
  }

  public static boolean isGloballyAssignableAnonymousClass(CtType<?> anonymousType) {
    CtReturn<?> ctReturn;
    CtNewClass<?> newClass = anonymousType.getParent(CtNewClass.class);
    if (newClass.equals(anonymousType.getParent())) {
      ctReturn = newClass.getParent(CtReturn.class);
      if (ctReturn != null && ctReturn.equals(newClass.getParent())) {
        CtMethod<?> factoryMethod = ctReturn.getParent(CtMethod.class);
        if (factoryMethod.getDeclaringType().isAnonymous()) {
          return false;
        }
        return !inaccessible(AccessLevel.GLOBAL, factoryMethod, null) &&
                isGloballyAccessible(factoryMethod.getDeclaringType());
      }
    }
    return false;
  }

  public static boolean isPackageLevelAccessible(CtType<?> ctType, CtPackage ctPackage) {
    if (TypeUtils.isPrimitive(ctType)) {
      return true;
    }

    if (ctType.isAnonymous()) {
      return isPackageLevelAssignableAnonymousClass(ctType, ctPackage);
    }

    if (inaccessible(AccessLevel.PACKAGE, ctType, ctPackage)) {
      return false;
    }

    CtType<?> parent = ctType.getDeclaringType();
    while (parent != null) {
      if (inaccessible(AccessLevel.PACKAGE, ctType, ctPackage)) {
        return false;
      }
      parent = parent.getDeclaringType();
    }
    return true;
  }

  private static boolean isPackageLevelAssignableAnonymousClass(CtType<?> anonymousType, CtPackage ctPackage) {
    CtReturn<?> ctReturn;
    CtNewClass<?> newClass = anonymousType.getParent(CtNewClass.class);
    if (newClass.equals(anonymousType.getParent())) {
      ctReturn = newClass.getParent(CtReturn.class);
      if (ctReturn != null && ctReturn.equals(newClass.getParent())) {
        CtMethod<?> factoryMethod = ctReturn.getParent(CtMethod.class);
        if (factoryMethod.getDeclaringType().isAnonymous()) {
          return false;
        }
        return inaccessible(AccessLevel.PACKAGE, factoryMethod, ctPackage) &&
                isPackageLevelAccessible(factoryMethod.getDeclaringType(), ctPackage);
      }
    }
    return false;
  }

  enum AccessLevel {
    GLOBAL, PACKAGE,
  }

  private static boolean inaccessible(AccessLevel level, CtMethod<?> ctMethod, CtPackage ctPackage) {
    switch (level) {
      case GLOBAL:
        return !ctMethod.isPublic();
      case PACKAGE:
        return ctMethod.isPrivate() || (!ctMethod.isPublic() && !ctPackage.equals(ctMethod.getParent(CtPackage.class)));
      default:
        throw new IllegalArgumentException("Unknown access level: " + level);
    }
  }

  private static boolean inaccessible(AccessLevel level, CtType<?> ctType, CtPackage ctPackage) {
    switch (level) {
      case GLOBAL:
        return !ctType.isPublic();
      case PACKAGE:
        return ctType.isPrivate() || (!ctType.isPublic() && !ctPackage.equals(ctType.getParent(CtPackage.class)));
      default:
        throw new IllegalArgumentException("Unknown access level: " + level);
    }
  }
}
