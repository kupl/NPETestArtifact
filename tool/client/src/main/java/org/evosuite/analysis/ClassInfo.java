package org.evosuite.analysis;

import java.lang.reflect.Method;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;

import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtVariableAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.CtVariable;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.reference.CtVariableReference;
import spoon.reflect.visitor.filter.TypeFilter;

import org.evosuite.Properties;
import org.evosuite.analysis.controlflow.ControlFlowBuilder;
import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.staticanalysis.NullableFieldAnalyzer;

public class ClassInfo {
    private static final ClassInfo instance = new ClassInfo();

    private HashSet<String> publicMethodSet = new HashSet<>();
    private HashSet<String> privateMethodSet = new HashSet<>();
    private HashSet<String> publicConstrSet = new HashSet<>();
    private HashSet<String> privateConstrSet = new HashSet<>();

    private HashMap<String, CtConstructor<?>> sigConstrMap = new HashMap<>();
    private HashMap<String, CtMethod<?>> sigMethodMap = new HashMap<>();

    private Collection<CtFieldReference<?>> fieldInfo = new HashSet<>();
    private HashMap<CtFieldReference<?>, String> fieldsTyp = new HashMap<>();

    private HashSet<CtLiteral<?>> literalsInClass = new HashSet<>();

    private HashSet<String> targetMethods = new HashSet<>();
    private HashSet<String> allMethods = new HashSet<>();

    private HashMap<String, ControlFlowGraph> constrGraphMap = new HashMap<>();
    private HashMap<String, ControlFlowGraph> methodGraphMap = new HashMap<>();

    private int numTotalPaths;

    private CtClass<?> codeClass;

    public CtClass<?> getCodeClass() {
        return codeClass;
    }

    public int getNumTotalPaths() {
        return numTotalPaths;
    }
    
    public static ClassInfo getInstance() {
        return instance;
    }

    private String currentMethod;

    public String getCurrentMethod() {
        return currentMethod;
    }

    public CtClass<?> getCtClass() {
        return codeClass;
    }

    public void setCurrentMethod(String methodKey) {
        currentMethod = methodKey;
    }

    public HashSet<String> getPublicMethods() {
        return publicMethodSet;
    }
    public HashSet<String> getPrivateMethods() {
        return privateMethodSet;
    }
    
    public HashSet<String> getPublicConstructors() {
        return publicConstrSet;
    }

    public HashSet<String> getPrivateConstructors() {
        return privateConstrSet;
    }

    public HashSet<String> getAllMethods() {
        return allMethods;
    }

    public HashSet<String> getTargetMethods() {
        return targetMethods;
    }


    public void addTargetMethods(String methodKey) {
        targetMethods.add(methodKey);
    }

    public HashSet<String> getAllConstrs() {
        HashSet<String> tmpSet = new HashSet<>();
        tmpSet.addAll(publicConstrSet);
        tmpSet.addAll(privateConstrSet);

        return tmpSet;
    }

    public HashSet<String> getAlls() {
        HashSet<String> tmpSet = new HashSet<>();

        tmpSet.addAll(getAllMethods());
        tmpSet.addAll(getAllConstrs());

        return tmpSet;
    }

    public CtElement getMethodConstr(String sig) {
        if (sigConstrMap.containsKey(sig)) return getConstrBySig(sig);
        return getMethodBySig(sig);
    }

    public CtConstructor<?> getConstrBySig(String constrSig) {
        if (!sigConstrMap.containsKey(constrSig)) return null;

        return sigConstrMap.get(constrSig);
    }
    
    public CtMethod<?> getMethodBySig(String methodSig) {
        if (!sigMethodMap.containsKey(methodSig)) return null;

        return sigMethodMap.get(methodSig);
    }

    public String getFieldType(CtFieldReference<?> field) {
        if (!fieldsTyp.containsKey(field)) return null;

        return fieldsTyp.get(field);
    }

    public Collection<CtFieldReference<?>> getFieldInfo() {
        return fieldInfo;
    }
    
    public void setup(CtClass<?> classCode) {
        codeClass = classCode;

    }   

