
@Grapes([
        @Grab(group='xom', module='xom', version="1.3.7" ),
        @Grab(group='org.apache.commons', module='commons-compress', version='1.21')
])

import nu.xom.*
import java.util.*
import java.util.stream.*
import groovy.json.*
import groovy.transform.AutoClone
import org.apache.commons.compress.archivers.tar.TarArchiveEntry;
import org.apache.commons.compress.archivers.tar.TarArchiveOutputStream;
import org.apache.commons.compress.compressors.gzip.GzipCompressorOutputStream;


if( args.length < 1 ) {
    println( "Usage: groovy Eanascrape.groovy filename [outputDir]");
    System.exit(1);
}

// write a json output with all literals, the monolinguals and the bilinguals

// provide a jsonPrinter
def getJsonPrinter( ) {
    return new JsonGenerator.Options()
    .excludeNulls()
    .addConverter( LiteralSetWithIds ) { LiteralSetWithIds lits, String key  -> 
        return LiteralSetWithIds.convert( lits );
    }
    .addConverter( BiLingual ) { BiLingual bling, String key  ->
        return BiLingual.convert( bling )
    }
    .disableUnicodeEscaping()
    .build()
}

// processZipfile( new File( args[0] ), System.out );
def allLiterals = zipToLiterals( new File( args [0] ));
def basename = (args[0] =~ /([^\/]+).zip/)[0][1]
def outputDir =new File( ".ignore" ).getAbsoluteFile().getParentFile()
if( args.length > 1 ) {
    def targetDir = new File( args[1] )
    if( targetDir.isDirectory() && targetDir.canWrite() ) {
        outputDir = targetDir
    }
}

makeTar( outputDir, basename, allLiterals)

def makeTar( File outputDir, String basename, allLiterals ) {
    def outFile = new File(outputDir,  basename+".json.tgz")

    OutputStream fo, gzo
    TarArchiveOutputStream o
    try {
         fo = new FileOutputStream( outFile )
         gzo = new GzipCompressorOutputStream(fo);
         o = new TarArchiveOutputStream(gzo) 
         def entry = new TarArchiveEntry( basename + "/");
         o.putArchiveEntry( entry )
         o.closeArchiveEntry()

         writeToTar( basename + "/all.json", allLiterals, o  )
         writeToTar( basename + "/monolingual.json", buildMonoLingual(allLiterals.literalsByField), o  )
         writeToTar( basename + "/bilingual.json", buildBiLingual( allLiterals.literalsByField ), o  )
         o.finish()
         gzo.finish()
    } catch( Exception e ) {
        e.printStackTrace()
    } finally {
        try {
            o?.close()
            gzo?.close()
            fo?.close()
        } catch( Exception e ) {
            e.printStackTrace()
        }
    }
}

def writePrettyJson( obj, writer ) {
    writer.println( 
        JsonOutput.prettyPrint(
            getJsonPrinter().toJson( obj )
        )
    )
}

def writeToTar( name, obj, tarStream ) {
    def entry = new TarArchiveEntry( name );
    def buffer = new ByteArrayOutputStream()
    def writer = buffer.newWriter( "UTF-8")

    writer.println( 
        //JsonOutput.unescaped(
         // JsonOutput.prettyPrint(
            getJsonPrinter().toJson( obj )
         //)
        //).getText()
    )
    writer.flush()

    entry.setSize( (long)buffer.size())
    tarStream.putArchiveEntry( entry )
    tarStream.write( buffer.toByteArray() )
    tarStream.closeArchiveEntry()

}

/**
  Build BiLingual
*/
BiLingual buildBiLingual( HashMap<String, LiteralSetWithIds> allLiterals ) {
    BiLingual res = new BiLingual()
    allLiterals.values().stream()
    .flatMap{ 
        litSetWithIds -> litSetWithIds.keySet().stream()
    }
    .map{ litSet -> litSet.getBiLingual() }
    .forEachOrdered{ bl -> res.merge( bl )}

    res.filterAmbiguous()

    return res
}


/**
  Extract any languaged unique entry from any field into the monolingual hash
*/
Map buildMonoLingual( HashMap<String, LiteralSetWithIds> allLiterals ) {
    def res = new LiteralSet() 
    for( LiteralSetWithIds literals: allLiterals.values()) {
        for( LiteralSet lit: literals.keySet()) {
            res.merge( lit )
        }
    }
    return [literals:res,counts: res.getCounts() ]
}

