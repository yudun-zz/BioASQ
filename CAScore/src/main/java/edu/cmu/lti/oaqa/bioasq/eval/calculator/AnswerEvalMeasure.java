package edu.cmu.lti.oaqa.bioasq.eval.calculator;

import edu.cmu.lti.oaqa.baseqa.eval.Measure;

public enum AnswerEvalMeasure implements Measure {

  // PER-TOPIC FACTOID QUESTION ANSWER MEASURES
  FACTOID_COUNT, FACTOID_STRICT_RETRIEVED, FACTOID_LENIENT_RETRIEVED, FACTOID_RECIPROCAL_RANK,
  
  // ACCUMULATED FACTOID QUESTION ANSWER MEASURES
  FACTOID_STRICT_ACCURACY, FACTOID_LENIENT_ACCURACY, FACTOID_MRR,
  
  // PER-TOPIC LIST QUESTION ANSWER MEASURES
  LIST_COUNT, LIST_PRECISION, LIST_RECALL, LIST_F1,

  // ACCUMULATED LIST QUESTION ANSWER MEASURES
  LIST_MEAN_PRECISION, LIST_MEAN_RECALL, LIST_MEAN_F1;

  static {
    for (AnswerEvalMeasure measure : values()) {
      Measure.name2measure.put(measure.getName(), measure);
    }
  }

  @Override
  public String getName() {
    return name();
  }

}
