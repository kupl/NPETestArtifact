/*
 * Copyright (C) 2010-2018 Gordon Fraser, Andrea Arcuri and EvoSuite
 * contributors
 *
 * This file is part of EvoSuite.
 *
 * EvoSuite is free software: you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as published
 * by the Free Software Foundation, either version 3.0 of the License, or
 * (at your option) any later version.
 *
 * EvoSuite is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with EvoSuite. If not, see <http://www.gnu.org/licenses/>.
 */
package org.evosuite.strategy;

import org.evosuite.Properties;
import org.evosuite.Properties.Criterion;
import org.evosuite.analysis.ClassInfo;
import org.evosuite.analysis.MethodInfo;
import org.evosuite.coverage.TestFitnessFactory;
import org.evosuite.ga.ChromosomeFactory;
import org.evosuite.ga.FitnessFunction;
import org.evosuite.ga.archive.Archive;
import org.evosuite.ga.archive.ArchiveTestChromosomeFactory;
import org.evosuite.ga.archive.GuidedArchiveTestChromosomeFactory;
import org.evosuite.ga.metaheuristics.GeneticAlgorithm;
import org.evosuite.ga.stoppingconditions.MaxStatementsStoppingCondition;
import org.evosuite.ga.stoppingconditions.MaxTestsStoppingCondition;
import org.evosuite.ga.stoppingconditions.MaxTimeStoppingCondition;
import org.evosuite.ga.stoppingconditions.StoppingCondition;
import org.evosuite.result.TestGenerationResultBuilder;
import org.evosuite.rmi.ClientServices;
import org.evosuite.rmi.service.ClientState;
import org.evosuite.statistics.RuntimeVariable;
import org.evosuite.statistics.StatisticsSender;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testcase.execution.ExecutionTracer;
import org.evosuite.testcase.execution.TestCaseExecutor;
import org.evosuite.testcase.factories.AllMethodsTestChromosomeFactory;
import org.evosuite.testcase.factories.GuidedRandomLengthTestFactory;
import org.evosuite.testcase.factories.JUnitTestCarvedChromosomeFactory;
import org.evosuite.testcase.factories.RandomLengthTestFactory;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;
import org.evosuite.testsuite.similarity.DiversityObserver;
import org.evosuite.utils.ArrayUtil;
import org.evosuite.utils.LoggingUtils;
import org.evosuite.utils.Randomness;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

/**
 * Iteratively generate random tests. If adding the random test
 * leads to improved fitness, keep it, otherwise drop it again.
 *
 * @author gordon
 */
public class NPEGuidedStrategy extends TestGenerationStrategy {

    private static final Logger logger = LoggerFactory.getLogger(NPEGuidedStrategy.class);

    private HashSet<TestChromosome> buildInitialPopulation() {
        HashSet<TestChromosome> tcSet = new HashSet<>();

        LoggingUtils.getEvoLogger().info("BUILD INITIAL POPULATION");
            
        ChromosomeFactory<TestChromosome> defaultFactory = new GuidedRandomLengthTestFactory();

        for (int i = 0; i < ClassInfo.getInstance().getTargetMethods().size() * (Randomness.nextInt(5)+3); i++) {
            TestChromosome test = defaultFactory.getChromosome();
            ExecutionResult result = TestCaseExecutor.runTest(test.getTestCase());

            test.setLastExecutionResult(result);

            Archive.getArchiveInstance().updateArchive(test);
            tcSet.add(test);
        }

        return tcSet;
    }

    
    protected boolean isFinished(StoppingCondition<TestSuiteChromosome> stoppingCondition) {
        if (stoppingCondition.isFinished())
            return true;

        if (!(stoppingCondition instanceof MaxTimeStoppingCondition)) {
            return globalTime.isFinished();
        }

        return false;
    }
    
    @Override
    public TestSuiteChromosome generateTests() {
        Properties.ARCHIVE_TYPE = Properties.ArchiveType.NPETEST;

        LoggingUtils.getEvoLogger().info("* Using NPE guided test generation: ");

        List<TestSuiteFitnessFunction> fitnessFunctions = getFitnessFunctions();

        TestSuiteChromosome suite = new TestSuiteChromosome();
        for (TestSuiteFitnessFunction fitnessFunction : fitnessFunctions)
            suite.addFitness(fitnessFunction);

        List<TestFitnessFactory<? extends TestFitnessFunction>> goalFactories = getFitnessFactories();
        List<TestFitnessFunction> goals = new ArrayList<>();

        LoggingUtils.getEvoLogger().info("* Total number of test goals: ");
        for (TestFitnessFactory<? extends TestFitnessFunction> goalFactory : goalFactories) {
            goals.addAll(goalFactory.getCoverageGoals());
            LoggingUtils.getEvoLogger().info("  - " + goalFactory.getClass().getSimpleName().replace("CoverageFactory", "")
                    + " " + goalFactory.getCoverageGoals().size());
        }
        
        ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Total_Goals,
                goals.size());

