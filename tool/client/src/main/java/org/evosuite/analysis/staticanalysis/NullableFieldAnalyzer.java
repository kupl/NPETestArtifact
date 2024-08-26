package org.evosuite.analysis.staticanalysis;

import org.evosuite.analysis.ASTUtils;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.analysis.MethodInfo;
import org.evosuite.analysis.PathBuilder;
import org.evosuite.analysis.TypeUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spoon.reflect.code.BinaryOperatorKind;
import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBinaryOperator;
import spoon.reflect.code.CtCodeElement;
import spoon.reflect.code.CtFieldAccess;
import spoon.reflect.code.CtFieldWrite;
import spoon.reflect.code.CtIf;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtReturn;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtType;
import spoon.reflect.factory.CodeFactory;
import spoon.reflect.path.CtRole;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.visitor.filter.TypeFilter;

import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.utils.LoggingUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;
import java.util.stream.Collectors;

public class NullableFieldAnalyzer {
  private static final Logger logger = LoggerFactory.getLogger(NullableFieldAnalyzer.class);
  private static final NullableFieldAnalyzer instance = new NullableFieldAnalyzer();

  public static NullableFieldAnalyzer getInstance() {
    return instance;
  }

  private final Set<CtField<?>> nonNullableFields = new HashSet<>();

  private final Map<String, Set<CtField<?>>> unInitializedFieldsMap = new HashMap<>();

  private Set<CtField<?>> nullableFields = new HashSet<>();

  public void analyze() {
    CtClass<?> targetClass = ClassInfo.getInstance().getCodeClass();

    nullableFields = findNullableFields(targetClass);
    
    this.nullableFields.addAll(nullableFields);

    ClassInfo.getInstance().updateNullableFields(this.nullableFields);

    // for (CtField<?> f : this.nullableFields) {
    //   // f.getValueByRole(CtRole.VARIABLE)
    //   ASTUtils.printAllEle(f);      
    //   LoggingUtils.getEvoLogger().info("NULLABLE FIELDS: " + f.toString() + " - " + f.getSimpleName());
    // }
  }

  private Set<CtField<?>> findNullableFields(CtType<?> ctType) {
    Set<CtField<?>> nullableFields = new HashSet<>();
    
    if (ctType.isClass()) {
      nullableFields.addAll(inferNullableFieldFromConstructors((CtClass<?>) ctType));
    }
    nullableFields.addAll(inferNullableFieldFromNullHandlingCode(ctType));
    return nullableFields;
  }

  private Set<CtField<?>> inferNullableFieldFromNullHandlingCode(CtType<?> ctType) {
    List<CtField<?>> nullableFields = ctType.filterChildren(new TypeFilter<>(CtBinaryOperator.class))
            .select((CtBinaryOperator<?> binOp) -> binOp.getKind().equals(BinaryOperatorKind.EQ)
                    || binOp.getKind().equals(BinaryOperatorKind.NE))
            .select(ASTUtils::isNullCheckingConditionForField)
            .map(ASTUtils::extractFieldAccess)
            .map((CtFieldAccess<?> fa) -> fa.getVariable().getFieldDeclaration())
          .list();

    return new HashSet<>(nullableFields);
  }

  private Set<CtField<?>> inferNullableFieldFromConstructors(CtClass<?> ctClass) {
    Set<CtField<?>> nullableFields = new HashSet<>();
    Set<? extends CtConstructor<?>> constructors = ctClass.getConstructors();
    for (CtField<?> field : ctClass.getFields()) {
      // if (TypeUtils.isPrimitive(field.getType()) || field.getDefaultExpression() != null) {
      if ((field.getDefaultExpression() != null && !field.getDefaultExpression().toString().equals("null")) || 
          TypeUtils.isPrimitive(field.getType())) {
        continue;
      }

      boolean isFieldNullable = false;

      for (CtConstructor<?> constructor : constructors) {
        if (isFieldNullable) {
          break;
        }
        isFieldNullable = getInitializedFields(constructor).contains(field);
      }

      if (isFieldNullable) {
        nullableFields.add(field);
      }
    }

    return nullableFields;
  }

