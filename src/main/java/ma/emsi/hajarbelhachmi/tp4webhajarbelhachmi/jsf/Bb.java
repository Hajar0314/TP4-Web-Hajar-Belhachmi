package ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.jsf;

import jakarta.faces.application.FacesMessage;
import jakarta.faces.context.FacesContext;
import jakarta.faces.model.SelectItem;
import jakarta.faces.view.ViewScoped;
import jakarta.inject.Inject;
import jakarta.inject.Named;
import jakarta.servlet.http.Part;
import ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm.LlmClient;
import ma.emsi.hajarbelhachmi.tp4webhajarbelhachmi.llm.MagasinEmbeddings;

import java.io.InputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * Backing bean pour la page JSF index.xhtml.
 * Portée view pour conserver l'état de la conversation qui dure pendant plusieurs requêtes HTTP.
 * La portée view nécessite l'implémentation de Serializable (le backing bean peut être mis en mémoire secondaire).
 */
@Named
@ViewScoped
public class Bb implements Serializable {

    /**
     * Rôle "système" que l'on attribue au LLM.
     * Possible d'écrire un nouveau rôle dans la liste déroulante.
     */
    private String roleSysteme;

    /**
     * Quand le rôle est choisi par l'utilisateur dans la liste déroulante,
     * il n'est plus possible de le modifier (voir code de la page JSF), sauf si on veut un nouveau chat.
     */
    private boolean roleSystemeChangeable = true;

    /**
     * Liste de tous les rôles de l'API prédéfinis.
     */
    private List<SelectItem> listeRolesSysteme;

    /**
     * Dernière question posée par l'utilisateur.
     */
    private String question;
    /**
     * Dernière réponse du LLM.
     */
    private String reponse;
    /**
     * La conversation depuis le début.
     */
    private StringBuilder conversation = new StringBuilder();

    /**
     * Client du LLM auquel le backing bean délègue l'interface avec l'API.
     */
    @Inject
    private LlmClient llmClient;

    /**
     * Contexte JSF. Utilisé pour qu'un message d'erreur s'affiche dans le formulaire.
     */
    @Inject
    private FacesContext facesContext;

    /**
     * Magasin d'embeddings qui reçoit les PDF chargés par l'utilisateur (upload).
     */
    @Inject
    private MagasinEmbeddings magasinEmbeddings;

    /**
     * Fichier PDF choisi par l'utilisateur dans la page (transient : Part n'est pas Serializable
     * et le backing bean, de portée view, doit l'être).
     */
    private transient Part fichier;

    /**
     * Message affiché après le chargement d'un fichier.
     */
    private String messagePourChargementFichier;

    /**
     * Obligatoire pour un bean CDI (classe gérée par CDI), s'il y a un autre constructeur.
     */
    public Bb() {
    }

    public String getRoleSysteme() {
        return this.roleSysteme;
    }

    public void setRoleSysteme(String roleSysteme) {
        this.roleSysteme = roleSysteme;
    }

    public boolean isRoleSystemeChangeable() {
        return this.roleSystemeChangeable;
    }

    public String getQuestion() {
        return this.question;
    }

    public void setQuestion(String question) {
        this.question = question;
    }

    public String getReponse() {
        return this.reponse;
    }

    /**
     * setter indispensable pour le textarea.
     *
     * @param reponse la réponse à la question.
     */
    public void setReponse(String reponse) {
        this.reponse = reponse;
    }

    public String getConversation() {
        return conversation.toString();
    }

    public void setConversation(String conversation) {
        this.conversation = new StringBuilder(conversation);
    }

