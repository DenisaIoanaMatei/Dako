package edu.hm.dako.chat.client;

import edu.hm.dako.chat.common.ColourTypes;
import edu.hm.dako.chat.common.ExceptionHandler;
import edu.hm.dako.chat.common.SystemConstants;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXMLLoader;
import javafx.geometry.*;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.paint.Color;
import javafx.stage.Stage;
import javafx.stage.WindowEvent;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.log4j.PropertyConfigurator;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.regex.Pattern;

/**
 * Benutzeroberflaeche zum Starten des Clients
 *
 * @author Nisi
 *
 */
public class ClientGUINew extends Application implements ClientUserInterface {

    private static Log log = LogFactory.getLog(ClientFxGUI.class);

    private String userName;
    protected String colourCode;

    protected Stage stage;
    private static LoggedInGuiController lc;
    private ClientImpl communicator;
    private ClientModel model = new ClientModel();

    final VBox pane = new VBox(5);

    // Testfelder, Buttons und Labels der ClientGUI
    protected TextField txtUsername;
    protected TextField txtServername;
    protected TextField txtServerPort;
    protected Label lblName;
    protected Label lblIP;
    protected Label lblServerPort;
    protected Label lblServerImpl;
    protected Label lblColourImpl;


    protected Button loginButton;
    protected Button finishButton;

    // Combobox fuer Auswahl der Farbe
    protected ComboBox<String> comboChooseColour;
    protected ComboBox<String> comboBoxImplType;

    private static final Pattern IPV6_PATTERN = Pattern
            .compile("^(?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}$");
    private static final Pattern IPV4_PATTERN = Pattern.compile(
            "(?:(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)\\.){3}(?:25[0-5]|2[0-4][0-9]|[01]?[0-9][0-9]?)");

    // Moegliche Belegungen des Implementierungsfeldes in der GUI
    ObservableList<String> implTypeOptions = FXCollections.observableArrayList(SystemConstants.IMPL_TCP_ADVANCED, SystemConstants.IMPL_TCP_SIMPLE);

    // Moegliche Faarbauswahlen in der GUI
    ObservableList<String> implColourOptions = FXCollections.observableArrayList(ColourTypes.DEFAULT_COLOUR.toString(), ColourTypes.WHITE_COLOUR.toString(),
            ColourTypes.GREY_COLOUR.toString(), ColourTypes.GREEN_COLOUR.toString(),
            ColourTypes.YELLOW_COLOUR.toString(), ColourTypes.PINK_COLOUR.toString());

    public static void main(String[] args) {
        PropertyConfigurator.configureAndWatch("log4j.client.properties", 60 * 1000);
        launch(args);
    }


    public ClientImpl getCommunicator() {
        return communicator;
    }

    public ClientModel getModel() {
        return model;
    }

    @Override
    public void start(final Stage primaryStage) throws Exception {
        primaryStage.setTitle("Anmelden");
        log.debug("Titel erstellt.");
        primaryStage.setScene(new Scene(pane, 280, 320));
        stage = primaryStage;
        primaryStage.show();
        log.debug("Fenster erstellt.");

        pane.setStyle("-fx-background-color: #99CBBC");
        pane.setPadding(new Insets(16, 16, 16, 16));
        log.debug("Hintergrundfarbe.");

        txtUsername = createEditableTextfield("");
        pane.getChildren().add(createSeperator(260));
        log.debug("Textfeld Benutzername.");

        pane.getChildren().add(createInputPane());
        log.debug("Inputparameter.");

        pane.getChildren().add(createSeperator(260));
        pane.getChildren().add(createButtonPane());
        log.debug("Buttons erstellt.");

        reactOnLoginButton();
        reactOnFinishButton();
    }