  private Set<CtField<?>> getInitializedFields(CtConstructor<?> constructor) {
    List<CtFieldWrite<?>> writtenFields = constructor.filterChildren(new TypeFilter<>(CtFieldWrite.class))
            .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
            .select((CtFieldWrite<?> fw) -> {
              CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
              CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
              return (rhs instanceof CtInvocation) || 
              (rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE)));
            })
            .list();

    return writtenFields.stream()
            .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
            .collect(Collectors.toSet());
  }

  private void updateNullableFieldGetters(CtType<?> ctType, Set<CtField<?>> nullableFields) {
    if (!ctType.isClass()) {
      return;
    }

    CtClass<?> ctClass = (CtClass<?>) ctType;
    for (CtMethod<?> method : ctClass.getMethods()) {
      CtTypeReference<?> returnType = method.getType();
      if (TypeUtils.isVoidPrimitive(returnType)) {
        continue;
      }

      List<CtReturn<?>> returns = method.filterChildren(new TypeFilter<>(CtReturn.class)).list();
      boolean returnNullableField = returns.stream()
              .anyMatch(ret -> ret.getReturnedExpression() instanceof CtFieldAccess<?>
                      && ((CtFieldAccess<?>) ret.getReturnedExpression()).getVariable() != null &&
                      nullableFields.contains(((CtFieldAccess<?>) ret.getReturnedExpression()).getVariable()
                              .getFieldDeclaration()));
                              
    }

  }
  
  public Set<CtField<?>> getNullableFields() {      
    return nullableFields;
  }


  // --------------------------------------------------------------------------------------

  // Gather all fields which are initially not null
  // 1) The default value is not null when initialized
  // 2) The value is set to nonnull when calling constructor
  private void constructorCheck(String className) {
    CtConstructor<?> constr = ClassInfo.getInstance().getConstrBySig(className);
    CtType<?> ctType = (CtType<?>) constr.getParent();

    if (ctType == null) {
      return;
    }

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtConstructor<?>> constructors = ctClass.getConstructors();

      for (CtField<?> field : ctClass.getFields()) {
        if (TypeUtils.isPrimitive(field.getType())) {
          continue;
        }

        for (CtConstructor<?> constructor : constructors) {

          CtElement targetBlk = null;
          List<CtElement> tmpList = constructor.filterChildren(new TypeFilter<>(CtIf.class)).list();
          if (!tmpList.isEmpty()) {

            for (CtElement ifEle : tmpList) {
              targetBlk = ifEle;

              List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                      .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                      .select((CtFieldWrite<?> fw) -> {
                        CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                        CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                        // logger.debug(rhs.toString());
                        return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                      })
                      .list();

              writtenFields.stream()
                      .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                      .collect(Collectors.toSet());

              for (CtFieldWrite<?> fw : writtenFields) {
                nonNullableFields.add(fw.getVariable().getFieldDeclaration());
              }

            }

          } else {
            targetBlk = constructor;

            List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                    .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                    .select((CtFieldWrite<?> fw) -> {
                      CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                      CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                      return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                    })
                    .list();

            writtenFields.stream()
                    .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                    .collect(Collectors.toSet());

            for (CtFieldWrite<?> fw : writtenFields) {
              nonNullableFields.add(fw.getVariable().getFieldDeclaration());
            }     

          }      

        }

        if (field.getDefaultExpression() != null) {
          nonNullableFields.add(field);
        } 
      }
    }
  }

  
  private void constructorCheck(CtClass<?> ctType) {
    if (ctType == null) {
      return;
    }

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtConstructor<?>> constructors = ctClass.getConstructors();

      for (CtField<?> field : ctClass.getFields()) {
        if (TypeUtils.isPrimitive(field.getType())) {
          continue;
        }

        for (CtConstructor<?> constructor : constructors) {

          CtElement targetBlk = null;
          List<CtElement> tmpList = constructor.filterChildren(new TypeFilter<>(CtIf.class)).list();
          if (!tmpList.isEmpty()) {

            for (CtElement ifEle : tmpList) {
              targetBlk = ifEle;

              List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                      .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                      .select((CtFieldWrite<?> fw) -> {
                        CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                        CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                        // logger.debug(rhs.toString());
                        return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                      })
                      .list();

              writtenFields.stream()
                      .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                      .collect(Collectors.toSet());

              for (CtFieldWrite<?> fw : writtenFields) {
                nonNullableFields.add(fw.getVariable().getFieldDeclaration());
              }

            }

          } else {
            targetBlk = constructor;

            List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                    .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                    .select((CtFieldWrite<?> fw) -> {
                      CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                      CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                      return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                    })
                    .list();

            writtenFields.stream()
                    .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                    .collect(Collectors.toSet());

            for (CtFieldWrite<?> fw : writtenFields) {
              nonNullableFields.add(fw.getVariable().getFieldDeclaration());
            }     

          }      

        }

        if (field.getDefaultExpression() != null) {
          nonNullableFields.add(field);
        } 
      }
    }
  }

  public void checkConstruct(String constrSig) {
    CtConstructor<?> constr = ClassInfo.getInstance().getConstrBySig(constrSig);

    CtElement targetBlk = null;
    List<CtElement> tmpList = constr.filterChildren(new TypeFilter<>(CtIf.class)).list();

    if (!tmpList.isEmpty()) {

      for (CtElement ifEle : tmpList) {
        targetBlk = ifEle;

        List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                .select((CtFieldWrite<?> fw) -> {
                  CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                  CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                  return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                })
                .list();

        writtenFields.stream()
                .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                .collect(Collectors.toSet());

        for (CtFieldWrite<?> fw : writtenFields) {
          nonNullableFields.add(fw.getVariable().getFieldDeclaration());
        }

      }

    } else {
      targetBlk = constr;

      List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
              .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
              .select((CtFieldWrite<?> fw) -> {
                CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
              })
              .list();

      writtenFields.stream()
              .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
              .collect(Collectors.toSet());

      for (CtFieldWrite<?> fw : writtenFields) {
        nonNullableFields.add(fw.getVariable().getFieldDeclaration());
      }     
    }      
  }

  
  public void checkMethod(String constrSig) {
    CtMethod<?> constr = ClassInfo.getInstance().getMethodBySig(constrSig);

    CtElement targetBlk = null;
    List<CtElement> tmpList = constr.filterChildren(new TypeFilter<>(CtIf.class)).list();

    if (!tmpList.isEmpty()) {

      for (CtElement ifEle : tmpList) {
        targetBlk = ifEle;

        List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                .select((CtFieldWrite<?> fw) -> {
                  CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                  CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                  return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                })
                .list();

        writtenFields.stream()
                .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                .collect(Collectors.toSet());

        for (CtFieldWrite<?> fw : writtenFields) {
          nonNullableFields.add(fw.getVariable().getFieldDeclaration());
        }

      }

    } else {
      targetBlk = constr;

      List<CtFieldWrite<?>> writtenFields = targetBlk.filterChildren(new TypeFilter<>(CtFieldWrite.class))
              .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
              .select((CtFieldWrite<?> fw) -> {
                CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
              })
              .list();

      writtenFields.stream()
              .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
              .collect(Collectors.toSet());

      for (CtFieldWrite<?> fw : writtenFields) {
        nonNullableFields.add(fw.getVariable().getFieldDeclaration());
      }     
    }      
  }

  public Set<CtField<?>> getUnInitFields(String className) {
    return unInitializedFieldsMap.get(className);
    
  }

  // Gather all fields which never become null
  // 1) The initial value is not null
  // 2) There exists no methods which may set the field to null 
  //    - not directly assignes null to the given field
  //    - no external calls to the given field (except certain constructors... new HashSet..)
  private void methodCheck(String className) {
    CtConstructor<?> constr = ClassInfo.getInstance().getConstrBySig(className);
    CtType<?> ctType = (CtType<?>) constr.getParent();

    if (ctType == null) {
      return;
    }

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtMethod<?>> methods = ctClass.getAllMethods();
      Set<CtField<?>> tmpFields = new HashSet(nonNullableFields);

      for (CtField<?> field : tmpFields) {
        boolean check = false;

        for (CtMethod<?> method : methods) {

          List<CtFieldWrite<?>> writtenFields = method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                  .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                  .select((CtFieldWrite<?> fw) -> {
                    CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                    CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                    return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                  })
                  .list();
          writtenFields.stream()
                  .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                  .collect(Collectors.toSet());
          for (CtFieldWrite<?> fw : writtenFields) {
            nonNullableFields.add(fw.getVariable().getFieldDeclaration());
            
          }           
        }

        if (check) nonNullableFields.remove(field);

      }
    }
  }
  
  private void methodCheck(CtClass<?> ctType) {
    if (ctType == null) {
      return;
    }

    if (ctType.isClass()) {
      CtClass<?> ctClass = (CtClass<?>) ctType;
      Set<? extends CtMethod<?>> methods = ctClass.getAllMethods();
      Set<CtField<?>> tmpFields = new HashSet(nonNullableFields);

      for (CtField<?> field : tmpFields) {
        boolean check = false;

        for (CtMethod<?> method : methods) {

          List<CtFieldWrite<?>> writtenFields = method.filterChildren(new TypeFilter<>(CtFieldWrite.class))
                  .select((CtFieldWrite<?> fw) -> fw.getRoleInParent().equals(CtRole.ASSIGNED))
                  .select((CtFieldWrite<?> fw) -> {
                    CtAssignment<?, ?> assignment = fw.getParent(CtAssignment.class);
                    CtCodeElement rhs = assignment.getValueByRole(CtRole.ASSIGNMENT);
                    return rhs.getValueByRole(CtRole.TYPE) != null && !TypeUtils.isNull(rhs.getValueByRole(CtRole.TYPE));
                  })
                  .list();
          writtenFields.stream()
                  .map((CtFieldWrite<?> fw) -> fw.getVariable().getFieldDeclaration())
                  .collect(Collectors.toSet());
          for (CtFieldWrite<?> fw : writtenFields) {
            nonNullableFields.add(fw.getVariable().getFieldDeclaration());
            
          }           
        }

        if (check) nonNullableFields.remove(field);
      }
    }
  }

  // Return a set of fields which are initially set to non-null.
  // If the class has no method which assigns the fields to null,
  // the fields in "nonNullableFields" never becomes null.
  public Set<CtField<?>> getCheckingFieldInfo(String className) {
    constructorCheck(className);
    methodCheck(className);
      
    return nonNullableFields;
  }

  
  public Set<CtField<?>> getCheckingFieldInfo() {
    CtClass<?> codeClass = ClassInfo.getInstance().getCodeClass();
    constructorCheck(codeClass);
    methodCheck(codeClass);
      
    return nonNullableFields;
  }

  public boolean isNonNullable(CtElement ele) {
    return nonNullableFields.contains(ele);
  }

  public boolean isNonNullable(String varName) {
    return nonNullableFields.stream().filter(f -> f.getSimpleName().equals(varName)).count() > 0;
  }

  public boolean isNullable(String className, String varName) {
    return unInitializedFieldsMap.get(className).stream().filter(f -> f.getSimpleName().equals(varName)).count() > 0;
  }
}
