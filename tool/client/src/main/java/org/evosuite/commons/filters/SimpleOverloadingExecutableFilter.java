package org.evosuite.commons.filters;

import org.evosuite.commons.spoon.TypeUtils;
import spoon.reflect.code.*;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtModifiable;

import java.util.List;

public class SimpleOverloadingExecutableFilter extends BaseTypeFilter<CtExecutable<?>> {
  public static final SimpleOverloadingExecutableFilter INSTANCE = new SimpleOverloadingExecutableFilter();
  private boolean enabled;

  private SimpleOverloadingExecutableFilter() {
    super(CtExecutable.class);
  }

  @Override
  public boolean matches(CtExecutable<?> executable) {
    if (!super.matches(executable)) {
      return false;
    }

    // if this filter is de-activated
    if (!enabled) {
      return false;
    }

    CtBlock<?> body = executable.getBody();
    if (body == null) {
      return false;
    }

    List<CtStatement> statements = body.getStatements();
    if (statements.size() != 1) {
      return false;
    }

    CtStatement statement = statements.get(0);
    return (statement instanceof CtReturn<?> && ((CtReturn<?>) statement).getReturnedExpression() instanceof CtInvocation<?>
            && ((CtInvocation<?>) ((CtReturn<?>) statement).getReturnedExpression()).getExecutable().getSimpleName().equals(executable.getSimpleName())
            && ((CtInvocation<?>) ((CtReturn<?>) statement).getReturnedExpression()).getTarget() instanceof CtThisAccess<?>
            && ((CtInvocation<?>) ((CtReturn<?>) statement).getReturnedExpression()).getExecutable().getExecutableDeclaration() instanceof CtModifiable
            && !((CtModifiable) ((CtInvocation<?>) ((CtReturn<?>) statement).getReturnedExpression()).getExecutable().getExecutableDeclaration()).isPrivate())
            ||
            (executable.getType().equals(TypeUtils.voidPrimitive()) && statement instanceof CtInvocation<?>
            && ((CtInvocation<?>) statement).getExecutable().getSimpleName().equals(executable.getSimpleName())
            && ((CtInvocation<?>) statement).getTarget() instanceof CtThisAccess<?>
            && ((CtInvocation<?>) statement).getExecutable().getExecutableDeclaration() instanceof CtModifiable
            && !((CtModifiable) ((CtInvocation<?>) statement).getExecutable().getExecutableDeclaration()).isPrivate());
  }

  public void set(boolean enabled) {
    this.enabled = enabled;
  }
}
