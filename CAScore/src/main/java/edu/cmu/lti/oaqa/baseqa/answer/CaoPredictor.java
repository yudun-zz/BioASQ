package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.CharMatcher;

import edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.FeaturesSequenceCollector;
import edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.TransducerProvider;
import edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors.TransducerContextFeatureConstructorProvider;
import edu.cmu.lti.oaqa.baseqa.providers.ml.transducer.featctors.TransducerFeatureConstructorProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CaoPredictor extends JCasAnnotator_ImplBase {

  private List<TransducerFeatureConstructorProvider> featureConstructors;

  private List<TransducerContextFeatureConstructorProvider> contextFeatureConstructors;

  private TransducerProvider transducer;

  private String viewNamePrefix;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String featureConstructorNames = UimaContextHelper.getConfigParameterStringValue(context,
            "feature-constructors");
    featureConstructors = ProviderCache.getProviders(featureConstructorNames,
            TransducerFeatureConstructorProvider.class);
    String contextFeatureConstructorNames = UimaContextHelper.getConfigParameterStringValue(context,
            "context-feature-constructors", null);
    if (contextFeatureConstructorNames != null) {
      contextFeatureConstructors = ProviderCache.getProviders(contextFeatureConstructorNames,
              TransducerContextFeatureConstructorProvider.class);
    } else {
      contextFeatureConstructors = new ArrayList<>();
    }
    String transducerName = UimaContextHelper.getConfigParameterStringValue(context, "transducer");
    transducer = ProviderCache.getProvider(transducerName, TransducerProvider.class);
    viewNamePrefix = UimaContextHelper.getConfigParameterStringValue(context, "view-name-prefix");
  }

  private static final CharMatcher BO = CharMatcher.anyOf("BO");

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    for (JCas view : ViewType.listViews(jcas, viewNamePrefix)) {
      // generate feature vector sequence
      List<Token> tokens = TypeUtil.getOrderedTokens(view);
      int length = tokens.size();
      List<Map<String, Double>> features = featureConstructors.stream()
              .map(featCtor -> featCtor.constructFeaturesSequence(tokens, view, jcas))
              .collect(new FeaturesSequenceCollector(tokens.size()));
      assert features.size() == length;
      List<Map<String, Double>> contextFeatures = contextFeatureConstructors.stream()
              .map(featCtor -> featCtor.constructFeaturesSequence(tokens, features))
              .collect(new FeaturesSequenceCollector(tokens.size()));
      assert contextFeatures.size() == length;
      IntStream.range(0, length).forEach(i -> features.get(i).putAll(contextFeatures.get(i)));
      // transduce
      List<String> labels = transducer.predict(features);

      Collection<CandidateAnswerOccurrence> gsCaos = TypeUtil.getCandidateAnswerOccurrences(view);
      if (!gsCaos.isEmpty() || labels.contains("B")) {
        System.out.println(IntStream.range(0, length)
                .mapToObj(i -> tokens.get(i).getCoveredText() + "/" + labels.get(i))
                .collect(Collectors.joining(" ")));
//        IntStream.range(0, length)
//                .mapToObj(i -> tokens.get(i).getCoveredText() + "/" + features.get(i))
//                .forEach(System.out::println);
        System.out.println(
                "predicted: " + String.join("", labels) + " " + transducer.infer(features, labels));
        Set<Token> gsBeginTokens = gsCaos.stream()
                .map(cao -> JCasUtil.selectCovered(Token.class, cao))
                .map(caotokens -> caotokens.get(0)).collect(toSet());
        Set<Token> gsSubseqTokens = gsCaos.stream()
                .map(cao -> JCasUtil.selectCovered(Token.class, cao))
                .filter(caotokens -> caotokens.size() > 1)
                .map(caotokens -> caotokens.subList(1, caotokens.size())).flatMap(List::stream)
                .collect(toSet());
        List<String> gslabels = tokens.stream().map(token -> {
          if (gsBeginTokens.contains(token)) {
            return "B";
          } else if (gsSubseqTokens.contains(token)) {
            return "I";
          } else {
            return "O";
          }
        }).collect(toList());
        IntStream.range(0, length).filter(i -> labels.get(i).equals("I"))
                .filter(i -> i == 0 || labels.get(i - 1).equals("O"))
                .forEach(i -> labels.set(i, "B"));
        System.out.println("gs:        " + String.join("", gslabels) + " "
                + transducer.infer(features, gslabels));
        System.out.println("zeros:     "
                + transducer.infer(features, Collections.nCopies(labels.size(), "O")));
//        System.out.println("xxxxx:     " + transducer.infer(features, "OOOOOBOOOBIIOOOBIOO".chars()
//                .mapToObj(c -> Character.toString((char) c)).collect(toList())));

      }

      if (!labels.contains("B"))
        continue;
      // generate label sequence
      String labelStr = String.join("", labels) + "O";
      assert labelStr.length() == length + 1;
      // System.out.println(labelString);
      int[] begins = IntStream.range(0, length).filter(i -> labelStr.charAt(i) == 'B').toArray();
      int[] ends = IntStream.of(begins).map(begin -> BO.indexIn(labelStr, begin + 1)).toArray();
      List<CandidateAnswerOccurrence> caos = IntStream
              .range(0, begins.length).mapToObj(i -> new CandidateAnswerOccurrence(view,
                      tokens.get(begins[i]).getBegin(), tokens.get(ends[i] - 1).getEnd()))
              .collect(toList());
      System.out.println("Extracted " + caos.size() + " CAOs: "
              + caos.stream().map(CandidateAnswerOccurrence::getCoveredText).collect(toList()));
      caos.forEach(CandidateAnswerOccurrence::addToIndexes);
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
  }

}
