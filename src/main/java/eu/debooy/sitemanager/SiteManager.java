/**
 * Copyright 2008 Marco de Booij
 *
 * Licensed under the EUPL, Version 1.1 or - as soon they will be approved by
 * the European Commission - subsequent versions of the EUPL (the "Licence");
 * you may not use this work except in compliance with the Licence. You may
 * obtain a copy of the Licence at:
 *
 * http://ec.europa.eu/idabc/eupl
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.text.ParseException;
import java.util.Calendar;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;


/**
 * @author Marco de Booij
 */
public class SiteManager {
  private static  Boolean         force           = false;
  private static  Boolean         ftpOverview     = false;
  private static  Boolean         ftpSync         = false;
  private static  Boolean         localOverview   = false;
  private static  Boolean         sourceGenerate  = false;
  private static  BufferedWriter  sitemap         = null;
  private static  Calendar        nu              = new GregorianCalendar();
  private static  FTPClient       ftp             = new FTPClient();
  private static  String          ftpHome         = "";
  private static  String          ftpHost         = "";
  private static  String          ftpPasswd       = "";
  private static  String          ftpType         = "";
  private static  String          ftpUser         = "";
  private static  String          localSite       = "";
  private static  String          siteURL         = "";
  private static  String          sourcePages     = "";
  private static  String          sourceIncludes  = "";
  private static  Map<String, Long>
                                  includes        = new HashMap<String, Long>();

  private final static  String  INCLUDE_TAG     = "<include>";
  private final static  String  INCLUDE_ENDTAG  = "</include>";

  private SiteManager() {}

  /**
   * @param args
   */
  public static void main(String[] args) {
    if (args.length == 0) {
      Banner.printBanner("Site Manager");
      help();
      return;
    }
    String    commando      = args[0];

    String[]  commandoArgs  = new String[args.length-1];
    System.arraycopy(args, 1, commandoArgs, 0, args.length-1);

    if ("generaterss".equalsIgnoreCase(commando)) {
      GenerateRss.execute(commandoArgs);
      return;
    }
    if ("generatesite".equalsIgnoreCase(commando)) {
      GenerateSite.execute(commandoArgs);
      return;
    }
    if ("synchronisesite".equalsIgnoreCase(commando)) {
      SynchroniseSite.execute(commandoArgs);
      return;
    }

    Banner.printBanner("Site Manager");

    Arguments       arguments   = new Arguments(args);
    arguments.setParameters(new String[] {"force", "ftpHome", "ftpHost",
                                          "ftpOverview", "ftpPasswd", "ftpSync",
                                          "ftpType", "ftpUser", "localOverview",
                                          "localSite", "siteURL",
                                          "sourceGenerate", "sourceIncludes",
                                          "sourcePages"});
    if (!arguments.isValid()) {
      help();
      System.out.println();
      System.out.println("Verander de parameters en probeer opnieuw.");
      return;
    }

    processParameters(arguments);

    if (localOverview) {
      System.out.println("===== " + localSite + " =====");
      openSitemap();
      showDirectoryContent(localSite);
      closeSitemap();
      System.out.println();
    }
    
    if (sourceGenerate) {
      System.out.println("===== Genereer =====");
      setIncludeModified(sourceIncludes);
      processWebSite(sourcePages);
      System.out.println();
    }

    if (ftpOverview || ftpSync) {
      try {
        ftp.connect(ftpHost);
        if ("pas".equalsIgnoreCase(ftpType)) {
          ftp.enterLocalPassiveMode();
        }
        ftp.login(ftpUser, ftpPasswd);
        ftp.setFileType(FTPClient.BINARY_FILE_TYPE);

        if (ftpOverview) {
          System.out.println("===== " + ftpHome + " =====");
          showFTPDirectoryContent(ftpHome);
          System.out.println();
        }

        if (ftpSync) {
          System.out.println("===== Synchronise =====");
          ftp.changeWorkingDirectory(ftpHome);
          synchroniseDirectory(localSite);
          System.out.println();
        }

        ftp.logout();
        ftp.disconnect();
      } catch (Exception e) {
        System.err.println(e.getLocalizedMessage());
      }
    }
    System.out.println("===== Klaar =====");
  }

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

