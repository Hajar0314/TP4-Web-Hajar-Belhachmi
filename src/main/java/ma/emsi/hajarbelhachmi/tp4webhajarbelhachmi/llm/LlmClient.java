package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.googleai.GoogleAiGeminiChatModel;
import dev.langchain4j.service.AiServices;
import jakarta.enterprise.context.Dependent;

import java.io.Serializable;

/**
 * Classe "métier" qui gère l'interface avec l'API du LLM (Gemini) en utilisant LangChain4j.
 * Portée Dependent : l'instance est supprimée en même temps que le backing bean qui l'a reçue par injection.
 * Elle implémente Serializable car le backing bean (portée view) est sérialisable.
 */
@Dependent
public class LlmClient implements Serializable {

    /**
     * Rôle système choisi par l'utilisateur pour l'assistant.
     */
    private String systemRole;

    /**
     * Interface qui décrit les échanges avec le LLM (implémentée par LangChain4j).
     */
    private Assistant assistant;

    /**
     * Mémoire de l'assistant : historique de la conversation.
     */
    private ChatMemory chatMemory;

    public LlmClient() {
        // Récupère la clé secrète dans la variable d'environnement GEMINI_KEY.
        String apiKey = System.getenv("GEMINI_KEY");

        // Création du modèle qui représente le LLM.
        ChatModel model = GoogleAiGeminiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gemini-3.5-flash")
                .build();

        // Création de l'assistant, avec une mémoire qui garde les derniers messages échangés.
        this.chatMemory = MessageWindowChatMemory.withMaxMessages(20);
        this.assistant = AiServices.builder(Assistant.class)
                .chatModel(model)
                .chatMemory(chatMemory)
                .build();
    }

    /**
     * Définit le rôle système. La mémoire est d'abord vidée car le contexte change complètement,
     * puis le rôle est ajouté à la mémoire sous forme de SystemMessage pour être pris en compte par le LLM.
     *
     * @param systemRole le rôle système choisi par l'utilisateur.
     */
    public void setSystemRole(String systemRole) {
        this.systemRole = systemRole;
        this.chatMemory.clear();
        this.chatMemory.add(SystemMessage.from(systemRole));
    }

    /**
     * Envoie une requête au LLM et retourne sa réponse.
     *
     * @param prompt la question de l'utilisateur.
     * @return la réponse du LLM.
     */
    public String envoyerRequete(String prompt) {
        return this.assistant.chat(prompt);
    }
}