package org.evosuite.commons.filters;

import spoon.reflect.code.*;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtShadowable;
import spoon.reflect.visitor.filter.TypeFilter;

public class DereferenceExistenceFilter extends BaseTypeFilter<CtMethod<?>> {
  public static final DereferenceExistenceFilter INSTANCE = new DereferenceExistenceFilter();

  private boolean enabled;

  private DereferenceExistenceFilter() {
    super(CtMethod.class);
  }

  public void set(boolean enabled) {
    this.enabled = enabled;
  }

  @Override
  public boolean matches(CtMethod<?> method) {
    if (!super.matches(method)) {
      return false;
    }

    // if this filter is de-activated
    if (!enabled) {
      return true;
    }

    if (method.getBody() == null) {
      /* Conservative filtering policy
       *
       * Engineering Issue:
       * Spoon's `getAllMethods` return only a definition of method if it is overridden,
       * so we can't decide which method will be called with this API.
       *
       */
      return true;
    }

    int variableAccessCount = method.getBody().filterChildren(new TypeFilter<>(CtTargetedExpression.class))
            .select((CtTargetedExpression<?, ?> expr) -> expr.getTarget() instanceof CtVariableAccess<?>
                    && !(expr.getTarget() instanceof CtThisAccess<?>))
            .map((CtTargetedExpression<?, ?> expr) -> !(((CtVariableAccess<?>) expr.getTarget())
                    .getVariable().getDeclaration() instanceof CtField<?> &&
                    ((CtField<?>) ((CtVariableAccess<?>) expr.getTarget())
                            .getVariable().getDeclaration()).getDefaultExpression() != null))
            .list().size();
    int invocationCount = method.getBody().filterChildren(new TypeFilter<>(CtInvocation.class))
            .select((CtInvocation<?> inv) -> inv.getExecutable() != null &&
                    inv.getExecutable().getExecutableDeclaration() instanceof CtShadowable &&
                    !((CtShadowable) inv.getExecutable().getExecutableDeclaration()).isShadow())
            .list().size();
    int methodReferenceExpressionCount = method.getBody().filterChildren(new TypeFilter<>(CtExecutableReferenceExpression.class))
            .select((CtExecutableReferenceExpression<?, ?> expr) -> expr.getExecutable() != null &&
                    expr.getExecutable().getExecutableDeclaration() instanceof CtShadowable &&
                    !((CtShadowable) expr.getExecutable().getExecutableDeclaration()).isShadow())
            .list().size();
    return variableAccessCount != 0 || invocationCount != 0 || methodReferenceExpressionCount != 0;
  }
}