LiteralSetByField zipToLiterals( File file ) {
    def zf = new java.util.zip.ZipFile( file )
    def result = new LiteralSetByField()
    def streamProc = buildAllLiteralsStreamProcessor( result )

    // zf.entries().findAll { !it.directory }.each {
    //    streamProc( zf.getInputStream(it))
    // }

    zf.stream()
    .filter{ e -> ! e.directory }
    .map{ e -> 
        def is = zf.getInputStream( e )
        def bytes = is.getBytes() //groovy
        return new ByteArrayInputStream( bytes )
    }
    .parallel()
    .forEach{ is -> streamProc( is ) }
    return result;
}

def buildAllLiteralsStreamProcessor( LiteralSetByField allLiterals ) {
    def namespaces = setupNamespaces()
    def literalFieldNames = getAllLiteralFieldNames()

    return { stream ->
        def builder = new Builder();
        def optDoc = processInputStream( stream, builder )
        if( optDoc.isPresent() ) {
            Document doc = optDoc.get()
            extractSkosLiterals( doc, namespaces, allLiterals )
            addDocLiterals( doc, namespaces, allLiterals, literalFieldNames )
        }
    }   
}


def extractSkosLiterals( Document doc, XPathContext namespaces, LiteralSetByField allLiterals ) {
     Nodes concepts = doc.query( "//skos:Concept", namespaces ) 
    for( int i=0; i<concepts.size(); i++ ) {
        Element concept = (Element) concepts.get( i );

        String url = concept.getAttributeValue( "about", namespaces.lookup( "rdf"))
        if( url == null ) continue
        Nodes labelNodes = concept.query( "skos:prefLabel", namespaces)
        Map labelMap = [:]
        for( int j=0; j<labelNodes.size(); j++ ) {
            Element labelNode = (Element) labelNodes.get( j )
            String lang = labelNode.getAttributeValue( "lang", namespaces.lookup( "xml"))
            if( lang == null ) continue
            String literal = labelNode.getValue().trim()
            if( labelMap.containsKey( lang )) {
                labelMap[lang] = "!@#"
            } else {
                labelMap[lang] = literal
            }
        }
        labelMap.each{ lang, lit ->
            if( lit != "!@#") allLiterals.addSkos( url, lang, lit)
        }
    }
}


def getAllLiteralFieldNames() {
    return [ 
        "dc:coverage",
        "dc:description",
//        "dc:format",
        "dc:relation",
        "dc:rights",
        "dc:source",
        "dc:subject",
        "dc:title",
        "dc:type",
        "dcterms:alternative",
        "dcterms:hasPart",
        "dcterms:isPartOf",
        "dcterms:isReferencedBy",
        "dcterms:medium",
        "dcterms:provenance",
        "dcterms:references",
        "dcterms:spatial ",
        "dcterms:tableOfContents",
        "dcterms:temporal",
        "edm:currentLocation",
        "edm:hasType",
        "edm:isRelatedTo"
    ] as List<String>
}

def setupNamespaces() {
    def namespaces = new XPathContext()
    for( String[] pair: [["dcterms","http://purl.org/dc/terms/" ],
    [ "dc","http://purl.org/dc/elements/1.1/"], 
    [ "ore","http://www.openarchives.org/ore/terms/"],
    [ "rdf","http://www.w3.org/1999/02/22-rdf-syntax-ns#"],
    [ "edm","http://www.europeana.eu/schemas/edm/" ],
    [ "dqv", "http://www.w3.org/ns/dqv#" ],
    [ "skos", "http://www.w3.org/2004/02/skos/core#"]
    ] )  namespaces.addNamespace( pair[0], pair[1]);
    return namespaces;
}


def Optional<Document> processInputStream( InputStream xml, Builder parser ) {
    try {
      return Optional.of( parser.build(xml));
    } catch( Exception e ) {
        println( e )
           return Optional.empty()
        // throw e
    }
}

Optional<String> uniqueVal( Document doc, XPathContext namespaces, String query ) {
    def values = valuesFromQuery( doc, namespaces, query )
    def opt = (values.size() == 1) ?
        Optional.of( values[0] ) : Optional.empty()
    return opt
}

def cleanDoc(Document doc, XPathContext namespaces ) {
 // clean out parts we dont want ..
 // all agents places skos concepts
 // the skos aprt we might revisit later ?? Or get the skos directly from europeana 
 // remove ore:Aggregation that is not contains /aggregation provider
 // and ore:proxy that does not contain proxy/provider
    removeFromDoc( doc, namespaces, "//edm:Agent");
    removeFromDoc( doc, namespaces, "//edm:Place");
    removeFromDoc( doc, namespaces, "//skos:Concept");
    removeFromDoc( doc, namespaces, "//ore:Proxy[ contains( @rdf:about, '/proxy/aggregator')]")
    removeFromDoc( doc, namespaces, "//dqv:QualityAnnotation")
}

