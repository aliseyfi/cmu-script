package edu.cmu.cs.lti.learning.decoding;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import edu.cmu.cs.lti.learning.feature.extractor.ChainFeatureExtractor;
import edu.cmu.cs.lti.learning.model.*;
import edu.cmu.cs.lti.learning.utils.CubicLagrangian;
import edu.cmu.cs.lti.learning.utils.DummyCubicLagrangian;
import gnu.trove.map.TIntObjectMap;
import org.apache.commons.lang3.mutable.MutableInt;
import org.apache.commons.lang3.tuple.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * Date: 8/20/15
 * Time: 10:41 PM
 *
 * @author Zhengzhong Liu
 */
public class ViterbiDecoder extends SequenceDecoder {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private SequenceSolution solution;

    private GraphFeatureVector bestVector;

    private FeatureVector[] bestVectorAtEachIndex;

    private int kBest;

    private CubicLagrangian dummyLagrangian = new DummyCubicLagrangian();

    private ArrayListMultimap<Integer, Integer> constraints;

    public ViterbiDecoder(FeatureAlphabet featureAlphabet, ClassAlphabet classAlphabet) {
        this(featureAlphabet, classAlphabet, false, ArrayListMultimap.create());
    }

    public ViterbiDecoder(FeatureAlphabet featureAlphabet, ClassAlphabet classAlphabet,
                          ArrayListMultimap<Integer, Integer> constraints) {
        this(featureAlphabet, classAlphabet, false, constraints);
    }

    public ViterbiDecoder(FeatureAlphabet featureAlphabet, ClassAlphabet classAlphabet, boolean binaryFeature,
                          ArrayListMultimap<Integer, Integer> constraints) {
        this(featureAlphabet, classAlphabet, binaryFeature, 1, constraints);
    }

    public ViterbiDecoder(FeatureAlphabet featureAlphabet, ClassAlphabet classAlphabet, boolean binaryFeature,
                          int kBest, ArrayListMultimap<Integer, Integer> constraints) {
        super(featureAlphabet, classAlphabet, binaryFeature);
        this.kBest = kBest;
        this.constraints = constraints;
    }

    private FeatureVector newFeatureVector() {
        return new RealValueHashFeatureVector(featureAlphabet);
    }

    private GraphFeatureVector newGraphFeatureVector() {
        return new GraphFeatureVector(classAlphabet, featureAlphabet);
    }

