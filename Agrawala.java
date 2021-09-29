package Ricart;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.net.URI;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

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
import javax.ws.rs.container.AsyncResponse;
import javax.ws.rs.container.Suspended;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.UriBuilder;


@Singleton
@Path("/Agrawala")
public class Agrawala {


	String respuesta;
	Random r = new Random();
	int max = 500;
	int min = 300;
	int maxSC = 300;
	int minSC = 100;
	int tiempo;
	ConcurrentHashMap<Integer,String> procesos;
	Object peticiones = new Object();
	
	int id = 0;
	int T;
	int C=0;
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
	
	FileWriter log;
	
	@Path("proceso")
	@GET
	public void proceso(@Suspended final AsyncResponse asyn,@QueryParam(value="identificador") int ID,@QueryParam(value="IP") int IP) throws InterruptedException, IOException {
		id = ID;
		if(id%2!=0)
			log = new FileWriter(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "log.txt",false);
		else
			log = new FileWriter(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "log2.txt",false);


		BbestDelay= Long.MAX_VALUE;
		BbestOffset = Long.MAX_VALUE;
		FbestDelay= Long.MAX_VALUE;
		FbestOffset = Long.MAX_VALUE;
		
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
		
		AsyncInvoker async;
		//100 iteraciones entrada seccion critica
		for(int i=0;i<100;i++) {
			tiempo = r.nextInt(max-min)+min;
			Thread.sleep((long) tiempo);
			//************//
			//PETICION ENTRADA A SC
			//************//
			concedidos=0;
			synchronized (this.getClass()) {
				estado=BUSCADA;
				T=C;
			//ENVIAR TODAS LAS PETICIONES A TODOS LOS PROCESOS
				for(int j = 1;j <= procesos.size(); j++) {
					if(j==id)
						continue;
					//Lanzamos todas las peticiones de forma asincrona
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
					//System.out.println("Petición enviada en " + System.currentTimeMillis());
					async.get(new InvocationCallback<String>() {
						@Override
						public void completed(String response) {
							synchronized (peticiones) {
								concedidos++;
								peticiones.notify();
							}
						}
						@Override
						public void failed(Throwable arg0) {
							//System.out.println("Recibido fallo");
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
						//************//
						//EN SC
						//************//
							//System.out.println("P" + id + " E " + System.currentTimeMillis());
							log.append("P" + id + " E " + System.currentTimeMillis() + System.lineSeparator());
							tiempo = r.nextInt(maxSC-minSC)+minSC;
							Thread.sleep((long) tiempo);
							log.append("P" + id + " S " + System.currentTimeMillis() + System.lineSeparator());
							//System.out.println("P" + id + " S " + System.currentTimeMillis());
						//************//
						//SALIDA DE SC
						//************/
							synchronized (getClass()) {
								estado = LIBERADA;
								this.getClass().notifyAll();
							}

						//************//
						//SALIDA DE SC
						//************//
			}
			//************//
			//FIN PETICION ENTRADA A SC
			//************//
		}
			log.close();
			
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
				  		//System.out.println("T llamador:\t" + Tj + "ID llamador:\t" + IDj+"peticion encolada en " + System.currentTimeMillis());
						this.getClass().wait();
						//System.out.println("T llamador:\t" + Tj + "ID llamador:\t" + IDj+"respondiendo en " + System.currentTimeMillis());
						//T = Math.max(T, Tj) +1;
						return ""+id;
					}
				else {
					//T = Math.max(T, Tj) +1;
					return ""+id;
				}
		  }
	  }

	  @Path("log")
	  @GET
	  @Produces(MediaType.APPLICATION_OCTET_STREAM)
	  public Response getFile() {
	    File file;
	    if(id%2!=0)
	    	file = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "log.txt");
	    else
	    	file = new File(System.getProperty("user.home") + File.separator + "Desktop" + File.separator + "log2.txt");
	    return Response.ok(file, MediaType.APPLICATION_OCTET_STREAM)
	        .header("Content-Disposition", "attachment; filename=\"" + file.getName() + "\"" ) //optional
	        .build();
	  }
}