    public void setClassFields() {
        fieldInfo = codeClass.getAllFields();

        if (fieldInfo.size() == 0) return;

        for (CtFieldReference<?> field : fieldInfo) {
            fieldsTyp.put(field, field.getType().toString());
        }
    }
    
    
    public void updateLiterals(HashSet<CtLiteral<?>> literals) {
        literalsInClass.addAll(literals);
    }

    public HashSet<CtLiteral<?>> getLiterals() {
        return literalsInClass;
    }

    public void initSet() {
        List<CtMethod<?>> methodList = codeClass.getElements(new TypeFilter<>(CtMethod.class));
        List<CtConstructor<?>> constrList = codeClass.getElements(new TypeFilter<>(CtConstructor.class));
        
        for (CtConstructor<?> constr : constrList) {
            if (constr.getParent() instanceof CtInterface) continue;

            String constrSig = constr.getSignature();
            sigConstrMap.put(constrSig, constr);
            if (constr.isPrivate()) {
                privateConstrSet.add(constrSig);

            } else {
                publicConstrSet.add(constrSig);
            }
        }

        for (CtMethod<?> method : methodList) {
            if (method.getParent() instanceof CtInterface) continue;

            String methodSig = method.getSignature();

            String methodRef = method.getDeclaringType().getReference().toString();

            if (methodRef.equals(Properties.TARGET_CLASS)) {
                methodSig = methodRef + "." + method.getSignature();
            } else {
                methodSig = Properties.TARGET_CLASS + "$" + method.getDeclaringType().getReference().getSimpleName() + "." + method.getSignature();
            }
            
            sigMethodMap.put(methodSig, method);
            
            if (method.isPrivate() || method.isProtected()) {
                privateMethodSet.add(methodSig);
            } else if (method.isPublic()) {
                publicMethodSet.add(methodSig);
            }

            allMethods.add(methodSig);
            // addTargetMethods(methodSig);
        }
    }

    public void buildCfgs() {
        ControlFlowGraph graph;
        
        for (String constrKey : sigConstrMap.keySet()) {
            CtConstructor<?> constr = sigConstrMap.get(constrKey);

            if (constr == null) continue;

            try {
                graph = GraphManager.getInstance().buildCfg(constr.getBody());
            } catch (Exception e) {
                continue;
            }

            constrGraphMap.put(constrKey, graph);            
        }

        for (String methodKey : sigMethodMap.keySet()) {
            CtMethod<?> method = sigMethodMap.get(methodKey);

            if (method == null) continue;

            // LoggingUtils.getEvoLogger().info("BUILD CFG for " + methodKey);
            try {
                graph = GraphManager.getInstance().buildCfg(method.getBody());
            } catch (Exception e) {
                LoggingUtils.getEvoLogger().info("ClassInfo: buildCfgs - GRAPH IS NULL");
                continue;
            }

            // LoggingUtils.getEvoLogger().info(graph.toGraphVisText());

            methodGraphMap.put(methodKey, graph);
        }
    }

    public ControlFlowGraph getGraphBySig(String sig) {
        if (constrGraphMap.containsKey(sig)) return constrGraphMap.get(sig);
        else if (methodGraphMap.containsKey(sig)) return methodGraphMap.get(sig);

        return null;
    }

    private HashSet<String> usingMethod = new HashSet<>();

    public boolean isTargetMethod (String sig) {
        if (usingMethod.contains(sig)) return true;

        for (String m : targetMethods) {
            if (sig.contains(m) || m.contains(sig)) {
                usingMethod.add(m);
                return true;
            }
        }

        return false;
    }

    public boolean isTargetMethod (Method method) {
        String sig = method.toString();

        if (usingMethod.contains(sig)) return true;

        for (String m : targetMethods) {
            if (sig.contains(m) || m.contains(sig)) {
                usingMethod.add(sig);
                usingMethod.add(m);
                return true;
            }

            CtMethod<?> ctm = getMethodBySig(m);            
            
            if (ctm.getParameters().size() == method.getParameterCount()) {
                if (sig.contains(m) || m.contains(sig)) {
                    usingMethod.add(sig);
                    usingMethod.add(m);
                    return true;
                } else {
                    if (m.contains(method.getName())) {
                        boolean check = false;
                        for (int i = 0; i < ctm.getParameters().size(); i++) {
                            if (!method.getGenericParameterTypes()[i].getTypeName().contains(ctm.getParameters().get(i).getType().getQualifiedName())) {
                                check = true;
                                break;
                            }
                        }

                        if (!check) {
                            usingMethod.add(sig);
                            usingMethod.add(m);
                            return true;
                        }
                    }
                }                
            }
        }

        return false;
    }
    
