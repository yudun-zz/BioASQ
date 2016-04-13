package edu.cmu.lti.oaqa.baseqa.retrieval.passage;

import com.aliasi.sentences.IndoEuropeanSentenceModel;
import com.aliasi.sentences.SentenceChunker;
import com.aliasi.sentences.SentenceModel;
import com.aliasi.tokenizer.IndoEuropeanTokenizerFactory;
import com.aliasi.tokenizer.TokenizerFactory;
import edu.cmu.lti.oaqa.baseqa.providers.nlp.NlpProvider;
import edu.cmu.lti.oaqa.baseqa.retrieval.query.BagOfPhraseQueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.retrieval.query.QueryStringConstructor;
import edu.cmu.lti.oaqa.baseqa.retrieval.util.RetrievalUtil;
import edu.cmu.lti.oaqa.baseqa.util.ProviderCache;
import edu.cmu.lti.oaqa.baseqa.util.UimaContextHelper;
import edu.cmu.lti.oaqa.bioasq.util.StanfordLemmatizer;
import edu.cmu.lti.oaqa.type.nlp.Token;
import edu.cmu.lti.oaqa.type.retrieval.AbstractQuery;
import edu.cmu.lti.oaqa.type.retrieval.Passage;
import edu.cmu.lti.oaqa.util.TypeUtil;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.similarities.BM25Similarity;
import org.apache.lucene.store.RAMDirectory;
import org.apache.uima.UIMAException;
import org.apache.uima.UimaContext;
import org.apache.uima.analysis_component.JCasAnnotator_ImplBase;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.fit.factory.JCasFactory;
import org.apache.uima.jcas.JCas;
import org.apache.uima.resource.ResourceInitializationException;

import java.io.IOException;
import java.util.*;

import static java.util.stream.Collectors.toList;

/**
 * @author Xiangyu Sun, Zi Yang
 */
public class LuceneInMemorySentenceRetrievalExecutor extends JCasAnnotator_ImplBase {

  private Analyzer analyzer;

  private int hits;

  private QueryParser parser;

  private SentenceChunker chunker;

  private QueryStringConstructor queryStringConstructor;

  private NlpProvider nlpProvider;

  private StanfordLemmatizer lemma;

  //private static GoldQuestions questions;

  //private static HashMap<String, HashSet<Snippet>> gold;

  @Override
  public void initialize(UimaContext context) throws ResourceInitializationException {
    super.initialize(context);
    TokenizerFactory tokenizerFactory = UimaContextHelper.createObjectFromConfigParameter(context,
            "tokenizer-factory", "tokenizer-factory-params", IndoEuropeanTokenizerFactory.class,
            TokenizerFactory.class);
    SentenceModel sentenceModel = UimaContextHelper.createObjectFromConfigParameter(context,
            "sentence-model", "sentence-model-params", IndoEuropeanSentenceModel.class,
            SentenceModel.class);
    chunker = new SentenceChunker(tokenizerFactory, sentenceModel);
    // initialize hits
    hits = UimaContextHelper.getConfigParameterIntValue(context, "hits", 200);
    // initialize query analyzer, index writer config, and query parser
    analyzer = UimaContextHelper.createObjectFromConfigParameter(context, "query-analyzer",
            "query-analyzer-params", StandardAnalyzer.class, Analyzer.class);
    parser = new QueryParser("text", analyzer);
    // initialize query string constructor
    queryStringConstructor = UimaContextHelper.createObjectFromConfigParameter(context,
            "query-string-constructor", "query-string-constructor-params",
            BagOfPhraseQueryStringConstructor.class, QueryStringConstructor.class);
    String nlpProviderName = UimaContextHelper
            .getConfigParameterStringValue(context, "nlp-provider");
    nlpProvider = ProviderCache.getProvider(nlpProviderName, NlpProvider.class);

    lemma = new StanfordLemmatizer();
  }

