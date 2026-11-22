package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.google.genai.GoogleGenAiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.Dependent;
import jakarta.inject.Inject;

import java.io.Serializable;

/**
 * Classe "métier" qui gère l'interface avec l'API du LLM (Gemini) en utilisant LangChain4j.
 * Portée Dependent : l'instance est supprimée en même temps que le backing bean qui l'a reçue par injection.
 * Elle implémente Serializable car le backing bean (portée view) est sérialisable.
 */
@Dependent
public class LlmClient implements Serializable {

    /** Rôle système choisi par l'utilisateur pour l'assistant. */
    private String systemRole;

    /** Interface qui décrit les échanges avec le LLM (implémentée par LangChain4j). */
    private Assistant assistant;

    /** Mémoire de l'assistant : historique de la conversation. */
    private ChatMemory chatMemory;

    /** Service RAG (partagé par toute l'application) qui fournit le RetrievalAugmentor. */
    @Inject
    private RagService ragService;

    public LlmClient() {
    }

    @PostConstruct
    void init() {
        // Récupère la clé secrète dans la variable d'environnement GEMINI_KEY.
        String apiKey = System.getenv("GEMINI_KEY");

        // Température basse : pour le RAG on veut des réponses fiables, pas de "créativité".
        ChatModel model = GoogleGenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-3.5-flash")
                .temperature(0.3)
                .build();

        // Assistant avec mémoire des derniers messages échangés et RAG.
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .retrievalAugmentor(ragService.getAugmentor())
                .build();
    }

    /**
     * Définit le rôle système. La mémoire est d'abord vidée car le contexte change complètement,
     * puis le rôle est ajouté à la mémoire sous forme de SystemMessage pour être pris en compte par le LLM.
     */
    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
        this.chatMemory.clear();
        this.chatMemory.add(SystemMessage.from(systemRole));
    }

    /** Envoie une requête au LLM et retourne sa réponse. */
    public String envoyerRequete(String prompt) {
        return this.assistant.chat(prompt);
    }
}
