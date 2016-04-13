package edu.cmu.lti.oaqa.baseqa.providers.ml.transducer;

import static java.util.stream.Collectors.counting;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.SetMultimap;

import cc.mallet.fst.HMM;
import cc.mallet.fst.HMMTrainerByLikelihood;
import cc.mallet.fst.SumLatticeDefault;
import cc.mallet.types.Alphabet;
import cc.mallet.types.ArraySequence;
import cc.mallet.types.FeatureSequence;
import cc.mallet.types.Instance;
import cc.mallet.types.InstanceList;
import cc.mallet.types.Label;
import cc.mallet.types.LabelAlphabet;
import cc.mallet.types.LabelSequence;
import cc.mallet.types.Sequence;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;

public class MalletHmmTransducer extends ConfigurableProvider implements TransducerProvider {

  private static final String DEFAULT_TOKEN = "<DEFAULT>";

  private String modelFile;

  private HMM hmm;

  private Set<String> vocabulary;

  private String featurePrefix;

  @SuppressWarnings("unchecked")
  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    if (Files.exists(Paths.get(modelFile = (String) getParameterValue("model-file")))) {
      try (ObjectInputStream oos = new ObjectInputStream(new FileInputStream(modelFile))) {
        hmm = (HMM) oos.readObject();
        vocabulary = (Set<String>) oos.readObject();
        oos.close();
      } catch (IOException | ClassNotFoundException e) {
        throw new ResourceInitializationException(e);
      }
    }
    featurePrefix = (String) getParameterValue("feature-prefix");
    return ret;
  }

  @Override
  public List<String> predict(List<Map<String, Double>> featuresSequence)
          throws AnalysisEngineProcessException {
    List<String> dataSequence = featuresSequence.stream().map(this::getObservation)
            .map(obs -> vocabulary.contains(obs) ? obs : DEFAULT_TOKEN).collect(toList());
    Alphabet localDataAlphabet = new Alphabet(dataSequence.stream().distinct().toArray());
    int[] dataIndexes = dataSequence.stream().mapToInt(localDataAlphabet::lookupIndex).toArray();
    @SuppressWarnings("unchecked")
    Sequence<String> labels = (Sequence<String>) hmm.label(
            new Instance(new FeatureSequence(localDataAlphabet, dataIndexes), null, null, null))
            .getTarget();
    return IntStream.range(0, labels.size()).mapToObj(labels::get).collect(toList());
  }

  @Override
  public double infer(List<Map<String, Double>> featuresSequence, List<String> labelSequence)
          throws AnalysisEngineProcessException {
    List<String> dataSequence = featuresSequence.stream().map(this::getObservation)
            .map(obs -> vocabulary.contains(obs) ? obs : DEFAULT_TOKEN).collect(toList());
    Alphabet localDataAlphabet = new Alphabet(dataSequence.stream().distinct().toArray());
    int[] dataIndexes = dataSequence.stream().mapToInt(localDataAlphabet::lookupIndex).toArray();
    FeatureSequence inputSequence = new FeatureSequence(localDataAlphabet, dataIndexes);
    Sequence<String> outputSequence = new ArraySequence<>(labelSequence.toArray(new String[0]));
    double logScore = new SumLatticeDefault(hmm, inputSequence, outputSequence).getTotalWeight();
    double logZ = new SumLatticeDefault(hmm, inputSequence).getTotalWeight();
    return Math.exp(logScore - logZ);
  }

  @Override
  public void train(List<List<Map<String, Double>>> X, List<List<String>> Y, String zerothLabel)
          throws AnalysisEngineProcessException {
    // get input/output alphabets
    Map<String, Long> obsv2count = X.stream().flatMap(List::stream).map(this::getObservation)
            .collect(Collectors.groupingBy(Function.identity(), counting()));
    vocabulary = obsv2count.entrySet().stream().filter(entry -> entry.getValue() >= 2)
            .map(Map.Entry::getKey).collect(toSet());
    vocabulary.add(DEFAULT_TOKEN);
    Alphabet dataAlphabet = new Alphabet(vocabulary.toArray());
    LabelAlphabet labelAlphabet = new LabelAlphabet();
    Y.stream().flatMap(List::stream).forEach(labelAlphabet::lookupIndex);
    // create training data
    assert X.size() == Y.size();
    InstanceList trainingData = new InstanceList(dataAlphabet, labelAlphabet);
    IntStream.range(0, X.size()).mapToObj(i -> {
      int[] dataIndexes = X.get(i).stream().map(this::getObservation)
              .map(obs -> vocabulary.contains(obs) ? obs : DEFAULT_TOKEN)
              .mapToInt(dataAlphabet::lookupIndex).toArray();
      Label[] labels = Y.get(i).stream().map(labelAlphabet::lookupLabel).toArray(Label[]::new);
      return new Instance(new FeatureSequence(dataAlphabet, dataIndexes), new LabelSequence(labels),
              null, null);
    }).forEach(trainingData::add);
    // model
    hmm = new HMM(dataAlphabet, labelAlphabet);
    // construct the finite state machine
    addStatesAndZerothStateForLabelsConnectedAsIn(hmm, Y, zerothLabel);
    // train
    HMMTrainerByLikelihood hmmt = new HMMTrainerByLikelihood(hmm);
    hmmt.train(trainingData, 1000);
    // save the trained model (if CRFWriter is not used)
    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(modelFile))) {
      oos.writeObject(hmm);
      oos.writeObject(vocabulary);
      oos.close();
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    hmm.print();
  }

  private static void addStatesAndZerothStateForLabelsConnectedAsIn(HMM hmm, List<List<String>> Y,
          String zerothLabel) {
    SetMultimap<String, String> connections = HashMultimap.create();
    Y.stream().forEach(y -> {
      IntStream.range(0, y.size() - 1).forEach(i -> connections.put(y.get(i), y.get(i + 1)));
      connections.put(zerothLabel, y.get(0));
    });
    connections.asMap().entrySet().stream().forEach(entry -> {
      String label = entry.getKey();
      String[] destinatiions = entry.getValue().toArray(new String[0]);
      hmm.addState(label, label.equals(zerothLabel) ? 0.0 : Double.NEGATIVE_INFINITY, 0.0,
              destinatiions, destinatiions);
    });
  }

  private String getObservation(Map<String, Double> features) {
    return features.entrySet().stream().filter(entry -> entry.getValue() > 0.0)
            .map(Map.Entry::getKey).filter(key -> key.startsWith(featurePrefix))
            .map(key -> key.substring(featurePrefix.length())).findAny().get();
  }

}
