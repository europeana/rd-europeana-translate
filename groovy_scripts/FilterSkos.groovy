@Grapes([
        @Grab(group='org.apache.commons', module='commons-compress', version='1.21')
])

import java.util.stream.*
import groovy.json.*
import org.apache.commons.compress.compressors.gzip.*

// arg is the file to compress
if( args.length < 1 ) {
    println( "Usage: groovy FilterSkos.groovy Skos_raw_en_...json.gz")
}

def file = new File( args[0] )

def gzin = new GzipCompressorInputStream( file.newInputStream())
// slurp it in
def json = new JsonSlurper().parse( gzin )

def outfile = new File( file.parent, file.name.replace( "_raw_", "_filtered_"))

def skosEntries = new SkosEntries()

for( def skosEntry: json ) {
    def otherLangSet = skosEntry.keySet() - "en" -"url"
    if( otherLangSet.isEmpty()) continue
    def otherLang = otherLangSet.iterator().next()
    skosEntries.add( skosEntry.get("en"), otherLang, skosEntry.get( otherLang ), skosEntry.get( "url"))
}

def outputJson = new JsonGenerator.Options()
        .excludeNulls()
        .disableUnicodeEscaping()
        .build()
        .toJson( skosEntries.filteredResult())
// println( outputJson )
def fos = outfile.newOutputStream()
def gzout = new GzipCompressorOutputStream( fos )

gzout.withWriter( "UTF-8") {
    w->
        w.println( outputJson )
        w.flush()
}
gzout.flush()
gzout.close()

class SkosEntries {
    // en
    // (otherLang)
    // url []
   HashMap<String, Map> engKeyedMap = [:]
   HashMap<String, Map> otherKeyMap = [:]


   public void add( String englishLit, String lang, String otherLit, String url) {
      Map entry = engKeyedMap.computeIfAbsent( englishLit ) { k -> [en:k, url:[] as Set, (lang):[] as Set, help:lang ]}
      entry["url"].add( url )
      entry[lang].add( otherLit )

      entry = otherKeyMap.computeIfAbsent( otherLit ) { k -> [en:[] as Set, url:[] as Set, (lang):otherLit, help:lang ]}
      entry["url"].add( url )
      entry["en"].add( englishLit )
   }

    // 3 sections unique ..synonymTranslation ... synonymEnglish
    public Map filteredResult() {
        def res = [:]

        res["unique"] = engKeyedMap.values()
        .findAll { skos->
            String lang = skos["help"]
            Set translations = skos[lang]
            return (translations.size() == 1) 
        }
        .collect { m-> 
            [en:m.en, url:m.url, (m["help"]):m[m["help"]].iterator().next()]
        }

        res["translationSynonyms"] = engKeyedMap.values()
        .findAll { skos->
            String lang = skos["help"]
            Set translations = skos[lang]
            return (translations.size() > 1) 
        }
        .collect { m-> 
            [en:m.en, url:m.url, (m["help"]):m[m["help"]]]
        } 

        res["englishSynonyms"] = otherKeyMap.values()
        .findAll { skos->
            Set english = skos["en"]
            return (english.size() > 1) 
        }
        .collect { m-> 
            [en:m.en, url:m.url, (m["help"]):m[m["help"]]]
        }
        return res
    }
}

