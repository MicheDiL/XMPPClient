/**
 * Nombre del Archivo: XMPPClient.java
 * Descripción: Este archivo contiene la implementación de la clase XMPPClient, que maneja la funcionalidad de un cliente XMPP.
 * Autor: Oscar Estrada
 * Fecha: 22/08/2023
 * Versión: 1.0
 *
 * Dependencias Externas:
 * - Smack API: Biblioteca para la comunicación XMPP. Versión 4.2.0.
 * - JXMPP: Biblioteca para la manipulación de JID (Jabber ID).
 *
 * Notas:
 * - Esta clase implementa varias funcionalidades de un cliente XMPP, como la conexión al servidor, gestión de contactos,
 *   envío de mensajes, administración de estados de presencia y funciones de chat grupal.
 * - Asegúrate de completar la versión de las dependencias externas con las versiones reales utilizadas en tu proyecto.
 */

package org.example;

import org.jivesoftware.smack.*;
import org.jivesoftware.smack.chat2.Chat;
import org.jivesoftware.smack.chat2.ChatManager;
import org.jivesoftware.smack.filter.MessageTypeFilter;
import org.jivesoftware.smack.packet.Message;
import org.jivesoftware.smack.packet.Presence;
import org.jivesoftware.smack.sasl.SASLErrorException;
import org.jivesoftware.smackx.muc.InvitationListener;
import org.jivesoftware.smackx.muc.MultiUserChatManager;
import org.jivesoftware.smackx.muc.packet.MUCUser;
import org.jivesoftware.smackx.xdata.Form;
import org.jivesoftware.smackx.xdata.packet.DataForm;
import org.jxmpp.jid.EntityBareJid;
import org.jxmpp.jid.EntityJid;
import org.jxmpp.jid.impl.JidCreate;
import org.jivesoftware.smack.roster.*;
import org.jivesoftware.smack.tcp.XMPPTCPConnection;
import org.jivesoftware.smack.tcp.XMPPTCPConnectionConfiguration;
import org.jxmpp.jid.parts.Localpart;
import org.jxmpp.jid.parts.Resourcepart;
import org.jxmpp.stringprep.XmppStringprepException;
import org.jivesoftware.smackx.iqregister.AccountManager;
import org.jivesoftware.smackx.muc.MultiUserChat;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;

/**
 * Contiene quasi tutta la logica XMPP:

    connessione;
    autenticazione;
    gestione degli account;
    contatti;
    presenza;
    messaggi;
    file;
    chat di gruppo.
 */
public class XMPPClient {

    // Host di rete e dominio degli account XMPP
    private static final String XMPP_SERVER = "127.0.0.1";  // IP della macchina dove gira il server XMPP
    private static final int PORT = 5222;                   // porta sulla quale il client prova ad aprire una connessione TCP verso il server XMPP
    private static final String DOMAIN = "example.com";     // Dominio XMPP degli account, per esempio alice@example.com

    // Conservano le credenziali dopo il login
    private String username;                    // Nome dell’utente dopo il login
    private String password;                    // Password conservata dopo il login

    // Riferimento Smack alla connessione
    private AbstractXMPPConnection connection;  // Oggetto Smack che rappresenta la connessione 
    
    // Rubrica dell'account
    private Roster roster;                      // Rubrica XMPP cha rappresenta la lista dei contatti dell'utente
    
    // Lista in memoria dei JID che hanno chiesto la sottoscrizione alla presenza
    private List<String> incomingSubscriptionRequests = new ArrayList<>(); // Richieste di sottoscrizione ricevute 
    
    // Riferimento alla mappa ricevuta dal programma principale
    private Map<String, List<String>> messageHistory; // Cronologia dei messaggi in memoria


    private List<String> notifications = new ArrayList<>();

    public XMPPClient(Map<String, List<String>> messageHistory) {
        connect(); // tenta la di stabilire la connessione al server XMPP
        this.messageHistory = messageHistory; // assegna la cronologia al campo dell’oggetto 
    }

//=================================================================================================GENERAL CONECTION=================================================================================================

