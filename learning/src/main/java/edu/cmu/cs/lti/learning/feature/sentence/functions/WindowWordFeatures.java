package edu.cmu.cs.lti.learning.feature.sentence.functions;

import edu.cmu.cs.lti.learning.feature.sentence.FeatureUtils;
import edu.cmu.cs.lti.script.type.StanfordCorenlpToken;
import edu.cmu.cs.lti.script.type.StanfordEntityMention;
import edu.cmu.cs.lti.utils.Configuration;
import gnu.trove.map.TObjectDoubleMap;
import java8.util.function.Consumer;
import java8.util.function.Function;
import java8.util.function.IntConsumer;
import java8.util.function.IntFunction;
import java8.util.stream.IntStreams;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 8/22/15
 * Time: 10:13 PM
 *
 * @author Zhengzhong Liu
 */
public class WindowWordFeatures extends SequenceFeatureWithFocus {
    private final Logger logger = LoggerFactory.getLogger(getClass());

    private int posWindowSize;
    private int lemmaWindowSize;
    private int nerWindowSize;
    private boolean useBigram;

    public WindowWordFeatures(Configuration generalConfig, Configuration featureConfig) {
        super(generalConfig, featureConfig);
        posWindowSize = getIntFromConfig(featureConfig, "PosWindowSize", -1);
        lemmaWindowSize = getIntFromConfig(featureConfig, "LemmaWindowSize", -1);
        nerWindowSize = getIntFromConfig(featureConfig, "NerWindowSize", -1);
        useBigram = getBoolFromConfig(featureConfig, "Bigram", false);
    }

    private int getIntFromConfig(Configuration config, String paramName, int defaultVal) {
        return config.getInt(this.getClass().getSimpleName() + "." + paramName, defaultVal);
    }

    private boolean getBoolFromConfig(Configuration config, String paramName, boolean defaultVal) {
        return config.getBoolean(this.getClass().getSimpleName() + "." + paramName, defaultVal);
    }

    @Override
    public void initDocumentWorkspace(JCas context) {
        // Set types to each token for easy feature extraction.
        for (StanfordEntityMention mention : JCasUtil.select(context, StanfordEntityMention.class)) {
            String entityType = mention.getEntityType();
            for (StanfordCorenlpToken token : JCasUtil.selectCovered(StanfordCorenlpToken.class, mention)) {
                token.setNerTag(entityType);
            }
        }
    }

    @Override
    public void resetWorkspace(JCas aJCas, int begin, int end) {

    }

    @Override
    public void extract(List<StanfordCorenlpToken> sequence, int focus, TObjectDoubleMap<String> features,
                        TObjectDoubleMap<String> featuresNeedForState) {
        if (posWindowSize >= 0) {
            addWindowFeatures(sequence, focus, features, new Function<StanfordCorenlpToken, String>() {
                @Override
                public String apply(StanfordCorenlpToken stanfordCorenlpToken) {
                    return stanfordCorenlpToken.getPos();
                }
            }, "Pos", posWindowSize);
            // POS conjoined with previous state, we are conservative about window size here.
            addWindowFeatures(sequence, focus, featuresNeedForState, new Function<StanfordCorenlpToken, String>() {
                @Override
                public String apply(StanfordCorenlpToken stanfordCorenlpToken) {
                    return stanfordCorenlpToken.getPos();
                }
            }, "Pos", 0);
            if (useBigram) {
                addNgramFeatureWithOffsetRange(sequence, focus, -posWindowSize, posWindowSize, "Pos",
                        new Function<StanfordCorenlpToken, String>() {
                            @Override
                            public String apply(StanfordCorenlpToken stanfordCorenlpToken) {
                                return stanfordCorenlpToken.getPos();
                            }
                        }, features, 2);
            }
        }
        if (lemmaWindowSize >= 0) {
            addWindowFeatures(sequence, focus, features, new Function<StanfordCorenlpToken, String>() {
                @Override
                public String apply(StanfordCorenlpToken stanfordCorenlpToken) {
                    return stanfordCorenlpToken.getLemma();
                }
            }, "Lemma", lemmaWindowSize);
            if (useBigram) {
                addNgramFeatureWithOffsetRange(sequence, focus, -lemmaWindowSize, lemmaWindowSize, "Lemma",
                        new Function<StanfordCorenlpToken, String>() {
                            @Override
                            public String apply(StanfordCorenlpToken stanfordCorenlpToken) {
                                return stanfordCorenlpToken.getLemma();
                            }
                        }, features, 2);
            }
        }
        if (nerWindowSize >= 0) {
            addWindowFeatures(sequence, focus, features, new Function<StanfordCorenlpToken, String>() {
                @Override
                public String apply(StanfordCorenlpToken stanfordCorenlpToken) {
                    return stanfordCorenlpToken.getNerTag();
                }
            }, "Ner", nerWindowSize);
        }
    }

