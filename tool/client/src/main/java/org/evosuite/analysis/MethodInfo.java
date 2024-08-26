package org.evosuite.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Stack;

import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;

import spoon.reflect.code.CtAssignment;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtCatch;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtLiteral;
import spoon.reflect.code.CtLocalVariable;
import spoon.reflect.code.CtThisAccess;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.reference.CtFieldReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.code.CtCaseImpl;
import spoon.support.reflect.code.CtConditionalImpl;
import spoon.support.reflect.code.CtForEachImpl;
import spoon.support.reflect.code.CtForImpl;
import spoon.support.reflect.code.CtIfImpl;
import spoon.support.reflect.code.CtInvocationImpl;
import spoon.support.reflect.code.CtSwitchImpl;
import spoon.support.reflect.code.CtWhileImpl;

public class MethodInfo {
    private static final MethodInfo instance = new MethodInfo();

    private static HashMap<String, List<CtParameter<?>>> methodParamMap = new HashMap<>();

    private static HashMap<String, Float> methodScoreMap = new HashMap<>();

    private static HashMap<String, Integer> methodComplexScore = new HashMap<>();

    private static HashMap<String, HashSet<CtFieldReference<?>>> impureMethod = new HashMap<>();

    private static HashSet<String> impureMethodSet = new HashSet<>();

    private static HashMap<String, List<CtElement>> conditionElements = new HashMap<>();

    private static HashMap<String, HashSet<CtFieldReference<?>>> fieldReadMap = new HashMap<>();
    private static HashMap<String, HashSet<CtFieldReference<?>>> fieldWriteMap = new HashMap<>();

    private static HashMap<String, HashSet<Integer>> targetParam = new HashMap<>();

    private static HashMap<String, HashSet<CtFieldReference<?>>> targetField = new HashMap<>();

    private static List<String> methodNames = new ArrayList<>();

    private static HashSet<String> analyzedMethod = new HashSet<>();

    private static HashMap<String, HashSet<CtLiteral<?>>> literalsInMethod = new HashMap<>();

    private HashMap<String, Boolean> impureMethodMap = new HashMap<>();
    

    private HashMap<String, HashSet<String>> readField = new HashMap<>();
    private HashMap<String, HashSet<String>> writtenField = new HashMap<>();

    private HashMap<String, HashSet<String>> methodInvoMap = new HashMap<>();

    private HashMap<String, HashSet<Stack<ControlFlowNode>>> mayNullPaths = new HashMap<>();

    private HashMap<String, ControlFlowGraph> methodCfg = new HashMap<>();
    
    private HashMap<String, List<CtElement>> npeCandidates = new HashMap<>();

    private HashMap<String, Boolean> returnNullMap = new HashMap<>();

    private HashMap<String, HashSet<Stack<ControlFlowNode>>> returnPaths = new HashMap<>();

    private HashMap<String, HashMap<String, Boolean>> nullableMap = new HashMap<>();
    

    private HashMap<String, ControlFlowGraph> graphMap = new HashMap<>();

    private HashMap<String, HashSet<ControlFlowNode>> mayNPENode = new HashMap<>();

    public HashMap<String, Boolean> getNullableMap(String methodKey) {
        if (!nullableMap.containsKey(methodKey)) {
            nullableMap.put(methodKey, new HashMap<>());
        }

        return nullableMap.get(methodKey);        
    }

    public void updateNullableMap(String methodKey, HashMap<String, Boolean> map) {
        nullableMap.put(methodKey, map);
    }

    public void updateCfg(String methodKey, ControlFlowGraph graph) {
        methodCfg.put(methodKey, graph);
    }

    public ControlFlowGraph getCfgBySig(String methodKey) {
        if (!methodCfg.containsKey(methodKey)) {
            ControlFlowGraph graph = GraphManager.getInstance().buildMethodCfgBySig(methodKey);
            methodCfg.put(methodKey, graph);
            return graph;
        }
        
        return methodCfg.get(methodKey);
    }

    public HashSet<String> getInvoCalls(String methodKey) {
        if (!methodInvoMap.containsKey(methodKey)) return null;
        return methodInvoMap.get(methodKey);
    }

    public void updateComplexity(String methodKey, int score) {
        methodComplexScore.put(methodKey, score);
    }


