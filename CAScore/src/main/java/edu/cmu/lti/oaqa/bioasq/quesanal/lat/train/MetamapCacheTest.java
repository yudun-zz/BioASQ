package edu.cmu.lti.oaqa.bioasq.quesanal.lat.train;

import java.io.File;

import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

public class MetamapCacheTest {

  public static void main(String[] args) {
    DB db = DBMaker.newFileDB(new File(args[0])).compressionEnable().commitFileSyncDisable()
            .cacheSize(1024).readOnly().make();
    HTreeMap<String, String> map = db.getHashMap(args[1]);
    System.out.println(map.size());
    System.out.println(map.values().stream().filter(key -> key.startsWith("<MMO>")).count());
    db.close();
  }

}
