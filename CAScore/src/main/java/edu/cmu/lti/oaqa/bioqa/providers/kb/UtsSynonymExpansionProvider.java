package edu.cmu.lti.oaqa.bioqa.providers.kb;

import static java.util.stream.Collectors.toSet;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.apache.uima.analysis_engine.AnalysisEngineProcessException;
import org.apache.uima.resource.ResourceInitializationException;
import org.apache.uima.resource.ResourceSpecifier;

import edu.cmu.lti.oaqa.baseqa.providers.kb.SynonymExpansionProvider;
import edu.cmu.lti.oaqa.ecd.config.ConfigurableProvider;
import gov.nih.nlm.uts.webservice.content.AtomDTO;
import gov.nih.nlm.uts.webservice.content.TermStringDTO;
import gov.nih.nlm.uts.webservice.content.UtsWsContentController;
import gov.nih.nlm.uts.webservice.content.UtsWsContentControllerImplService;
import gov.nih.nlm.uts.webservice.security.UtsFault_Exception;
import gov.nih.nlm.uts.webservice.security.UtsWsSecurityController;
import gov.nih.nlm.uts.webservice.security.UtsWsSecurityControllerImplService;

public class UtsSynonymExpansionProvider extends ConfigurableProvider
        implements SynonymExpansionProvider {

  private String service;

  private String version;

  private String grantTicket;

  private UtsWsSecurityController securityService;

  private UtsWsContentController contentService;

  private int nthreads;

  private Integer timeout;

  @Override
  public boolean initialize(ResourceSpecifier aSpecifier, Map<String, Object> aAdditionalParams)
          throws ResourceInitializationException {
    boolean ret = super.initialize(aSpecifier, aAdditionalParams);
    this.service = String.class.cast(getParameterValue("service"));
    this.version = String.class.cast(getParameterValue("version"));
    securityService = (new UtsWsSecurityControllerImplService())
            .getUtsWsSecurityControllerImplPort();
    String username = String.class.cast(getParameterValue("username"));
    String password = String.class.cast(getParameterValue("password"));
    try {
      grantTicket = securityService.getProxyGrantTicket(username, password);
    } catch (UtsFault_Exception e) {
      throw new ResourceInitializationException(e);
    }
    contentService = (new UtsWsContentControllerImplService()).getUtsWsContentControllerImplPort();
    nthreads = Integer.class.cast(getParameterValue("nthreads"));
    timeout = Integer.class.cast(getParameterValue("timeout"));
    return ret;
  }

  public UtsSynonymExpansionProvider() {
  }

  public UtsSynonymExpansionProvider(String service, String version, String username,
          String password) throws gov.nih.nlm.uts.webservice.security.UtsFault_Exception {
    this.service = service;
    this.version = version;
    securityService = (new UtsWsSecurityControllerImplService())
            .getUtsWsSecurityControllerImplPort();
    grantTicket = securityService.getProxyGrantTicket(username, password);
    contentService = (new UtsWsContentControllerImplService()).getUtsWsContentControllerImplPort();
  }

  @Override
  public Set<String> getSynonyms(String id) throws AnalysisEngineProcessException {
    try {
      return contentService.getConceptAtoms(getSingleUseTicket(), version, id, createContentPsf())
              .stream().map(AtomDTO::getTermString).map(TermStringDTO::getName).collect(toSet());
    } catch (gov.nih.nlm.uts.webservice.content.UtsFault_Exception | UtsFault_Exception e) {
      throw new AnalysisEngineProcessException(e);
    }
  }

  @Override
  public Map<String, Set<String>> getSynonyms(Collection<String> ids)
          throws AnalysisEngineProcessException {
    Map<String, Set<String>> id2synonyms = new ConcurrentHashMap<>();
    ExecutorService es = Executors.newFixedThreadPool(nthreads);
    ids.forEach(id -> {
      es.execute(() -> {
        try {
          id2synonyms.put(id, getSynonyms(id));
        } catch (Exception e) {
          e.printStackTrace();
        }
      } );
    } );
    es.shutdown();
    try {
      if (!es.awaitTermination(timeout, TimeUnit.MINUTES)) {
        System.out.println("Timeout occurs for one or some concept retrieval service.");
      }
    } catch (InterruptedException e) {
      throw new AnalysisEngineProcessException(e);
    }
    return id2synonyms;
  }

  private gov.nih.nlm.uts.webservice.content.Psf createContentPsf() {
    gov.nih.nlm.uts.webservice.content.Psf psf = new gov.nih.nlm.uts.webservice.content.Psf();
    psf.setIncludedLanguage("ENG");
    psf.setPageLn(1000);
    return psf;
  }

  private String getSingleUseTicket()
          throws gov.nih.nlm.uts.webservice.security.UtsFault_Exception {
    return securityService.getProxyTicket(grantTicket, service);
  }

  public static void main(String[] args) throws Exception {
    UtsSynonymExpansionProvider service = new UtsSynonymExpansionProvider(args[0], args[1], args[2],
            args[3]);
    Set<String> synonyms = service.getSynonyms("C1527336");
    synonyms.stream().forEach(System.out::println);
  }

}
