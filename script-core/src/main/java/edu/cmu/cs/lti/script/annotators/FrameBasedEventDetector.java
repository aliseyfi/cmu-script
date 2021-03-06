package edu.cmu.cs.lti.script.annotators;

import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Table;
import edu.cmu.cs.lti.frame.FrameRelationReader;
import edu.cmu.cs.lti.frame.FrameStructure;
import edu.cmu.cs.lti.frame.UimaFrameExtractor;
import edu.cmu.cs.lti.model.Span;
import edu.cmu.cs.lti.pipeline.BasicPipeline;
import edu.cmu.cs.lti.script.type.*;
import edu.cmu.cs.lti.uima.annotator.AbstractLoggingAnnotator;
import edu.cmu.cs.lti.uima.io.IOUtils;
import edu.cmu.cs.lti.uima.io.reader.GzippedXmiCollectionReader;
import edu.cmu.cs.lti.uima.io.writer.StepBasedDirGzippedXmiWriter;
import edu.cmu.cs.lti.uima.util.EntityMentionManager;
import edu.cmu.cs.lti.uima.util.UimaAnnotationUtils;
import edu.cmu.cs.lti.uima.util.UimaNlpUtils;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_engine.AnalysisEngineDescription;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.collection.CollectionReaderDescription;
import org.apache.uima.fit.descriptor.ConfigurationParameter;
import org.apache.uima.fit.factory.AnalysisEngineFactory;
import org.apache.uima.fit.factory.CollectionReaderFactory;
import org.apache.uima.fit.factory.TypeSystemDescriptionFactory;
import org.apache.uima.fit.util.FSCollectionFactory;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.FSList;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.metadata.TypeSystemDescription;
import org.apache.xmlbeans.impl.piccolo.xml.EntityManager;
import org.jdom2.JDOMException;

import java.io.*;
import java.util.*;

/**
 * Created with IntelliJ IDEA.
 * Date: 10/25/17
 * Time: 3:58 PM
 *
 * @author Zhengzhong Liu
 */
public class FrameBasedEventDetector extends AbstractLoggingAnnotator {
    public static final String PARAM_FRAME_RELATION = "frameRelationFile";
    @ConfigurationParameter(name = PARAM_FRAME_RELATION)
    private File frameRelationFile;

    public static final String PARAM_IGNORE_BARE_FRAME = "ignoreBareFrame";
    @ConfigurationParameter(name = PARAM_IGNORE_BARE_FRAME, defaultValue = "false")
    private boolean ignoreBareFrame;

    public static final String PARAM_TAKE_ALL_FRAMES = "takeAllFrame";
    @ConfigurationParameter(name = PARAM_TAKE_ALL_FRAMES, defaultValue = "false")
    private boolean takeAllFrame;

    public static final String PARAM_IGNORE_PREP_ARG = "ignorePrepArg";
    @ConfigurationParameter(name = PARAM_IGNORE_PREP_ARG, defaultValue = "false")
    private boolean ignorePrepArg;

    private Set<String> ignoredHeadWords;
    private UimaFrameExtractor extractor;

