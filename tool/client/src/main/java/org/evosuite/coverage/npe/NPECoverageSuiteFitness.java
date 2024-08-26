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
package org.evosuite.coverage.npe;

import org.evosuite.Properties;
import org.evosuite.coverage.MethodNameMatcher;
import org.evosuite.coverage.line.LineCoverageFactory;
import org.evosuite.coverage.line.LineCoverageTestFitness;
import org.evosuite.ga.archive.Archive;
import org.evosuite.testcase.TestChromosome;
import org.evosuite.testcase.TestFitnessFunction;
import org.evosuite.testcase.execution.ExecutionResult;
import org.evosuite.testsuite.TestSuiteChromosome;
import org.evosuite.testsuite.TestSuiteFitnessFunction;
import org.evosuite.utils.LoggingUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Exception fitness is different from the others, as we do not know a priori how
 * many exceptions could be thrown in the SUT. In other words, we cannot really
 * speak about coverage percentage here
 */
public class NPECoverageSuiteFitness extends TestSuiteFitnessFunction {

    private static final long serialVersionUID = 1565793073526627496L;

    private static final Logger logger = LoggerFactory.getLogger(NPECoverageSuiteFitness.class);

    private static int maxExceptionsCovered = 0;

    // target goals
    private final int numNPEs;
    private final Map<String, TestFitnessFunction> npeGoals = new LinkedHashMap<>();

    public NPECoverageSuiteFitness() {
        @SuppressWarnings("unused")

        List<NPECoverageTestFitness> goals = new NPECoverageFactory().getCoverageGoals();
        for (NPECoverageTestFitness goal : goals) {
            npeGoals.put(goal.getKey(), goal);
            if (Properties.TEST_ARCHIVE)
                Archive.getArchiveInstance().addTarget(goal);
        }
        this.numNPEs = npeGoals.size();
    }
    
    /**
     * {@inheritDoc}
     * <p>
     * Execute all tests and count covered branches
     */
    @Override
    public double getFitness(TestSuiteChromosome suite) {
        double fitness = 0.0;

        Map<String, Set<String>> npeExceptions = new HashMap<>();

        List<ExecutionResult> results = runTestSuite(suite);
        
        calculateNPEInfo(results, npeExceptions, this);

        if (Properties.TEST_ARCHIVE) {
            // If we are using the archive, then fitness is by definition 0
            // as all assertions already covered are in the archive
            suite.setFitness(this, 0.0);
            suite.setCoverage(this, 1.0);
            return 0.0;
        }

        int nExc = getNumExceptions(npeExceptions);

        if (nExc > maxExceptionsCovered) {
            logger.info("(Exceptions) Best individual covers " + nExc + " exceptions");
            maxExceptionsCovered = nExc;
        }

        // We cannot set a coverage here, as it does not make any sense
        // suite.setCoverage(this, 1.0);

        
        if (this.numNPEs > 0)
            suite.setCoverage(this, (double) nExc / (double) this.numNPEs);
        else
            suite.setCoverage(this, 1.0);
            
        fitness += normalize(this.numNPEs - nExc);      
        
        LoggingUtils.getEvoLogger().info("UPDATE INDIVIDUAL");

        updateIndividual(suite, fitness);
        LoggingUtils.getEvoLogger().info("UPDATE INDIVIDUAL - DONE");

        return fitness;
    }    

    public static void calculateNPEInfo(List<ExecutionResult> results,
                                              Map<String, Set<String>> npeExceptions,
                                              NPECoverageSuiteFitness contextFitness)
            throws IllegalArgumentException {

        MethodNameMatcher matcher = new MethodNameMatcher();

        if (results == null || npeExceptions == null || !npeExceptions.isEmpty()) {
            throw new IllegalArgumentException();
        }

        // for each test case
        for (ExecutionResult result : results) {
            
            if (result.hasTimeout() || result.hasTestException() || result.noThrownExceptions() || result.calledReflection()) {
                continue;
            }

            TestChromosome test = new TestChromosome();
            test.setTestCase(result.test);
            test.setLastExecutionResult(result);
            test.setChanged(false);

            //iterate on the indexes of the statements that resulted in an exception
            for (Integer i : result.getPositionsWhereExceptionsWereThrown()) {
                Throwable t = result.getExceptionThrownAtPosition(i);

                if (NPECoverageHelper.shouldSkip(result, i) || t == null) {
                    continue;
                }

                String methodIdentifier = NPECoverageHelper.getMethodIdentifier(result, i); //eg name+descriptor

                if (!matcher.methodMatches(methodIdentifier)) {
                    logger.info("Method {} does not match criteria. ", methodIdentifier);
                    continue;
                }

                // was the exception originated by a direct call on the SUT?
                boolean sutException = NPECoverageHelper.isSutException(result, i); 

                /*
                 * We only consider exceptions that were thrown by calling directly the SUT (not the other
                 * used libraries). However, this would ignore cases in which the SUT is indirectly tested
                 * through another class
                 */

                if (sutException) {
                    
                    if (t.toString().contains("NullPointerException")) {
                        boolean isTriggered = false;

                        String line = NPECoverageHelper.getLine(result, i);

                        if (!npeExceptions.containsKey(methodIdentifier)) {
                            npeExceptions.put(methodIdentifier, new HashSet<>());
                        }

                        npeExceptions.get(methodIdentifier).add(line);
    
                        NPECoverageTestFitness goal = new NPECoverageTestFitness(Properties.TARGET_CLASS, methodIdentifier, line, isTriggered);
                        
                        test.getTestCase().addCoveredGoal(goal);
                    }

                }
    
            }
        }
    }

    public static int getNumExceptions(Map<String, Set<String>> exceptions) {
        int total = 0;
        for (Set<String> exceptionSet : exceptions.values()) {
            total += exceptionSet.size();
        }
        return total;
    }

    public static int getNumClassExceptions(Map<String, Set<Class<?>>> exceptions) {
        Set<Class<?>> classExceptions = new HashSet<>();
        for (Set<Class<?>> exceptionSet : exceptions.values()) {
            classExceptions.addAll(exceptionSet);
        }
        return classExceptions.size();
    }
}
