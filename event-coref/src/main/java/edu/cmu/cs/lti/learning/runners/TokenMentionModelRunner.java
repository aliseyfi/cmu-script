package edu.cmu.cs.lti.learning.runners;

import edu.cmu.cs.lti.emd.annotators.CrfMentionTypeAnnotator;
import edu.cmu.cs.lti.emd.annotators.TokenBasedMentionErrorAnalyzer;
import edu.cmu.cs.lti.emd.annotators.postprocessors.MentionTypeSplitter;
import edu.cmu.cs.lti.emd.annotators.train.TokenLevelEventMentionCrfTrainer;
import edu.cmu.cs.lti.emd.pipeline.TrainingLooper;
import edu.cmu.cs.lti.learning.utils.ModelUtils;
import edu.cmu.cs.lti.model.UimaConst;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.uima.io.reader.CustomCollectionReaderFactory;
import edu.cmu.cs.lti.uima.io.reader.RandomizedXmiCollectionReader;
import edu.cmu.cs.lti.utils.Configuration;
import edu.cmu.cs.lti.utils.FileUtils;
import org.apache.commons.lang.mutable.MutableInt;
import org.apache.uima.UIMAException;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.collection.metadata.CpeDescriptorException;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created with IntelliJ IDEA.
 * Date: 12/11/16
 * Time: 9:52 PM
 *
 * @author Zhengzhong Liu
 */
public class TokenMentionModelRunner extends AbstractMentionModelRunner {
    public TokenMentionModelRunner(Configuration config, TypeSystemDescription typeSystemDescription) {
        super(config, typeSystemDescription);
    }

    public String trainSentLvType(Configuration config, CollectionReaderDescription trainingReader,
                                  CollectionReaderDescription testReader, String suffix, boolean usePaTraing,
                                  String lossType, String processOutputDir, String resultDir, File testGold,
                                  int numWorkers, boolean skipTrain, boolean skipTest)
            throws UIMAException, IOException, ClassNotFoundException, NoSuchMethodException, InstantiationException,
            IllegalAccessException, InvocationTargetException {
        logger.info("Starting training sentence level mention type model ...");

        String modelPath = ModelUtils.getTrainModelPath(eventModelDir, config, suffix, "loss=" + lossType);
        File modelFile = new File(modelPath);

        MutableInt trainingSeed = new MutableInt(config.getInt("edu.cmu.cs.lti.random.seed", 17));
        int maxIter = config.getInt("edu.cmu.cs.lti.perceptron.maxiter", 20);
        int modelOutputFreq = config.getInt("edu.cmu.cs.lti.perceptron.model.save.frequency", 1);
        boolean ignoreUnannotated = config.getBoolean("edu.cmu.cs.lti.mention.ignore.empty.sentence", false);

        String classFile = FileUtils.joinPaths(mainConfig.get("edu.cmu.cs.lti.training.working.dir"),
                "mention_types.txt");

        if (usePaTraing) {
            logger.info("Use PA with loss : " + lossType);
        }

        // Only skip training when model directory exists.
        if (skipTrain && modelFile.exists()) {
            logger.info("Skipping mention type training, taking existing models.");
        } else {
            logger.info("Model file " + modelFile + " not exists or no skipping, start training.");
            File cacheDir = new File(FileUtils.joinPaths(mainConfig.get("edu.cmu.cs.lti.training.working.dir"),
                    processOut, config.get("edu.cmu.cs.lti.mention.cache.base")));

            AnalysisEngineDescription trainingEngine = AnalysisEngineFactory.createEngineDescription(
                    TokenLevelEventMentionCrfTrainer.class, typeSystemDescription,
                    TokenLevelEventMentionCrfTrainer.PARAM_GOLD_STANDARD_VIEW_NAME, UimaConst.goldViewName,
                    TokenLevelEventMentionCrfTrainer.PARAM_CLASS_FILE, classFile,
                    TokenLevelEventMentionCrfTrainer.PARAM_CACHE_DIRECTORY, cacheDir,
                    TokenLevelEventMentionCrfTrainer.PARAM_USE_PA_UPDATE, usePaTraing,
                    TokenLevelEventMentionCrfTrainer.PARAM_LOSS_TYPE, lossType,
                    TokenLevelEventMentionCrfTrainer.PARAM_IGNORE_UNANNOTATED_SENTENCE, ignoreUnannotated
            );

            TokenLevelEventMentionCrfTrainer.setConfig(config);

            TrainingLooper mentionTypeTrainer = new TrainingLooper(modelPath, trainingReader, trainingEngine,
                    maxIter, modelOutputFreq) {
                @Override
                protected boolean loopActions() {
                    boolean modelSaved = super.loopActions();

                    if (modelSaved && testReader != null) {
                        test(modelPath + "_iter" + numIteration, "token_mention_heldout_iter" + numIteration);
                    }

                    trainingSeed.add(2);
                    logger.debug("Update the training seed to " + trainingSeed.intValue());
                    trainingReader.setAttributeValue(RandomizedXmiCollectionReader.PARAM_SEED, trainingSeed.getValue());

                    return modelSaved;
                }

                @Override
                protected void finish() throws IOException {
                    test(modelPath, "token_mention_heldout_final");
                    TokenLevelEventMentionCrfTrainer.loopStopActions();
                }

                private void test(String model, String runName) {
                    if (testReader != null) {
                        try {
                            testPlainMentionModel(config, testReader, model, suffix, runName, processOutputDir,
                                    resultDir, testGold, numWorkers, skipTest);
                        } catch (SAXException | InterruptedException | IOException | CpeDescriptorException |
                                UIMAException e) {
                            e.printStackTrace();
                        }
                    }
                }

                @Override
                protected void saveModel(File modelOutputDir) throws IOException {
                    TokenLevelEventMentionCrfTrainer.saveModels(modelOutputDir, TokenLevelEventMentionCrfTrainer
                            .MODEL_NAME);
                }
            };

            mentionTypeTrainer.runLoopPipeline();
        }

        return modelPath;
    }

