package edu.hm.dako.chat.client;

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
}
