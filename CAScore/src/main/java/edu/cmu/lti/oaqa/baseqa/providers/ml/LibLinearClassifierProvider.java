package edu.cmu.lti.oaqa.baseqa.providers.ml;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.groupingBy;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.IntStream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.primitives.Ints;

import de.bwaldvogel.liblinear.Feature;
import de.bwaldvogel.liblinear.FeatureNode;
import de.bwaldvogel.liblinear.Linear;
import de.bwaldvogel.liblinear.Model;
import de.bwaldvogel.liblinear.Parameter;
import de.bwaldvogel.liblinear.Problem;
import de.bwaldvogel.liblinear.SolverType;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.gerp.util.Pair;

public class LibLinearClassifierProvider extends ConfigurableProvider
        implements ClassifierProvider {

  private File featIndexFile;

  private File labelIndexFile;

  private File modelFile;

  private boolean balanceWeight;

  private Map<Integer, String> fid2feat;

  private BiMap<Integer, String> lid2label;

  private BiMap<String, Integer> label2lid;

  private Model model;

  private Parameter parameter;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // feature id map
    if ((featIndexFile = new File((String) getParameterValue("feat-index-file"))).exists()) {
      try {
        fid2feat = ClassifierProvider.loadIdKeyMap(featIndexFile);
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // label id map
    if ((labelIndexFile = new File((String) getParameterValue("label-index-file"))).exists()) {
      try {
        lid2label = HashBiMap.create(ClassifierProvider.loadIdKeyMap(labelIndexFile));
        label2lid = lid2label.inverse();
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // model
    if ((modelFile = new File((String) getParameterValue("model-file"))).exists()) {
      try {
        model = Model.load(modelFile);
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    balanceWeight = (boolean) getParameterValue("balance-weight");
    // parameter
    SolverType solver = SolverType.valueOf((String) getParameterValue("solver-type")); // -s 0
    double C = 1.0; // cost of constraints violation
    double eps = 0.01; // stopping criteria
    parameter = new Parameter(solver, C, eps);
    return ret;
  }

  @Override
  public double infer(Map<String, Double> features, String label) {
    Feature[] x = IntStream.range(1, fid2feat.size() + 1)
            .mapToObj(j -> new FeatureNode(j, features.getOrDefault(fid2feat.get(j), 0.0)))
            .toArray(Feature[]::new);
    double[] values = new double[lid2label.size()];
    Linear.predictValues(model, x, values);
    if (lid2label.size() == 2) {
      values[0] = 1 / (1 + Math.exp(-values[0]));
      values[1] = 1 - values[0];
    }
    int[] lids = model.getLabels();
    return values[Ints.asList(lids).indexOf(label2lid.get(label))];
  }

  @Override
  public String predict(Map<String, Double> features) {
    Feature[] x = IntStream.range(1, fid2feat.size() + 1)
            .mapToObj(j -> new FeatureNode(j, features.getOrDefault(fid2feat.get(j), 0.0)))
            .toArray(Feature[]::new);
    double result = Linear.predict(model, x);
    return lid2label.get((int) result);
  }

  @Override
  public List<String> predict(Map<String, Double> features, int k)
          throws AnalysisEngineProcessException {
    Feature[] x = IntStream.range(1, fid2feat.size() + 1)
            .mapToObj(j -> new FeatureNode(j, features.getOrDefault(fid2feat.get(j), 0.0)))
            .toArray(Feature[]::new);
    double[] values = new double[lid2label.size()];
    double result = Linear.predictProbability(model, x, values);
    int[] lids = model.getLabels();
    List<String> topK = IntStream.range(0, lid2label.size())
            .mapToObj(j -> Pair.of(lid2label.get(lids[j]), values[j]))
            .sorted(Comparator.comparing(Pair::getValue, Comparator.reverseOrder())).limit(k)
            .map(Pair::getKey).collect(toList());
    assert lid2label.get((int) result).equals(topK.get(0));
    return topK;
  }

  @Override
  public void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException {
    // create feature to id map
    fid2feat = ClassifierProvider.createFeatureIdKeyMap(X);
    // create label to id map
    lid2label = ClassifierProvider.createLabelIdKeyMap(Y);
    label2lid = lid2label.inverse();
    try {
      ClassifierProvider.saveIdKeyMap(fid2feat, featIndexFile);
      ClassifierProvider.saveIdKeyMap(lid2label, labelIndexFile);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // train model
    Problem prob = new Problem();
    assert X.size() == Y.size();
    int dataCount = X.size();
    int featCount = fid2feat.size();
    System.out.println("Training for " + dataCount + " instances, " + featCount + " features, "
            + lid2label.size() + " labels.");
    prob.l = dataCount;
    prob.n = featCount;
    prob.x = X.stream()
            .map(x -> IntStream.range(1, featCount + 1)
                    .mapToObj(j -> new FeatureNode(j, x.getOrDefault(fid2feat.get(j), 0.0)))
                    .toArray(Feature[]::new))
            .toArray(Feature[][]::new);
    prob.y = Y.stream().mapToDouble(label2lid::get).toArray();
    if (balanceWeight) {
      Map<String, Long> y2count = Y.stream().collect(groupingBy(Function.identity(), counting()));
      Long yMin = Collections.min(y2count.values());
      Map<String, Double> y2weight = y2count.entrySet().stream()
              .collect(toMap(Map.Entry::getKey, entry -> (double) yMin / entry.getValue()));
      double[] weights = y2weight.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray();
      int[] weightLabels = y2weight.entrySet().stream().map(Map.Entry::getKey)
              .mapToInt(label2lid::get).toArray();
      parameter.setWeights(weights, weightLabels);
    }
    model = Linear.train(prob, parameter);
    try {
      model.save(modelFile);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    if (crossValidation) {
      crossValidate(prob, 10);
    }
  }

  public void crossValidate(Problem problem, int nrFold) {
    double[] target = new double[problem.l];
    Linear.crossValidation(problem, parameter, nrFold, target);
    long totalCorrect = IntStream.range(0, problem.l).filter(i -> target[i] == problem.y[i])
            .count();
    System.out.printf("correct: %d%n", totalCorrect);
    System.out.printf("Cross Validation Accuracy = %g%%%n", 100.0 * totalCorrect / problem.l);
  }

}
