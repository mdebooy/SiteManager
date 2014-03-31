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
import eu.debooy.doosutils.DoosUtils;
import eu.debooy.doosutils.access.Bestand;
import eu.debooy.doosutils.exception.BestandException;

import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.charset.Charset;
import java.text.DateFormat;
import java.text.MessageFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.ResourceBundle;
import java.util.TimeZone;


/**
 * @author Marco de Booij
 */
public final class GenerateRss {
  private static  ResourceBundle  resourceBundle  =
      ResourceBundle.getBundle("ApplicatieResources", Locale.getDefault());

  private GenerateRss(){}

  public static void execute(String[] args) {
    String          charsetIn   = Charset.defaultCharset().name();
    String          charsetUit  = Charset.defaultCharset().name();
    BufferedWriter  output      = null;
    DateFormat      gmtDatum    =
        new SimpleDateFormat("EEE, dd MMM yyyy HH:mm:ss z", Locale.UK);

    gmtDatum.setTimeZone(TimeZone.getTimeZone("GMT"));
    Banner.printBanner(resourceBundle.getString("banner.genereer.rss"));

    Arguments arguments = new Arguments(args);
    arguments.setParameters(new String[] {"charsetin", "charsetuit", "inhoud"});
    arguments.setVerplicht(new String[] {"inhoud"});
    if (!arguments.isValid()) {
      help();
      return;
    }

    // Zet de parameters.
    if (arguments.hasArgument("charsetin")) {
      charsetIn   = arguments.getArgument("charsetin");
    }
    if (arguments.hasArgument("charsetuit")) {
      charsetUit  = arguments.getArgument("charsetuit");
    }
    String      inhoud      = arguments.getArgument("inhoud");
    try {
      File        bestand     = null;
      Properties  properties  = new Properties();
      properties.load(Bestand.openInvoerBestand(inhoud, charsetIn));

      List<String>  fouten  = new ArrayList<String>();
      if (!properties.containsKey("home.dir")) {
        fouten.add(
            MessageFormat.format(
                resourceBundle.getString("error.parameter.afwezig"),
                "home.dir"));
      }
      if (!properties.containsKey("home.url")) {
        fouten.add(
            MessageFormat.format(
                resourceBundle.getString("error.parameter.afwezig"),
                "home.url"));
      }
      if (!properties.containsKey("rss.name")) {
        fouten.add(
            MessageFormat.format(
                resourceBundle.getString("error.parameter.afwezig"),
                "rss.name"));
      }
      // Kan geen RSS bestand maken.
      if (!fouten.isEmpty()) {
        for (String fout: fouten) {
          DoosUtils.foutNaarScherm(fout);
        }
        return;
      }

      String      homeDir     = properties.getProperty("home.dir");
      String      homeUrl     = properties.getProperty("home.url");
      String      rssName     = properties.getProperty("rss.name");
      File        rss         = new File(homeDir + File.separator + rssName);
      output  = Bestand.openUitvoerBestand(rss, charsetUit);
      Map<String, String> params  = new HashMap<String, String>();
      if (properties.containsKey("channel.title")) {
        params.put("title", properties.getProperty("channel.title"));
      } else {
        fouten.add(MessageFormat.format(
            resourceBundle.getString("error.parameter.afwezig"),
            "channel.title"));
      }
      if (properties.containsKey("channel.description")) {
        params.put("description",
                   properties.getProperty("channel.description"));
      } else {
        fouten.add(MessageFormat.format(
            resourceBundle.getString("error.parameter.afwezig"),
            "channel.description"));
      }
      // Kan geen header maken.
      if (!fouten.isEmpty()) {
        for (String fout: fouten) {
          DoosUtils.foutNaarScherm(fout);
        }
        return;
      }

      if (properties.containsKey("channel.image")) {
        params.put("image", properties.getProperty("channel.image"));
      }
      params.put("language",
          properties.getProperty("channel.language",
                                 Locale.getDefault().getLanguage()));
      params.put("link", homeUrl);
      if (properties.containsKey("rss.stylesheet")) {
        params.put("stylesheet", properties.getProperty("rss.stylesheet"));
      }
      params.put("pubDate", gmtDatum.format(new Date()));
      params.put("rssBestand", homeUrl + "/" + rssName);
      header(output, params);

      int item          = 1;
      String  itemNaam  = "";
      while (properties.containsKey("item" + item + ".title")) {
        boolean correct = true;
        params          = new HashMap<String, String>();
        params.put("title", properties.getProperty("item" + item + ".title"));
        if (properties.containsKey("item" + item + ".description")) {
          params.put("description",
                     properties.getProperty("item" + item + ".description"));
        } else {
          DoosUtils.foutNaarScherm(
              MessageFormat.format(
                  resourceBundle.getString("error.omschrijving.afwezig"),
                  item));
          correct = false;
        }
        if (properties.containsKey("item" + item + ".link")) {
          itemNaam  = properties.getProperty("item" + item + ".link");
          params.put("link", homeUrl + "/" + itemNaam);
        } else {
          DoosUtils.foutNaarScherm(
              MessageFormat.format(
                  resourceBundle.getString("error.link.afwezig"), item));
          correct = false;
        }
        // Kan item maken?
        if (correct) {
          bestand = new File(homeDir + File.separator + itemNaam);
          params.put("pubDate",
                     gmtDatum.format(new Date(bestand.lastModified())));
          item(output, params);
        }
        item++;
      }

      footer(output);
      DoosUtils.naarScherm(resourceBundle.getString("label.bestand") + homeDir
                           + File.separator + rssName);
      DoosUtils.naarScherm(resourceBundle.getString("label.items")
                           + (item - 1));
    } catch (BestandException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    } catch (IOException e) {
      DoosUtils.foutNaarScherm(e.getLocalizedMessage());
    } finally {
      try {
        if (output != null) {
          output.close();
        }
      } catch (IOException e) {
        DoosUtils.foutNaarScherm(e.getLocalizedMessage());
      }
    }
    DoosUtils.naarScherm(resourceBundle.getString("label.klaar"));
  }