    /**
     * Test the token based mention model and return the result directory as a reader
     */
    public CollectionReaderDescription testPlainMentionModel(Configuration taskConfig,
                                                             CollectionReaderDescription reader, String typeModel,
                                                             String sliceSuffix, String runName, String outputDir,
                                                             String resultDir, File gold, int numWorkers,
                                                             boolean skipTest)
            throws SAXException, UIMAException, CpeDescriptorException, IOException, InterruptedException {
        return new ModelTester(mainConfig) {
            @Override
            protected CollectionReaderDescription runModel(Configuration taskConfig, CollectionReaderDescription
                    reader, String mainDir, String baseDir) throws SAXException, UIMAException,
                    CpeDescriptorException, IOException {
                return sentenceLevelMentionTagging(taskConfig, reader, typeModel,
                        outputDir, baseDir, numWorkers, skipTest);
            }
        }.run(taskConfig, reader, typeSystemDescription, sliceSuffix, runName, outputDir, resultDir, gold);
    }

    public CollectionReaderDescription sentenceLevelMentionTagging(Configuration crfConfig,
                                                                   CollectionReaderDescription reader,
                                                                   String modelDir, String parentOutput,
                                                                   String baseOutput, int numWorkers, boolean skipTest)
            throws UIMAException {
        File outputFile = new File(parentOutput, baseOutput);

        if (skipTest && outputFile.exists()) {
            logger.info("Skipping sent level tagging because output exists.");
            return CustomCollectionReaderFactory.createXmiReader(parentOutput, baseOutput);
        } else {
            AnalysisEngineDescription sentenceLevelTagger = AnalysisEngineFactory.createEngineDescription(
                    CrfMentionTypeAnnotator.class, typeSystemDescription,
                    CrfMentionTypeAnnotator.PARAM_MODEL_DIRECTORY, modelDir
            );

            CrfMentionTypeAnnotator.setConfig(crfConfig);

            AnalysisEngineDescription mentionSplitter = AnalysisEngineFactory.createEngineDescription(
                    MentionTypeSplitter.class, typeSystemDescription
            );

            return new BasicPipeline(reader, parentOutput, baseOutput, numWorkers, sentenceLevelTagger,
                    mentionSplitter).run().getOutput();
        }
    }

    public void tokenMentionErrorAnalysis(Configuration taskConfig,
                                          CollectionReaderDescription reader, String tokenModel) throws
            SAXException, UIMAException, CpeDescriptorException, IOException {
        AnalysisEngineDescription analyzer = AnalysisEngineFactory.createEngineDescription(
                TokenBasedMentionErrorAnalyzer.class, typeSystemDescription,
                TokenBasedMentionErrorAnalyzer.PARAM_MODEL_DIRECTORY, tokenModel
        );
        TokenBasedMentionErrorAnalyzer.setConfig(taskConfig);
        new BasicPipeline(reader, 8, analyzer).run();
    }
}
