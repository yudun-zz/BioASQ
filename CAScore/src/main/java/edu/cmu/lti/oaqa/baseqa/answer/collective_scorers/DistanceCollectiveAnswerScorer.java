package edu.cmu.lti.oaqa.baseqa.answer.collective_scorers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.uima.cas.text.AnnotationFS;
import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
import org.apache.uima.jcas.cas.TOP;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Sets;
import com.google.common.collect.Table;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.type.answer.CandidateAnswerOccurrence;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class DistanceCollectiveAnswerScorer extends ConfigurableProvider
        implements CollectiveAnswerScorer {

  private Iterable<Integer> topLimits;

  private List<Answer> answers;

  private Table<Answer, Answer, Double> distances;

  private Table<Answer, Answer, Double> ndistances;

  @SuppressWarnings("unchecked")
  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    topLimits = (Iterable<Integer>) getParameterValue("top-limit");
    return ret;
  }

  @SuppressWarnings("unchecked")
  @Override
  public void prepare(JCas jcas, List<Answer> answers) {
    this.answers = answers;
    distances = HashBasedTable.create();
    ImmutableSet<Answer> answerSet = ImmutableSet.copyOf(answers);
    SetMultimap<Answer, CandidateAnswerOccurrence> answer2caos = HashMultimap.create();
    answers.stream()
            .forEach(answer -> TypeUtil.getCandidateAnswerVariants(answer).stream()
                    .map(TypeUtil::getCandidateAnswerOccurrences)
                    .forEach(caos -> answer2caos.putAll(answer, caos)));
    for (List<Answer> pair : Sets.cartesianProduct(answerSet, answerSet)) {
      Answer answer1 = pair.get(0);
      Answer answer2 = pair.get(1);
      if (answer1.equals(answer2)) {
        distances.put(answer1, answer2, 1.0);
      } else {
        Sets.cartesianProduct(answer2caos.get(answer1), answer2caos.get(answer2)).stream()
                .filter(DistanceCollectiveAnswerScorer::allInTheSameView)
                .mapToInt(caopair -> getDistance(caopair.get(0), caopair.get(1))).min()
                .ifPresent(x -> distances.put(answer1, answer2, 1.0 / (1.0 + x)));
      }
    }
    ndistances = normalize(distances);
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Map<Answer, Double> neighbor2distance = distances.row(answer);
    Map<Answer, Double> neighbor2ndistance = ndistances.row(answer);
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    for (int topLimit : topLimits) {
      double minDistance = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2distance.getOrDefault(neighbor, 0.0)).max()
              .orElse(0);
      builder.put("distance-" + topLimit, minDistance);
      double minNDistance = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2ndistance.getOrDefault(neighbor, 0.0)).max()
              .orElse(0);
      builder.put("ndistance-" + topLimit, minNDistance);
    }
    return builder.build();
  }

  private static <K1, K2> Table<K1, K2, Double> normalize(Table<K1, K2, Double> orig) {
    Table<K1, K2, Double> ret = HashBasedTable.create();
    orig.rowMap().entrySet().stream().forEach(entry -> {
      K1 key1 = entry.getKey();
      double sum = entry.getValue().values().stream().mapToDouble(x -> x).sum();
      entry.getValue().entrySet().stream()
              .forEach(e -> ret.put(key1, e.getKey(), e.getValue() / sum));
    });
    return ret;
  }

  private static boolean allInTheSameView(Collection<? extends TOP> tops) {
    return tops.stream().map(TOP::getCAS).distinct().count() == 1;
  }

  private static int getDistance(AnnotationFS annotation1, AnnotationFS annotation2) {
    if (annotation1.getEnd() < annotation2.getBegin()) {
      return JCasUtil.selectBetween(Token.class, annotation1, annotation2).size();
    } else if (annotation1.getBegin() > annotation2.getEnd()) {
      return JCasUtil.selectBetween(Token.class, annotation2, annotation1).size();
    } else {
      return 0;
    }
  }

}
