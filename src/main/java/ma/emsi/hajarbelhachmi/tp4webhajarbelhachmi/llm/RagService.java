package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

@ApplicationScoped
public class RagService {

    // ===== À ADAPTER à vos PDF =====
    private static final String PDF_1 = "rag/document1.pdf";
    private static final String DESC_1 = "Cours d'intelligence artificielle et de machine learning";
    private static final String PDF_2 = "rag/document2.pdf";
    private static final String DESC_2 = "Cours de programmation Java";
    // ================================

    @Inject
    private MagasinEmbeddings magasinEmbeddings;

    private final Map<ContentRetriever, String> sourcesFixes = new LinkedHashMap<>();
    private RetrievalAugmentor augmentor;

    @PostConstruct
    void init() {
        EmbeddingModel embeddingModel = magasinEmbeddings.getEmbeddingModel();

        sourcesFixes.put(creerRetriever(PDF_1, embeddingModel), DESC_1);
        sourcesFixes.put(creerRetriever(PDF_2, embeddingModel), DESC_2);

        ChatModel modelRouteur = GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GEMINI_KEY"))
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .build();

        this.augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(new RouteurRag(modelRouteur, this::sourcesActuelles))
                .build();
    }

    /** PDF fixes + éventuels PDF uploadés. */
    private Map<ContentRetriever, String> sourcesActuelles() {
        Map<ContentRetriever, String> sources = new LinkedHashMap<>(sourcesFixes);
        if (magasinEmbeddings.aDesFichiers()) {
            sources.put(magasinEmbeddings.getRetriever(), magasinEmbeddings.getDescription());
        }
        return sources;
    }

    private ContentRetriever creerRetriever(String cheminPdf, EmbeddingModel embeddingModel) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(cheminPdf)) {
            if (in == null) {
                throw new IllegalStateException("PDF introuvable dans les resources : " + cheminPdf);
            }
            Document document = new ApachePdfBoxDocumentParser().parse(in);
            InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(300, 30))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(store)
                    .build()
                    .ingest(document);
            return EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .maxResults(3)
                    .minScore(0.5)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chargement de " + cheminPdf, e);
        }
    }

    public RetrievalAugmentor getAugmentor() {
        return augmentor;
    }
}