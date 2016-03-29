package edu.cmu.lti.oaqa.bioasq.eval.calculator;

import static edu.cmu.lti.oaqa.baseqa.eval.EvalCalculatorUtil.sumMeasurementValues;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_COUNT;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_LENIENT_ACCURACY;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_LENIENT_RETRIEVED;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_MRR;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_RECIPROCAL_RANK;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_STRICT_ACCURACY;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.FACTOID_STRICT_RETRIEVED;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_COUNT;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_F1;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_MEAN_F1;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_MEAN_PRECISION;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_MEAN_RECALL;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_PRECISION;
import static edu.cmu.lti.oaqa.bioasq.eval.calculator.AnswerEvalMeasure.LIST_RECALL;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.uima.jcas.JCas;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.baseqa.eval.EvalCalculator;
import edu.cmu.lti.oaqa.baseqa.eval.EvalCalculatorUtil;
import edu.cmu.lti.oaqa.baseqa.eval.Measure;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.answer.Answer;
import edu.cmu.lti.oaqa.util.TypeUtil;

public class AnswerEvalCalculator<T extends Answer> extends ConfigurableProvider
        implements EvalCalculator<T> {

  @Override
  public Map<Measure, Double> calculate(JCas jcas, Collection<T> resultEvaluatees,
          Collection<T> gsEvaluatees, Comparator<T> comparator,
          Function<T, String> uniqueIdMapper) {
    Set<String> gsVariants = gsEvaluatees.stream().map(TypeUtil::getCandidateAnswerVariantNames)
            .flatMap(Collection::stream).map(String::toLowerCase).collect(toSet());
    List<Answer> resultAnswers = resultEvaluatees.stream().sorted(comparator).collect(toList());
    String questionType = TypeUtil.getQuestion(jcas).getQuestionType();
    ImmutableMap.Builder<Measure, Double> builder = ImmutableMap.builder();
    if (questionType.equals("FACTOID")) {
      Set<String> strictResultVariants = resultAnswers.stream().limit(1)
              .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(Collection::stream)
              .map(String::toLowerCase).collect(toSet());
      Set<String> lenientResultVariants = resultAnswers.stream().limit(5)
              .map(TypeUtil::getCandidateAnswerVariantNames).flatMap(Collection::stream)
              .map(String::toLowerCase).collect(toSet());
      int strictRetrieved = Sets.intersection(gsVariants, strictResultVariants).isEmpty() ? 0 : 1;
      builder.put(FACTOID_STRICT_RETRIEVED, (double) strictRetrieved);
      int lenientRetrieved = Sets.intersection(gsVariants, lenientResultVariants).isEmpty() ? 0 : 1;
      builder.put(FACTOID_LENIENT_RETRIEVED, (double) lenientRetrieved);
      double reciprocalRank = IntStream.range(0, resultAnswers.size())
              .filter(i -> TypeUtil.getCandidateAnswerVariantNames(resultAnswers.get(i)).stream()
                      .map(String::toLowerCase).anyMatch(gsVariants::contains))
              .mapToDouble(i -> 1.0 / (i + 1.0)).findFirst().orElse(0.0);
      builder.put(FACTOID_RECIPROCAL_RANK, reciprocalRank);
      builder.put(FACTOID_COUNT, 1.0);
    } else if (questionType.equals("LIST")) {
      int relevantRetrieved = (int) resultAnswers.stream()
              .map(TypeUtil::getCandidateAnswerVariantNames).filter(names -> names.stream()
                      .map(String::toLowerCase).anyMatch(gsVariants::contains))
              .count();
      double precision = EvalCalculatorUtil.calculatePrecision(resultAnswers.size(),
              relevantRetrieved);
      builder.put(LIST_PRECISION, precision);
      double recall = EvalCalculatorUtil.calculateRecall(gsVariants.size(), relevantRetrieved);
      builder.put(LIST_RECALL, recall);
      builder.put(LIST_F1, EvalCalculatorUtil.calculateF1(precision, recall));
      builder.put(LIST_COUNT, 1.0);
    }
    return builder.build();
  }

  @Override
  public Map<Measure, Double> accumulate(
          Map<Measure, ? extends Collection<Double>> measure2values) {
    ImmutableMap.Builder<Measure, Double> builder = ImmutableMap.builder();
    if (measure2values.get(FACTOID_COUNT) != null) {
      double factoidCount = sumMeasurementValues(measure2values.get(FACTOID_COUNT));
      builder.put(FACTOID_COUNT, factoidCount);
      builder.put(FACTOID_STRICT_ACCURACY,
              sumMeasurementValues(measure2values.get(FACTOID_STRICT_RETRIEVED)) / factoidCount);
      builder.put(FACTOID_LENIENT_ACCURACY,
              sumMeasurementValues(measure2values.get(FACTOID_LENIENT_RETRIEVED)) / factoidCount);
      builder.put(FACTOID_MRR,
              sumMeasurementValues(measure2values.get(FACTOID_RECIPROCAL_RANK)) / factoidCount);
    }
    if (measure2values.get(LIST_COUNT) != null) {
      double listCount = sumMeasurementValues(measure2values.get(LIST_COUNT));
      builder.put(LIST_COUNT, listCount);
      builder.put(LIST_MEAN_PRECISION,
              sumMeasurementValues(measure2values.get(LIST_PRECISION)) / listCount);
      builder.put(LIST_MEAN_RECALL,
              sumMeasurementValues(measure2values.get(LIST_RECALL)) / listCount);
      builder.put(LIST_MEAN_F1, sumMeasurementValues(measure2values.get(LIST_F1)) / listCount);
    }
    return builder.build();
  }

  @Override
  public String getName() {
    return "Answer";
  }

}