        if (!canGenerateTestsForSUT()) {
            LoggingUtils.getEvoLogger().info("* Found no testable methods in the target class "
                    + Properties.TARGET_CLASS);
            return new TestSuiteChromosome();
        }
        ChromosomeFactory<TestChromosome> factory = new GuidedArchiveTestChromosomeFactory();

        StoppingCondition<TestSuiteChromosome> stoppingCondition = getStoppingCondition();

        int number_generations = 0;
        number_generations++;
        
        // Initialize the Suite

        for (TestChromosome test : buildInitialPopulation()) {
            ExecutionResult result = test.getLastExecutionResult();

            Collection<Throwable> exceptions = result.getAllThrownExceptions();

            if (exceptions == null || exceptions.isEmpty()) continue;

            for (Throwable t : exceptions) {
                for (StackTraceElement trace : t.getStackTrace()) {
                    String triggeredMethod = trace.getClassName() + "." + trace.getMethodName();
                    String methodKey = ClassInfo.getInstance().getMethodKey(triggeredMethod);

                    if (methodKey == null) break;

                    if (!ClassInfo.getInstance().getFoundNPELineInfo(methodKey).contains(trace.getLineNumber())) {

                        test.setTriggeredNPE(trace.getLineNumber());
    
                        test.setChanged(false);
    
                        MethodInfo.getInstance().deleteNPEPathMap(methodKey, trace.getLineNumber());
                        ClassInfo.getInstance().updateFoundNPELineInfo(methodKey, trace.getLineNumber());    
                    }
                    break;
                }
            }
            suite.addTest(test);
        }

        for (FitnessFunction<TestSuiteChromosome> fitness_function : fitnessFunctions)
            fitness_function.getFitness(suite);

        ClientServices.getInstance().getClientNode().changeState(ClientState.SEARCH);

        while (!isFinished(suite, stoppingCondition)) {
            boolean added = false;
            number_generations++;
            TestChromosome test = factory.getChromosome();

            TestSuiteChromosome clone = suite.clone();

            
            clone.addTest(test);
            for (FitnessFunction<TestSuiteChromosome> fitness_function : fitnessFunctions) {
                fitness_function.getFitness(clone);
                logger.debug("Old fitness: {}, new fitness: {}", suite.getFitness(),
                        clone.getFitness());
            }

            ExecutionResult result = clone.getTestChromosome(clone.size() - 1).getLastExecutionResult();
            
            Collection<Throwable> exceptions = result.getAllThrownExceptions();

            if (exceptions != null && !exceptions.isEmpty()) {
                for (Throwable t : exceptions) {
                    for (StackTraceElement trace : t.getStackTrace()) {
                        String triggeredMethod = trace.getClassName() + "." + trace.getMethodName();
                        String methodKey = ClassInfo.getInstance().getMethodKey(triggeredMethod);       

                        if (methodKey == null) break;
                        
                        if (!ClassInfo.getInstance().getFoundNPELineInfo(methodKey).contains(trace.getLineNumber())) {
                            
                            added = true;
        
                            MethodInfo.getInstance().deleteNPEPathMap(methodKey, trace.getLineNumber());
                            ClassInfo.getInstance().updateFoundNPELineInfo(methodKey, trace.getLineNumber());    
        
                            Archive.getArchiveInstance().updateArchive(test);

                        }
                        break;
                    }
                }
            }
            

            if (clone.compareTo(suite) < 0 || added) {
                suite = clone;
            }
            
        }
         
        LoggingUtils.getEvoLogger().info("* Search Budget:");
        LoggingUtils.getEvoLogger().info("\t- " + stoppingCondition);
        LoggingUtils.getEvoLogger().info("# Generations: " + Integer.toString(number_generations));
        LoggingUtils.getEvoLogger().info("# Mutation: " + Integer.toString(Properties.NUM_MUTATION));
        LoggingUtils.getEvoLogger().info("# Generation: " + Integer.toString(Properties.NUM_GENERATION));

        // In the GA, these statistics are sent via the SearchListener when notified about the GA completing
        // Search is finished, send statistics
        sendExecutionStatistics();

