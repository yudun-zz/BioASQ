package edu.cmu.lti.oaqa.baseqa.providers.ml;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.commons.codec.Charsets;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;
import com.google.common.primitives.Ints;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import libsvm.svm;
import libsvm.svm_model;
import libsvm.svm_node;
import libsvm.svm_parameter;
import libsvm.svm_problem;

public class LibSvmClassifierProvider extends ConfigurableProvider implements ClassifierProvider {

  private File featIndexFile;

  private File labelIndexFile;

  private File modelFile;

  private Map<Integer, String> fid2feat;

  private BiMap<Integer, String> lid2label;

  private BiMap<String, Integer> label2lid;

  private svm_model model;

  private svm_parameter param;

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
        model = svm.svm_load_model(Files.newReader(modelFile, Charsets.UTF_8));
      } catch (IOException e) {
        throw new ResourceInitializationException(e);
      }
    }
    // parameter
    param = new svm_parameter();
    param.svm_type = svm_parameter.C_SVC;
    param.kernel_type = svm_parameter.RBF;
    // param.probability = 1;
    // param.gamma = 0.5;
    // param.nu = 0.5;
    // param.C = 1;
    // param.cache_size = 20000;
    // param.eps = 0.001;
    return ret;
  }

  @Override
  public double infer(Map<String, Double> features, String label) {
    svm_node[] x = IntStream.range(1, fid2feat.size() + 1).mapToObj(j -> {
      svm_node node = new svm_node();
      node.index = j;
      node.value = features.getOrDefault(fid2feat.get(j), 0.0);
      return node;
    } ).toArray(svm_node[]::new);
    double[] values = new double[lid2label.size()];
    svm.svm_predict_values(model, x, values);
    int[] lids = new int[lid2label.size()];
    svm.svm_get_labels(model, lids);
    return values[Ints.asList(lids).indexOf(label2lid.get(label))];
  }

  @Override
  public String predict(Map<String, Double> features) {
    svm_node[] x = IntStream.range(1, fid2feat.size() + 1).mapToObj(j -> {
      svm_node node = new svm_node();
      node.index = j;
      node.value = features.getOrDefault(fid2feat.get(j), 0.0);
      return node;
    } ).toArray(svm_node[]::new);
    double result = svm.svm_predict(model, x);
    return lid2label.get((int) result);
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
    // create libsvm data structure and train
    svm_problem prob = new svm_problem();
    assert X.size() == Y.size();
    int dataCount = X.size();
    int featCount = fid2feat.size();
    System.out.println("Training for " + dataCount + " instances, " + featCount + " features, "
            + lid2label.size());
    prob.l = dataCount;
    prob.x = X.stream().map(x -> IntStream.range(1, featCount + 1).mapToObj(j -> {
      svm_node node = new svm_node();
      node.index = j;
      node.value = x.getOrDefault(fid2feat.get(j), 0.0);
      return node;
    } ).toArray(svm_node[]::new)).toArray(svm_node[][]::new);
    prob.y = Y.stream().mapToDouble(label2lid::get).toArray();
    model = svm.svm_train(prob, param);
    try {
      svm.svm_save_model(modelFile.getAbsolutePath(), model);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    double[] target = new double[prob.l];
    if (crossValidation) {
      svm.svm_cross_validation(prob, param, 10, target);
    }
  }

}
