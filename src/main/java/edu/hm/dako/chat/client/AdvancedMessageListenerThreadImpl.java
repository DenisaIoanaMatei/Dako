package edu.hm.dako.chat.client;

import edu.hm.dako.chat.common.ChatPDU;
import edu.hm.dako.chat.common.ExceptionHandler;
import edu.hm.dako.chat.connection.Connection;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/**
 * Thread wartet auf ankommende Nachrichten vom Server, bearbeitet diese und schickt eine Benachrichtigung,
 * wenn eine Nachricht erfolgreich empfangen wurde.
 *
 * @author Nisi
 *
 */
public class AdvancedMessageListenerThreadImpl extends SimpleMessageListenerThreadImpl {

    private static Log log = LogFactory.getLog(AdvancedMessageListenerThreadImpl.class);

    public AdvancedMessageListenerThreadImpl(ClientUserInterface userInterface,
                                             Connection con, SharedClientData sharedData) {
        super(userInterface, con, sharedData);
    }

    @Override
    protected void chatMessageEventAction(ChatPDU receivedPdu) {

        log.debug(
                "Chat-Message-Event-PDU von " + receivedPdu.getEventUserName() + " empfangen");

        // Eventzaehler und Confirmzaehler (ADVANCED) fuer Testzwecke erhoehen
        sharedClientData.eventCounter.getAndIncrement();
        sharedClientData.confirmCounter.getAndIncrement();

        int events = SharedClientData.messageEvents.incrementAndGet();

        log.debug("MessageEventCounter: " + events);
        log.debug("ConfirmCounter: " + sharedClientData.confirmCounter.get());

        // ADVANCED_CHAT:Chat-Message-Event bestaetigen
        confirmChatMessageEvent(receivedPdu);

        // Empfangene Chat-Nachricht an User Interface zur
        // Darstellung uebergeben
        userInterface.setMessageLine(receivedPdu.getEventUserName(), receivedPdu.getMessage());
    }

    /**
     * Bestaetigung fuer Chat-Event-Message-PDU an Server senden (ADVANCED)
     *
     * @param receivedPdu
     *          Empfangene Chat-Event-Message-PDU
     * @throws Exception
     */
    private void confirmChatMessageEvent(ChatPDU receivedPdu) {
        ChatPDU responsePdu = ChatPDU.createMessageConfirm(sharedClientData.userName,
                receivedPdu);

        try {
            connection.send(responsePdu);
            log.debug("Message-Confirm-PDU fuer " + receivedPdu.getUserName()
                    + " das urspruengliche Event von " + receivedPdu.getEventUserName()
                    + " an den Server gesendet");
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }
    }
}
