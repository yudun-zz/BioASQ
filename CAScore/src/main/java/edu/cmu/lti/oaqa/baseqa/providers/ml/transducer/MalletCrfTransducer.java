package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer;

import static java.util.stream.Collectors.toList;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import cc.mallet.fst.CRF;
import cc.mallet.fst.CRFOptimizableByLabelLikelihood;
import cc.mallet.fst.CRFTrainerByValueGradients;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.optimize.Optimizable;
import cc.mallet.types.Alphabet;
import cc.mallet.types.ArraySequence;
import cc.mallet.types.FeatureVector;
import cc.mallet.types.FeatureVectorSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.Sequence;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;

public class MalletCrfTransducer extends ConfigurableProvider implements TransducerProvider {

  private String modelFile;

  private CRF crf;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    if (Files.exists(Paths.get(modelFile = (String) getParameterValue("model-file")))) {
      try (ObjectInputStream oos = new ObjectInputStream(new FileInputStream(modelFile))) {
        crf = (CRF) oos.readObject();
        oos.close();
      } catch (IOException | ClassNotFoundException e) {
        throw new ResourceInitializationException(e);
      }
    }
    return ret;
  }

  @Override
  public List<String> predict(List<Map<String, Double>> featuresSequence)
          throws AnalysisEngineProcessException {
    Alphabet localDataAlphabet = new Alphabet(
            featuresSequence.stream().map(Map::keySet).flatMap(Set::stream).distinct().toArray());
    FeatureVector[] dataFeatureVectors = featuresSequence.stream()
            .map(x -> new FeatureVector(localDataAlphabet,
                    x.entrySet().stream().map(Map.Entry::getKey).toArray(),
                    x.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray()))
            .toArray(FeatureVector[]::new);
    FeatureVectorSequence inputSequence = new FeatureVectorSequence(dataFeatureVectors);
    @SuppressWarnings("unchecked")
    Sequence<String> labels = (Sequence<String>) crf
            .label(new Instance(inputSequence, null, null, null)).getTarget();
    return IntStream.range(0, labels.size()).mapToObj(labels::get).collect(toList());
  }

  @Override
  public double infer(List<Map<String, Double>> featuresSequence, List<String> labelSequence)
          throws AnalysisEngineProcessException {
    Alphabet localDataAlphabet = new Alphabet(
            featuresSequence.stream().map(Map::keySet).flatMap(Set::stream).distinct().toArray());
    FeatureVector[] dataFeatureVectors = featuresSequence.stream()
            .map(x -> new FeatureVector(localDataAlphabet,
                    x.entrySet().stream().map(Map.Entry::getKey).toArray(),
                    x.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray()))
            .toArray(FeatureVector[]::new);
    FeatureVectorSequence inputSequence = new FeatureVectorSequence(dataFeatureVectors);
    Sequence<String> outputSequence = new ArraySequence<>(labelSequence.toArray(new String[0]));
    double logScore = new SumLatticeDefault(crf, inputSequence, outputSequence).getTotalWeight();
    double logZ = new SumLatticeDefault(crf, inputSequence).getTotalWeight();
    return Math.exp(logScore - logZ);
  }

  @Override
  public void train(List<List<Map<String, Double>>> X, List<List<String>> Y, String zerothLabel)
          throws AnalysisEngineProcessException {
    // get input/output alphabets
    Alphabet dataAlphabet = new Alphabet(X.stream().flatMap(List::stream).map(Map::keySet)
            .flatMap(Set::stream).distinct().toArray());
    LabelAlphabet labelAlphabet = new LabelAlphabet();
    Y.stream().flatMap(List::stream).forEach(labelAlphabet::lookupIndex);
    // create training data
    assert X.size() == Y.size();
    InstanceList trainingData = new InstanceList(dataAlphabet, labelAlphabet);
    IntStream.range(0, X.size()).mapToObj(i -> {
      FeatureVector[] dataFeatureVectors = X.get(i).stream()
              .map(x -> new FeatureVector(dataAlphabet,
                      x.entrySet().stream().map(Map.Entry::getKey).toArray(),
                      x.entrySet().stream().mapToDouble(Map.Entry::getValue).toArray()))
              .toArray(FeatureVector[]::new);
      Label[] labels = Y.get(i).stream().map(labelAlphabet::lookupLabel).toArray(Label[]::new);
      return new Instance(new FeatureVectorSequence(dataFeatureVectors), new LabelSequence(labels),
              null, null);
    } ).forEach(trainingData::add);
    // model
    crf = new CRF(dataAlphabet, labelAlphabet);
    // construct the finite state machine
    addStatesAndZerothStateForLabelsConnectedAsIn(crf, Y, zerothLabel);
    // initialize model's weights
    crf.setWeightsDimensionAsIn(trainingData, false);
    // CRFOptimizableBy* objects (terms in the objective function)
    // objective 1: label likelihood objective
    Optimizable.ByGradientValue optLabel = new CRFOptimizableByLabelLikelihood(crf, trainingData);
    // CRF trainer
    Optimizable.ByGradientValue[] opts = new Optimizable.ByGradientValue[] { optLabel };
    // by default, use L-BFGS as the optimizer
    CRFTrainerByValueGradients crft = new CRFTrainerByValueGradients(crf, opts);
    // train until convergence
    crft.setMaxResets(0);
    crft.train(trainingData, 1000);
    // save the trained model (if CRFWriter is not used)
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile))) {
      oos.writeObject(crf);
      oos.close();
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    try (PrintWriter pw = new PrintWriter(new FileWriter(modelFile + "-print"))) {
      crf.print(pw);
      pw.close();
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
  }

  private static void addStatesAndZerothStateForLabelsConnectedAsIn(CRF crf, List<List<String>> Y,
          String zerothLabel) {
    SetMultimap<String, String> connections = HashMultimap.create();
    Y.stream().forEach(y -> {
      IntStream.range(0, y.size() - 1).forEach(i -> connections.put(y.get(i), y.get(i + 1)));
      connections.put(zerothLabel, y.get(0));
    } );
    connections.asMap().entrySet().stream().forEach(entry -> {
      String label = entry.getKey();
      String[] destinatiions = entry.getValue().toArray(new String[0]);
      crf.addState(label, label.equals(zerothLabel) ? 0.0 : Double.NEGATIVE_INFINITY, 0.0,
              destinatiions, destinatiions);
    } );
  }

}