    public HashSet<String> getReadFields(String methodKey) {
        if (!readField.containsKey(methodKey)) return null;
        return readField.get(methodKey);
    }

    public void updateReadFields(String methodKey, HashSet<String> readFields) {
        HashSet<String> tmp = readField.get(methodKey);
        tmp.addAll(readFields);
        readField.put(methodKey, tmp);
    }

    public void updateNPECandidates(String methodKey, List<CtElement> mayNPEs) {
        npeCandidates.put(methodKey, mayNPEs);
    }

    public List<CtElement> getNPECandidates(String methodKey) {
        return npeCandidates.get(methodKey);
    }

    public void setReturnNull(String methodKey, boolean isReturn) {
        returnNullMap.put(methodKey, isReturn);
    }

    public void setReturnPaths(String methodKey, HashSet<Stack<ControlFlowNode>> paths) {
        returnPaths.put(methodKey, paths);
    }

    public HashSet<Stack<ControlFlowNode>> getReturnPaths(String methodKey) {
        if (!returnPaths.containsKey(methodKey)) return null;

        return returnPaths.get(methodKey);
    }

    HashSet<String> analyzedReturns = new HashSet<>();

    public void updateAnalyzedReturns(String methodKey) {
        analyzedReturns.add(methodKey);
    }

    public boolean isReturnAnalyzed(String methodKey) {
        return analyzedReturns.contains(methodKey);
    }
    
    public boolean isReturnNull(String methodKey) {
        if (!returnNullMap.containsKey(methodKey)) {
            return true;
        }
        return returnNullMap.get(methodKey);
    }

    public HashSet<String> getWrittenFields(String methodKey) {
        if (!writtenField.containsKey(methodKey)) return null;
        return writtenField.get(methodKey);
    }

    public void updateWrittenFields(String methodKey, HashSet<String> writtenFields) {
        HashSet<String> tmp = writtenField.get(methodKey);
        tmp.addAll(writtenFields);
        writtenField.put(methodKey, tmp);
    }
    
    public float getScore(String methodKey) {        
        return methodScoreMap.getOrDefault(methodKey, 0.1f);
    }

    private float getNPEScore(String methodKey) {
        if (ClassInfo.getInstance().isInOurMethods(methodKey) && !methodScoreMap.containsKey(methodKey)) {
            setMethodScore(methodKey);
        }

        return getScore(methodKey);
    }

    public HashMap<String, Float> getScoreMap() {
        return methodScoreMap;
    }


    public static MethodInfo getInstance() {
        return instance;
    }

    public int chooseParams(String key) {
        HashSet<Integer> paramCandidates = targetParam.get(key);

        if (paramCandidates == null || paramCandidates.size() == 0) return 0;

        int randIndex = Randomness.nextInt(paramCandidates.size());

        for (Integer i : paramCandidates) {
            if (i == randIndex) {
                return i;
            }
        }

        return 0;
    }

    public HashSet<Stack<ControlFlowNode>> getMayNullPaths(String methodKey) {
        return mayNullPaths.get(methodKey);
    }

    public void setMayNullPaths(String methodKey, Stack<ControlFlowNode> path) {
        if (!mayNullPaths.containsKey(methodKey)) 
            mayNullPaths.put(methodKey, new HashSet<>());

        mayNullPaths.get(methodKey).add(path);
    }

    public List<String> getMethodNames() {
        return methodNames;
    }

    public boolean hasMethod(String methodSig) {
        return methodNames.contains(methodSig);
    }

    public HashMap<String, Integer> getMethodComplexMap() {
        return methodComplexScore;
    }

    public void addUsedField(String method, CtFieldReference<?> field) {
        if (!impureMethod.containsKey(method)) impureMethod.put(method, new HashSet<>());
        impureMethod.get(method).add(field);
    }

    public List<CtParameter<?>> getMethodParams (String method) {
        return methodParamMap.getOrDefault(method, new ArrayList<>());
    }
    
    public HashMap<String, List<CtParameter<?>>> getMethodParamMap() {
        return methodParamMap;
    }

    public Integer getMethodComplexScore(String method) {
        return methodComplexScore.getOrDefault(method, 0);
    }

    public HashSet<CtFieldReference<?>> getFieldRead(String ele) {
        return fieldReadMap.getOrDefault(ele, new HashSet<>());
    }

