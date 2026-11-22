package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.pdfbox.ApachePdfBoxDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.rag.DefaultRetrievalAugmentor;
import dev.langchain4j.rag.RetrievalAugmentor;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Charge les PDF une seule fois (au premier usage), crée les retrievers et le RetrievalAugmentor.
 */
@ApplicationScoped
public class RagService {

    // ===== À ADAPTER à vos PDF =====
    private static final String PDF_1 = "rag/document1.pdf";
    private static final String DESC_1 = "Cours d'intelligence artificielle et de machine learning";
    private static final String PDF_2 = "rag/document2.pdf";
    private static final String DESC_2 = "Cours de programmation Java";
    // ================================

    private RetrievalAugmentor augmentor;

    @PostConstruct
    void init() {
        EmbeddingModel embeddingModel = new AllMiniLmL6V2EmbeddingModel();

        ContentRetriever retriever1 = creerRetriever(PDF_1, embeddingModel);
        ContentRetriever retriever2 = creerRetriever(PDF_2, embeddingModel);

        ChatModel modelRouteur = GoogleAiGeminiChatModel.builder()
                .apiKey(System.getenv("GEMINI_KEY"))
                .modelName("gemini-2.5-flash")
                .temperature(0.0)
                .build();

        // Description de chaque source, utilisée par le LM pour choisir le bon PDF.
        Map<ContentRetriever, String> sources = new LinkedHashMap<>();
        sources.put(retriever1, DESC_1);
        sources.put(retriever2, DESC_2);

        LanguageModelQueryRouter routeurPdf = new LanguageModelQueryRouter(modelRouteur, sources);
        RouteurRag routeur = new RouteurRag(modelRouteur, DESC_1 + " ; " + DESC_2, routeurPdf);

        this.augmentor = DefaultRetrievalAugmentor.builder()
                .queryRouter(routeur)
                .build();
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