    public void addWindowFeatures(List<StanfordCorenlpToken> sentence, int focus, TObjectDoubleMap<String> features,
                                  Function<StanfordCorenlpToken, String> operator, String featureType, int windowSize) {
        putWithoutOutside(features, computeWordFeature(sentence, featureType, operator, focus, 0));

        addPositionFeatureWithOffsetRange(sentence, focus, -windowSize, -1, featureType, operator, features);
        addPositionFeatureWithOffsetRange(sentence, focus, 1, windowSize, featureType, operator, features);

        addWindowFeatureWithOffsetRange(sentence, focus, 1, windowSize, featureType, operator, features);
//        addWindowFeatureWithOffsetRange(sentence, focus, 1, windowSize, featureType, operator, features);
    }

    public void addPositionFeatureWithOffsetRange(final List<StanfordCorenlpToken> sentence, final int focus,
                                                  int begin, int end, final String prefix,
                                                  final Function<StanfordCorenlpToken, String> operator,
                                                  final TObjectDoubleMap<String> features) {
        IntStreams.rangeClosed(begin, end)
                .mapToObj(new IntFunction<Pair<String, String>>() {
                    @Override
                    public Pair<String, String> apply(int offset) {
                        return computeWordFeature(sentence, prefix, operator, focus, offset);
                    }
                }).forEach(new Consumer<Pair<String, String>>() {
            @Override
            public void accept(Pair<String, String> featureTypeAndName) {
                putWithoutOutside(features, featureTypeAndName);
            }
        });
    }

    public void addWindowFeatureWithOffsetRange(final List<StanfordCorenlpToken> sentence, final int focus,
                                                int limitStart, int limitEnd, final String prefix,
                                                final Function<StanfordCorenlpToken, String> operator,
                                                final TObjectDoubleMap<String> features) {
        IntStreams.rangeClosed(limitStart, limitEnd).forEach(new IntConsumer() {
            @Override
            public void accept(final int windowLimit) {
                IntStreams.rangeClosed(1, windowLimit)
                        .mapToObj(new IntFunction<Pair<String, String>>() {
                            @Override
                            public Pair<String, String> apply(int offset) {
                                return computeWindowWordFeature(sentence, prefix, operator, windowLimit, focus, offset);
                            }
                        }).forEach(new Consumer<Pair<String, String>>() {
                    @Override
                    public void accept(Pair<String, String> featureTypeAndName) {
                        putWithoutOutside(features, featureTypeAndName);
                    }
                });

                IntStreams.rangeClosed(-windowLimit, -1)
                        .mapToObj(new IntFunction<Pair<String, String>>() {
                            @Override
                            public Pair<String, String> apply(int offset) {
                                return computeWindowWordFeature(sentence, prefix, operator, windowLimit, focus, offset);
                            }
                        }).forEach(new Consumer<Pair<String, String>>() {
                    @Override
                    public void accept(Pair<String, String> featureTypeAndName) {
                        putWithoutOutside(features, featureTypeAndName);
                    }
                });
            }
        });
    }

    public void addNgramFeatureWithOffsetRange(List<StanfordCorenlpToken> sentence, int focus, int begin, int end,
                                               String prefix, Function<StanfordCorenlpToken, String> operator,
                                               TObjectDoubleMap<String> features, int n) {
        int left = Math.min(focus + begin, -1);
        int right = Math.max(focus + end, sentence.size());

        int[] runners = new int[n];
        for (int i = 0; i < n; i++) {
            runners[i] = left + i;
            if (runners[i] > right) {
                return;
            }
        }

        String ngramPrefix = "window_" + n + "gram" + prefix;

        while (true) {
            StringBuilder sb = new StringBuilder();
            String ngramSep = "";
            for (int i = 0; i < runners.length; i++) {
                sb.append(ngramSep);
                ngramSep = "_";
                // Operate with outside will ensure we get a feature value. However, we do not allow outside here, this
                // function will simply create <outside> for tokens without NER.
                String val = operateWithOutsideLowerCase(sentence, operator, runners[i]);
                sb.append(val);
                runners[i]++;
                if (runners[i] > right) {
                    return;
                }
            }
            features.put(FeatureUtils.formatFeatureName(ngramPrefix, sb.toString()), 1);
        }
    }

    public void putWithoutOutside(TObjectDoubleMap<String> features, Pair<String, String> featureTypeAndName) {
        if (!featureTypeAndName.getValue1().equals(outsideValue)) {
            features.put(FeatureUtils.formatFeatureName(featureTypeAndName), 1);
        }
    }

    public Pair<String, String> computeWordFeature(List<StanfordCorenlpToken> sentence, String
            prefix, Function<StanfordCorenlpToken, String> operator, int focus, int offset) {
        // NOTE all word features are lowercased.
        return Pair.with(String.format("%s_offset=%d", prefix, offset), operateWithOutsideLowerCase(sentence, operator,
                focus + offset));
    }

    public Pair<String, String> computeWindowWordFeature(List<StanfordCorenlpToken> sentence, String
            prefix, Function<StanfordCorenlpToken, String> operator, int windowSize, int focus, int offset) {
        // NOTE all word features are lowercased.
        return Pair.with(String.format("%s_window=%d", prefix, windowSize), operateWithOutsideLowerCase(sentence,
                operator, focus + offset));
    }
}