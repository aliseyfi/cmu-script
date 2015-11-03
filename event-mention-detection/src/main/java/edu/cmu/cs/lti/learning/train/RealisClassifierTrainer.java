package edu.cmu.cs.lti.learning.train;

import edu.cmu.cs.lti.emd.annotators.classification.RealisFeatureExtractor;
import edu.cmu.cs.lti.learning.model.ClassAlphabet;
import edu.cmu.cs.lti.learning.model.FeatureAlphabet;
import edu.cmu.cs.lti.learning.training.WekaBasedTrainer;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TIntDoubleMap;
import org.apache.uima.UIMAException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.javatuples.Pair;
import weka.classifiers.Classifier;
import weka.classifiers.functions.LibLINEAR;
import weka.core.OptionHandler;

import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created with IntelliJ IDEA.
 * Date: 9/11/15
 * Time: 12:52 PM
 *
 * @author Zhengzhong Liu
 */
public class RealisClassifierTrainer extends WekaBasedTrainer {
    private TypeSystemDescription typeSystemDescription;
    private CollectionReaderDescription reader;
    private Configuration config;

    private List<String> classifierNames;

    public RealisClassifierTrainer(TypeSystemDescription typeSystemDescription, CollectionReaderDescription reader,
                                   Configuration config) {
        this.typeSystemDescription = typeSystemDescription;
        this.reader = reader;
        this.config = config;
    }

    @Override
    protected Map<String, Classifier> getClassifiers() throws Exception {
        Map<String, Classifier> classifiers = new HashMap<String, Classifier>();
        classifierNames = new ArrayList<String>();
        classifiers.put("lib-linear", getClassifiers(new LibLINEAR(), "-S", "0", "-C", "1.0", "-E", "0.0001", "-B",
                "1.0", "-Z"));
//        classifiers.put("svm-linear", getClassifiers(new LibSVM(), "-K", "0"));
//        classifiers.put("svm-poly", getClassifiers(new LibSVM(), "-K", "1"));
        classifierNames.addAll(classifiers.keySet());
        return classifiers;
    }

    private Classifier getClassifiers(Classifier cls, String... args) throws Exception {
        if (!(cls instanceof OptionHandler)) {
            return cls;
        }
        OptionHandler clsWithOptions = (OptionHandler) cls;
        clsWithOptions.setOptions(args);
        return (Classifier) clsWithOptions;
    }

    @Override
    protected void getFeatures(List<Pair<TIntDoubleMap, String>> instances, FeatureAlphabet alphabet, ClassAlphabet
            classAlphabet) {
        try {
            RealisFeatureExtractor.getFeatures(reader, typeSystemDescription, instances, alphabet, classAlphabet,
                    config);
        } catch (UIMAException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (InvocationTargetException e) {
            e.printStackTrace();
        } catch (InstantiationException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (NoSuchMethodException e) {
            e.printStackTrace();
        }
    }

    public List<String> getClassifierNames() {
        return classifierNames;
    }
}
