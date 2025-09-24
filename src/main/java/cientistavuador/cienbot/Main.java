package cientistavuador.cienbot;

import cientistavuador.cienbot.ai.CienBot;
import cientistavuador.cienbot.storage.Packet;
import cientistavuador.cienbot.storage.PacketCipherFileStream;
import cientistavuador.cienbot.storage.PacketID;
import cientistavuador.cienbot.ui.LogWindow;
import cientistavuador.cienbot.ui.LoginWindow;
import com.formdev.flatlaf.FlatDarkLaf;
import java.awt.AWTException;
import java.awt.CheckboxMenuItem;
import java.awt.Dimension;
import java.awt.Image;
import java.awt.Menu;
import java.awt.MenuItem;
import java.awt.PopupMenu;
import java.awt.SystemTray;
import java.awt.Toolkit;
import java.awt.TrayIcon;
import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.Logger;
import javax.swing.ImageIcon;
import javax.swing.JFileChooser;
import javax.swing.JOptionPane;
import javax.swing.SwingUtilities;
import javax.swing.filechooser.FileNameExtensionFilter;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.channel.concrete.TextChannel;
import net.dv8tion.jda.api.events.GenericEvent;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.EventListener;
import net.dv8tion.jda.api.requests.GatewayIntent;

/**
 *
 * @author Cien
 */
public class Main implements EventListener {

    public static final ImageIcon CIENBOT_ICON = new ImageIcon(Main.class.getResource("icon.png"));

    public static void main(String[] args) throws Exception {
        FlatDarkLaf.setup();

        Main main = new Main();

        LoginWindow window = new LoginWindow() {
            @Override
            public void onFileStreamCreated(PacketCipherFileStream stream) {
                main.setPacketStream(stream);
                main.start();
            }

            @Override
            public void onPacketRead(Packet p) throws IOException {
                main.onPacket(p);
            }
        };
        window.setVisible(true);
    }

    private final AtomicBoolean saveLogOutput = new AtomicBoolean(false);

    private final LogWindow defaultLog = new LogWindow(System.out);
    private final LogWindow errorLog = new LogWindow(System.err);

    private PacketCipherFileStream packetStream = null;
    private CienBot bot = null;
    private JDA jda = null;

    private final List<String> messages = new ArrayList<>();
    private String botToken = "";
    private int maxContextSize = 3;
    private int maxTokens = 64;
    private long masterUser = 0;
    private long textChannel = 0;
    private boolean startOnOpen = true;

    private TrayIcon trayIcon = null;
    private final MenuItem startItem = new MenuItem("Iniciar");
    private final MenuItem stopItem = new MenuItem("Parar");

    public Main() {

    }

    public PacketCipherFileStream getPacketStream() {
        return packetStream;
    }

    public void setPacketStream(PacketCipherFileStream packetStream) {
        this.packetStream = packetStream;
    }

    public void onPacket(Packet p) {
        switch (p.getId()) {
            case PacketID.SET_BOT_TOKEN -> {
                this.botToken = new String(p.getData(), StandardCharsets.UTF_8);
            }
            case PacketID.SET_MAX_CONTEXT_SIZE -> {
                this.maxContextSize = ByteBuffer.wrap(p.getData()).getInt();
            }
            case PacketID.SET_MAX_TOKENS -> {
                this.maxTokens = ByteBuffer.wrap(p.getData()).getInt();
            }
            case PacketID.SET_MASTER_USER -> {
                this.masterUser = ByteBuffer.wrap(p.getData()).getLong();
            }
            case PacketID.SET_TEXT_CHANNEL -> {
                this.textChannel = ByteBuffer.wrap(p.getData()).getLong();
            }
            case PacketID.SET_START_ON_OPEN -> {
                this.startOnOpen = p.getData()[0] != 0;
            }
            case PacketID.ADD_MESSAGE -> {
                this.messages.add(new String(p.getData(), StandardCharsets.UTF_8));
            }
        }
    }