  /**
   * Delete een directory leeg om die te kunnen verwijderen.
   * 
   * @param directory
   * @throws IOException 
   */
  private static void deleteFTPDirectory(String directory) throws IOException {
    ftp.changeWorkingDirectory(directory);
    FTPFile[] ftpContent  = ftp.listFiles();
    if (ftpContent != null) {
      for (int i = 0; i < ftpContent.length; i++) {
        if (ftpContent[i].isDirectory()) {
          deleteFTPDirectory(ftpContent[i].getName());
        }
        ftp.deleteFile(ftpContent[i].getName());
      }
    }

    ftp.changeWorkingDirectory("..");
    ftp.removeDirectory(directory);
  }

  /**
   * Geeft de 'help' pagina.
   */
  private static void help() {
    System.out.println("java -jar SiteManager.jar [OPTIE...]");
    System.out.println();
    System.out.println("  GenerateRSS       Genereer een RSS feed.");
    System.out.println("  GenerateSite      Genereer de Website in een lokale directory.");
    System.out.println("  SynchroniseSite   Synchroniseert de Website met de lokale directory.");
    System.out.println();
    System.out.println("  --force          [true|FALSE] Altijd kopiëren naar de website.");
    System.out.println("  --ftpHome                     De HOME directory van de website.");
    System.out.println("  --ftpHost                     De URL van de host van de website.");
    System.out.println("  --ftpOverview    [true|FALSE] Bestandslijst van de website.");
    System.out.println("  --ftpPasswd                   Password van de user voor het onderhoud van de website.");
    System.out.println("  --ftpSync        [true|FALSE] De website synchroniseren?");
    System.out.println("  --ftpType        [ACT|pas]    ACTive or PASsive FTP.");
    System.out.println("  --ftpUser                     De user voor het onderhoud van de website.");
    System.out.println("  --localOverview  [true|FALSE] Bestandslijst van de 'lokale' website?");
    System.out.println("  --localSite                   De directory van de 'lokale' website.");
    System.out.println("  --siteURL                     De 'home' URL van de website (zonder http://).");
    System.out.println("  --sourceGenerate [true|FALSE] De 'lokale' website genereren?");
    System.out.println("  --sourceIncludes              De directory van de 'includes'.");
    System.out.println("  --sourcePages                 De directory van de 'source' paginas.");
    System.out.println();
    GenerateRss.help();
    System.out.println();
    GenerateSite.help();
    System.out.println();
    SynchroniseSite.help();
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
    } catch(IOException e) {
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
      sitemap = new BufferedWriter(
          new FileWriter((localSite + File.separator + "sitemap.xml")));
      sitemap.write("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
      sitemap.newLine();
      sitemap.write("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
      sitemap.newLine();
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }

  /**
   * Zet de parameters in de variabelen.
   */
  private static void processParameters(Arguments arguments) {
    if (arguments.hasArgument("force")) {
      force           =
        Boolean.parseBoolean(arguments.getArgument("force"));
    }
    if (arguments.hasArgument("ftpOverview")) {
      ftpOverview     =
        Boolean.parseBoolean(arguments.getArgument("ftpOverview"));
    }
    if (arguments.hasArgument("ftpSync")) {
      ftpSync         =
        Boolean.parseBoolean(arguments.getArgument("ftpSync"));
    }
    if (arguments.hasArgument("localOverview")) {
      localOverview   =
        Boolean.parseBoolean(arguments.getArgument("localOverview"));
    }
    if (arguments.hasArgument("sourceGenerate")) {
      sourceGenerate  =
        Boolean.parseBoolean(arguments.getArgument("sourceGenerate"));
    }
    if (arguments.hasArgument("ftpHome")) {
      ftpHome         = arguments.getArgument("ftpHome");
    }
    if (arguments.hasArgument("ftpHost")) {
      ftpHost         = arguments.getArgument("ftpHost");
    }
    if (arguments.hasArgument("ftpPasswd")) {
      ftpPasswd       = arguments.getArgument("ftpPasswd");
    }
    if (arguments.hasArgument("ftpType")) {
      ftpType         = arguments.getArgument("ftpType");
    }
    if (arguments.hasArgument("ftpUser")) {
      ftpUser         = arguments.getArgument("ftpUser");
    }
    if (arguments.hasArgument("localSite")) {
      localSite       = arguments.getArgument("localSite");
    }
    if (arguments.hasArgument("siteURL")) {
      siteURL         = arguments.getArgument("siteURL");
    }
    if (arguments.hasArgument("sourcePages")) {
      sourcePages     = arguments.getArgument("sourcePages");
    }
    if (arguments.hasArgument("sourceIncludes")) {
      sourceIncludes  = arguments.getArgument("sourceIncludes");
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
            new BufferedReader(
                new FileReader(directory + File.separator + content[i]));
            BufferedWriter  uitvoer =
              new BufferedWriter(
                  new FileWriter((directory + File.separator+ content[i])
                      .replace(sourcePages, localSite)));
            kopieerInhoud(invoer, uitvoer);
            uitvoer.close();
            invoer.close();
          }
        } catch (RuntimeException re) {
          System.out.println(re.getLocalizedMessage());
        } catch (Exception e) {
          System.out.println(e.getLocalizedMessage());
        }
      }
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
                                 "dd/MM/yyyy HH:mm:ss.SSS") + " - ";
          if (!"".equals(siteURL)
              && file.isFile()
              && content[i].endsWith(".html")) {
            schrijfSitemap((directory + File.separator + content[i])
                              .replace(localSite + "\\", "").replace('\\', '/'),
                           new Date(file.lastModified()));
          }
        } catch (ParseException e) {
          System.out.println(e.getLocalizedMessage());
        }
        System.out.println(datum +  directory + File.separator + content[i]);
        if (file.isDirectory()) {
          showDirectoryContent(directory + File.separator + content[i]);
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
  private static void showFTPDirectoryContent(String directory) {
    try {
      ftp.changeWorkingDirectory(directory);
      String  pwd = ftp.printWorkingDirectory();
      FTPFile[] content = ftp.listFiles();
      if (content != null) {
        for (int i = 0; i < content.length; i++) {
          System.out.println(pwd + "/" + content[i].getName());
          if (content[i].isDirectory()) {
            showFTPDirectoryContent(content[i].getName());
          }
        }
      }
      ftp.changeWorkingDirectory("..");
    } catch (IOException e) {
      System.out.println(e.getLocalizedMessage());
    }
  }
  /**
   * Synchronise the WebSite with the local copy.
   * 
   * @param directory
   * @throws IOException
   */
  private static void synchroniseDirectory(String directory)
      throws IOException {
    String  ftpDir  =
      directory.substring(directory.lastIndexOf(File.separator)+1);
    if (localSite.equals(directory)) {
      ftpDir  = "";
    }
    ftp.changeWorkingDirectory(ftpDir);
    System.out.println("FTP Directory: " + ftp.printWorkingDirectory());
    FTPFile[] ftpContent  = ftp.listFiles();
    String[]  locContent  = new File(directory).list();
    TreeMap<String, FTPFile> 
              ftpFiles    = new TreeMap<String, FTPFile>();
    TreeMap<String, String>
              locFiles    = new TreeMap<String, String>();

    for (int i = 0; i < ftpContent.length; i++) {
      ftpFiles.put(ftpContent[i].getName(), ftpContent[i]);
    }
    for (int i = 0; i < locContent.length; i++) {
      locFiles.put(locContent[i], locContent[i]);
    }

    Iterator<Entry<String, FTPFile>> ftpIter = ftpFiles.entrySet().iterator();
    Iterator<Entry<String, String>>  locIter = locFiles.entrySet().iterator();
    String            ftpFile = "{";
    FTPFile           ftpItem = null;
    String            locFile = "{";
    File              locItem = null;
    if (ftpIter.hasNext()) {
      ftpFile = ftpIter.next().getKey();
      ftpItem = ftpFiles.get(ftpFile);
    }
    if (locIter.hasNext()) {
      locFile = locIter.next().getKey();
      locItem = new File(directory + File.separator + locFile);
    }
    while (!"{".equals(ftpFile)
        || !"{".equals(locFile)) {
      // Bestaat aan beide kanten.
      if (ftpFile.equals(locFile)) {
        if (locItem.isDirectory()
            && ftpItem.isDirectory()) {
          synchroniseDirectory(directory + File.separator + locFile);
        } else {
          if (locItem.isFile()
              && ftpItem.isFile()) {
            if (locItem.lastModified() > ftpItem.getTimestamp()
                                                .getTimeInMillis()
                || force) {
              String  datum = "";
              try {
                datum = Datum.fromDate(new Date(locItem.lastModified()),
                                       "dd/MM/yyyy HH:mm:ss.SSS") + " - "
                        + Datum.fromDate(new Date(ftpItem.getTimestamp()
                                                         .getTimeInMillis()),
                                       "dd/MM/yyyy HH:mm:ss.SSS");
              } catch (ParseException e) {
                System.out.println(e.getLocalizedMessage());
              }
              System.out.println("Vervang  : " + ftp.printWorkingDirectory()
                                 + "/" + locFile + " [" +  datum + "]");
              ftp.storeFile(locFile, new FileInputStream(directory
                                                         + File.separator
                                                         + locFile));
            }
          } else {
            System.out.println("Vervang  : " + ftp.printWorkingDirectory()
                               + "/" + locFile);
            if (locItem.isFile()) {
              deleteFTPDirectory(ftpFile);
              ftp.storeFile(locFile, new FileInputStream(directory
                                                         + File.separator
                                                         + locFile));
            } else {
              ftp.deleteFile(ftpFile);
              ftp.makeDirectory(locFile);
              synchroniseDirectory(directory + File.separator + locFile);
            }
          }
        }
        if (ftpIter.hasNext()) {
          ftpFile = ftpIter.next().getKey();
          ftpItem = ftpFiles.get(ftpFile);
        } else {
          ftpFile = "{";
          ftpItem = null;
        }
        if (locIter.hasNext()) {
          locFile = locIter.next().getKey();
          locItem = new File(directory + File.separator + locFile);
        } else {
          locFile = "{";
          locItem = null;
        }
      } else {
        // Enkel op WebSite.
        if (ftpFile.compareTo(locFile) < 0) {
          System.out.println("Verwijder: " + ftp.printWorkingDirectory()
                             + "/" + ftpFile);
          if (ftpItem.isFile()) {
            ftp.deleteFile(ftpFile);
          } else {
            deleteFTPDirectory(ftpFile);
          }
          if (ftpIter.hasNext()) {
            ftpFile = ftpIter.next().getKey();
            ftpItem = ftpFiles.get(ftpFile);
          } else {
            ftpFile = "{";
            ftpItem = null;
          }
        } else {
          // Nog niet op WebSite.
          if (ftpFile.compareTo(locFile) > 0) {
            System.out.println("Kopieer  : " + ftp.printWorkingDirectory()
                               + "/" + locFile);
            if (locItem.isFile()) {
              ftp.storeFile(locFile, new FileInputStream(directory
                                                         + File.separator
                                                         + locFile));
            } else {
              ftp.makeDirectory(locFile);
              synchroniseDirectory(directory + File.separator + locFile);
            }
            if (locIter.hasNext()) {
              locFile = locIter.next().getKey();
              locItem = new File(directory + File.separator + locFile);
            } else {
              locFile = "{";
              locItem = null;
            }
          }
        }
      }
    }
    ftp.changeWorkingDirectory("..");
  }
}
