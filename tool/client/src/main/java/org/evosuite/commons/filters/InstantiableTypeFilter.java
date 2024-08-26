package org.evosuite.commons.filters;

import org.evosuite.analysis.ClassInfo;
import org.evosuite.commons.spoon.TypeAccessibilityChecker;
import org.evosuite.commons.spoon.TypeUtils;
import spoon.reflect.declaration.*;
import spoon.reflect.reference.CtTypeReference;

import java.util.ArrayList;
import java.util.List;

public class InstantiableTypeFilter extends BaseTypeFilter<CtTypeReference<?>> {
  public static final InstantiableTypeFilter INSTANCE = new InstantiableTypeFilter();

  private CtPackage ctPackage;

  protected InstantiableTypeFilter() {
    super(CtTypeReference.class);
  }

  public InstantiableTypeFilter setPackage(CtPackage ctPackage) {
    this.ctPackage = ctPackage;
    return this;
  }

  // @Override
  // public boolean matches(CtTypeReference<?> ctTypeReference) {
  //   if (TypeUtils.isJavaLangClass(ctTypeReference)) {
  //     return true;
  //   }

  //   if (ctTypeReference.getTypeDeclaration() instanceof CtInterface) {
  //     return false;
  //   }

  //   CtClass<?> ctClass = (CtClass<?>) ctTypeReference.getTypeDeclaration();

  //   if (ctClass.isAbstract()) {
  //     return false;
  //   }

  //   if (!TypeAccessibilityChecker.isGloballyAccessible(ctClass)) {
  //     return false;
  //   }

  //   List<CtExecutable<?>> nonPublicConstructors = new ArrayList<>();
  //   for (String constrName: ClassInfo.getInstance().getAllConstrs()) {

  //     if (constructor.isPublic()) {
  //       return true;
  //     } else if (constructor.isPrivate()) {
  //       nonPublicConstructors.add(constructor);
  //     }
  //   }

  //   boolean isAccessibleInPackageLevel = ctClass.getParent(CtPackage.class).equals(ctPackage);
  //   return !nonPublicConstructors.isEmpty() && isAccessibleInPackageLevel;
  // }
}
