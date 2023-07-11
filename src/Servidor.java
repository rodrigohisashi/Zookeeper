import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Servidor {
    public static final int NUMERO_SERVIDORES_EXCETO_LIDER = 2;

    public static final String SERVER_IP_DEFAULT_LOCAL = "127.0.0.1";
    public static final String TRY_OTHER_SERVER_OR_LATER = "TRY_OTHER_SERVER_OR_LATER";
    private String ip;
    private int porta;
    private String ipLider;
    private int portaLider;
    private Boolean ehLider;
    private List<Integer> portasServidores;
    private Map<String, Socket> clientes = new HashMap<>();  // Mapa para armazenar os sockets dos clientes
    private Map<String, Mensagem> tabela = new HashMap<>();

    public Servidor(String ip, int porta, String ipLider, int portaLider, List<Integer> portasServidores) {
        this.ip = ip;
        this.porta = porta;
        this.ipLider = ipLider;
        this.portaLider = portaLider;
        this.ehLider = false;
        this.portasServidores = portasServidores;
    }

    public Boolean getEhLider() {
        return ehLider;
    }

    public void setEhLider(Boolean ehLider) {
        this.ehLider = ehLider;
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


    private synchronized void inserirMensagem(String key, String value, long timestamp, String remetente) {
        tabela.put(key, new Mensagem("PUT", key, value, timestamp, remetente));
    }

    private synchronized void replicarRegistro(String key, String value, long timestamp) {
        Mensagem mensagemReplicacao = new Mensagem("REPLICATION", key, value, timestamp, montarEnderecoServidor());

        for (int portaServidor : portasServidores) {
            try {
                Socket servidorSocket = new Socket(SERVER_IP_DEFAULT_LOCAL, portaServidor);
                ObjectOutputStream out = new ObjectOutputStream(servidorSocket.getOutputStream());
                out.writeObject(mensagemReplicacao);
                servidorSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public synchronized void registrarCliente(String remetente, Socket clienteSocket) {
        clientes.put(remetente, clienteSocket);
    }

    private synchronized void encaminharParaLider(Mensagem mensagem) {
        try {
            Socket liderSocket = new Socket(ipLider, portaLider);
            ObjectOutputStream out = new ObjectOutputStream(liderSocket.getOutputStream());
            out.writeObject(mensagem);
            liderSocket.close();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String montarEnderecoServidor() {
        return ip + ":" + porta;
    }

    public synchronized void processarReplicationOK(Mensagem mensagem) {
        // Verificar se ja nao foi contado que ele recebeu essa mensagem desse mesmo remetente, se nao, adicionar como remetente
        if (!mensagem.getReplicationStatus().contains(mensagem.getRemetente())) {
            mensagem.getReplicationStatus().add(mensagem.getRemetente());
        }

        // Verificar se todas as mensagens de replicaçao foram para todos os servidores da lista
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

    /**
     * Método para inserir porta do servidor - Verifica se a porta digitada já está em uso
     * @param scanner scanner para pegar teclado
     * @return a porta do servidor criado
     */

    private static int inserirPortaServidor(Scanner scanner) {
        int porta;
        while (true) {
            System.out.print("Digite a porta do servidor: ");
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
     *
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
                switch (mensagem.getMetodo()) {
                    case "PUT":
                        if (servidor.getEhLider()) {
                            inserirMensagem(key, value, timestamp, remetente);
                            replicarRegistro(key, value, timestamp);
                            registrarCliente(remetente, clienteSocket);
                        } else {
                            encaminharParaLider(mensagem);
                        }
                        break;
                    case "GET":
                        requisicaoGET(out, key, value, timestamp);
                        break;
                    case "REPLICATION":
                        inserirMensagem(key, value, timestamp, remetente);
                        Mensagem mensagemOK = new Mensagem("REPLICATION_OK", null, null, System.currentTimeMillis(), montarEnderecoServidor());
                        encaminharParaLider(mensagemOK);
                        break;
                    case "REPLICATION_OK":
                        processarReplicationOK(mensagem);
                        break;
                }

                clienteSocket.close();
            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        private void requisicaoGET(ObjectOutputStream out, String key, String value, long timestamp) throws IOException {
            Long valueTimestamp = null;
            if (servidor.tabela.containsKey(key)) {
                Mensagem mensagemArmazenada = servidor.tabela.get(key);
                valueTimestamp = mensagemArmazenada.getTimestamp();

                if (valueTimestamp >= timestamp) {
                    value = mensagemArmazenada.getValor();
                } else {
                    value = TRY_OTHER_SERVER_OR_LATER;
                }
            }
            // Enviar resposta para o cliente
            Mensagem resposta = new Mensagem("GET_RESPONSE", key, value, valueTimestamp, servidor.montarEnderecoServidor());
            out.writeObject(resposta);
        }
    }

    public static void main(String[] args) {
        List<Integer> portas = new ArrayList<>();
        portas.add(10097);
        portas.add(10098);
        portas.add(10099);

        Scanner scanner = new Scanner(System.in);
        // Pega informaçoes do teclado
        System.out.print("Digite o IP do servidor (Enter para default - 127.0.0.1): ");
        String serverIP = scanner.nextLine().trim();
        if (serverIP.isEmpty()) {
            serverIP = SERVER_IP_DEFAULT_LOCAL;
        }

        Integer serverPorta = inserirPortaServidor(scanner);

        System.out.print("Digite o IP do servidor Líder (Enter para default - 127.0.0.1): ");
        String serverIpLider = scanner.nextLine().trim();
        if (serverIpLider.isEmpty()) {
            serverIpLider = SERVER_IP_DEFAULT_LOCAL;
        }

        System.out.print("Digite a porta do servidor Líder: ");
        int portaLider = Integer.parseInt(scanner.nextLine());

        // Inicializa o servidor
        Servidor servidor = new Servidor(serverIP, serverPorta, serverIpLider, portaLider, portas);

        // Se o server IP e a porta do server foram iguais do lider significa que ele é o servidor líder
        if (serverIP.equals(serverIpLider) && serverPorta.equals(portaLider)) {
            servidor.setEhLider(Boolean.TRUE);
        }
        servidor.iniciar();

    }


}
