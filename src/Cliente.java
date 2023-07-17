import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.*;

public class Cliente {

    private static final int[] SERVER_PORTS_DEFAULT = {10097, 10098, 10099};
    private static final String SERVER_IP_DEFAULT_LOCAL = "127.0.0.1";
    private static List<String> serverIPs = new ArrayList<>();
    private static List<Integer> serverPorts = new ArrayList<>();

    private static Map<String, Long> ultimoTimestamps = new HashMap<>();
    public static final String TRY_OTHER_SERVER_OR_LATER = "TRY_OTHER_SERVER_OR_LATER";

    public void iniciar() {
        preencherInfoServidores();
        Thread thread1 = new Thread();
        Thread thread2 = new Thread();
        while (true) {
            menuInterativo(thread1, thread2);
        }
    }

    private static void menuInterativo(Thread thread1, Thread thread2) {

        Scanner scanner = new Scanner(System.in);
        Random random = new Random();

        while (true) {
            System.out.println("\nEscolha uma opção válida:");
            System.out.println("1. Requisição PUT");
            System.out.println("2. Requisição GET");
            System.out.println("3. Sair");

            System.out.print("Escolha uma opção: ");
            int escolha;
            try {
                escolha = Integer.parseInt(scanner.nextLine());
            } catch (NumberFormatException e) {
                System.out.println("Digite um número válido.");
                continue;
            }
            switch (escolha) {
                case 1:
                    System.out.print("Digite a chave: ");
                    String chavePut = scanner.nextLine();

                    System.out.print("Digite o valor: ");
                    String valorPut = scanner.nextLine();

                    int indiceServidorPut = random.nextInt(serverIPs.size());
                    thread1 = new Thread(() -> enviarPut(chavePut, valorPut, serverIPs.get(indiceServidorPut), serverPorts.get(indiceServidorPut)));
                    thread1.start();
                    break;
                case 2:
                    System.out.print("Digite a chave: ");
                    String chaveGet = scanner.nextLine();

                    int indiceServidorGet = random.nextInt(serverIPs.size());
                    thread2 = new Thread(() -> enviarGet(chaveGet, serverIPs.get(indiceServidorGet), serverPorts.get(indiceServidorGet)));
                    thread2.start();
                    break;
                case 3:
                    System.exit(0);
                default:
                    System.out.println("Opção inválida. Digite um número válido.");
            }
        }
    }


    private void preencherInfoServidores() {
        Scanner scanner = new Scanner(System.in);

        for (int i = 0; i < SERVER_PORTS_DEFAULT.length; i++) {
            System.out.print("Digite o IP do " + (i + 1) + "° servidor (Enter para default - 127.0.0.1): ");
            String serverIP = scanner.nextLine().trim();
            if (serverIP.isEmpty() || serverIP == null) {
                serverIP = SERVER_IP_DEFAULT_LOCAL;
            }
            serverIPs.add(serverIP);
            serverPorts.add(SERVER_PORTS_DEFAULT[i]);
        }
    }


    /**
     * Método para enviar o PUT para o servidor assincronamente
     *
     * @param chave       para ser enviado
     * @param valor       para ser preenchido
     * @param serverIP    servidor para ser enviado
     * @param serverPorta server porta para ser enviado
     */
    private static void enviarPut(String chave, String valor, String serverIP, int serverPorta) {

        try {
            // Inicia o socket para comunicacao com  o servidor escolhido
            Socket clienteSocket = new Socket(serverIP, serverPorta);
            ObjectOutputStream out = new ObjectOutputStream(clienteSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clienteSocket.getInputStream());

            // Prepara a mensagem de PUT e envia
            Mensagem mensagem = new Mensagem("PUT", chave, valor, 0L, montarRemetenteCliente(clienteSocket));
            out.writeObject(mensagem);

            // A mensagem que o servidor vai voltar
            Mensagem resposta = (Mensagem) in.readObject();
            if (resposta.getMetodo().equals("PUT_OK")) {
                System.out.println("PUT_OK key: " + resposta.getChave() + "value " + resposta.getValor() + " timestamp " + resposta.getTimestamp()
                        + " realizada no servidor [" + resposta.getRemetente().split(":")[0] + ":" + resposta.getRemetente().split(":")[1] + "]");
                ultimoTimestamps.put(chave, resposta.getTimestamp());
            } else {
                System.out.println("OCORREU UM ERRO AO FAZER O PUT");
            }

        } catch (IOException e) {
            e.printStackTrace();
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

    }

    private static String montarRemetenteCliente(Socket clienteSocket) {
        return clienteSocket.getInetAddress().getHostAddress() + ":" + clienteSocket.getPort();
    }

    /**
     * Método para enviar o GET para o Servidor
     *
     * @param chave       chave a ser enviado para procura
     * @param serverIP    ip do server para ser enviado
     * @param serverPorta a porta do server
     */
    private static void enviarGet(String chave, String serverIP, int serverPorta) {
        try {
            // Inicia o socket para comunicacao com  o servidor escolhido
            Socket clienteSocket = new Socket(serverIP, serverPorta);
            ObjectOutputStream out = new ObjectOutputStream(clienteSocket.getOutputStream());
            ObjectInputStream in = new ObjectInputStream(clienteSocket.getInputStream());

            // Obter o último timestamp associado à chave (se existir no mapa)
            long ultimoTimestamp = ultimoTimestamps.getOrDefault(chave, 0L);

            // Prepara a mensagem de GET e envia
            Mensagem mensagem = new Mensagem("GET", chave, null, ultimoTimestamp, montarRemetenteCliente(clienteSocket));
            out.writeObject(mensagem);

            // Verifica a resposta recebida se for GET_OK printar que foi recebido com sucesso com o valor da chave, ou se não achou o valor da chave
            Mensagem resposta = (Mensagem) in.readObject();
            if (resposta.getMetodo().equals("GET_RESPONSE")) {
                if (resposta.getValor() != null) {
                    String output = "GET key: " + chave +
                            " value: " + resposta.getValor() +
                            " obtido do servidor " + serverIP + ":" + serverPorta +
                            ", meu timestamp " + ultimoTimestamp +
                            " e do servidor " + resposta.getTimestamp();
                    System.out.println(output);
                    ultimoTimestamps.put(resposta.getChave(), resposta.getTimestamp());
                } else if (resposta.getValor() == null) {
                    System.out.println("Chave " + chave + " não foi encontrada");
                }
            }

            // Fecha o socket e retorna a resposta
            clienteSocket.close();
        } catch (IOException | ClassNotFoundException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Cliente cliente = new Cliente();
        cliente.iniciar();
    }

}