    /** CONNESSIONE AL SERVER XMPP
     *  crea la configurazione;
        apre la connessione;
        configura le richieste di presenza;
        registra il listener dei messaggi
     * @return boolean
     */
    public boolean connect() {
        try {
            // Apre la connessione TCP, configura il roster e registra un listener per i messaggi

            XMPPTCPConnectionConfiguration config = XMPPTCPConnectionConfiguration.builder() // Configura la connessione 
                    .setUsernameAndPassword(username, password) // in questa fare username e password sono ancora null, il loro valore effettivo sarà imposto dal metodo login() dopo che l'utente le avrà inserite a mano 
                    .setXmppDomain(DOMAIN)  // imposta DOMAIN come dominio XMPP
                    .setHost(XMPP_SERVER)   // imposta XMPP_SERVER come host del sever XMPP
                    .setPort(PORT)          // imposta PORT come porta del server XMPP
                    .setSecurityMode(ConnectionConfiguration.SecurityMode.disabled) // disabilita la sicurezza della connessione XMPP a livello di configurazione Smack 
                    .build();

            // Creazione della connessione TCP con il server XMPP usando la configurazione appena creata
            connection = new XMPPTCPConnection(config); // Crea la connessione XMPP con la configurazione di cui sopra
            connection.connect(); // Stabilisce la connessione al server XMPP

            /* Recupera la rubrica associata alla connessione. 
            Contiene informazioni come:
                - JID dei contatti;
                - nickname;
                - gruppi;
                - stato della sottoscrizione
            */
            Roster roster = Roster.getInstanceFor(connection);
            roster.setSubscriptionMode(Roster.SubscriptionMode.manual); // Imposta la gestione manuale delle richieste di sottoscrizione: le richieste di sottoscrizione non devono essere accettate automaticamente
            // Registra una callback per aggiungere alla lista le richieste di sottoscrizione ricevute.
            roster.addSubscribeListener((from, subscribeRequest) -> { // parametri di input della lmbda: from = JID del utente che fa la richiesta di sottoscrizione, subscribeRequest = oggetto che rappresenta la richiesta di sottoscrizione
                incomingSubscriptionRequests.add(from.toString()); // Aggiunge la richiesta di sottoscrizione alla lista delle richieste in arrivo
                return null;
            });

            /* Registra una callback ASINCRONA di ricezione dei messaggi in arrivo. 
                In questo caso l'evento che attiva la callback è: "Arrivo di una stanza XMPP compatibile con il filtro"
                ASINCRONA significa che la callback viene eseguita da un thread separato, non blocca il thread principale (quello che esegue il main() e che gestisce l'interfaccia utente)
            */
            registerMessageListener(stanza -> {
                
                if (stanza instanceof Message) { // controlla che la stanza ricevuta sia un <messaggio>
                    
                    Message message = (Message) stanza; // converte la stanza in un oggetto di tipo Message
                    String notification = message.getBody(); // Esrtae il corpo del messaggio, ovvero il test del messaggio ricevuto
                    
                    if (notification != null) { // ignora messaggi senza corpo

                        /* il formato previsto per un messaggio è qualcosa del tipo: 
                        <nome_utente>: <contenuto_messaggio>            -> NEL CASO DI UN MESSAGGIO DI TESTO NORMALE 
                        File <nomeFile> <mittente>: contenutoBase64     -> NEL CASSO DI UN FILE INVIATO
                        <nome_utente> <stato>                           -> NEL CASO DI UNA NOTIFICA DI STATO (es. "alice Available")
                        */

                        // Parsing dei messaggi ricevuti e salvataggio nella cronologia dei messaggi
                        if(notification.contains(":")){ // se il messaggio contiene ":", allora è plausibile che sia o un messaggio di testo o un file 
                            String[] parts = notification.split(": "); // divide il messaggio in due parti: la prima parte contiene il nome dell'utente che ha inviato il messaggio e la seconda parte contiene il contenuto del messaggio. Il risultato è un array di stringhe chiamato parts
                            if (parts[0].contains("File ")){
                                String[] meta = parts[0].split(" ");
                                String notKey = getChatKey(username, meta[2]);
                                String notificationFile = receiveFile(meta[2], meta[1], parts[1]);
                                addMessageToChatHistory(notKey, notificationFile);
                            }else{
                                String notKey = getChatKey(username, parts[0]); // Obtener la clave del chat
                                addMessageToChatHistory(notKey, notification);
                            }
                        }else{
                            String[] parts = notification.split(" ");
                            String notKey = getChatKey(username, parts[0]); // Obtener la clave del chat
                            addMessageToChatHistory(notKey, notification);
                        }

                    }
                }
            });

            System.out.println("\nSuccessful connection to the XMPP server.\n");
            return true; // Client connesso, ma non ancora autenticato
        
        } catch (XmppStringprepException e) {
            e.printStackTrace();
            System.err.println("Failed to establish connection to the XMPP server: " + e.getMessage());
            return false; 
        } catch (SmackException | IOException | XMPPException | InterruptedException ex) {
            ex.printStackTrace();
            System.err.println("Failed to establish connection to the XMPP server: " + ex.getMessage());
            return false;
        }
    }

//=================================================================================================ACCOUNT SETS=================================================================================================

