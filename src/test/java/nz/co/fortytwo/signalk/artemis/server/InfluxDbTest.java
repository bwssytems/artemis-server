package nz.co.fortytwo.signalk.artemis.server;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.NavigableMap;
import java.util.SortedMap;
import java.util.concurrent.ConcurrentSkipListMap;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.influxdb.dto.Query;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import mjson.Json;
import nz.co.fortytwo.signalk.artemis.service.InfluxDbService;
import nz.co.fortytwo.signalk.util.JsonSerializer;

public class InfluxDbTest {

	private static Logger logger = LogManager.getLogger(InfluxDbTest.class);
	private InfluxDbService influx;
	private JsonSerializer ser = new JsonSerializer();
	@Before
	public void setUpInfluxDb() {
		logger.debug("Start influxdb client");
		influx = new InfluxDbService();
		
		ser.setPretty(2);
	}

	@After
	public void closeInfluxDb() {
		influx.close();
	}
	
	private void clearDb(){
		influx.getInfluxDB().query(new Query("drop measurement vessels", "signalk"));
		influx.getInfluxDB().query(new Query("drop measurement sources", "signalk"));
		influx.getInfluxDB().query(new Query("drop measurement config", "signalk"));
	}
	
	@Test
	public void shouldProcessFullModel() throws IOException {
		
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model.json");
		assertEquals(13,map.size());
	}

	

	@Test
	public void shouldSaveFullModelAndReturnLatest() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb("urn:mrn:signalk:uuid:705f5f1a-efaf-44aa-9cb8-a0fd6305567c");
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveFullModelAndReturnLatestWithEdit() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb("urn:mrn:signalk:uuid:705f5f1a-efaf-44aa-9cb8-a0fd6305567c");
		compareMaps(map,rslt);
		//now run again with variation
		map.put("vessels.urn:mrn:signalk:uuid:705f5f1a-efaf-44aa-9cb8-a0fd6305567c.navigation.headingMagnetic.value",Json.make(6.55));
		//save and flush
		influx.save(map);
		//reload
		rslt = influx.loadData(map,"select * from vessels group by skey,uuid order by time desc limit 1","signalk");
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveFullModelSamples() throws IOException {
		File dir = new File("./src/test/resources/samples/full");
		for(File f:dir.listFiles()){
			if(f.isDirectory())continue;
			clearDb();
			logger.debug("Testing sample:"+f.getName());
			// get a sample of signalk
			NavigableMap<String, Json> map = getJsonMap(f.getAbsolutePath());
			//save and flush
			influx.save(map);
			//reload from db
			NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString(),map.get("version").asString());
			compareMaps(map,rslt);
		}
	}
	
	@Test
	public void shouldSaveFullModelWithMultipleValues() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model_multiple_values.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveFullModelWithMeta() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-data_model_metadata.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}

	@Test
	public void shouldSaveNotifications() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/docs-notifications.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveAcElectrical() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/electrical-ac-full.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveMobAlarm() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/electrical-ac-full.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveDepthAlarm() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/signalk-depth-alarm.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveDepthAlarmMeta() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/signalk-depth-meta-attr.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	@Test
	public void shouldSaveVesselTime() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/vessel-time.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void shouldSaveRmcMin() throws IOException {
		// get a sample of signalk
		NavigableMap<String, Json> map = getJsonMap("./src/test/resources/samples/full/0183-RMC-export-min.json");
		//save and flush
		influx.save(map);
		//reload from db
		NavigableMap<String, Json> rslt = loadFromDb(map.get("self").asString());
		compareMaps(map,rslt);
	}
	
	@Test
	public void testFullResources() throws IOException {
		// get a hash of signalk
		String body = FileUtils.readFileToString(new File("./src/test/resources/samples/full_resources.json"));
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		influx.recurseJsonFull(Json.read(body), map, "");
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		map.forEach((k, v) -> influx.save(k, v));
	}
	
	

	@Test
	public void testConfigJson() throws IOException {
		// get a hash of signalk
		String body = FileUtils.readFileToString(new File("./src/test/resources/samples/signalk-config.json"));
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		influx.recurseJsonFull(Json.read(body), map, "");
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		map.forEach((k, v) -> influx.save(k, v));
	}
	
	@Test
	public void testConfigQuery() throws IOException {
		NavigableMap<String, Json> map = influx.loadConfig();
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		logger.debug(ser.write((SortedMap)map));
	}
	

//	@Test
//	public void testDeltaJson() throws Exception {
//		// get a hash of signalk
//		List<String> body = FileUtils.readLines(new File("./src/test/resources/samples/signalkKeesLog.txt"));
//		for (String l : body) {
//			NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
//			influx.parseDelta(Json.read(l), map);
//			map.forEach((t, u) -> logger.debug(t + "=" + u));
//			map.forEach((k, v) -> influx.save(k, v));
//		}
//	}

	@Test
	public void testPKTreeJson() throws Exception {
		// get a hash of signalk
		String body = FileUtils.readFileToString(new File("./src/test/resources/samples/PK_tree.json"));
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		influx.recurseJsonFull(Json.read(body), map, "");
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		map.forEach((k, v) -> influx.save(k, v));
	}
	
	private void compareMaps(NavigableMap<String, Json> map, NavigableMap<String, Json> rslt) {
		//are they the same
		
		map.forEach((t, u) -> {
			logger.debug(t+":"+u+"|"+rslt.get(t));
			assertEquals("Entries differ: "+t ,u, rslt.get(t));
		});
		assertEquals("Maps differ",map,rslt);
		logger.debug("Entries are the same");
		logger.debug("Map size=" + map.size());
		logger.debug("Rslt size=" + rslt.size());
		assertEquals("Maps differ in size",map.size(),rslt.size());
	}

	private NavigableMap<String, Json> getJsonMap(String file) throws IOException {
		String body = FileUtils.readFileToString(new File(file));
		//convert to map
		NavigableMap<String, Json> map = new ConcurrentSkipListMap<String, Json>();
		influx.recurseJsonFull(Json.read(body), map, "");
		map.forEach((t, u) -> logger.debug(t + "=" + u));
		return map;
	}
	
	private NavigableMap<String, Json> loadFromDb(String self) {
		return loadFromDb(self,"1.0.0");
	}
	private NavigableMap<String, Json> loadFromDb(String self, String version) {
		NavigableMap<String, Json> rslt = new ConcurrentSkipListMap<String, Json>();
		
		//add std entries
		rslt.put("self",Json.make(self));
		rslt.put("version",Json.make(version));
		
		rslt = influx.loadData(rslt,"select * from vessels group by skey,uuid order by time desc limit 1","signalk");
		rslt = influx.loadSources(rslt,"select * from sources group by skey order by time desc limit 1","signalk");
		rslt.forEach((t, u) -> logger.debug(t + "=" + u));
		return rslt;
	}
}
