package pt.hlbk.prompt_cache.core;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;

@Component
public class PromptCache {

    private static final Logger LOGGER = LoggerFactory.getLogger(PromptCache.class);
    private static final double SIMILARITY_THRESHOLD = 0.85;

    private final Cache<List<Float>, String> cache;

    public PromptCache() {
        this.cache = Caffeine.newBuilder()
                .maximumSize(10000)
                .expireAfterAccess(Duration.of(1, ChronoUnit.HOURS))
                .build();
    }

    public String get(List<Float> embeddings) {
        String cachedResult = cache.getIfPresent(embeddings);
        if (cachedResult != null) {
            return cachedResult;
        }

        for (Map.Entry<List<Float>, String> entry : cache.asMap().entrySet()) {

            List<Float> cachedEmbeddings = entry.getKey();

            double similarity = cosineSimilarity(embeddings, cachedEmbeddings);
            LOGGER.info("Similarity between embeddings for request: {}", similarity);

            if (similarity >= SIMILARITY_THRESHOLD) {
                LOGGER.info("Similar embedding found in cache!");
                return entry.getValue();
            }
        }
        return null;
    }

    public void put(List<Float> embeddings, String result) {
        cache.put(embeddings, result);
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
