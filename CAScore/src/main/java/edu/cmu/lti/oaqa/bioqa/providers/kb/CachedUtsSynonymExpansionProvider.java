package edu.cmu.lti.oaqa.bioqa.providers.kb;

import java.io.File;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.apache.uima.UIMAFramework;
import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.CustomResourceSpecifier;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;
import org.apache.uima.resource.impl.CustomResourceSpecifier_impl;
import org.mapdb.DB;
import org.mapdb.DBMaker;
import org.mapdb.HTreeMap;

import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Sets;

import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;

public class CachedUtsSynonymExpansionProvider extends ConfigurableProvider
        implements SynonymExpansionProvider {

  private UtsSynonymExpansionProvider delegate;

  private static final Class<UtsSynonymExpansionProvider> delegateClass = UtsSynonymExpansionProvider.class;

  private DB db;

  private HTreeMap<String, Set<String>> id2synonyms;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    // initialize delegate
    CustomResourceSpecifier delegateResourceSpecifier = new CustomResourceSpecifier_impl();
    delegateResourceSpecifier.setResourceClassName(delegateClass.getCanonicalName());
    delegate = delegateClass.cast(UIMAFramework.produceResource(delegateClass,
            delegateResourceSpecifier, aAdditionalParams));
    // initialize mapdb
    File file = new File((String) getParameterValue("db-file"));
    db = DBMaker.newFileDB(file).compressionEnable().commitFileSyncDisable().cacheSize(2048)
            .closeOnJvmShutdown().make();
    String map = (String) getParameterValue("map-name");
    id2synonyms = db.getHashMap(map);
    return ret;
  }

  @Override
  public Set<String> getSynonyms(String id) throws AnalysisEngineProcessException {
    return getSynonyms(Arrays.asList(id)).get(id);
  }

  @Override
  public Map<String, Set<String>> getSynonyms(Collection<String> ids)
          throws AnalysisEngineProcessException {
    Map<String, Set<String>> ret = ids.stream().filter(id2synonyms::containsKey)
            .collect(Collectors.toMap(Function.identity(), id2synonyms::get));
    Set<String> mids = Sets.difference(ImmutableSet.copyOf(ids), ret.keySet());
    System.out.println("Retrieved " + ret.size() + " from cache, requesting " + mids.size()
            + " missing concepts.");
    Map<String, Set<String>> mids2synonysm = delegate.getSynonyms(mids);
    ret.putAll(mids2synonysm);
    id2synonyms.putAll(mids2synonysm);
    db.commit();
    return ret;
  }

  @Override
  public void destroy() {
    super.destroy();
    db.commit();
    db.compact();
  }

}
