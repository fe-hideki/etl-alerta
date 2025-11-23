package sptech.school;

public class Alerta {
    private String dtHora;
    private Double valorColetado;
    private String componente;
    private String gravidade;
    private String macAdress;
    private String identificacaoMainframe;

    public Alerta(String dtHora, Double valorColetado, String componente, String gravidade, String macAdress, String identificacaoMainframe) {
        this.dtHora = dtHora;
        this.valorColetado = valorColetado;
        this.componente = componente;
        this.gravidade = gravidade;
        this.macAdress = macAdress;
        this.identificacaoMainframe = identificacaoMainframe;
    }

    //
    @Override
    public String toString() {
        return String.format("%s;%.2f;%s;%s;%s;%s",
                dtHora,
                valorColetado,
                componente,
                gravidade,
                macAdress,
                identificacaoMainframe
        );
    }

    public String getDtHora() {
        return dtHora;
    }

    public void setDtHora(String dtHora) {
        this.dtHora = dtHora;
    }

    public Double getValorColetado() {
        return valorColetado;
    }

    public void setValorColetado(Double valorColetado) {
        this.valorColetado = valorColetado;
    }

    public String getComponente() {
        return componente;
    }

    public void setComponente(String componente) {
        this.componente = componente;
    }

    public String getGravidade() {
        return gravidade;
    }

    public void setGravidade(String gravidade) {
        this.gravidade = gravidade;
    }

    public String getMacAdress() {
        return macAdress;
    }

    public void setMacAdress(String macAdress) {
        this.macAdress = macAdress;
    }

    public String getIdentificacaoMainframe() {
        return identificacaoMainframe;
    }

    public void setIdentificacaoMainframe(String identificacaoMainframe) {
        this.identificacaoMainframe = identificacaoMainframe;
    }
}
