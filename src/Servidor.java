import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Servidor {
    public static final int NUMERO_SERVIDORES_EXCETO_LIDER = 2;

    public static final String SERVER_IP_DEFAULT_LOCAL = "127.0.0.1";
    private String ip;
    private int porta;
    private String ipLider;
    private int portaLider;
    private Boolean ehLider;

    private Map<String, Socket> clientes;  // Mapa para armazenar os sockets dos clientes
    private Map<String, Mensagem> tabela = new HashMap<>();

    public Servidor(String ip, int porta, String ipLider, int portaLider) {
        this.ip = ip;
        this.porta = porta;
        this.ipLider = ipLider;
        this.portaLider = portaLider;
        this.ehLider = false;
    }

    public Boolean getEhLider() {
        return ehLider;
    }

    public void iniciar() {
        try {
            ServerSocket servidorSocket = new ServerSocket(porta);
            // Criar pool de threads para varias conexoes
            ExecutorService executorService = Executors.newCachedThreadPool();

            // Espera conexao dos clientes
            while (true) {
                Socket clienteSocket = servidorSocket.accept();
                executorService.execute(new ClienteHandler(clienteSocket, this));
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        // Pega informaçoes do teclado
        System.out.println("Digite o IP do servidor (Enter para default - 127.0.0.1: ");
        String serverIP = scanner.nextLine().trim();
        if (serverIP.isEmpty()) {
            serverIP = SERVER_IP_DEFAULT_LOCAL;
        }

        int serverPorta = inserirPortaServidor(scanner, "");

        System.out.println("Digite o IP do servidor Líder (Enter para default - 127.0.0.1: ");
        String serverIpLider = scanner.nextLine().trim();
        if (serverIpLider.isEmpty()) {
            serverIpLider = SERVER_IP_DEFAULT_LOCAL;
        }

        int portaLider = inserirPortaServidor(scanner, " líder");

        // Inicializa o servidor
        Servidor servidor = new Servidor(serverIP, serverPorta, serverIpLider, portaLider);
        servidor.iniciar();

    }

    private void inserirMensagem(String key, String value, long timestamp, String remetente) {
        tabela.put(key, new Mensagem("PUT",key, value, timestamp, remetente));
    }

    private void replicarRegistro(String key, String value, long timestamp) {
        //TODO replicar a mensaggem
    }

    public synchronized void registrarCliente(String remetente, Socket clienteSocket) {
        clientes.put(remetente, clienteSocket);
    }

    private void encaminharParaLider(Mensagem mensagem) {
        try {
            Socket liderSocket = new Socket(ipLider, portaLider);
            ObjectOutputStream out = new ObjectOutputStream(liderSocket.getOutputStream());
            out.writeObject(mensagem);
            liderSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void encaminharMensagemOKParaLider() {
        try {
            Socket liderSocket = new Socket(ipLider, portaLider);
            ObjectOutputStream out = new ObjectOutputStream(liderSocket.getOutputStream());
            out.writeObject(new Mensagem("REPLICATION_OK", null, null, System.currentTimeMillis(), montarEnderecoServidor()));
            liderSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private String montarEnderecoServidor() {
        return ip + ":" + porta;
    }

    public synchronized void processarReplicationOK(Mensagem mensagem) {
        // Verifica se ja nao foi contado que ele recebeu essa mensagem desse mesmo remetente, se nao, adicionar como remetente
        if (!mensagem.getReplicationStatus().contains(mensagem.getRemetente())) {
            mensagem.getReplicationStatus().add(mensagem.getRemetente());
        }

        // Verificar se todas as réplicas foram bem-sucedidas
        if (mensagem.getReplicationStatus().size() == NUMERO_SERVIDORES_EXCETO_LIDER) {
            // Obter o IP e a porta do remetente
            String remetente = mensagem.getRemetente();

            // Enviar a mensagem PUT_OK para o cliente remetente
            Socket clienteSocket = clientes.get(remetente);
            if (clienteSocket != null) {
                Mensagem mensagemPutOk = new Mensagem("PUT_OK", mensagem.getChave(), null, mensagem.getTimestamp(), montarEnderecoServidor());
                try {
                    ObjectOutputStream out = new ObjectOutputStream(clienteSocket.getOutputStream());
                    out.writeObject(mensagemPutOk);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }


    private static int inserirPortaServidor(Scanner scanner, String Lider) {
        int porta;
        while (true) {
            System.out.print("Digite a porta do servidor" + Lider + " :");
            porta = Integer.parseInt(scanner.nextLine());
            if (!portaEstaEmUso(porta)) {
                break;
            }
            System.out.println("A porta " + porta + " já está em uso. Escolha outra porta.");
        }
        return porta;
    }

    /**
     * Verifica se a porta do servidor já está em uso através do Socket
     * @param porta a porta a ser verificada
     * @return true se está em uso e der erro, false caso contrário
     */
    private static boolean portaEstaEmUso(int porta) {
        try {
            ServerSocket socket = new ServerSocket(porta);
            socket.close();
            return false;
        } catch (IOException e) {
            return true;
        }
    }

    public class ClienteHandler implements Runnable {
        private Servidor servidor;
        private Socket clienteSocket;
        public ClienteHandler(Socket clienteSocket, Servidor servidor) {
            this.clienteSocket = clienteSocket;
            this.servidor = servidor;
        }

        @Override
        public void run() {
            try {
                ObjectOutputStream out = new ObjectOutputStream(clienteSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(clienteSocket.getInputStream());

                // Ler a mensagem do cliente
                Mensagem mensagem = (Mensagem) in.readObject();

                String key = mensagem.getChave();
                String value = mensagem.getValor();
                String remetente = mensagem.getRemetente();
                long timestamp = System.currentTimeMillis();

                // Tratar a mensagem de acordo com o método
                if (mensagem.getMetodo().equals("PUT")) {
                    if (servidor.getEhLider()) {
                        inserirMensagem(key, value, timestamp, remetente);
                        replicarRegistro(key, value, timestamp);

                    } else {
                        encaminharParaLider(mensagem);
                    }
                    registrarCliente(remetente, clienteSocket);
                } else if (mensagem.getMetodo().equals("GET")) {
                    // Lidar com a requisição GET
                    // ...
                } else if (mensagem.getMetodo().equals("REPLICATION")) {
                    inserirMensagem(key, value, timestamp, remetente);
                    encaminharMensagemOKParaLider();
                } else if (mensagem.getMetodo().equals("REPLICATION_OK")) {
                    processarReplicationOK(mensagem);
                }

                clienteSocket.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }
    }




}
