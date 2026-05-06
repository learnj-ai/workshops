# Chapter: TechCorpDocumentLoader - Loading Source Documents

## Introduction

**TechCorpDocumentLoader** loads markdown documents from the classpath at startup, feeding the vector index.

## Code

```java
@Component
public class TechCorpDocumentLoader {
    public List<Document> loadDocuments() {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] resources = resolver.getResources("classpath:data/*.md");
        
        return Arrays.stream(resources)
                .map(this::toDocument)
                .toList();
    }

    private Document toDocument(Resource resource) {
        String text = resource.getContentAsString(StandardCharsets.UTF_8);
        Metadata metadata = Metadata.metadata("source", resource.getFilename());
        return Document.from(text, metadata);
    }
}
```

## Key Points

- **Loads all .md files** from classpath:data/
- **Adds source metadata** for traceability
- **Called once at startup** by VectorStoreService
- **Could be extended** to load PDFs, Word docs, etc.

## Next Steps

Learn how **VectorStoreService** orchestrates the entire pipeline.
