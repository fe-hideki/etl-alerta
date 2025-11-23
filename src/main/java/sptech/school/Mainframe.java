package sptech.school;

public class Mainframe {
    private String macAdress;
    private String timestamp;
    private String identificaoMainframe;
    private Double usoCpuTotal;
    private Double usoRamTotal;
    private Double swapRateMbs;
    private Double tempoCpuOciosa;
    private Double cpuIoWait;
    private Double usoDiscoTotal;
    private Double discoIopsTotal;
    private Double discoThroughputMbs;
    private Integer discoReadCount;
    private Integer discoWriteCount;
    private Double discoLatenciaMs;


    public Mainframe(Double discoThroughputMbs) {
        this.discoThroughputMbs = discoThroughputMbs;
    }
    public Mainframe(){

    }

    @Override
    public String toString() {
        return "Mainframe{" +
                "macAdress='" + macAdress + '\'' +
                ", timestamp='" + timestamp + '\'' +
                ", identificaoMainframe='" + identificaoMainframe + '\'' +
                ", usoCpuTotal=" + usoCpuTotal +
                ", usoRamTotal=" + usoRamTotal +
                ", swapRateMbs=" + swapRateMbs +
                ", tempoCpuOciosa=" + tempoCpuOciosa +
                ", cpuIoWait=" + cpuIoWait +
                ", usoDiscoTotal=" + usoDiscoTotal +
                ", discoIopsTotal=" + discoIopsTotal +
                ", discoThroughputMbs=" + discoThroughputMbs +
                ", discoReadCount=" + discoReadCount +
                ", discoWriteCount=" + discoWriteCount +
                ", discoLatenciaMs=" + discoLatenciaMs +
                '}';
    }

    public String getMacAdress() {
        return macAdress;
    }
    public void setMacAdress(String macAdress) {
        this.macAdress = macAdress;
    }
    public String getTimestamp() {
        return timestamp;
    }
    public void setTimestamp(String timestamp) {
        this.timestamp = timestamp;
    }
    public String getIdentificaoMainframe() {
        return identificaoMainframe;
    }
    public void setIdentificaoMainframe(String identificaoMainframe) {
        this.identificaoMainframe = identificaoMainframe;
    }
    public Double getUsoCpuTotal() {
        return usoCpuTotal;
    }
    public void setUsoCpuTotal(Double usoCpuTotal) {
        this.usoCpuTotal = usoCpuTotal;
    }
    public Double getUsoRamTotal() {
        return usoRamTotal;
    }
    public void setUsoRamTotal(Double usoRamTotal) {
        this.usoRamTotal = usoRamTotal;
    }
    public Double getSwapRateMbs() {
        return swapRateMbs;
    }
    public void setSwapRateMbs(Double swapRateMbs) {
        this.swapRateMbs = swapRateMbs;
    }
    public Double getTempoCpuOciosa() {
        return tempoCpuOciosa;
    }
    public void setTempoCpuOciosa(Double tempoCpuOciosa) {
        this.tempoCpuOciosa = tempoCpuOciosa;
    }
    public Double getCpuIoWait() {
        return cpuIoWait;
    }
    public void setCpuIoWait(Double cpuIoWait) {
        this.cpuIoWait = cpuIoWait;
    }
    public Double getUsoDiscoTotal() {
        return usoDiscoTotal;
    }
    public void setUsoDiscoTotal(Double usoDiscoTotal) {
        this.usoDiscoTotal = usoDiscoTotal;
    }
    public Double getDiscoIopsTotal() {
        return discoIopsTotal;
    }
    public void setDiscoIopsTotal(Double discoIopsTotal) {
        this.discoIopsTotal = discoIopsTotal;
    }
    public Double getDiscoThroughputMbs() {
        return discoThroughputMbs;
    }
    public void setDiscoThroughputMbs(Double discoThroughputMbs) {
        this.discoThroughputMbs = discoThroughputMbs;
    }
    public Integer getDiscoReadCount() {
        return discoReadCount;
    }
    public void setDiscoReadCount(Integer discoReadCount) {
        this.discoReadCount = discoReadCount;
    }
    public Integer getDiscoWriteCount() {
        return discoWriteCount;
    }
    public void setDiscoWriteCount(Integer discoWriteCount) {
        this.discoWriteCount = discoWriteCount;
    }
    public Double getDiscoLatenciaMs() {
        return discoLatenciaMs;
    }
    public void setDiscoLatenciaMs(Double discoLatenciaMs) {
        this.discoLatenciaMs = discoLatenciaMs;
    }
}
