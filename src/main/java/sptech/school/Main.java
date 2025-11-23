package sptech.school;

import io.github.cdimascio.dotenv.Dotenv;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.*;

public class Main {

    public static void main(String[] args) {
        List<Mainframe> listaLidoMainframe = new ArrayList<>();
        List<Processo> listaLidoProcesso = new ArrayList<>();

        Dotenv dotenv = Dotenv.load();
        String modoExecucao = dotenv.get("MODO_EXECUCAO", "LOCAL");

        if (modoExecucao.equalsIgnoreCase("AWS")) {
            //Baixa e trata os CSVs do bucket RAW direto da AWS
            List<String[]> dadosMainframe = ConexaoAws.lerArquivoCsvDoRaw("dados-mainframe.csv");
            List<String[]> dadosProcesso = ConexaoAws.lerArquivoCsvDoRaw("processos.csv");

            importarArquivoCSVMaquinaMemoria(dadosMainframe, listaLidoMainframe);
            importarArquivoCSVProcessoMemoria(dadosProcesso, listaLidoProcesso);

            //Gera CSV tratado e envia pro bucket TRUSTED
            String csvTratado = gerarCsvTrusted(listaLidoMainframe, listaLidoProcesso);
            ConexaoAws.enviarCsvTrusted("trusted.csv", csvTratado);

            String csvAlertas = GeradorAlertas.gerarCsvAlertas();
            if (!csvAlertas.isEmpty()) {
                ConexaoAws.enviarCsvClient("alertas.csv", csvAlertas);
            }

            //Valida alertas no Synkro
            validarAlerta(listaLidoMainframe, listaLidoProcesso);

        } else if (modoExecucao.equalsIgnoreCase("LOCAL")) {
            importarArquivoCSVMaquina("dados-mainframe", listaLidoMainframe);
            importarArquivoCSVProcesso("processos", listaLidoProcesso);
            gravarArquivoCSV(listaLidoMainframe, listaLidoProcesso, "trusted");

            validarAlerta(listaLidoMainframe, listaLidoProcesso);

        } else {
            importarArquivoCSVMaquina("dados-mainframe", listaLidoMainframe);
            importarArquivoCSVProcesso("processos", listaLidoProcesso);
            try {
                gravarArquivoCSVSimulado(listaLidoMainframe, listaLidoProcesso, "trusted");
            } catch (ParseException e) {
                throw new RuntimeException(e);
            }

            validarAlerta(listaLidoMainframe, listaLidoProcesso);
        }
    }

