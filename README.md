# IndexBuilder
Generates an Inverted Index in binary format, a Lexicon , and an URL table. A Component of the search engine which helps in search query processing in a few milliseconds.Uses I/O effecient disk access techniques and compression.

<b>How to run on mac :</b>

Go to the root of source code directory:

java -Xmx2000m -cp .:out/artifacts/IndexBuilder_jar/IndexBuilder.jar Indexer 2000000000

where 2000000000 is the number of bytes of total memory to be used by sort and merge which is equivalent to 2GB,
and –Xmx2000m sets a max memory limit on the JVM and hence the program.

External Libraries used:
•	Apache commons IO
•	WARC ArchiveReader


<b>Broad Features of the program</b>:

•	Generates an Inverted Index in binary format, a Lexicon , and an URL table.
•	Takes gzip compressed wet files as input and only uncompresses the Document in the wet file currently being parsed.
•	Parses each document in wet file , extracts alphanumeric terms, assigns termId in the order the words are encountered , adds the term to a Term To Term Id Mapping.
•	Assigns DocId in the order the Documents are encountered.
•	Appends a row with docId, URL & No. of terms in Page of the visited doc to the URL Table txt file.   
•	Generates Intermediate posting in binary for every Term encountered by writing 4 bytes for TermId & 4 bytes for corresponding DocId. Hence every record is of 8 bytes.
•	Once all the wet files are parsed, it performs I/O efficient Merge Sort on the intermediate postings using C.
•	Also Checks the output of Merge Sort for correctness.
•	Parses the Sorted Intermediate Postings and removes WordId from each posting and adds Term Frequency by implementing a smart O(n) algorithm.
•	The final Inverted Index contains records of 8 byte comprised of 4 Bytes of DocId & next 4 bytes of term Frequency. Subsequently it is compressed to save disk space.
•	Lexicon is also updated to map each Word to start Index, end Index & Document Frequency and written out to disk.


<b>Working of the program:</b>

<b>Data Structures Used:</b>

•	List<PageTable> pageTableList implemented as an ArrayList ,where PageTable is a user defined class and each of its object represents a row in the URL Table:

public class PageTable {
    public Integer docId;
    public String url;
    public Integer numberOfTerms;

    public PageTable(Integer docId, String url,Integer numberOfTerms){
        this.docId = docId;
        this.url = url ;
        this.numberOfTerms = numberOfTerms;
    }

}

•	Map<String,Integer> wordToWordId implemented as a HashMap, contains the Term To TermId mapping

•	Map<Integer,String> wordIdToWord implemented as a HashMap, contains the TermId To Term mapping which is used during reformatting the Intermediate postings to generate the final Inverted Index

•	Map<String,List<Integer>> lexicon implemented as a HashMap,
Contains mappings of the form :
		Term => (StartIndex, EndIndex, DocumentFrequency)

<b>Methods :</b>

•	public void parseWetFile(String wetFilePath) 
•	public void generateIntermediatePosting(int wordId,int docId)
•	public void mergeSort(String memoryLimit)
•	public void generateURLTableFile()
•	public void reformatPostings()
•	public void updateLexicon(int wordId,int startIndex,int endIndex,int docFrequency)
•	public void generateLexiconFile()
•	public void generatePosting(int docId,int termFrequency)

<b>Internal Working of the program:</b>

•	parseWetFile(String wetFilePath) takes one wet file at a time, uncompresses one document at a time and parses it using ArchiveReader Library. Firstly, It Checks whether the document is of mime type text/plain. Then, it reads the uncompressed ArchiveRecord into a byte array. Converts the byte array to string and splits the string using all characters other than (a-z,A-Z,0-9,’) as seperators. This is performed using Regular Expression.

Now, the program iterates over the Terms array and insert each term as key and its termId as the value into the wordToWordId hashmap. In particular, words are assigned wordId’s in the order they are first discovered.
The Hashmap for words, initially contains numw = 0 words and whenever a word is parsed out that has never occurred before, it assigns the word ID numw and increase numw by one.

•	generateIntermediatePosting(int wordId,int docID) takes wordId & docId as input and writes them in binary into the IntermediateInvertedIndex file. Firstly, the 2 integers are inserted into a ByteBuffer and byte ordering is changed to LITTLE_ENDIAN since C program for sorting reads the bytes in LITTLE_ENDIAN unlike Java where the default is BIG_ENDIAN.Then, the byte buffer is converted to a byte array and written to the Intermediate Inverted Index using DataOutputStream Class.

