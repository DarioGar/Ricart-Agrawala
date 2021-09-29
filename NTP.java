package Ricart;


import javax.inject.Singleton;
import javax.ws.rs.GET;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;

@Singleton
@Path("/NTP")
public class NTP {

	long t1,t2;
	
	  @Path("pedirTiempo")
	   @GET
	   @Produces(MediaType.TEXT_PLAIN)
	   public String pedirTiempo()
	   {
		  t1 = System.currentTimeMillis();
		  t2 = System.currentTimeMillis();
		  return ""+t1+" "+t2;
	   }
}
