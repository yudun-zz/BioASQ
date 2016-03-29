package edu.cmu.lti.oaqa.baseqa.answer.collective_scorers;

import static java.util.stream.Collectors.toMap;

import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;

public class OriginalCollectiveAnswerScorer extends ConfigurableProvider
        implements CollectiveAnswerScorer {

  private Map<Answer, Double> answer2irank;

  @Override
  public void prepare(JCas jcas, List<Answer> answers) {
    answer2irank = IntStream.range(0, answers.size()).boxed()
            .collect(toMap(answers::get, i -> 1.0 / (1.0 + i)));
  }

  @Override
  public Map<String, Double> score(JCas jcas, Answer answer) {
    ImmutableMap.Builder<String, Double> builder = ImmutableMap.builder();
    builder.put("orig-score", answer.getScore());
    builder.put("orig-rank", answer2irank.getOrDefault(answer, 0.0));
    return builder.build();
  }

}
