package org.evosuite.commons.filters;

import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtLocalVariableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

public class ConstantAccessFilter extends BaseTypeFilter<CtFieldAccess<?>> {
  private CtTypeReference<?> type;

  public static final ConstantAccessFilter INSTANCE = new ConstantAccessFilter();

  private ConstantAccessFilter() {
    super(CtFieldAccess.class);
  }

  public ConstantAccessFilter setType(CtTypeReference<?> type) {
    this.type = type;
    return this;
  }

  @Override
  public boolean matches(CtFieldAccess<?> fieldRead) {
    if (!super.matches(fieldRead)) {
      return false;
    }

    if (!(fieldRead.getTarget() instanceof CtTypeAccess<?>)) {
      return false;
    }

    if (!fieldRead.getType().unbox().getQualifiedName().equals(type.getQualifiedName())) {
      return false;
    }

    if (fieldRead.getElements(new TypeFilter<>(CtFieldAccess.class)).stream().anyMatch(fr -> fr.getTarget() instanceof CtThisAccess)) {
      return false;
    }

    if (fieldRead.getTarget() instanceof CtVariableRead<?>
            && fieldRead.getTarget().getValueByRole(CtRole.VARIABLE) instanceof CtLocalVariableReference<?>) {
      return false;
    }


    return fieldRead.getVariable().getFieldDeclaration() == null ||
            !fieldRead.getVariable().getFieldDeclaration().isPrivate();
  }
}
