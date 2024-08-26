package org.evosuite.analysis.staticanalysis;

import org.evosuite.analysis.ASTUtils;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.analysis.MethodInfo;
import org.evosuite.analysis.TypeUtils;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAbstractInvocation;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtStatement;
import spoon.reflect.code.CtThrow;
import spoon.reflect.code.CtVariableRead;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class NullLiteralAnalyzer {
  private static final NullLiteralAnalyzer instance = new NullLiteralAnalyzer();

  private final Map<String, Set<String>> nullableParamMap = new HashMap<>();

  private final Map<String, Set<CtParameter<?>>> nullParamMap = new HashMap<>();

  private final Map<String, HashSet<CtLocalVariable<?>>> nullableLocalVariable = new HashMap<>();

  private final Map<String, Integer> nullableVariableAccessCounts = new HashMap<>();

  public static NullLiteralAnalyzer getInstance() {
    return instance;
  }

  public void analyze(String methodKey) {
    /* 3. Check nullable field getter call*/
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
    if (method == null || method.getBody() == null || nullableVariableAccessCounts.containsKey(methodKey)) {
      return;
    }
    
    List<CtLiteral<?>> nullRefs = ASTUtils.getAllNullLiterals(method);

    /* record nullable parameter (if null is passed to other methods) */
    for (CtLiteral<?> nullRef : nullRefs) {
      handleNullReference(method, nullRef);
    }

    /* 1. Check nullable variable access */
    int weight = 0;
    for (CtLiteral<?> nullRef : nullRefs) {
      if (nullRef.getRoleInParent().equals(CtRole.DEFAULT_EXPRESSION)) {
        CtLocalVariable<?> localVariable = nullRef.getParent(CtLocalVariable.class);
        weight += localVariable == null ? 0 : ASTUtils.countVariableAccess(method, localVariable);
      }
    }

    nullableVariableAccessCounts.put(methodKey, weight);
  }

  private void handleNullReference(CtMethod<?> method, CtLiteral<?> nullRef) {
    if (nullRef.getRoleInParent().equals(CtRole.ARGUMENT)) {
      handleNullPassingInvocation(nullRef);
    } else if (ASTUtils.isNullCheckingConditionForParameter(nullRef)) {
      handleNullCheckingForParameter(method, nullRef);
    }
  }

  private void handleNullPassingInvocation(CtLiteral<?> nullRef) {
    CtElement parent = nullRef.getParent();
    if (parent instanceof CtAbstractInvocation<?>) {
      CtAbstractInvocation<?> invocation = (CtAbstractInvocation<?>) parent;
      int index = invocation.getArguments().indexOf(nullRef);
      CtExecutableReference<?> executableRef = invocation.getExecutable();
      CtExecutable<?> executable = executableRef.getExecutableDeclaration();
      if (executable instanceof CtMethod<?> && ((CtMethod<?>) executable).isPublic()
        ) {
        //       && CtModelExt.INSTANCE.getCUTs().contains(TypeKey.of(((CtMethod<?>) executable).getDeclaringType()))) {
        // nullableParameters.add(ParameterKey.of(executable, index));
      }
    }
  }


  private void handleNullCheckingForParameter(CtMethod<?> method, CtLiteral<?> nullRef) {
    if (isNullProperlyHandled(nullRef)) {
      return;
    }

    CtExpression<?> opposite = ASTUtils.getOppositeSideOfBinOp(nullRef);
    if (opposite == null) {
      // throw new UnexpectedFailure("Contradict in traversing null checking expression in " + method.getSignature());
    }
    CtVariableReference<?> parameterReference = ((CtVariableRead<?>) opposite).getVariable();
    CtParameter<?> parameter = (CtParameter<?>) parameterReference.getDeclaration();
    int index = ASTUtils.getParameterIndex(method, parameter);
    if (index != -1) {
      // nullableParameters.add(ParameterKey.of(method, index));
    }
  }

  private boolean isNullProperlyHandled(CtLiteral<?> nullRef) {
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

  // find local variables that are nullabe (not primitive)
  public void setNullableVariable(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
    if (method == null || method.getBody() == null) {
      return;
    }
    HashSet<CtLocalVariable<?>> tmpSet = new HashSet<>();

    List<CtLocalVariable<?>> localVariableList = method.filterChildren(new TypeFilter<>(CtLocalVariable.class)).list();

    for (CtLocalVariable<?> tmpVar : localVariableList) {
      CtTypeReference<?> ctTypeReference = tmpVar.getType();
      CtType<?> typeDeclaration = ctTypeReference.getTypeDeclaration();
      if (typeDeclaration == null) {
        if (ctTypeReference.isArray()) {
          tmpSet.add(tmpVar);
        }
      } else {
        if (!TypeUtils.isPrimitive(typeDeclaration)) {
          tmpSet.add(tmpVar);
        }
      }
    }

    this.nullableLocalVariable.put(methodKey, tmpSet);
  }

  // check the existance of nullable local variables
  // if yes, return true
  public boolean hasNullableLocalVariable(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);
    if (method == null || method.getBody() == null) {
      return false;
    }

    if (!nullableLocalVariable.containsKey(methodKey)) 
      setNullableVariable(methodKey);

    return !(this.nullableLocalVariable.get(methodKey).isEmpty());
  }

  public Set<CtLocalVariable<?>> getNullableLocalVariables(String methodKey) {
    if (!nullableLocalVariable.containsKey(methodKey)) 
      setNullableVariable(methodKey);

    return nullableLocalVariable.get(methodKey);
  }

  private void setNullableParamMap(String methodKey) {
    CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

    List<CtParameter<?>> params = method.getParameters();
    Set<CtParameter<?>> tmp = new HashSet<>();

    for (CtParameter<?> param : params) {
      if (!TypeUtils.isPrimitive(param.getType())) {
        tmp.add(param);
        // tmp.add((CtVariableReference<?>) param.getSimpleName());
      }
    }

    // nullableParamMap.put(methodKey, tmp);
    nullParamMap.put(methodKey, tmp);
  }
  
  public Set<CtParameter<?>> getNullParams(String methodKey) {
    if (!nullParamMap.containsKey(methodKey)) setNullableParamMap(methodKey);

    return nullParamMap.get(methodKey);
  }


  public Set<String> getNullableParams(String methodKey) {
    if (!nullableParamMap.containsKey(methodKey)) setNullableParamMap(methodKey);

    return nullableParamMap.get(methodKey);
  }

  public int getScore(String methodKey) {
    return nullableVariableAccessCounts.getOrDefault(methodKey, 0);
  }
}