    /** AUTENTICAZIONE DELL'UTENTE AL SERVER XMPP USANDO LE SUE CREDENZIALI
     * @param username
     * @param password
     * @return boolean
     * Questo metodo serve ad autenticare l'utente con il server XMPP
     */
    public boolean login(String username, String password) {
        try {
            
            connection.login(username, password); // Autentica l'untete con il server XMPP usando le credenziali fornite
            // Se il server XMPP accetta le credenziali, l'autenticazione ha successo, memorizza le credenziali dell'utente
            this.username = username;
            this.password = password;
            return true; // Client connesso e autenticato

        } catch (SASLErrorException saslError) {
            // Presenta tutti gli errori SASL come credenziali errate
            System.err.println("Login failed: Invalid credentials.");
            return false;
        } catch (SmackException | IOException | XMPPException | InterruptedException ex) {
            ex.printStackTrace();
            System.err.println("Failed to establish connection to the XMPP server: " + ex.getMessage());
            return false;
        }
    }

    public void disconnect() { // verifica che esista una connessione attiva e la chiude
        if (connection != null && connection.isConnected()) {
            connection.disconnect();
        }
    }

    
    /** CREAZIONE DI UN NUOVO ACCOUNT XMPP SUL SERVER
     * @param newUsername
     * @param newPassword
     * @return boolean
     */
    public boolean createAccount(String newUsername, String newPassword) {

        // Richiede che la connessione esista, sia attiva e che l'utente sia autenticato prima di tentare di creare un nuovo account
        if (connection != null && connection.isConnected() && connection.isAuthenticated()) {
            
            AccountManager accountManager = AccountManager.getInstance(connection); // Ottiene l'istanza della classe AccountManager che gestisce la creazione e gestione degli account XMPP
            
            try {

                accountManager.sensitiveOperationOverInsecureConnection(true); // Permette operazioni sensibili su connessioni non sicure (prove di protezione SSL/TLS), come la creazione di account
                accountManager.createAccount(Localpart.from(newUsername), newPassword); // Crea un nuovo account XMPP con le credenziali fornite
                return true;
            
            } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException |
                     SmackException.NotConnectedException | InterruptedException ex) {
                ex.printStackTrace();
                System.err.println("Error encountered while creating the account: " + ex.getMessage());
            
            } catch (XmppStringprepException e) {
                throw new RuntimeException(e);
            }
        
        }else{
            System.out.print(connection != null);
            System.out.print(connection.isConnected());
            System.out.print(connection.isAuthenticated());
        }
        return false;
    }

    
    /** 
     * @return boolean
     */
    public boolean deleteAccount() {
        if (connection != null && connection.isConnected() && connection.isAuthenticated()) {
            try {
                AccountManager accountManager = AccountManager.getInstance(connection);
                accountManager.deleteAccount();
                return true;
            } catch (SmackException.NoResponseException | XMPPException.XMPPErrorException |
                     SmackException.NotConnectedException | InterruptedException ex) {
                ex.printStackTrace();
                System.err.println("Error encountered while deleting the account: " + ex.getMessage());
            }
        }
        return false;
    }

