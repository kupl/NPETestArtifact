package org.evosuite.commons.spoon;

import spoon.reflect.code.*;
import spoon.reflect.declaration.*;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.Filter;
import spoon.reflect.visitor.chain.CtQueryable;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class ASTUtils {
  private ASTUtils() {
  }

  public static CtStatement getEnclosingStatement(CtCodeElement el) {
    CtCodeElement parent = el;
    while (parent != null) {
      if (!parent.isParentInitialized()) {
        return null;
      }
      if (parent.getParent() instanceof CtBlock<?>) {
        return (CtStatement) parent;
      } else {
        parent = parent.getParent(CtStatement.class);
      }
    }
    return null;
  }

  public static CtMethod<?> getEnclosingMethod(CtElement el) {
    return el.getParent(CtMethod.class);
  }

  public static CtExecutable<?> getEnclosingExecutable(CtType<?> ctType, int position) {
    List<CtCodeElement> codeElements = ctType.filterChildren(new TypeFilter<CtCodeElement>(CtCodeElement.class) {
              @Override
              public boolean matches(CtCodeElement codeElement) {
                return super.matches(codeElement) && codeElement.getParent(CtExecutable.class) != null;
              }
            })
            .select(codeElement -> codeElement.getPosition().isValidPosition() &&
                    codeElement.getPosition().getLine() <= position && position <= codeElement.getPosition().getEndLine())
            .list();

    return codeElements.isEmpty() ? null : codeElements.get(0).getParent(CtExecutable.class);
  }

  public static CtTypeReference<?> findMatchingSuperTypeReference(CtType<?> subType, CtTypeReference<?> superType) {
    if (superType.isInterface()) {
      List<CtTypeReference<?>> matchingSuperInterfaces = subType.getSuperInterfaces().stream()
              .filter(superInterface -> superInterface.getQualifiedName().equals(superType.getQualifiedName()))
              .collect(Collectors.toList());
      if (matchingSuperInterfaces.size() != 1) {
        return null;
      }
      return matchingSuperInterfaces.get(0);
    } else {
      CtTypeReference<?> superclass = subType.getSuperclass();
      if (superclass == null || !superclass.getQualifiedName().equals(superType.getQualifiedName())) {
        return null;
      }
      return superclass;
    }
  }

  public static CtExpression<?> getOtherSideOfBinOp(CtFieldAccess<?> fieldAccess) {
    CtBinaryOperator<?> binOp = (CtBinaryOperator<?>) fieldAccess.getParent();
    CtRole roleInParent = fieldAccess.getRoleInParent();
    if (roleInParent.equals(CtRole.LEFT_OPERAND)) {
      return binOp.getValueByRole(CtRole.RIGHT_OPERAND);
    } else {
      return binOp.getValueByRole(CtRole.LEFT_OPERAND);
    }
  }

  public static void removeDuplicatedVariable(CtStatementList statements) {
    List<CtLocalVariable<?>> localVariables = statements
            .filterChildren(new TypeFilter<>(CtLocalVariable.class)).list();
    Map<String, Integer> coveredCounts = new HashMap<>();
    HashMap<String, Integer> counts = new HashMap<>();
    for (CtLocalVariable<?> localVariable : localVariables) {
      String variableName = localVariable.getSimpleName();
      int cnt = counts.getOrDefault(variableName, 0);
      if (cnt != 0) {
        coveredCounts.put(variableName, 0);
      }
      counts.put(variableName, cnt + 1);
    }

    int size = statements.getStatements().size();
    List<Integer> removeTargetIndices = new ArrayList<>();
    for (int i = size - 1; i >= 0; i--) {
      CtStatement statement = statements.getStatement(i);
      if (statement instanceof CtLocalVariable<?>) {
        CtLocalVariable<?> localVariable = (CtLocalVariable<?>) statement;
        String variableName = localVariable.getSimpleName();
        if (coveredCounts.containsKey(variableName)) {
          int coveredCount = coveredCounts.get(variableName);
          int count = counts.get(variableName);
          if (coveredCount < count - 1) {
            coveredCounts.put(variableName, coveredCount + 1);
            removeTargetIndices.add(i);
          }
        }
      }
    }

    for (int index : removeTargetIndices) {
      statements.getStatements().remove(index);
    }
  }

  public static CtTypeReference<?> getReturnType(CtExecutable<?> ctMethod) {
    return ctMethod.getType();
  }

  public static boolean isNullCheckingConditionForParameter(CtLiteral<?> nullRef) {
    if (nullRef.getRoleInParent().equals(CtRole.LEFT_OPERAND)) {
      CtExpression<?> rhs = ((CtBinaryOperator<?>) nullRef.getParent()).getRightHandOperand();
      return rhs instanceof CtVariableRead<?> &&
              ((CtVariableReference<?>) ((CtVariableRead<?>) rhs).getVariable()).getDeclaration() instanceof CtParameter<?>;
    } else if (nullRef.getRoleInParent().equals(CtRole.RIGHT_OPERAND)) {
      CtExpression<?> lhs = ((CtBinaryOperator<?>) nullRef.getParent()).getLeftHandOperand();
      return lhs instanceof CtVariableRead<?> &&
              ((CtVariableReference<?>) ((CtVariableRead<?>) lhs).getVariable()).getDeclaration() instanceof CtParameter<?>;
    }
    return false;
  }

  public static CtExpression<?> getOppositeSideOfBinOp(CtExpression<?> expression) {
    CtRole rhsOrLhs = expression.getRoleInParent();
    switch (rhsOrLhs) {
      case LEFT_OPERAND:
        return ((CtBinaryOperator<?>) expression.getParent()).getRightHandOperand();
      case RIGHT_OPERAND:
        return ((CtBinaryOperator<?>) expression.getParent()).getLeftHandOperand();
      default:
        return null;
    }
  }

  public static int getParameterIndex(CtMethod<?> method, CtParameter<?> parameter) {
    int index = -1;
    for (int i = 0; i < method.getParameters().size(); i++) {
      if (method.getParameters().get(i).equals(parameter)) {
        index = i;
        break;
      }
    }
    return index;
  }

  public static List<CtLiteral<?>> getAllNullLiterals(CtQueryable queryRoot) {
    return queryRoot.filterChildren(new TypeFilter<>(CtLiteral.class))
            .select((CtLiteral<?> l) -> l.getType() != null && l.getType().equals(TypeUtils.nullType()))
            .list();
  }

  public static int countVariableAccess(CtMethod<?> method, CtVariable<?> lv) {
    return method.filterChildren(new TypeFilter<>(CtTargetedExpression.class))
            .select((CtTargetedExpression<?, ?> expr) ->
                    expr.getTarget() instanceof CtVariableAccess &&
                            lv.equals(((CtVariableAccess<?>) expr.getTarget()).getVariable().getDeclaration()))
            .list()
            .size();
  }

  public static boolean hasVariableAccess(CtMethod<?> method, CtVariable<?> lv) {
    return method.filterChildren(new TypeFilter<>(CtTargetedExpression.class))
            .select((CtTargetedExpression<?, ?> expr) ->
                    expr.getTarget() instanceof CtVariableAccess &&
                            lv.equals(((CtVariableAccess<?>) expr.getTarget()).getVariable().getDeclaration()))
            .list()
            .isEmpty();
  }

  public static Filter<CtMethod<?>> isEmptyMethod() {
    return (CtMethod<?> m) -> m.getBody() != null && !m.getBody().getStatements().isEmpty();
  }

  public static boolean isNullProperlyHandled(CtLiteral<?> nullRef) {
    if (nullRef.getParent() instanceof CtBinaryOperator &&
            ((CtBinaryOperator<?>) nullRef.getParent()).getKind().equals(BinaryOperatorKind.EQ) &&
            nullRef.getParent().getParent() instanceof CtIf) {
      CtIf ifStatement = (CtIf) (nullRef.getParent().getParent());
      CtStatement thenStatement = ifStatement.getThenStatement();
      return !thenStatement.filterChildren(new TypeFilter<>(CtThrow.class))
              .select((CtThrow th) -> th.getThrownExpression().getType().getSimpleName().equals("NullPointerException"))
              .list().isEmpty();
    }

    return false;
  }

  public static boolean isNullCheckingConditionForField(CtBinaryOperator<?> binOp) {
    CtExpression<?> lhs = binOp.getLeftHandOperand();
    CtExpression<?> rhs = binOp.getRightHandOperand();
    if (lhs instanceof CtLiteral<?> && TypeUtils.isNull(lhs.getType())) {
      return rhs instanceof CtFieldAccess<?> && ((CtFieldAccess<?>) rhs).getTarget() instanceof CtThisAccess<?>;
    } else if (rhs instanceof CtLiteral<?> && TypeUtils.isNull(rhs.getType())) {
      return lhs instanceof CtFieldAccess<?> && ((CtFieldAccess<?>) lhs).getTarget() instanceof CtThisAccess<?>;
    }
    return false;
  }

  public static CtFieldAccess<?> extractFieldAccess(CtBinaryOperator<?> binOp) {
    CtExpression<?> lhs = binOp.getLeftHandOperand();
    CtExpression<?> rhs = binOp.getRightHandOperand();
    if (lhs instanceof CtLiteral<?> && TypeUtils.isNull(lhs.getType())) {
      return ((CtFieldAccess<?>) rhs);
    } else if (rhs instanceof CtLiteral<?> && TypeUtils.isNull(rhs.getType())) {
      return ((CtFieldAccess<?>) lhs);
    }
    return null;
  }
}

