package edu.cmu.lti.oaqa.baseqa.quesanal.lat;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.yaml.snakeyaml.Yaml;

import com.google.common.base.Charsets;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.baseqa.providers.ml.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.providers.ml.FeatureConstructorProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.nlp.Focus;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class LexicalAnswerTypeTrainer extends JCasAnnotator_ImplBase {

  private FeatureConstructorProvider featureConstructor;

  private ClassifierProvider classifier;

  private Map<String, List<String>> qid2labels;

  private String cvPredictFile;

  private List<Map<String, Double>> trainX;

  private List<List<String>> trainY;

  private List<String> qids;

  private int limit;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    // feature constructor and classifier
    String featureConstructorName = UimaContextHelper.getConfigParameterStringValue(context,
            "feature-constructor");
    featureConstructor = ProviderCache.getProvider(featureConstructorName,
            FeatureConstructorProvider.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    // labels for training instances
    String qaTypesFile = UimaContextHelper.getConfigParameterStringValue(context, "qa-types-file");
    Yaml yaml = new Yaml();
    @SuppressWarnings("unchecked")
    List<Map<String, Object>> qmaps = yaml.loadAs(getClass().getResourceAsStream(qaTypesFile),
            List.class);
    qid2labels = qmaps.stream().collect(toMap(qmap -> (String) qmap.get("qid"), qmap -> {
      @SuppressWarnings("unchecked")
      List<Map<String, Object>> tcmaps = (List<Map<String, Object>>) qmap.get("type-count");
      int maxCount = tcmaps.stream().mapToInt(tcmap -> (int) tcmap.get("count")).max().orElse(0);
      return tcmaps.stream().filter(tcmap -> (int) tcmap.get("count") == maxCount)
              .map(tcmap -> (String) tcmap.get("abbr")).collect(toList());
    } , (x, y) -> y));
    // cv file
    cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context, "cv-predict-file",
            null);
    trainX = new ArrayList<>();
    trainY = new ArrayList<>();
    if (cvPredictFile != null) {
      qids = new ArrayList<>();
    }
    limit = UimaContextHelper.getConfigParameterIntValue(context, "cv-predict-limit", 1);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    List<ConceptMention> cmentions = TypeUtil.getOrderedConceptMentions(jcas);
    Focus focus = TypeUtil.getFocus(jcas);
    Map<String, Double> features = featureConstructor.constructFeatures(tokens, cmentions, focus);
    trainX.add(features);
    String qid = TypeUtil.getQuestion(jcas).getId();
    trainY.add(qid2labels.get(qid));
    if (cvPredictFile != null) {
      qids.add(qid);
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (cvPredictFile != null) {
      try (BufferedWriter bw = Files.newWriter(new File(cvPredictFile), Charsets.UTF_8)) {
        List<List<String>> results = classifier.crossTrainPredictMultiLabel(trainX, trainY, limit);
        for (int i = 0; i < qids.size(); i++) {
          bw.write(qids.get(i) + "\t" + results.get(i).stream().collect(joining(";")) + "\n");
        }
        bw.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    classifier.trainMultiLabel(trainX, trainY, true);
  }

}