    @Override
    public void decode(ChainFeatureExtractor extractor, GraphWeightVector weightVector, int sequenceLength,
                       CubicLagrangian u, CubicLagrangian v,
                       TIntObjectMap<Pair<FeatureVector, HashBasedTable<Integer, Integer, FeatureVector>>> featureCache,
                       boolean useAverage) {
        solution = new SequenceSolution(classAlphabet, sequenceLength, kBest);

        final GraphFeatureVector[] currentFeatureVectors = new GraphFeatureVector[classAlphabet.size()];
        final GraphFeatureVector[] previousColFeatureVectors = new GraphFeatureVector[classAlphabet.size()];

        final FeatureVector[][] featureAtEachIndex = new FeatureVector[sequenceLength + 1][classAlphabet.size()];

        for (int i = 0; i < currentFeatureVectors.length; i++) {
            currentFeatureVectors[i] = newGraphFeatureVector();
        }

//        List<Double> bestScores = new ArrayList<>();

        for (; !solution.finished(); solution.advance()) {
            int sequenceIndex = solution.getCurrentPosition();
            if (sequenceIndex < 0) {
                continue;
            }

            // Feature vector to be extracted or loaded from cache.
            final FeatureVector nodeFeature;
            final HashBasedTable<Integer, Integer, FeatureVector> edgeFeatures;

            Pair<FeatureVector, HashBasedTable<Integer, Integer, FeatureVector>> allBaseFeatures = null;
            if (featureCache != null) {
                allBaseFeatures = featureCache.get(sequenceIndex);
            }

            // The extraction part is not parallelized.
            if (allBaseFeatures == null) {
                nodeFeature = newFeatureVector();
                edgeFeatures = HashBasedTable.create();
                // If features are not found in the cache, we extract them here.
                extractor.extract(sequenceIndex, nodeFeature, edgeFeatures);
                if (featureCache != null) {
                    featureCache.put(sequenceIndex, Pair.of(nodeFeature, edgeFeatures));
                }
            } else {
                nodeFeature = allBaseFeatures.getLeft();
                edgeFeatures = allBaseFeatures.getRight();
            }

            // Before move on to calculate the features of current index, copy the vector of the previous column,
            // which are all candidates for the final feature of the prediction.
            System.arraycopy(currentFeatureVectors, 0, previousColFeatureVectors, 0, previousColFeatureVectors.length);

            for (int i = 0; i < currentFeatureVectors.length; i++) {
                currentFeatureVectors[i] = newGraphFeatureVector();
            }

            logger.debug("========== Current index is : " + sequenceIndex + " ===========");

            // Fill up lattice score for each of class in the current column.
            solution.getCurrentPossibleClassIndices().parallel().forEach(classIndex -> {
                double lagrangianPenalty = solution.isRightLimit() ? 0 :
                        u.getSumOverJVariable(sequenceIndex, classIndex)
                                - getConstraintSumI(constraints, u, sequenceIndex, classIndex)
                                + v.getSumOverIVariable(sequenceIndex, classIndex)
                                - getConstraintSumJ(constraints, v, sequenceIndex, classIndex);

                double newNodeScore = lagrangianPenalty;

                double rawScore = useAverage ?
                        weightVector.dotProd(nodeFeature, classAlphabet.getClassName(classIndex)) :
                        weightVector.dotProd(nodeFeature, classIndex);

                logger.debug(String.format("Class %s have score %.4f",
                        classAlphabet.getClassName(classIndex), rawScore));

                newNodeScore += rawScore;

                int index = solution.getCurrentPosition();
//                if (bestScores.size() == index) {
//                    bestScores.add(newNodeScore);
//                } else {
//                    if (newNodeScore > bestScores.get(index)) {
//                        bestScores.set(index, newNodeScore);
//                    }
//                }

                MutableInt argmaxPreviousState = new MutableInt(-1);

                // Check which previous state gives the best score.
                //TODO delete this later
                double finalNewNodeScore = newNodeScore;
                solution.getPreviousPossibleClassIndices().forEach(prevState -> {
                    for (SequenceSolution.LatticeCell previousBest : solution.getPreviousBests(prevState)) {
                        double newEdgeScore = 0;
                        if (edgeFeatures.contains(prevState, classIndex)) {
                            FeatureVector edgeFeature = edgeFeatures.get(prevState, classIndex);
                            newEdgeScore = useAverage ? weightVector.dotProdAver(edgeFeature, classIndex, prevState)
                                    : weightVector.dotProd(edgeFeature, classIndex, prevState);
                        }

                        int addResult = solution.scoreNewEdge(classIndex, previousBest, newEdgeScore,
                                finalNewNodeScore);
                        if (addResult == 1) {
                            // The new score is the best.
                            argmaxPreviousState.setValue(prevState);
                        } else if (addResult == -1) {
                            // The new score is worse than the worst, i.e. rejected by the heap. We don't
                            // need to check any scores that is worse than this.
                            break;
                        }
                    }
                });

                // Add feature vector from previous state, also added new features of current state.
                int bestPrev = argmaxPreviousState.getValue();

                // Adding features for the new cell.
                currentFeatureVectors[classIndex].extend(nodeFeature, classIndex);
                // Taking features from previous best cell.
                currentFeatureVectors[classIndex].extend(previousColFeatureVectors[bestPrev]);

                featureAtEachIndex[sequenceIndex][classIndex] = nodeFeature;

                // Adding features for the edge.
                if (edgeFeatures.contains(bestPrev, classIndex)) {
                    currentFeatureVectors[classIndex].extend(edgeFeatures.get(bestPrev, classIndex), classIndex,
                            bestPrev);
                }
            });
        }

//        logger.debug("Score at each position is " + bestScores);

        solution.backTrace();

        // Since we remembered the feature vector we calculated at each place, we can use the final solution to get it.
        bestVectorAtEachIndex = new FeatureVector[solution.getSequenceLength()];
        for (int i = 0; i < solution.getSequenceLength(); i++) {
            int tag = solution.getClassAt(i);
            bestVectorAtEachIndex[i] = featureAtEachIndex[i][tag];
        }

        // TODO: we only need to keep either bestVectorAtEachIndex or currentFeatureVectors, but we need to make sure
        // the implementation is correct for both.

        bestVector = currentFeatureVectors[classAlphabet.getOutsideClassIndex()];
    }

    private double getConstraintSumJ(ArrayListMultimap<Integer, Integer> allowedCorefs, CubicLagrangian l,
                                     int decodingIndex, int decodingType) {
        double constraintSum = 0;

        for (int allowedType : allowedCorefs.get(decodingType)) {
            constraintSum += l.getSumOverJVariable(decodingIndex, allowedType);
        }
        return constraintSum;
    }

    private double getConstraintSumI(ArrayListMultimap<Integer, Integer> allowedCorefs, CubicLagrangian l,
                                     int decodingIndex, int decodingType) {
        double constraintSum = 0;
        for (int allowedType : allowedCorefs.get(decodingType)) {
            constraintSum += l.getSumOverIVariable(decodingIndex, allowedType);
        }
        return constraintSum;
    }

    @Override
    public SequenceSolution getDecodedPrediction() {
        return solution;
    }

    @Override
    public GraphFeatureVector getBestDecodingFeatures() {
        return bestVector;
    }

    public FeatureVector[] getBestVectorAtEachIndex() {
        return bestVectorAtEachIndex;
    }

    @Override
    public GraphFeatureVector getSolutionFeatures(ChainFeatureExtractor extractor, SequenceSolution solution) {
        GraphFeatureVector fv = newGraphFeatureVector();

        for (int solutionIndex = 0; solutionIndex <= solution.getSequenceLength(); solutionIndex++) {
            FeatureVector nodeFeatures = newFeatureVector();
            Table<Integer, Integer, FeatureVector> edgeFeatures = HashBasedTable.create();

            extractor.extract(solutionIndex, nodeFeatures, edgeFeatures);

            int classIndex = solution.getClassAt(solutionIndex);

            fv.extend(nodeFeatures, classIndex);

            int prevClass = solution.getClassAt(solutionIndex);

            fv.extend(nodeFeatures, prevClass, classIndex);
        }
        return fv;
    }
}
