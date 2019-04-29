package edu.cmu.cs.lti.script;

import java.util.List;

/**
 * Created with IntelliJ IDEA.
 * Date: 10/23/18
 * Time: 12:11 PM
 *
 * @author Zhengzhong Liu
 */
public class Cloze {
    public static class ClozeDoc {
        public String docid;
        public List<String> sentences;
        public List<ClozeEventMention> events;
        public List<ClozeEntity> entities;
        public List<CorefCluster> eventCorefClusters;
    }

    public static class CorefCluster {
        public List<Integer> elementIds;
    }

    public static class ClozeEntity {
        public int entityId;
        public double[] entityFeatures;
        public String[] featureNames;
        public String representEntityHead;
    }

    public static class ClozeEventMention {
        public String predicate;
        public String predicatePhrase;
        public String verbForm;
        public String context;
        public int sentenceId;
        public int predicateStart;
        public int predicateEnd;
        public String frame;
        public List<ClozeArgument> arguments;
        public String eventType;

        public int eventId;

        public static class ClozeArgument {
            public String feName;
            public String dep;
            public String propbank_role;
            public String context;
            public String text;
            public String argumentPhrase;
            public String ner;
            public int entityId;

            public int argStart;
            public int argEnd;
            public boolean isImplicit;
        }
    }
}
