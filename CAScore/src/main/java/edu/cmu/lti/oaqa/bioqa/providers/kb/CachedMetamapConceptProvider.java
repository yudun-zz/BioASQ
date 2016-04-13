package edu.cmu.lti.oaqa.bioqa.providers.kb;

import static java.util.stream.Collectors.toList;

import java.io.File;
import java.io.IOException;
import java.io.StringReader;
import java.io.StringWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.CustomResourceSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.impl.CustomResourceSpecifier_impl;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;
import org.w3c.dom.Element;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;

import edu.cmu.lti.oaqa.baseqa.providers.kb.ConceptProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;

public class CachedMetamapConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private MetamapConceptProvider delegate;

  private static final Class<MetamapConceptProvider> delegateClass = MetamapConceptProvider.class;

  private DB db;

  private HTreeMap<String, String> text2mmo;

  private TransformerFactory transFactory;

  private DocumentBuilderFactory buildFactory;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // initialize delegate
    CustomResourceSpecifier delegateResourceSpecifier = new CustomResourceSpecifier_impl();
    delegateResourceSpecifier.setResourceClassName(delegateClass.getCanonicalName());
    delegate = delegateClass.cast(UIMAFramework.produceResource(delegateClass,
            delegateResourceSpecifier, aAdditionalParams));
    // initialize mapdb
    File file = new File((String) getParameterValue("db-file"));
    db = DBMaker.newFileDB(file).compressionEnable().commitFileSyncDisable().cacheSize(2048)
            .closeOnJvmShutdown().make();
    String map = (String) getParameterValue("map-name");
    text2mmo = db.getHashMap(map);
    // serializers
    transFactory = TransformerFactory.newInstance();
    buildFactory = DocumentBuilderFactory.newInstance();
    return ret;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    return getConcepts(Arrays.asList(jcas)).get(0);
  }

  @Override
  public List<List<Concept>> getConcepts(List<JCas> jcases) throws AnalysisEngineProcessException {
    List<String> texts = jcases.stream().map(JCas::getDocumentText)
            .map(MetamapConceptProvider::formatBody).collect(toList());
    // retrieve cached text/mmos and leave missing ones as null
    List<Element> mergedElements = texts.stream().map(text2mmo::get)
            .map(this::convertStringToElement).collect(toList());
    // find missing indexes and collect texts
    int[] missingIndexes = IntStream.range(0, texts.size())
            .filter(i -> mergedElements.get(i) == null).toArray();
    System.out.println(missingIndexes.length + " missing documents.");
    if (missingIndexes.length > 0) {
      List<String> missingTexts = Arrays.stream(missingIndexes).mapToObj(texts::get)
              .collect(toList());
      // retrieve concepts and add to both cache and mergedElements to return
      List<Element> missingElements = delegate.requestConcepts(missingTexts);
      IntStream.range(0, missingIndexes.length).forEach(i -> {
        Element element = missingElements.get(i);
        mergedElements.set(missingIndexes[i], element);
        text2mmo.put(missingTexts.get(i), convertElementToString(element));
      } );
      db.commit();
    }
    return IntStream
            .range(0, jcases.size()).mapToObj(i -> MetamapConceptProvider
                    .parseMetamapServiceMMO(jcases.get(i), mergedElements.get(i)))
            .collect(toList());
  }

  private String convertElementToString(Element element) {
    if (element == null) {
      return null;
    }
    StringWriter buffer = new StringWriter();
    try {
      Transformer transformer = transFactory.newTransformer();
      transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      transformer.setOutputProperty(OutputKeys.INDENT, "no");
      transformer.transform(new DOMSource(element), new StreamResult(buffer));
    } catch (TransformerException e) {
      return null;
    }
    return buffer.toString().replaceAll("\\n", "");
  }

  private Element convertStringToElement(String string) {
    if (string == null) {
      return null;
    }
    try {
      DocumentBuilder builder = buildFactory.newDocumentBuilder();
      Element element = builder.parse(new InputSource(new StringReader(string)))
              .getDocumentElement();
      // builder.reset();
      return element;
    } catch (SAXException | IOException | ParserConfigurationException e) {
      return null;
    }
  }

  @Override
  public void destroy() {
    super.destroy();
    db.commit();
    db.compact();
  }

}
