import java.io.*;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.*;

import org.apache.commons.io.IOUtils;
import org.archive.io.ArchiveReader;
import org.archive.io.ArchiveRecord;
import org.archive.io.warc.WARCReaderFactory;


public class Indexer {

    private final List<PageTable> pageTableList;
    private final Map<String,Integer> wordToWordId;
    private final Map<Integer,String> wordIdToWord;
    private final Map<String,List<Integer>> lexicon;
    private int docId;
    private static int numw;
    //private final DataOutputStream dataOutputStream;
    private final BufferedOutputStream bufferedOutputStream;
    //private final DataOutputStream invertedIndexOutputStream;
    private final BufferedOutputStream invertedIndexOutputStream;

    public Indexer() throws FileNotFoundException {
        this.pageTableList = new ArrayList<PageTable>(100000);
        this.wordToWordId = new HashMap<>(100000, 0.9f);
        //this.dataOutputStream = new DataOutputStream(new FileOutputStream("IntermediateInvertedIndex"));
        this.bufferedOutputStream = new BufferedOutputStream(new FileOutputStream("IntermediateInvertedIndex"),4096 * 10000);
        this.invertedIndexOutputStream = new BufferedOutputStream(new FileOutputStream("InvertedIndex"),4096 * 10000);
        this.docId = 0;
        this.wordIdToWord = new HashMap<>(100000, 0.9f);
        this.lexicon = new TreeMap<>();

    }

    public List<String> getWetFiles(String path) {
        final File folder = new File(path);
        List<String> fileNames = new ArrayList<>();
        for (final File fileEntry : folder.listFiles()) {
            if (fileEntry.getName().equals(".DS_Store")) {
                fileEntry.delete();
            }
            else {
                fileNames.add(fileEntry.getName());
            }
        }

        return fileNames;
    }

    public void addEntryToPageTable(String url,int size){
        pageTableList.add(new PageTable(docId,url,size));
    }

    public void generateIntermediatePosting(int wordId) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(wordId);
        buffer.putInt(docId);