//=================================================================================================CONTACTS=================================================================================================

    /** GESTIONE DEI CONTATTI DELL'UTENTE: OTTIENE LA LISTA DEI CONTATTI DALLA RUBRICA DELL'UTENTE
     * @return List<String>
     */
    public List<String> getContacts() {
        // Crea una lista vuota per memorizzare i contatti
        List<String> contactList = new ArrayList<>();

        if (connection != null && connection.isConnected()) {
            // Ottiene l'istanza della rubrica (roster) associata alla connessione e imposta la modalità di sottoscrizione manuale
            roster = Roster.getInstanceFor(connection);
            roster.setSubscriptionMode(Roster.SubscriptionMode.manual);

            // Itera attraverso gli elementi della rubrica e aggiunge i JID dei contatti alla lista dei contatti
            for (RosterEntry entry : roster.getEntries()) {
                contactList.add(entry.getJid().toString());
            }
        }

        /* Il metodo restituisce quindi una lista simile a:
        [
            "alice@example.com",
            "bob@example.com"
        ] */
        return contactList;
    }

    
    /** METODO CHE RESTITUISCE LA LISTA DEI CONTATTI DELL'UTENTE CON IL LORO STATO DI PRENSEZA (ONLINE, OFFLINE, AWAY, ETC...)
     * @return List<String>
     */
    public List<String> getContactsWithStatus() {
        List<String> contactList = new ArrayList<>();

        if (connection != null && connection.isConnected()) {
            roster = Roster.getInstanceFor(connection);
            roster.setSubscriptionMode(Roster.SubscriptionMode.manual);

            for (RosterEntry entry : roster.getEntries()) {
                String contactJID = entry.getJid().toString();
                // Ottiene lo stato di presenza del contatto dalla rubrica (roster) usando il suo JID
                Presence presence = roster.getPresence(entry.getJid());

                String presenceStatus = "Unknown";
                String customStatusMessage = "";

                if (presence.isAvailable()) { // se il contatto è disponibile

                    // Determina lo stato di presenza del contatto in base al suo oggetto Presence.Mode
                    Presence.Mode presenceMode = presence.getMode();

                    if (presenceMode == Presence.Mode.available) { // Se il contatto è Available, crea una stringa di stato di presenza (presenceStatus) e un messaggio di stato personalizzato (customStatusMessage) se disponibile
                        presenceStatus = "Available";
                        customStatusMessage = presence.getStatus(); // il messaggio di stato personalizzato
                        if (customStatusMessage == null) { 
                            customStatusMessage = "...";
                        }
                    } else if (presenceMode == Presence.Mode.chat) {
                        presenceStatus = "Available (Chat)";
                    } else if (presenceMode == Presence.Mode.away) {
                        presenceStatus = "Away";
                    } else if (presenceMode == Presence.Mode.xa) {
                        presenceStatus = "Extended Away";
                    } else if (presenceMode == Presence.Mode.dnd) {
                        presenceStatus = "Do Not Disturb";
                    }
                } else { // Il contatto viene considerato Offline se non è disponibile, indipendetemente dal suo Presence.Mode
                    presenceStatus = "Offline";
                }

                /* Il metodo produce infine:
                    alice@example.com (Away)
                    bob@example.com (Available) - Sto lavorando
                */
                String contactInfo = contactJID + " (" + presenceStatus + ")";
                if (!customStatusMessage.isEmpty()) {
                    contactInfo += " - " + customStatusMessage;
                }
                contactList.add(contactInfo);
            }
        }

        return contactList;
    }

    
    /** 
     * @param targetUser
     * @return String
     * @throws XmppStringprepException
     */
    public String getUserStatus(String targetUser) throws XmppStringprepException {
        if (connection != null && connection.isConnected()) {
            roster = Roster.getInstanceFor(connection);
            roster.setSubscriptionMode(Roster.SubscriptionMode.manual);

            RosterEntry entry = roster.getEntry(JidCreate.bareFrom(targetUser));
            if (entry != null) {
                Presence presence = roster.getPresence(entry.getJid());

                String presenceStatus = "Unknown";
                String customStatusMessage = "";

                if (presence.isAvailable()) {
                    Presence.Mode presenceMode = presence.getMode();

                    if (presenceMode == Presence.Mode.available) {
                        presenceStatus = "Available";
                        customStatusMessage = presence.getStatus();
                        if (customStatusMessage == null) {
                            customStatusMessage = "...";
                        }
                    } else if (presenceMode == Presence.Mode.chat) {
                        presenceStatus = "Available (Chat)";
                    } else if (presenceMode == Presence.Mode.away) {
                        presenceStatus = "Away";
                    } else if (presenceMode == Presence.Mode.xa) {
                        presenceStatus = "Extended Away";
                    } else if (presenceMode == Presence.Mode.dnd) {
                        presenceStatus = "Do Not Disturb";
                    }
                } else {
                    presenceStatus = "Offline";
                }

                return "User: " + targetUser + "\nStatus: " + presenceStatus + "\nMessageStatus: " + customStatusMessage + "\n==============================================";
            } else {
                return "User not found in your roster.\n==============================================";
            }
        }

        return "Not connected to the XMPP server.";
    }

