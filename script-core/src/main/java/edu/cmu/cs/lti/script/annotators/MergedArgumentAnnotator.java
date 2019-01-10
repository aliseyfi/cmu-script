package edu.cmu.cs.lti.script.annotators;

import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.util.UimaAnnotationUtils;
import edu.cmu.cs.lti.uima.util.UimaConvenience;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;

import java.util.ArrayList;

/**
 * After arguments being annotated to tokens (such as ArgumentMerger), we move them to the event mentions for further
 * processing.
 *
 * @author Zhengzhong Liu
 */
public class MergedArgumentAnnotator extends AbstractLoggingAnnotator {
    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        for (EventMention mention : JCasUtil.select(aJCas, EventMention.class)) {
            StanfordCorenlpToken headWord = (StanfordCorenlpToken) mention.getHeadWord();
            FSList headArgsFS = headWord.getChildSemanticRelations();

            if (mention.getFrameName() == null) {
                mention.setFrameName(headWord.getFrameName());
            }

            if (headArgsFS != null) {
                for (SemanticRelation relation : FSCollectionFactory.create(headArgsFS, SemanticRelation.class)) {
                    EventMentionArgumentLink argumentLink = new EventMentionArgumentLink((aJCas));
                    SemanticArgument argument = relation.getChild();
                    EntityMention argumentEntityMention = UimaNlpUtils.createArgMention(aJCas, argument
                            .getBegin(), argument.getEnd(), argument.getComponentId());
                    argumentLink.setArgument(argumentEntityMention);

                    if (relation.getPropbankRoleName() != null) {
                        argumentLink.setPropbankRoleName(relation.getPropbankRoleName());
                    }

                    if (relation.getFrameElementName() != null) {
                        argumentLink.setFrameElementName(relation.getFrameElementName());
                    }
                    mention.setArguments(UimaConvenience.appendFSList(aJCas, mention.getArguments(), argumentLink,
                            EventMentionArgumentLink.class));
                    UimaAnnotationUtils.finishTop(argumentLink, relation.getComponentId(), 0, aJCas);
                }
            } else {
                // An empty argument list.
                mention.setArguments(FSCollectionFactory.createFSList(aJCas, new ArrayList<>()));
            }
        }
    }
}