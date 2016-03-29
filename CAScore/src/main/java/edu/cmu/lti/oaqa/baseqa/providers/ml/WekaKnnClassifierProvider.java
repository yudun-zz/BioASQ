package edu.cmu.lti.oaqa.baseqa.providers.ml;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.stream.DoubleStream;
import java.util.stream.IntStream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.io.Files;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import weka.classifiers.Evaluation;
import weka.classifiers.lazy.IBk;
import weka.core.Attribute;
import weka.core.FastVector;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.SelectedTag;
import weka.core.SerializationHelper;
import weka.core.SparseInstance;

public class WekaKnnClassifierProvider extends ConfigurableProvider implements ClassifierProvider {

  private File featIndexFile;

  private File labelIndexFile;

  private File modelFile;

  private Map<Integer, String> fid2feat;

  private List<String> feats;

  private FastVector attributes;

  private BiMap<Integer, String> lid2label;

  private BiMap<String, Integer> label2lid;

  private IBk ibk;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // feature id map
    if ((featIndexFile = new File((String) getParameterValue("feat-index-file"))).exists()) {
      try {
        fid2feat = ClassifierProvider.loadIdKeyMap(featIndexFile);
        feats = IntStream.range(1, fid2feat.size() + 1).boxed().map(fid2feat::get)
                .collect(toList());
        attributes = new FastVector(fid2feat.size() + 1);
        attributes.addElement(new Attribute("label"));
        feats.stream().map(Attribute::new).forEachOrdered(attributes::addElement);
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
        ibk = (IBk) SerializationHelper.read(modelFile.getAbsolutePath());
      } catch (Exception e) {
        throw new ResourceInitializationException(e);
      }
    }
    return ret;
  }

  @Override
  public double infer(Map<String, Double> features, String label)
          throws AnalysisEngineProcessException {
    double[] values = DoubleStream.concat(DoubleStream.of(Double.NaN),
            feats.stream().mapToDouble(feat -> features.getOrDefault(feat, 0.0))).toArray();
    Instances instances = new Instances("test", attributes, 1);
    instances.setClassIndex(0);
    Instance instance = new SparseInstance(1.0, values);
    instance.setDataset(instances);
    double prob;
    try {
      prob = ibk.distributionForInstance(instance)[label2lid.get(label)];
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    return prob;
  }

  @Override
  public String predict(Map<String, Double> features) throws AnalysisEngineProcessException {
    double[] values = DoubleStream.concat(DoubleStream.of(Double.NaN),
            feats.stream().mapToDouble(feat -> features.getOrDefault(feat, 0.0))).toArray();
    Instances instances = new Instances("test", attributes, 1);
    instances.setClassIndex(0);
    Instance instance = new SparseInstance(1.0, values);
    instance.setDataset(instances);
    double result;
    try {
      result = ibk.classifyInstance(instance);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    return lid2label.get((int) result);
  }

  @Override
  public void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException {
    // create feature to id map
    fid2feat = ClassifierProvider.createFeatureIdKeyMap(X);
    feats = IntStream.range(1, fid2feat.size() + 1).boxed().map(fid2feat::get).collect(toList());
    FastVector attributes = new FastVector(fid2feat.size() + 1);
    attributes.addElement(new Attribute("label"));
    feats.stream().map(Attribute::new).forEachOrdered(attributes::addElement);
    // create label to id map
    lid2label = ClassifierProvider.createLabelIdKeyMap(Y);
    label2lid = lid2label.inverse();
    try {
      ClassifierProvider.saveIdKeyMap(fid2feat, featIndexFile);
      ClassifierProvider.saveIdKeyMap(lid2label, labelIndexFile);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // create instances
    String name = Files.getNameWithoutExtension(modelFile.getName());
    Instances instances = new Instances(name, attributes, X.size());
    instances.setClassIndex(0);
    IntStream.range(0, X.size()).mapToObj(i -> {
      Map<String, Double> x = X.get(i);
      String y = Y.get(i);
      double[] values = DoubleStream.concat(DoubleStream.of(label2lid.get(y)),
              feats.stream().mapToDouble(feat -> x.getOrDefault(feat, 0.0))).toArray();
      return new SparseInstance(1.0, values);
    } ).forEach(instances::add);
    // training
    ibk = new IBk(5);
    ibk.setCrossValidate(true);
    ibk.setDistanceWeighting(new SelectedTag(IBk.WEIGHT_INVERSE, IBk.TAGS_WEIGHTING));
    try {
      ibk.buildClassifier(instances);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    try {
      SerializationHelper.write(modelFile.getAbsolutePath(), ibk);
    } catch (Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
    if (crossValidation) {
      try {
        Evaluation eval = new Evaluation(instances);
        Random rand = new Random();
        eval.crossValidateModel(ibk, instances, 10, rand);
        System.out.println(eval.toSummaryString());
      } catch (Exception e) {
        throw new AnalysisEngineProcessException(e);
      }
    }
  }

}