    /**
     * Envoie la question au LLM et récupère sa réponse.
     * Au début de la conversation, le rôle système est transmis au LlmClient
     * pour être ajouté à la mémoire du LLM.
     *
     * @return null pour rester sur la même page.
     */
    public String envoyer() {
        if (this.question == null || this.question.isBlank()) {
            // Erreur ! Le formulaire va être réaffiché en réponse à la requête POST, avec un message d'erreur.
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Texte question vide", "Il manque le texte de la question");
            facesContext.addMessage(null, message);
            return null;
        }

        try {
            // Si la conversation n'a pas encore commencé
            if (this.conversation.isEmpty()) {
                // Passe le rôle système au LlmClient (ajouté à la mémoire du LLM)
                this.llmClient.setSystemRole(this.roleSysteme);
                // Fige la liste déroulante qui permet de choisir le rôle système
                this.roleSystemeChangeable = false;
            }
            // Envoie la question au LLM et récupère la réponse.
            this.reponse = this.llmClient.envoyerRequete(this.question);
        } catch (Exception e) {
            FacesMessage message = new FacesMessage(FacesMessage.SEVERITY_ERROR,
                    "Problème de connexion avec l'API du LLM",
                    "Problème de connexion avec l'API du LLM : " + e.getMessage());
            facesContext.addMessage(null, message);
            return null;
        }

        // La conversation contient l'historique des questions-réponses depuis le début.
        afficherConversation();
        return null;
    }

    /**
     * Pour un nouveau chat.
     * Termine la portée view en retournant "index" (la page index.xhtml sera affichée après le traitement
     * effectué pour construire la réponse) et pas null. null aurait indiqué de rester dans la même page (index.xhtml)
     * sans changer de vue.
     * Le fait de changer de vue va faire supprimer l'instance en cours du backing bean par CDI et donc on reprend
     * tout comme au début puisqu'une nouvelle instance du backing va être utilisée par la page index.xhtml.
     * @return "index"
     */
    public String nouveauChat() {
        return "index";
    }

    /**
     * Pour afficher la conversation dans le textArea de la page JSF.
     */
    private void afficherConversation() {
        this.conversation.append("== User:\n").append(question).append("\n== LLM:\n").append(reponse).append("\n");
    }

    public Part getFichier() {
        return fichier;
    }

    public void setFichier(Part fichier) {
        this.fichier = fichier;
    }

    public String getMessagePourChargementFichier() {
        return messagePourChargementFichier;
    }

    /**
     * Téléchargement d'un fichier PDF, ajouté dans la base de données vectorielle.
     */
    public void upload() {
        if (this.fichier == null || this.fichier.getSize() == 0) {
            this.messagePourChargementFichier = "Aucun fichier choisi.";
            return;
        }
        // getSubmittedFileName() retourne le nom du fichier sur le disque du client.
        String nom = this.fichier.getSubmittedFileName();
        if (nom != null && nom.toLowerCase().endsWith(".pdf")) {
            try (InputStream in = this.fichier.getInputStream()) {
                // Charger le fichier dans la BD vectorielle
                this.magasinEmbeddings.ajouterPdf(in, nom);
                this.messagePourChargementFichier = "Fichier " + nom + " chargé, il sera utilisé pour le RAG.";
            } catch (Exception e) {
                this.messagePourChargementFichier = "Erreur lors du chargement de " + nom + " : " + e.getMessage();
            }
        } else {
            this.messagePourChargementFichier = "Le fichier doit être un PDF.";
        }
    }

    public List<SelectItem> getRolesSysteme() {
        if (this.listeRolesSysteme == null) {
            // Génère les rôles de l'API prédéfinis
            this.listeRolesSysteme = new ArrayList<>();
            // Vous pouvez évidemment écrire ces rôles dans la langue que vous voulez.
            String role = """
                    You are a helpful assistant. You help the user to find the information they need.
                    If the user type a question, you answer it.
                    """;
            // 1er argument : la valeur du rôle (envoyé au serveur)
            // 2ème argument : le libellé du rôle dans la liste déroulante.
            this.listeRolesSysteme.add(new SelectItem(role, "Assistant"));

            role = """
                    You are an interpreter. You translate from English to French and from French to English.
                    If the user type a French text, you translate it into English.
                    If the user type an English text, you translate it into French.
                    If the text contains only one to three words, give some examples of usage of these words in English.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Traducteur Anglais-Français"));

            role = """
                    Your are a travel guide. If the user type the name of a country or of a town,
                    you tell them what are the main places to visit in the country or the town
                    are you tell them the average price of a meal.
                    """;
            this.listeRolesSysteme.add(new SelectItem(role, "Guide touristique"));
        }

        return this.listeRolesSysteme;
    }

}