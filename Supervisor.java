package Ricart;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;

import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.client.AsyncInvoker;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;
import javax.ws.rs.client.InvocationCallback;
import javax.ws.rs.client.WebTarget;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;


@Singleton
@Path("/Supervisor")
public class Supervisor {

	String respuesta;
	Object peticiones = new Object();
	
	Random r = new Random();
	int max = 500;
	int min = 300;
	int maxSC = 300;
	int minSC = 100;
	int tiempo;
	
	ConcurrentHashMap<Integer,String> procesos;
	int id = 0;
	int T;
	int C=0;
	private static final int MAQUINAS = 3;
	private static final int LIBERADA = 0;
	private static final int BUSCADA = 1;
	private static final int TOMADA = 2;
	int estado = LIBERADA;
	int concedidos=0;
	
	long BbestDelay = 0;
	long BbestOffset = 0;
	long FbestDelay = 0;
	long FbestOffset = 0;
	long currDelay=0;
	long currOffset=0;
	long t0,t1,t2,t3;
	long[] delays = new long[MAQUINAS-1];
	long[] offsets = new long[MAQUINAS-1];
	
	FileWriter log;
	FileWriter debug;
	
	@Path("proceso")
	@GET
	public String proceso(@QueryParam(value="identificador") int ID,@QueryParam(value="IP") int IP) throws InterruptedException, IOException {

		id = ID;
		log = new FileWriter(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "log.txt",false);
		
		debug = new FileWriter(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "debug.txt",false);

		//LEER ARCHIVO DE CONFIGURACIÓN CON LAS IDS-IPS
        BufferedReader br = new BufferedReader(new FileReader(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "ids-ips.txt")); 
        String line = br.readLine();
        procesos = new ConcurrentHashMap<Integer,String>();
        while (line != null) 
        {
        	String[] split = line.split(" ");
        	procesos.put(Integer.parseInt(split[0]),split[1]);
            line = br.readLine(); 
        }
        Client client;
		URI uri;
		WebTarget target;
        br.close();
			//NTP
			for(int i=2;i<=MAQUINAS;i++) {
				BbestDelay= Long.MAX_VALUE;
				BbestOffset = Long.MAX_VALUE;
					for(int j=0;j<10;j++)
					{
						client = ClientBuilder.newClient();
						uri = UriBuilder.fromUri("http://" + procesos.get(2*i) + ":8080/Ricart").build();
						target = client.target(uri);
						long t0,t1,t2,t3;
						t0=System.currentTimeMillis();
						respuesta = target.path("NTP").path("pedirTiempo").request(MediaType.TEXT_PLAIN).get(String.class);
						String[] split = respuesta.split("\\s+");
						t1=Long.parseLong(split[0]);
						t2=Long.parseLong(split[1]);
						t3=System.currentTimeMillis();
						currOffset=determinarOffset(t0,t1,t2,t3);
						currDelay=determinarDelay(t0,t1,t2,t3);
						if(currDelay<BbestDelay)
						{
							BbestDelay = currDelay;
							BbestOffset = currOffset;
						}
					}
					delays[i-2]=BbestDelay;
					offsets[i-2]=BbestOffset;
			}
			AsyncInvoker async;
			//LANZAR EL RESTO DE PROCESOS
			for(int i=2;i<=procesos.size();i++) {
					client = ClientBuilder.newClient();
					if(i%2!=0)
						uri = UriBuilder.fromUri("http://" + procesos.get(i) + ":8080/Ricart").build();
					else
						uri = UriBuilder.fromUri("http://" + procesos.get(i) + ":8081/Ricart").build();
					target = client.target(uri);
					async = target.path("Agrawala").path("proceso").queryParam("identificador",i).request(MediaType.TEXT_PLAIN).async();
					async.get();
			}
		
			//100 iteraciones entrada seccion critica
			for(int i=0;i<100;i++) {
				tiempo = r.nextInt(max-min)+min;
				Thread.sleep((long) tiempo);
				//************************************//
				//PETICION ENTRADA A SC
				//************************************//
				concedidos=0;
				synchronized (this.getClass()) {
					estado=BUSCADA;
					T=C;
				//ENVIAR TODAS LAS PETICIONES A TODOS LOS PROCESOS
				for(int j = 1;j <= procesos.size(); j++) {
					//Lanzamos todas las peticiones de forma asincrona
					if(j==id)
						continue;
					client = ClientBuilder.newClient();
					if(j%2!=0)
						uri = UriBuilder.fromUri("http://" + procesos.get(j) + ":8080/Ricart").build();
					else
						uri = UriBuilder.fromUri("http://" + procesos.get(j) + ":8081/Ricart").build();
					target = client.target(uri);
					if(j!=1)
						async = target.path("Agrawala")
							 .path("peticion")
							 .queryParam("tiempo",T)
							 .queryParam("id", id)
							 .request(MediaType.TEXT_PLAIN)
							 .async();
					else
						async = target.path("Supervisor")
						 .path("peticion")
						 .queryParam("tiempo",T)
						 .queryParam("id", id)
						 .request(MediaType.TEXT_PLAIN)
						 .async();
					//Esperamos la respuesta futura
					debug.append("Petición enviada en " + System.currentTimeMillis() + "en TL" + T + System.lineSeparator());
					async.get(new InvocationCallback<String>() {
						@Override
						public void completed(String response) {
							//IMPLEMENTAR SEMÁFORO
							try {
								debug.append("Recibida respuesta de " + response +" en " + System.currentTimeMillis() + System.lineSeparator());
							} catch (IOException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
							synchronized (peticiones) {
									concedidos++;
									peticiones.notify();
							}
							
						}
						@Override
						public void failed(Throwable arg0) {
							System.out.println("Recibido fallo");
						}
					});
					}
				}
			//PROCESAR TODAS LAS PETICIONES A TODOS LOS PROCESOS
			synchronized (peticiones) {
			while(concedidos!=procesos.size()-1) {
				peticiones.wait();
				synchronized (getClass()) {
					if(concedidos==procesos.size()-1) {
						estado=TOMADA;
						C++;
					}
				}
			}
							//************************************//
							//EN SC
							//************************************//
								debug.append("P" + id + " E " + System.currentTimeMillis() + System.lineSeparator());
								log.append("P" + id + " E " + System.currentTimeMillis() + System.lineSeparator());
								tiempo = r.nextInt(maxSC-minSC)+minSC;
								Thread.sleep((long) tiempo);
								log.append("P" + id + " S " + System.currentTimeMillis() + System.lineSeparator());
								debug.append("P" + id + " S " + System.currentTimeMillis() + System.lineSeparator());
							//************************************//
							//SALIDA DE SC
							//************************************/
								synchronized (getClass()) {
									estado = LIBERADA;
									this.getClass().notifyAll();
								}

							//************************************//
							//SALIDA DE SC
							//************************************//
				}
				//************************************//
				//FIN PETICION ENTRADA A SC
				//************************************//
			}
			
			log.close();
			//NTP
			for(int i=2;i<=MAQUINAS;i++) {
				FbestDelay= Long.MAX_VALUE;
				FbestOffset = Long.MAX_VALUE;
				for(int j=0;j<10;j++)
				{
					client = ClientBuilder.newClient();
					uri = UriBuilder.fromUri("http://" + procesos.get(2*i) + ":8080/Ricart").build();
					target = client.target(uri);
					long t0,t1,t2,t3;
					t0=System.currentTimeMillis();
					respuesta = target.path("NTP").path("pedirTiempo").request(MediaType.TEXT_PLAIN).get(String.class);
					String[] split = respuesta.split("\\s+");
					t1=Long.parseLong(split[0]);
					t2=Long.parseLong(split[1]);
					t3=System.currentTimeMillis();
					currOffset=determinarOffset(t0,t1,t2,t3);
					currDelay=determinarDelay(t0,t1,t2,t3);
					if(currDelay<FbestDelay)
					{
						FbestDelay = currDelay;
						FbestOffset = currOffset;
					}
				}
				delays[i-2]=(delays[i-2]+FbestDelay)/2;
				offsets[i-2]=(offsets[i-2]+FbestOffset)/2;
			}
			for (int i=0;i<MAQUINAS-1;i++) {
				System.out.println("MAQUINA " + i + "Delay " + delays[i] + " Offset" + offsets[i]);
			}
			//TRATAMIENTO DE LOGS
			Thread.sleep(20000);
	        // LOG LOCAL
	        br = new BufferedReader(new FileReader(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "log.txt")); 
	        line = br.readLine(); 
	        ArrayList<Log> logs = new ArrayList<Log>();
	        while (line != null) 
	        {
	        	String[] split = line.split(" ");
	        	logs.add(new Log(split[0],split[1].charAt(0),Long.parseLong(split[2])));
	            line = br.readLine(); 
	        }
	        
	        //LOG REMOTO
			for(int i=2;i<=procesos.size();i++) {
				client = ClientBuilder.newClient();
				if(i%2!=0)
					uri = UriBuilder.fromUri("http://" + procesos.get(i) + ":8080/Ricart").build();
				else
					uri = UriBuilder.fromUri("http://" + procesos.get(i) + ":8081/Ricart").build();
				target = client.target(uri);
				
				Response log2 = target.path("Agrawala").path("log").request(MediaType.APPLICATION_OCTET_STREAM).get();
				
		        br = new BufferedReader(new InputStreamReader((InputStream) log2.getEntity())); 
		        line = br.readLine(); 
		        Long tiempo = null;
		        while(line != null) 
		        {
		        	//PROCESAMIENTO OFFSETS
		        	String[] split = line.split(" ");
		        	if(split[0].charAt(1)=='2')//Al proceso 2 que reside en la misma máquina no contarle offset
		        		tiempo = Long.parseLong(split[2]);
		        	if(split[0].charAt(1)=='3' || split[0].charAt(1)=='4')
		        		tiempo = Long.parseLong(split[2])-offsets[0];
		        	if(split[0].charAt(1)=='5' || split[0].charAt(1)=='6')
		        		tiempo = Long.parseLong(split[2])-offsets[1];
		        	split[2] = tiempo.toString();
		        	logs.add(new Log(split[0],split[1].charAt(0),Long.parseLong(split[2])));
		            line = br.readLine(); 
		        }
			}
	        Collections.sort(logs, new timeCompare());
	        
	        BufferedWriter bw = new BufferedWriter(new FileWriter(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "logFinal.txt"));
	        for (Log log : logs) {
				bw.write(log.proceso);
				bw.write(" ");
				bw.write(log.operacion);
				bw.write(" ");
				bw.write(Long.toString(log.tiempo));
				bw.newLine();
			}
	        // closing resources 
	        br.close(); 
	        bw.close();
	        debug.close();
			return "";
	}

	//Recepcion de una peticion para entrar en la SC
	  @Path("peticion")
	  @Produces(MediaType.TEXT_PLAIN)
	  @GET
	  public String peticion(
			  @QueryParam(value="tiempo") int Tj,
			  @QueryParam(value="id") int IDj)
					  throws InterruptedException, IOException
	  {
		  synchronized (this.getClass()) {
			  C = Math.max(C, Tj) +1;
					if(estado==TOMADA || (estado==BUSCADA && (T<Tj || (T==Tj && id<IDj))))
					{
							debug.append("Estado " + estado +" T llamador:\t" + Tj + "ID llamador:\t" + IDj+"peticion encolada en " + System.currentTimeMillis() + System.lineSeparator());
							this.getClass().wait();
							debug.append("T llamador:\t" + Tj + "ID llamador:\t" + IDj+"respondiendo en " + System.currentTimeMillis() + System.lineSeparator());
							return ""+id;
						}
					else {
						debug.append("T llamador:\t" + Tj + "ID llamador:\t" + IDj+"respondiendo automaticamente en " + System.currentTimeMillis() + System.lineSeparator());
						debug.append("T local:\t" + T + "ID local:\t" + id+"respondiendo automaticamente en " + System.currentTimeMillis() + System.lineSeparator());
						return ""+id;
					}
		  }
	  }

	private static long determinarDelay(long t0, long t1, long t2, long t3) {
		long delay = t1-t0+t3-t2;
		return delay;
	}

	private static long determinarOffset(long t0, long t1, long t2, long t3) {
		long offset = (t1-t0+t2-t3)/2;
		return offset;
	}
	
	public class Log {
		String proceso;
		char operacion;
		long tiempo;
		
		public Log(String proceso, char operacion, long tiempo) {
			super();
			this.proceso = proceso;
			this.operacion = operacion;
			this.tiempo = tiempo;
		}
	}
	class timeCompare implements Comparator
	{

		public int compare(Object log1, Object log2) {
			Log l1 = (Log) log1;
			Log l2 = (Log) log2;
			return (int) (l1.tiempo - l2.tiempo);
		}
		
	}
}
