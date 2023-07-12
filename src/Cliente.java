import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Scanner;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Cliente {

    private static final int[] SERVER_PORTS_DEFAULT = {10097, 10098, 10099};
    private static final String SERVER_IP_DEFAULT_LOCAL = "127.0.0.1";
    private static List<String> serverIPs = new ArrayList<>();
    private static List<Integer> serverPorts = new ArrayList<>();

    public static final String TRY_OTHER_SERVER_OR_LATER = "TRY_OTHER_SERVER_OR_LATER";

    public void iniciar() {
        preencherInfoServidores();

        while (true) {
            menuInterativo();
        }
    }

    private static void menuInterativo() {

        ExecutorService executorService = Executors.newFixedThreadPool(10);

        Scanner scanner = new Scanner(System.in);
        Random random = new Random();

        System.out.println("\nEscolha uma opção válida:");
        System.out.println("1. Requisição PUT");
        System.out.println("2. Requisição GET");
        System.out.println("3. Sair");

        System.out.print("Escolha uma opção: ");
        int escolha = Integer.parseInt(scanner.nextLine());

        switch (escolha) {
            case 1:
                System.out.print("Digite a chave: ");
                String chavePut = scanner.nextLine();

                System.out.print("Digite o valor: ");
                String valorPut = scanner.nextLine();

                //     int indiceServidorPut = random.nextInt(serverIPs.size());
                int indiceServidorPut = 1;
                enviarPut(chavePut, valorPut, serverIPs.get(indiceServidorPut), serverPorts.get(indiceServidorPut), executorService);
                break;
            case 2:
                System.out.print("Digite a chave: ");
                String chaveGet = scanner.nextLine();

                int indiceServidorGet = random.nextInt(serverIPs.size());
                enviarGet(chaveGet, serverIPs.get(indiceServidorGet), serverPorts.get(indiceServidorGet), executorService);
                break;
            case 3:
                System.exit(0);
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
     * @param chave           para ser enviado
     * @param valor           para ser preenchido
     * @param serverIP        servidor para ser enviado
     * @param serverPorta     server porta para ser enviado
     * @param executorService service para tarefas assincronas
     */
    private static void enviarPut(String chave, String valor, String serverIP, int serverPorta, ExecutorService executorService) {
        // Utiliza a classe CompletableFuture para fazer requisicao assincrona
        CompletableFuture<Mensagem> future = CompletableFuture.supplyAsync(() -> {
            try {
                // Inicia o socket para comunicacao com  o servidor escolhido
                Socket clienteSocket = new Socket(serverIP, serverPorta);
                ObjectOutputStream out = new ObjectOutputStream(clienteSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clienteSocket.getInputStream());

                // Prepara a mensagem de PUT e envia
                Mensagem mensagem = new Mensagem("PUT", chave, valor, 0L, montarRemetenteCliente(clienteSocket));
                out.writeObject(mensagem);

                Mensagem resposta = (Mensagem) in.readObject();
                System.out.println(resposta);


                clienteSocket.close();
                return null;
            } catch (IOException e) {
                e.printStackTrace();
                return null;
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }, executorService);
    }

    private static String montarRemetenteCliente(Socket clienteSocket) {
        return clienteSocket.getInetAddress().getHostAddress() + ":" + clienteSocket.getPort();
    }

    /**
     * Método para enviar o GET para o Servidor
     *
     * @param chave           chave a ser enviado para procura
     * @param serverIP        ip do server para ser enviado
     * @param serverPorta     a porta do server
     * @param executorService service das threads para paralelização
     */
    private static void enviarGet(String chave, String serverIP, int serverPorta, ExecutorService executorService) {
        // Utiliza a classe CompletableFuture para fazer requisicao assincrona
        CompletableFuture<Mensagem> future = CompletableFuture.supplyAsync(() -> {
            try {
                // Inicia o socket para comunicacao com  o servidor escolhido
                Socket clienteSocket = new Socket(serverIP, serverPorta);
                ObjectOutputStream out = new ObjectOutputStream(clienteSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clienteSocket.getInputStream());

                // Prepara a mensagem de GET e envia
                Mensagem mensagem = new Mensagem("GET", chave, null, 0L, montarRemetenteCliente(clienteSocket));
                out.writeObject(mensagem);

                // Verifica a resposta recebida se for GET_OK printar que foi recebido com sucesso com o valor da chave
                Mensagem resposta = (Mensagem) in.readObject();
                if (resposta.getMetodo().equals("GET_RESPONSE")) {
                    if (resposta.getValor() != null) {
                        System.out.println("Valor encontrado: " + resposta.getValor() + ", Timestamp: " + resposta.getTimestamp());
                    } else if (resposta.getValor() == null) {
                        System.out.println("Chave " + chave + " não foi encontrada");
                    } else if (resposta.getValor().equals(TRY_OTHER_SERVER_OR_LATER)) {
                        System.out.println("Tente outro server ou mais tarde");
                    }
                }

                // Fecha o socket e retorna a resposta
                clienteSocket.close();
                return resposta;
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
                return null;
            }
        }, executorService);
        // bloquear a execução até que o resultado esteja disponível.
        future.join();
    }

    public static void main(String[] args) {
        Cliente cliente = new Cliente();
        cliente.iniciar();
    }


}
