package pt.hlbk.prompt_cache.core;

import jakarta.annotation.PreDestroy;
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.KnnFloatVectorField;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.KnnFloatVectorQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.store.ByteBuffersDirectory;
import org.apache.lucene.store.Directory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

@Component
public class PromptCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptCache.class);
    private static final double SIMILARITY_THRESHOLD = 0.85;

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
        doc.add(new KnnFloatVectorField("prompt", embeddings));
        doc.add(new StoredField("result", result));

        try {
            writer.addDocument(doc);
            writer.commit();
        } catch (IOException e) {
            LOGGER.error("Error indexing document", e);
        }
    }
}
