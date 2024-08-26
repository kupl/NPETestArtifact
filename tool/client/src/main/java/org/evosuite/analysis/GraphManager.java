package org.evosuite.analysis;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;

import org.evosuite.analysis.controlflow.BranchKind;
import org.evosuite.analysis.controlflow.ControlFlowBuilder;
import org.evosuite.analysis.controlflow.ControlFlowEdge;
import org.evosuite.analysis.controlflow.ControlFlowGraph;
import org.evosuite.analysis.controlflow.ControlFlowNode;
import org.evosuite.analysis.controlflow.NaiveExceptionControlFlowStrategy;
import org.evosuite.analysis.controlflow.NotFoundException;
import org.evosuite.utils.LoggingUtils;
import org.jgrapht.graph.DefaultDirectedGraph;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import spoon.Launcher;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.visitor.filter.TypeFilter;


public class GraphManager {

    private static final Logger logger = LoggerFactory.getLogger(GraphManager.class);

    private static final GraphManager instance = new GraphManager();

    private HashMap<String, List<ControlFlowNode>> loopMap = new HashMap<>();

    private HashMap<ControlFlowNode, ControlFlowGraph> sccMap = new HashMap<>();

    private HashSet<ControlFlowNode> reachableNodes = new HashSet<>();    

    private ControlFlowNode lastNode;
    private ControlFlowNode startNode;

    private ControlFlowGraph sccGraph;


    public static GraphManager getInstance() {
        return instance;
    }

    public HashSet<ControlFlowNode> getReachableNodes(ControlFlowGraph graph, ControlFlowNode node) {
        HashSet<ControlFlowNode> result = new HashSet<>();



        return result;
    }

    public ControlFlowGraph buildMethodCfgBySig(String methodSig) {
        CtMethod<?> targetMethod = ClassInfo.getInstance().getMethodBySig(methodSig);

        ControlFlowBuilder builder = new ControlFlowBuilder();
		ControlFlowGraph graph = builder.build(targetMethod);
        graph.simplify();

        return graph;
    }

    public ControlFlowGraph buildCfg(CtElement target) {
        ControlFlowBuilder builder = new ControlFlowBuilder();
        builder.setExceptionControlFlowStrategy(new NaiveExceptionControlFlowStrategy());
        
		ControlFlowGraph graph = builder.build(target);

        if (graph == null) LoggingUtils.getEvoLogger().info("GRAPH IS NULLL");

		graph.simplify();

        return graph;
    }

    private void buildLoopGraph(ControlFlowGraph graph) {
        if (lastNode.getId() == startNode.getId()) return;

        Set<ControlFlowEdge> outgoing = graph.outgoingEdgesOf(lastNode);

        // LoggingUtils.getEvoLogger().info("BUILD 1 - " + Integer.toString(outgoing.size()));
                
        for (ControlFlowEdge out : outgoing) {
            // LoggingUtils.getEvoLogger().info("BUILD 2 - " + Integer.toString(outgoing.size()));
            sccGraph.addVertex(out.getTargetNode());
            
        // LoggingUtils.getEvoLogger().info("BUILD 3 - " + Integer.toString(outgoing.size()));
            sccGraph.addEdge(lastNode, out.getTargetNode());

            // LoggingUtils.getEvoLogger().info("BUILD 4 - " + Integer.toString(outgoing.size()));
            lastNode = out.getTargetNode();

            buildLoopGraph(graph);
        }
    }

    public HashSet<ControlFlowNode> getReachableNodes(String methodSig, CtElement ele) throws NotFoundException {
        if (!reachableNodes.isEmpty()) reachableNodes.clear();

        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodSig);

        ControlFlowNode node = graph.findNode(ele);

        buildReachableNodes(node, graph);

        return reachableNodes;        
    }

    public HashSet<ControlFlowNode> getReachableNodes(String methodSig, ControlFlowNode node) {
        if (!reachableNodes.isEmpty()) reachableNodes.clear();

        ControlFlowGraph graph = ClassInfo.getInstance().getGraphBySig(methodSig);

        if (graph == null) return null;

        buildReachableNodes(node, graph);

        return reachableNodes;        
    }
    
	private void buildReachableNodes(ControlFlowNode startNode, ControlFlowGraph graph) {
		if (startNode.getParent() == null) {
            LoggingUtils.getEvoLogger().info("PARENT IS NULL + " + startNode.toString());
			return;
		}
        
        for (ControlFlowEdge e : graph.incomingEdgesOf(startNode)) {
            ControlFlowNode n = e.getSourceNode();

            if (reachableNodes.contains(n)) continue;

            reachableNodes.add(n);
            buildReachableNodes(n, graph);
        }
    }    

    private void computeScc(String methodSig, ControlFlowGraph graph) {
        List<ControlFlowNode> loopEntries = graph.findNodesOfKind(BranchKind.LOOPENTRY);

        loopMap.put(methodSig, loopEntries);

        if (!loopEntries.isEmpty()) {
            for (ControlFlowNode loopEntry : loopEntries) {
                startNode = loopEntry;
                LoggingUtils.getEvoLogger().info("START SCC BUILDING FOR: " + startNode.toString());

                sccGraph = new ControlFlowGraph();

				Set<ControlFlowEdge> outgoing = graph.outgoingEdgesOf(loopEntry);

                sccGraph.addVertex(loopEntry);

                LoggingUtils.getEvoLogger().info("SCC GRAPH OUTGOING PATHS");
                // update lastNode to the loop branch node
                for (ControlFlowEdge out : outgoing) {
                    lastNode = out.getTargetNode();

                    if (lastNode.getKind().compareTo(BranchKind.TRUE) == 0) {
                        sccGraph.addVertex(lastNode);
                        sccGraph.addEdge(loopEntry, lastNode);
                        break;
                    }
                }
                LoggingUtils.getEvoLogger().info("SCC GRAPH PATHS DONE ");

                buildLoopGraph(graph);

                LoggingUtils.getEvoLogger().info("SCC GRAPH TEST");
                LoggingUtils.getEvoLogger().info(sccGraph.toGraphVisText());
                LoggingUtils.getEvoLogger().info("SCC GRAPH TEST DONE");


                sccMap.put(startNode, sccGraph);
            }
        }
    }
}