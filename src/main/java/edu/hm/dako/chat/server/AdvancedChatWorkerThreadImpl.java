package edu.hm.dako.chat.server;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ClientConversationStatus;
import edu.hm.dako.chat.common.ClientListEntry;
import edu.hm.dako.chat.common.ExceptionHandler;
import edu.hm.dako.chat.connection.Connection;
import edu.hm.dako.chat.connection.ConnectionTimeoutException;
import edu.hm.dako.chat.connection.EndOfFileException;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.Vector;

/**
 * Worker-Thread zur serverseitigen Bedienung einer Session mit einem Client.
 * Jedem Chat-Client wird serverseitig ein Worker-Thread zugeordnet.
 *
 * @author Nisi
 *
 */
public class AdvancedChatWorkerThreadImpl extends AbstractWorkerThread {

	private static Log log = LogFactory.getLog(AdvancedChatWorkerThreadImpl.class);

	public AdvancedChatWorkerThreadImpl(Connection con, SharedChatClientList clients, SharedServerCounter counter,
			ChatServerGuiInterface serverGuiInterface) {

		super(con, clients, counter, serverGuiInterface);
	}

	@Override
	public void run() {
		log.debug("ChatWorker-Thread erzeugt, Threadname: " + Thread.currentThread().getName());
		while (!finished && !Thread.currentThread().isInterrupted()) {
			try {
				// Warte auf naechste Nachricht des Clients und fuehre
				// entsprechende Aktion aus
				handleIncomingMessage();
			} catch (Exception e) {
				log.error("Exception waehrend der Nachrichtenverarbeitung");
				ExceptionHandler.logException(e);
			}
		}
		log.debug(Thread.currentThread().getName() + " beendet sich");
		closeConnection();
	}

	/**
	 * Senden eines Login-List-Update-Event an alle angemeldeten Clients
	 *
	 * @param pdu
	 *            Zu sendende PDU
	 */
	protected void sendLoginListUpdateEvent(ChatPDU pdu) {

		// Liste der eingeloggten bzw. sich einloggenden User ermitteln
		Vector<String> clientList = clients.getRegisteredClientNameList();

		log.debug("Aktuelle Clientliste, die an die Clients uebertragen wird: " + clientList);

		pdu.setClients(clientList);

		Vector<String> clientList2 = clients.getClientNameList();
		for (String s : new Vector<String>(clientList2)) {
			log.debug("Fuer " + s + " wird Login- oder Logout-Event-PDU an alle aktiven Clients gesendet");

			ClientListEntry client = clients.getClient(s);
			try {
				if (client != null) {

					client.getConnection().send(pdu);
					log.debug("Login- oder Logout-Event-PDU an " + client.getUserName() + " gesendet");
					clients.incrNumberOfSentChatEvents(client.getUserName());
					eventCounter.getAndIncrement();
				}
			} catch (Exception e) {
				log.error("Senden einer Login- oder Logout-Event-PDU an " + s + " nicht moeglich");
				ExceptionHandler.logException(e);
			}
		}
	}

	@Override
	protected void loginRequestAction(ChatPDU receivedPdu) {

		log.debug("Login-Request-PDU für " + receivedPdu.getUserName() + " empfangen");

		// Neuer Client moechte sich einloggen, Client in Client-Liste
		// eintragen
		if (!clients.existsClient(receivedPdu.getUserName())) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
			ClientListEntry client = new ClientListEntry(receivedPdu.getUserName(), connection);
			client.setLoginTime(System.nanoTime());
			clients.createClient(receivedPdu.getUserName(), client);
			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.REGISTERING);
			log.debug("User " + receivedPdu.getUserName() + " nun in Clientliste");

			userName = receivedPdu.getUserName();
			clientThreadName = receivedPdu.getClientThreadName();
			Thread.currentThread().setName(receivedPdu.getUserName());
			log.debug("Laenge der Clientliste: " + clients.size());
			serverGuiInterface.incrNumberOfLoggedInClients();

            // ADVANCED: Warteliste für Event erzeugen
            clients.createWaitList(receivedPdu.getUserName());

