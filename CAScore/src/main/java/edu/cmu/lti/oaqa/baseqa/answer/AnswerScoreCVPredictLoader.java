package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toMap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.google.common.base.Charsets;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.io.Resources;

import edu.cmu.lti.oaqa.baseqa.answer.scorers.CavScorer;
import edu.cmu.lti.oaqa.baseqa.providers.ml.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class AnswerScoreCVPredictLoader extends JCasAnnotator_ImplBase {

  private List<CavScorer> scorers;

  private ClassifierProvider classifier;

  private Map<String, Double> idname2score;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String cvPredictFile = UimaContextHelper.getConfigParameterStringValue(context,
            "cv-predict-file");
    String scorerNames = UimaContextHelper.getConfigParameterStringValue(context, "scorers");
    scorers = ProviderCache.getProviders(scorerNames, CavScorer.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    List<String> lines;
    try {
      lines = Resources.readLines(getClass().getResource(cvPredictFile), Charsets.UTF_8);
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    idname2score = lines.stream().map(line -> line.split("\t"))
            .collect(toMap(segs -> segs[0] + "\t" + segs[1], segs -> Double.parseDouble(segs[2]),
                    (x, y) -> Math.max(x, y)));
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    String id = TypeUtil.getQuestion(jcas).getId();
    List<Answer> answers = new ArrayList<>();
    for (CandidateAnswerVariant cav : TypeUtil.getCandidateAnswerVariants(jcas)) {
      String name = TypeUtil.getCandidateAnswerVariantNames(cav).stream()
              .map(str -> str.replaceAll("\t", "")).collect(joining(";"));
      double score;
      if (idname2score.containsKey(id + "\t" + name)) {
        score = idname2score.get(id + "\t" + name);
      } else {
        Map<String, Double> features = scorers.stream().map(scorer -> scorer.score(jcas, cav))
                .map(Map::entrySet).flatMap(Set::stream)
                .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
        score = classifier.infer(features, "true");
      }
      answers.add(TypeFactory.createAnswer(jcas, score, Arrays.asList(cav)));
    }
    answers.sort(TypeUtil.ANSWER_SCORE_COMPARATOR);
    answers.forEach(Answer::addToIndexes);
  }

}
