package eu.arrowhead.client.skeleton.stream;

import java.util.ArrayList;
import java.util.concurrent.TimeUnit;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.http.HttpMethod;
import org.springframework.web.socket.client.WebSocketConnectionManager;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.WebSocketMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.CloseStatus;

import eu.arrowhead.client.library.ArrowheadService;
import eu.arrowhead.common.CommonConstants;
import eu.arrowhead.common.dto.shared.OrchestrationFlags.Flag;
import eu.arrowhead.common.dto.shared.OrchestrationFormRequestDTO;
import eu.arrowhead.common.dto.shared.OrchestrationFormRequestDTO.Builder;
import eu.arrowhead.common.dto.shared.OrchestrationResponseDTO;
import eu.arrowhead.common.dto.shared.OrchestrationResultDTO;
import eu.arrowhead.common.dto.shared.ServiceInterfaceResponseDTO;
import eu.arrowhead.common.dto.shared.ServiceQueryFormDTO;
import eu.arrowhead.common.exception.ArrowheadException;

@SpringBootApplication
@ComponentScan(basePackages = {CommonConstants.BASE_PACKAGE}) //TODO: add custom packages if any
public class ConsumerMain implements ApplicationRunner, WebSocketHandler {
    
    //=================================================================================================
	// members
	
    @Autowired
	private ArrowheadService arrowheadService;
    
	private final Logger logger = LogManager.getLogger( ConsumerMain.class );
    
    //=================================================================================================
	// methods

	//------------------------------------------------------------------------------------------------
    public static void main( final String[] args ) {
    	SpringApplication.run(ConsumerMain.class, args);
    }

    //-------------------------------------------------------------------------------------------------
    @Override
	public void run(final ApplicationArguments args) throws Exception {
		//SIMPLE EXAMPLE OF INITIATING AN ORCHESTRATION
    	
    	final Builder orchestrationFormBuilder = arrowheadService.getOrchestrationFormBuilder();
    	
    	final ServiceQueryFormDTO requestedService = new ServiceQueryFormDTO();
    	requestedService.setServiceDefinitionRequirement("historian");
		ArrayList<String> neededInterfaces = new ArrayList<String>();
		neededInterfaces.add("WS-INSECURE-JSON");
		requestedService.setInterfaceRequirements(neededInterfaces);
    	
    	orchestrationFormBuilder.requestedService(requestedService)
    							.flag(Flag.MATCHMAKING, false) //When this flag is false or not specified, then the orchestration response cloud contain more proper provider. Otherwise only one will be chosen if there is any proper.
    							.flag(Flag.OVERRIDE_STORE, false) //When this flag is false or not specified, then a Store Orchestration will be proceeded. Otherwise a Dynamic Orchestration will be proceeded.
    							.flag(Flag.TRIGGER_INTER_CLOUD, false); //When this flag is false or not specified, then orchestration will not look for providers in the neighbor clouds, when there is no proper provider in the local cloud. Otherwise it will. 
    	
    	final OrchestrationFormRequestDTO orchestrationRequest = orchestrationFormBuilder.build();
    	
    	OrchestrationResponseDTO response = null;
    	try {
    		response = arrowheadService.proceedOrchestration(orchestrationRequest);			
		} catch (final ArrowheadException ex) {
			//Handle the unsuccessful request as you wish!
		}
    	
    	//EXAMPLE OF CONSUMING THE SERVICE FROM A CHOSEN PROVIDER
    	
    	if (response == null || response.getResponse().isEmpty()) {
    		//If no proper providers found during the orchestration process, then the response list will be empty. Handle the case as you wish!
    		System.out.println("Orchestration response is empty");
    		logger.debug("Orchestration response is empty");
    		return;
    	}
    	
    	final OrchestrationResultDTO result = response.getResponse().get(0); //Simplest way of choosing a provider.
    	
    	final HttpMethod httpMethod = HttpMethod.GET;//Http method should be specified in the description of the service.
    	final String address = result.getProvider().getAddress();
    	final int port = result.getProvider().getPort();
    	final String serviceUri = result.getServiceUri();
		
    	final String interfaceName = result.getInterfaces().get(0).getInterfaceName(); //Simplest way of choosing an interface.
    	String token = null;
    	if (result.getAuthorizationTokens() != null) {
    		token = result.getAuthorizationTokens().get(interfaceName); //Can be null when the security type of the provider is 'CERTIFICATE' or nothing.
		}
    	final Object payload = null; //Can be null if not specified in the description of the service.
    	
		System.out.println("Get endpoint as http://" + address + ":" + port + serviceUri );

		WebSocketConnectionManager wsManager = arrowheadService.connnectServiceWS(this, address, port, serviceUri, interfaceName, token, null);
		wsManager.start();
		while (true) {
    		//final String consumedService = arrowheadService.consumeServiceHTTP(this, address, port, serviceUri, interfaceName, token, payload);
			System.out.println("Got response: \n" /*+ consumedService*/);
			TimeUnit.MINUTES.sleep(1);
		}
    }

    @Override
    public void afterConnectionEstablished(WebSocketSession session) throws Exception {           
       System.out.println("Connected!");

       Thread thread = new Thread(){
       public void run() {
        TextMessage message;
        while(true) {
          try {
            System.out.println("Thread Running");
            //session.sendMessage("[{\"bn\":\"temperature\",\"bu\":\"V\"},{\"n\":\"an0\",\"v\":1.456}]"); 
            message = new TextMessage("[{\"bn\":\"temperature\",\"bu\":\"V\"},{\"n\":\"an0\",\"v\":1.456}]");
            session.sendMessage(message);
            Thread.sleep(1000);
          } catch(Exception e) {}
        }
      }
      };

      thread.start();   

    }

    @Override
    public void handleMessage(WebSocketSession session, WebSocketMessage<?> message) throws Exception {
  	System.out.println("WS-recv:\t " + message);              
    }

    @Override
    public void handleTransportError(WebSocketSession session, Throwable exception) throws Exception {
      System.out.println("Error!");             
    }

    @Override
    public void afterConnectionClosed(WebSocketSession session, CloseStatus closeStatus) throws Exception {
      System.out.println("Disconnected!");              
    }

    @Override
    public boolean supportsPartialMessages() {
      // TODO Auto-generated method stub
      return false;
    }
}
