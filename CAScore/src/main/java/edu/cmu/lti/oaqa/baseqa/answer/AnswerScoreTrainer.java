package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.CharMatcher;
import com.google.common.base.Charsets;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.baseqa.answer.scorers.CavScorer;
import edu.cmu.lti.oaqa.baseqa.providers.ml.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.baseqa.util.ViewType;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class AnswerScoreTrainer extends JCasAnnotator_ImplBase {

  private List<CavScorer> scorers;

  private ClassifierProvider classifier;

  private String cvPredictFile;

  private List<Map<String, Double>> X;

  private List<String> Y;

  private List<String> idnames;

  private boolean atLeastOneCorrectCav;

  private boolean balanceSample;

  private static CharMatcher alphaNumeric = CharMatcher.JAVA_LETTER_OR_DIGIT;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String scorerNames = UimaContextHelper.getConfigParameterStringValue(context, "scorers");
    scorers = ProviderCache.getProviders(scorerNames, CavScorer.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context, "cv-predict-file",
            null);
    if (cvPredictFile != null) {
      idnames = new ArrayList<>();
    }
    X = new ArrayList<>();
    Y = new ArrayList<>();
    atLeastOneCorrectCav = UimaContextHelper.getConfigParameterBooleanValue(context,
            "at-least-one-correct-cav", true);
    balanceSample = UimaContextHelper.getConfigParameterBooleanValue(context, "balance-sample",
            false);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Set<String> gsNames = TypeUtil.getRankedAnswers(ViewType.getGsView(jcas)).stream()
            .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(Collection::stream)
            .map(alphaNumeric::retainFrom).map(String::toLowerCase).collect(toSet());
    Collection<CandidateAnswerVariant> cavs = TypeUtil.getCandidateAnswerVariants(jcas);
    List<String> Ysubset = new ArrayList<>();
    for (CandidateAnswerVariant cav : cavs) {
      Set<String> retrievedNames = TypeUtil.getCandidateAnswerVariantNames(cav).stream()
              .map(alphaNumeric::retainFrom).map(String::toLowerCase).collect(toSet());
      Ysubset.add(Sets.intersection(gsNames, retrievedNames).isEmpty() ? "false" : "true");
    }
    if (atLeastOneCorrectCav && !Ysubset.contains("true")) {
      return;
    }
    Y.addAll(Ysubset);
    for (CandidateAnswerVariant cav : cavs) {
      Map<String, Double> features = scorers.stream().map(scorer -> scorer.score(jcas, cav))
              .map(Map::entrySet).flatMap(Set::stream)
              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
      X.add(features);
    }
    if (cvPredictFile != null) {
      for (CandidateAnswerVariant cav : cavs) {
        String name = TypeUtil.getCandidateAnswerVariantNames(cav).stream()
                .map(str -> str.replaceAll("\t", "")).collect(joining(";"));
        idnames.add(TypeUtil.getQuestion(jcas).getId() + "\t" + name);
      }
    }
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (balanceSample) {
      Map<String, Long> y2count = Y.stream().collect(groupingBy(Function.identity(), counting()));
      Long yMin = Collections.min(y2count.values());
      Map<String, Double> y2weight = y2count.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, entry -> (double) yMin / entry.getValue()));
      Set<Integer> indexes = IntStream.range(0, Y.size())
              .filter(i -> Math.random() < y2weight.get(Y.get(i))).boxed().collect(toSet());
      X = indexes.stream().map(X::get).collect(toList());
      Y = indexes.stream().map(Y::get).collect(toList());
      idnames = indexes.stream().map(idnames::get).collect(toList());
    }
    if (cvPredictFile != null) {
      try (BufferedWriter bw = Files.newWriter(new File(cvPredictFile), Charsets.UTF_8)) {
        List<Double> results = classifier.crossTrainInfer(X, Y, "true");
        for (int i = 0; i < idnames.size(); i++) {
          bw.write(idnames.get(i) + "\t" + results.get(i) + "\n");
        }
        bw.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
    classifier.train(X, Y);
  }

}