    @Override
    public void initialize(UimaContext aContext) throws ResourceInitializationException {
        super.initialize(aContext);

        String[] ignoredVerbs = new String[]{"become", "be", "do", "have", "seem", "go", "have", "keep", "argue",
                "claim", "say", "suggest", "tell"};

        ignoredHeadWords = new HashSet<>();

        if (!takeAllFrame) {
            Collections.addAll(ignoredHeadWords, ignoredVerbs);
        }

        if (ignoreBareFrame) {
            logger.info("Frames without arguments will be ignored.");
        }

        try {
            FrameRelationReader frameReader = new FrameRelationReader(frameRelationFile.getPath());
            HashSet<String> targetFrames = new HashSet<>();
            if (!takeAllFrame) {
                InputStream frameInputStream = getClass().getResourceAsStream("/event_evoking_frames.txt");
                BufferedReader br = new BufferedReader(new InputStreamReader(frameInputStream));
                String line;
                while ((line = br.readLine()) != null) {
                    targetFrames.add(line.trim());
                }
                logger.info(String.format("Loaded %d event target frames.", targetFrames.size()));
            } else {
                logger.info("All frames will be output.");
            }
            extractor = new UimaFrameExtractor(frameReader.getFeByName(), targetFrames, true);
        } catch (IOException | JDOMException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void process(JCas aJCas) throws AnalysisEngineProcessException {
        annotateEvents(aJCas);
    }

    private void annotateEvents(JCas aJCas) {
        ArticleComponent article = JCasUtil.selectSingle(aJCas, Article.class);
        EntityMentionManager manager = new EntityMentionManager(aJCas);

//        Map<Word, EntityMention> h2Entities = UimaNlpUtils.indexEntityMentions(aJCas);
        ArrayListMultimap<Word, EntityMention> entityWordMap = ArrayListMultimap.create();
        for (EntityMention entityMention : JCasUtil.select(aJCas, EntityMention.class)) {
            for (StanfordCorenlpToken token : JCasUtil.selectCovered(StanfordCorenlpToken.class, entityMention)) {
                entityWordMap.put(token, entityMention);
            }
        }

        Table<Integer, Integer, EventMention> span2Events = UimaNlpUtils.indexEventMentions(aJCas);

        for (FrameStructure frameStructure : extractor.getTargetFrames(article)) {
            SemaforLabel predicate = frameStructure.getTarget();
            String frameName = frameStructure.getFrameName();

            if (ignoreBareFrame) {
                if (frameStructure.getFrameElements().size() == 0) {
                    // Ignoring frames without arguments for now?
                    continue;
                }
            }

            StanfordCorenlpToken predHead = UimaNlpUtils.findHeadFromStanfordAnnotation(predicate);

            if (predHead == null) {
                continue;
            } else if (ignoredHeadWords.contains(predHead.getLemma().toLowerCase())) {
                continue;
            }

            EventMention eventMention;
            if (span2Events.contains(predicate.getBegin(), predicate.getEnd())) {
                eventMention = span2Events.get(predicate.getBegin(), predicate.getEnd());
            } else {
                eventMention = new EventMention(aJCas, predicate.getBegin(), predicate.getEnd());
                eventMention.setEventType(frameName);
                UimaAnnotationUtils.finishAnnotation(eventMention, COMPONENT_ID, 0, aJCas);
//                logger.info(String.format("Creating new event [%s] with frame [%s]", eventMention.getCoveredText(), frameName));
            }

            eventMention.setFrameName(frameName);
            eventMention.setHeadWord(predHead);

            List<EventMentionArgumentLink> argumentLinks = new ArrayList<>();
            FSList existingArgsFS = eventMention.getArguments();
            if (existingArgsFS != null) {
                argumentLinks.addAll(FSCollectionFactory.create(existingArgsFS, EventMentionArgumentLink.class));
            }

            List<String> superFeNames = frameStructure.getSuperFeNames();

            Map<EntityMention, EventMentionArgumentLink> existingArgs = UimaNlpUtils.indexArgs(eventMention);

            int i = 0;
            for (SemaforLabel frameElement : frameStructure.getFrameElements()) {
                String feName = frameElement.getName();
                StanfordCorenlpToken argHead = UimaNlpUtils.findHeadFromStanfordAnnotation(frameElement);

                if (UimaNlpUtils.isPrepWord(argHead)) {
                    argHead = UimaNlpUtils.findNonPrepHeadInRange(aJCas, predHead, argHead, frameElement);
                    if (ignorePrepArg) {
                        if (UimaNlpUtils.isPrepWord(argHead)) {
                            continue;
                        }
                    }
                }

                EventMentionArgumentLink argumentLink;
                if (UimaNlpUtils.isWhWord(argHead)) {
                    argHead = (StanfordCorenlpToken) UimaNlpUtils.findWhTarget(argHead);
                    if (argHead == null) {
                        continue;
                    }
                    argumentLink = UimaNlpUtils.addEventArgument(
                            aJCas, eventMention, manager, existingArgs, argumentLinks,
                            argHead, COMPONENT_ID);

                } else {
                    argumentLink = UimaNlpUtils.addEventArgument(
                            aJCas, eventMention, manager, existingArgs, argumentLinks,
                            frameElement, argHead, COMPONENT_ID);
                }


                EntityMention argEntMention = argumentLink.getArgument();
                Entity argEnt = argEntMention.getReferingEntity();

                if (argEnt == null || argEnt.getEntityMentions().size() == 1) {
                    if (entityWordMap.containsKey(argEntMention.getHead())) {
                        List<EntityMention> coveringMentions = entityWordMap.get(argEntMention.getHead());
                        for (EntityMention coveringMention : coveringMentions) {
                            if (coveringMention == argEntMention) {
                                continue;
                            }

                            if (UimaNlpUtils.compatibleMentions(argEntMention, coveringMention)) {
                                if (argEnt != null) {
                                    argEnt.removeFromIndexes();
                                }
                                UimaNlpUtils.addToEntityCluster(aJCas, coveringMention.getReferingEntity(),
                                        Arrays.asList(argEntMention));
                                break;
                            }
                        }
                    }
                }

                String superFeName = superFeNames.get(i);
                argumentLink.setFrameElementName(feName);
                argumentLink.setSuperFrameElementRoleName(superFeName);
                i++;
            }

            eventMention.setArguments(FSCollectionFactory.createFSList(aJCas, argumentLinks));
        }

        UimaNlpUtils.cleanEntityMentionMetaData(aJCas, new ArrayList<>(JCasUtil.select(aJCas, EntityMention.class)),
                COMPONENT_ID);
    }

    public static void main(String[] argv) throws UIMAException {
        TypeSystemDescription typeSystemDescription = TypeSystemDescriptionFactory
                .createTypeSystemDescription("TypeSystem");
        String workingDir = argv[0];
        String inputDir = argv[1];
        String outputDir = argv[2];

        CollectionReaderDescription reader = CollectionReaderFactory.createReaderDescription(
                GzippedXmiCollectionReader.class, typeSystemDescription,
                GzippedXmiCollectionReader.PARAM_PARENT_INPUT_DIR_PATH, workingDir,
                GzippedXmiCollectionReader.PARAM_BASE_INPUT_DIR_NAME, inputDir,
                GzippedXmiCollectionReader.PARAM_EXTENSION, ".xmi.gz",
                GzippedXmiCollectionReader.PARAM_RECURSIVE, true
        );

        AnalysisEngineDescription detector = AnalysisEngineFactory.createEngineDescription(
                FrameBasedEventDetector.class, typeSystemDescription,
                FrameBasedEventDetector.PARAM_FRAME_RELATION, "../data/resources/fndata-1.7/frRelation.xml"
        );

        StepBasedDirGzippedXmiWriter.dirSegFunction = IOUtils::indexBasedSegFunc;

        new BasicPipeline(reader, true, true, 7, workingDir, outputDir, true, detector).run();
    }
}
