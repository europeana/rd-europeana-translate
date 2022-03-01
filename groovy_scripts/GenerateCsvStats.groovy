// read filenames from input stream and 
// stream the tarball entries into jsonSlurper
// extract counts members and sources
// write a csv out

@Grapes([
        @Grab(group='org.apache.commons', module='commons-compress', version='1.21'),
         @Grab(group='org.apache.commons', module='commons-csv', version='1.9.0')
])

import groovy.json.*
import org.apache.commons.compress.archivers.ArchiveEntry;
import java.util.*
import java.util.concurrent.*
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import java.nio.charset.StandardCharsets;
import org.apache.commons.compress.archivers.tar.TarArchiveInputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;
import org.apache.commons.csv.CSVPrinter
import org.apache.commons.csv.CSVFormat

def allStats = [] as LinkedBlockingQueue
// def allStats = [:] as ConcurrentHashMap
if( args.length > 0 ) {
    // tarball names are args
    for( String filename: args ) {
        allStats.add( extractStats( filename ))
    }
} else {
    // all tgz files in current dir
    def cwd = new File( ".ignore").getAbsoluteFile().getParentFile()
    cwd.listFiles()
    .stream()
    .filter{ file -> 
        file.name.endsWith(  ".json.tgz")
    }
     .parallel()
    .forEach{ tgzFile -> 
        String name = tgzFile.getName()
        if( !name.endsWith( ".json.tgz")) return
        System.out.println( "$name")
        System.out.flush()
        allStats.add( extractStatsJq( tgzFile ))
        // some feedback
    }
}

/*
allStats.collect{ e -> e }
.sort{ -it.value }
.each{ println( "$it.key - $it.value")}
*/

System.out.println( "Writing Output file 'stats.csv'")
def listOfLists = makeTable( allStats )
File csvOut = new File( "stats.csv");
csvOut.withPrintWriter(  "UTF-8", { writer ->
    def printer = new CSVPrinter( writer, CSVFormat.DEFAULT )
    printer.printRecords( listOfLists )
    printer.flush()
} )

// provide a jsonPrinter
def getJsonPrinter( ) {
    return new JsonGenerator.Options()
    .excludeNulls()
    .disableUnicodeEscaping()
    .build()
}

def makeTable( Collection stats ) {
    def monoHeadersSet = [] as Set
    def biHeadersSet = [] as Set
    def otherHeaders = [ "CollectionId", "Source", "Count" ]
    // extract headers 
    for( coll in stats ) {
        if( coll.mono != null )
            monoHeadersSet.addAll( coll.mono.keySet())
        if(coll.bi != null )  
            biHeadersSet.addAll( coll.bi.keySet())
    }
    def monoHeaders = monoHeadersSet as List
    def biHeaders = biHeadersSet as List
    def row = otherHeaders + monoHeaders + biHeaders 
    def rows = [row ] as List
    System.out.println( "Headers extracted")
    for( def coll :stats ) {
        def stat = []
        stat.add( coll.collection )
        stat.addAll( 
           // extractAggProvCount( coll.source )
           aggProvCount( coll.source)
        )
        for( String mono: monoHeaders ) {
            if( (coll.mono!= null) &&  coll.mono.containsKey( mono ))
                stat.add( coll.mono[mono])
            else
                stat.add( "" ) 
        }

        for( String biKey: biHeaders ) {
            if((coll.bi != null ) && coll.bi.containsKey( biKey ))
                stat.add( coll.bi[biKey])
            else
                stat.add( "" ) 
        }
        rows.add( stat )
    }
    return rows
}

List extractAggProvCount( Map allProviders ) {
    def res
    def count = 0
    // there should be only one provider, assume that for now
    // if there is many, the last one goes in, count is sum
    for( def e: allProviders.entrySet()) {
        count += e.getValue()

        res = []
        String[] aggProv = e.getKey().split( "\n")
        res.add( aggProv[0].substring( 12 )) //Aggregator: xx
        res.add( aggProv[1].substring( 10 )) // Provider: xx
        res.add( count )
    }
    return res 
}

