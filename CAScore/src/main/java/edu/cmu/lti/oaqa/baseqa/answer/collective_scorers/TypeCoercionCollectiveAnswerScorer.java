package edu.cmu.lti.oaqa.baseqa.answer.collective_scorers;

import java.util.Collection;
import java.util.List;
import java.util.Map;

import org.apache.uima.fit.util.JCasUtil;
import org.apache.uima.jcas.JCas;
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
import edu.cmu.lti.oaqa.type.kb.ConceptMention;
import edu.cmu.lti.oaqa.type.kb.ConceptType;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class TypeCoercionCollectiveAnswerScorer extends ConfigurableProvider
        implements CollectiveAnswerScorer {

  private Iterable<Integer> topLimits;

  private List<Answer> answers;

  private Table<Answer, Answer, Double> typecors;

  private Table<Answer, Answer, Double> ntypecors;

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
    typecors = HashBasedTable.create();
    ImmutableSet<Answer> answerSet = ImmutableSet.copyOf(answers);
    SetMultimap<Answer, String> answer2ctypes = HashMultimap.create();
    answers.stream().forEach(answer -> TypeUtil.getCandidateAnswerVariants(answer).stream()
            .map(TypeUtil::getCandidateAnswerOccurrences).forEach(caos -> {
              caos.stream().map(cao -> JCasUtil.selectCovering(ConceptMention.class, cao))
                      .flatMap(Collection::stream).map(ConceptMention::getConcept)
                      .map(TypeUtil::getConceptTypes).flatMap(Collection::stream)
                      .map(ConceptType::getAbbreviation)
                      .forEach(ctype -> answer2ctypes.put(answer, ctype));
            }));
    for (List<Answer> pair : Sets.cartesianProduct(answerSet, answerSet)) {
      Answer answer1 = pair.get(0);
      Answer answer2 = pair.get(1);
      if (answer1.equals(answer2))
        continue;
      int typecor = Sets.intersection(answer2ctypes.get(answer1), answer2ctypes.get(answer2))
              .size();
      if (typecor > 0)
        typecors.put(answer1, answer2, (double) typecor);
    }
    ntypecors = normalize(typecors);
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    Map<Answer, Double> neighbor2typecor = typecors.row(answer);
    Map<Answer, Double> neighbor2ntypecor = ntypecors.row(answer);
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    for (int topLimit : topLimits) {
      double minTypecor = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2typecor.getOrDefault(neighbor, 0.0)).max()
              .orElse(0);
      builder.put("typecor-" + topLimit, minTypecor);
      double minNTypecor = answers.subList(0, Math.min(answers.size(), topLimit)).stream()
              .mapToDouble(neighbor -> neighbor2ntypecor.getOrDefault(neighbor, 0.0)).max()
              .orElse(0);
      builder.put("ntypecor-" + topLimit, minNTypecor);
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

}
