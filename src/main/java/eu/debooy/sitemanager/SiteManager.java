/**
 * Copyright 2008 Marco de Booy
 *
 * Licensed under the EUPL, Version 1.1 or – as soon they will be approved by
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

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Hashtable;
import java.util.Iterator;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.apache.commons.net.ftp.FTPClient;
import org.apache.commons.net.ftp.FTPFile;


/**
 * @author Marco de Booy
 */
public class SiteManager {
  private static  Boolean   force           = false;
  private static  Boolean   ftpOverview     = false;
  private static  Boolean   ftpSync         = false;
  private static  Boolean   localOverview   = false;
  private static  Boolean   sourceGenerate  = false;
  private static  FTPClient ftp             = new FTPClient();
  private static  String    ftpHome         = "";
  private static  String    ftpHost         = "";
  private static  String    ftpPasswd       = "";
  private static  String    ftpType         = "";
  private static  String    ftpUser         = "";
  private static  String    localSite       = "";
  private static  String    sourcePages     = "";
  private static  String    sourceIncludes  = "";
  private static  Hashtable<String, Long>
                            includes        = new Hashtable<String, Long>();

  /**
   * @param args
   */
  public static void main(String[] args) {
    Banner.printBanner("Site Manager");

    Arguments       arguments   = new Arguments(args);
    arguments.setParameters(new String[] {"force", "ftpHome", "ftpHost",
                                          "ftpOverview", "ftpPasswd", "ftpSync",
                                          "ftpType", "ftpUser", "localOverview",
                                          "localSite", "sourceGenerate",
                                          "sourceIncludes", "sourcePages"});
    if (!arguments.isValid()) {
      help();
      System.out.println();
      System.out.println("Verander de parameters en probeer opnieuw.");
      return;
    }

    processParameters(arguments);

    if (localOverview) {
      System.out.println("===== " + localSite + " =====");
      showDirectoryContent(localSite);
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
        e.printStackTrace();
      }
    }
    System.out.println("===== Klaar =====");
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
        if (file.isDirectory())
          deleteDirectory(directory + File.separator + content[i]);
        else
          file.delete();
      }
    }
    new File(directory).delete();
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
        if (ftpContent[i].isDirectory())
          deleteFTPDirectory(ftpContent[i].getName());
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
    System.out.println("  --force          [true|FALSE] De HOME directory van de website.");
    System.out.println("  --ftpHome                     De HOME directory van de website.");
    System.out.println("  --ftpHost                     De URL van de host van de website.");
    System.out.println("  --ftpOverview    [true|FALSE] Bestandslijst van de website?");
    System.out.println("  --ftpPasswd                   Password van de user voor het onderhoud van de website.");
    System.out.println("  --ftpSync        [true|FALSE] De website synchroniseren?");
    System.out.println("  --ftpType        [ACT|pas]    ACTive or PASsive FTP.");
    System.out.println("  --ftpUser                     De user voor het onderhoud van de website.");
    System.out.println("  --localOverview  [true|FALSE] Bestandslijst van de 'lokale' website?");
    System.out.println("  --localSite                   De directory van de 'lokale' website.");
    System.out.println("  --sourceGenerate [true|FALSE] De 'lokale' website genereren?");
    System.out.println("  --sourceIncludes              De directory van de 'includes'.");
    System.out.println("  --sourcePages                 De directory van de 'source' paginas.");
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
        if (lijn.indexOf("<include>") >= 0) {
          String  include = lijn.substring(lijn.indexOf("<include>") + 9,
                                           lijn.indexOf("</include>"));
          if (includes.get(sourceIncludes + File.separator
                           + include) > modified) {
            invoer.close();
            return true;
          }
        }
      }
      invoer.close();
    } catch(IOException ex) {
      if (null != invoer)
      ex.printStackTrace();
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
    if (file.exists())
      if (file.isFile()) {
        return (file.lastModified() < sourceModified
                || isChanged((directory + File.separator + content),
                             file.lastModified()));
      } else {
        deleteDirectory((directory + File.separator+ content)
            .replace(sourcePages, localSite));
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
        if (lijn.indexOf("<include>") >= 0) {
          BufferedReader  include = new BufferedReader(
              new FileReader(sourceIncludes + File.separator
                             + lijn.substring(lijn.indexOf("<include>") + 9,
                                              lijn.indexOf("</include>"))));
          kopieerInhoud(include, uitvoer);
        } else {
          uitvoer.write(lijn);
          uitvoer.newLine();
        }
      }
    } catch (Exception e) {
      e.printStackTrace();
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
              (directory + File.separator+ content[i]).replace(sourcePages,
                                                               localSite);
            File    uitvoer = new File(naam);
            if (uitvoer.exists()) {
              if (uitvoer.isFile()) {
                uitvoer.delete();
                (new File(naam)).mkdir();
              }
            } else {
              (new File(naam)).mkdir();
            }
            processWebSite(directory + File.separator + content[i]);
          } else if (isChanged(directory, content[i])) {
            System.out.println("Modified: "+ directory + File.separator
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
          re.printStackTrace();
        } catch (Exception e) {
          e.printStackTrace();
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
        if (file.isDirectory())
          setIncludeModified(fileName);
        else
          includes.put(fileName, file.lastModified());
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
        System.out.println(directory + File.separator + content[i]);
        if (file.isDirectory())
          showDirectoryContent(directory + File.separator + content[i]);
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
          if (content[i].isDirectory())
            showFTPDirectoryContent(content[i].getName());
        }
      }
      ftp.changeWorkingDirectory("..");
    } catch (IOException e) {
      e.printStackTrace();
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
    String    ftpDir      =
      directory.substring(directory.lastIndexOf(File.separator)+1);
    if (localSite.equals(directory))
      ftpDir  = "";
    ftp.changeWorkingDirectory(ftpDir);
    System.out.println("FTP Directory: " + ftp.printWorkingDirectory());
    FTPFile[] ftpContent  = ftp.listFiles();
    String[]  locContent  = new File(directory).list();
    TreeMap<String, FTPFile> 
              ftpFiles    = new TreeMap<String, FTPFile>();
    TreeMap<String, String>
              locFiles    = new TreeMap<String, String>();

    for (int i = 0; i < ftpContent.length; i++)
      ftpFiles.put(ftpContent[i].getName(), ftpContent[i]);
    for (int i = 0; i < locContent.length; i++)
      locFiles.put(locContent[i], locContent[i]);

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
              System.out.println("Vervang  : " + ftp.printWorkingDirectory()
                                 + "/" + locFile);
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