//=================================================================================================PRESENCE=================================================================================================
    
    /** METODO PER MODIFICARE LA PROPRIA PRESENZA
     * @param presenceMode
     * @param statusMessage
     * @return boolean
     */
    public boolean setPresenceMode(Presence.Mode presenceMode, String statusMessage) {
        if (connection != null && connection.isConnected()) {

            //Costruisce una presneza disponibile
            Presence presence = new Presence(Presence.Type.available);

            // Imposta la modalità
            presence.setMode(presenceMode);
            if (statusMessage != null && !statusMessage.isEmpty()) {
                // Imposta l'eventuale messaggio
                presence.setStatus(statusMessage);
            }

            List<String> friends = getContacts();
            String newStatusNotification = "";
            if (presenceMode == Presence.Mode.available) {
                newStatusNotification = "'Available'";
            } else if (presenceMode == Presence.Mode.chat) {
                newStatusNotification = "'Available (Chat)'";
            } else if (presenceMode == Presence.Mode.away) {
                newStatusNotification = "'Away'";
            } else if (presenceMode == Presence.Mode.xa) {
                newStatusNotification = "'Extended Away'";
            } else if (presenceMode == Presence.Mode.dnd) {
                newStatusNotification = "'Do Not Disturb'";
            }
            try {
                // Invia la stanza
                connection.sendStanza(presence);

                /* il programma manda anche un messaggio normale a ogni contatto del tipo:
                    "michele has updated his presence to 'Away"
                 */
                String notificationMessage = username + " has updated his presence to " + newStatusNotification;

                for (String friend : friends) {
                    try {
                        EntityBareJid jid = JidCreate.entityBareFrom(friend);
                        ChatManager chatManager = ChatManager.getInstanceFor(connection);
                        Chat chat = chatManager.chatWith(jid);
                        chat.send(notificationMessage);
                    } catch (Exception ex) {
                        ex.printStackTrace();
                        System.err.println("Error encountered while sending message: " + ex.getMessage());
                    }
                }

                return true;
            } catch (SmackException.NotConnectedException | InterruptedException e) {
                e.printStackTrace();
                return false;
            }
        }
        return false;
    }

