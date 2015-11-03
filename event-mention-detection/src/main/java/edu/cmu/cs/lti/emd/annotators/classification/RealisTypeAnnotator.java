package edu.cmu.cs.lti.emd.annotators.classification;

import edu.cmu.cs.lti.collection_reader.TbfEventDataReader;
import edu.cmu.cs.lti.learning.feature.FeatureSpecParser;
import edu.cmu.cs.lti.learning.feature.sentence.extractor.SentenceFeatureExtractor;
import edu.cmu.cs.lti.learning.model.FeatureAlphabet;
import edu.cmu.cs.lti.learning.model.FeatureVector;
import edu.cmu.cs.lti.learning.model.RealValueHashFeatureVector;
import edu.cmu.cs.lti.learning.model.WekaModel;
import edu.cmu.cs.lti.script.type.EventMention;
import edu.cmu.cs.lti.script.type.StanfordCorenlpSentence;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.TokenAlignmentHelper;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import gnu.trove.map.hash.TObjectDoubleHashMap;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.javatuples.Pair;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/14/15
 * Time: 3:00 PM
 *
 * @author Zhengzhong Liu
 */
public class RealisTypeAnnotator extends AbstractLoggingAnnotator {
    public static final String PARAM_MODEL_DIRECTORY = "modelDirectory";

    public static final String PARAM_FEATURE_PACKAGE_NAME = "featurePakcageName";

    public static final String PARAM_CONFIG_PATH = "configPath";

    @ConfigurationParameter(name = PARAM_MODEL_DIRECTORY)
    File modelDirectory;

    @ConfigurationParameter(name = PARAM_CONFIG_PATH)
    File configPath;

    @ConfigurationParameter(name = PARAM_FEATURE_PACKAGE_NAME)
    String featurePackageName;

    private static SentenceFeatureExtractor extractor;

    private WekaModel model;

    private FeatureVector dummy;

    private TokenAlignmentHelper alignmentHelper = new TokenAlignmentHelper();

    private String goldTokenComponentId = TbfEventDataReader.class.getSimpleName();

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);
        logger.info("Loading models ...");
        try {
            model = new WekaModel(modelDirectory);
        } catch (Exception e) {
            throw new ResourceInitializationException(e);
        }

        Configuration config = null;
        try {
            config = new Configuration(configPath);
        } catch (IOException e) {
            throw new IllegalArgumentException("Configuration path is not correct : " + configPath.getPath());
        }

        String featureSpec = config.get("edu.cmu.cs.lti.features.realis.spec");

        FeatureSpecParser parser = new FeatureSpecParser(featurePackageName);
        Configuration realisSpec = parser.parseFeatureFunctionSpecs(featureSpec);
        try {
            extractor = new SentenceFeatureExtractor(model.getAlphabet(), config, realisSpec);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        }

        dummy = new RealValueHashFeatureVector(model.getAlphabet());
        logger.info("Model loaded");
        logger.info("Feature size is " + model.getAlphabet().getAlphabetSize());
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        extractor.initWorkspace(aJCas);
        alignmentHelper.loadWord2Stanford(aJCas, goldTokenComponentId);

        FeatureAlphabet alphabet = model.getAlphabet();

        for (StanfordCorenlpSentence sentence : JCasUtil.select(aJCas, StanfordCorenlpSentence.class)) {
            extractor.resetWorkspace(aJCas, sentence);

            for (EventMention mention : JCasUtil.selectCovered(EventMention.class, sentence)) {
                TObjectDoubleMap<String> rawFeatures = new TObjectDoubleHashMap<String>();

                FeatureVector mentionFeatures = new RealValueHashFeatureVector(alphabet);
                int head = extractor.getTokenIndex(UimaNlpUtils.findHeadFromRange(aJCas, mention.getBegin(),
                        mention.getEnd()));
                extractor.extract(head, mentionFeatures, dummy);

                for (FeatureVector.FeatureIterator iter = mentionFeatures.featureIterator(); iter.hasNext(); ) {
                    iter.next();
                    rawFeatures.put(alphabet.getFeatureNames(iter.featureIndex())[0], iter.featureValue());
                }

                // Do prediction.
                try {
                    Pair<Double, String> prediction = model.classify(rawFeatures);
                    mention.setRealisType(prediction.getValue1());
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
