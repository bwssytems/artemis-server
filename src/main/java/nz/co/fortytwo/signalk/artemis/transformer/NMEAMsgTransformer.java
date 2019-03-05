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

import static nz.co.fortytwo.signalk.artemis.util.Config.AIS;
import static nz.co.fortytwo.signalk.artemis.util.Config.AMQ_CONTENT_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config.JSON_DELTA;
import static nz.co.fortytwo.signalk.artemis.util.Config.MSG_SRC_BUS;
import static nz.co.fortytwo.signalk.artemis.util.Config.MSG_SRC_TYPE;
import static nz.co.fortytwo.signalk.artemis.util.Config._0183;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.UPDATES;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.dot;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.self_str;
import static nz.co.fortytwo.signalk.artemis.util.SignalKConstants.source;
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
import org.graalvm.polyglot.Context;
import org.graalvm.polyglot.Value;

import com.coveo.nashorn_modules.FilesystemFolder;
import com.coveo.nashorn_modules.Folder;
import com.coveo.nashorn_modules.ResourceFolder;

import jdk.nashorn.api.scripting.NashornScriptEngine;
import mjson.Json;
import nz.co.fortytwo.signalk.artemis.service.SignalkKvConvertor;
import nz.co.fortytwo.signalk.artemis.util.Config;
import nz.co.fortytwo.signalk.artemis.util.SignalKConstants;
import nz.co.fortytwo.signalk.artemis.util.Util;

/**
 * Processes NMEA sentences in the body of a message, firing events to
 * interested listeners Converts the NMEA messages to signalk
 * 
 * @author robert
 * 
 */

public class NMEAMsgTransformer extends JsBaseTransformer implements Transformer {

	private static Logger logger = LogManager.getLogger(NMEAMsgTransformer.class);
	private ThreadLocal<Context> engineHolder;
	
	
	@SuppressWarnings("restriction")
	public NMEAMsgTransformer() throws Exception {
		super();

		String resourceDir = getClass().getClassLoader().getResource("signalk-parser-nmea0183/parser.js").toString();
		resourceDir = StringUtils.substringBefore(resourceDir, "index-es5.js");
		resourceDir = StringUtils.substringAfter(resourceDir, "file:");
		if(logger.isDebugEnabled())logger.debug("Javascript jsRoot: {}", resourceDir);

		Folder rootFolder = null;
		if (new File(resourceDir).exists()) {
			rootFolder = FilesystemFolder.create(new File(resourceDir), "UTF-8");
		} else {
			rootFolder = ResourceFolder.create(getClass().getClassLoader(), resourceDir, Charsets.UTF_8.name());
		}
		if(logger.isDebugEnabled())logger.debug("Starting graal env from: {}", rootFolder.getPath());
		
		engineHolder = ThreadLocal.withInitial(() -> {
			
				try {
					return initEngine();
				} catch (IOException e) {
					logger.error(e,e);
					return null;
				}
			
		});
		
		
		
		
	}

	protected Context initEngine() throws IOException  {
		
		Context context = Context.newBuilder("js").allowHostAccess(true).build();
		
		if(logger.isDebugEnabled())logger.debug("Load parser: {}", "signalk-parser-nmea0183/dist/bundle.js");
		 Value jsCtx = context.eval("js", IOUtils.toString(getIOStream("signalk-parser-nmea0183/dist/bundle.js")));
		 
		if(logger.isDebugEnabled())logger.debug("Parser: {}",jsCtx.getMemberKeys());
		
		String hooks = IOUtils.toString(getIOStream("signalk-parser-nmea0183/hooks-es5/supported.txt"), Charsets.UTF_8);
		if(logger.isDebugEnabled())logger.debug("Hooks: {}",hooks);

		String[] files = hooks.split("\n");
		
		for (String f : files) {
			// seatalk breaks
			if (f.startsWith("ALK"))
				continue;
			if(logger.isDebugEnabled())logger.debug(f);
			//Invocable inv = (Invocable) engine;
			context.getBindings("js").getMember("parser").invokeMember("loadHook", f.trim());
		}
		return context;
	}


	@Override
	public Message transform(Message message) {
		
		if (!(_0183.equals(message.getStringProperty(AMQ_CONTENT_TYPE))|| AIS.equals(message.getStringProperty(AMQ_CONTENT_TYPE))))
			return message;
		
		String bodyStr = Util.readBodyBufferToString(message.toCore()).trim();
		if (logger.isDebugEnabled())
			logger.debug("NMEA Message: " + bodyStr);
		
		if (StringUtils.isNotBlank(bodyStr) && bodyStr.startsWith("$")) {
			try {
				if(engineHolder==null)engineHolder=ThreadLocal.withInitial(() -> {
					try {
						return initEngine();
					} catch (IOException e) {
						logger.error(e,e);
						return null;
					}
				});
				if (logger.isDebugEnabled()) {
					logger.debug("Processing NMEA:[" + bodyStr + "]");
					logger.debug("Parser inv: {}",engineHolder.get().getBindings("js").getMember("parser"));
					
				}
				
				Object result =  engineHolder.get().getBindings("js").getMember("parser").invokeMember("parse", bodyStr);

				if (logger.isDebugEnabled())
					logger.debug("Processed NMEA: " + result );

				if (result==null || StringUtils.isBlank(result.toString())|| result.toString().startsWith("WARN")) {
					logger.warn(bodyStr + "," + result);
					return message;
				}
				Json json = Json.read(result.toString());
				if(!json.isObject())return message;
				
				json.set(SignalKConstants.CONTEXT, vessels + dot + Util.fixSelfKey(self_str));
				//add type.bus to source
				String type = message.getStringProperty(MSG_SRC_TYPE);
				String bus = message.getStringProperty(MSG_SRC_BUS);
				//now its a signalk delta msg
				message.putStringProperty(AMQ_CONTENT_TYPE, JSON_DELTA);
				for(Json j:json.at(UPDATES).asJsonList()){
					convertSource(this, message, j, bus, type);
				}
				
				if (logger.isDebugEnabled())
					logger.debug("Converted NMEA msg:" + json.toString());
				
				SignalkKvConvertor.parseDelta(this,message, json);
				json.clear(true);
				
				
			} catch (Exception e) {
				logger.error(e, e);
				
			}
			
		}
		return message;
	}

	
}