//=================================================================================================MESSAGES=================================================================================================

    /** METODO CHE GESTISCE L'INVIO DI MESSAGGI & FILE
     * @param contactJID
     * @param messageBody
     * @param base64File
     * @return boolean
     */
    public boolean sendMessage(String contactJID, String messageBody, String base64File) {
        if (connection != null && connection.isConnected()) { // verifica che la connessione sia attiva
            try {
                EntityBareJid jid = JidCreate.entityBareFrom(contactJID); // Converte il destinatario (alice@example.com) in EntityBareJid (un oggetto JID valido)
                ChatManager chatManager = ChatManager.getInstanceFor(connection); // ChatManager è il gestore delle conversazioni individuali
                Chat chat = chatManager.chatWith(jid); // Apertura logica dell'oggetto Java per la chat
                // Costruisce la chiave locale della conversazione
                String contact = contactJID.replace("@alumchat.xyz", ""); 
                String key = getChatKey(username, contact);
                // Prepara il testo, lo salva nella cronologia ed effettua l'invio
                if (base64File != null && !base64File.isEmpty()) {
                    String[] data = base64File.split(": ");
                    String formattedMessageToSend = "File " + data[0] + " " + username + ": " + data[1];
                    String formattedMessageToSave = "You sent the file: " + data[0] + " to " + contact;
                    addMessageToChatHistory(key, formattedMessageToSave);
                    chat.send(formattedMessageToSend); // Smack trasforma il contenuto in una stanza XMPP e la invia al server XMPP
                } else {
                    String formattedMessage = username + ": " + messageBody;
                    addMessageToChatHistory(key, formattedMessage);
                    chat.send(formattedMessage);
                }

                return true;
            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("Error encountered while sending message: " + ex.getMessage());
            }
        }
        return false;
    }
    
    /** METODO CHE CREA LE CHIAVI DELLA CRONOLOGIA DEI MESSAGGI
     * @param user1
     * @param user2
     * @return String -> le chiavi sono stringhe concatenate del tipo "alice bob"
     */
    private String getChatKey(String user1, String user2) {
        if (user1.compareTo(user2) < 0) {
            return user1 + " " + user2;
        } else {
            return user2 + " " + user1;
        }
    }
    
    /** MEOTODO PER MEMORIZZARE IL MESSAGGIO NELLA CRONOLOGIA
     * @param key
     * @param message
     */
    public void addMessageToChatHistory(String key, String message) {
        List<String> chatHistory = messageHistory.getOrDefault(key, new ArrayList<>());
        chatHistory.add(message);
        messageHistory.put(key, chatHistory);
    }
    
    /** 
     * @param contactJID
     * @return List<String>
     */
    public List<String> getChatHistory(String contactJID) {
        String key = getChatKey(username, contactJID.replace("@alumchat.xyz", ""));
        return messageHistory.getOrDefault(key, new ArrayList<>());
    }

    /**
     * @return Map<String, List<String>>
     */
    public Map<String, List<String>> getMessageHistory(){return this.messageHistory;}

//=================================================================================================FILES================================================================================================
    
    /** METODO PER INVIARE FILE
     * @param contactJID
     * @param filePath
     * @return boolean
     */
    public boolean sendFile(String contactJID, String filePath) {
        try {
            File archivo = new File(filePath); // trova il file nel filesystem
            String nombreArchivo = archivo.getName(); // nome del file
            byte[] fileBytes = Files.readAllBytes(Paths.get(filePath)); // legge il file come un array di byte
            String base64File = Base64.getEncoder().encodeToString(fileBytes); // converte il file in Base64
            String content = nombreArchivo + ": " + base64File;

            return sendMessage(contactJID, "", content);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.err.println("Error while sending file: " + ex.getMessage());
        }
        return false;
    }

    /** METODO PER DECODIFICARE I FILE RICEVUTI
     * @param user
     * @param nameFile
     * @param base64File
     * @return String
     */
    public String receiveFile(String user, String nameFile, String base64File) {
        String savePath = "C:/Users/oestr/OneDrive/Escritorio/recibirPrueba/" + nameFile;
        try {
            byte[] fileBytes = Base64.getDecoder().decode(base64File); // la stringa Base64 viene riconvertita in byte
            Files.write(Paths.get(savePath), fileBytes); // scrive i byte sul disco alla directory indicata
            return (user + " sent you a file and was saved at " + savePath);
        } catch (IOException ex) {
            ex.printStackTrace();
            return ("Error while receiving file: " + ex.getMessage());
        }
    }

