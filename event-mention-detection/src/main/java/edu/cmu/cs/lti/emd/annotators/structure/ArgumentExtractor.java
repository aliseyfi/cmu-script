package edu.cmu.cs.lti.emd.annotators.structure;

import edu.cmu.cs.lti.script.model.SemaforConstants;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.TokenAlignmentHelper;
import edu.cmu.cs.lti.uima.util.UimaAnnotationUtils;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Quick and dirty argument extractor based on Semafor and Fanse parsers.
 *
 * @author Zhengzhong Liu
 */
public class ArgumentExtractor extends AbstractLoggingAnnotator {
    public static final String ANNOTATOR_COMPONENT_ID = ArgumentExtractor.class.getSimpleName();

    TokenAlignmentHelper helper = new TokenAlignmentHelper();

    Logger logger = LoggerFactory.getLogger(ArgumentExtractor.class.getName());

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
//        UimaConvenience.printProcessLog(aJCas, logger);

        helper.loadStanford2Fanse(aJCas);
        helper.loadFanse2Stanford(aJCas);

        Map<SemaforLabel, Pair<String, Map<String, SemaforLabel>>> semaforArguments = getSemaforArguments(aJCas);

        for (EventMention mention : JCasUtil.select(aJCas, EventMention.class)) {
            StanfordCorenlpToken headWord = UimaNlpUtils.findHeadFromTreeAnnotation(mention);

            if (headWord == null) {
                // TODO this is a temporary solution.
//                logger.debug("Found null headword for " + mention.getId() + " : " + mention.getCoveredText());
                headWord = JCasUtil.selectCovering(StanfordCorenlpToken.class, mention).get(0);
            }
            mention.setHeadWord(headWord);

            List<SemaforLabel> coveredSemaforLabel = JCasUtil.selectCovered(SemaforLabel.class, headWord);
            Map<StanfordCorenlpToken, String> semafordHeadWord2Role = new HashMap<StanfordCorenlpToken, String>();
            Map<String, SemaforLabel> semaforRoles = new HashMap<String, SemaforLabel>();
            for (SemaforLabel label : coveredSemaforLabel) {
                if (semaforArguments.containsKey(label)) {
                    Pair<String, Map<String, SemaforLabel>> frameNameAndRoles = semaforArguments.get(label);
                    semaforRoles = frameNameAndRoles.getValue1();
                    mention.setFrameName(frameNameAndRoles.getValue0());
                    for (Map.Entry<String, SemaforLabel> aSemaforArgument : semaforRoles.entrySet()) {
                        StanfordCorenlpToken argumentHead = UimaNlpUtils.findHeadFromTreeAnnotation(aSemaforArgument
                                .getValue());
                        String roleName = aSemaforArgument.getKey();
                        if (argumentHead != null) {
                            semafordHeadWord2Role.put(argumentHead, roleName);
                        }
                    }
                }
            }

            FanseToken headFanseToken = helper.getFanseToken(headWord);
            FSList childSemanticRelations = headFanseToken.getChildSemanticRelations();
            Map<StanfordCorenlpToken, String> fanseHeadWord2Role = new HashMap<StanfordCorenlpToken, String>();
            Map<String, FanseToken> fanseRoles = new HashMap<String, FanseToken>();
            if (childSemanticRelations != null) {
                for (FanseSemanticRelation childRelation : JCasUtil.select(childSemanticRelations,
                        FanseSemanticRelation.class)) {
                    FanseToken fanseChild = (FanseToken) childRelation.getChild();
                    StanfordCorenlpToken argumentHead = helper.getStanfordToken(fanseChild);
                    if (argumentHead != null) {
                        fanseHeadWord2Role.put(argumentHead, childRelation.getSemanticAnnotation());
                        fanseRoles.put(childRelation.getSemanticAnnotation(), fanseChild);
                    }
                }
            }

            // For the same mention, all arguments should be merged together.
            Set<StanfordCorenlpToken> mappedArguments = new HashSet<StanfordCorenlpToken>();

            for (Map.Entry<StanfordCorenlpToken, String> semaforRoleHead : semafordHeadWord2Role.entrySet()) {
                StanfordCorenlpToken headToken = semaforRoleHead.getKey();
                mappedArguments.add(headToken);
                String semaforRoleName = semaforRoleHead.getValue();
                SemaforLabel argumentAnnotation = semaforRoles.get(semaforRoleName);
                EventMentionArgumentLink argumentLink = new EventMentionArgumentLink(aJCas);
                EntityMention argumentEntityMention = UimaNlpUtils.createEntityMention(aJCas, argumentAnnotation
                        .getBegin(), argumentAnnotation.getEnd(), ANNOTATOR_COMPONENT_ID);
                argumentLink.setArgument(argumentEntityMention);
                argumentLink.setFrameElementName(semaforRoleName);
                if (fanseHeadWord2Role.containsKey(headToken)) {
                    argumentLink.setPropbankRoleName(fanseHeadWord2Role.get(headToken));
                }
                mention.setArguments(UimaConvenience.appendFSList(aJCas, mention.getArguments(), argumentLink,
                        EventMentionArgumentLink.class));
                UimaAnnotationUtils.finishTop(argumentLink, ANNOTATOR_COMPONENT_ID, 0, aJCas);
            }

            for (Map.Entry<StanfordCorenlpToken, String> fanseRoleHead : fanseHeadWord2Role.entrySet()) {
                StanfordCorenlpToken headToken = fanseRoleHead.getKey();
                if (!mappedArguments.contains(headToken)) {
                    String fanseRoleName = fanseRoleHead.getValue();
                    FanseToken argumentAnnotation = fanseRoles.get(fanseRoleName);
                    EventMentionArgumentLink argumentLink = new EventMentionArgumentLink((aJCas));
                    EntityMention argumentEntityMention = UimaNlpUtils.createEntityMention(aJCas, argumentAnnotation
                            .getBegin(), argumentAnnotation.getEnd(), ANNOTATOR_COMPONENT_ID);
                    argumentLink.setArgument(argumentEntityMention);
                    argumentLink.setPropbankRoleName(fanseRoleName);
                    mention.setArguments(UimaConvenience.appendFSList(aJCas, mention.getArguments(), argumentLink,
                            EventMentionArgumentLink.class));
                    UimaAnnotationUtils.finishTop(argumentLink, ANNOTATOR_COMPONENT_ID, 0, aJCas);
                }
            }
        }
    }

    private Map<SemaforLabel, Pair<String, Map<String, SemaforLabel>>> getSemaforArguments(JCas aJCas) {
        Map<SemaforLabel, Pair<String, Map<String, SemaforLabel>>> semaforArguments = new HashMap<SemaforLabel, Pair<String, Map<String, SemaforLabel>>>();

        for (SemaforAnnotationSet annotationSet : JCasUtil.select(aJCas, SemaforAnnotationSet.class)) {
            SemaforLabel targetLabel = null;
            Map<String, SemaforLabel> roleLabels = new HashMap<String, SemaforLabel>();

            for (SemaforLayer layer : JCasUtil.select(annotationSet.getLayers(), SemaforLayer.class)) {
                String layerName = layer.getName();
                if (layerName.equals(SemaforConstants.TARGET_LAYER_NAME)) {
                    for (SemaforLabel label : JCasUtil.select(layer.getLabels(), SemaforLabel.class)) {
                        targetLabel = label;
                    }
                } else if (layerName.equals(SemaforConstants.FRAME_ELEMENT_LAYER_NAME)) {
                    for (SemaforLabel label : JCasUtil.select(layer.getLabels(), SemaforLabel.class)) {
                        roleLabels.put(label.getName(), label);
                    }
                }
            }
            semaforArguments.put(targetLabel, Pair.with(annotationSet.getFrameName(), roleLabels));
        }
        return semaforArguments;
    }
}