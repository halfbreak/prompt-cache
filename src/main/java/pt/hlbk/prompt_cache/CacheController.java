package pt.hlbk.prompt_cache;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pt.hlbk.prompt_cache.core.PromptCache;

import java.util.List;

@RestController
@RequestMapping("cache")
public class CacheController {

    private static final Logger LOGGER = LoggerFactory.getLogger(CacheController.class);

    private final EmbeddingModel embeddingModel;
    private final PromptCache promptCache;

    @Autowired
    public CacheController(EmbeddingModel embeddingModel, PromptCache promptCache) {
        this.embeddingModel = embeddingModel;
        this.promptCache = promptCache;
    }


    @GetMapping
    public ResponseEntity<CacheResponse> cache(@RequestParam String prompt) {
        var response = this.embeddingModel.embedForResponse(List.of(prompt));
        LOGGER.info("Embedding: {}", response);
        var embeddings = response.getResult().getOutput();

        String cachedResult = promptCache.get(embeddings);
        if (cachedResult != null) {
            return ResponseEntity.ok(new CacheResponse(cachedResult));
        }

        return ResponseEntity.notFound().build();
    }

    @PostMapping
    public ResponseEntity<CacheResponse> cachePut(@RequestBody CacheRequest cacheRequest) {
        LOGGER.info("Received {}", cacheRequest);
        var response = this.embeddingModel.embedForResponse(List.of(cacheRequest.prompt()));
        var embeddings = response.getResult().getOutput();
        promptCache.put(embeddings, cacheRequest.response());
        return ResponseEntity.ok(new CacheResponse(cacheRequest.response()));
    }
}
