package pt.hlbk.prompt_cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("cache")
public class CacheController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheController.class);
    private static final double SIMILARITY_THRESHOLD = 0.85;

    @Autowired
    private EmbeddingModel embeddingModel;

    private final ConcurrentHashMap<List<Float>, String> embeddingCache = new ConcurrentHashMap<>();

    @GetMapping
    public ResponseEntity<CacheResponse> cache(@RequestParam String prompt) {
        var response = this.embeddingModel.embedForResponse(List.of(prompt));
        LOGGER.info("Embedding: {}", response);
        var embeddings = response.getResult().getOutput();
        var embeddingsList = new ArrayList<Float>();
        for (float embedding : embeddings) {
            embeddingsList.add(embedding);
        }

        String cachedResult = embeddingCache.get(embeddingsList); // Direct cache lookup
        if (cachedResult != null) {
            LOGGER.info("Cache hit!");
            return ResponseEntity.ok(new CacheResponse(cachedResult));
        }

        for (Map.Entry<List<Float>, String> entry : embeddingCache.entrySet()) {

            List<Float> cachedEmbeddings = entry.getKey();

            double similarity = cosineSimilarity(embeddingsList, cachedEmbeddings);
            LOGGER.info("Similarity between embeddings for prompt {}: {}", prompt, similarity);

            if (similarity >= SIMILARITY_THRESHOLD) {
                System.out.println("Similar embedding found in cache!");
                return ResponseEntity.ok(new CacheResponse(entry.getValue()));
            }
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<CacheResponse> cachePut(@RequestBody CacheRequest cacheRequest) {
        LOGGER.info("Received {}", cacheRequest);
        var response = this.embeddingModel.embedForResponse(List.of(cacheRequest.prompt()));
        var embeddings = response.getResult().getOutput();
        var embeddingsList = new ArrayList<Float>();
        for (float embedding : embeddings) {
            embeddingsList.add(embedding);
        }
        embeddingCache.put(embeddingsList, cacheRequest.response());
        LOGGER.info("Cache now {}", embeddingCache);
        return ResponseEntity.ok(new CacheResponse(cacheRequest.response()));
    }

    private double cosineSimilarity(List<Float> vec1, List<Float> vec2) {
        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        for (int i = 0; i < vec1.size(); i++) {
            dotProduct += vec1.get(i) * vec2.get(i);
            norm1 += vec1.get(i) * vec1.get(i);
            norm2 += vec2.get(i) * vec2.get(i);
        }

        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }
}
