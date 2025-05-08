/**
 * CSC 583 Final Project Jeopardy 
 * @authors: Amimul Ehsan Zoha, Taoseef Aziz
 * 
 */

package org.example;
import java.io.*;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.StopFilterFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.miscellaneous.HyphenatedWordsFilterFactory;
import org.apache.lucene.analysis.miscellaneous.KeywordRepeatFilterFactory;
import org.apache.lucene.analysis.snowball.SnowballPorterFilterFactory;
import org.apache.lucene.analysis.standard.StandardTokenizerFactory;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StringField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;

public class App {

    static final String WIKI_DIR = "src/main/resources/wiki-subset-20140602/";
    static final String QUESTIONS_FILE = "src/main/resources/questions.txt";
    static final String OUTPUT_FILE = "src/main/resources/queriesProcessedBaseline.txt";

    private final Analyzer analyzer;
    private final Directory index;

    public App() throws IOException {
        this.analyzer = new MyAnalyzer().get();
        this.index = new ByteBuffersDirectory();
        IndexWriterConfig cfg = new IndexWriterConfig(analyzer);
        IndexWriter writer = new IndexWriter(index, cfg);
        loadData(WIKI_DIR, writer);
        writer.close();   
    }

    public static void main(String[] args) throws IOException {
        System.out.println("******** Welcome to Jeopardy QA Engine! ********");
        App engine = new App();
        Map<String,String> queries = getQueryQuestions(QUESTIONS_FILE);
        engine.queryAndComputeStats(queries);
    }

    private List<String> queryIt(String q) throws ParseException, IOException {
        String query = q.toLowerCase()
            .replaceAll("!", "")
            .replaceAll("==", " ")
            .replaceAll("--", " ")
            .replaceAll("(\\[tpl])|(\\[/tpl])", "")
            .replaceAll("\\s+", " ");

        QueryParser parser = new QueryParser("content", analyzer);
        Query luceneQ = parser.parse(query);
        IndexReader rdr = DirectoryReader.open(index);
        IndexSearcher searcher = new IndexSearcher(rdr);
        TopDocs hits = searcher.search(luceneQ,10);

        List<String> answers = new ArrayList<>();
        for (ScoreDoc sd : hits.scoreDocs) {
            Document d = searcher.doc(sd.doc);
            answers.add(d.get("title"));
        }
        rdr.close();
        return answers;
    }

    private void queryAndComputeStats(Map<String,String> qa) {
        double mrrSum = 0;
        int found=0, correctAt1=0, total=qa.size();
        StringBuilder out = new StringBuilder();

        for (var e : qa.entrySet()) {
            String question = e.getKey(), gold = e.getValue();
            List<String> preds = Collections.emptyList();
            try { preds = queryIt(question); }
            catch (Exception ex) { }
            out.append("|\n")
            .append("[content]\n")
            .append(question).append("[content]\n")
            .append(gold).append("[content]\n")
            .append(preds).append("|\n");
            if (!preds.isEmpty() && preds.contains(gold)) {
                found++;
                int rank = preds.indexOf(gold) + 1;
                mrrSum += 1.0 / rank;
                if (preds.get(0).equals(gold)) correctAt1++;
            }
        }
        //computing the results 
        // our baseline gets MRR = 0.36 and P@1 = 0.30 without using LLM to rerank :)
        double mrr = mrrSum / total;
        double pAt1 = (double)correctAt1 / total;
        System.out.printf("\nMRR = %.4f%nP@1 = %.4f%nFound = %d / %d%n",
                          mrr, pAt1, found, total);

        try (FileWriter fw = new FileWriter(OUTPUT_FILE)) {
            fw.write(out.toString());
            System.out.println("Results written to " + OUTPUT_FILE);
        } catch (IOException ioe) {
            ioe.printStackTrace();
        }
    }

    private static Map<String,String> getQueryQuestions(String path) throws IOException {
        Map<String,String> map = new LinkedHashMap<>();
        try (Scanner sc = new Scanner(new File(path), "UTF-8")) {
            while (sc.hasNextLine()) {
                String clue = sc.nextLine().trim();
                if (clue.isEmpty()) continue;
                String question = clue + " " + sc.nextLine().trim();
                String answer   = sc.nextLine().trim();
                map.put(question, answer);
            }
        }
        return map;
    }

    private static void loadData(String dir, IndexWriter writer) throws IOException {
        System.out.println("… Indexing from " + dir);
        var files = Stream.of(Objects.requireNonNull(new File(dir).listFiles()))
                          .filter(f -> f.isFile())
                          .map(File::getName)
                          .collect(Collectors.toSet());

        int docs=0;
        for (String f : files) {
            try (Scanner sc = new Scanner(new File(dir + f), "UTF-8")) {
                String title="", text="";
                while (sc.hasNextLine()) {
                    String line = sc.nextLine();
                    if (line.startsWith("[[")) {
                        if (!title.isEmpty()) {
                            addDoc(writer, title, text);
                            text=""; docs++;
                        }
                        title = line.substring(2, line.length()-2).trim();
                    } else text += " "+line;
                }
                if (!title.isEmpty()) {
                    addDoc(writer, title, text);
                    docs++;
                }
            }
        }
        writer.commit();
        System.out.println("Total docs: " + docs);
    }

    private static void addDoc(IndexWriter w, String title, String txt) throws IOException {
        String text = (title + " " + txt)
                        .toLowerCase()
                        .replaceAll("!", "")
                        .replaceAll("=="," ")
                        .replaceAll("--"," ")
                        .replaceAll("(\\[tpl])|(\\[/tpl])","")
                        .replaceAll("\\s+"," ");

        Document d = new Document();
        d.add(new StringField("title", title, Field.Store.YES));
        d.add(new TextField("content", text, Field.Store.YES));
        w.addDocument(d);
    }
    // Custom Analyzer 
    static class MyAnalyzer {
        Analyzer get() throws IOException {
            Map<String,String> snowballParams = new HashMap<>();
            snowballParams.put("language", "English");
            Map<String,String> stopMap = new HashMap<>();
            stopMap.put("words", "stopwords.txt");
            stopMap.put("format", "wordset");
            // you can comment out some lines to see the scores of other configurations 
            // as mentioned in our results table
            return CustomAnalyzer.builder()
                   .withTokenizer(StandardTokenizerFactory.class)
                   .addTokenFilter(LowerCaseFilterFactory.class)
                   .addTokenFilter(StopFilterFactory.class, stopMap)
                   .addTokenFilter(HyphenatedWordsFilterFactory.class)
                   .addTokenFilter(KeywordRepeatFilterFactory.class)
                   .addTokenFilter(SnowballPorterFilterFactory.class, snowballParams)
                   .build();
        }
    }
}
