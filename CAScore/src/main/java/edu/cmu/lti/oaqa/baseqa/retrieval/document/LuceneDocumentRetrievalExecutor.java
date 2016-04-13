package edu.cmu.lti.oaqa.baseqa.retrieval.document;

/**
 * @author Zi Yang
 */

import edu.cmu.lti.oaqa.baseqa.retrieval.query.BooleanBagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.retrieval.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.Document;
import edu.cmu.lti.oaqa.util.TypeFactory;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.FSDirectory;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.nio.file.Paths;
import java.util.Collection;

public class LuceneDocumentRetrievalExecutor extends JCasAnnotator_ImplBase {

  private QueryStringConstructor constructor;

  private int hits;

  private QueryParser parser;

  private IndexReader reader;

  private IndexSearcher searcher;

  private String idFieldName;

  private String titleFieldName;

  private String textFieldName;

  private String uriPrefix;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 100);
    // query constructor
    constructor = UimaContextHelper.createObjectFromConfigParameter(context,
            "query-string-constructor", "query-string-constructor-params",
            BooleanBagOfPhraseQueryStringConstructor.class, QueryStringConstructor.class);
    // lucene
    Analyzer analyzer = UimaContextHelper.createObjectFromConfigParameter(context, "query-analyzer",
            "query-analyzer-params", StandardAnalyzer.class, Analyzer.class);
    String[] fields = UimaContextHelper
            .getConfigParameterStringArrayValue(context, "search-fields");
    parser = new MultiFieldQueryParser(fields, analyzer);
    String index = UimaContextHelper.getConfigParameterStringValue(context, "index");
    try {
      reader = DirectoryReader.open(FSDirectory.open(Paths.get(index)));
    } catch (IOException e) {
      throw new ResourceInitializationException(e);
    }
    searcher = new IndexSearcher(reader);
    idFieldName = UimaContextHelper.getConfigParameterStringValue(context, "id-field", null);
    titleFieldName = UimaContextHelper.getConfigParameterStringValue(context, "title-field", null);
    textFieldName = UimaContextHelper.getConfigParameterStringValue(context, "text-field", null);
    uriPrefix = UimaContextHelper.getConfigParameterStringValue(context, "uri-prefix", null);
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    Collection<AbstractQuery> aqueries = TypeUtil.getAbstractQueries(jcas);
    for (AbstractQuery aquery : aqueries) {
      String queryString = constructor.construct(aquery);
      TopDocs results;
      try {
        Query query = parser.parse(queryString);
        results = searcher.search(query, hits);
      } catch (ParseException | IOException e) {
        throw new AnalysisEngineProcessException(e);
      }
      boolean returnsNotEmpty = false;
      ScoreDoc[] scoreDocs = results.scoreDocs;
      for (int i = 0; i < scoreDocs.length; i++) {
        try {
          convertScoreDocToDocument(jcas, scoreDocs[i], i, queryString).addToIndexes();
        } catch (IOException e) {
          throw new AnalysisEngineProcessException(e);
        }
        returnsNotEmpty = true;
      }
      if (returnsNotEmpty)
        break;
    }
  }

  private Document convertScoreDocToDocument(JCas jcas, ScoreDoc scoreDoc, int rank,
          String queryString) throws IOException {
    org.apache.lucene.document.Document doc = reader.document(scoreDoc.doc);
    String id = idFieldName == null ? null : doc.get(idFieldName);
    String title = titleFieldName == null ? null : doc.get(titleFieldName);
    String text = textFieldName == null ? null : doc.get(textFieldName);
    return TypeFactory.createDocument(jcas, uriPrefix + id, text, rank, queryString, title, id);
  }

}
