/**
 * Copyright 2012 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://www.osor.eu/eupl
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the Licence is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the Licence for the specific language governing permissions and
 * limitations under the Licence.
 */
package eu.debooy.sitemanager;

import eu.debooy.doosutils.Arguments;
import eu.debooy.doosutils.Banner;
import eu.debooy.doosutils.Datum;
import eu.debooy.doosutils.DoosConstants;
import eu.debooy.doosutils.access.Bestand;
import eu.debooy.doosutils.exception.BestandException;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


/**
 * @author Marco de Booij
 */
public class GenerateSite {
  private final static  String  INCLUDE_TAG     = "<include>";
  private final static  String  INCLUDE_ENDTAG  = "</include>";

  private static  Boolean           localOverview   = false;
  private static  Boolean           sourceGenerate  = true;
  private static  BufferedWriter    sitemap         = null;
  private static  Map<String, Long> includes        =
      new HashMap<String, Long>();
  private static  Calendar          nu              = new GregorianCalendar();
  private static  String            localSite       = "";
  private static  String            siteURL         = "";
  private static  String            sourcePages     = "";
  private static  String            sourceIncludes  = "";

  private GenerateSite(){}

  /**
   * Sluit het sitemap.xml bestand.
   */
  private static void closeSitemap() {
    try {
      sitemap.write("</urlset>");
      sitemap.newLine();
      sitemap.close();
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }

  /**
   * Verwijderd een directory met alle onderliggende files/directories.
   * 
   * @param directory
   */
  private static void deleteDirectory(String directory) {
    String[]  content = new File(directory).list();
    if (content != null) {
      for (int i = 0; i < content.length; i++) {
        File  file  = new File(directory + File.separator + content[i]);
        if (file.isDirectory()) {
          deleteDirectory(directory + File.separator + content[i]);
        } else {
          try {
            if (!file.delete()) {
              System.out.println("Kan bestand " + directory + File.separator
                                 + content[i] + " niet verwijderen.");
            }
          } catch (SecurityException e) {
            System.out.println("Fout bij verwijderen van " + directory
                               + File.separator + content[i] + " ["
                               + e.getLocalizedMessage() + "].");
          }
        }
      }
    }
    try {
      if (!new File(directory).delete()) {
        System.out.println("Kan directory " + directory + " niet verwijderen.");
      }
    } catch (SecurityException e) {
      System.out.println("Fout bij verwijderen van " + directory + " ["
                         + e.getLocalizedMessage() + "].");
    }
  }

  public static void execute(String[] args) {

    Banner.printBanner("Generate Site");

    Arguments arguments = new Arguments(args);
    arguments.setParameters(new String[] {"localOverview", "localSite",
                                          "siteURL", "sourceGenerate",
                                          "sourceIncludes", "sourcePages"});
    arguments.setVerplicht(new String[] {"localSite"});
    if (!arguments.isValid()) {
      help();
      return;
    }

    // Zet de parameters.
    if (arguments.hasArgument("localOverview")) {
      localOverview   =
        Boolean.parseBoolean(arguments.getArgument("localOverview"));
    }
    localSite       = arguments.getArgument("localSite");
    if (arguments.hasArgument("siteURL")) {
      siteURL         = arguments.getArgument("siteURL");
    }
    if (arguments.hasArgument("sourceGenerate")) {
      sourceGenerate  =
        Boolean.parseBoolean(arguments.getArgument("sourceGenerate"));
    }
    if (arguments.hasArgument("sourcePages")) {
      sourcePages     = arguments.getArgument("sourcePages");
    }
    if (arguments.hasArgument("sourceIncludes")) {
      sourceIncludes  = arguments.getArgument("sourceIncludes");
    }
    // Test of de parameters voor een 'sourceGenerate' gegeven zijn.
    List<String>  fouten  = new ArrayList<String>();
    if (sourceGenerate) {
      if (!arguments.hasArgument("sourcePages")) {
        fouten.add("- sourcePages verplicht bij sourceGenerate=true.");
      }
      if (!arguments.hasArgument("siteURL")) {
        fouten.add("- siteURL verplicht bij sourceGenerate=true.");
      }
      if (!arguments.hasArgument("sourceIncludes")) {
        fouten.add("- sourceIncludes verplicht bij sourceGenerate=true.");
      }
    }
    if (!fouten.isEmpty()) {
      help();
      for (String fout: fouten) {
        System.out.println(fout);
      }
      return;
    }

    if (sourceGenerate) {
      setIncludeModified(sourceIncludes);
      processWebSite(sourcePages);
    }

    if (localOverview
        || sourceGenerate) {
      openSitemap();
      showDirectoryContent(localSite);
      closeSitemap();
    }

    System.out.println("Local Site: " + localSite);
    System.out.println("Klaar.");
  }

  /**
   * Geeft de 'help' pagina.
   */
  protected static void help() {
    System.out.println("java -jar SiteManager.jar GenerateSite --localSite=<directory> [OPTIE...]");
    System.out.println();
    System.out.println("  --localOverview  [true|FALSE] Bestandslijst van de 'lokale' website?");
    System.out.println("  --localSite                   De directory van de 'lokale' website.");
    System.out.println("  --siteURL                     De 'home' URL van de website (zonder http://).");
    System.out.println("  --sourceGenerate [TRUE|false] De 'lokale' website genereren?");
    System.out.println("  --sourceIncludes              De directory van de 'includes'.");
    System.out.println("  --sourcePages                 De directory van de 'source' paginas.");
    System.out.println();
    System.out.println("De parameter localSite is altijd verplicht.");
    System.out.println("Bij sourceGenerate TRUE zijn de parameters siteURL, sourceIncludes, en");
    System.out.println("  sourcePages ook verplicht.");
    System.out.println();
  }

  /**
   * Kijk of een van de gebruikte 'include' veranderd is.
   *
   * @param bestand
   * @return
   */
  private static boolean isChanged(String bestand, Long modified) {
    BufferedReader  invoer    = null;
    String          lijn      = null;

    try {
      invoer  = new BufferedReader(new FileReader(bestand));
      while ((lijn = invoer.readLine()) != null) {
        if (lijn.indexOf(INCLUDE_TAG) >= 0) {
          String  include = lijn.substring(lijn.indexOf(INCLUDE_TAG)
                                            + INCLUDE_TAG.length(),
                                           lijn.indexOf(INCLUDE_ENDTAG));
          if (includes.get(sourceIncludes + File.separator
                           + include) > modified) {
            invoer.close();
            return true;
          }
        }
      }
      invoer.close();
    } catch (IOException e) {
      if (null != invoer) {
        System.out.println(e.getLocalizedMessage());
      }
    }

    return false;
  }

  /**
   * Kijkt of de 'bron' veranderd is. Dit is altijd true als de laatste
   * modificatie datum van een include file later is dan de file.
   * 
   * @param directory
   * @param content
   * @return
   */
  private static boolean isChanged(String directory, String content) {
    File  file            =
      new File((directory + File.separator + content).replace(sourcePages,
                                                             localSite));
    Long  sourceModified  =
      new File(directory + File.separator + content).lastModified();
    if (file.exists()) {
      if (file.isFile()) {
        return (file.lastModified() < sourceModified
                || isChanged((directory + File.separator + content),
                             file.lastModified()));
      } else {
        deleteDirectory((directory + File.separator+ content)
            .replace(sourcePages, localSite));
      }
    }

    return true;
  }

  /**
   * Kopieer de inhoud van 'invoer' naar 'uitvoer'.
   * 
   * @param invoer
   * @param uitvoer
   */
  private static void kopieerInhoud(BufferedReader invoer,
                                    BufferedWriter uitvoer) {
    String  lijn  = null;
    try {
      while ((lijn = invoer.readLine()) != null) {
        if (lijn.indexOf(INCLUDE_TAG) >= 0) {
          BufferedReader  include = new BufferedReader(
              new FileReader(sourceIncludes + File.separator
                             + lijn.substring(lijn.indexOf(INCLUDE_TAG)
                                               + INCLUDE_TAG.length(),
                                              lijn.indexOf(INCLUDE_ENDTAG))));
          kopieerInhoud(include, uitvoer);
        } else {
          uitvoer.write(lijn);
          uitvoer.newLine();
        }
      }
    } catch (Exception e) {
      System.out.println(e.getLocalizedMessage());
    }
  }

  /**
   * Open het sitmap.xml bestand.
   */
  private static void openSitemap() {
    nu.setTime(new Date());

    try {
      sitemap = Bestand.openUitvoerBestand(localSite + File.separator
                                          + "sitemap.xml");
      sitemap.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      sitemap.newLine();
      sitemap.write("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
      sitemap.newLine();
    } catch (BestandException e) {
      System.out.println(e.getLocalizedMessage());
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }

  /**
   * Synchroniseert de website met de 'bron'.
   * 
   * @param directory
   */
  private static void processWebSite(String directory) {
    String[]  content = new File(directory).list();  
    if (content != null) {
      for (int i = 0; i < content.length; i++) {
        try {
          File  item  = new File(directory + File.separator + content[i]);
          if (item.isDirectory()) {
            String  naam    =
              (directory + File.separator + content[i]).replace(sourcePages,
                                                                localSite);
            File    uitvoer = new File(naam);
            if (uitvoer.exists()) {
              if (uitvoer.isFile()) {
                try{
                  if (!uitvoer.delete()) {
                    System.out.println("Kan bestand " + naam
                                       + " niet verwijderen.");
                  }
                } catch (SecurityException e) {
                  System.out.println("Fout bij verwijderen van " + naam + " ["
                                     + e.getLocalizedMessage() + "].");
                }
                try{
                  if (!(new File(naam)).mkdir()) {
                    System.out.println("Kan directory " + naam
                        + " niet maken.");
                  }
                } catch (SecurityException e) {
                  System.out.println("Fout bij maken van " + naam + " ["
                                     + e.getLocalizedMessage() + "].");
                }
              }
            } else {
              try{
                if (!(new File(naam)).mkdir()) {
                  System.out.println("Kan directory " + naam
                      + " niet maken.");
                }
              } catch (SecurityException e) {
                System.out.println("Fout bij maken van " + naam + " ["
                                   + e.getLocalizedMessage() + "].");
              }
            }
            processWebSite(directory + File.separator + content[i]);
          } else if (isChanged(directory, content[i])) {
            System.out.println("Modified: " + directory + File.separator
                               + content[i]);
            BufferedReader  invoer  =
                Bestand.openInvoerBestand(directory + File.separator
                                          + content[i]);
            BufferedWriter  uitvoer =
                Bestand.openUitvoerBestand((directory + File.separator
                                            + content[i])
                                              .replace(sourcePages, localSite));
            kopieerInhoud(invoer, uitvoer);
            uitvoer.close();
            invoer.close();
          }
        } catch (BestandException e) {
          System.out.println(e.getLocalizedMessage());
        } catch (IOException e) {
          System.out.println(e.getLocalizedMessage());
        }
      }
    }
  }

  /**
   * Voeg een pagina toe aan de sitemap.xml
   * 
   * @param loc
   * @param lastmod
   */
  private static void schrijfSitemap(String loc, Date lastmod) {
    Calendar  file        = new GregorianCalendar();
    file.setTime(lastmod);
    // always, hourly, daily, weekly, monthly, yearly, never
    String    changefreq  = "weekly";
    int       priority    = 10;
    long      dagen       = (nu.getTimeInMillis() - file.getTimeInMillis())
                            / (24 * 60 * 60 * 1000);
    
    if (dagen <= 7) {
      changefreq  = "weekly";
      priority    = 10;
    } else if (dagen <= 31) {
      changefreq  = "monthly";
      priority    = 9;
    } else if (dagen <= 93) {
      changefreq  = "monthly";
      priority    = 8;
    } else if (dagen <= 183) {
      changefreq  = "monthly";
      priority    = 7;
    } else if (dagen <= 365) {
      changefreq  = "yearly";
      priority    = 6;
    } else {
      changefreq  = "never";
      priority    = 5;
    }

    try {
      sitemap.write("  <url>");
      sitemap.newLine();
      sitemap.write("    <loc>http://" + siteURL + "/" + loc + "</loc>");
      sitemap.newLine();
      sitemap.write("    <lastmod>" + Datum.fromDate(lastmod, "yyyy-MM-dd")
                    + "</lastmod>");
      sitemap.newLine();
      sitemap.write("    <changefreq>" + changefreq + "</changefreq>");
      sitemap.newLine();
      sitemap.write("    <priority>" + ((priority > 9) ? "1.0" : "0."
                    + priority) + "</priority>");
      sitemap.newLine();
      sitemap.write("  </url>");
      sitemap.newLine();
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    } catch (ParseException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }

  /**
   * Vult de includeModified met de laatste modificatie datum.
   * 
   * @param directory
   */
  private static void setIncludeModified(String directory) {
    String[]  content = new File(directory).list();
    if (content != null) {
      for (int i = 0; i < content.length; i++) {
        String  fileName  = directory + File.separator + content[i];
        File  file  = new File(fileName);
        if (file.isDirectory()) {
          setIncludeModified(fileName);
        } else {
          includes.put(fileName, file.lastModified());
        }
      }
    }
 }

  /**
   * Geeft de inhoud van een directory.
   * 
   * @param directory
   * @return
   */
  private static void showDirectoryContent(String directory) {
    String[]  content = new File(directory).list();
    if (content != null) {
      for (int i = 0; i < content.length; i++) {
        File  file  = new File(directory + File.separator + content[i]);
        String  datum = "";
        try {
          datum = Datum.fromDate(new Date(file.lastModified()),
                                 DoosConstants.DATUM_TIJD) + " - ";
        } catch (ParseException e) {
          System.out.println(e.getLocalizedMessage());
        }
        if (!"".equals(siteURL)
            && file.isFile()
            && content[i].endsWith(".html")) {
          schrijfSitemap((directory + File.separator + content[i])
                            .replace('\\', '/').replace(localSite + "/", ""),
                         new Date(file.lastModified()));
        }
        if (localOverview) {
          System.out.println(datum +  directory + File.separator + content[i]);
        }
        if (file.isDirectory()) {
          showDirectoryContent(directory + File.separator + content[i]);
        }
      }
    }
  }
}
