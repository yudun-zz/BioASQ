package edu.cmu.lti.oaqa.baseqa.answer;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import com.google.common.base.Charsets;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.Table;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.baseqa.answer.collective_scorers.CollectiveAnswerScorer;
import edu.cmu.lti.oaqa.baseqa.providers.ml.ClassifierProvider;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.ecd.phase.ProcessingStepUtils;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class CollectiveAnswerScorePredictor extends JCasAnnotator_ImplBase {

  private List<CollectiveAnswerScorer> scorers;

  private ClassifierProvider classifier;

  private String featureFilename;

  private Table<String, String, Double> feat2value;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    String scorerNames = UimaContextHelper.getConfigParameterStringValue(context, "scorers");
    scorers = ProviderCache.getProviders(scorerNames, CollectiveAnswerScorer.class);
    String classifierName = UimaContextHelper.getConfigParameterStringValue(context, "classifier");
    classifier = ProviderCache.getProvider(classifierName, ClassifierProvider.class);
    featureFilename = UimaContextHelper.getConfigParameterStringValue(context, "feature-file",
            null);
    if (featureFilename != null) {
      feat2value = HashBasedTable.create();
    }
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    List<Answer> answers = TypeUtil.getRankedAnswers(jcas);
    String qid = null;
    if (featureFilename != null) {
      qid = ProcessingStepUtils.getSequenceId(jcas);
    }
    scorers.stream().forEach(scorer -> scorer.prepare(jcas, answers));
    for (Answer answer : answers) {
      Map<String, Double> features = scorers.stream().map(scorer -> scorer.score(jcas, answer))
              .map(Map::entrySet).flatMap(Set::stream)
              .collect(toMap(Map.Entry::getKey, Map.Entry::getValue));
      double score = classifier.infer(features, "true");
      answer.setScore(score);
      if (featureFilename != null) {
        putFeatureValues(qid, answer, score, features);
      }
    }
    answers.sort(TypeUtil.ANSWER_SCORE_COMPARATOR);
    System.out.println("Ranked top 5 answers " + answers.stream().limit(5)
            .map(TypeUtil::getCandidateAnswerVariantNames).collect(toList()));
  }

  private void putFeatureValues(String qid, Answer answer, double score,
          Map<String, Double> features) {
    String cavNameString = TypeUtil.getCandidateAnswerVariantNames(answer).stream()
            .collect(joining(";"));
    String id = String.join("\t", qid, cavNameString, String.valueOf(score));
    feat2value.row(id).putAll(features);
  }

  @Override
  public void collectionProcessComplete() throws AnalysisEngineProcessException {
    super.collectionProcessComplete();
    if (featureFilename != null) {
      try {
        BufferedWriter bw = Files.newWriter(new File(featureFilename), Charsets.UTF_8);
        Set<String> feats = feat2value.columnKeySet();
        bw.write("\t\t\t" + feats.stream().collect(joining("\t")) + "\n");
        bw.write(feat2value.rowMap().entrySet().stream().map(entry -> {
          return entry.getKey() + "\t" + feats.stream().map(feat -> entry.getValue().get(feat))
                  .map(String::valueOf).collect(joining("\t"));
        }).collect(joining("\n")));
        bw.close();
      } catch (IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

}