    public HashSet<CtFieldReference<?>> getFieldWrite(String ele) {
        return fieldWriteMap.getOrDefault(ele, new HashSet<>());
    }

    public void updateImpureMethodSet(String methodKey) {
        impureMethodSet.add(methodKey);
    }


    public void addFieldRead(String method, CtFieldReference<?> field) {
        if (!fieldReadMap.containsKey(method)) fieldReadMap.put(method, new HashSet<>());
        
        fieldReadMap.get(method).add(field);
    }

    public void addFieldWrite(String method, CtFieldReference<?> field) {
        if (!fieldWriteMap.containsKey(method)) fieldWriteMap.put(method, new HashSet<>());
        
        fieldWriteMap.get(method).add(field);
    }

    public HashSet<CtFieldReference<?>> getMethodFieldUsed (String targetMethod) {
        HashSet<CtFieldReference<?>> result = new HashSet<>();
        result = getFieldRead(targetMethod);
        if (result == null) return getFieldWrite(targetMethod);
        else {
            HashSet<CtFieldReference<?>> writeSet = getFieldWrite(targetMethod);            
            if (writeSet != null) result.addAll(writeSet);
        }

        return result;
    }

    public HashSet<Integer> getTargetParamIndex (String methodSig) {
        return targetParam.getOrDefault(methodSig, new HashSet<>());
        // if (targetParam.containsKey(methodSig)) return targetParam.get(methodSig);
        // else return new HashSet<Integer>();
    }

    public void addTargetParam (String methodSig, int index) {
        HashSet<Integer> tmpSet = targetParam.get(methodSig);
        if (tmpSet == null) tmpSet = new HashSet<>();

        tmpSet.add(index);
        targetParam.put(methodSig, tmpSet);
    }


    public void addTargetField (String methodSig, CtFieldReference<?> field) {
        HashSet<CtFieldReference<?>> tmpSet = targetField.get(methodSig);
        if (tmpSet == null) tmpSet = new HashSet<>();

        targetField.put(methodSig, tmpSet);
    }

    public boolean isAnalyzed (String signature) {
        return analyzedMethod.contains(signature);
    }
    
    private boolean hasInvocation(CtElement ele) {
        List<CtElement> invoStmts = new ArrayList();

        try {
            invoStmts = ele.filterChildren(new TypeFilter<>(CtInvocation.class))
                .select((CtInvocation<?> call) -> !(call instanceof CtAssignment) || !(call instanceof CtLocalVariable))
                .select((CtInvocation<?> call) -> !(call.getParent().getParent() instanceof CtCatch))
                .select((CtInvocation<?> call) -> !(call.getExecutable() == null))
                .list();

        } catch (Exception e) {
            LoggingUtils.getEvoLogger().info(e.toString());
        }

        return invoStmts.isEmpty();
    }

    private boolean isMethodCall(ControlFlowNode n) {
        if (!hasInvocation(n.getStatement())) {
            CtInvocation<?> inv = (CtInvocation<?>) n.getStatement().filterChildren(new TypeFilter<>(CtInvocation.class)).list().get(0);
            
            if (inv.getDirectChildren().get(0) instanceof CtLiteral) {
                return false;
            } else if (inv.getDirectChildren().get(0) instanceof CtThisAccess) {
                if (inv.getExecutable() == null) return false;

                return true;
            } else if (inv.toString().startsWith("java.util.")) return true;
        }

        return false;
    }

    private String getMethodName(ControlFlowNode n) {
        String result = "";
        CtInvocation<?> inv = (CtInvocation<?>) n.getStatement().filterChildren(new TypeFilter<>(CtInvocation.class)).list().get(0);

        try {
            if (inv.getExecutable().getActualMethod() == null) {
                result = inv.getExecutable().getSignature();
            } else {
                result = inv.getExecutable().getActualMethod().toString();
            }
        } catch (Exception e) {
            result = inv.getExecutable().getSignature();
        }

        return result;
    }

    public List<CtElement> getConditions(String methodSig) {
        if (conditionElements.containsKey(methodSig)) return conditionElements.get(methodSig);
        else return new ArrayList<CtElement>();
    }

    public boolean doneConditionMap(String methodSig) {
        return conditionElements.containsKey(methodSig);
    }

