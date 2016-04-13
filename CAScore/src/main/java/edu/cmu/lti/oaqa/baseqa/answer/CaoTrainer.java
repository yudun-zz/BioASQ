package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

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

public class CaoTrainer extends JCasAnnotator_ImplBase {

  private List<TransducerFeatureConstructorProvider> featureConstructors;

  private List<TransducerContextFeatureConstructorProvider> contextFeatureConstructors;

  private TransducerProvider transducer;

  private String viewNamePrefix;

  private List<List<Map<String, Double>>> X;

  private List<List<String>> Y;

  private float sampleOToO;

  private int windowSize;

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
    sampleOToO = UimaContextHelper.getConfigParameterFloatValue(context, "sample-o2o", 1.0f);
    windowSize = UimaContextHelper.getConfigParameterIntValue(context, "window-size", 5);
    X = new ArrayList<>();
    Y = new ArrayList<>();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    for (JCas view : ViewType.listViews(jcas, viewNamePrefix)) {
      Collection<CandidateAnswerOccurrence> gsCaos = TypeUtil.getCandidateAnswerOccurrences(view);
      // ignore texts without a GS CAO annotation
      if (gsCaos.isEmpty())
        continue;
      // generate label sequence
      Set<Token> gsBeginTokens = gsCaos.stream()
              .map(cao -> JCasUtil.selectCovered(Token.class, cao)).map(tokens -> tokens.get(0))
              .collect(toSet());
      Set<Token> gsSubseqTokens = gsCaos.stream()
              .map(cao -> JCasUtil.selectCovered(Token.class, cao))
              .filter(tokens -> tokens.size() > 1).map(tokens -> tokens.subList(1, tokens.size()))
              .flatMap(List::stream).collect(toSet());
      List<Token> tokens = gsCaos.stream()
              .flatMap(cao -> Stream
                      .of(JCasUtil.selectPreceding(Token.class, cao, windowSize),
                              JCasUtil.selectCovered(Token.class, cao),
                              JCasUtil.selectFollowing(Token.class, cao, windowSize))
                      .flatMap(List::stream))
              .distinct().sorted(Comparator.comparing(Token::getBegin)).collect(toList());
      int length = tokens.size();
      List<String> labels = tokens.stream().map(token -> {
        if (gsBeginTokens.contains(token)) {
          return "B";
        } else if (gsSubseqTokens.contains(token)) {
          return "I";
        } else {
          return "O";
        }
      } ).collect(toList());
      System.out.println(String.join("", labels));
      IntStream.range(0, length).filter(i -> labels.get(i).equals("I"))
              .filter(i -> i == 0 || labels.get(i - 1).equals("O"))
              .forEach(i -> labels.set(i, "B"));
      assert labels.size() == length;
      Y.add(labels);
      // generate feature vector sequence
      List<Map<String, Double>> features = featureConstructors.stream()
              .map(featCtor -> featCtor.constructFeaturesSequence(tokens, view, jcas))
              .collect(new FeaturesSequenceCollector(tokens.size()));
      assert features.size() == length;
      List<Map<String, Double>> contextFeatures = contextFeatureConstructors.stream()
              .map(featCtor -> featCtor.constructFeaturesSequence(tokens, features))
              .collect(new FeaturesSequenceCollector(tokens.size()));
      assert contextFeatures.size() == length;
      IntStream.range(0, length).forEach(i -> features.get(i).putAll(contextFeatures.get(i)));
      X.add(features);
    }
  }

  private static final List<String> sampleTarget = Arrays.asList("O", "O", "O");

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    int sequenceCount = X.size();
    if (sampleOToO < 1.0f) {
      IntStream.range(0, sequenceCount).forEach(i -> {
        List<Map<String, Double>> featuresSequence = X.get(i);
        List<String> labelSequence = Y.get(i);
        int sequenceLength = featuresSequence.size();
        int[] indexes = IntStream.range(0, sequenceLength)
                .filter(j -> j == 0 || j == sequenceLength - 1
                        || !sampleTarget.equals(labelSequence.subList(j - 1, j + 2))
                        || Math.random() < sampleOToO)
                .toArray();
        X.set(i, IntStream.of(indexes).mapToObj(featuresSequence::get).collect(toList()));
        Y.set(i, IntStream.of(indexes).mapToObj(labelSequence::get).collect(toList()));
      } );
    }
    Map<String, Long> label2count = Y.stream().flatMap(List::stream)
            .collect(groupingBy(Function.identity(), counting()));
    System.out.println("Label count: " + label2count);
    transducer.train(X, Y, "O");
  }

}