//=================================================================================================CONTACTS=================================================================================================
    
    /** 
     * @param contactJID
     * @param nickname
     * @return boolean
     */
    public boolean addContact(String contactJID, String nickname) {
        if (connection != null && connection.isConnected()) {
            try {
                Roster roster = Roster.getInstanceFor(connection);
                roster.createEntry(JidCreate.bareFrom(contactJID), nickname, null);
                return true;
            } catch (SmackException | InterruptedException | IOException | XMPPException.XMPPErrorException ex) {
                ex.printStackTrace();
                System.err.println("Error while adding contact: " + ex.getMessage());
            }
        }
        return false;
    }

    
    /** 
     * @return boolean
     */
    public boolean acceptAllRequests() {
        if (connection != null && connection.isConnected()) {
            try {
                for (String jid : incomingSubscriptionRequests) {
                    Presence subscribed = new Presence(Presence.Type.subscribed);
                    subscribed.setTo(JidCreate.bareFrom(jid));
                    connection.sendStanza(subscribed);

                    // Send a "subscribe" presence to the contact as well
                    Presence subscribe = new Presence(Presence.Type.subscribe);
                    subscribe.setTo(JidCreate.bareFrom(jid));
                    connection.sendStanza(subscribe);
                }
                return true;
            } catch (SmackException | InterruptedException | IOException ex) {
                ex.printStackTrace();
                System.err.println("Error while accepting requests: " + ex.getMessage());
            }
        }
        return false;
    }

    /** 
     * @return List<String>
     */
    public List<String> getSubscriptionRequests() { return incomingSubscriptionRequests; }

//=================================================================================================NOTIFICATIONS=================================================================================================

    /**
     *   invia anche un messaggio di chat a ogni contatto dopo il login
     */
    public void sendConnectionNotificationToFriends() {
        List<String> friends = getContacts(); // Obtener la lista de amigos
        String notification = username + " has just connected.";

        for (String friend : friends) {
            try {
                EntityBareJid jid = JidCreate.entityBareFrom(friend);
                ChatManager chatManager = ChatManager.getInstanceFor(connection);
                Chat chat = chatManager.chatWith(jid);
                chat.send(notification);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.err.println("Error encountered while sending message: " + ex.getMessage());
            }
        }
    }

    
    /** Metodo che effettua la registrazione di un listener per i messaggi in arrivo 
     * usando il filtro MessageTypeFilter.NORMAL, che permette di ricevere solo i messaggi 
     * normali (non di tipo chat o groupchat) 
     * @param listener
     */
    public void registerMessageListener(StanzaListener listener) {
        if (connection != null && connection.isConnected()) {
            // Qui avviene la registrazione effettiva del listener per i messaggi in arrivo
            connection.addAsyncStanzaListener(listener, MessageTypeFilter.NORMAL);
        }
    }

