package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.query.Query;
import dev.langchain4j.rag.query.router.LanguageModelQueryRouter;
import dev.langchain4j.rag.query.router.QueryRouter;

import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.function.Supplier;
import java.util.logging.Logger;

/**
 * 1) Le LM décide si la question concerne les documents (sinon : pas de RAG).
 * 2) Si oui, un LanguageModelQueryRouter choisit le ou les documents à interroger.
 * Les sources sont relues à chaque question, pour prendre en compte les PDF uploadés.
 */
public class RouteurRag implements QueryRouter {

    private static final Logger LOG = Logger.getLogger(RouteurRag.class.getName());

    private final ChatModel model;
    private final Supplier<Map<ContentRetriever, String>> sources;

    public RouteurRag(ChatModel model, Supplier<Map<ContentRetriever, String>> sources) {
        this.model = model;
        this.sources = sources;
    }

    @Override
    public Collection<ContentRetriever> route(Query query) {
        Map<ContentRetriever, String> sourcesActuelles = sources.get();
        String sujets = String.join(" ; ", sourcesActuelles.values());

        String prompt = "Voici les sujets traités par mes documents : " + sujets + "\n"
                + "Question de l'utilisateur : " + query.text() + "\n"
                + "Cette question concerne-t-elle ces sujets ? Réponds uniquement par OUI ou NON.";
        String reponse = model.chat(prompt).trim().toUpperCase();
        LOG.info(">>> Décision RAG pour \"" + query.text() + "\" : " + reponse);

        if (reponse.startsWith("NON")) {
            return Collections.emptyList();   // pas de RAG
        }
        Collection<ContentRetriever> choisis =
                new LanguageModelQueryRouter(model, sourcesActuelles).route(query);
        LOG.info(">>> Nombre de sources choisies : " + choisis.size());
        return choisis;
    }
}