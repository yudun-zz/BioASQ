package edu.cmu.lti.oaqa.baseqa.providers.ml;

import java.util.Map;

import org.apache.uima.resource.Resource;

public interface RankerProvider extends Resource {
  
  double predict(Map<String, Double> features);

}
