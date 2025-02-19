package pt.hlbk.prompt_cache.core;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.LongPoint;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.*;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Component
public class PromptCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptCache.class);
    private static final double SIMILARITY_THRESHOLD = 0.85;
    private static final int MAX_INDEX_SIZE = 10000;
    private static final int NUM_DOCS_TO_DELETE = 100;

    private final Directory directory;
    private final IndexWriter writer;

    public PromptCache() throws IOException {
        this.directory = new ByteBuffersDirectory();
        IndexWriterConfig config = new IndexWriterConfig(new StandardAnalyzer());
        this.writer = new IndexWriter(directory, config);
    }

    @PreDestroy
    public void stop() {
        try {
            this.writer.close();
            this.directory.close();
        } catch (IOException e) {
            LOGGER.error("Error closing the directory", e);
        }
    }

    public String get(float[] embeddings) {
        try {
            DirectoryReader reader = DirectoryReader.open(directory);
            IndexSearcher searcher = new IndexSearcher(reader);
            KnnFloatVectorQuery query = new KnnFloatVectorQuery("prompt", embeddings, 1);
            TopDocs results = searcher.search(query, 1);
            if (results.totalHits.value == 0) {
                return null;
            }

            ScoreDoc scoreDoc = results.scoreDocs[0];
            LOGGER.info("Found doc {} with similarity {}", scoreDoc.doc, scoreDoc.score);
            var retrievedDoc = searcher.doc(scoreDoc.doc);
            var result = retrievedDoc.get("result");
            if (result != null && scoreDoc.score >= SIMILARITY_THRESHOLD) {
                return result;
            }
            reader.close();
        } catch (IOException e) {
            LOGGER.info("Failed to open index", e);
            throw new RuntimeException(e);
        }
        return null;
    }

    public void put(float[] embeddings, String result) {
        var doc = new Document();
        long timestamp = System.currentTimeMillis();
        doc.add(new KnnFloatVectorField("prompt", embeddings));
        doc.add(new StoredField("result", result));
        doc.add(new LongPoint("timestamp", timestamp));
        doc.add(new StoredField("timestamp", timestamp));

        try {
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            LOGGER.error("Error indexing document", e);
        }
    }

    @Scheduled(initialDelay = 60000, fixedRate = 60000)
    public void maxNumberOfEntriesEviction() {
        LOGGER.info("Evaluating Max Number of Entries Eviction");
        try (DirectoryReader reader = DirectoryReader.open(directory)) {
            int numDocs = reader.numDocs();
            LOGGER.info("Number of entries {}", numDocs);
            if (numDocs >= MAX_INDEX_SIZE) {
                IndexSearcher searcher = new IndexSearcher(reader);
                Sort sort = new Sort(new SortField("timestamp", SortField.Type.LONG, false));
                TopDocs results = searcher.search(new MatchAllDocsQuery(), NUM_DOCS_TO_DELETE, sort);

                List<Term> deleteTerms = new ArrayList<>();
                for (ScoreDoc scoreDoc : results.scoreDocs) {
                    Document doc = searcher.doc(scoreDoc.doc);
                    String result = doc.get("result");
                    deleteTerms.add(new Term("result", result));
                }
                if (!deleteTerms.isEmpty()) {
                    writer.deleteDocuments(deleteTerms.toArray(new Term[0]));
                    writer.commit();
                }
            }
        } catch (IOException e) {
            LOGGER.info("Error while executing max size eviction", e);
        }
    }
}
