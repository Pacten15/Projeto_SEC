package pt.ulisboa.tecnico.hdsledger.client;

import pt.ulisboa.tecnico.hdsledger.communication.AppendMessage;
import pt.ulisboa.tecnico.hdsledger.communication.Link;
import pt.ulisboa.tecnico.hdsledger.communication.Message;
import pt.ulisboa.tecnico.hdsledger.utilities.CustomLogger;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfig;
import pt.ulisboa.tecnico.hdsledger.utilities.ProcessConfigBuilder;

import java.text.MessageFormat;
import java.util.Arrays;
import java.util.Scanner;
import java.util.logging.Level;

public class Node {

    private static final CustomLogger LOGGER = new CustomLogger(Node.class.getName());

    public static void main(String[] args) {

        try {
            String nodesConfigPath = "src/main/resources/regular_config.json";
            ProcessConfig[] nodeConfigs = new ProcessConfigBuilder().fromFile(nodesConfigPath);
            ProcessConfig clientConfig = Arrays.stream(nodeConfigs).filter(c -> c.getId().equals("69")).findAny().get();
            
            Scanner scanner = new Scanner(System.in);
            System.out.print("Enter a string: ");
            String input = scanner.nextLine();
            System.out.println("You entered: " + input);
            scanner.close();

            AppendMessage message = new AppendMessage();
            message.setMessage(input);
            message.setMessageId(69);
            
            Link l = new Link(clientConfig, 0, new ProcessConfig[0], AppendMessage.class);

            l.send_to_server(message);


        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