    public String getMethodKey (String sig) {
        for (String m : allMethods) {
            if (sig.contains(m) || m.contains(sig)) {
                return m;
            }
        }

        return null;
    }
    
    public String getExecutedMethodKey (String sig) {
        String[] tmpStr = sig.split("\\(");

        for (String m : allMethods) {
            if (tmpStr[0].contains(m) || m.contains(tmpStr[0])) {
                if (tmpStr[1].startsWith("L")) {
                    tmpStr[1].replaceAll("\\/", ".");
                    tmpStr[1].replace("\\;)", ")");
                    tmpStr[1].replace("\\;", ",");
                    String[] tmpStr2 = tmpStr[1].split("\\)");

                    if (m.equals(tmpStr2[0]+")")) return m;
                } 

                String[] tmpStr2 = tmpStr[1].split("\\;");
                String[] tmpStr3 = m.split("\\,");
                if (tmpStr2.length == tmpStr3.length + 1)
                    return m;
            }
        }

        return null;
    }

    
    public boolean isInOurMethods (String sig) {
        if (sig == null || usingMethod.contains(sig)) return true;

        for (String m : allMethods) {
            if (sig.contains(m) || m.contains(sig)) {
                usingMethod.add(m);
                usingMethod.add(sig);
                return true;
            }
        }

        return false;
    }

    HashMap<String, HashSet<Integer>> npeLineInfo = new HashMap<>();
    HashMap<String, HashSet<Integer>> foundNpeLineInfo = new HashMap<>();

    public HashSet<Integer> getFoundNPELineInfo(String key) {
        return foundNpeLineInfo.getOrDefault(key, new HashSet<>());
    }
    

    public void updateFoundNPELineInfo (String key, int line) {
        if (!foundNpeLineInfo.containsKey(key)) {
            foundNpeLineInfo.put(key, new HashSet<>());
        }
        foundNpeLineInfo.get(key).add(line);

        // MethodInfo.getInstance().updateScore(key);
    }


    public HashSet<Integer> getNPELineInfo (String key) {
        return npeLineInfo.getOrDefault(key, new HashSet<>());
    }

    public void updateNPELineInfo (String key, int line) {
        if (!npeLineInfo.containsKey(key)) {
            npeLineInfo.put(key, new HashSet<>());
        }
        npeLineInfo.get(key).add(line);

        // MethodInfo.getInstance().updateScore(key);
    }

    HashSet<CtField<?>> nullableFields = new HashSet<>();

    public boolean isNonNullFields(CtVariable<?> exp) {
        if (exp instanceof CtField)
            return !nullableFields.contains((CtField<?>)exp);

        return false;
    }

    // public void updateNullableFields(CtField<?> field) {
    //     nullableFields.add(field);
    // }
    
    public void updateNullableFields(Set<CtField<?>> field) {
        nullableFields.addAll(field);
    }
    
    private Map<String, Float> normalizedScoreMap = new HashMap<>();

    private Map<String, Float> overallScoreMap = new HashMap<>();
    
    private WeightedCollection<String> wc;

    private boolean updated = false;

    
    private void updateWeightedCollection() {
        Map<String, Float> scoreMap = new HashMap<>();

        for (String m : targetMethods) {
            scoreMap.put(m, MethodInfo.getInstance().getScore(m));
        }
            
        float max = scoreMap.values().stream().reduce(Float::max).orElse(-1f);
        float min = scoreMap.values().stream().reduce(Float::min).orElse(-1f);

        for (Map.Entry<String, Float> entry : scoreMap.entrySet()) {
            String m = entry.getKey();
            float newScore = max == min ? 0f : (entry.getValue() - min) / (max - min);
            normalizedScoreMap.put(entry.getKey(), newScore);
            overallScoreMap.put(m, newScore);
        }

        updated = true;

        wc = new WeightedCollection<>(overallScoreMap);
    }

    public String choose() {
        return wc.next();
    }

    public String chooseTargetMethod() {
        if (!updated)
            updateWeightedCollection();
        
        return choose();
    }
}
