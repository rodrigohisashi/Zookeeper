import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

public class Mensagem implements Serializable {
    private String metodo;
    private String chave;
    private String valor;
    private Long timestamp;
    private List<String> replicationStatus = new ArrayList<>();
    private String remetente;



    public Mensagem(String metodo, String chave, String valor, Long timestamp, String remetente) {
        this.metodo = metodo;
        this.chave = chave;
        this.valor = valor;
        this.timestamp = timestamp;
        this.remetente = remetente;
    }

    public String getMetodo() {
        return metodo;
    }

    public void setMetodo(String metodo) {
        this.metodo = metodo;
    }

    public String getChave() {
        return chave;
    }

    public void setChave(String chave) {
        this.chave = chave;
    }

    public String getValor() {
        return valor;
    }

    public void setValor(String valor) {
        this.valor = valor;
    }

    public Long getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Long timestamp) {
        this.timestamp = timestamp;
    }

    public String getRemetente() {
        return remetente;
    }

    public void setRemetente(String remetente) {
        this.remetente = remetente;
    }

    public List<String> getReplicationStatus() {
        return replicationStatus;
    }

    public void setReplicationStatus(List<String> replicationStatus) {
        this.replicationStatus = replicationStatus;
    }

}
