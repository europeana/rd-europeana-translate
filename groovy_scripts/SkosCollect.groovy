// go over all the all.json and collect lang pairs with en
// write lang pair raw files, with 
// { en: lit, other:lit, id:url },...
// have a compact and filter part

@Grapes([
        @Grab(group='org.apache.commons', module='commons-compress', version='1.21')
])

import java.util.concurrent.atomic.*
import java.util.*
import java.util.stream.*
import groovy.json.*
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;


File processedDir = new File("." )
OpenFiles of = new OpenFiles();
AtomicInteger fileCounter = new AtomicInteger( 0 )
AtomicInteger active = new AtomicInteger(0 )
processedDir.listFiles()
 .stream()
 .filter{ tgzFile -> 
    String name = tgzFile.getName()
    return ( name.matches( ".*\\.json\\.tgz\$"))
 }
  .parallel()
  .forEach{ f ->
    int fileNo = fileCounter.incrementAndGet()
    active.incrementAndGet()
    println( "Processing $fileNo: ${f.name}")
     Map skos = getSkos( f )
     skos.each{ k,v ->
         of.addSkos( k, v )
     }
    println( "Done $fileNo: ${f.name}    ${active.decrementAndGet()} active.")
  }

of.close();


class OpenFiles {
    // an opne file per other lang 
    Map<String, OpenFile> inProcessFiles = [:]

    public void addSkos( String url, Map skosEntry ) {
        if( !skosEntry.containsKey( "en")) return;
        String englishLit = skosEntry.get( "en")
        for( String lang in skosEntry.keySet()) {
           if( lang == "en" ) continue
           addSection( englishLit, lang, skosEntry.get( lang ), url )
        }        
    }

    public void addSection( String englishLit, String lang, String otherLit, String url ) {
         def openFile
        synchronized( inProcessFiles ) {
            openFile = inProcessFiles.computeIfAbsent( lang,{ k-> new OpenFile(k)})
        }
        openFile.addLiteral( englishLit, otherLit, url )
    }

    public void close() {

        inProcessFiles.each{ k,v -> 
            println( "Closing $k")
            v.close()
        }
    }
}



class OpenFile {
    // start a json output file
    String otherLang;
    String filename

    File outputFile
    OutputStream os;
    Writer w;
    boolean firstEntry = true;

    def jsonPrinter = getJsonPrinter()
    static def getJsonPrinter( ) {
        return new JsonGenerator.Options()
        .excludeNulls()
        .disableUnicodeEscaping()
        .build()
    }

    public OpenFile( String lang ) {
        filename = "Skos_raw_en_"+lang+".json.gz"
        otherLang = lang
        def fos = new File( filename).newOutputStream()
        os = new GzipCompressorOutputStream(fos);
        w = os.newWriter( "UTF-8")
        w.println( "[")
        w.flush()
    }

    // we trust the other lang is the one from this OpenFile
    public synchronized void addLiteral( String englishLiteral, String otherLiteral, String url ) {
        if( !firstEntry ) w.println( ",")
       w.print( jsonPrinter.toJson( [ en:englishLiteral, (otherLang):otherLiteral, url:url]))
       w.flush()
       firstEntry = false
    }

    public void close() {
        // finish the array
        w.println( "\n]")
        w.flush()
        w.close()
        os.close()
    }
}


// get the Skos hashmap from the tgz
// key is URL, val is another Map lang-> lit
def getSkos( File tgzArchive ) {
    String basename = tgzArchive.name.replaceAll( ".json.tgz","")
    def jsonSlurper = new JsonSlurper( type: JsonParserType.CHAR_BUFFER)
    def buffer = new  ByteArrayOutputStream()
    try {
        [ "tar", "xzOf", tgzArchive.absolutePath, basename+"/all.json"].execute()
        .pipeTo( ["jq", ".allSkosEntries"].execute())
        .waitForProcessOutput( buffer, System.err )
        return jsonSlurper.parseText( buffer.toString( "UTF-8"))
    } catch( Exception e ) {
        e.printStackTrace()
    }
    return [:]
}
