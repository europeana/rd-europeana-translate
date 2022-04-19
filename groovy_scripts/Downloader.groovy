// Purpose of this script is to connect to Europeana FTP serverq// get a list of the available zip archives
// queue them all for processing, ordered by size descending
// download them into the cache area DOWNLOAD_DIR that has maximum size MAX_CACHE, as our discs couldnt hold many sets and the analysis results
// process each one with the Eanascrape.groovy script
// start at most three of those ... strictly not needed, as Eanascrape already is multithreaded and will keep the machine busy by itself
// configure the output dir for Eanascrape tarballs in here
// when analysis is done, the XML zip archive is deleted

@Grapes([
        @Grab(group='commons-net', module='commons-net', version='2.0')
])

import org.apache.commons.net.ftp.*
import java.util.function.*

// setup here
String DOWNLOAD_DIR = "/home/pscalia/arne/downloading"
String PROCESSED_DIR = "/home/pscalia/arne/json"
long MAX_CACHE = 5_000_000_000l



def downloader = new DownloadHelper( DOWNLOAD_DIR, MAX_CACHE)

def filterDownload = { ns -> 
    def onlyThose = [ "92062.zip", "2058621.zip", "2021618.zip", "9200115.zip", "2021639.zip", "2064102.zip",
     "2023006.zip", "2059509.zip", "92070.zip", "2058617.zip", "403.zip", "91607.zip"] as Set
    return onlyThose.contains( ns.name )
}
downloader.filterDownload = filterDownload

threads = []
def t = new Thread( downloader )
t.start()
threads.add( t )

for( int i=0; i<3; i++ ) {
    def processor = new Process( DOWNLOAD_DIR, PROCESSED_DIR, downloader )
    t =  new Thread( processor )
    t.start()
    threads.add( t )
}

// wait here 
threads.each{ tr -> tr.join() }

class Process implements Runnable {
    DownloadHelper dh
    File sourceDir, targetDir

    public Process(String source, String target, DownloadHelper dh ) {
        this.sourceDir = new File( source ) 
        this.targetDir = new File( target )


        this.dh = dh
  }

    public void run() {
        def env = System.getenv() + ["JAVA_OPTS":"-Xmx16G"]
        def envList = env.collect{ k,v -> "$k=$v"}
        File cwd = new File( ".ignore").getAbsoluteFile().getParentFile()
        while( true ) {
            Optional<File> nextFile = dh.get()
            if( !nextFile.isPresent()) break
            println( "Processing ${nextFile.get().name} Size:${nextFile.get().size()}")
            def proc = ["groovy", "Eanascrape.groovy", nextFile.get().absolutePath, targetDir.absolutePath].execute( envList, cwd )
            proc.waitForProcessOutput( System.out, System.err )
            // println( "Deleting ${nextFile.get().name}")

            // run process
            // move tgz to target ... possibly not needed

            // nextFile.get().delete()
            dh.remove( nextFile.get().name )
            // remove from dh
        }
    }
}


class DownloadHelper implements Runnable {
    // start with biggest ..
    LinkedList<NameSize> toDo
    LinkedList<NameSize> activeSet = [] as LinkedList
    List<NameSize> checkedOutFiles = []

    public Predicate<NameSize> filterDownload

    long maxSize
    File downloadDir;
    boolean busy = true;

    FTPClient ftpClient = new FTPClient()
    static class NameSize {
        public String name
        public long size
    }

    public DownloadHelper( String targetDir, long maxSize ) {
        // get the file list with sizes
        // a directory where to download to
        // an active set where client can take data from to process
        // a remove method that deletes the file
        this.maxSize = maxSize
        this.downloadDir = new File( targetDir )
    }

    public void connect() {
        ftpClient.connect('download.europeana.eu')
        ftpClient.login("anonymous", "anonymous")
        ftpClient.cwd( "dataset/XML")
    }

    public LinkedList<NameSize> getFileList() {
        def res = [] as LinkedList
        FTPFile[] files = ftpClient.listFiles().sort{ -it.getSize() }
        for (FTPFile file : files) {
            if( file.getName().endsWith( ".zip")) {
                def nameSize = new NameSize()
                nameSize.name = file.getName()
                nameSize.size = file.getSize()
                // for testing 
                // if( nameSize.size <= 1000000)
                    res.add( nameSize )
            }
        }
        return res
    }

    // are we done yet?
    public synchronized boolean finished() {
        return !busy
    }

    public void run() {
        connect()
        toDo = getFileList()
        ftpClient.setFileType( FTP.BINARY_FILE_TYPE )
        while( !toDo.isEmpty() ) {
            while( isFull()) sleep( 1000*10 )
            NameSize top = toDo.pop()
            if( filterDownload != null )
                if( !filterDownload.test( top ))
                    continue                
            int retry = 3
            while( retry > 0 )
                try {
                    download( top.name )
                    synchronized( activeSet ) {
                        activeSet.add( top )
                        activeSet.notifyAll()
                    }
                    break
                } catch( Exception e ) {
                    e.printStackTrace();
                    retry--
                }
        }
        busy = false;
        synchronized( activeSet ) {
            activeSet.notifyAll()
        }
    }

    private void download( String name ) throws Exception {
        def incomingFile = new File( downloadDir, name )
        println( "Downloading '$name' ...")
        incomingFile.withOutputStream { ostream -> 
            ftpClient.retrieveFile( name, ostream )
            ostream.flush()
            }
        println( "Finshed downloading '$name'")
    }


    private boolean isFull() {
        long size = 0
        synchronized( activeSet ) {
            activeSet.each{ ns -> size += ns.size }
            checkedOutFiles.each{ ns -> size += ns.size }
            return (size >= maxSize)
        }
    }

    public void wakeup() {
        // interrupt the sleep of the worker thread
        // or do nothing, just wait until it wakes up by itself
    }

    // can only remove stuff that was worked on
    public remove( String name ) {
        synchronized( activeSet ) {
            def i = checkedOutFiles.iterator()
            while( i.hasNext() ) {
                if( i.next().name.equals( name )) {
                    i.remove()
                    break;
                }
            }
            wakeup()
        }
    }

    // blocking get .. works only for one client ..
    public Optional<File> get() {
        while( true ) {
            synchronized( activeSet ) {
                if( finished() && activeSet.isEmpty()) return Optional.empty()
                if( !activeSet.isEmpty()) {
                    def elem = activeSet.pop()
                    checkedOutFiles.add( elem )
                    return Optional.of( new File( downloadDir,elem.name ))
                }
                activeSet.wait(10000)
            }
        }
    }

    public void close() {
        ftpClient.logout()
        ftpClient.disconnect()
    }
}
