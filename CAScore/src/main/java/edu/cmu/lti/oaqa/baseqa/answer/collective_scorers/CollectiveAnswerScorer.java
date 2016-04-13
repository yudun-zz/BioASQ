package edu.cmu.lti.oaqa.baseqa.answer.collective_scorers;

import java.util.List;
import java.util.Map;

import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.Resource;

import edu.cmu.lti.oaqa.type.answer.Answer;

public interface CollectiveAnswerScorer extends Resource {

  void prepare(JCas jcas, List<Answer> answers);
  
  Map<String, Double> score(JCas jcas, Answer answer);
  
}
