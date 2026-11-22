package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.parser.apache.tika.ApacheTikaDocumentParser;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.embedding.onnx.allminilml6v2.AllMiniLmL6V2EmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.store.embedding.inmemory.InMemoryEmbeddingStore;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.io.InputStream;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Magasin d'embeddings (en mémoire) pour les PDF chargés (uploadés) par les utilisateurs.
 * Partagé par toute l'application. Le contenu disparaît au redémarrage du serveur.
 * Il possède aussi le modèle d'embeddings, partagé avec RagService.
 */
@ApplicationScoped
public class MagasinEmbeddings {

    private EmbeddingModel embeddingModel;
    private InMemoryEmbeddingStore<TextSegment> store;
    private ContentRetriever retrieverUploads;
    private final List<String> nomsFichiers = new CopyOnWriteArrayList<>();

    @PostConstruct
    void init() {
        this.embeddingModel = new AllMiniLmL6V2EmbeddingModel();
        this.store = new InMemoryEmbeddingStore<>();
        this.retrieverUploads = EmbeddingStoreContentRetriever.builder()
                .embeddingStore(store)
                .embeddingModel(embeddingModel)
                .maxResults(RagService.MAX_RESULTATS)
                .minScore(RagService.SCORE_MIN)
                .build();
    }

    public EmbeddingModel getEmbeddingModel() {
        return embeddingModel;
    }

    /**
     * Ajoute un PDF : extraction du texte, découpage en chunks, calcul des embeddings,
     * ajout dans le magasin.
     */
    public void ajouterPdf(InputStream pdf, String nomFichier) {
        Document document = new ApacheTikaDocumentParser().parse(pdf);
        EmbeddingStoreIngestor.builder()
                .documentSplitter(DocumentSplitters.recursive(RagService.TAILLE_SEGMENT, RagService.CHEVAUCHEMENT))
                .embeddingModel(embeddingModel)
                .embeddingStore(store)
                .build()
                .ingest(document);
        nomsFichiers.add(nomFichier);
    }

    public boolean aDesFichiers() {
        return !nomsFichiers.isEmpty();
    }

    public ContentRetriever getRetriever() {
        return retrieverUploads;
    }

    /** Description donnée au LM pour qu'il sache quand utiliser ces documents. */
    public String getDescription() {
        return "Documents chargés par l'utilisateur : " + String.join(", ", nomsFichiers);
    }
}