// get Values, maybe not useful
def valuesFromQuery( Document doc, XPathContext namespaces, String query ) {
    def res = []
    Nodes nodes = doc.query( query, namespaces ) 
    for( int i=0; i<nodes.size(); i++ ) {
        Node n = nodes.get( i );
        res.add( n.getValue())
    }
    return res
}


/**
  
*/

def removeFromDoc( Document doc, XPathContext namespaces, String query ) {
    // modify Doc ion place
    Nodes nodes = doc.query( query, namespaces )
    for( int i=0; i<nodes.size(); i++ ) {
        Node n = nodes.get( i );
        n.getParent().removeChild( n )
    }
}

Optional<String> langOfNode( Node n, XPathContext namespaces ) {
    if( ! ( n instanceof Element )) return Optional.empty()
    Element elem = (Element) n
    String lang = elem.getAttributeValue( "lang", namespaces.lookup( "xml"))
    return Optional.ofNullable( lang )
}

/**
    extract literals from given fields, and add them to given allLiterlas strukture.
    Doc is cleaned before.
*/
void addDocLiterals(  Document doc, XPathContext namespaces, LiteralSetByField allLiterals, List<String> allFields ) {
    cleanDoc( doc, namespaces );
    for( String field: allFields ) {
        addFieldLiteralToLiteralSet( doc, namespaces, field, allLiterals )
    }

    // and some counting
    def optProvider = uniqueVal( doc, namespaces, "(//edm:provider[not( @rdf:resource)])[1]")
    def optDataProvider = uniqueVal( doc, namespaces, "(//edm:dataProvider[not(@rdf:resource)])[1]")

    def aggregator = optProvider.orElse( "-missing-" )
    def dataProvider = optDataProvider.orElse( "-missing-")
    allLiterals.addSource( aggregator, dataProvider )
}

void addFieldLiteralToLiteralSet( Document doc, XPathContext namespaces, String field, LiteralSetByField allLiterals ) {
    def literals = getLiteralsByField( doc, namespaces, field );
    def optId = uniqueVal( doc, namespaces, "(//ore:Aggregation/edm:aggregatedCHO/@rdf:resource)[1]")
    def id = optId.orElse( "urn://no.source" )

    if( ! literals.isPresent() ) return;
    allLiterals.addLiteralSet( id, field, literals.get() ) 
}

// lang keyed literals
Optional<LiteralSet> getLiteralsByField( Document doc, XPathContext namespaces, String field ) {
    int count = 0;
   Nodes allLiterals = doc.query( "//"+field, namespaces  )
    LiteralSet langKeyed = new LiteralSet()
    for( int i=0; i<allLiterals.size(); i++ ) {
        Node n =allLiterals.get(i )
        String literal = n.getValue().trim();
        if( literal.equals( "" )) continue;
        def optLang = langOfNode( n, namespaces )
        def lang = optLang.orElse( "xx")
        langKeyed.addLiteral( lang, literal )
        count++;
    }
    if( count > 0 ) return Optional.of( langKeyed )
    else return Optional.empty();
}    

/**
 LiteralSet is just one set of literals possibly many languages, possibly more than one entry per language 
*/
class LiteralSet extends HashMap<String, Set<String>> {
    public void addLiteral( String lang, String literal ) {
        Set<String> literals = computeIfAbsent( lang, {k-> new HashSet<String>()})
        literals.add( literal )
    }

    // one is english, has one entry and there is another with one entry
    public boolean isMultilang() {
        if( size() < 2 ) return false;
        if( ! containsKey( "en")) return false;
        if( get( "en").size() > 1 ) return false;
        Set<String> keys = [] as Set;
        keys.addAll( keySet())
        keys.remove( "en" );
        for( String lang: keys ) {
            if( lang == "xx" ) continue;
            if( get( lang ).size() == 1) return true; 
        }
        return false;
    }

    public BiLingual getBiLingual() {
        def res = new BiLingual();
        if( ! isMultilang()) return res;
        String engLiteral = get("en").iterator().next();
        for( String lang: keySet()) {
            if( lang =="xx" ) continue;
            if( lang == "en" ) continue;
            Set<String> otherLitSet = get( lang );
            if( otherLitSet.size() != 1 ) continue;
            String other = otherLitSet.iterator().next()
            res.addLiteralPair( lang, engLiteral, other )
        }
        return res
    }

     public HashMap<String, Integer> getCounts() {
        HashMap<String,Integer> res = new HashMap();
        for( Entry<String, Set<String>> entry: entrySet() ) {
            res.put( entry.getKey(), entry.getValue().size())
        }
        return res;
    }

