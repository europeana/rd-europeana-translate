// find missing collections

@Grapes([
        @Grab(group='commons-net', module='commons-net', version='2.0')
])

import org.apache.commons.net.ftp.*

// setup here
String DOWNLOAD_DIR = "/data/fast1/stabenau/staging"
String PROCESSED_DIR = "/data/fast1/stabenau/jsonTarballs"

def availableFiles = [] as Set

FTPClient ftpClient = new FTPClient()
ftpClient.connect('download.europeana.eu')
ftpClient.login("anonymous", "anonymous")
ftpClient.cwd( "dataset/XML")

FTPFile[] files = ftpClient.listFiles().sort{ -it.getSize() }
for (FTPFile file : files) {
    if( file.getName().endsWith( ".zip")) 
    availableFiles.add( file.getName())
}
ftpClient.disconnect()

File processedDir = new File( PROCESSED_DIR )
processedDir.eachFile{ tgzFile -> 
    String name = tgzFile.getName()
    String downloadName = name.replaceAll( ".json.tgz", ".zip")
    availableFiles.remove( downloadName )
}

println( availableFiles.collect{ "\"$it\"" }.join( ", " ))

/* 
[ "92062.zip", "188.zip", "2022702c.zip", "2022702b.zip", "2022702a.zip", "2058621.zip", "2022702d.zip", "2021618.zip", "9200115.zip", 
"2021639.zip", "2064102.zip", "2023006.zip", "2059509.zip", "0940430.zip", "92070.zip", "2059220.zip", "08570.zip", "2058208.zip",
 "2058207.zip", "2058617.zip", "2048234.zip", "91962.zip", "403.zip", "91607.zip", "384.zip", "00735.zip", "2048306.zip", "9200515.zip", 
 "402.zip", "919196.zip", "919168.zip", "259.zip", "919201.zip", "2021722.zip"]
 */
 