        byte[] bytes = buffer.array();
        bufferedOutputStream.write(bytes);
        //dataOutputStream.write(bytes,0,buffer.position());

    }

    public void generatePosting(int docId,int termFrequency) throws IOException {
        ByteBuffer buffer = ByteBuffer.allocate(8);
        buffer.order(ByteOrder.LITTLE_ENDIAN);
        buffer.putInt(docId);
        buffer.putInt(termFrequency);

        byte[] bytes = buffer.array();
        invertedIndexOutputStream.write(bytes);
    }

    public void printPosting(String word){
        System.out.println(word +"->" + docId);
    }

    public void closeIntermediatePostingsFile() throws IOException {
        bufferedOutputStream.flush();
        bufferedOutputStream.close();
        //dataOutputStream.close();
    }

    public void closeInvertedIndex() throws IOException {
        invertedIndexOutputStream.flush();
        invertedIndexOutputStream.close();
    }

    public void generateURLTableFile() throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter writer = new PrintWriter("URLTable.txt", "UTF-8");

        for(PageTable pageTable : pageTableList){
            writer.println(pageTable.docId+"\t"+pageTable.url+"\t"+pageTable.numberOfTerms);
        }

        writer.close();
    }

    public void mergeSort(String memoryLimit) throws InterruptedException, IOException {

        ProcessBuilder pb = new ProcessBuilder("./sortphase","8",memoryLimit,"IntermediateInvertedIndex","temp","list");

        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        Process p = pb.start();
        int exitValue = p.waitFor();
        System.out.println("Sort Phase exited with value:"+exitValue);

        pb = new ProcessBuilder("./mergephase","8",memoryLimit,"40","list","result","list2");
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        p = pb.start();
        exitValue = p.waitFor();
        System.out.println("Merge Phase exited with value:"+exitValue);

        pb = new ProcessBuilder("./checkoutput","8","result0");
        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
        p = pb.start();
        exitValue = p.waitFor();
        System.out.println("Check Output exited with value:"+exitValue);
    }

    public void updateLexicon(int wordId,int startIndex,int endIndex,int docFrequency){
        List<Integer> list = new ArrayList<>(3);
        list.add(startIndex);
        list.add(endIndex);
        list.add(docFrequency);
        lexicon.put(wordIdToWord.get(wordId),list);
    }

    public void generateLexiconFile() throws FileNotFoundException, UnsupportedEncodingException {
        PrintWriter writer = new PrintWriter("Lexicon.txt", "UTF-8");

        for(String key : lexicon.keySet()){
            writer.println(key+ " Start: "+lexicon.get(key).get(0)+" End: "+lexicon.get(key).get(1)+" Df: "+lexicon.get(key).get(2));
        }

        writer.close();
    }

    public void reformatPostings() throws IOException {
        //  read the file into a byte array
        File file = new File("result0");
        FileInputStream fis = new FileInputStream(file);
        System.out.println("File Length"+file.length());
        System.out.println("File Length in int"+(int)file.length());
        byte [] arr = new byte[(int)file.length()];
        fis.read(arr);

        ByteBuffer bb = ByteBuffer.wrap(arr);

        //  if the file uses little endian as apposed to network
        //  (big endian, Java's native) format,
        //  then set the byte order of the ByteBuffer

        bb.order(ByteOrder.LITTLE_ENDIAN);

        Map<Integer,Integer> docIdToTermFrequency = new TreeMap();
        int wordId = bb.getInt();
        int docId = bb.getInt();
        int startIndex = 0;
        for(int i=8;i<bb.capacity();i+=8){
            int nextWordId = bb.getInt();
            int nextDocId = bb.getInt();

            if(wordId != nextWordId){
                for(int docIdKey : docIdToTermFrequency.keySet()){
                    generatePosting(docIdKey,docIdToTermFrequency.get(docIdKey));
                }
                updateLexicon(wordId,startIndex,i,docIdToTermFrequency.size());
                docIdToTermFrequency.clear();
                startIndex = i;
            }

            if(docIdToTermFrequency.containsKey(nextDocId)){
                docIdToTermFrequency.put(nextDocId,docIdToTermFrequency.get(nextDocId)+1);
            }
            else{
                docIdToTermFrequency.put(nextDocId,1);
            }

            wordId = nextWordId;
            //docId = nextDocId;

        }

        for(int docIdKey : docIdToTermFrequency.keySet()){
            generatePosting(docIdKey,docIdToTermFrequency.get(docIdKey));
        }
        docIdToTermFrequency.clear();
    }

    public void printInvertedIndex() throws IOException {
        File file = new File("InvertedIndex");
        FileInputStream fis = new FileInputStream(file);
        byte [] arr = new byte[(int)file.length()];
        fis.read(arr);

        ByteBuffer bb = ByteBuffer.wrap(arr);
        bb.order(ByteOrder.LITTLE_ENDIAN);

        while(bb.hasRemaining()) {
            int docId = bb.getInt();
            int termFrequency = bb.getInt();
            System.out.println("DocId: "+docId+" Tf: "+termFrequency);
        }
    }

    public void parseWetFile(String wetFilePath) throws IOException {
        BufferedInputStream is = new BufferedInputStream(new FileInputStream(wetFilePath));
        ArchiveReader ar = WARCReaderFactory.get(wetFilePath, is, true);

        for (ArchiveRecord r : ar) {
            int numberOfTerms = 0;
            // The header contains information such as the type of record, size, creation time, and URL
            //System.out.println(r.getHeader());
            if(r.getHeader().getMimetype().equals("text/plain")) {
                // Creating a byte array that is as long as the record's stated length
                byte[] rawData = IOUtils.toByteArray(r, r.available());

                String content = new String(rawData);
                String[] words = content.split("[^a-zA-Z0-9]+");

                for (String word : words) {

                    if (!wordToWordId.containsKey(word)) {
                        wordToWordId.put(word, numw);
                        wordIdToWord.put(numw,word);
                        generateIntermediatePosting(numw);
                        //printPosting(word);
                        numw++;
                    }
                    else{
                        generateIntermediatePosting(wordToWordId.get(word));
                        //printPosting(word);
                    }
                }

                addEntryToPageTable(r.getHeader().getUrl(),words.length);

//                System.out.println(content.substring(0, Math.min(500, content.length())));
//                System.out.println((content.length() > 500 ? "..." : ""));
//
//                System.out.println("=-=-=-=-=-=-=-=-=");
                //System.out.println(docId);
                docId++;
//                if (docId > 1000){
//                    break;
//                }
            }
        }
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        Indexer indexer = new Indexer();

        String path = "/Users/pravarsingh/Desktop/Study Material/sem 3/Web Search Engines/Indexer/wetFiles";
        List<String> wetFileNames = indexer.getWetFiles(path);
        int count = 0;

        long startTime = System.currentTimeMillis();
        for (String wetFileName : wetFileNames) {
            if(wetFileName.contains("gz")) {
                indexer.parseWetFile(path + "/" + wetFileName);
            }
            System.out.println(count);
            if (++count == 50) {
                break;
            }
        }

        indexer.closeIntermediatePostingsFile();
        indexer.generateURLTableFile();

        long sortStartTime = System.currentTimeMillis();

        indexer.mergeSort(args[0]);

        long sortEndTime = System.currentTimeMillis();
        long reformatStartTime = System.currentTimeMillis();

        indexer.reformatPostings();

        long reformatEndTime = System.currentTimeMillis();

        indexer.closeInvertedIndex();
        indexer.generateLexiconFile();

        long endTime = System.currentTimeMillis();
        System.out.println("Mergesort time :" + (sortEndTime-sortStartTime)/1000+" seconds");
        System.out.println("Inverted Index generation & Lexicon creation time :" + (reformatEndTime-reformatStartTime)/1000+" seconds");
        System.out.println("Total time elapsed :" + (endTime-startTime)/1000+" seconds");

    }
}