  /**
   * Voeg de footer toe aan de RSS feed.
   * 
   * @param output
   * @throws IOException
   */
  protected static void footer(BufferedWriter output) throws IOException {
    output.write("  </channel>");
    output.newLine();
    output.write("</rss>");
    output.newLine();
  }

  /**
   * Voeg de header to aan de RSS feed.
   * 
   * @param output
   * @param params
   * @throws IOException
   */
  protected static void header(BufferedWriter output,
                               Map<String, String> params) throws IOException {
    output.write("<?xml version=\"1.0\" encoding=\"utf-8\"?>");
    output.newLine();
    if (params.containsKey("stylesheet")) {
      output.write("<?xml-stylesheet type=\"text/css\" href=\""
                   + params.get("stylesheet") + "\"?>");
      output.newLine();
    }
    output.write("<rss version=\"2.0\" xmlns:atom=\"http://www.w3.org/2005/Atom\">");
    output.newLine();
    output.write("  <channel>");
    output.newLine();
    output.write("    <title>" + params.get("title") + "</title>");
    output.newLine();
    output.write("    <description>" + params.get("description")
                 + "</description>");
    output.newLine();
    output.write("    <link>"+ params.get("link") + "</link>");
    output.newLine();
    output.write("    <pubDate>" + params.get("pubDate") + "</pubDate>");
    output.newLine();
    output.write("    <lastBuildDate>" + params.get("pubDate")
                 + "</lastBuildDate>");
    output.newLine();
    output.write("    <language>" + params.get("language") + "</language>");
    output.newLine();
    output.write("    <atom:link href=\"" + params.get("rssBestand")
                 + "\" rel=\"self\" type=\"application/rss+xml\" />");
    output.newLine();
    if (params.containsKey("image")) {
      output.write("    <image>");
      output.newLine();
      output.write("      <title>" + params.get("title") + "</title>");
      output.newLine();
      output.write("      <url>" + params.get("image") + "</url>");
      output.newLine();
      output.write("      <link>" + params.get("link") + "</link>");
      output.newLine();
      output.write("    </image>");
      output.newLine();
    }
  }

  /**
   * Geeft de 'help' pagina.
   */
  protected static void help() {
    DoosUtils.naarScherm("java -jar SiteManager.jar GenerateRSS --inhoud=<"
                         + resourceBundle.getString("label.properties.bestand")
                         + ">");
    DoosUtils.naarScherm("");
    DoosUtils.naarScherm("  --charsetin   ",
        MessageFormat.format(resourceBundle.getString("help.charsetin"),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm("  --charsetuit  ",
        MessageFormat.format(resourceBundle.getString("help.charsetuit"),
                             Charset.defaultCharset().name()), 80);
    DoosUtils.naarScherm("  --inhoud      ",
                         resourceBundle.getString("help.inhoud"), 80);
    DoosUtils.naarScherm("");
    DoosUtils.naarScherm(
        MessageFormat.format(resourceBundle.getString("help.paramverplicht"),
                             "inhoud"), 80);
    DoosUtils.naarScherm("");
  }

  /**
   * Voeg een item toe aan de RSS feed.
   * 
   * @param output
   * @param params
   * @throws IOException
   */
  protected static void item(BufferedWriter output,
                             Map<String, String> params) throws IOException {
    output.write("  <item>");
    output.newLine();
    output.write("    <title>" + params.get("title") + "</title>");
    output.newLine();
    output.write("    <description>" + params.get("description")
                 + "</description>");
    output.newLine();
    output.write("    <link>" + params.get("link") + "</link>");
    output.newLine();
    output.write("    <guid>" + params.get("link") + "</guid>");
    output.newLine();
    output.write("    <pubDate>" + params.get("pubDate") + "</pubDate>");
    output.newLine();
    output.write("  </item>");
    output.newLine();
  }
}
