package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.input.PromptTemplate;
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
 * QueryRouter personnalisé qui combine deux fonctionnalités des tests du TP 4 :
 * - Test 4 "Pas de RAG" : le LM décide si la question concerne les documents ;
 *   sinon aucun ContentRetriever n'est utilisé (liste vide), donc pas de RAG.
 * - Test 3 "Routage" : si la question concerne les documents, un LanguageModelQueryRouter
 *   choisit le ou les documents (ContentRetriever) à interroger.
 *
 * Les sources sont fournies par un Supplier, relu à chaque question : les PDF chargés
 * (upload) après le démarrage de l'application sont ainsi pris en compte.
 */
public class RouteurRag implements QueryRouter {

    private static final Logger LOG = Logger.getLogger(RouteurRag.class.getName());

    private final PromptTemplate promptTemplate = PromptTemplate.from(
            "Voici les sujets traités par mes documents : {{sujets}}. "
                    + "Est-ce que la requête '{{requete}}' porte sur un de ces sujets ? "
                    + "Réponds seulement par 'oui', 'non', ou 'peut-être'.");

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

        String question = promptTemplate.apply(Map.of("sujets", sujets, "requete", query.text())).text();
        String reponse = model.chat(question).trim().toLowerCase();
        LOG.info(">>> RAG ? question = \"" + query.text() + "\" ; réponse du LM = " + reponse);

        if (reponse.contains("non")) {
            // Pas de RAG (aucun ContentRetriever utilisé)
            LOG.info(">>> Pas de RAG pour cette question");
            return Collections.emptyList();
        }

        // Routage : le LM choisit le ou les documents à interroger
        Collection<ContentRetriever> choisis =
                new LanguageModelQueryRouter(model, sourcesActuelles).route(query);
        LOG.info(">>> RAG avec " + choisis.size() + " source(s) de documents");
        return choisis;
    }
}