    public static void validarAlerta(List<Mainframe> listamainframe, List<Processo> listaprocesso) {
        try (Connection conn = DriverManager.getConnection(
                Dotenv.load().get("DB_URL"),
                Dotenv.load().get("DB_USER"),
                Dotenv.load().get("DB_PASSWORD"))) {
            int countCpu = 0, countRam = 0, countDisco = 0, countSwap = 0;
            int countOc = 0, countWait = 0, countThru = 0, countIops = 0;
            int countRead = 0, countWrite = 0, countLat = 0;

            for (Mainframe mainframe : listamainframe) {
                String data = mainframe.getTimestamp();
                String macAdress = mainframe.getMacAdress();

                double usoDisco = mainframe.getUsoDiscoTotal();
                double usoRam = mainframe.getUsoRamTotal();
                double usoCpu = mainframe.getUsoCpuTotal();
                double cpuOciosa = mainframe.getTempoCpuOciosa();
                double cpuIoWait = mainframe.getCpuIoWait();
                double swapRate = mainframe.getSwapRateMbs();
                double throughput = mainframe.getDiscoThroughputMbs();
                double discIops = mainframe.getDiscoIopsTotal();
                double read = mainframe.getDiscoReadCount().doubleValue();
                double write = mainframe.getDiscoWriteCount().doubleValue();
                double latenciaDisc = mainframe.getDiscoLatenciaMs();


                List<List<Object>> componentes = ConexaoBd.buscarMetricas(conn, macAdress);

                for (List<Object> c : componentes) {
                    int fkcomp = (Integer) c.get(0);
                    Double min = (Double) c.get(1);
                    Double max = (Double) c.get(2);
                    String nomecomponente = (String) c.get(3);
                    Integer qtdIncidencias = 5;
                    Double valor = 0.0;
                    String metrica = "";
                    boolean gerarAlerta = false;

                    switch (fkcomp) {
                        case 1:
                            valor = usoCpu;
                            metrica = "Uso CPU";
                            if (valor < min || valor > max) {
                                countCpu++;
                                if (countCpu >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countCpu = 0;
                                }
                                break;
                            }
                        case 2:
                            valor = usoRam;
                            metrica = "Uso RAM";
                            if (valor < min || valor > max) countRam++;
                        {
                            if (countRam >= qtdIncidencias) {
                                gerarAlerta = true;
                                countRam = 0;
                            }
                            break;
                        }
                        case 3:
                            valor = usoDisco;
                            metrica = "Uso Disco";
                            if (valor < min || valor > max) {
                                countDisco++;
                                if (countDisco >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countDisco = 0;
                                }
                                break;
                            }
                        case 4:
                            valor = swapRate;
                            metrica = "Swap Rate";
                            if (valor < min || valor > max) {
                                countSwap++;
                                if (countSwap >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countSwap = 0;
                                }
                                break;
                            }
                        case 5:
                            valor = cpuOciosa;
                            metrica = "CPU Ociosa";
                            if (valor < min || valor > max) {
                                countOc++;
                                if (countOc >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countOc = 0;
                                }
                                break;
                            }
                        case 6:
                            valor = cpuIoWait;
                            metrica = "CPU IO Wait";
                            if (valor < min || valor > max) {
                                countWait++;
                                if (countWait >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countWait = 0;
                                }
                                break;
                            }
                        case 7:
                            valor = throughput;
                            metrica = "Throughput";
                            if (valor < min || valor > max) {
                                countThru++;
                                if (countThru >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countThru = 0;
                                }
                                break;
                            }
                        case 8:
                            valor = discIops;
                            metrica = "Disco IOps";
                            if (valor < min || valor > max) {
                                countIops++;
                                if (countIops >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countIops = 0;
                                }
                                break;
                            }
                        case 9:
                            valor = read;
                            metrica = "Leitura do Disco";
                            if (valor < min || valor > max) {
                                countRead++;
                                if (countRead >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countRead = 0;
                                }
                                break;
                            }
                        case 10:
                            valor = write;
                            metrica = "Escrita do Disco";
                            if (valor < min || valor > max) {
                                countWrite++;
                                if (countWrite >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countWrite = 0;
                                }
                                break;
                            }
                        case 11:
                            valor = latenciaDisc;
                            metrica = "Latência do Disco";
                            if (valor < min || valor > max) {
                                countLat++;
                                if (countLat >= qtdIncidencias) {
                                    gerarAlerta = true;
                                    countLat = 0;
                                }
                                break;
                            }
                    }

                    if (gerarAlerta) {
                        //System.out.println(" Alerta: Componente " + fkcomp + " fora dos limites");
                        ConexaoBd.inserirAlerta(conn, data, fkcomp, valor, macAdress, nomecomponente, metrica);
                    }
                }
            }

        } catch (SQLException e) {
            System.err.println("Erro ao conectar no banco: " + e.getMessage());
        }
    }