    private void setupTrayIcon() {
        SystemTray systemTray = SystemTray.getSystemTray();

        Dimension trayIconSize = systemTray.getTrayIconSize();
        Image imageIcon = CIENBOT_ICON.getImage()
                .getScaledInstance(
                        (int) trayIconSize.getWidth(),
                        (int) trayIconSize.getHeight(),
                        Image.SCALE_SMOOTH);

        PopupMenu popup = new PopupMenu();

        CheckboxMenuItem startOnOpenItem = new CheckboxMenuItem("Iniciar Bot ao Abrir", this.startOnOpen);
        Menu editItem = new Menu("Editar");
        Menu logItem = new Menu("Log");
        MenuItem exitItem = new MenuItem("Sair");

        startOnOpenItem.addActionListener((e) -> {
            this.startOnOpen = startOnOpenItem.getState();
            try {
                this.packetStream.writePacket(
                        new Packet(PacketID.SET_START_ON_OPEN,
                                new byte[]{(byte) (this.startOnOpen ? 1 : 0)}));
                this.packetStream.flush();
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }
        });

        this.startItem.addActionListener((e) -> {
            startBot();
        });

        this.stopItem.addActionListener((e) -> {
            stopBot();
        });

        MenuItem tokenItem = new MenuItem("Token do Bot");
        MenuItem masterUserItem = new MenuItem("Usuário Mestre");
        MenuItem textChannelItem = new MenuItem("Definir Canal de Texto");
        MenuItem trainBot = new MenuItem("Treinar Bot com Arquivo de Texto");
        MenuItem maxContextSizeItem = new MenuItem("Tamanho Máximo de Contexto");
        MenuItem maxTokensItem = new MenuItem("Máximo de Tokens por Mensagem");

        tokenItem.addActionListener((e) -> {
            String token = JOptionPane
                    .showInputDialog(null, "Insira o Token do Bot:", "Token do Bot", JOptionPane.INFORMATION_MESSAGE);
            if (token == null) {
                return;
            }
            this.botToken = token;
            try {
                this.packetStream.writePacket(new Packet(PacketID.SET_BOT_TOKEN, token));
                this.packetStream.flush();
            } catch (IOException ex) {
                ex.printStackTrace(System.err);
            }

            stopBot();
        });

        masterUserItem.addActionListener((e) -> {
            String idString = JOptionPane
                    .showInputDialog(null, "Insira o ID do Usuário Mestre:", "Usuário Mestre", JOptionPane.INFORMATION_MESSAGE);
            if (idString == null) {
                return;
            }
            try {
                this.masterUser = Long.parseLong(idString);
                try {
                    this.packetStream.writePacket(
                            new Packet(PacketID.SET_BOT_TOKEN,
                                    ByteBuffer.allocate(8).putLong(this.masterUser).array()));
                    this.packetStream.flush();
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
            } catch (NumberFormatException ex) {
                Toolkit.getDefaultToolkit().beep();
                ex.printStackTrace(System.err);
            }
        });
        
        textChannelItem.addActionListener((e) -> {
            String idString = JOptionPane
                    .showInputDialog(null, "Insira o ID do Canal de Texto:", "Canal de Texto", JOptionPane.INFORMATION_MESSAGE);
            if (idString == null) {
                return;
            }
            try {
                this.textChannel = Long.parseLong(idString);
                try {
                    this.packetStream.writePacket(
                            new Packet(PacketID.SET_TEXT_CHANNEL,
                                    ByteBuffer.allocate(8).putLong(this.textChannel).array()));
                    this.packetStream.flush();
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
            } catch (NumberFormatException ex) {
                Toolkit.getDefaultToolkit().beep();
                ex.printStackTrace(System.err);
            }
        });
        
        trainBot.addActionListener((e) -> {
            JFileChooser chooser = new JFileChooser(new File("").getAbsoluteFile());
            chooser.setFileFilter(new FileNameExtensionFilter("Texto", "txt"));
            chooser.setMultiSelectionEnabled(true);
            chooser.setFileSelectionMode(JFileChooser.FILES_ONLY);
            chooser.setDialogType(JFileChooser.OPEN_DIALOG);
            int result = chooser.showOpenDialog(null);
            if (result == JFileChooser.APPROVE_OPTION) {
                File file = chooser.getSelectedFile();
                if (file != null && file.isFile()) {
                    try {
                        BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(file), StandardCharsets.UTF_8));
                        String line;
                        while ((line = reader.readLine()) != null) {
                            this.bot.teach(line);
                            this.packetStream.writePacket(new Packet(PacketID.ADD_MESSAGE, line));
                        }
                        Toolkit.getDefaultToolkit().beep();
                    } catch (IOException ex) {
                        ex.printStackTrace(System.err);
                    }
                }
            }
        });
        
        maxContextSizeItem.addActionListener((e) -> {
            String sizeString = JOptionPane
                    .showInputDialog(null, "Insira o Tamanho do Contexto em Tokens:", "Tamanho do Contexto", JOptionPane.INFORMATION_MESSAGE);
            if (sizeString == null) {
                return;
            }
            try {
                this.maxContextSize = Integer.parseInt(sizeString);
                try {
                    this.packetStream.writePacket(
                            new Packet(PacketID.SET_MAX_CONTEXT_SIZE,
                                    ByteBuffer.allocate(4).putLong(this.maxContextSize).array()));
                    this.packetStream.flush();
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
            } catch (NumberFormatException ex) {
                Toolkit.getDefaultToolkit().beep();
                ex.printStackTrace(System.err);
            }
            JOptionPane.showConfirmDialog(
                    null,
                    "Reinicie para aplicar a alteração", "Aviso",
                    JOptionPane.OK_OPTION, JOptionPane.WARNING_MESSAGE);
        });
        
        maxTokensItem.addActionListener((e) -> {
            String sizeString = JOptionPane
                    .showInputDialog(null, "Insira o Tamanho Máximo da Mensagem em Tokens:", "Tamanho da Mensagem", JOptionPane.INFORMATION_MESSAGE);
            if (sizeString == null) {
                return;
            }
            try {
                this.maxTokens = Integer.parseInt(sizeString);
                try {
                    this.packetStream.writePacket(
                            new Packet(PacketID.SET_MAX_TOKENS,
                                    ByteBuffer.allocate(4).putLong(this.maxTokens).array()));
                    this.packetStream.flush();
                } catch (IOException ex) {
                    ex.printStackTrace(System.err);
                }
            } catch (NumberFormatException ex) {
                Toolkit.getDefaultToolkit().beep();
                ex.printStackTrace(System.err);
            }
        });

        editItem.add(tokenItem);
        editItem.add(masterUserItem);
        editItem.add(textChannelItem);
        editItem.addSeparator();
        editItem.add(trainBot);
        editItem.add(maxContextSizeItem);
        editItem.add(maxTokensItem);

        MenuItem defaultLogItem = new MenuItem("Log Padrão");
        MenuItem errorLogItem = new MenuItem("Log de Erro");

        defaultLogItem.addActionListener((e) -> {
            this.defaultLog.setVisible(true);
        });

        errorLogItem.addActionListener((e) -> {
            this.errorLog.setVisible(true);
        });

        logItem.add(defaultLogItem);
        logItem.add(errorLogItem);

        exitItem.addActionListener((e) -> {
            System.exit(0);
        });

        popup.add(this.startItem);
        popup.add(this.stopItem);
        popup.addSeparator();
        popup.add(startOnOpenItem);
        popup.addSeparator();
        popup.add(editItem);
        popup.add(logItem);
        popup.addSeparator();
        popup.add(exitItem);

        TrayIcon icon = new TrayIcon(imageIcon, "CienBOT", popup);

        try {
            systemTray.add(icon);
        } catch (AWTException ex) {
            throw new RuntimeException(ex);
        }
        this.trayIcon = icon;
    }

    private void startBot() {
        if (this.jda == null) {
            try {
                JDA j = JDABuilder.createDefault(this.botToken)
                        .enableIntents(GatewayIntent.MESSAGE_CONTENT)
                        .addEventListeners(this)
                        .build();
                j.awaitReady();
                this.jda = j;
                this.startItem.setEnabled(false);
                this.stopItem.setEnabled(true);
            } catch (Throwable ex) {
                System.err.println("Falha ao Iniciar:");
                ex.printStackTrace(System.err);
            }
        }
    }

    private void stopBot() {
        if (this.jda != null) {
            this.jda.shutdown();
            this.jda = null;
            this.startItem.setEnabled(true);
            this.stopItem.setEnabled(false);
        }
    }

    public void start() {
        this.defaultLog.getPrintStream().flush();
        this.errorLog.getPrintStream().flush();
        System.setOut(this.defaultLog.getPrintStream());
        System.setErr(this.errorLog.getPrintStream());
        this.saveLogOutput.set(true);

        CienBot b = new CienBot(this.maxContextSize);
        for (String msg : this.messages) {
            b.teach(msg);
        }
        this.messages.clear();
        this.bot = b;

        setupTrayIcon();

        this.startItem.setEnabled(true);
        this.stopItem.setEnabled(false);

        if (this.startOnOpen) {
            startBot();
        }
    }

    @Override
    public void onEvent(GenericEvent event) {
        if (event instanceof MessageReceivedEvent m) {
            if (m.getChannel() instanceof TextChannel channel) {
                long channelId = m.getChannel().getIdLong();
                
                if (channelId == this.textChannel) {
                    String botMention = this.jda.getSelfUser().getAsMention();
                    String rawMessage = m.getMessage().getContentRaw();
                    String completedMessage = this.bot.generate(
                            rawMessage.substring(botMention.length()),
                            this.maxTokens);
                    if (completedMessage.isEmpty()) {
                        return;
                    }
                    if (rawMessage.startsWith(botMention)) {
                        channel.sendMessage(completedMessage).setAllowedMentions(List.of()).complete();
                    }
                }
            }
        }
    }

}