    private GridPane createInputPane() {
        final GridPane inputPane = new GridPane();

        lblName = createLabel("Benutzername: ");
        lblIP = createLabel("IP-Adresse des Servers: ");
        lblServerPort = createLabel("Serverport: ");
        lblServerImpl = createLabel("Serverauswahl: ");
        lblColourImpl =createLabel("Farbauswahl: ");
        log.debug("Label für Inputparameter erstellt.");


        inputPane.setPadding(new Insets(5, 5, 5, 5));
        inputPane.setVgap(1);

        txtServername = createEditableTextfield("127.0.0.1");
        txtServerPort = createEditableTextfield("50000");
        log.debug("Textfelder für Inputparameter erstellt.");

        comboChooseColour = createComboBox(implColourOptions);
        comboBoxImplType = createComboBox(implTypeOptions);
        log.debug("Dropdown für Inputparameter erstellt.");

        inputPane.add(lblName,1,1);
        inputPane.add(txtUsername, 1, 3);
        inputPane.add(lblIP, 1, 5);
        inputPane.add(txtServername, 1, 7);
        inputPane.add(lblServerPort, 1, 9);
        inputPane.add(txtServerPort, 1, 11);
        inputPane.add(lblServerImpl, 1, 13);
        inputPane.add(comboBoxImplType, 1, 15);
        inputPane.add(lblColourImpl, 1, 17);
        inputPane.add(comboChooseColour, 1, 19);


        log.debug("Fenster für Inputparameter erstellt.");
        return inputPane;
    }

    /**
     * Pane fuer Buttons erzeugen
     *
     * @return HBox
     */
    private HBox createButtonPane() {
        final HBox buttonPane = new HBox(5);

        loginButton = new Button("Einloggen");
        finishButton = new Button("Beenden");
        log.debug("Login. und Beenden-Button erstellt.");

        buttonPane.getChildren().addAll(loginButton, finishButton);
        buttonPane.setAlignment(Pos.CENTER);
        return buttonPane;
    }

    /**
     * Label erzeugen
     *
     * @param value
     * @return Label
     */
    private Label createLabel(String value) {
        final Label label = new Label(value);
        label.setMinSize(200, 17);
        label.setMaxSize(200, 17);
        return label;
    }

    /**
     * Aufbau der Combobox fuer die FArbauswahl in der GUI
     *
     * @param options
     *            Optionen fuer Implementierungstyp
     * @return Combobox
     */
    private ComboBox<String> createComboBox(ObservableList<String> options) {
        final ComboBox<String> comboBox = new ComboBox<>(options);
        comboBox.setMinSize(150, 25);
        comboBox.setMaxSize(150, 25);
        comboBox.setValue(options.get(0));
        comboBox.setStyle(
                "-fx-background-color: white; -fx-border-color: lightgrey; -fx-border-radius: 5px, 5px, 5px, 5px");
        return comboBox;
    }

    /**
     * Trennlinie erstellen
     *
     * @param size
     *            Groesse der Trennlinie
     * @return Trennlinie
     */
    private Separator createSeperator(int size) {
        // Separator erstellen
        final Separator rightSeparator = new Separator(Orientation.HORIZONTAL);
        rightSeparator.setHalignment(HPos.CENTER);
        rightSeparator.setMinWidth(size);
        rightSeparator.setMaxWidth(size);

        return rightSeparator;
    }

    /**
     * Erstellung editierbarer Textfelder
     *
     * @param value
     *            Feldinhalt
     * @return textField
     */
    private TextField createEditableTextfield(String value) {
        TextField textField = new TextField(value);
        textField.setMaxSize(250, 25);
        textField.setMinSize(250, 25);
        textField.setEditable(true);
        textField.setStyle(
                "-fx-background-color: white; -fx-border-color: lightgrey; -fx-border-radius: 5px, 5px, 5px, 5px");
        return textField;
    }

    /**
     * Kommunikationsschnittstelle zur Kommunikation mit dem Chat-Server
     * aktivieren
     *
     * @param String
     *          serverType Servertyp
     * @param port
     *          Serverport
     * @param host
     *          Hostname oder IP-Adresse des Servers
     * @return Referenz auf Kommunikationsobjekt
     */
    public ClientImpl createCommunicator(String serverType, int port, String host) {

        communicator = new ClientImpl(this, port, host, serverType);
        return communicator;
    }