        // TODO: Check this: Fitness_Evaluations = getNumExecutedTests?
        ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Fitness_Evaluations, MaxTestsStoppingCondition.getNumExecutedTests());
        ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Generations, number_generations);        

        return suite;
    }

    
    // @Override
    // public TestSuiteChromosome generateTests() {
    //     Properties.ARCHIVE_TYPE = Properties.ArchiveType.NPETEST;

    //     LoggingUtils.getEvoLogger().info("* Using NPE guided test generation: ");

    //     List<TestSuiteFitnessFunction> fitnessFunctions = getFitnessFunctions();

    //     TestSuiteChromosome suite = new TestSuiteChromosome();
    //     for (TestSuiteFitnessFunction fitnessFunction : fitnessFunctions)
    //         suite.addFitness(fitnessFunction);

    //     List<TestFitnessFactory<? extends TestFitnessFunction>> goalFactories = getFitnessFactories();
    //     List<TestFitnessFunction> goals = new ArrayList<>();

    //     LoggingUtils.getEvoLogger().info("* Total number of test goals: ");
    //     for (TestFitnessFactory<? extends TestFitnessFunction> goalFactory : goalFactories) {
    //         goals.addAll(goalFactory.getCoverageGoals());
    //         LoggingUtils.getEvoLogger().info("  - " + goalFactory.getClass().getSimpleName().replace("CoverageFactory", "")
    //                 + " " + goalFactory.getCoverageGoals().size());
    //     }
        
    //     ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Total_Goals,
    //             goals.size());

    //     if (!canGenerateTestsForSUT()) {
    //         LoggingUtils.getEvoLogger().info("* Found no testable methods in the target class "
    //                 + Properties.TARGET_CLASS);
    //         return new TestSuiteChromosome();
    //     }
    //     ChromosomeFactory<TestChromosome> factory = new GuidedArchiveTestChromosomeFactory();

    //     StoppingCondition<TestSuiteChromosome> stoppingCondition = getStoppingCondition();

    //     int number_generations = 0;
    //     number_generations++;
        
    //     LoggingUtils.getEvoLogger().info("INITIALIZE THE POPULATION");
    //     // Initialize the Suite
    //     for (TestChromosome test : Archive.getArchiveInstance().getStoredArchives()) {
    //         ExecutionResult result = test.getLastExecutionResult();

    //         Collection<Throwable> exceptions = result.getAllThrownExceptions();

    //         if (exceptions == null || exceptions.isEmpty()) continue;

    //         for (Throwable t : exceptions) {
    //             for (StackTraceElement trace : t.getStackTrace()) {
    //                 String triggeredMethod = trace.getClassName() + "." + trace.getMethodName();
    //                 String methodKey = ClassInfo.getInstance().getMethodKey(triggeredMethod);

    //                 if (methodKey == null) break;

    //                 if (!ClassInfo.getInstance().getFoundNPELineInfo(methodKey).contains(trace.getLineNumber())) {

                        // test.setTriggeredNPE(trace.getLineNumber());
    
    
    //                     test.setChanged(false);
    
    //                     MethodInfo.getInstance().deleteNPEPathMap(methodKey, trace.getLineNumber());
    //                     ClassInfo.getInstance().updateFoundNPELineInfo(methodKey, trace.getLineNumber());    
    //                 }
    //                 break;
    //             }
    //         }
    
    //         test.setLastExecutionResult(result);
    //         Archive.getArchiveInstance().updateArchive(test);
    //         suite.addTest(test);
    //     }

    //     ClientServices.getInstance().getClientNode().changeState(ClientState.SEARCH);

    //     LoggingUtils.getEvoLogger().info("START MUTATION");
    //     while (!isFinished(suite, stoppingCondition)) {
    //         boolean added = false;
    //         number_generations++;
                        
    //         TestChromosome test = factory.getChromosome();
    //         ExecutionResult result = TestCaseExecutor.runTest(test.getTestCase());     
    
    //         test.setLastExecutionResult(result);       

    //         Collection<Throwable> exceptions = result.getAllThrownExceptions();

    //         if (exceptions == null || exceptions.isEmpty()) continue;

    //         for (Throwable t : exceptions) {
    //             for (StackTraceElement trace : t.getStackTrace()) {
    //                 String triggeredMethod = trace.getClassName() + "." + trace.getMethodName();
    //                 String methodKey = ClassInfo.getInstance().getMethodKey(triggeredMethod);       

    //                 if (methodKey == null) break;
                    
    //                 if (!ClassInfo.getInstance().getFoundNPELineInfo(methodKey).contains(trace.getLineNumber())) {

    //                     test.setTriggeredNPE(trace.getLineNumber());
    //                     test.setChanged(false);
    
    //                     MethodInfo.getInstance().deleteNPEPathMap(methodKey, trace.getLineNumber());
    //                     ClassInfo.getInstance().updateFoundNPELineInfo(methodKey, trace.getLineNumber());    

    //                 }
    //                 break;
    //             }
    //         }
    
    //         suite.addTest(test);
    //         Archive.getArchiveInstance().updateArchive(test);
    //     }
         
    //     LoggingUtils.getEvoLogger().info("* Search Budget:");
    //     LoggingUtils.getEvoLogger().info("\t- " + stoppingCondition);
    //     LoggingUtils.getEvoLogger().info("# Generations: " + Integer.toString(number_generations));
    //     LoggingUtils.getEvoLogger().info("# Mutation: " + Integer.toString(Properties.NUM_MUTATION));
    //     LoggingUtils.getEvoLogger().info("# Generation: " + Integer.toString(Properties.NUM_GENERATION));

    //     // In the GA, these statistics are sent via the SearchListener when notified about the GA completing
    //     // Search is finished, send statistics
    //     sendExecutionStatistics();

    //     // TODO: Check this: Fitness_Evaluations = getNumExecutedTests?
    //     ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Fitness_Evaluations, MaxTestsStoppingCondition.getNumExecutedTests());
    //     ClientServices.getInstance().getClientNode().trackOutputVariable(RuntimeVariable.Generations, number_generations);        

    //     return suite;
    // }
}
