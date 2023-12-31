import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class Servidor {

    public static final String SERVER_IP_DEFAULT_LOCAL = "127.0.0.1";
    public static final String TRY_OTHER_SERVER_OR_LATER = "TRY_OTHER_SERVER_OR_LATER";
    private String ip;
    private int porta;
    private String ipLider;
    private int portaLider;
    private Boolean ehLider;
    private List<Integer> portasServidores;
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
            // Cria um novo socket para escutar requisicoes do cliente
            ServerSocket servidorSocket = new ServerSocket(porta);

            // Espera conexao dos clientes
            while (true) {
                // Caso tenha uma nova requisicao é aberta thread para tratá-la
                Socket clienteSocket = servidorSocket.accept();
                ClienteHandler clienteHandler = new ClienteHandler(clienteSocket, this);
                Thread thread1 = new Thread(clienteHandler);
                thread1.start();

            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    private String montarEnderecoServidor() {
        return ip + ":" + porta;
    }

    /**
     * Método para inserir porta do servidor - Verifica se a porta digitada já está em uso
     *
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
                            System.out.println("Cliente [" + montarClienteEndereco(clienteSocket) + "] PUT key:[" + key + "] value:[" + value + "].");
                            boolean replicouParaTodos = replicarRegistro(key, value, timestamp, remetente);
                            mandarMensagemPutOK(out, key, value, timestamp, replicouParaTodos);
                        } else {
                            encaminharParaLider(mensagem, out);
                        }
                        break;
                    case "GET":
                        requisicaoGET(out, key, value, mensagem.getTimestamp(), clienteSocket);
                        break;
                    case "REPLICATION":
                        inserirMensagem(key, value, mensagem.getTimestamp(), remetente);
                        System.out.println("REPLICATION key:[" + key + "] value:[" + value + "] ts:["+mensagem.getTimestamp()+"].");
                        Mensagem mensagemOK = new Mensagem("REPLICATION_OK", key, value,
                                System.currentTimeMillis(), montarEnderecoServidor());
                        out.writeObject(mensagemOK);
                        break;
                }

                clienteSocket.close();

            } catch (IOException | ClassNotFoundException e) {
                e.printStackTrace();
            }
        }

        private void mandarMensagemPutOK(ObjectOutputStream out, String key, String value, long timestamp, boolean replicouParaTodos) throws IOException {
            if (replicouParaTodos) {
                Mensagem mensagemEnviada = new Mensagem("PUT_OK", key, value, timestamp, montarEnderecoServidor());
                out.writeObject(mensagemEnviada);
            }
        }

        private String montarClienteEndereco(Socket clienteSocket) {
            return clienteSocket.getInetAddress().getHostAddress() + ":" + clienteSocket.getPort();
        }


        private void requisicaoGET(ObjectOutputStream out, String key, String value, long timestamp, Socket socket) throws IOException {
            Long valueTimestampServidor = -1L;

            // Se contém a chave na tabela faz validações
            if (servidor.tabela.containsKey(key)) {
                Mensagem mensagemArmazenada = servidor.tabela.get(key);
                valueTimestampServidor = mensagemArmazenada.getTimestamp();

                // Se o timestamp do servidor associado a mensagem for maior que o timestamp do cliente, devolve a chave
                // Caso contrário devolve TRY_OTHER_SERVER_OR_LATER
                if (valueTimestampServidor >= timestamp) {
                    value = mensagemArmazenada.getValor();
                } else {
                    value = TRY_OTHER_SERVER_OR_LATER;
                }
            } else {
                value = null;
            }

            setarMensagemValue(value);
            // Imprimir a mensagem para o console do servidor
            System.out.println("Cliente [" +montarClienteEndereco(clienteSocket)+ "] GET key:[" + key + "] ts:[" + timestamp +
                    "]. Meu ts é [" + valueTimestampServidor + "], portanto devolvendo " + "valor: " + value + ".");

            // Enviar resposta para o cliente
            Mensagem resposta = new Mensagem("GET_RESPONSE", key, value, valueTimestampServidor, servidor.montarEnderecoServidor());
            out.writeObject(resposta);
        }

        private void setarMensagemValue(String value) {
            if (value == null) {
                value = "CHAVE NAO ENCONTRADA";
            }
        }


        private void inserirMensagem(String key, String value, long timestamp, String remetente) {
            tabela.put(key, new Mensagem("PUT", key, value, timestamp, remetente));
        }

        /**
         * Replica a mensagem para os outros servidores
         * @param key a chave
         * @param value o valor
         * @param timestamp o timestamp
         * @param remetente o remetente
         * @return true se replicou para todos, false caso contrário
         */
        private boolean replicarRegistro(String key, String value, long timestamp, String remetente) {
            Mensagem mensagemReplicacao = new Mensagem("REPLICATION", key, value, timestamp, remetente);
            // Para cada porta do servidor da lista, replica a mensagem para eles inserirem na tabela deles
            for (int portaServidor : portasServidores) {
                try {
                    Socket servidorSocket = new Socket(SERVER_IP_DEFAULT_LOCAL, portaServidor);

                    ObjectOutputStream out = new ObjectOutputStream(servidorSocket.getOutputStream());
                    ObjectInputStream in = new ObjectInputStream(servidorSocket.getInputStream());

                    // Aguarda 5 segundos antes de enviar a mensagem
                    Thread.sleep(10000);

                    out.writeObject(mensagemReplicacao);
                    Mensagem mensagem = (Mensagem) in.readObject();

                    if (mensagem == null || !mensagem.getMetodo().equals("REPLICATION_OK")) {
                        return false;
                    }
                    servidorSocket.close();
                } catch (Exception e) {
                    e.printStackTrace();
                    return false;
                }
            }
            return true;
        }


        private void encaminharParaLider(Mensagem mensagem, ObjectOutputStream outCliente) {
            try {
                // Abre o socket para mandar mensagem para o lider
                Socket liderSocket = new Socket(ipLider, portaLider);
                ObjectOutputStream out = new ObjectOutputStream(liderSocket.getOutputStream());
                ObjectInputStream in = new ObjectInputStream(liderSocket.getInputStream());
                // Manda a mensagem para o líder
                out.writeObject(mensagem);
                System.out.println("Encaminhando PUT key:[" + mensagem.getChave() + "] value:[" + mensagem.getValor() + "]");
                // Espera a mensagem do líder e o encaminha de volta para o cliente
                Mensagem mensagemVoltaLider = (Mensagem) in.readObject();
                outCliente.writeObject(mensagemVoltaLider);
                liderSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            } catch (ClassNotFoundException e) {
                throw new RuntimeException(e);
            }
        }
    }

    public static void main(String[] args) {
        // Adiciona portas padrão do servidor já conhecidos
        List<Integer> portas = new ArrayList<>();
        portas.add(10097);
        portas.add(10099);

        Scanner scanner = new Scanner(System.in);
        // Pega informaçoes do teclado
        System.out.print("Digite o IP do servidor (Enter para default - 127.0.0.1): ");
        String serverIP = scanner.nextLine().trim();
        if (serverIP.isEmpty()) {
            serverIP = SERVER_IP_DEFAULT_LOCAL;
        }

        // Insere a porta do Servidor
        Integer serverPorta = inserirPortaServidor(scanner);

        // Define o IP do líder
        System.out.print("Digite o IP do servidor Líder (Enter para default - 127.0.0.1): ");
        String serverIpLider = scanner.nextLine().trim();
        if (serverIpLider.isEmpty()) {
            serverIpLider = SERVER_IP_DEFAULT_LOCAL;
        }

        // Define a porta do líder
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