    public static void importarArquivoCSVMaquinaMemoria(List<String[]> dados, List<Mainframe> listaLido) {
        try {
            SimpleDateFormat dtEntrada = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            SimpleDateFormat dtSaida = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

            for (int i = 1; i < dados.size(); i++) {
                String[] registro = dados.get(i);
                Mainframe mainframe = new Mainframe();

                try {
                    mainframe.setMacAdress(registro[0]);
                    Date date = dtEntrada.parse(registro[1]);
                    mainframe.setTimestamp(dtSaida.format(date));

                    mainframe.setIdentificaoMainframe(registro[2]);
                    mainframe.setUsoCpuTotal(Double.valueOf(registro[3].replace(",", ".")));
                    mainframe.setUsoRamTotal(Double.valueOf(registro[4].replace(",", ".")));
                    mainframe.setSwapRateMbs(Double.valueOf(registro[5].replace(",", ".")));
                    mainframe.setTempoCpuOciosa(Double.valueOf(registro[6].replace(",", ".")));
                    mainframe.setCpuIoWait(Double.valueOf(registro[7].replace(",", ".")));
                    mainframe.setUsoDiscoTotal(Double.valueOf(registro[8].replace(",", ".")));
                    mainframe.setDiscoThroughputMbs(Double.valueOf(registro[9].replace(",", ".")));
                    mainframe.setDiscoIopsTotal(Double.valueOf(registro[10].replace(",", ".")));
                    mainframe.setDiscoReadCount(Integer.valueOf(registro[11]));
                    mainframe.setDiscoWriteCount(Integer.valueOf(registro[12]));
                    mainframe.setDiscoLatenciaMs(Double.valueOf(registro[13].replace(",", ".")));

                    listaLido.add(mainframe);
                } catch (NumberFormatException | ParseException erro) {
                    System.out.println("Linha ignorada por erro de formatação.");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar dados do Mainframe.");
        }
    }

    public static void importarArquivoCSVProcessoMemoria(List<String[]> dados, List<Processo> listaLidoProcesso) {
        try {
            SimpleDateFormat dtEntrada = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            SimpleDateFormat dtSaida = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String ultimoTimestamp = "";
            for (int i = 1; i < dados.size(); i++) {
                String[] registro = dados.get(i);

                try {
                    Date date = dtEntrada.parse(registro[0]);
                    String timestampAtual = dtSaida.format(date);
                    if (timestampAtual.equals(ultimoTimestamp)) {
                        continue;
                    }
                    ultimoTimestamp = timestampAtual;

                    Processo processo = new Processo();
                    processo.setTimestamp(timestampAtual);
                    processo.setTimestamp(dtSaida.format(date));
                    processo.setMacAdress(registro[1]);
                    processo.setIdentificacaoMainframe(registro[2]);

                    //processos
                    processo.setNome1(registro[6]);
                    processo.setCpu1(Double.parseDouble(registro[7].replace(",", ".")));
                    processo.setMem1(Double.parseDouble(registro[8].replace(",", ".")));

                    processo.setNome2(registro[9]);
                    processo.setCpu2(Double.parseDouble(registro[10].replace(",", ".")));
                    processo.setMem2(Double.parseDouble(registro[11].replace(",", ".")));

                    processo.setNome3(registro[12]);
                    processo.setCpu3(Double.parseDouble(registro[13].replace(",", ".")));
                    processo.setMem3(Double.parseDouble(registro[14].replace(",", ".")));

                    processo.setNome4(registro[15]);
                    processo.setCpu4(Double.parseDouble(registro[16].replace(",", ".")));
                    processo.setMem4(Double.parseDouble(registro[17].replace(",", ".")));

                    processo.setNome5(registro[18]);
                    processo.setCpu5(Double.parseDouble(registro[19].replace(",", ".")));
                    processo.setMem5(Double.parseDouble(registro[20].replace(",", ".")));

                    processo.setNome6(registro[21]);
                    processo.setCpu6(Double.parseDouble(registro[22].replace(",", ".")));
                    processo.setMem6(Double.parseDouble(registro[23].replace(",", ".")));

                    processo.setNome7(registro[24]);
                    processo.setCpu7(Double.parseDouble(registro[25].replace(",", ".")));
                    processo.setMem7(Double.parseDouble(registro[26].replace(",", ".")));

                    processo.setNome8(registro[27]);
                    processo.setCpu8(Double.parseDouble(registro[28].replace(",", ".")));
                    processo.setMem8(Double.parseDouble(registro[29].replace(",", ".")));

                    processo.setNome9(registro[30]);
                    processo.setCpu9(Double.parseDouble(registro[31].replace(",", ".")));
                    processo.setMem9(Double.parseDouble(registro[32].replace(",", ".")));


                    //System.out.printf("%1s %16s %20s %20s %20s %20s %20s %20s %20s %20s %20s %20s %20s\n",registro[0],registro[1],registro[2],registro[3],registro[4],registro[5],registro[6],registro[7],registro[8],registro[9],registro[10],registro[11],registro[12]);

                    listaLidoProcesso.add(processo);
                } catch (NumberFormatException erro) {
                    System.out.println("Erro import");
                }
            }
        } catch (Exception e) {
            System.out.println("Erro ao processar dados de Processo.");
        }
    }

    public static String gerarCsvTrusted(List<Mainframe> listamainframe, List<Processo> listaprocesso) {
        StringBuilder sb = new StringBuilder();

        sb.append("macAdress;timestamp;identificao-mainframe;uso_cpu_total_%;uso_ram_total_%;uso_disco_total_%;disco_throughput_mbs;disco_iops_total;disco_read_count;disco_write_count;disco_latencia_ms;nome1;cpu_%1;mem_%1;nome2;cpu_%2;mem_%2;nome3;cpu_%3;mem_%3;nome4;cpu_%4;mem_%4;nome5;cpu_%5;mem_%5;nome6;cpu_%6;mem_%6;nome7;cpu_%7;mem_%7;nome8;cpu_%8;mem_%8;nome9;cpu_%9;mem_%9;\n");

        for (Processo processo : listaprocesso) {
            for (Mainframe mainframe : listamainframe) {
                if (mainframe.getTimestamp().equals(processo.getTimestamp())) {

                    sb.append(String.format(
                            "%s;%s;%s;%.2f;%.2f;%.2f;%.2f;%d;%d;%d;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f\n",
                            mainframe.getMacAdress(),
                            mainframe.getTimestamp(),
                            mainframe.getIdentificaoMainframe(),
                            mainframe.getUsoCpuTotal(),
                            mainframe.getUsoRamTotal(),
                            mainframe.getUsoDiscoTotal(),
                            mainframe.getDiscoThroughputMbs(),
                            mainframe.getDiscoIopsTotal().intValue(),
                            mainframe.getDiscoReadCount(),
                            mainframe.getDiscoWriteCount(),
                            mainframe.getDiscoLatenciaMs(),
                            processo.getNome1(), processo.getCpu1(), processo.getMem1(),
                            processo.getNome2(), processo.getCpu2(), processo.getMem2(),
                            processo.getNome3(), processo.getCpu3(), processo.getMem3(),
                            processo.getNome4(), processo.getCpu4(), processo.getMem4(),
                            processo.getNome5(), processo.getCpu5(), processo.getMem5(),
                            processo.getNome6(), processo.getCpu6(), processo.getMem6(),
                            processo.getNome7(), processo.getCpu7(), processo.getMem7(),
                            processo.getNome8(), processo.getCpu8(), processo.getMem8(),
                            processo.getNome9(), processo.getCpu9(), processo.getMem9()
                    ));
                }

            }
        }

        return sb.toString();
    }

    public static void importarArquivoCSVMaquina(String nomeArq, List<Mainframe> listaLido) {
        Reader arq = null; //arq eh o objeto que corresponde o arquivo
        BufferedReader entrada = null; //entrada eh o objeto usado para ler do arquivo
        nomeArq += ".csv";


        //bloco trycatch para abrir o arquivo
        try {
            arq = new InputStreamReader(new FileInputStream(nomeArq), "UTF-8");
            entrada = new BufferedReader(arq);
        } catch (IOException erro) {
            System.out.println("Erro na abertura do arquivo");
            System.exit(1);
        }

        try {
            SimpleDateFormat dtEntrada = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            SimpleDateFormat dtSaida = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String[] registro; //registro é um vetor que armazenará toda as linhas do arquivo
            String linha = entrada.readLine(); //le somenta uma linha inteira
            registro = linha.split(";");

            //ler a segunda linha do arquivo
            linha = entrada.readLine();
            while (linha != null) {
                registro = linha.split(";");

                Mainframe mainframe = new Mainframe();

                try {
                    mainframe.setMacAdress(registro[0]);
                    Date date = dtEntrada.parse(registro[1]);
                    mainframe.setTimestamp(dtSaida.format(date));

                    mainframe.setIdentificaoMainframe(registro[2]);
                    mainframe.setUsoCpuTotal(Double.valueOf(registro[3].replace(",", ".")));
                    mainframe.setUsoRamTotal(Double.valueOf(registro[4].replace(",", ".")));
                    mainframe.setSwapRateMbs(Double.valueOf(registro[5].replace(",", ".")));
                    mainframe.setTempoCpuOciosa(Double.valueOf(registro[6].replace(",", ".")));
                    mainframe.setCpuIoWait(Double.valueOf(registro[7].replace(",", ".")));
                    mainframe.setUsoDiscoTotal(Double.valueOf(registro[8].replace(",", ".")));
                    mainframe.setDiscoThroughputMbs(Double.valueOf(registro[9].replace(",", ".")));
                    mainframe.setDiscoIopsTotal(Double.valueOf(registro[10].replace(",", ".")));
                    mainframe.setDiscoReadCount(Integer.valueOf(registro[11]));
                    mainframe.setDiscoWriteCount(Integer.valueOf(registro[12]));
                    mainframe.setDiscoLatenciaMs(Double.valueOf(registro[13].replace(",", ".")));
                    listaLido.add(mainframe);

                } catch (NumberFormatException erro) {
                    System.out.println("valor nulo, objeto não foi salvo na lista para evitar irregularidades");
                } catch (ParseException e) {
                    System.out.println("data não formatada corretamente");
                    throw new RuntimeException(e);
                }

                linha = entrada.readLine();
            }
        } catch (IOException erro) {
            System.out.println("erro ao ler arquivo");
            erro.printStackTrace();
        } finally {
            try {
                entrada.close();
                arq.close();
            } catch (IOException erro) {
                System.out.println("Erro ao fechar o arquivo");
            }
        }

    }

    public static void importarArquivoCSVProcesso(String nomeArq, List<Processo> listaLidoProcesso) {
        Reader arq = null; //arq eh o objeto que corresponde o arquivo
        BufferedReader entrada = null; //entrada eh o objeto usado para ler do arquivo
        nomeArq += ".csv";


        //bloco trycatch para abrir o arquivo
        try {
            arq = new InputStreamReader(new FileInputStream(nomeArq), "UTF-8");
            entrada = new BufferedReader(arq);
        } catch (IOException erro) {
            System.out.println("Erro na abertura do arquivo");
            System.exit(1);
        }

        try {
            SimpleDateFormat dtEntrada = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
            SimpleDateFormat dtSaida = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
            String ultimoTimestamp = "";
            String[] registro; //registro é um vetor que armazenará toda as linhas do arquivo
            String linha = entrada.readLine(); //le somenta uma linha inteira
            registro = linha.split(";");
//            System.out.printf("%1s %40s %19s %20s %14s %20s %20s %20s %20s %20s %20s %20s %20s\n",registro[0],registro[1],registro[2],registro[3],registro[4],registro[5],registro[6],registro[7],registro[8],registro[9],registro[10],registro[11],registro[12]);

            //ler a segunda linha do arquivo
            linha = entrada.readLine();
            while (linha != null) {
                registro = linha.split(";");
                Processo processo = new Processo();

                //filtra os dados nulos
                try {
                    Date date = dtEntrada.parse(registro[0]);
                    String timestampAtual = dtSaida.format(date);
                    if (timestampAtual.equals(ultimoTimestamp)) {
                        linha = entrada.readLine();
                        continue;
                    }
                    //se a cpu for maior que 100%
                    if (Double.parseDouble(registro[3]) > 100.0) {
                        linha = entrada.readLine();
                        continue;
                    }
                    ultimoTimestamp = timestampAtual;

                    processo.setTimestamp(timestampAtual);
                    processo.setMacAdress(registro[1]);
                    processo.setIdentificacaoMainframe(registro[2]);
                    processo.setCpu_total(Double.parseDouble(registro[3].replace(",", ".")));
                    processo.setRam_total(Double.parseDouble(registro[4].replace(",", ".")));

                    //processos
                    processo.setNome1(registro[5]);
                    processo.setCpu1(Double.parseDouble(registro[6].replace(",", ".")));
                    processo.setMem1(Double.parseDouble(registro[7].replace(",", ".")));

                    processo.setNome2(registro[8]);
                    processo.setCpu2(Double.parseDouble(registro[9].replace(",", ".")));
                    processo.setMem2(Double.parseDouble(registro[10].replace(",", ".")));

                    processo.setNome3(registro[11]);
                    processo.setCpu3(Double.parseDouble(registro[12].replace(",", ".")));
                    processo.setMem3(Double.parseDouble(registro[13].replace(",", ".")));

                    processo.setNome4(registro[14]);
                    processo.setCpu4(Double.parseDouble(registro[15].replace(",", ".")));
                    processo.setMem4(Double.parseDouble(registro[16].replace(",", ".")));

                    processo.setNome5(registro[17]);
                    processo.setCpu5(Double.parseDouble(registro[18].replace(",", ".")));
                    processo.setMem5(Double.parseDouble(registro[19].replace(",", ".")));

                    processo.setNome6(registro[20]);
                    processo.setCpu6(Double.parseDouble(registro[21].replace(",", ".")));
                    processo.setMem6(Double.parseDouble(registro[22].replace(",", ".")));

                    processo.setNome7(registro[23]);
                    processo.setCpu7(Double.parseDouble(registro[24].replace(",", ".")));
                    processo.setMem7(Double.parseDouble(registro[25].replace(",", ".")));

                    processo.setNome8(registro[26]);
                    processo.setCpu8(Double.parseDouble(registro[27].replace(",", ".")));
                    processo.setMem8(Double.parseDouble(registro[28].replace(",", ".")));

                    processo.setNome9(registro[29]);
                    processo.setCpu9(Double.parseDouble(registro[30].replace(",", ".")));
                    processo.setMem9(Double.parseDouble(registro[31].replace(",", ".")));


                    //System.out.printf("%1s %16s %20s %20s %20s %20s %20s %20s %20s %20s %20s %20s %20s\n",registro[0],registro[1],registro[2],registro[3],registro[4],registro[5],registro[6],registro[7],registro[8],registro[9],registro[10],registro[11],registro[12]);

                    listaLidoProcesso.add(processo);
                } catch (NumberFormatException erro) {
                    //System.out.println("valor nulo, objeto não foi salvo na lista para evitar irregularidades");
                } catch (ParseException e) {
                    throw new RuntimeException(e);
                }

                linha = entrada.readLine();
            }
        } catch (IOException erro) {
            System.out.println("erro ao ler arquivo");
            erro.printStackTrace();
        } finally {
            try {
                entrada.close();
                arq.close();
            } catch (IOException erro) {
                System.out.println("Erro ao fechar o arquivo");
            }
        }
    }

    public static void gravarArquivoCSV(List<Mainframe> listamainframe, List<Processo> listaprocesso, String nomeArq) {
        //biblioteca
        OutputStreamWriter saida = null;
        Boolean falha = false;
        nomeArq += ".csv";


        try {
            saida = new OutputStreamWriter(new FileOutputStream(nomeArq), StandardCharsets.UTF_8);
        } catch (IOException erro) {
            System.out.println("Erro ao abrir o arquivo");
            System.exit(1);
        }

        try {


            saida.append("macAdress;timestamp;identificao-mainframe;uso_cpu_total_perc;uso_ram_total_perc;uso_disco_total_perc;disco_throughput_mbs;disco_iops_total;disco_read_count;disco_write_count;disco_latencia_ms;nome1;cpu_perc1;mem_perc1;nome2;cpu_perc2;mem_perc2;nome3;cpu_perc3;mem_perc3;nome4;cpu_perc4;mem_perc4;nome5;cpu_perc5;mem_perc5;nome6;cpu_perc6;mem_perc6;nome7;cpu_perc7;mem_perc7;nome8;cpu_perc8;mem_perc8;nome9;cpu_perc9;mem_perc9;nome10;cpu_perc10;mem_perc10\n");
            for (Processo processo : listaprocesso) {
                for (Mainframe mainframe : listamainframe) {
                    if (mainframe.getTimestamp().equals(processo.getTimestamp())) {
                        saida.write(String.format("%s;%s;%s;%.2f;%.2f;%.2f;%.2f;%.2f;%d;%d;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f\n", mainframe.getMacAdress(), mainframe.getTimestamp(), mainframe.getIdentificaoMainframe(), processo.getCpu_total(), processo.getRam_total(), mainframe.getUsoDiscoTotal(), mainframe.getDiscoThroughputMbs(), mainframe.getDiscoIopsTotal(), mainframe.getDiscoReadCount(), mainframe.getDiscoWriteCount(), mainframe.getDiscoLatenciaMs(), processo.getNome1(), processo.getCpu1(), processo.getMem1(), processo.getNome2(), processo.getCpu2(), processo.getMem2(), processo.getNome3(), processo.getCpu3(), processo.getMem3(), processo.getNome4(), processo.getCpu4(), processo.getMem4(), processo.getNome5(), processo.getCpu5(), processo.getMem5(), processo.getNome6(), processo.getCpu6(), processo.getMem6(), processo.getNome7(), processo.getCpu7(), processo.getMem7(), processo.getNome8(), processo.getCpu8(), processo.getMem8(), processo.getNome9(), processo.getCpu9(), processo.getMem9()));
                    }

                }
            }

        } catch (IOException erro) {
            System.out.println("Erro ao gravar no arquivo");
            erro.printStackTrace();
            falha = true;
        } finally {
            try {
                saida.close();
            } catch (IOException erro) {
                System.out.println("erro ao feixar o arquivo");
                falha = true;
            }
            if (falha) {
                System.exit(1);
            }
        }


        System.out.println("lendo o arquivo");

    }

    public static void gravarArquivoCSVSimulado(List<Mainframe> listamainframe, List<Processo> listaprocesso, String nomeArq) throws ParseException {
        //biblioteca
        OutputStreamWriter saida = null;
        Boolean falha = false;
        nomeArq += ".csv";
        long cincoMinutos = 5 * 60 * 1000;
        SimpleDateFormat dtEntrada = new SimpleDateFormat("dd-MM-yyyy HH:mm:ss");
        SimpleDateFormat dtSaida = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");
        Date date = dtSaida.parse("2025-11-21 12:00:00");

        try {
            saida = new OutputStreamWriter(new FileOutputStream(nomeArq), StandardCharsets.UTF_8);
        } catch (IOException erro) {
            System.out.println("Erro ao abrir o arquivo");
            System.exit(1);
        }

        try {


            saida.append("macAdress;timestamp;identificao-mainframe;uso_cpu_total_perc;uso_ram_total_perc;uso_disco_total_perc;disco_throughput_mbs;disco_iops_total;disco_read_count;disco_write_count;disco_latencia_ms;nome1;cpu_perc1;mem_perc1;nome2;cpu_perc2;mem_perc2;nome3;cpu_perc3;mem_perc3;nome4;cpu_perc4;mem_perc4;nome5;cpu_perc5;mem_perc5;nome6;cpu_perc6;mem_perc6;nome7;cpu_perc7;mem_perc7;nome8;cpu_perc8;mem_perc8;nome9;cpu_perc9;mem_perc9;nome10;cpu_perc10;mem_perc10\n");
            for (Processo processo : listaprocesso) {
                for (Mainframe mainframe : listamainframe) {

                    if (mainframe.getTimestamp().equals(processo.getTimestamp())) {

                        date = new Date(date.getTime() + cincoMinutos);
                        String timestampGerado = dtSaida.format(date);
                        saida.write(String.format("%s;%s;%s;%.2f;%.2f;%.2f;%.2f;%.2f;%d;%d;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f;%s;%.2f;%.2f\n", mainframe.getMacAdress(), timestampGerado, mainframe.getIdentificaoMainframe(), processo.getCpu_total(), processo.getRam_total(), mainframe.getUsoDiscoTotal(), mainframe.getDiscoThroughputMbs(), mainframe.getDiscoIopsTotal(), mainframe.getDiscoReadCount(), mainframe.getDiscoWriteCount(), mainframe.getDiscoLatenciaMs(), processo.getNome1(), processo.getCpu1(), processo.getMem1(), processo.getNome2(), processo.getCpu2(), processo.getMem2(), processo.getNome3(), processo.getCpu3(), processo.getMem3(), processo.getNome4(), processo.getCpu4(), processo.getMem4(), processo.getNome5(), processo.getCpu5(), processo.getMem5(), processo.getNome6(), processo.getCpu6(), processo.getMem6(), processo.getNome7(), processo.getCpu7(), processo.getMem7(), processo.getNome8(), processo.getCpu8(), processo.getMem8(), processo.getNome9(), processo.getCpu9(), processo.getMem9()));
                    }

                }
            }

        } catch (IOException erro) {
            System.out.println("Erro ao gravar no arquivo");
            erro.printStackTrace();
            falha = true;
        } finally {
            try {
                saida.close();
            } catch (IOException erro) {
                System.out.println("erro ao feixar o arquivo");
                falha = true;
            }
            if (falha) {
                System.exit(1);
            }
        }


        System.out.println("lendo o arquivo");

    }

}