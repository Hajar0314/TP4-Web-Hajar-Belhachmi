package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

/**
 * Interface qui décrit les interactions entre l'application et le LLM.
 * LangChain4j fournit automatiquement l'implémentation de cette interface.
 */
public interface Assistant {
    /**
     * Envoie une question (prompt) au LLM et retourne sa réponse.
     *
     * @param prompt la question posée au LLM.
     * @return la réponse du LLM.
     */
    String chat(String prompt);
}