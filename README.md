# XMPPClientLDO — guida al codice e allo sviluppo di un client custom

Questo progetto è un client XMPP Java con un'interfaccia a menu nel terminale. Usa la libreria **Smack 4.3.5** per collegarsi a un server, autenticarsi, gestire contatti e presenza, scambiare messaggi, inviare file e interagire con chat di gruppo.

È una base di studio e sperimentazione: diverse funzionalità sono abbozzate o presentano errori descritti in questa guida. La presenza di una voce nel menu non garantisce che la relativa operazione funzioni correttamente. Nei sorgenti esaminati non compare una logica applicativa specifica per ARBIT/LDO.

La guida descrive il codice presente nel workspace, inclusa la configurazione attuale `127.0.0.1:5222` con dominio `example.com`. Le proposte per il client custom sono indicate come tali e **non sono già implementate**. La documentazione deriva dall'analisi dei sorgenti; non certifica un collaudo con un server reale.

## Indice

1. [XMPP: i concetti di base](#1-xmpp-i-concetti-di-base)
2. [Struttura e dipendenze](#2-struttura-e-dipendenze)
3. [Avvio e configurazione](#3-avvio-e-configurazione)
4. [XMPPMain: il programma interattivo](#4-xmppmain-il-programma-interattivo)
5. [XMPPClient: stato e connessione](#5-xmppclient-stato-e-connessione)
6. [Account, contatti e presenza](#6-account-contatti-e-presenza)
7. [Messaggi e cronologia](#7-messaggi-e-cronologia)
8. [Invio e ricezione dei file](#8-invio-e-ricezione-dei-file)
9. [Chat di gruppo](#9-chat-di-gruppo)
10. [Limiti ed errori da correggere](#10-limiti-ed-errori-da-correggere)
11. [Come partire per un client custom](#11-come-partire-per-un-client-custom)
12. [Riferimenti](#12-riferimenti)

## 1. XMPP: i concetti di base

### Client, server e libreria

XMPP significa *Extensible Messaging and Presence Protocol*. Permette di scambiare dati strutturati tramite flussi XML persistenti. Il client apre una connessione e può ricevere eventi mentre sta svolgendo altre operazioni. Smack rappresenta gli elementi del protocollo con oggetti Java e gestisce la comunicazione di rete. [RFC 6120](https://xmpp.org/rfcs/rfc6120.html)

Nel nostro caso i ruoli sono:

```text
XMPPMain                 XMPPClient + Smack          Server XMPP
menu e input utente  -->  connessione e messaggi -->  ejabberd / Prosody
                                                        |
                                                        v
                                                   altro client
```

Il programma Java è il client. ejabberd o Prosody gestiscono gli account e instradano gli scambi. Docker è il contenitore in cui può essere eseguito il server: non sostituisce il protocollo né la libreria Java. Il commento nel sorgente cita Prosody, ma non viene usata una sua API proprietaria.

### JID: l'indirizzo XMPP

Un JID identifica un'entità XMPP. Per un utente, un esempio è `alice@example.com/desktop`: `alice` identifica l'account, `example.com` il dominio e `desktop` una risorsa, cioè una specifica connessione. `alice@example.com` è il *bare JID*; con la risorsa diventa un *full JID*. La forma ricorda un indirizzo email, ma identifica un account XMPP. [RFC 6120, indirizzi](https://xmpp.org/rfcs/rfc6120.html#arch-addresses)

Nel codice:

- `JidCreate.entityBareFrom(...)` costruisce un JID di entità senza risorsa, usato per contatti e stanze.
- `JidCreate.bareFrom(...)` converte un indirizzo in bare JID.
- `Localpart.from(...)` prepara la parte locale di un nome utente.
- `Resourcepart.from(...)` prepara una risorsa; nei gruppi viene usata come nickname.
- `XmppStringprepException` segnala problemi nella preparazione degli identificativi.

### Stanza: un elemento del protocollo

In XMPP, una **stanza** è un elemento XML scambiato sul flusso. Non è una stanza di chat. Le tre categorie principali sono `message` per i messaggi, `presence` per disponibilità e sottoscrizioni, e `iq` per richieste e risposte strutturate. Smack gestisce anche gli IQ dietro alle operazioni di livello superiore. [RFC 6120](https://xmpp.org/rfcs/rfc6120.html)

Un esempio schematico di messaggio, scritto qui per spiegare il protocollo:

```xml
<message from="alice@example.com/desktop"
         to="bob@example.com" type="chat">
  <body>Ciao Bob</body>
</message>
```

Il mittente è già nell'attributo `from`: aggiungerlo al testo non è necessario per identificarlo. Il progetto aggiunge invece un prefisso come `alice: Ciao Bob`, che è una convenzione propria dell'applicazione.

### Roster, presenza e sottoscrizioni

Il **roster** è la rubrica dell'account. La **presenza** rappresenta la disponibilità, per esempio disponibile, assente o non disturbare. La **sottoscrizione** riguarda il permesso di ricevere la presenza di un altro utente; è direzionale, quindi i due utenti possono dover autorizzare reciprocamente lo scambio. Non equivale, in generale, a un requisito per inviare qualsiasi messaggio: possono intervenire le politiche del server. [RFC 6121](https://xmpp.org/rfcs/rfc6121.html)

Nel progetto la rubrica viene usata anche per scegliere i destinatari dal menu: questo è un vincolo dell'interfaccia, distinto dal protocollo.

## 2. Struttura e dipendenze

```text
XMPPClientLDO/
├── pom.xml
├── README.md
├── src/main/java/org/example/
│   ├── XMPPMain.java
│   └── XMPPClient.java
├── JavaDoc/
└── target/
```

I sorgenti applicativi sono soltanto due:

| File | Responsabilità |
|---|---|
| `src/main/java/org/example/XMPPMain.java` | Punto d'ingresso, input da tastiera, menu, visualizzazione dei risultati. |
| `src/main/java/org/example/XMPPClient.java` | Connessione, account, rubrica, presenza, messaggi, cronologia, file e gruppi. |
| `pom.xml` | Versioni, dipendenze e costruzione dei JAR tramite Maven. |
| `JavaDoc/` | Documentazione generata già presente; i sorgenti restano il riferimento per il comportamento attuale. |
| `target/` | Risultati della compilazione e del packaging. |

Il `pom.xml` richiede **Java 21** e usa quattro moduli Smack, tutti alla versione **4.3.5**:

| Modulo | Impiego nel progetto |
|---|---|
| `smack-tcp` | Connessione XMPP su TCP. |
| `smack-im` | Chat individuali e rubrica. |
| `smack-extensions` | Funzioni aggiuntive, fra cui account e chat di gruppo. |
| `smack-java7` | Integrazione Smack per l'ambiente Java SE; il nome non significa che questo progetto vada compilato con Java 7. |

Le classi `org.jxmpp.*` sono disponibili tramite le dipendenze transitive. Maven usa `maven-compiler-plugin` 3.13.0, `maven-surefire-plugin` 3.5.4 e `maven-assembly-plugin` 3.3.0. Il plugin Assembly crea un JAR con le dipendenze e imposta `org.example.XMPPMain` come classe principale. Non sono presenti sorgenti di test in `src/test`.

## 3. Avvio e configurazione

### Compilare ed eseguire

Servono JDK 21 e Maven disponibili nel terminale. Dalla cartella del progetto:

```powershell
java -version
mvn -version
mvn package
java -jar target/XMPPClientLDO-1.0.0-SNAPSHOT-jar-with-dependencies.jar
```

Il primo download delle dipendenze richiede l'accesso ai repository Maven. Usare il JAR con suffisso `jar-with-dependencies`: il JAR ordinario non incorpora le librerie Smack. Questi comandi descrivono la configurazione del POM; non sono stati eseguiti come parte di questo aggiornamento della documentazione.

### Host di rete e dominio degli account

La configurazione corrente in `XMPPClient` è:

```java
private static final String XMPP_SERVER = "127.0.0.1";
private static final int PORT = 5222;
private static final String DOMAIN = "example.com";
```

| Valore | Significato |
|---|---|
| `XMPP_SERVER` | Indirizzo di rete al quale Java apre la connessione TCP. |
| `PORT` | Porta raggiungibile dal client. |
| `DOMAIN` | Dominio XMPP degli account, per esempio `alice@example.com`. |

Il server deve gestire realmente `example.com`; questa stringa non configura automaticamente il server. Nel login si inserisce normalmente `alice`, mentre per aggiungere un contatto si usa `bob@example.com`.

Se il server gira in Docker sullo stesso PC del client, la porta interna deve essere pubblicata, per esempio `5222:5222`. Con una pubblicazione `15222:5222`, il programma sul PC deve usare `PORT = 15222`. Se Java gira in un altro container, `127.0.0.1` indica quel container: va sostituito con un indirizzo del server raggiungibile dalla sua rete.

Host, dominio, credenziali, stanza di gruppo e percorso dei file non sono configurabili tramite argomenti: `main(String[] args)` non utilizza `args`.

### TLS e login

Il builder contiene `SecurityMode.disabled`: **TLS è disabilitato**, non semplicemente facoltativo. Un server che richiede TLS può rifiutare il collegamento o l'autenticazione. Per un client custom va configurato TLS con verifica del certificato; cambiare solo la modalità non risolve un certificato non attendibile o non coerente con l'identità del server.

Per il primo collaudo è più semplice predisporre due account direttamente sul server: il percorso di registrazione del programma dipende da credenziali fisse e dalla politica di registrazione del server.

## 4. XMPPMain: il programma interattivo

### Preparazione e ciclo esterno

`main()` crea uno `Scanner` su `System.in`, due variabili per le scelte e una mappa:

```java
Map<String, List<String>> messageHistory = new HashMap<>();
```

La chiave rappresenta una conversazione; il valore è la lista delle sue righe di testo. La mappa nasce prima del ciclo principale, quindi può essere condivisa dai diversi oggetti `XMPPClient` creati durante la stessa esecuzione.

A ogni iterazione del `do ... while` esterno viene costruito un nuovo `XMPPClient(messageHistory)`. Il costruttore tenta già la connessione, prima che l'utente scelga login, registrazione o uscita.

`scanner.nextInt()` legge una scelta numerica. Le successive chiamate a `nextLine()` servono spesso a consumare la fine riga prima di leggere testo. Non c'è una gestione generale degli input non numerici: una lettera al posto di un numero può interrompere il programma con un'eccezione.

### Login e menu interno

Con l'opzione 1 il programma legge username e password, chiama `login()` e, se riesce, invia ai contatti una notifica testuale tramite `sendConnectionNotificationToFriends()`.

Il menu successivo collega ogni scelta ai seguenti metodi:

| Opzione | Comportamento e metodi |
|---|---|
| 1. Show contacts | `getContactsWithStatus()`: stampa contatti e disponibilità. |
| 2. View user information | `getUserStatus(jid)`: consulta la presenza di un contatto, non un profilo completo. |
| 3. Add contact | `addContact(jid, nickname)`: aggiunge un contatto alla rubrica. |
| 4. Accept friend requests | `getSubscriptionRequests()` e `acceptAllRequests()`: mostra e accetta tutte le richieste memorizzate. |
| 5. Switch Presence Mode | Traduce la scelta in `Presence.Mode` e chiama `setPresenceMode()`. |
| 6. Direct messages | Legge `getContacts()`, mostra `getChatHistory()` e può inviare un messaggio con `sendMessage()`. |
| 7. Send Files | Legge un percorso locale e chiama `sendFile()` per il contatto selezionato. |
| 8. Group messages | Apre un sottomenu per inviti, creazione, ricezione di inviti e invio al gruppo. |
| 9. Delete account | Chiede conferma, chiama `deleteAccount()` e, in caso di successo, disconnette. |
| 10. Log out | Recupera il riferimento alla cronologia e chiama `disconnect()`. |

La chat privata non è una schermata che si aggiorna continuamente: il menu stampa la cronologia disponibile e consente un invio alla volta. I messaggi ricevuti dal listener vengono aggiunti alla mappa, senza aggiornare automaticamente quella schermata.

**Errore attuale:** il ciclo interno termina con `while (lgchoice != 9)`, ma il logout è 10. Di conseguenza il logout lascia il menu attivo dopo la disconnessione. Inoltre, annullare la cancellazione lascia `lgchoice = 9` e fa uscire dal menu; una cancellazione riuscita lo imposta a 10 e mantiene il ciclo. Prima di usare il menu come base va corretta la gestione dello stato e delle uscite.

### Registrazione e chiusura

L'opzione 2 del menu esterno tenta prima un login con credenziali fisse nel sorgente, poi chiama `createAccount()`. Non verifica l'esito di quel login prima di proseguire. Questo percorso non è una registrazione generica pronta per un altro server.

L'opzione 3 termina il ciclo esterno, ma non chiama esplicitamente `disconnect()` sulla connessione aperta dal costruttore. Nel sottomenu dei gruppi, invece, l'opzione 5 disconnette e chiama `System.exit(0)`: chiude tutta l'applicazione.

## 5. XMPPClient: stato e connessione

### Campi della classe

| Campo | Uso effettivo |
|---|---|
| `username`, `password` | Conservano le credenziali dopo il login. |
| `connection` | Riferimento Smack alla connessione, dichiarato come `AbstractXMPPConnection`. |
| `roster` | Rubrica dell'account, recuperata nei metodi di consultazione. |
| `incomingSubscriptionRequests` | Lista in memoria dei JID che hanno chiesto la sottoscrizione alla presenza. |
| `messageHistory` | Riferimento alla mappa ricevuta dal programma principale. |
| `notifications` | Lista dichiarata ma non utilizzata dal resto del codice. |

### Costruttore e connect()

Il costruttore chiama `connect()` e solo dopo assegna `this.messageHistory`. Il valore booleano restituito da `connect()` viene ignorato: l'oggetto viene restituito anche se la connessione fallisce.

`connect()` svolge questi passaggi:

1. Costruisce `XMPPTCPConnectionConfiguration` con host, dominio, porta e TLS disabilitato.
2. Passa anche username e password al builder, ma alla prima costruzione sono ancora `null`.
3. Crea `XMPPTCPConnection` e chiama `connection.connect()`.
4. Recupera il roster e imposta `SubscriptionMode.manual`.
5. Registra un callback per aggiungere alla lista le richieste di sottoscrizione ricevute.
6. Registra il listener di ricezione descritto nella sezione sui messaggi.
7. Restituisce `true`, oppure stampa l'eccezione e restituisce `false`.

Il `Roster roster` dichiarato dentro `connect()` è una variabile locale che nasconde il campo omonimo. Il campo viene valorizzato separatamente da altri metodi.

### Login e disconnessione

`login(username, password)` chiama `connection.login(...)`, poi memorizza le credenziali. Distingue `SASLErrorException` dalle altre eccezioni, ma presenta tutti gli errori SASL come credenziali errate, una diagnosi potenzialmente troppo generica.

`disconnect()` controlla che la connessione esista e sia connessa, poi la chiude. Non azzera cronologia, richieste o credenziali conservate nei campi.

La distinzione utile nel codice è:

```text
oggetto creato -> connect() -> trasporto connesso -> login() -> autenticato
                                                            |
                                                       operazioni utente
                                                            |
                                                       disconnect()
```

`isConnected()` non equivale a `isAuthenticated()`. Molti metodi controllano soltanto il primo stato, pur essendo pensati per un utente autenticato. Non è implementata una gestione applicativa completa di riconnessione, nuovo login e ripristino dei gruppi.

### Callback e concorrenza

Una lambda come `stanza -> { ... }` definisce una funzione che Smack esegue quando riceve un evento. `addAsyncStanzaListener()` la esegue in modo asincrono rispetto al menu. La ricezione può quindi modificare i dati mentre il terminale li sta leggendo.

`HashMap` e `ArrayList` sono condivise senza sincronizzazione. Per un client custom serve una strategia esplicita: per esempio un singolo executor che gestisce lo stato, oppure strutture concorrenti e copie dei dati per la UI. Va anche inizializzato tutto lo stato prima di registrare listener; username e cronologia, oggi, non sono disponibili durante tutte le fasi di inizializzazione.

## 6. Account, contatti e presenza

### createAccount() e deleteAccount()

`createAccount(newUsername, newPassword)` richiede una connessione già autenticata, ottiene `AccountManager`, abilita esplicitamente operazioni sensibili sulla connessione non sicura e richiede la creazione dell'account. Il server può rifiutare l'operazione: il metodo non attribuisce privilegi amministrativi al client.

Nel ramo in cui i prerequisiti non sono soddisfatti, il codice stampa lo stato della connessione senza proteggere tutti gli accessi da `null`: è possibile un `NullPointerException`.

`deleteAccount()` usa `AccountManager.deleteAccount()` sull'account autenticato. È un'operazione sul server: non si limita a cancellare dati locali o a effettuare logout.

### Lettura e modifica della rubrica

- `getContacts()` percorre `roster.getEntries()` e restituisce i JID come stringhe.
- `getContactsWithStatus()` aggiunge la presenza restituita da `roster.getPresence(...)` e costruisce una stringa da visualizzare.
- `getUserStatus(targetUser)` cerca l'utente nel roster e restituisce una descrizione testuale della sua presenza. Se non è in rubrica, segnala che non è stato trovato.
- `addContact(contactJID, nickname)` chiama `roster.createEntry(...)` senza assegnare gruppi di rubrica. Il nickname è un'etichetta del contatto, non il suo identificativo di login.

La logica di formattazione della presenza è duplicata in due metodi. Il messaggio di stato personalizzato viene letto solo nel ramo `available`, anche se potrebbe accompagnare altri stati.

### acceptAllRequests()

Per ogni JID memorizzato, il metodo invia prima una presenza `subscribed` e poi `subscribe`. La prima approva la richiesta ricevuta; la seconda chiede la sottoscrizione nella direzione opposta. [RFC 6121, sottoscrizioni](https://xmpp.org/rfcs/rfc6121.html)

La lista non viene svuotata dopo l'accettazione e non vengono eliminate eventuali richieste duplicate. `getSubscriptionRequests()` restituisce direttamente la lista interna, senza una copia.

### setPresenceMode() e notifiche

`setPresenceMode(mode, statusMessage)` costruisce una presenza disponibile, imposta la modalità e l'eventuale testo, poi la invia con `connection.sendStanza(presence)`.

| Modalità Java | Significato presentato dal menu |
|---|---|
| `available` | Disponibile |
| `chat` | Disponibile per chattare |
| `away` | Assente |
| `xa` | Assente per un periodo prolungato |
| `dnd` | Non disturbare |

Il metodo invia anche un messaggio di chat a ogni contatto con il testo del cambiamento. `sendConnectionNotificationToFriends()` fa qualcosa di simile dopo il login. Questi messaggi testuali sono una scelta dell'applicazione, aggiuntiva rispetto alla presenza XMPP, e possono riempire inutilmente la cronologia. Un errore nell'invio di una di queste notifiche non rende necessariamente `false` il risultato di `setPresenceMode()`.

## 7. Messaggi e cronologia

### Invio: sendMessage(contactJID, messageBody, base64File)

Il metodo:

1. Verifica che la connessione sia attiva.
2. Converte il destinatario in `EntityBareJid`.
3. Recupera `ChatManager` e una `Chat` mediante `chatWith(jid)`.
4. Costruisce la chiave locale della conversazione.
5. Prepara il testo, lo salva nella cronologia e chiama `chat.send(...)`.
6. Restituisce `true` se non intercetta un'eccezione, altrimenti `false`.

Per un messaggio normale il testo inviato è `username + ": " + messageBody`. Se è presente `base64File`, viene usato il formato per i file descritto nella prossima sezione.

Il messaggio viene salvato **prima** del tentativo di invio: può quindi apparire nella cronologia anche se l'invio fallisce. Inoltre `true` non rappresenta una conferma di lettura o una ricevuta applicativa del destinatario. Il menu ignora il risultato e stampa comunque `Message sent`.

### Ricezione: registerMessageListener() e lambda di connect()

`registerMessageListener(listener)` registra il callback con:

```java
connection.addAsyncStanzaListener(listener, MessageTypeFilter.NORMAL);
```

Questo filtro accetta messaggi di tipo `normal`. L'invio tramite le chat individuali usa il tipo `chat`: il listener scelto non copre quel flusso. È uno dei primi punti da correggere per una prova fra due client.

La lambda registrata in `connect()` legge `message.getBody()` e applica un formato testuale personalizzato:

| Corpo atteso | Interpretazione |
|---|---|
| `alice: Ciao` | Messaggio di Alice da aggiungere alla cronologia. |
| `File prova.txt alice: SGVsbG8=` | File da decodificare e salvare; in cronologia viene aggiunto l'esito. |
| `alice has just connected.` | Notifica attribuita alla prima parola, cioè Alice. |

Questo parser non usa `message.getFrom()` per determinare il mittente. Un altro client che invia semplicemente `Ciao` viene interpretato male; inoltre il testo del corpo può dichiarare un'identità arbitraria. I separatori e gli indici degli array non vengono validati in modo robusto: nomi di file con spazi, formati inattesi o dati mancanti possono produrre errori.

Per il client custom conviene usare il mittente della stanza XMPP e conservare il corpo come contenuto, separando i metadati dal testo.

### Come vengono indicizzate le conversazioni

`getChatKey(user1, user2)` ordina alfabeticamente le due stringhe e le concatena con uno spazio:

```text
getChatKey("alice", "bob") -> "alice bob"
getChatKey("bob", "alice") -> "alice bob"
```

`addMessageToChatHistory(key, message)` recupera la lista oppure ne crea una nuova, aggiunge la stringa e la reinserisce nella mappa. `getChatHistory(contactJID)` restituisce la lista esistente o una lista vuota. `getMessageHistory()` espone la mappa interna.

In invio e nella lettura della cronologia, il contatto viene ancora elaborato con `replace("@alumchat.xyz", "")`. Con il dominio attuale `example.com`, il suffisso rimane: l'invio può usare la chiave `alice bob@example.com`, mentre la ricezione del testo `bob: ...` usa `alice bob`. I due lati della stessa conversazione finiscono così in liste diverse.

Per risolvere il problema in modo generale, utilizzare bare JID completi e coerenti per entrambi i partecipanti. Rimuovere soltanto il nuovo suffisso non risolverebbe le collisioni fra utenti omonimi su domini differenti.

La cronologia è solo in RAM, senza timestamp, identificativi di messaggio, stati di consegna o persistenza. Non viene interrogato un archivio del server. Le liste restituite non sono copie: modificarle significa modificare lo stato interno.

## 8. Invio e ricezione dei file

`sendFile(contactJID, filePath)` legge l'intero file con `Files.readAllBytes()`, lo codifica in Base64 e passa `nomeFile + ": " + contenuto` a `sendMessage()`.

Esempio del corpo trasmesso:

```text
File prova.txt alice: SGVsbG8=
```

`receiveFile(user, nameFile, base64File)` decodifica il testo e scrive i byte con `Files.write()`. La destinazione è fissa nel sorgente:

```text
C:/Users/oestr/OneDrive/Escritorio/recibirPrueba/
```

Il metodo non crea la directory. Il nome ricevuto viene concatenato al percorso senza verificarlo, e un file esistente può essere sovrascritto. È necessario validare il nome e verificare che il percorso finale resti nella directory scelta. Un Base64 non valido può inoltre causare un'eccezione che non è coperta dal solo `catch (IOException)`.

Questo è un formato applicativo ad hoc, non un trasferimento file interoperabile già completo. Un normale client XMPP potrebbe visualizzare soltanto il testo Base64. Non ci sono controllo delle dimensioni, avanzamento o ripresa del trasferimento; tutto il contenuto viene caricato in memoria e inserito in un messaggio. Anche qui il menu stampa `File sent` senza verificare il risultato.

## 9. Chat di gruppo

Le chat multiutente sono gestite tramite `MultiUserChatManager` e `MultiUserChat`, abbreviato **MUC**. Un indirizzo di esempio è `laboratorio@conference.example.com`; un partecipante può comparire come `laboratorio@conference.example.com/alice`. Il servizio e il dominio effettivi dipendono dalla configurazione del server. [XEP-0045](https://xmpp.org/extensions/xep-0045.html)

| Metodo | Cosa fa il codice |
|---|---|
| `createGroupChatAndInvite(roomName)` | Ottiene la MUC, tenta la creazione con nickname uguale allo username, invia un modulo di configurazione vuoto e invita lo stesso utente corrente. |
| `inviteUserToGroupChat(roomName, userJID)` | Invia un invito al JID indicato. |
| `sendMessageToGroupChat(roomName, message)` | Costruisce un oggetto `Message` con il corpo e lo invia alla MUC. |
| `registerGroupMessageListener(roomName)` | Registra un listener che stampa i corpi ricevuti dal gruppo nel terminale. Non li inserisce nella mappa della cronologia privata. |
| `acceptInvitationAndJoinGroupChat(roomName)` | Registra un listener degli inviti; il callback `invitationReceived(...)` tenta `room.join(...)` quando arriva un invito. Non esegue immediatamente l'ingresso nella stanza passata come parametro. |

Ci sono tre incoerenze da conoscere:

1. La creazione usa `roomName + "@" + XMPP_SERVER`, quindi oggi, per esempio, `laboratorio@127.0.0.1`. Gli altri metodi usano `laboratorio@conference.example.com`. Non identificano la stessa stanza e l'host TCP non va usato come dominio MUC.
2. Il menu passa sempre `Prueba4` alla funzione di ricezione inviti. Il callback entra nella stanza dell'invito ricevuto, ma registra l'ascolto usando il nome passato: le due stanze potrebbero essere diverse.
3. Ogni chiamata può aggiungere nuovi listener. Non vengono gestiti esplicitamente rimozione, duplicati, password delle stanze o ripristino dopo una disconnessione.

Per un client custom occorre un parametro separato per il servizio MUC e una distinzione chiara fra creare una stanza, entrarvi direttamente e accettare un invito.

## 10. Limiti ed errori da correggere

Questa tabella riassume le conseguenze pratiche dei problemi descritti sopra. Sono osservazioni sui sorgenti, non risultati di prove sul server.

| Priorità di lavoro | Problema | Conseguenza |
|---|---|---|
| Prima chat | Filtro `NORMAL` per la ricezione | I messaggi individuali `chat` non vengono gestiti dal listener registrato. |
| Prima chat | Mittente ricavato dal corpo | Errori con client standard e attribuzione non attendibile dei messaggi. |
| Prima chat | Suffisso `@alumchat.xyz` e chiavi miste | Cronologia spezzata con il dominio attuale. |
| Prima chat | Connessione fallita ignorata e controlli parziali | Il menu può proporre operazioni in uno stato non valido. |
| Prima chat | Logout e condizione del ciclo incoerenti | Menu attivo dopo logout e uscite inattese. |
| Uso affidabile | Esiti ignorati e salvataggio prima dell'invio | Messaggi presentati come inviati anche in caso di errore. |
| Uso affidabile | Collezioni condivise senza coordinamento | Possibili problemi fra callback e letture del menu. |
| Uso affidabile | Nessuna persistenza e nessun recupero applicativo | Perdita della cronologia alla chiusura e sessioni da ripristinare. |
| Connessione e account | TLS disabilitato e credenziali fisse per la registrazione | Incompatibilità con la configurazione del server e assenza di protezione TLS. |
| Funzioni aggiuntive | Percorso file fisso e input remoto non validato | Errori di salvataggio, sovrascritture o scritture fuori dalla destinazione prevista. |
| Funzioni aggiuntive | Indirizzi MUC e listener incoerenti | Creazione, ingresso e ricezione possono riferirsi a stanze differenti. |

## 11. Come partire per un client custom

### Primo obiettivo: due utenti che si scambiano testo

Procedere per passi permette di distinguere problemi del trasporto, del server e dell'applicazione:

1. Configurare host, porta, dominio e TLS coerenti con il proprio server.
2. Predisporre due account di prova, per esempio Alice e Bob sul dominio configurato.
3. Rendere espliciti connessione e login, gestendo un eventuale errore prima di aprire il menu operativo.
4. Correggere ricezione e identificazione del mittente, usando `ChatManager.addIncomingListener()` oppure un filtro coerente sui messaggi.
5. Usare bare JID completi nella cronologia e rimuovere il parsing del prefisso `username:` dai normali messaggi.
6. Correggere logout e gestione dei risultati.
7. Avviare due processi Java e provare uno scambio bidirezionale.

Esempio di flusso desiderato dopo queste correzioni:

```text
Alice accede come alice@example.com
Bob accede come bob@example.com
Alice invia "Ciao Bob" al JID bob@example.com
Bob riceve mittente=alice@example.com e corpo="Ciao Bob"
Bob risponde e Alice visualizza la risposta nella stessa conversazione
```

Come controlli manuali, verificare anche password errata, server non disponibile, messaggi contenenti `: ` e spazi, invio dopo disconnessione e logout seguito da nuovo login. Una stampa `Message sent` non basta: verificare la ricezione nell'altro processo. Aggiungere file e MUC solo dopo questo percorso minimo.

### Separare le responsabilità

La struttura seguente è una proposta per l'evoluzione, non descrive classi già presenti:

| Componente proposto | Responsabilità |
|---|---|
| `XmppConfig` | Host, porta, dominio, politica TLS, risorsa, servizio MUC e directory dei file. |
| `XmppSession` | Connessione, autenticazione, disconnessione e ripristino della sessione. |
| `ChatService` | Invio e ricezione con destinatari e mittenti rappresentati come JID. |
| `ContactService` | Roster e richieste di sottoscrizione. |
| `GroupChatService` | Creazione, ingresso, inviti e uscita dalle MUC. |
| `MessageRepository` | Cronologia con oggetti messaggio, eventualmente salvata su database. |
| Interfaccia CLI o grafica | Input e visualizzazione, senza parsing del protocollo. |

Un oggetto messaggio dovrebbe distinguere almeno mittente, destinatario, corpo, identificativo, istante e stato locale di invio. La UI dovrebbe ricevere eventi dal servizio invece di ricostruire il significato da stringhe stampate. Gli errori dovrebbero distinguere autenticazione, rete, timeout, JID non valido e rifiuto del server.

### Messaggi applicativi al posto della sola chat

Per un client che collega componenti software, un account può identificare un servizio o un dispositivo. Il corpo può contenere un formato concordato, per esempio questo JSON dimostrativo:

```json
{
  "version": 1,
  "type": "status-request",
  "requestId": "req-001",
  "payload": {}
}
```

Il server instrada il messaggio; è il client destinatario a interpretare `type`, verificare i permessi applicativi ed eventualmente rispondere riportando `requestId`. XMPP non esegue automaticamente il comando rappresentato dal JSON. Entrambi i client devono concordare formato, versioni, validazione, timeout e gestione dei duplicati. Il progetto attuale non implementa questo schema.

Per scambi più strutturati si possono progettare estensioni XMPP e interazioni IQ. È uno sviluppo successivo: la prima base da rendere affidabile resta connessione, identità del mittente e scambio bidirezionale.

## 12. Riferimenti

- [RFC 6120 — XMPP Core](https://xmpp.org/rfcs/rfc6120.html): flusso XML, indirizzamento, TLS, autenticazione e stanza.
- [RFC 6121 — Instant Messaging and Presence](https://xmpp.org/rfcs/rfc6121.html): messaggi, roster, presenza e sottoscrizioni.
- [XEP-0045 — Multi-User Chat](https://xmpp.org/extensions/xep-0045.html): stanze di gruppo e inviti.

Per capire l'implementazione di questo progetto, leggere nell'ordine `XMPPMain.main()`, il costruttore di `XMPPClient`, `connect()`, `login()`, `sendMessage()` e il listener registrato in `connect()`. Seguire poi contatti, presenza, file e gruppi in base alle funzionalità da sviluppare.
