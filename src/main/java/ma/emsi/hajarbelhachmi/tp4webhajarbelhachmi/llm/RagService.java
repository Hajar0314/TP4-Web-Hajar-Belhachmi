package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
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
import java.util.logging.Logger;

/**
 * Phase 1 du RAG (ingestion des 2 PDF, une seule fois) et construction du RetrievalAugmentor
 * (phase 2). Les PDF sont dans src/main/resources/rag.
 */
@ApplicationScoped
public class RagService {

    private static final Logger LOG = Logger.getLogger(RagService.class.getName());

    // Paramètres du RAG (à ajuster en testant)
    public static final int TAILLE_SEGMENT = 300;   // en caractères (comme dans les tests du TP)
    public static final int CHEVAUCHEMENT = 30;
    public static final int MAX_RESULTATS = 3;
    public static final double SCORE_MIN = 0.5;

    // Les 2 PDF du RAG, avec une description que le LM utilise pour choisir le bon document
    private static final String PDF_1 = "rag/document1.pdf";
    private static final String DESC_1 = "Cours sur l'IA et le machine learning : réseaux de neurones, "
            + "apprentissage, descente de gradient, modèles de langage, tokens, embeddings, transformeurs";
    private static final String PDF_2 = "rag/document2.pdf";
    private static final String DESC_2 = "Cours sur le fine-tuning et le RAG : bases de données vectorielles, "
            + "LangChain4j, ingestion de documents, ContentRetriever, routage, reranking";

    @Inject
    private MagasinEmbeddings magasinEmbeddings;

    private final Map<ContentRetriever, String> sourcesFixes = new LinkedHashMap<>();
    private RetrievalAugmentor augmentor;

    @PostConstruct
    void init() {
        EmbeddingModel embeddingModel = magasinEmbeddings.getEmbeddingModel();

        sourcesFixes.put(creerRetriever(PDF_1, embeddingModel), DESC_1);
        sourcesFixes.put(creerRetriever(PDF_2, embeddingModel), DESC_2);

        // Modèle utilisé par le routeur ; température basse pour des décisions stables
        ChatModel modelRouteur = GoogleGenAiChatModel.builder()
                .apiKey(System.getenv("GEMINI_KEY"))
                .modelName("gemini-3.5-flash")
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
            Document document = new ApacheTikaDocumentParser().parse(in);

            InMemoryEmbeddingStore<TextSegment> store = new InMemoryEmbeddingStore<>();
            EmbeddingStoreIngestor.builder()
                    .documentSplitter(DocumentSplitters.recursive(TAILLE_SEGMENT, CHEVAUCHEMENT))
                    .embeddingModel(embeddingModel)
                    .embeddingStore(store)
                    .build()
                    .ingest(document);
            LOG.info(">>> PDF ingéré : " + cheminPdf);

            return EmbeddingStoreContentRetriever.builder()
                    .embeddingStore(store)
                    .embeddingModel(embeddingModel)
                    .maxResults(MAX_RESULTATS)
                    .minScore(SCORE_MIN)
                    .build();
        } catch (Exception e) {
            throw new RuntimeException("Erreur lors du chargement de " + cheminPdf, e);
        }
    }

    public RetrievalAugmentor getAugmentor() {
        return augmentor;
    }
}
