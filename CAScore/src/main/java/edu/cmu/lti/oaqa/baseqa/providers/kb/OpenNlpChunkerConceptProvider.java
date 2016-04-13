package edu.cmu.lti.oaqa.baseqa.providers.kb;

import static java.util.stream.Collectors.toList;

import java.io.InputStream;
import java.util.Arrays;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import com.aliasi.util.Streams;

import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import edu.cmu.lti.oaqa.type.kb.Concept;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import opennlp.tools.chunker.ChunkerME;
import opennlp.tools.chunker.ChunkerModel;
import opennlp.tools.util.Span;

/**
 * 
 * @author Zi Yang
 *
 */
public class OpenNlpChunkerConceptProvider extends ConfigurableProvider implements ConceptProvider {

  private ChunkerME chunker;

  private List<String> type;

  private int minLength;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    String model = String.class.cast(getParameterValue("chunker-model"));
    try (InputStream ois = getClass().getResourceAsStream(model)) {
      chunker = new ChunkerME(new ChunkerModel(ois));
      Streams.closeQuietly(ois);
    } catch (Exception e) {
      throw new ResourceInitializationException(e);
    }
    type = Arrays.asList(String.class.cast(getParameterValue("type")).split(","));
    minLength = Integer.class.cast(getParameterValue("min-length"));
    return ret;
  }

  @Override
  public List<Concept> getConcepts(JCas jcas) throws AnalysisEngineProcessException {
    List<Token> tokens = TypeUtil.getOrderedTokens(jcas);
    String[] texts = tokens.stream().map(Token::getCoveredText).toArray(String[]::new);
    String[] pos = tokens.stream().map(Token::getPartOfSpeech).toArray(String[]::new);
    List<Span> spans = insertOutsideSpans(chunker.chunkAsSpans(texts, pos));
    return IntStream.rangeClosed(0, spans.size() - type.size())
            .mapToObj(i -> spans.subList(i, i + type.size()))
            .filter(spansSublist -> type
                    .equals(spansSublist.stream().map(Span::getType).collect(toList())))
            .map(spansSublist -> tokens.subList(spansSublist.get(0).getStart(),
                    spansSublist.get(spansSublist.size() - 1).getEnd()))
            .filter(toks -> toks.size() >= minLength)
            .map(toks -> TypeFactory.createConceptMention(jcas, toks.get(0).getBegin(),
                    toks.get(toks.size() - 1).getEnd()))
            .map(cmention -> TypeFactory.createConcept(jcas, cmention,
                    TypeFactory.createConceptType(jcas, "opennlp:" + String.join("-", type))))
            .collect(toList());
  }

  private static List<Span> insertOutsideSpans(Span[] spans) {
    List<Span> spansWithO = new LinkedList<>(Arrays.asList(spans));
    IntStream.range(0, spans.length - 1).filter(i -> spans[i].getEnd() < spans[i + 1].getStart())
            .forEach(i -> spansWithO.add(spansWithO.indexOf(spans[i + 1]),
                    new Span(spans[i].getEnd(), spans[i + 1].getStart(), "O")));
    return spansWithO;
  }

}