  @Override
  public void process(JCas jcas) throws AnalysisEngineProcessException {
    // create lucene documents for all sentences in all sections and delete the duplicate ones
    Map<Integer, Passage> hash2passage = new HashMap<Integer, Passage>();
    for (Passage d : TypeUtil.getRankedPassages(jcas)) {
      for (Passage s : RetrievalUtil.extractSentences(jcas, d, chunker)) {
        if (!hash2passage.containsKey(TypeUtil.hash(s))) {
          hash2passage.put(TypeUtil.hash(s), s);
        }
      }
    }
    // remove the documents from pipeline
    TypeUtil.getRankedPassages(jcas).forEach(Passage::removeFromIndexes);
    List<Document> luceneDocs = hash2passage.values().stream()
            .map(sent -> RetrievalUtil.createLuceneDocument(sent)).collect(toList());
    // create lucene index
    RAMDirectory index = new RAMDirectory();
    try (IndexWriter writer = new IndexWriter(index, new IndexWriterConfig(analyzer))) {
      writer.addDocuments(luceneDocs);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // search in the index
    AbstractQuery aquery = TypeUtil.getAbstractQueries(jcas).stream().findFirst().get();
    Map<Integer, Float> hash2score = new HashMap<>();
    try (IndexReader reader = DirectoryReader.open(index)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      String queryString = queryStringConstructor.construct(aquery).replace("\"", " ")
              .replace("/", " ").replace("[", " ").replace("]", " ");
      System.out.println("Search for query: " + queryString);

      // construct the query
      Query query = parser.parse(queryString);
      System.out.println(query.toString());
      searcher.setSimilarity(new BM25Similarity());
      ScoreDoc[] scoreDocs = searcher.search(query, hits).scoreDocs;
      for (ScoreDoc scoreDoc : scoreDocs) {
        float score = scoreDoc.score;
        int hash;
        hash = Integer.parseInt(searcher.doc(scoreDoc.doc).get("hash"));
        hash2score.put(hash, score);
      }
    } catch (IOException | ParseException e) {
      throw new AnalysisEngineProcessException(e);
    }
    System.out.println("The size of Returned Sentences:\t" + hash2score.size());
    // add to CAS
    hash2score.entrySet().stream().map(entry -> {
      Passage passage = hash2passage.get(entry.getKey());
      passage.setScore(entry.getValue());
      return passage;
    }).sorted(Comparator.comparing(Passage::getScore).reversed()).forEach(Passage::addToIndexes);

    Collection<Passage> snippets = TypeUtil.getRankedPassages(jcas);

    for (Passage psg : snippets)
      System.out.println(psg.getText());

    // rank the snippet and add them to pipeline
    rankSnippets(jcas, calSkip(jcas, hash2passage),
            calBM25(jcas, hash2passage),
            calAlignment(jcas, hash2passage),
            calSentenceLength(hash2passage),
            hash2passage
    );

  }

  /* 
   * Combine all the evidence of snippet and rank them
   *  */
  private void rankSnippets(JCas jcas, Map<Integer, Float> skip_bigram, Map<Integer, Float> bm25,
          Map<Integer, Float> alignment, Map<Integer, Float> length,
          Map<Integer, Passage> hash2passage) throws AnalysisEngineProcessException {
    HashMap<Integer, Float> hash2score = new HashMap<Integer, Float>();
    double[] params = { -3, -3436.8, -0.2, 0, 0.3 };
    for (Integer it : hash2passage.keySet()) {
      double wT = skip_bigram.get(it) * params[0] +
              alignment.get(it) * params[1] +
              length.get(it) * params[2] +
              (bm25.get(it) == null ? 0 : bm25.get(it)) * params[3] + params[4];
      hash2score.put(it, (float) Math.exp(wT) / (float) (1 + Math.exp(wT)));
    }
    hash2score.entrySet().stream().map(entry -> {
      Passage passage = hash2passage.get(entry.getKey());
      passage.setScore(entry.getValue());
      return passage;
    }).sorted(Comparator.comparing(Passage::getScore).reversed()).forEach(Passage::addToIndexes);

  }

  /*
   * Use dependency relations to calculate skip-bigram score
   * */
  private Map<Integer, Float> calSkip(JCas jcas, Map<Integer, Passage> hash2passage)
          throws AnalysisEngineProcessException {
    HashMap<Integer, Float> skip_bigram = new HashMap<Integer, Float>();
    String question = TypeUtil.getQuestion(jcas).getText();
    // question sentence analysis
    HashMap<String, String> questionTokens = sentenceAnalysis(question);
    for (Map.Entry<Integer, Passage> iter : hash2passage.entrySet()) {
      String text = iter.getValue().getText();
      HashMap<String, String> snippetTokens = sentenceAnalysis(text);
      int count = 0;
      for (String child : snippetTokens.keySet()) {
        if (questionTokens.containsKey(child) &&
                questionTokens.get(child) == snippetTokens.get(child))
          count++;
      }
      float scoreP = (float) count / (float) snippetTokens.size();
      float scoreQ = (float) count / (float) questionTokens.size();
      float score = scoreP * scoreQ / (scoreP + scoreQ);
      if (count == 0)
        score = 0;
      ;
      skip_bigram.put(iter.getKey(), score);
    }
    return skip_bigram;
  }

  /*
   * Dynamic programming to cal the algiment score
   * */
  private Map<Integer, Float> calAlignment(JCas jcas, Map<Integer, Passage> hash2passage)
          throws AnalysisEngineProcessException {
    HashMap<Integer, Float> alignment = new HashMap<Integer, Float>();
    String question = TypeUtil.getQuestion(jcas).getText();
    String[] questionTokens = lemma.stemText(question).split(" ");
    for (Integer it : hash2passage.keySet()) {
      String[] text = hash2passage.get(it).getText().split(" ");
      int[][] score = new int[text.length][questionTokens.length];
      // initate score
      for (int i = 0; i < text.length; i++) {
        if (text[i].equals(questionTokens[0]))
          score[i][0] = 1;
      }
      for (int i = 0; i < questionTokens.length; i++) {
        if (text[0].equals(questionTokens[i]))
          score[0][i] = 1;
      }
      // start calculating
      for (int i = 1; i < text.length; i++) {
        for (int j = 1; j < questionTokens.length; j++) {
          if (text[i].equals(questionTokens[j]))
            score[i][j] = Integer.max(score[i][j], score[i - 1][j - 1] + 1);
          else
            score[i][j] = Integer.max(score[i - 1][j], score[i][j - 1]);
        }
      }
      alignment.put(it, (float) score[text.length - 1][questionTokens.length - 1]);
    }
    return alignment;
  }

  private Map<Integer, Float> calBM25(JCas jcas, Map<Integer, Passage> hash2passage)
          throws AnalysisEngineProcessException {
    // index the documents using lucene
    List<Document> luceneDocs = hash2passage.values().stream()
            .map(sent -> RetrievalUtil.createLuceneDocument(sent)).collect(toList());
    // create lucene index
    RAMDirectory index = new RAMDirectory();
    try (IndexWriter writer = new IndexWriter(index,
            new IndexWriterConfig(analyzer))) {
      writer.addDocuments(luceneDocs);
    } catch (IOException e) {
      throw new AnalysisEngineProcessException(e);
    }
    // search in the index
    AbstractQuery aquery = TypeUtil.getAbstractQueries(jcas).stream().findFirst().get();
    Map<Integer, Float> hash2score = new HashMap<>();
    try (IndexReader reader = DirectoryReader.open(index)) {
      IndexSearcher searcher = new IndexSearcher(reader);
      String queryString = queryStringConstructor.construct(aquery).replace("\"", " ")
              .replace("/", " ").replace("[", " ").replace("]", " ");
      System.out.println("Search for query: " + queryString);

      // construct the query
      Query query = parser.parse(queryString);
      searcher.setSimilarity(new BM25Similarity());
      ScoreDoc[] scoreDocs = searcher.search(query, hits).scoreDocs;
      for (ScoreDoc scoreDoc : scoreDocs) {
        float score = scoreDoc.score;
        int hash;
        hash = Integer.parseInt(searcher.doc(scoreDoc.doc).get("hash"));
        hash2score.put(hash, score);
      }
    } catch (IOException | ParseException e) {
      throw new AnalysisEngineProcessException(e);
    }
    return hash2score;
  }

  /* 
   * Dependency Analysis for all the snippets and questions 
   * */
  private HashMap<String, String> sentenceAnalysis(String sentence) {
    HashMap<String, String> dependency = new HashMap<String, String>();
    try {
      JCas snippetJcas = JCasFactory.createJCas();
      snippetJcas.setDocumentText(sentence);
      List<Token> tokens = nlpProvider.parseDependency(snippetJcas);
      for (Token tok : tokens) {
        if (tok.getHead() == null)
          continue;
        dependency.put(tok.getLemmaForm(), tok.getHead().getLemmaForm());
      }
      snippetJcas.release();
    } catch (UIMAException err) {
      err.printStackTrace();
    }
    return dependency;
  }

  /* 
   * calculating the length of all the snippet 
   * */
  private HashMap<Integer, Float> calSentenceLength(Map<Integer, Passage> hash2passage) {
    HashMap<Integer, Float> ret = new HashMap<Integer, Float>();
    for (Integer it : hash2passage.keySet()) {
      ret.put(it, (float) hash2passage.get(it).getText().length());
    }
    return ret;
  }

}
