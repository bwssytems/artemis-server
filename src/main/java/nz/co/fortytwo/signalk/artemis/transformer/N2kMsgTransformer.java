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
package nz.co.fortytwo.signalk.artemis.transformer;

import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config.JSON_DELTA;
import static nz.co.fortytwo.signalk.artemis.util.Config.MSG_SRC_BUS;
import static nz.co.fortytwo.signalk.artemis.util.Config.MSG_SRC_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config.N2K;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.UPDATES;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.self_str;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.vessels;

import java.io.File;
import java.io.IOException;

import javax.script.Bindings;
import javax.script.Invocable;
import javax.script.ScriptException;

import org.apache.activemq.artemis.api.core.ActiveMQException;
import org.apache.activemq.artemis.api.core.Message;
import org.apache.activemq.artemis.core.server.transformer.Transformer;
import org.apache.commons.io.Charsets;
import org.apache.commons.io.IOUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.coveo.nashorn_modules.FilesystemFolder;
import com.coveo.nashorn_modules.Folder;
import com.coveo.nashorn_modules.ResourceFolder;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import mjson.Json;
import nz.co.fortytwo.signalk.artemis.service.SignalkKvConvertor;
import nz.co.fortytwo.signalk.artemis.util.SignalKConstants;
import nz.co.fortytwo.signalk.artemis.util.Util;

/**
 * Processes N2K messages from canboat or similar. Converts the N2k messages to signalk
 * 
 * @author robert 
 * 
 */

public class N2kMsgTransformer extends JsBaseTransformer implements Transformer {

	private static Logger logger = LogManager.getLogger(N2kMsgTransformer.class);
	private ThreadLocal<Bindings> engineHolder;
	
	@SuppressWarnings("restriction")
	public N2kMsgTransformer() throws Exception {
		super();
	
		String resourceDir = getClass().getClassLoader().getResource("n2k-signalk/dist/bundle.js").toString();
		resourceDir = StringUtils.substringBefore(resourceDir, "dist/bundle.js");
		resourceDir = StringUtils.substringAfter(resourceDir, "file:");
		if(logger.isDebugEnabled())logger.debug("Javascript jsRoot: {}", resourceDir);

		Folder rootFolder = null;
		if (new File(resourceDir).exists()) {
			rootFolder = FilesystemFolder.create(new File(resourceDir), "UTF-8");
		} else {
			rootFolder = ResourceFolder.create(getClass().getClassLoader(), resourceDir, Charsets.UTF_8.name());
		}
		
		if(logger.isDebugEnabled())logger.debug("Starting nashorn env from: {}", rootFolder.getPath());
		
		engine = getEngine();
		
		engineHolder = ThreadLocal.withInitial(() -> {
				return engine.createBindings();
			
		});
		
	
	}

	protected NashornScriptEngine getEngine() throws IOException, ScriptException, NoSuchMethodException {
		
		if(logger.isDebugEnabled())logger.debug("Load parser: {}", "n2k-signalk/dist/bundle.js");
		
		engine.eval(IOUtils.toString(getIOStream("n2k-signalk/dist/bundle.js")));
		
		if(logger.isDebugEnabled())logger.debug("N2K mapper: {}",engine.get("n2kMapper"));	
		return engine;
		
	}


	@Override
	public Message transform(Message message) {
		
		if (!N2K.equals(message.getStringProperty(AMQ_CONTENT_TYPE)))
			return message;
		
		String bodyStr = Util.readBodyBufferToString(message.toCore()).trim();
		if (logger.isDebugEnabled())
			logger.debug("N2K Message: {}", bodyStr);
		
		if (StringUtils.isNotBlank(bodyStr) ) {
			try {
				if (logger.isDebugEnabled())
					logger.debug("Processing N2K: {}",bodyStr);

				Object result = ((Invocable) engineHolder.get()).invokeMethod(engineHolder.get().get("n2kMapper"),"toDelta", bodyStr);

				if (logger.isDebugEnabled())
					logger.debug("Processed N2K: {} ",result);

				if (result == null || StringUtils.isBlank(result.toString()) || result.toString().startsWith("Error")) {
					logger.error("{},{}", bodyStr, result);
					return message;
				}
				Json json = Json.read(result.toString());
				if(!json.isObject())return message;
						
				json.set(SignalKConstants.CONTEXT, vessels + dot + Util.fixSelfKey(self_str));
				
				if (logger.isDebugEnabled())
					logger.debug("Converted N2K msg: {}", json.toString());
				//add type.bus to source
				String type = message.getStringProperty(MSG_SRC_TYPE);
				String bus = message.getStringProperty(MSG_SRC_BUS);
				for(Json j:json.at(UPDATES).asJsonList()){
					convertSource(this, message, j, bus, type);
				}
				//now its a signalk delta msg
				message.putStringProperty(AMQ_CONTENT_TYPE, JSON_DELTA);
				SignalkKvConvertor.parseDelta(this,message, json);
				json.clear(true);
			} catch (Exception e) {
				logger.error(e, e);
				
			}
		}
		return message;
	}

	

	


}
