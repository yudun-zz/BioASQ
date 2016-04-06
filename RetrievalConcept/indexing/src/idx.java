/**
 * Created by Sherry on 4/5/16.
 */

import java.io.*;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.*;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.search.ScoreDoc;

public class idx {

    public static final String FILES_TO_INDEX_DIRECTORY = "ontology";
    public static final String INDEX_DIRECTORY = "indexDirectory";

    public static final String FIELD_ID = "id";
    public static final String FIELD_NAME = "name";
    public static final String FIELD_DEF = "definition";
    public static final String FIELD_SYN = "synonym";

    public static void main(String[] args) throws Exception {

        //createIndex();
        searchIndex("definition:sarcoma");
        //searchIndex(args[1], args[2]);
    }

    public static void createIndex() throws CorruptIndexException, LockObtainFailedException, IOException {
        Analyzer analyzer = new StandardAnalyzer();
        Directory directory = FSDirectory.open(new File(INDEX_DIRECTORY).toPath());
        IndexWriterConfig config = new IndexWriterConfig(analyzer);
        IndexWriter indexWriter = new IndexWriter(directory, config);

        File dir = new File(FILES_TO_INDEX_DIRECTORY);
        File[] files = dir.listFiles();
        for (File file : files) {
            if (file.getName().compareTo(".DS_Store") == 0){continue;}

            BufferedReader reader = new BufferedReader(new FileReader(file));

            String line = null;
            while ((line = reader.readLine()) != null){
                Document document = new Document();
                String []terms = line.split("@#");
                if (terms.length != 4){
                    System.out.println("Error data format");
                    return;
                }
                document.add(new Field(FIELD_ID, terms[0], TextField.TYPE_STORED));
                if (terms[1].compareTo("null") != 0){
                    document.add(new Field(FIELD_NAME, terms[1].substring(4), TextField.TYPE_STORED));
                }
                if (terms[2].compareTo("null") != 0){
                    document.add(new Field(FIELD_DEF, terms[2].substring(4), TextField.TYPE_STORED));
                }
                if (terms[3].compareTo("null") != 0){
                    document.add(new Field(FIELD_SYN, terms[3].substring(4), TextField.TYPE_STORED));
                }
                indexWriter.addDocument(document);
            }
        }
        indexWriter.close();
    }

    public static void searchIndex(String searchString) throws IOException, ParseException {
        System.out.println("Searching for '" + searchString + "'");
        Directory directory = FSDirectory.open(new File(INDEX_DIRECTORY).toPath());
        DirectoryReader indexReader = DirectoryReader.open(directory);
        IndexSearcher indexSearcher = new IndexSearcher(indexReader);
        QueryParser queryParser;
        Analyzer analyzer = new StandardAnalyzer();

        queryParser = new QueryParser(FIELD_DEF, analyzer);


        Query query = queryParser.parse(searchString);
        ScoreDoc[] hits = indexSearcher.search(query, null, 1000).scoreDocs;
        System.out.println("Number of hits: " + hits.length);
        for (int i = 0; i < hits.length; i ++){
            Document hitDoc = indexSearcher.doc(hits[i].doc);
            System.out.println("This is the text to be indexed: " + hitDoc.get(FIELD_ID));
        }

    }

}