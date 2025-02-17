package pt.hlbk.prompt_cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("cache")
public class CacheController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheController.class);

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
            System.out.println("Cache hit!");
            return ResponseEntity.ok(new CacheResponse(cachedResult));
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
}