    public LiteralSet merge( LiteralSet other ) {
        for( Entry<String, Set<String>> entry: other.entrySet() ) 
            for( String lit: entry.getValue() )
                addLiteral( entry.getKey(), lit)
    }
}

class LiteralSerial {
    public LiteralSerial( LiteralSet ls, List<String> ids ) {
        this.literals = ls;
        this.ids = ids 
    }
    LiteralSet literals
    List<String> ids 
}

class LiteralSetWithIds extends HashMap<LiteralSet, List<String>> {
    public void addIdAndSet( String id, LiteralSet literals ) {
        List<String> ids = computeIfAbsent( literals, {k -> new ArrayList<String>()});
        ids.add( id )
    }
    // serialize as list of { literals: .. ids: []}
    public static List<LiteralSerial> convert( LiteralSetWithIds litset ) {
        return litset.entrySet()
            .stream()
            .map({ entry -> new LiteralSerial( entry.getKey(), entry.getValue())})
            .collect( Collectors.toList())
    }
}

class Skos extends HashMap<String, String> {
}

class BiLiteral extends HashMap<String, String> {
    public BiLiteral( String enLiteral, String otherLang, String otherLiteral ) {
        super()
        put( "en", enLiteral)
        put( otherLang, otherLiteral)
    }
}

class Counters extends HashMap<String, Integer> {
    public void count( String literal ) {
        Integer currentCount = get( literal )
        if( currentCount != null )
            put( literal, currentCount+1 )
        else 
            put( literal, 1 )
    }

    public Set<String> getDuplicates() {
        def res = [] as Set
        for( Entry<String, Integer> entry: entrySet() ) {
            if( entry.getValue() > 1 ) 
                res.add( entry.getKey())
        }
        return res
    }


 }

class BiLingual extends HashMap<String, Set<BiLiteral>> {
    public addLiteralPair( String otherLang, String engLiteral, String otherLiteral ) {
        Set<BiLiteral> pairs = computeIfAbsent( otherLang,{ k-> new HashSet<BiLiteral>()})
        def lit = new BiLiteral( engLiteral, otherLang, otherLiteral)
        pairs.add( lit )
    }

    public void merge( BiLingual other ) {
        for( String lang: other.keySet()) {
            Set<BiLiteral> pairs = computeIfAbsent( lang,{ k-> new HashSet<BiLiteral>()})
            pairs.addAll( other.get( lang ))
        }
    }

    public Map getCounts() {
        def res = [:]
        for( String otherLang: keySet()) {
            res["en-"+otherLang] = get(otherLang).size()
        }
        return res;
    }

    // remove entries where one or the other lang literal has different partners
    public void filterAmbiguous() {

        for( String otherLang: keySet()) {
            def engLit = new Counters()
            def otherLit= new Counters()

            for( BiLiteral biLit: get( otherLang )) {
                engLit.count( biLit.get("en"))
                otherLit.count( biLit.get( otherLang ))
            }
            Set<String> engDuplicates = engLit.getDuplicates()
            Set<String> otherDuplicates = otherLit.getDuplicates()
            // println( engLit.dump())
            Iterator i = get(otherLang).iterator()
            while( i.hasNext()) {
                BiLiteral bi = i.next()
                if( engDuplicates.contains( bi.get("en")) || 
                    otherDuplicates.contains( bi.get( otherLang )))
                    i.remove()
            }
            if( get( otherLang).size() == 0 ) {
                remove( otherLang )
            }
        }
    }

    // make a structure for nicer json
    public static Map convert( BiLingual bling ) {
        Map res = [:]
        res["counts"] = bling.getCounts()
        for( String otherLang: bling.keySet() ) {
            res["en-"+otherLang] = 
                bling.get( otherLang )
        }
        return res
    }
}

class LiteralSetByField  {
    // access to this is probably masked by groovy converting 
    // member access on map to key retrieval
    Counters sources = new Counters();
    HashMap<String, LiteralSetWithIds> literalsByField = new HashMap();
    HashMap<String, Skos> allSkosEntries = new HashMap();

    public synchronized void addLiteralSet( String id, String field, LiteralSet literalSet ) {
        LiteralSetWithIds litWithIds = literalsByField.computeIfAbsent( field, {k-> new LiteralSetWithIds() } );
        litWithIds.addIdAndSet( id, literalSet )
    }

    public synchronized void addSource( String aggregator, String dataProvider ) {
        sources.count( "Aggregator: " + aggregator + "\nProvider: " + dataProvider )
    }

    public synchronized void addSkos( String url, String lang, String lit ) {
        def skos = allSkosEntries.computeIfAbsent( url, {k-> new Skos( )})
        // this may overwrite stuff, but its likely all skos sections with the same url are actually the same
        skos.put( lang, lit )
    }
}


