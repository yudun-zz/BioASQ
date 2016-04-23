package edu.cmu.lti.oaqa.baseqa.answer.scorers;

import java.util.Map;
import java.util.Set;
import java.util.stream.DoubleStream;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import com.google.common.collect.ImmutableMap;
import com.google.common.collect.ImmutableSet;

import edu.cmu.lti.oaqa.type.answer.CandidateAnswerVariant;

public interface CavScorer extends Resource {

  Map<String, Double> score(JCas jcas, CandidateAnswerVariant cav);

  static Map<String, Double> generateSummaryFeatures(double[] ratios, String keyword) {
    return generateSummaryFeatures(ratios, keyword, "avg", "max", "min", "pos-ratio", "one-ratio",
            "any-one", "dev");
  }

  static Map<String, Double> generateSummaryFeatures(double[] ratios, String keyword,
          String... operators) {
    ImmutableMap.Builder<String, Double> feat2value = ImmutableMap.builder();
    Set<String> operatorSet = ImmutableSet.copyOf(operators);
    if (operatorSet.contains("avg")) {
      feat2value.put(keyword + "-avg", DoubleStream.of(ratios).average().orElse(0));
    }
    if (operatorSet.contains("max")) {
      feat2value.put(keyword + "-max", DoubleStream.of(ratios).max().orElse(0));
    }
    if (operatorSet.contains("min")) {
      feat2value.put(keyword + "-min", DoubleStream.of(ratios).min().orElse(0));
    }
    if (operatorSet.contains("pos-ratio")) {
      feat2value.put(keyword + "-pos-ratio",
              DoubleStream.of(ratios).mapToInt(r -> r == 0.0 ? 0 : 1).average().orElse(0));
    }
    if (operatorSet.contains("one-ratio")) {
      feat2value.put(keyword + "-one-ratio",
              DoubleStream.of(ratios).mapToInt(r -> r == 1.0 ? 1 : 0).average().orElse(0));
    }
    if (operatorSet.contains("any-one")) {
      feat2value.put(keyword + "-any-one",
              DoubleStream.of(ratios).anyMatch(r -> r == 1.0) ? 1.0 : 0.0);
    }
    if (operatorSet.contains("dev")) {
    	double avg = DoubleStream.of(ratios).average().orElse(0);
    	feat2value.put(keyword + "-dev", Math.sqrt(DoubleStream.of(ratios).map(r -> Math.pow(r - avg, 2.0)).average().orElse(0)));
    }
    return feat2value.build();
  }

  static double safeDividedBy(double x, double y) {
    if (x == 0.0 && y == 0.0) {
      return 0.0;
    } else {
      return x / y;
    }
  }

}
