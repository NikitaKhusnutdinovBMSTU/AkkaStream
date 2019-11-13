package lab5.bmstu;

public class GetMSG {

    private String URL;
    private int count;

    public GetMSG(String initURL, int initCount){
        this.URL = initURL;
        this.count = initCount;
    }

    public String getURL(){
        return URL;
    }

    public int getCount(){
        return count;
    }
}