    public void setMethodConditions(String methodSig, List<CtElement> eles) {
        conditionElements.put(methodSig, eles);
    }
    
    public void setMethodScore(String targetMethod) {
        int complexScore = methodComplexScore.getOrDefault(targetMethod, 1);

        int numParams = getMethodParams(targetMethod).size() == 0 ? 1 : getMethodParams(targetMethod).size();

        int numNPEPath = calculateNPENumPath(targetMethod);

        methodScoreMap.put(targetMethod, (float) (complexScore * numParams * numNPEPath));   
    }

    public void setMethodParamMap(String method, List<CtParameter<?>> paramList) {
        methodParamMap.put(method, paramList);
    }

    public void addAnalyzed(String signature) {
        analyzedMethod.add(signature);
    }

    public void updateLiterals(String signature, List<CtLiteral<?>> literals) {
        HashSet<CtLiteral<?>> tmpSet = new HashSet<>(literals);
        literalsInMethod.put(signature, tmpSet);

        ClassInfo.getInstance().updateLiterals(tmpSet);
    }
    
    public boolean isPrivate(String methodKey) {
        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

        return method.isPrivate();
    }

    public boolean isProtected(String methodKey) {
        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

        return method.isProtected();
    }

    public boolean isPublic(String methodKey) {
        CtMethod<?> method = ClassInfo.getInstance().getMethodBySig(methodKey);

        return method.isPublic();
    }

    public float getMethodScore(String methodKey) {
        return methodScoreMap.get(methodKey);
    }

    public void setGraph(String sig, ControlFlowGraph graph) {
        graphMap.put(sig, graph);
    }

    public ControlFlowGraph getGraphBySig(String sig) {
        if (graphMap.containsKey(sig)) return graphMap.get(sig);

        return null;
    }

    public void updateMethodScore() {
        for (String methodSig : ClassInfo.getInstance().getAllMethods()) {
            if (!getScoreMap().containsKey(methodSig))
                setMethodScore(methodSig);
        }
        
        for (String methodSig : ClassInfo.getInstance().getAllMethods()) {
            addInnerMethodScore(methodSig);
        }
    }

    HashMap<Integer, Integer> numNPEPathMap = new HashMap<>();

    HashMap<String, HashSet<ControlFlowNode>> innerScoreMap = new HashMap<>();

    private int calculateNPENumPath(String sig) {
        int result = 0;
        if (mayNPENode == null || mayNPENode.isEmpty() || !mayNPENode.containsKey(sig)) return 0;

        for (ControlFlowNode n : mayNPENode.get(sig)) {

            if (isMethodCall(n)) {
                if (!innerScoreMap.containsKey(sig)) innerScoreMap.put(sig, new HashSet<ControlFlowNode>());
                innerScoreMap.get(sig).add(n);

            } else {
                result += numNPEPathMap.getOrDefault(n.getStatement().getPosition().getLine(), 0);
            }
            

            // result += tmpResult;
        }

        return result;
    }

    private void addInnerMethodScore(String sig) {
        if (innerScoreMap == null || !innerScoreMap.containsKey(sig)) return;

        float tmpScore = methodScoreMap.get(sig);

        for (ControlFlowNode n : innerScoreMap.get(sig)) {
            tmpScore += getNPEScore(getMethodName(n)) * numNPEPathMap.getOrDefault(n.getStatement().getPosition().getLine(), 0);
        }

        methodScoreMap.put(sig, tmpScore);
    }

    public void addMayNPENode(String sig, ControlFlowNode n, int numPaths) {
        HashSet<ControlFlowNode> tmp = new HashSet<>();
        if (mayNPENode.containsKey(sig)) tmp = mayNPENode.get(sig);
        tmp.add(n);
        mayNPENode.put(sig, tmp);
        numNPEPathMap.put(n.getStatement().getPosition().getLine(), numPaths);
    }

    public void deleteNPEPathMap(String methodKey, int line) {
        numNPEPathMap.remove(line);
        setMethodScore(methodKey);
    }

    public Integer getNumPathByNode(ControlFlowNode node) {
        return numNPEPathMap.getOrDefault(node, 0);
    }

    public HashSet<ControlFlowNode> getNPENodes(String sig) {
        return mayNPENode.get(sig);
    }

    public boolean hasNPENode(String sig) {
        return mayNPENode.containsKey(sig);
    }
}
