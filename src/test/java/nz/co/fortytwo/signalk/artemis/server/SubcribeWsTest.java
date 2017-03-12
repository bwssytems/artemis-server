/*
 *
 * Copyright (C) 2012-2014 R T Huitema. All Rights Reserved.
 * Web: www.42.co.nz
 * Email: robert@42.co.nz
 * Author: R T Huitema
 *
 * This file is provided AS IS with NO WARRANTY OF ANY KIND, INCLUDING THE
 * WARRANTY OF DESIGN, MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package nz.co.fortytwo.signalk.artemis.server;

import static nz.co.fortytwo.signalk.util.SignalKConstants.SIGNALK_AUTH;
import static nz.co.fortytwo.signalk.util.SignalKConstants.SIGNALK_DISCOVERY;
import static nz.co.fortytwo.signalk.util.SignalKConstants.SIGNALK_WS;
import static nz.co.fortytwo.signalk.util.SignalKConstants.websocketUrl;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.*;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.service.SignalkBroadcaster;
import nz.co.fortytwo.signalk.util.SignalKConstants;

import org.apache.activemq.artemis.api.core.client.ClientMessage;
import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.ning.http.client.AsyncHttpClient;
import com.ning.http.client.Response;
import com.ning.http.client.ws.DefaultWebSocketListener;
import com.ning.http.client.ws.WebSocket;
import com.ning.http.client.ws.WebSocketUpgradeHandler;

public class SubcribeWsTest {
 
	private static Logger logger = LogManager.getLogger(SubcribeWsTest.class);
	String jsonDiff = null;
	private String restPort="8080";
	private String wsPort="8080";
	private ArtemisServer server;


	public SubcribeWsTest(){
		
	}
	@Before
	public void startServer() throws Exception {
		server = new ArtemisServer();
	}

	@After
	public void stopServer() throws Exception {
		server.stop();
	}
	@Test
    public void shouldGetWsUrl() throws Exception {
		
        final AsyncHttpClient c = new AsyncHttpClient();
        
        //get a sessionid
        //Response r1 = c.prepareGet("http://localhost:"+restPort+SIGNALK_AUTH+"/demo/pass").execute().get();
       // assertEquals(200, r1.getStatusCode());
        Response r2 = c.prepareGet("http://localhost:"+restPort+SIGNALK_DISCOVERY).execute().get();
        Json json = Json.read(r2.getResponseBody());
        assertEquals("ws://localhost:"+wsPort+SIGNALK_WS, json.at("endpoints").at("v1").at(websocketUrl).asString());
        c.close();
	}
	
	@Test
    public void shouldGetSubscribeWsResponse() throws Exception {
		final List<String> received = new ArrayList<String>();
	    final CountDownLatch latch3 = new CountDownLatch(5);
        final AsyncHttpClient c = new AsyncHttpClient();
        
       String restUrl = "ws://localhost:8080/signalk/v1/api";
        logger.debug("Open websocket at: "+restUrl);
        WebSocket websocket = c.prepareGet(restUrl).execute(
                new WebSocketUpgradeHandler.Builder()
                    .addWebSocketListener(new DefaultWebSocketListener() {
                    	
                        @Override
                        public void onMessage(byte[] message) {                         
                            logger.info("received BYTES --> " + String.valueOf(message));
                        }
                        
                        @Override
                        public void onMessage(String message) {
                            logger.info("received --> " + message);
                            received.add(message);
                        }
                      
                    }).build()).get();

      //subscribe
        String subscribeMsg="{\"context\":\"vessels.urn:mrn:imo:mmsi:123456789\",\"subscribe\":[{\"path\":\"navigation\"}]}";
		websocket.sendMessage(subscribeMsg);
		
		logger.debug("Sent subscribe = "+subscribeMsg);
        //latch4.await(2, TimeUnit.SECONDS);
      
       //send some data
		for (String line : FileUtils.readLines(new File("./src/test/resources/samples/signalkKeesLog.txt"))) {

			websocket.sendMessage(line);
			if (logger.isDebugEnabled())
				logger.debug("Sent:" + line);
		

		}
      
        latch3.await(10, TimeUnit.SECONDS);
        assertTrue(received.size()>1);
        //assertTrue(latch3.await(15, TimeUnit.SECONDS));
        String fullMsg = null;
        for(String msg : received){
        	logger.debug("Received msg = "+msg);
        	if(msg.contains("\"updates\":[{\"")&&msg.contains("\"path\":\"navigation")){
        		fullMsg=msg;
        	}
        }
        assertTrue(received.size()>1);
       
        //Json sk = Json.read("{\"context\":\"vessels."+SignalKConstants.self+".navigation\",\"updates\":[{\"values\":[{\"path\":\"courseOverGroundTrue\",\"value\":3.0176},{\"path\":\"speedOverGround\",\"value\":3.85}],\"source\":{\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"device\":\"/dev/actisense\",\"src\":\"115\",\"pgn\":\"128267\"}}]}");
        //Json sk = Json.read("{\"context\":\"vessels.motu\",\"updates\":[{\"values\":[{\"path\":\"navigation.courseOverGroundTrue\",\"value\":3.0176},{\"path\":\"navigation.speedOverGround\",\"value\":3.85}],\"timestamp\":\"2014-08-15T16:00:00.081+00:00\",\"source\":{\"device\":\"/dev/actisense\",\"src\":\"115\",\"pgn\":\"128267\"}}]}");
        assertNotNull(fullMsg);
        assertTrue(fullMsg.contains("\"context\":\"vessels.motu\""));
        assertTrue(fullMsg.contains("\"path\":\"navigation.courseOverGroundTrue\""));
        assertTrue(fullMsg.contains("\"value\":3.0176"));
        assertTrue(fullMsg.contains("\"updates\":[{"));
        
        c.close();
    }
	
}