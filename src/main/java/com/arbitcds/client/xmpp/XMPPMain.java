package com.arbitcds.client.xmpp;

import org.jivesoftware.smack.packet.Presence;
import org.jxmpp.stringprep.XmppStringprepException;
import java.util.*;

/**
 * È il punto di ingresso dell’applicazione.

    Si occupa di:

    mostrare i menu;
    leggere le scelte dalla console;
    leggere username e password;
    chiamare XMPPClient;
    visualizzare i risultati
 */
public class XMPPMain {

    /**
     * The main method that starts the XMPP chat application.
     *
     * @param args The command-line arguments (not used in this application).
     * @throws XmppStringprepException If there's an error with XMPP string preparation.
     */
    public static void main(String[] args) throws XmppStringprepException {
        
        // Oggetto Scanner per leggere gli input dell'utente da terminale
        Scanner scanner = new Scanner(System.in); // System.in rappresenta lo standard input, cioè ciò che l’utente digita nella console

        int choice; // variabile che memorizza la scelta dell'utente realtiva al menù inziale
        int lgchoice; // variabile che memorizza la scelta dell'utente dal menù che si apre dopo il log-in
        
        // Coronologia dei messaggi: viene persa quando il programma termina
        Map<String, List<String>> messageHistory = new HashMap<>(); // mappa della cronologia: a ogni conversazione (chiave) corrisponde una lista di messaggi (valore) scambiati

        do { // Ad ogni iterazione del ciclo, viene costruito un nuovo oggetto XMPPClient
            XMPPClient xmppClient = new XMPPClient(messageHistory); // Crea XMPPClient, passandogli la mappa della cronologia. Il costruttore di questo oggetto tenta già la connessione al server XMPP
            
            // Mostra il menù iniziale all'utente
            System.out.println("MENU");
            System.out.println("1. Login");
            System.out.println("2. Register new user");
            System.out.println("3. Close app");
            System.out.print("Select an option: ");

            choice = scanner.nextInt(); // legge la scelta numerica dell'utente
            
            switch (choice) { // entra nel ramo corrispondente alla scelta dell'utente
                
                case 1: // esegue il login dell'utente richiedendo username e password

                        scanner.nextLine(); // questo metodo vede il carattere "a capo" generati dal premere Invio e salta direttamente alla riga successiva
                        
                        System.out.print("\nUsername: ");
                        String username = scanner.nextLine(); // chiede e legge il nome utente
                        System.out.print("Password: ");
                        String password = scanner.nextLine(); // chiede e legge la password

                        // Legge username e password e chiama il metodo di login
                        if (xmppClient.login(username, password)){
                            
                            System.out.println("\nLogged in successfully!");
                            xmppClient.sendConnectionNotificationToFriends(); // invia ai contatti la notifica di connessione
                            
                            List<String> contacts;
                            
                            do {
                                // Mostra il menù dopo il login
                                System.out.println("\nMAIN MENU\n-------------------------------------------");
                                System.out.println("1.  Show contacts");
                                System.out.println("2.  View user information");
                                System.out.println("3.  Add contact");
                                System.out.println("4.  Accept friend requests");
                                System.out.println("5.  Switch Presence Mode");
                                System.out.println("6.  Direct messages");
                                System.out.println("7.  Send Files");
                                System.out.println("8.  Group messages");
                                System.out.println("9.  Delete account");
                                System.out.println("10. Log out");
                                System.out.print("Select an option: ");
                                
                                lgchoice = scanner.nextInt();
                                scanner.nextLine();
                                
                                switch (lgchoice) {
                                    case 1: // stampa contatti e disponibilità 
                                        List<String> mainContacts = xmppClient.getContactsWithStatus();
                                        if (mainContacts.isEmpty()) {
                                            System.out.println("You don't have any contacts in your list.");
                                        } else {
                                            System.out.println("\n=================================");
                                            System.out.println("List of contacts:");
                                            for (String contact : mainContacts) {
                                                System.out.println(contact);
                                            }
                                            System.out.println("=================================");
                                        }
                                        break;
                                    case 2: // visualizza le informazione di un contatto, compreso lo stato di presenza
                                        System.out.print("Enter the userJID (contact@domain): ");
                                        String targetUser = scanner.nextLine();
                                        String userStatus = xmppClient.getUserStatus(targetUser);
                                        System.out.println("\n==============================================");
                                        System.out.println(userStatus);
                                        break;
                                    case 3: // aggiunge un contatto alla rubrica dell'utente, richiedendo l'email e il nickname
                                        System.out.print("\nContact email (example@alumchat.xyz): ");
                                        String addEmail = scanner.nextLine();
                                        System.out.print("Nickname: ");
                                        String nickname = scanner.nextLine();
                                        boolean added = xmppClient.addContact(addEmail, nickname);
                                        if (added) {
                                            System.out.println("Contact successfully added..");
                                        } else {
                                            System.out.println("Contact could not be added.");
                                        }
                                        break;
                                    case 4: // visualizza le richieste di amicizia in sospeso e permette di accettarle tutte
                                        List<String> subscriptionRequests = xmppClient.getSubscriptionRequests();
                                        if (subscriptionRequests.isEmpty()) {
                                            System.out.println("\nYou don't have any requests.");
                                        } else {
                                            System.out.println("\nList of requests:");
                                            for (String request : subscriptionRequests) {
                                                System.out.println("Incoming subscription request from: " + request);
                                            }
                                            System.out.println("\nPress 1 to accept all requests or 0 to exit: ");
                                            int acorden = scanner.nextInt();

                                            if (acorden == 1) {
                                                xmppClient.acceptAllRequests();
                                                System.out.println("Accepted all subscription requests.");
                                            } else if (acorden == 0) {
                                                System.out.println("Exiting...");
                                            } else {
                                                System.out.println("Invalid choice.");
                                            }
                                        }

                                        break;
                                    case 5: // cambia lo stato di presenza dell'utente, richiedendo all'utente di selezionare un'opzione da un menù
                                        System.out.println("\nSelect presence mode:");
                                        System.out.println("_________________________________");
                                        System.out.println("1. Available");
                                        System.out.println("2. Chat");
                                        System.out.println("3. Away");
                                        System.out.println("4. Extended Away");
                                        System.out.println("5. Do Not Disturb");
                                        System.out.println("0. Cancel");
                                        System.out.print("Enter your choice: ");
                                        int presenceModeChoice = scanner.nextInt();
                                        scanner.nextLine();

                                        Presence.Mode selectedPresenceMode = null;
                                        String statusMessage = null;

                                        switch (presenceModeChoice) {
                                            case 1:
                                                selectedPresenceMode = Presence.Mode.available;
                                                break;
                                            case 2:
                                                selectedPresenceMode = Presence.Mode.chat;
                                                break;
                                            case 3:
                                                selectedPresenceMode = Presence.Mode.away;
                                                break;
                                            case 4:
                                                selectedPresenceMode = Presence.Mode.xa;
                                                break;
                                            case 5:
                                                selectedPresenceMode = Presence.Mode.dnd;
                                                break;
                                            case 0:
                                                System.out.println("Operation cancelled.");
                                                break;
                                            default:
                                                System.out.println("Invalid choice.");
                                        }

                                        if (selectedPresenceMode != null) {
                                            System.out.print("Enter status message (optional): ");
                                            statusMessage = scanner.nextLine();

                                            if (xmppClient.setPresenceMode(selectedPresenceMode, statusMessage)) {
                                                System.out.println("Presence mode updated successfully.");
                                            } else {
                                                System.out.println("Failed to update presence mode.");
                                            }
                                        }
                                        break;
                                    case 6: // visualizza la cronologia dei messaggi con un contatto selezionato e permette di inviare un nuovo messaggio
                                        contacts = xmppClient.getContacts();

                                        if (contacts.isEmpty()) {
                                            System.out.println("You don't have any contacts in your list.");
                                        } else {
                                            System.out.println("\n===================================================");
                                            System.out.println("List of contacts:");
                                            for (int i = 0; i < contacts.size(); i++) {
                                                System.out.println((i + 1) + ". " + contacts.get(i));
                                            }
                                            System.out.println("===================================================\n");
                                            System.out.print("Select the contact's number to whom you want to see the chat: ");
                                            int selectedContactIndex = scanner.nextInt();
                                            scanner.nextLine();
                                            if (selectedContactIndex >= 1 && selectedContactIndex <= contacts.size()) {
                                                String selectedContact = contacts.get(selectedContactIndex - 1);
                                                List<String> chatHistory = xmppClient.getChatHistory(selectedContact);

                                                System.out.println("\nChat with " + selectedContact + ":");
                                                for (String me : chatHistory) {
                                                    System.out.println(me);
                                                }

                                                System.out.print("\nDo you want to send a message to " + selectedContact + "? (1 for Yes, 0 for No): ");
                                                int sendMessageChoice = scanner.nextInt();
                                                scanner.nextLine();
                                                if (sendMessageChoice == 1) {
                                                    System.out.print("Message: ");
                                                    String chatMessage = scanner.nextLine();
                                                    xmppClient.sendMessage(selectedContact, chatMessage, null);
                                                    System.out.println("Message sent ✓");
                                                }
                                            } else {
                                                System.out.println("Invalid option.");
                                            }
                                        }
                                        break;
                                    case 7: // invia un file ad un contatto selezionato, richiedendo all'utente di selezionare un contatto e di inserire il percorso del file
                                        contacts = xmppClient.getContacts();
                                        if (contacts.isEmpty()) {
                                            System.out.println("You don't have any contacts in your list.");
                                        } else {
                                            System.out.println("\n===================================================");
                                            System.out.println("List of contacts:");
                                            for (int i = 0; i < contacts.size(); i++) {
                                                System.out.println((i + 1) + ". " + contacts.get(i));
                                            }
                                            System.out.println("===================================================\n");
                                            System.out.print("Select the contact's number to whom you want to send a file: ");
                                            int selectedContactIndex = scanner.nextInt();
                                            scanner.nextLine();
                                            if (selectedContactIndex >= 1 && selectedContactIndex <= contacts.size()) {
                                                String selectedContact = contacts.get(selectedContactIndex - 1);

                                                System.out.print("File path: ");
                                                String filePath = scanner.nextLine();
                                                xmppClient.sendFile(selectedContact, filePath);
                                                System.out.println("File sent ✓");
                                            } else {
                                                System.out.println("Invalid option.");
                                            }
                                        }
                                        break;
                                    case 8: // Apre un sottomenu per inviti, creazione, ricezione di inviti e invio al gruppo
                                        System.out.println("\n1. Invitar a usuario a sala de chat grupal");
                                        System.out.println("2. Crear sala de chat grupal");
                                        System.out.println("3. Unirse a una sala de chat grupal");
                                        System.out.println("4. Enviar mensaje a sala de chat grupal");
                                        System.out.println("5. Salir");

                                        System.out.print("Seleccione una opción: ");
                                        int option = scanner.nextInt();
                                        scanner.nextLine();

                                        switch (option) {
                                            case 1:
                                                System.out.print("Nombre de la sala: ");
                                                String roomName = scanner.nextLine();
                                                System.out.print("JID del usuario a invitar: ");
                                                String userJID = scanner.nextLine();
                                                xmppClient.inviteUserToGroupChat(roomName, userJID);
                                                break;

                                            case 2:
                                                System.out.print("Nombre de la sala: ");
                                                String newRoomName = scanner.nextLine();
                                                xmppClient.createGroupChatAndInvite(newRoomName);
                                                xmppClient.registerGroupMessageListener(newRoomName);
                                                break;
                                            case 3:
                                                xmppClient.acceptInvitationAndJoinGroupChat("Prueba4");
                                                xmppClient.registerGroupMessageListener("Prueba4");
                                                break;
                                            case 4:
                                                System.out.print("Nombre de la sala a la que enviar el mensaje: ");
                                                String targetRoom = scanner.nextLine();
                                                System.out.print("Mensaje a enviar: ");
                                                String message = scanner.nextLine();
                                                xmppClient.sendMessageToGroupChat(targetRoom, message);
                                                break;
                                            case 5:
                                                xmppClient.disconnect();
                                                System.exit(0);
                                            default:
                                                System.out.println("Opción inválida. Intente de nuevo.");
                                        }
                                        break;
                                    case 9: // elimina l'account dell'utente, richiedendo conferma prima di procedere
                                        System.out.println("Are you sure you want to delete your account?");
                                        System.out.println("Press 1 to confirm or 0 to cancel:");
                                        int confirmDelete = scanner.nextInt();

                                        if (confirmDelete == 1) {
                                            boolean deleted = xmppClient.deleteAccount();
                                            if (deleted) {
                                                System.out.println("Account deleted successfully.");
                                                xmppClient.disconnect();
                                                lgchoice = 10;
                                            } else {
                                                System.out.println("Failed to delete account.");
                                            }
                                        } else {
                                            System.out.println("Account deletion cancelled.");
                                        }
                                        break;
                                    case 10: // esegue il logout dell'utente, salvando la cronologia dei messaggi e chiudendo la connessione
                                        messageHistory = xmppClient.getMessageHistory();
                                        xmppClient.disconnect();
                                        break;

                                    default:
                                        System.out.println("Invalid option.");
                                }
                            } while (lgchoice != 9);
                        };
                        break;
                case 2: // registra un nuovo utente sul server XMPP richiedendo username e password
                    xmppClient.login("estrada20565", "admin"); // tenta prima un login con credenziali fisse nel sorgente (account amministrativo) per poter creare un nuovo account sul server XMPP
                    
                    // Non verifica se il login con credenziali fisse/di amminisratore va a buon fine
                    scanner.nextLine(); 
                    System.out.print("\nNew Username: ");
                    String newUsername = scanner.nextLine();
                    System.out.print("New Password: ");
                    String newPassword = scanner.nextLine();

                    // Chiama il metodo per la creazione di un nuovo account sul server XMPP
                    boolean accountCreated = xmppClient.createAccount(newUsername, newPassword);

                    if (accountCreated) {
                        xmppClient.disconnect();
                        System.out.println("Account created!");
                    }
                    break;
                case 3: // chiude l'applicazione ma non chiama esplicitamente disconnect() sulla connessione aperta dal costruttore
                    System.out.println("Thanks for using the program.");
                    break;
                default:
                    System.out.println("Invalid option.");
            }
        } while (choice != 3); // A ogni iterazione viene creato un nuovo client e viene aperta una nuova connessione finchè l'utente non sceglie di chiudere l'applicazione
    } // fine main
}
