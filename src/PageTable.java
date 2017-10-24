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