    /**
     * Reaktion auf das Betaetigen des Login-Buttons
     */
    private void reactOnLoginButton() {
        loginButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                performLogin();
            }
        });
    }

    /**
     * Reaktion auf das Betaetigen des Beenden-Buttons
     */
    public void reactOnFinishButton() {
        finishButton.setOnAction(new EventHandler<ActionEvent>() {
            @Override
            public void handle(ActionEvent event) {
                System.exit(0);
            }
        });
    }

    /**
     * Login-Eingaben entgegennehmen, pruefen und Anmeldung beim Server
     * durchfuehren
     */
    public void performLogin() {

        // Benutzernamen pruefen
        userName = txtUsername.getText() + System.getProperty("user.name");
        if (userName.isEmpty() == true) {
            log.debug("Benutzername ist leer");
            setErrorMessage("Chat-Client", "Benutzername fehlt", 1);
            return;
        } else {
            getModel().setUserName(userName);
        }

        // IP-Adresse pruefen

        if (!ipCorrect()) {
            setErrorMessage("Chat-Client",
                    "IP-Adresse nicht korrekt, z.B. 127.0.0.1, 192.168.178.2 oder localhost!", 3);
            lblIP.setTextFill(Color.web(SystemConstants.RED_COLOR));
            return;
        }
        // IP-Adresse ist korrekt
        lblIP.setTextFill(Color.web(SystemConstants.BLACK_COLOR));

        // Serverport pruefen

        int serverPort = 0;
        String valueServerPort = txtServerPort.getText();
        if (valueServerPort.matches("[0-9]+")) {
            serverPort = Integer.parseInt(valueServerPort);
            if ((serverPort < 1) || (serverPort > 65535)) {
                setErrorMessage("Chat-Client",
                        "Serverport ist nicht im Wertebereich von 1 bis 65535!", 2);
                log.debug("Serverport nicht im Wertebereich");
                lblServerPort.setTextFill(Color.web(SystemConstants.RED_COLOR));
                return;
            } else {
                // Serverport ist korrekt
                lblServerPort.setTextFill(Color.web(SystemConstants.BLACK_COLOR));
            }
        } else {
            setErrorMessage("Chat-Client", "Serverport ist nicht numerisch!", 3);
            lblServerPort.setTextFill(Color.web(SystemConstants.RED_COLOR));
            return;
        }

        // Verbindung herstellen und beim Server anmelden

        createCommunicator(comboBoxImplType.getValue(), serverPort,
                txtServername.getText());
        try {
            getCommunicator().login(userName);
        } catch (Exception e2) {

            // Benutzer mit dem angegebenen Namen schon angemeldet
            log.error("Login konnte nicht zum Server gesendet werden, Server aktiv?");
            setErrorMessage("Chat-Client",
                    "Login konnte nicht gesendet werden, vermutlich ist der Server nicht aktiv", 4);
            // Verbindung zum Server wird wieder abgebaut
            getCommunicator().cancelConnection();
        }
    }


    /**
     * Benutzeroberflaeche fuer Chat erzeugen
     */
    public void createNextGui(String colourCode) {
        try {
        	
        	MediaPlayer mediaPlayer = new MediaPlayer(new Media(new File("./src/main/java/edu/hm/dako/chat/client/loginton.mp3").toURI().toString()));
			mediaPlayer.play();
            FXMLLoader loader = new FXMLLoader(getClass().getResource("LoggedInGui.fxml"));
            Parent root = loader.load();
            lc = loader.getController();
            lc.setAppControllerNew(this);

            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    stage.setTitle("Angemeldet");
                    stage.setScene(new Scene(root, 600, 400));
                    //hier sollte die Farbauswahl übernommen werden
                    root.setStyle("-fx-background-color: " + colourCode);
                }
            });
        } catch (Exception e) {
            ExceptionHandler.logException(e);
        }
        stage.setOnCloseRequest(new EventHandler<WindowEvent>() {
            @Override
            public void handle(WindowEvent event) {
                try {
                    getCommunicator().logout(getModel().getUserName());
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        });
    }

    public void switchToLogInGui(){
        try {
            Stage stage1 = new Stage();
            start(stage1);
        } catch (Exception e){
            ExceptionHandler.logException(e);
        }

    }

    @Override
    public void setUserList( Vector<String> userList ) {
        final List<String> users = new ArrayList<>();
        for (String anUserList : userList) {
            if (anUserList.equals(getModel().getUserName())) {
                users.add("*" + anUserList + "*");
            } else {
                users.add(anUserList);
            }
            Platform.runLater(new Runnable() {
                @Override
                public void run() {
                    getModel().users.setAll(users);
                    log.debug(users);
                }
            });
        }
    }

    @Override
    public void setMessageLine( String sender, String message ) {
        String messageText;
        if (sender.equals(getModel().getUserName())) {
            messageText = "*" + sender + "*: " + message + "\t\t\u2713";
        } else {
            messageText = sender + ": " + message;
        }
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                getModel().chats.add(messageText);
            }
        });
    }

    @Override
    public void setMessageLineAdvanced(String sender, String message) {
        String messageText;
        if (sender.equals(getModel().getUserName())) {
            messageText = "*" + sender + "*: " + message + "\t\t\u2713";
        } else {
            messageText = sender + ": " + message;
            //play sound
            MediaPlayer mediaPlayer = new MediaPlayer(new Media(new File("./src/main/java/edu/hm/dako/chat/client/newmessage.mp3").toURI().toString()));
            mediaPlayer.play();
        }
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                getModel().chats.add(messageText);
            }
        });
    }

    @Override
    public void setSpecificLineAsMarked( String message ) {
        //System.out.println("\n--------------------   DEBUG   --------------------\n\n" + message + "\n\n--------------------   DEBUG   --------------------\n");
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                for (int i = getModel().chats.size()-1; i >= 0 ; i--) {
                    if (getModel().chats.get(i).toString().equals("*" + getModel().getUserName() + "*: " + message + "\t\t\u2713")) {
                        getModel().chats.set(i, getModel().chats.get(i) + "\u2713");
                        break;
                    }
                }
            }
        });
    }

    @Override
    public void setLock(boolean lock) {
        getModel().block.set(lock);
    }

    @Override
    public boolean getLock() {
        return false;
    }

    @Override
    public boolean isTestAborted() {
        return false;
    }

    @Override
    public void abortTest() {
    }

    @Override
    public void releaseTest() {
    }

    @Override
    public boolean isRunning() {
        return false;
    }

    @Override
    public void setLastServerTime(long lastServerTime) {
    }

    @Override
    public long getLastServerTime() {
        return 0;
    }

    @Override
    public void setSessionStatisticsCounter(long numberOfSentEvents,
                                            long numberOfReceivedConfirms, long numberOfLostConfirms, long numberOfRetries,
                                            long numberOfReceivedChatMessages) {

    }

    @Override
    public long getNumberOfSentEvents() {
        return 0;
    }

    @Override
    public long getNumberOfReceivedConfirms() {
        return 0;
    }

    @Override
    public long getNumberOfLostConfirms() {
        return 0;
    }

    @Override
    public long getNumberOfRetries() {
        return 0;
    }

    @Override
    public long getNumberOfReceivedChatMessages() {
        return 0;
    }

    @Override
    public void setErrorMessage(String sender, String errorMessage, long errorCode) {
        log.debug("errorMessage: " + errorMessage);
        Platform.runLater(new Runnable() {
            @Override
            public void run() {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle("Es ist ein Fehler im " + sender + " aufgetreten");
                alert.setHeaderText("Fehlerbehandlung (Fehlercode = " + errorCode + ")");
                alert.setContentText(errorMessage);
                alert.showAndWait();
            }
        });
    }

    @Override
    public void loginCompleteNew() {
        log.debug("Login erfolreich");
        colourCode = comboChooseColour.getValue();
        colourCode = ColourTypes.getHexCode(colourCode);
        log.debug("Farbecode: " + colourCode);
        createNextGui(colourCode);
    }

    @Override
    public void logoutCompleteNew() {
        log.debug("Abmeldung durchgefuehrt");
        log.debug("Logout-Vorgang ist nun beendet");
        System.exit(0);
    }

    /**
     * Pruefen, ob IP-Adresse korrekt ist
     *
     * @return true - korrekt, false - nicht korrekt
     */
    private Boolean ipCorrect() {
        String testString = txtServername.getText();
        if (testString.equals("localhost")) {
            return true;
        } else if (IPV6_PATTERN.matcher(testString).matches()) {
            return true;
        } else if (IPV4_PATTERN.matcher(testString).matches()) {
            return true;
        } else {
            return false;
        }
    }

    @Override
    public void loginComplete() {
    }

    @Override
    public void logoutComplete() {
    }
}
