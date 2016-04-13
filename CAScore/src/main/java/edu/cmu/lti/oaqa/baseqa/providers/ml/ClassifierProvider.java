package edu.cmu.lti.oaqa.baseqa.providers.ml;

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toMap;
import static java.util.stream.Collectors.toSet;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.IntStream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.Resource;

import com.google.common.base.Charsets;
import com.google.common.collect.BiMap;
import com.google.common.collect.HashBiMap;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import com.google.common.io.Files;

public interface ClassifierProvider extends Resource {

  double infer(Map<String, Double> features, String label) throws AnalysisEngineProcessException;

  String predict(Map<String, Double> features) throws AnalysisEngineProcessException;

  default List<String> predict(Map<String, Double> features, int k)
          throws AnalysisEngineProcessException {
    return Arrays.asList(predict(features));
  }

  default void trainMultiLabel(List<Map<String, Double>> X, List<List<String>> Y)
          throws AnalysisEngineProcessException {
    trainMultiLabel(X, Y, true);
  }

  default void trainMultiLabel(List<Map<String, Double>> X, List<List<String>> Y,
          boolean crossValidation) throws AnalysisEngineProcessException {
    int size = X.size();
    assert size == Y.size();
    List<Map<String, Double>> XX = new ArrayList<Map<String, Double>>();
    List<String> YY = new ArrayList<>();
    IntStream.range(0, size).forEach(i -> {
      List<String> y = Y.get(i);
      YY.addAll(y);
      XX.addAll(Collections.nCopies(y.size(), X.get(i)));
    });
    train(XX, YY, crossValidation);
  }

  default void train(List<Map<String, Double>> X, List<String> Y)
          throws AnalysisEngineProcessException {
    train(X, Y, true);
  }

  void train(List<Map<String, Double>> X, List<String> Y, boolean crossValidation)
          throws AnalysisEngineProcessException;

  default List<Double> crossTrainInferMultiLabel(List<Map<String, Double>> X, List<List<String>> Y,
          String label) throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    List<Double> ret = IntStream.range(0, X.size()).mapToObj(i -> Double.NaN).collect(toList());
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, 10)) {
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<List<String>> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).stream().forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      trainMultiLabel(cvTrainX, cvTrainY, false);
      for (int cvTestIndex : cvTestIndexes) {
        double result = infer(X.get(cvTestIndex), label);
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  default List<List<String>> crossTrainPredictMultiLabel(List<Map<String, Double>> X,
          List<List<String>> Y, int limit) throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    List<List<String>> ret = IntStream.range(0, X.size()).mapToObj(i -> new ArrayList<String>())
            .collect(toList());
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, 10)) {
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<List<String>> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).stream().forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      trainMultiLabel(cvTrainX, cvTrainY, false);
      for (int cvTestIndex : cvTestIndexes) {
        List<String> result = predict(X.get(cvTestIndex), limit).stream().collect(toList());
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  default List<Double> crossTrainInfer(List<Map<String, Double>> X, List<String> Y, String label)
          throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    List<Double> ret = IntStream.range(0, X.size()).mapToObj(i -> Double.NaN).collect(toList());
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, 10)) {
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<String> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).stream().forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      train(cvTrainX, cvTrainY, false);
      for (int cvTestIndex : cvTestIndexes) {
        double result = infer(X.get(cvTestIndex), label);
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  default List<List<String>> crossTrainPredict(List<Map<String, Double>> X, List<String> Y,
          int limit) throws AnalysisEngineProcessException {
    Set<Integer> indexes = IntStream.range(0, X.size()).boxed().collect(toSet());
    List<Integer> indexList = new ArrayList<>(indexes);
    Collections.shuffle(indexList);
    List<List<String>> ret = IntStream.range(0, X.size()).mapToObj(i -> new ArrayList<String>())
            .collect(toList());
    for (List<Integer> cvTestIndexes : Lists.partition(indexList, 10)) {
      List<Map<String, Double>> cvTrainX = new ArrayList<>();
      List<String> cvTrainY = new ArrayList<>();
      Sets.difference(indexes, new HashSet<>(cvTestIndexes)).stream().forEach(cvTrainIndex -> {
        cvTrainX.add(X.get(cvTrainIndex));
        cvTrainY.add(Y.get(cvTrainIndex));
      });
      train(cvTrainX, cvTrainY, false);
      for (int cvTestIndex : cvTestIndexes) {
        List<String> result = predict(X.get(cvTestIndex), limit).stream().collect(toList());
        ret.set(cvTestIndex, result);
      }
    }
    return ret;
  }

  static Map<Integer, String> createFeatureIdKeyMap(List<Map<String, Double>> X) {
    List<String> feats = X.stream().map(Map::keySet).flatMap(Set::stream).distinct()
            .collect(toList());
    return IntStream.range(0, feats.size()).boxed().collect(toMap(i -> i + 1, i -> feats.get(i)));
  }

  static BiMap<Integer, String> createLabelIdKeyMap(List<String> Y) {
    List<String> labels = Y.stream().distinct().collect(toList());
    BiMap<Integer, String> lid2label = HashBiMap.create();
    IntStream.range(0, labels.size()).forEach(i -> lid2label.put(i + 1, labels.get(i)));
    return lid2label;
  }

  static void saveIdKeyMap(Map<Integer, String> id2key, File idKeyMapFile) throws IOException {
    String lines = id2key.entrySet().stream().map(entry -> entry.getKey() + "\t" + entry.getValue())
            .collect(joining("\n"));
    Files.write(lines, idKeyMapFile, Charsets.UTF_8);
  }

  static Map<Integer, String> loadIdKeyMap(File idKeyMapFile) throws IOException {
    return Files.readLines(idKeyMapFile, Charsets.UTF_8).stream().map(line -> line.split("\t"))
            .collect(toMap(segs -> Integer.parseInt(segs[0]), segs -> segs[1]));
  }

}
