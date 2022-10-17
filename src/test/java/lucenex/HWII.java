package lucenex;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.core.LowerCaseFilterFactory;
import org.apache.lucene.analysis.core.WhitespaceTokenizerFactory;
import org.apache.lucene.analysis.custom.CustomAnalyzer;
import org.apache.lucene.analysis.en.EnglishAnalyzer;
import org.apache.lucene.analysis.en.EnglishMinimalStemFilterFactory;
import org.apache.lucene.analysis.en.EnglishPossessiveFilterFactory;
import org.apache.lucene.analysis.miscellaneous.HyphenatedWordsFilterFactory;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.miscellaneous.WordDelimiterGraphFilterFactory;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.codecs.Codec;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.Explanation;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import org.junit.Before;
import org.junit.Test;

public class HWII {

	private ArrayList<File> queue;
	private IndexWriter writer;
	private String docPath;
	@Before 
	public void setup() {
		queue= new ArrayList<File>();
		docPath="target/docs";
	}

	private void addFiles(File file) {
		if (!file.exists()) {
			System.out.println(file + " non esiste.");
		}
		if (file.isDirectory()) {
			for (File f : file.listFiles()) {
				addFiles(f);
			}
		} else {
			String filename = file.getName().toLowerCase();
			//===================================================
			// Only index text files
			//===================================================
			if (filename.endsWith(".txt")) {
				queue.add(file);
			} else {
				System.out.println("Non importato: " + filename);
			}
		}
	}



	private void indexDocs(String fileName, Directory directory, Codec codec) throws IOException {
		Analyzer myAnalyzer = CustomAnalyzer.builder()
				.withTokenizer(WhitespaceTokenizerFactory.class)
				.addTokenFilter(HyphenatedWordsFilterFactory.class)
				.addTokenFilter(EnglishPossessiveFilterFactory.class)
				.addTokenFilter(WordDelimiterGraphFilterFactory.class)
				.addTokenFilter(LowerCaseFilterFactory.class)
				.addTokenFilter(EnglishMinimalStemFilterFactory.class)
				.build();
		Map<String, Analyzer> perFieldAnalyzers = new HashMap<>();
		perFieldAnalyzers.put("contenuto", myAnalyzer);
		perFieldAnalyzers.put("titolo", new EnglishAnalyzer());

		Analyzer analyzer = new PerFieldAnalyzerWrapper(myAnalyzer, perFieldAnalyzers);
		IndexWriterConfig config = new IndexWriterConfig(analyzer);
		if (codec != null) {
			config.setCodec(codec);
		}
		writer = new IndexWriter(directory, config);
		writer.deleteAll();

		addFiles(new File(fileName));      
		for (File f : queue) {
			FileReader fr = null;
			try {
				Document doc = new Document();
				fr = new FileReader(f);
				doc.add(new TextField("contenuto", fr));
				doc.add(new TextField("titolo", f.getName(), Field.Store.YES));
				writer.addDocument(doc);
			} catch (Exception e) {
				System.out.println("Impossibile aggiungere al writer: " + f);
			} finally {
				fr.close();
			}
		}

		queue.clear();

		writer.commit();
		writer.close();
	}

	@Test
	public void testQuery() throws Exception {
		Path path = Paths.get("target/index");

		try (Directory directory = FSDirectory.open(path)) {
			indexDocs(docPath, directory, null);
			Scanner scan = new Scanner(System.in);
			System.out.println("Eseguire la query su titolo o contenuto?");
			String campo = scan.next();
			System.out.println("Query:");
			String parametro = scan.next();
			QueryParser parser;
			if(campo.equals("contenuto")) {	
				parser = new QueryParser("contenuto", new StandardAnalyzer());}
			else {
				parser = new QueryParser("titolo", new StandardAnalyzer());}
			Query queryContenuto = parser.parse(parametro);

			try (IndexReader reader = DirectoryReader.open(directory)) {
				IndexSearcher searcher = new IndexSearcher(reader);
				runQuery(searcher, queryContenuto);
			} finally {
				directory.close();
			}

		}
	}

	private void runQuery(IndexSearcher searcher, Query query) throws IOException {
		runQuery(searcher, query, false);
	}

	private void runQuery(IndexSearcher searcher, Query query, boolean explain) throws IOException {
		TopDocs hits = searcher.search(query, 10);
		System.out.println("Risultati: ");
		for (int i = 0; i < hits.scoreDocs.length; i++) {
			ScoreDoc scoreDoc = hits.scoreDocs[i];
			Document doc = searcher.doc(scoreDoc.doc);
			System.out.println("doc"+scoreDoc.doc + ":"+ doc.get("titolo") + " (" + scoreDoc.score +")");
			if (explain) {
				Explanation explanation = searcher.explain(query, scoreDoc.doc);
				System.out.println(explanation);
			}
		}
	}
}