			// Login-Event an alle Clients (auch an den gerade aktuell
			// anfragenden) senden
			ChatPDU pdu = ChatPDU.createLoginEventPdu(userName, receivedPdu);
			sendLoginListUpdateEvent(pdu);

		} else {
			// User bereits angemeldet, Fehlermeldung an Client senden,
			// Fehlercode an Client senden
			ChatPDU pdu = ChatPDU.createLoginErrorResponsePdu(receivedPdu, ChatPDU.LOGIN_ERROR);

			try {
				connection.send(pdu);
				log.debug("Login-Response-PDU an " + receivedPdu.getUserName() + " mit Fehlercode "
						+ ChatPDU.LOGIN_ERROR + " gesendet");
			} catch (Exception e) {
				log.debug("Senden einer Login-Response-PDU an " + receivedPdu.getUserName() + " nicth moeglich");
				ExceptionHandler.logExceptionAndTerminate(e);
			}
		}
	}

	@Override
	protected void logoutRequestAction(ChatPDU receivedPdu) {

		logoutCounter.getAndIncrement();
		log.debug("Logout-Request von " + receivedPdu.getUserName() + ", LogoutCount = " + logoutCounter.get());

		log.debug("Logout-Request-PDU von " + receivedPdu.getUserName() + " empfangen");

		if (!clients.existsClient(userName)) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {

			// ADVANCED: Warteliste erstellen
			clients.createWaitList(receivedPdu.getUserName());
			ChatPDU pdu = ChatPDU.createLogoutEventPdu(userName,receivedPdu);

			clients.changeClientStatus(receivedPdu.getUserName(), ClientConversationStatus.UNREGISTERING);
			sendLoginListUpdateEvent(pdu);
			serverGuiInterface.decrNumberOfLoggedInClients();
		}
	}

	@Override
	protected void chatMessageRequestAction(ChatPDU receivedPdu) {

		clients.setRequestStartTime(receivedPdu.getUserName(), startTime);
		clients.incrNumberOfReceivedChatMessages(receivedPdu.getUserName());
		serverGuiInterface.incrNumberOfRequests();

		log.debug("Chat-Message-Request-PDU von " + receivedPdu.getUserName() + " mit Sequenznummer "
				+ receivedPdu.getSequenceNumber() + " empfangen");

		// bleibt bestehen
		if (!clients.existsClient(receivedPdu.getUserName())) {
			log.debug("User nicht in Clientliste: " + receivedPdu.getUserName());
		} else {

			// ADVANCED: Warteliste für Event erzeugen
			clients.createWaitList(receivedPdu.getUserName());

			// Initiiator ermitteln
			ClientListEntry sender = clients.getClient(receivedPdu.getUserName());

			// Alle Clients in die Warteliste
			Vector<String> waitList = sender.getWaitList();
			log.debug("Warteliste: " + waitList);
			log.debug("Anzahl der User in der Warteliste: " + waitList.size());

			// Liste der betroffenen Clients ermitteln (bereits in Simple vorhanden)
			Vector<String> sendList = clients.getClientNameList();
			ChatPDU pdu = ChatPDU.createChatMessageEventPdu(userName, receivedPdu);

			// Event an Clients senden
			for (String s : new Vector<String>(sendList)) {
				ClientListEntry client = clients.getClient(s);
				try {
					if ((client != null) && (client.getStatus() != ClientConversationStatus.UNREGISTERED)) {
						pdu.setUserName(client.getUserName());
						client.getConnection().send(pdu);
						log.debug("Chat-Event-PDU an " + client.getUserName() + " gesendet");
						clients.incrNumberOfSentChatEvents(client.getUserName());
						eventCounter.getAndIncrement();
						log.debug(userName + ": EventCounter erhoeht = " + eventCounter.get()
								+ ", Aktueller ConfirmCounter = " + confirmCounter.get()
								+ ", Anzahl gesendeter ChatMessages von dem Client = "
								+ receivedPdu.getSequenceNumber());
					}
				} catch (Exception e) {
					log.debug("Senden einer Chat-Event-PDU an " + client.getUserName() + " nicht moeglich");
					ExceptionHandler.logException(e);
				}
			}

			log.debug("Aktuelle Laenge der Clientliste: " + clients.size());
		}
	}

	/**
	 * ADVANCED: ChatMessageEvent bestaetigen
	 *
	 * @param receivedPdu
	 *            . Empfangene PDU
	 */
	protected void messageConfirmAction(ChatPDU receivedPdu) {

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());
		confirmCounter.getAndIncrement();

		log.debug("Message-Confirm-PDU von " + receivedPdu.getUserName() + " fuer die Nachricht vom Client "
				+ receivedPdu.getEventUserName() + " empfangen");

		log.debug(userName + ": ConfirmCounter fuer ChatMessage erhoeht = " + confirmCounter.get()
				+ ", Aktueller EventCounter = " + eventCounter.get()
				+ ", Anzahl gesendeter ChatMessages von dem Client = " + receivedPdu.getSequenceNumber());

		// Respons wurde vorher im Request bereits gesendet
		try {
			clients.deleteWaitListEntry(receivedPdu.getEventUserName(), receivedPdu.getUserName());

			if (clients.getWaitListSize(receivedPdu.getEventUserName()) == 0) {

				ClientListEntry client = clients.getClient(receivedPdu.getEventUserName());
				if (client != null) {
					ChatPDU responsePdu = ChatPDU.createChatMessageResponsePdu(receivedPdu.getUserName(),
							receivedPdu.getMessage(), receivedPdu.getNumberOfSentEvents(),
							receivedPdu.getNumberOfLostConfirms(), receivedPdu.getNumberOfReceivedConfirms(),
                            receivedPdu.getNumberOfRetries(),
							client.getNumberOfReceivedChatMessages(), receivedPdu.getClientThreadName(),
							(System.nanoTime() - client.getStartTime()));

					if (responsePdu.getServerTime() / 1000000 > 100) {
						log.debug(Thread.currentThread().getName()
								+ ", Benoetigte Serverzeit vor dem Senden der Response-Nachricht > 100 ms: "
								+ responsePdu.getServerTime() + " ns = " + responsePdu.getServerTime() / 1000000
								+ " ms");
					}

					try {
						client.getConnection().send(responsePdu);
						log.debug("Chat-Message-Response-PDU an " + receivedPdu.getUserName() + " gesendet");
					} catch (Exception e) {
						log.debug("Senden einer Chat-Message-Response-PDU an " + client.getUserName()
								+ " nicht moeglich");
						ExceptionHandler.logExceptionAndTerminate(e);
					}
				}
			}
		} catch (Exception e) {
			ExceptionHandler.logException(e);
		}
	}

	/**
	 * ADVANCED: LoginEvent bestaetigen
	 *
	 * @param receivedPdu
	 *            . Empfangene PDU
	 */
	private void loginEventConfirmAction(ChatPDU receivedPdu) throws Exception {

		String eventUserName = receivedPdu.getEventUserName();
		String userName = receivedPdu.getUserName();

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());
		confirmCounter.getAndIncrement();

		log.debug("Login-Confirm-PDU von Client " + userName + " fuer die Nachricht vom Client " + eventUserName
				+ " empfangen");

		try {
			clients.deleteWaitListEntry(eventUserName, userName);
			log.debug(userName + " aus der Warteliste von " + eventUserName + " ausgetragen");

			if (clients.getClient(eventUserName).getStatus() == ClientConversationStatus.REGISTERING) {

				// Der initiierende Client ist im Login-Vorgang
				if (clients.getWaitListSize(eventUserName) == 0) {

					ChatPDU responsePdu = ChatPDU.createLoginResponsePdu(eventUserName, receivedPdu);

					try {
						clients.getClient(eventUserName).getConnection().send(responsePdu);
					} catch (Exception e) {
						log.debug("Senden einer Login-Response-PDU an " + eventUserName + " fehlgeschlagen");
						log.debug("Exception Message: " + e.getMessage());
						throw e;
					}

					log.debug("Login-Response-PDU an Client " + eventUserName + " gesendet");
					clients.changeClientStatus(eventUserName, ClientConversationStatus.REGISTERED);

				} else {
					log.debug("Warteliste von " + eventUserName + " enthält noch "
							+ clients.getWaitListSize(eventUserName) + " Einträge");
				}
			}

		} catch (Exception e) {
			log.debug("Login-Event-Confirm-PDU fuer nicht vorhandenen Client erhalten: " + eventUserName);
		}
	}

	/**
	 * ADVANCED: LoginEvent bestaetigen
	 *
	 * @param receivedPdu
	 *            . Empfangene PDU
	 */
	private void logoutConfirmAction(ChatPDU receivedPdu) throws Exception {

		String eventUserName = receivedPdu.getEventUserName();
		String userName = receivedPdu.getUserName();

		// Empfangene Confirms hochzaehlen
		clients.incrNumberOfReceivedChatEventConfirms(receivedPdu.getEventUserName());
		confirmCounter.getAndIncrement();

		log.debug("Logout-Confirm-PDU von Client " + userName + " fuer die Nachricht vom Client " + eventUserName
				+ " empfangen");

		try {
			clients.deleteWaitListEntry(eventUserName, userName);
			log.debug(userName + " aus der Warteliste von " + eventUserName + " ausgetragen");

			if (clients.getWaitListSize(eventUserName) == 0) {

				//Bugfix Sleep-Timer um 500ms erhöht
				try {
					Thread.sleep(1500);
				} catch (Exception e) {
					ExceptionHandler.logException(e);
				}

                // Worker-Thread des Clients, der den Logout-Request gesendet
                // hat, auch gleich zum Beenden markieren
                clients.finish(eventUserName);
                log.debug("Laenge der Clientliste beim Vormerken zum Loeschen von " + receivedPdu.getUserName() + ": "
                        + clients.size());
				sendLogoutResponse(eventUserName);


			} else {
				log.debug("Warteliste von " + eventUserName + " enthält noch "
							+ clients.getWaitListSize(eventUserName) + " Einträge");
			}

		} catch (Exception e) {
			log.debug("Logout-Confirm-PDU fuer nicht vorhandenen Client erhalten: " + eventUserName);
		}
	}

	/**
	 * Verbindung zu einem Client ordentlich abbauen
	 */
	private void closeConnection() {

		log.debug("Schliessen der Chat-Connection zum " + userName);

		// Bereinigen der Clientliste falls erforderlich

		if (clients.existsClient(userName)) {
			log.debug("Close Connection fuer " + userName
					+ ", Laenge der Clientliste vor dem bedingungslosen Loeschen: " + clients.size());

			clients.deleteClientWithoutCondition(userName);
			log.debug(
					"Laenge der Clientliste nach dem bedingungslosen Loeschen von " + userName + ": " + clients.size());
		}

		try {
			connection.close();
		} catch (Exception e) {
			log.debug("Exception bei close");
			// ExceptionHandler.logException(e);
		}
	}



	/**
	 * Antwort-PDU fuer den initiierenden Client aufbauen und senden
	 *
	 * @param eventInitiatorClient
	 *            Name des Clients
	 */
	private void sendLogoutResponse(String eventInitiatorClient) {

		ClientListEntry client = clients.getClient(eventInitiatorClient);

		if (client != null) {
			ChatPDU responsePdu = ChatPDU.createLogoutResponsePdu(eventInitiatorClient, 0, 0, 0, 0,
					client.getNumberOfReceivedChatMessages(), clientThreadName);

			log.debug(eventInitiatorClient + ": SentEvents aus Clientliste: " + client.getNumberOfSentEvents()
					+ ": ReceivedConfirms aus Clientliste: " + client.getNumberOfReceivedEventConfirms());
			try {
				clients.getClient(eventInitiatorClient).getConnection().send(responsePdu);
			} catch (Exception e) {
				log.debug("Senden einer Logout-Response-PDU an " + eventInitiatorClient + " fehlgeschlagen");
				log.debug("Exception Message: " + e.getMessage());
			}

			log.debug("Logout-Response-PDU an Client " + eventInitiatorClient + " gesendet");
		}
	}

	/**
	 * Prueft, ob Clients aus der Clientliste geloescht werden koennen
	 *
	 * @return boolean, true: Client geloescht, false: Client nicht geloescht
	 */
	private boolean checkIfClientIsDeletable() {

		ClientListEntry client;

		// Worker-Thread beenden, wenn sein Client schon abgemeldet ist
		if (userName != null) {
			client = clients.getClient(userName);
			if (client != null) {
				if (client.isFinished()) {
					// Loesche den Client aus der Clientliste
					// Ein Loeschen ist aber nur zulaessig, wenn der Client
					// nicht mehr in einer anderen Warteliste ist
					log.debug("Laenge der Clientliste vor dem Entfernen von " + userName + ": " + clients.size());
					if (clients.deleteClient(userName) == true) {
						// Jetzt kann auch Worker-Thread beendet werden

						log.debug("Laenge der Clientliste nach dem Entfernen von " + userName + ": " + clients.size());
						log.debug("Worker-Thread fuer " + userName + " zum Beenden vorgemerkt");
						return true;
					}
				}
			}
		}

		// Garbage Collection in der Clientliste durchfuehren
		Vector<String> deletedClients = clients.gcClientList();
		if (deletedClients.contains(userName)) {
			log.debug("Ueber Garbage Collector ermittelt: Laufender Worker-Thread fuer " + userName
					+ " kann beendet werden");
			finished = true;
			return true;
		}
		return false;
	}

	@Override
	protected void handleIncomingMessage() throws Exception {
		if (checkIfClientIsDeletable()) {
			return;
		}

		// Warten auf naechste Nachricht
		ChatPDU receivedPdu = null;

		// Nach einer Minute wird geprueft, ob Client noch eingeloggt ist
		final int RECEIVE_TIMEOUT = 1200000;

		try {
			receivedPdu = (ChatPDU) connection.receive(RECEIVE_TIMEOUT);
			// Nachricht empfangen
			// Zeitmessung fuer Serverbearbeitungszeit starten
			startTime = System.nanoTime();

		} catch (ConnectionTimeoutException e) {

			// Wartezeit beim Empfang abgelaufen, pruefen, ob der Client
			// ueberhaupt noch etwas sendet
			log.debug("Timeout beim Empfangen, " + RECEIVE_TIMEOUT + " ms ohne Nachricht vom Client");

			if (clients.getClient(userName) != null) {
				if (clients.getClient(userName).getStatus() == ClientConversationStatus.UNREGISTERING) {
					// Worker-Thread wartet auf eine Nachricht vom Client, aber es
					// kommt nichts mehr an
					log.error("Client ist im Zustand UNREGISTERING und bekommt aber keine Nachricht mehr");
					// Zur Sicherheit eine Logout-Response-PDU an Client senden und
					// dann Worker-Thread beenden
					finished = true;
				}
			}
			return;

		} catch (EndOfFileException e) {
			log.debug("End of File beim Empfang, vermutlich Verbindungsabbau des Partners fuer " + userName);
			finished = true;
			return;

		} catch (java.net.SocketException e) {
			log.error("Verbindungsabbruch beim Empfang der naechsten Nachricht vom Client " + getName());
			finished = true;
			return;

		} catch (Exception e) {
			log.error("Empfang einer Nachricht fehlgeschlagen, Workerthread fuer User: " + userName);
			ExceptionHandler.logException(e);
			finished = true;
			return;
		}

		// Empfangene Nachricht bearbeiten
		try {
			switch (receivedPdu.getPduType()) {

			case LOGIN_REQUEST:
				// Login-Request vom Client empfangen
				loginRequestAction(receivedPdu);
				break;

			case CHAT_MESSAGE_REQUEST:
				// Chat-Nachricht angekommen, an alle verteilen
				chatMessageRequestAction(receivedPdu);
				break;

			case LOGOUT_REQUEST:
				// Logout-Request vom Client empfangen
				logoutRequestAction(receivedPdu);
				break;

			case MESSAGE_CONFIRM:
				// Message-Confirm vom Client empfangen
				messageConfirmAction(receivedPdu);
				break;

			case LOGIN_CONFIRM:
				// Bestaetigung eines Login-Events angekommen
				try {
					loginEventConfirmAction(receivedPdu);
				} catch (Exception e) {
					ExceptionHandler.logException(e);
				}
				break;

			case LOGOUT_CONFIRM:
				// Bestaetigung eines Logout-Events angekommen
				logoutConfirmAction(receivedPdu);
				break;

			default:
				log.debug("Falsche PDU empfangen von Client: " + receivedPdu.getUserName() + ", PduType: "
						+ receivedPdu.getPduType());
				break;
			}
		} catch (Exception e) {
			log.error("Exception bei der Nachrichtenverarbeitung");
			ExceptionHandler.logExceptionAndTerminate(e);
		}
	}
}