String[] aggProvCount( Map allProviders ) {
    int count = 0
    String aggProv =""
    for( def e: allProviders.entrySet()) {
        if( aggProv.size() >0 ) aggProv+= "\n"
        count += e.getValue()

        aggProv += e.getKey().replaceAll( "\n", "   ")
        aggProv += "  Count:" + e.getValue()
    }
    return [aggProv,""+count] as String[]
}

Map extractStats(String fileName ) {
    // un-tarball
    Map allStats = [:]
    InputStream is, bis, gzi
    TarArchiveInputStream tis
    try {
        is = new File( fileName ).newInputStream()
         bis = new BufferedInputStream( is )
        gzi = new GzipCompressorInputStream( bis )
         tis = new TarArchiveInputStream(gzi)

        TarArchiveEntry entry = null;
        // collectionId is digits in the filename
        def collId = (fileName =~ /([^\/]+)/)[0][1]
        // Map res = [collection:collId]
        Map res = [:]

        while ((entry = tis.getNextTarEntry()) != null) {
            //println( entry.getName())
            //continue
            if (entry.isDirectory()) continue;
            def buffer = new byte[entry.getSize()]
            tis.read( buffer, 0, buffer.length )
            def bais = new ByteArrayInputStream( buffer )
            String entryName= entry.getName()
            entryName = entryName.substring( entryName.lastIndexOf( "/") + 1 )
            // System.out.println( entryName  )
            switch( entryName ) {
                case "all.json": 
                // res += [source:extractFieldStream( tis, "sources")]
                res += [ (collId+"_all"):extractFieldSize( bais, "sources")]
                break;
                case "monolingual.json":
                // res += [mono:extractFieldStream( tis, "counts")]
                res += [ (collId+"_mono"):extractFieldSize( bais, "counts")]
                break;
                case "bilingual.json":
                // res += [bi:extractFieldStream( tis, "counts")]
                res += [ (collId+"_bi"):extractFieldSize( bais, "counts")]
                break
                default: println( "Invalid tarball content $entryName")
            }
        }
        return res
    } finally {
        try {
            tis?.close()
            gzi?.close()
            bis?.close()
            is?.close()
        } catch( Exception e ) {
            e.printStackTrace()
        }
    }
}

Map extractStatsJq( File f ) {
    def jsonSlurper = new JsonSlurper( type: JsonParserType.CHAR_BUFFER)
    String basename = f.name.replaceAll( ".json.tgz","")
    Map res = [collection:basename]
    def buffer = new  ByteArrayOutputStream()
    try {
        [ "tar", "xzOf", f.absolutePath, basename+"/all.json"].execute()
        .pipeTo( ["jq", ".sources"].execute())
        .waitForProcessOutput( buffer, System.err )
        res += [source:jsonSlurper.parseText( buffer.toString( "UTF-8"))]
    } catch( Exception e ) {
        System.out.println( "Problem in $basename")
    }
    buffer.reset()

    try {
        [ "tar", "xzOf", f.absolutePath, basename+"/monolingual.json"].execute()
        .pipeTo( ["jq", ".counts"].execute())
        .waitForProcessOutput( buffer, System.err )
        res += [mono:jsonSlurper.parseText( buffer.toString( "UTF-8"))]
    } catch( Exception e ) {
        System.out.println( "Problem in $basename")
    }
    buffer.reset()

    try {
        [ "tar", "xzOf", f.absolutePath, basename+"/bilingual.json"].execute()
        .pipeTo( ["jq", ".counts"].execute())
        .waitForProcessOutput( buffer, System.err )
        res += [bi:jsonSlurper.parseText( buffer.toString( "UTF-8"))]
    } catch( Exception e ) {
        System.out.println( "Problem in $basename")
    }

    return res
}


Map extractField( String json, String fieldname ) {
    def jsonSlurper = new JsonSlurper( type: JsonParserType.CHAR_BUFFER)

    def file = jsonSlurper.parseText( json , "UTF-8" )
    def res = file[fieldname]
    if( res == null ) return [:]
    return res
}


long extractFieldSize( InputStream is, String fieldname ) {
    def jsonSlurper = new JsonSlurper( type: JsonParserType.CHAR_BUFFER)

   def file = jsonSlurper.parse( is, "UTF-8" )
     def res = file[fieldname]
    if( res == null ) return 0
    def partialJson = getJsonPrinter().toJson( res )
    return partialJson.size()
}