•	addEntryToPageTable(String url,int size) appends a new row to the URLTableFile.

•	I/O efficient merge sort program written in C is run using ProcessBuilder class of Java. Function mergeSort(String memoryLimit) takes in the memoryLimit parameter passed in by the user and passes it to the C Program. It comprises of 3 phases :

o	<b>Sort Phase:</b> Sort Phase reads in memsize no. of bytes from the intermediate Inverted List at a time and subsequently reads 8 bytes at a time which is one record and inserts it into a qsort() which is a modified partion-exchange sort or queuesort.

Qsort uses a custom comparator function which implements the following algo:

if(wordIdLeft == wordIdRight){
    return (docIdLeft - docIdRight);
}
else{
return (wordIdLeft - wordIdRight);
}

For eg. if you have up to 20 MB of main memory available for sorting, running 
       sortphase 8 20000000 data temp list
   will create 40 sorted files of size 20MB each, called temp0, temp1,... temp39. The list of generated files is also written to file "list"

o	<b>Merge Phase:</b> Merge Phase uses a min-heap ,copies the record corresponding to the minimum wordId which is at the root to the output & replaces the minimum in heap by the next record from that file.
For eg. To merge the 40 files in one phase, assuming you have again 20MB of main memory available,
       mergephase 8 20000000 40 list result list2
    will merge the 40 files listed in "list", merge them into one
    result file called "result0", and write the filename "result0" into file
    "list2".

o	<b>Check correctness Phase :</b> Checkoutput compares the 2 consecutive records at a time & return Not Sorted if (wordIdLeft > wordIdRight || (wordIdLeft == wordIdRight && docIdLeft > docIdRight))

•	MergeSort Process generates result0 which is then reformatted to generate the Final Inverted Index. reformatPostings() reads the bytes into a byte buffer, converts to LITTLE_ENDIAN and updates Map<Integer,Integer> 
docIdToTermFrequency implemented as a TreeMap in a single iteration. Hence the custom algorithm works in linear time and updates the Lexicon using docIdToTermFrequency TreeMap.

•	generateLexiconFile() iterates over the keyset of lexicon TreeMap and writes out the Terms in alphabetical order along with their startIndex,EndIndex & DocumentFrequency to Lexicon.txt file.


<b>Results:</b>

I parsed about 120 WET files = 120 * 40,000 = 4.8 million pages in 133 minutes = 2.25 hours which give a speed of about 600 pages /second approximately.

My intermediate Inverted List was 36.4 GB large and the Final Inverted Index was 21.8 GB.


<b>Design Decisions:</b>

•	I figured out that implementing the first part i.e. generating intermediate postings from the documents and writing these postings out in unsorted or partially sorted form could be done best using Java rather than C or C++ since there is a lot of support for WET parsing libraries in Java but not so much for C/C++.
•	The second part i.e. sorting and merging the postings needed I/O efficiency & C performs well in that scenario so I decided to go ahead with C for this part also since it is pretty easy to integrate C or for that fact any other executable with Java, since Java provides a ProcessBuilder class which runs any executable as you would in the terminal and also writes out the output & error stream back to console output.
•	For the last part i.e. reformatting the sorted postings into the final index and updating lookup structures, I decided to go ahead once again with Java since manipulating bytes is pretty easy using ByteBuffer class and also updation & writing out the Lexicon and URL Table to disk is pretty convenient.
•	One of the problems which I faced was not being able to figure why the bytes written in Java , when read in C, would get changed. After doing much research I found out that Java’s Native byte format uses BIG_ENDIAN notation which is useful for communicating over networks whereas C uses LITTLE_ENDIAN notation by default. So I fixed that up, and converted everything to LITTLE_ENDIAN notation.
•	Also I decided that uncompressing only the document that needs to parsed at a moment, is the most efficient way to save disk space.
•	For the purpose of reformatting the postings I chose to implement a WordIdToWord Hashmap as well, which is the most efficient way to replace the WordId with the word in the final Inverted Index.


 
<b>Design Limitations:</b>

The various HashMap’s that I have used grow pretty significantly in Java and occupy more than intended main memory space and start affecting performance after a certain time. Maybe playing around with initial Capacity and load factor values for Hashmap , I Can figure out some optimum values to increase the performance. Also writing them out to disk & emptying them after the size has grown beyond a certain threshold could also be another solution. I will certainly try to optimize these constraints in the next part of the assignment. 