//=================================================================================================GROUP CHAT=================================================================================================
    /* MUC significa Multi-User Chat.

    Una MUC rappresenta una stanza nella quale più utenti possono:
        entrare;
        inviare messaggi;
        invitare altri utenti;
        avere ruoli;
        avere permessi differenti.

    Un JID di stanza tipico è: nome-stanza@conference.example.com
    Un partecipante può comparire come nome-stanza@conference.example.com/alice. 
    Il servizio e il dominio effettivi dipendono dalla configurazione del server
    */  

    /** METODO PER LA CREAZIONE DI UNA STANZA PER CHAT DI GRUPPO
     * @param roomName
     */
    public void createGroupChatAndInvite(String roomName) {
        
        try {

            EntityBareJid roomJid = JidCreate.entityBareFrom(roomName + "@" + XMPP_SERVER); // configura il nome della stanza
            MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection); // manager che gestisce le stanze associate alla connessione
            MultiUserChat muc = manager.getMultiUserChat(roomJid);                          // ottiene l’oggetto che rappresenta una specifica stanza
            muc.create(Resourcepart.from(username));                                        // il creatore crea la stanza ed entra usando username come nickname
            muc.sendConfigurationForm(new Form(DataForm.Type.submit));                      // dopo la creazione il client invia una configurazione per rendere utilizzabile la stanza

            // invita l’utente corrente
            muc.invite(JidCreate.entityBareFrom(username + "@" + DOMAIN), "¡Unámonos a esta sala!");

            System.out.println("Sala de chat grupal '" + roomName + "' creada y unida con éxito.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al crear la sala de chat grupal: " + e.getMessage());
        }
    }


    /** 
     * Costruisce un oggetto Message con il corpo e lo invia alla MUC
     * @param roomName
     * @param message
     */
    public void sendMessageToGroupChat(String roomName, String message) {
        EntityBareJid roomJID;
        try {
            roomJID = JidCreate.entityBareFrom(roomName + "@conference." + DOMAIN);
            MultiUserChat muc = MultiUserChatManager.getInstanceFor(connection).getMultiUserChat(roomJID);

            Message msg = new Message(roomJID);
            msg.setBody(message);

            muc.sendMessage(msg);

            System.out.println("Mensaje enviado al grupo '" + roomName + "': " + message);
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al enviar mensaje al grupo: " + e.getMessage());
        }
    }
    
    /** 
     * Registra un listener che stampa i corpi ricevuti dal gruppo nel terminale. 
     * Non li inserisce nella mappa della cronologia privata
     * @param roomName
     */
    public void registerGroupMessageListener(String roomName) {
        EntityBareJid roomJID;
        try {
            roomJID = JidCreate.entityBareFrom(roomName + "@conference." + DOMAIN);
            MultiUserChat muc = MultiUserChatManager.getInstanceFor(connection).getMultiUserChat(roomJID);

            muc.addMessageListener(message -> {
                if (message.getBody() != null) {
                    System.out.println("Mensaje del grupo '" + roomName + "': " + message.getBody());
                }
            });

            System.out.println("Escuchando mensajes en el grupo '" + roomName + "'.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al registrar el oyente de mensajes en el grupo: " + e.getMessage());
        }
    }

    
    /** 
     * 	Invia un invito al JID indicato
     * @param roomName
     * @param userJID
     */
    public void inviteUserToGroupChat(String roomName, String userJID) {
        EntityBareJid roomJID;
        try {
            roomJID = JidCreate.entityBareFrom(roomName + "@conference." + DOMAIN);
            MultiUserChat muc = MultiUserChatManager.getInstanceFor(connection).getMultiUserChat(roomJID);

            muc.invite(JidCreate.entityBareFrom(userJID), "¡Te invito a unirte a la sala de chat!");

            System.out.println("Invitación enviada a " + userJID + " para unirse a '" + roomName + "'.");
        } catch (Exception e) {
            e.printStackTrace();
            System.err.println("Error al enviar invitación: " + e.getMessage());
        }
    }

    
    /** 
     * Registra un listener degli inviti; il callback invitationReceived(...) 
     * tenta room.join(...) quando arriva un invito
     * @param roomName
     */
    public void acceptInvitationAndJoinGroupChat(String roomName) {
        MultiUserChatManager manager = MultiUserChatManager.getInstanceFor(connection);
        // Registra una funzione da eseguire quando arriverà un invito futuro
        manager.addInvitationListener(new InvitationListener() {
            @Override
            /* Quando l’invito arriva:
                - Smack chiama invitationReceived;
                - il client riceve l’oggetto room;
                - chiama room.join(...);
                - registra un listener per i messaggi. 
            */
            public void invitationReceived(XMPPConnection conn, MultiUserChat room, EntityJid inviter, String reason, String password, Message message, MUCUser.Invite invitation) {
                try {
                    room.join(Resourcepart.from(username));
                    registerGroupMessageListener(roomName);
                    System.out.println("¡Te has unido a la sala de chat grupal '" + roomName + "'!");
                } catch (Exception e) {
                    e.printStackTrace();
                    System.err.println("Error al unirse a la sala de chat grupal: " + e.getMessage());
                }
            }
        });
    }

}